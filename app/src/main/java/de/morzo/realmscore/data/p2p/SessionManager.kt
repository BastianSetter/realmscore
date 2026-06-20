package de.morzo.realmscore.data.p2p

import android.bluetooth.BluetoothServerSocket
import de.morzo.realmscore.data.datastore.DeviceUuidProvider
import de.morzo.realmscore.domain.p2p.P2PSessionRepository
import de.morzo.realmscore.domain.p2p.model.BluetoothStatus
import de.morzo.realmscore.domain.p2p.model.ConnectedDevice
import de.morzo.realmscore.domain.p2p.model.HandCardSyncData
import de.morzo.realmscore.domain.p2p.model.HandshakePayload
import de.morzo.realmscore.domain.p2p.model.NavSignal
import de.morzo.realmscore.domain.p2p.model.ParticipantInfo
import de.morzo.realmscore.domain.p2p.model.SessionRole
import de.morzo.realmscore.domain.p2p.model.SessionState
import de.morzo.realmscore.domain.p2p.model.SyncMessage
import de.morzo.realmscore.domain.p2p.model.UnitLock
import de.morzo.realmscore.domain.repository.BackupRepository
import de.morzo.realmscore.domain.repository.RoundRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.IOException

/**
 * Coordinates a live P2P session over [BluetoothRfcommManager] (Phase 28, Stage A scope).
 *
 * The **host** opens an RFCOMM server, accepts clients in a loop, and broadcasts the participant
 * roster ([SyncMessage.PlayerListUpdate]). Each **client** connects to the host MAC (resolved by the
 * CompanionDeviceManager), announces itself ([SyncMessage.DeviceJoined]) and receives roster updates.
 *
 * Locking, live card sync, reconnect/heartbeat and end-of-game distribution are wired through the
 * same connections in Stage B; [routeMessage] is the single extension point for those.
 */
class SessionManager(
    private val bluetooth: BluetoothRfcommManager,
    private val handshake: HandshakeManager,
    private val deviceUuidProvider: DeviceUuidProvider,
    private val roundRepo: RoundRepository,
    private val backupRepository: BackupRepository,
    private val mirrorSync: GameMirrorSync,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob()),
) : P2PSessionRepository {

    private val _sessionState = MutableStateFlow<SessionState>(SessionState.Idle)
    override val sessionState: StateFlow<SessionState> = _sessionState.asStateFlow()

    private val _incomingParticipants = MutableStateFlow<List<ParticipantInfo>>(emptyList())
    override val incomingParticipants: StateFlow<List<ParticipantInfo>> =
        _incomingParticipants.asStateFlow()

    private val _joinedParticipants = MutableStateFlow<List<ParticipantInfo>>(emptyList())
    override val joinedParticipants: StateFlow<List<ParticipantInfo>> =
        _joinedParticipants.asStateFlow()

    private val _navSignals = MutableSharedFlow<NavSignal>(extraBufferCapacity = 16)
    override val navSignals: SharedFlow<NavSignal> = _navSignals.asSharedFlow()

    private val _roundStatus = MutableStateFlow<SyncMessage.RoundStatus?>(null)
    override val roundStatus: StateFlow<SyncMessage.RoundStatus?> = _roundStatus.asStateFlow()

    /**
     * Live, uncommitted per-unit card selections (Stage B+): unitId → in-progress card keys. Fed by
     * [SyncMessage.HandDraftUpdate]; the host relays these and is the sole authority for clearing them.
     * [liveDraftOwner] tracks which device owns each draft so a disconnect can free its in-progress cards.
     */
    private val _liveDrafts = MutableStateFlow<Map<String, Set<String>>>(emptyMap())
    override val liveDrafts: StateFlow<Map<String, Set<String>>> = _liveDrafts.asStateFlow()
    private val liveDraftOwner = mutableMapOf<String, String>() // unitId -> deviceId

    /** The previous round's per-device ordered submit lists, fed to each capture screen's auto-assign. */
    private val _roundOrderSeed = MutableStateFlow<Map<String, List<String>>>(emptyMap())
    override val roundOrderSeed: StateFlow<Map<String, List<String>>> = _roundOrderSeed.asStateFlow()

    /**
     * Host-only submit log: roundId → (deviceId → unitIds in the order that device submitted them).
     * In-memory only — a host restart mid-game loses it (the next round then falls back to seat order).
     * Snapshotted into [_roundOrderSeed] when the *next* round opens (see [startSharedSession]).
     */
    private val submissionLog = LinkedHashMap<String, LinkedHashMap<String, MutableList<String>>>()

    /** Host-only authoritative lock registry; clients drive it via [SyncMessage] over Bluetooth. */
    private val lockManager = LockManager()

    /** deviceId → display name, for the "wird bearbeitet von X" indicator (host resolves this). */
    private val deviceNamesById = mutableMapOf<String, String>()

    /** This device's id, cached once the session opens (avoids a suspend read on every lock op). */
    private var myDeviceId: String? = null

    /** Open connections: many (one per client) on the host, exactly one on a client. */
    private val connections = mutableListOf<P2PConnection>()
    private val lock = Any()

    private var serverSocket: BluetoothServerSocket? = null
    private var latestParticipants: List<ParticipantInfo> = emptyList()

    /**
     * Client-side: the `originDeviceId` this device announced as its own seat (set in [connectToHost]),
     * and the canonical (host-assigned) profile id the host gave that seat — resolved from the roster.
     * Used at game end to reattribute the mirror's self seat to the local owner profile (B7).
     */
    private var mySeatOriginDeviceId: String? = null
    private var mySeatCanonicalId: String? = null

    // --- Heartbeat / reconnect (B6). Per-connection liveness + the active game/round for catch-up. ---
    private val connectionDeviceId = mutableMapOf<P2PConnection, String>()
    private val connectionLastSeen = mutableMapOf<P2PConnection, Long>()
    private var heartbeatJob: kotlinx.coroutines.Job? = null
    private var activeGameId: String? = null
    private var activeRoundId: String? = null

    override fun bluetoothStatus(): BluetoothStatus = when {
        !bluetooth.isBluetoothSupported -> BluetoothStatus.UNSUPPORTED
        !bluetooth.hasConnectPermission() -> BluetoothStatus.PERMISSION_MISSING
        !bluetooth.isBluetoothEnabled -> BluetoothStatus.DISABLED
        else -> BluetoothStatus.READY
    }

    override fun localBluetoothName(): String? = bluetooth.localBluetoothName()

    override suspend fun openForJoins(
        gameId: String,
        participants: List<ParticipantInfo>,
    ): Result<Unit> = runCatching {
        check(bluetoothStatus() == BluetoothStatus.READY) { "Bluetooth not ready" }
        // Opening for joins is an explicit "host a fresh session" action: tear down any prior session
        // (e.g. a leftover from an abandoned game) so we don't run two RFCOMM servers on one service UUID.
        close()
        val deviceId = deviceUuidProvider.get()
        val name = bluetooth.localBluetoothName() ?: "RealmScore"
        myDeviceId = deviceId
        synchronized(lock) { deviceNamesById[deviceId] = name }
        val hostSession = handshake.openHostSession(gameId, deviceId, name)
        latestParticipants = participants.distinctBy { it.profileId }

        val server = bluetooth.openServerSocket()
        serverSocket = server
        _sessionState.value = SessionState.Hosting(
            gameId = gameId,
            payload = hostSession.payload,
            sessionCode = hostSession.sessionCode,
        )

        scope.launch {
            while (isActive) {
                val connection = try {
                    bluetooth.accept(server)
                } catch (_: IOException) {
                    break // server socket closed
                }
                onClientConnected(connection)
            }
        }
        startHeartbeat()
        Unit
    }

    private fun onClientConnected(connection: P2PConnection) {
        synchronized(lock) {
            connections += connection
            connectionLastSeen[connection] = System.currentTimeMillis()
        }
        scope.launch {
            // Send the joining client the current roster immediately.
            runCatching { connection.send(SyncMessage.PlayerListUpdate(latestParticipants)) }
            // Reconnect catch-up (B6): if a game is already running, bring the (re)joining device fully
            // up to date — the shared mirror, the live lock state, and a StartRound to navigate it in.
            val gameId = activeGameId
            val roundId = activeRoundId
            if (gameId != null && roundId != null) {
                backupRepository.exportGame(gameId)?.let { snap ->
                    runCatching { connection.send(SyncMessage.FullGameState(snap.game, snap.profiles)) }
                }
                _roundStatus.value?.let { runCatching { connection.send(it) } }
                runCatching {
                    connection.send(SyncMessage.StartRound(gameId, roundId, _roundOrderSeed.value))
                }
            }
        }
        scope.launch {
            try {
                connection.receive().collect { message -> routeMessage(message, connection) }
            } finally {
                onConnectionLost(connection)
            }
        }
    }

    override suspend fun broadcastParticipants(participants: List<ParticipantInfo>) {
        // Dedup by profileId: several devices/players can collide on one host-side profile (e.g. same
        // name, or a re-join), and a duplicate key would crash the roster's LazyColumn on every device.
        val deduped = participants.distinctBy { it.profileId }
        latestParticipants = deduped
        broadcast(SyncMessage.PlayerListUpdate(deduped))
    }

    override fun isInActiveGame(): Boolean = activeGameId != null

    override fun resetJoinedRoster() {
        // Clear leftover joiners without touching connections/sockets (a fresh new-game setup; the live
        // join re-announces over the existing or a re-scanned connection).
        _joinedParticipants.value = emptyList()
    }

    override suspend fun startSharedSession(gameId: String): Result<String> = runCatching {
        // Snapshot the just-finished round's per-device submit order before we mint the next one, so
        // every phone can build the adaptive round-2+ assign order (empty for the first round).
        val seed = snapshotSubmissions(activeRoundId)
        // Mint the next round, then distribute the whole game so every client builds the same-UUID
        // mirror before being told to navigate in.
        val round = roundRepo.startRound(gameId)
        val snapshot = backupRepository.exportGame(gameId)
            ?: error("Game $gameId not found for distribution")
        activeGameId = gameId
        activeRoundId = round.id
        _roundOrderSeed.value = seed
        clearAllDrafts() // a fresh round: no in-progress drafts carry over
        broadcast(SyncMessage.FullGameState(snapshot.game, snapshot.profiles))
        broadcast(SyncMessage.StartRound(gameId, round.id, seed))
        round.id
    }

    /** Host: a read-only copy of [roundId]'s submit log (deviceId → ordered unitIds), or empty. */
    private fun snapshotSubmissions(roundId: String?): Map<String, List<String>> =
        synchronized(lock) {
            roundId?.let { submissionLog[it]?.mapValues { (_, units) -> units.toList() } } ?: emptyMap()
        }

    /** Host: append [unitId] to [deviceId]'s ordered submit log for [roundId] (first submission wins). */
    private fun recordSubmission(roundId: String, unitId: String, deviceId: String) {
        synchronized(lock) {
            val perDevice = submissionLog.getOrPut(roundId) { LinkedHashMap() }
            val units = perDevice.getOrPut(deviceId) { mutableListOf() }
            if (unitId !in units) units += unitId
        }
    }

    // --- Stage B: distributed optimistic locking (host-authoritative) ---

    override fun ownSeatUnitId(): String? = mySeatCanonicalId

    private fun isHost(): Boolean = _sessionState.value is SessionState.Hosting

    override suspend fun requestLock(roundId: String, unitId: String) {
        val deviceId = myDeviceId ?: deviceUuidProvider.get().also { myDeviceId = it }
        if (isHost()) {
            lockManager.tryLock(roundId, unitId, deviceId)
            rebuildAndBroadcastStatus(roundId)
        } else {
            sendToHost(SyncMessage.LockRequest(profileId = unitId, roundId = roundId, deviceId = deviceId))
        }
    }

    override suspend fun releaseLock(roundId: String, unitId: String) {
        val deviceId = myDeviceId ?: deviceUuidProvider.get().also { myDeviceId = it }
        if (isHost()) {
            lockManager.release(roundId, unitId, deviceId)
            clearDraftAndBroadcast(roundId, unitId)
            rebuildAndBroadcastStatus(roundId)
        } else {
            sendToHost(SyncMessage.LockRelease(profileId = unitId, roundId = roundId))
        }
    }

    override suspend fun forceUnlock(roundId: String, unitId: String) {
        if (isHost()) {
            lockManager.forceUnlock(roundId, unitId)
            clearDraftAndBroadcast(roundId, unitId)
            rebuildAndBroadcastStatus(roundId)
        } else {
            sendToHost(SyncMessage.UnlockRequest(profileId = unitId, roundId = roundId))
        }
    }

    override suspend fun markUnitDone(roundId: String, unitId: String) {
        if (isHost()) {
            // Attribute to the lock holder (this device for its own hand), recorded before markDone
            // drops the lock, so the next round's auto-assign can replay each device's submit order.
            val deviceId = lockManager.holderOf(roundId, unitId)
                ?: myDeviceId ?: deviceUuidProvider.get().also { myDeviceId = it }
            recordSubmission(roundId, unitId, deviceId)
            lockManager.markDone(roundId, unitId)
            clearDraftAndBroadcast(roundId, unitId)
            rebuildAndBroadcastStatus(roundId)
        } else {
            sendToHost(SyncMessage.UnitDone(roundId = roundId, unitId = unitId))
        }
    }

    /** Host: rebuild [RoundStatus] from the registry (resolving device names) and fan it out. */
    private suspend fun rebuildAndBroadcastStatus(roundId: String) {
        val snapshot = lockManager.snapshot(roundId)
        val locks = synchronized(lock) {
            snapshot.locks.map { (unitId, deviceId) ->
                UnitLock(unitId, deviceId, deviceNamesById[deviceId] ?: deviceId)
            }
        }
        val status = SyncMessage.RoundStatus(roundId, locks, snapshot.doneUnitIds)
        activeRoundId = roundId
        _roundStatus.value = status
        broadcast(status)
    }

    override suspend fun pushHandCards(
        roundId: String,
        unitId: String,
        cards: List<HandCardSyncData>,
    ) {
        val message = SyncMessage.HandCardUpdate(roundId = roundId, profileId = unitId, cards = cards)
        if (isHost()) broadcast(message) else sendToHost(message)
    }

    override suspend fun pushDiscard(roundId: String, cards: List<String>) {
        val message = SyncMessage.DiscardUpdate(roundId = roundId, cards = cards)
        if (isHost()) broadcast(message) else sendToHost(message)
    }

    // --- Live in-progress card drafts (Stage B+): availability greying before a hand is submitted ---

    override suspend fun pushHandDraft(roundId: String, unitId: String, cardKeys: List<String>) {
        val deviceId = myDeviceId ?: deviceUuidProvider.get().also { myDeviceId = it }
        val message = SyncMessage.HandDraftUpdate(roundId, unitId, deviceId, cardKeys)
        applyDraft(message)
        if (isHost()) broadcast(message) else sendToHost(message)
    }

    /** Fold a draft update into [_liveDrafts] (host + client). Empty keys clear the unit. Active round only. */
    private fun applyDraft(message: SyncMessage.HandDraftUpdate) {
        val active = activeRoundId
        if (active != null && message.roundId != active) return
        synchronized(lock) {
            if (message.cardKeys.isEmpty()) liveDraftOwner.remove(message.unitId)
            else liveDraftOwner[message.unitId] = message.deviceId
        }
        _liveDrafts.update { current ->
            if (message.cardKeys.isEmpty()) current - message.unitId
            else current + (message.unitId to message.cardKeys.toSet())
        }
    }

    /** Host: clear one unit's in-progress draft on every device (on commit / release / takeover). */
    private suspend fun clearDraftAndBroadcast(roundId: String, unitId: String) {
        if (synchronized(lock) { unitId !in liveDraftOwner }) return
        val deviceId = myDeviceId ?: deviceUuidProvider.get()
        val message = SyncMessage.HandDraftUpdate(roundId, unitId, deviceId, emptyList())
        applyDraft(message)
        broadcast(message)
    }

    private fun clearAllDrafts() {
        synchronized(lock) { liveDraftOwner.clear() }
        _liveDrafts.value = emptyMap()
    }

    override suspend fun finishRound(roundId: String) {
        if (!isHost()) return
        val round = roundRepo.getRoundById(roundId) ?: return
        val snapshot = backupRepository.exportGame(round.gameId) ?: return
        // Distribute the canonical results, then tell everyone to reveal (host navigates locally too).
        broadcast(SyncMessage.FullGameState(snapshot.game, snapshot.profiles))
        broadcast(SyncMessage.RoundComplete(roundId))
        _navSignals.emit(NavSignal.OpenReveal(roundId))
    }

    override suspend fun announceNewGameSetup() {
        if (!isHost()) return
        // The session stays alive across a game close (closeSharedGame only cleared activeGameId), so we
        // can tell the still-connected clients to wait while the host builds the next game.
        broadcast(SyncMessage.NewGameSetup)
    }

    override suspend fun closeSharedGame(gameId: String) {
        if (!isHost()) return
        // The game row is already closed locally; export carries closedAt to every client.
        val snapshot = backupRepository.exportGame(gameId) ?: return
        broadcast(SyncMessage.FullGameState(snapshot.game, snapshot.profiles))
        broadcast(SyncMessage.GameClosed(gameId, System.currentTimeMillis()))
        // Game over: the session is no longer guarding an in-progress game.
        activeGameId = null
        activeRoundId = null
    }

    /** Client: send a message over the single host connection. */
    private suspend fun sendToHost(message: SyncMessage) {
        val connection = synchronized(lock) { connections.firstOrNull() }
        connection?.let { runCatching { it.send(message) } }
    }

    /** Host: fan a message out to every client except the one it came from (avoids echoing it back). */
    private suspend fun broadcastExcept(message: SyncMessage, except: P2PConnection) {
        val snapshot = synchronized(lock) { connections.filter { it !== except } }
        snapshot.forEach { runCatching { it.send(message) } }
    }

    override suspend fun connectToHost(
        payload: HandshakePayload,
        macAddress: String,
        selfParticipant: ParticipantInfo,
    ): Result<Unit> = runCatching {
        check(bluetoothStatus() == BluetoothStatus.READY) { "Bluetooth not ready" }
        // Joining is an explicit commit: drop any prior session so a re-join starts clean.
        close()
        _sessionState.value = SessionState.Connecting
        val connection = bluetooth.connect(macAddress)
        synchronized(lock) {
            connections += connection
            connectionLastSeen[connection] = System.currentTimeMillis()
        }

        // Remember which seat is ours so we can reconcile it to the local owner at game end (B7).
        mySeatOriginDeviceId = selfParticipant.originDeviceId

        val deviceId = deviceUuidProvider.get()
        val name = bluetooth.localBluetoothName() ?: "RealmScore"
        myDeviceId = deviceId
        connection.send(SyncMessage.DeviceJoined(deviceId, name, selfParticipant))

        _sessionState.value = SessionState.Connected(
            role = SessionRole.CLIENT,
            gameId = payload.gameId,
        )
        scope.launch {
            try {
                connection.receive().collect { message -> routeMessage(message, connection) }
            } finally {
                onConnectionLost(connection)
            }
        }
        startHeartbeat()
        Unit
    }.onFailure {
        _sessionState.value = SessionState.Error(it.message ?: "connect failed")
    }

    /** Single inbound-message dispatch. Stage B hangs lock / card-sync / full-state handling here. */
    private suspend fun routeMessage(message: SyncMessage, from: P2PConnection) {
        // Any traffic counts as a heartbeat: keep this connection marked alive (B6).
        synchronized(lock) { connectionLastSeen[from] = System.currentTimeMillis() }
        when (message) {
            is SyncMessage.DeviceJoined -> {
                addConnectedDevice(ConnectedDevice(message.deviceId, message.deviceName))
                synchronized(lock) {
                    deviceNamesById[message.deviceId] = message.deviceName
                    connectionDeviceId[from] = message.deviceId
                }
                // "Join adds a player": surface the joiner's own player so the host roster picks it up
                // (deduped by originDeviceId — one player per device, latest wins).
                message.participant?.let { joined ->
                    _joinedParticipants.update { current ->
                        current.filterNot { it.originDeviceId == joined.originDeviceId } + joined
                    }
                }
                // Push the latest roster to the newcomer.
                runCatching { from.send(SyncMessage.PlayerListUpdate(latestParticipants)) }
            }

            is SyncMessage.PlayerListUpdate -> {
                _incomingParticipants.value = message.participants.distinctBy { it.profileId }
                // Resolve our canonical (host-assigned) profile id from the roster (B7 reconcile).
                mySeatOriginDeviceId?.let { own ->
                    message.participants.firstOrNull { it.originDeviceId == own }
                        ?.let { mySeatCanonicalId = it.profileId }
                }
            }

            // Client: fold the host's game snapshot into the local mirror (same UUIDs everywhere).
            is SyncMessage.FullGameState -> mirrorSync.applyFullGameState(message)

            // Client: the host opened a round — record it as the active game (so visiting Home/Join
            // doesn't tear the live session down) and navigate everyone into capture together.
            is SyncMessage.StartRound -> {
                activeGameId = message.gameId
                activeRoundId = message.roundId
                _roundOrderSeed.value = message.priorSubmissions // seed the adaptive auto-assign order
                clearAllDrafts() // a fresh round: drop any leftover in-progress drafts
                _navSignals.emit(NavSignal.OpenRound(message.roundId))
            }

            // Client: adopt the host's authoritative lock + done state.
            is SyncMessage.RoundStatus -> _roundStatus.value = message

            // A live capture from another device: fold it into our mirror. The host also relays it on
            // to the other clients (the hub), skipping the sender to avoid an echo.
            is SyncMessage.HandCardUpdate -> {
                mirrorSync.applyHandCardUpdate(message)
                if (isHost()) broadcastExcept(message, from)
            }

            is SyncMessage.DiscardUpdate -> {
                mirrorSync.applyDiscardUpdate(message)
                if (isHost()) broadcastExcept(message, from)
            }

            // Live in-progress draft from another device: fold into the availability set; the host
            // (the hub) relays it on to the other clients, skipping the sender to avoid an echo.
            is SyncMessage.HandDraftUpdate -> {
                applyDraft(message)
                if (isHost()) broadcastExcept(message, from)
            }

            // Client: the host finished the round (its FullGameState arrived just before this) — reveal.
            is SyncMessage.RoundComplete -> _navSignals.emit(NavSignal.OpenReveal(message.roundId))

            // Client: the host closed the game. The final FullGameState was merged just before this;
            // now reattribute our own seat from the host's canonical profile to our local owner so the
            // finished game shows under us in stats.
            is SyncMessage.GameClosed -> {
                mySeatCanonicalId?.let { canonical -> backupRepository.reconcileSelfSeat(canonical) }
                // The game is over — the session is no longer "in a game", so a later Home/Join visit
                // is free to reset it for a fresh session.
                activeGameId = null
                activeRoundId = null
                _navSignals.emit(NavSignal.OpenGameSummary(message.gameId))
            }

            // Client: the host is preparing the next game with this group — show the waiting screen
            // until the host starts the next round (StartRound then pulls us into capture).
            is SyncMessage.NewGameSetup -> _navSignals.emit(NavSignal.OpenNewGameWait)

            // Host: a client requests/releases a lock or finishes a unit → arbitrate and re-broadcast.
            is SyncMessage.LockRequest -> if (isHost()) {
                lockManager.tryLock(message.roundId, message.profileId, message.deviceId)
                rebuildAndBroadcastStatus(message.roundId)
            }

            is SyncMessage.LockRelease -> if (isHost()) {
                // The holder is releasing its own unit; we trust the sender (no deviceId on the wire).
                lockManager.forceUnlock(message.roundId, message.profileId)
                clearDraftAndBroadcast(message.roundId, message.profileId)
                rebuildAndBroadcastStatus(message.roundId)
            }

            is SyncMessage.UnlockRequest -> if (isHost()) {
                lockManager.forceUnlock(message.roundId, message.profileId)
                clearDraftAndBroadcast(message.roundId, message.profileId)
                rebuildAndBroadcastStatus(message.roundId)
            }

            is SyncMessage.UnitDone -> if (isHost()) {
                // Attribute to the lock holder (the submitting client), falling back to the connection's
                // announced device, before markDone drops the lock.
                val deviceId = lockManager.holderOf(message.roundId, message.unitId)
                    ?: synchronized(lock) { connectionDeviceId[from] }
                deviceId?.let { recordSubmission(message.roundId, message.unitId, it) }
                lockManager.markDone(message.roundId, message.unitId)
                clearDraftAndBroadcast(message.roundId, message.unitId)
                rebuildAndBroadcastStatus(message.roundId)
            }

            SyncMessage.Ping -> runCatching { from.send(SyncMessage.Pong) }

            else -> Unit // Stage B: incremental card updates, reveal, …
        }
    }

    private fun addConnectedDevice(device: ConnectedDevice) {
        _sessionState.update { state ->
            when (state) {
                is SessionState.Hosting ->
                    if (state.connectedDevices.any { it.deviceId == device.deviceId }) {
                        state
                    } else {
                        state.copy(connectedDevices = state.connectedDevices + device)
                    }

                is SessionState.Connected ->
                    if (state.connectedDevices.any { it.deviceId == device.deviceId }) {
                        state
                    } else {
                        state.copy(connectedDevices = state.connectedDevices + device)
                    }

                else -> state
            }
        }
    }

    private suspend fun broadcast(message: SyncMessage) {
        val snapshot = synchronized(lock) { connections.toList() }
        snapshot.forEach { runCatching { it.send(message) } }
    }

    // --- Heartbeat + auto-reclaim (B6) ---

    /** Pings every connection every [HEARTBEAT_INTERVAL_MS] and reaps any silent for [HEARTBEAT_TIMEOUT_MS]. */
    private fun startHeartbeat() {
        if (heartbeatJob?.isActive == true) return
        heartbeatJob = scope.launch {
            while (isActive) {
                kotlinx.coroutines.delay(HEARTBEAT_INTERVAL_MS)
                val conns = synchronized(lock) { connections.toList() }
                conns.forEach { c -> runCatching { c.send(SyncMessage.Ping) } }
                val now = System.currentTimeMillis()
                val dead = synchronized(lock) {
                    conns.filter { now - (connectionLastSeen[it] ?: now) > HEARTBEAT_TIMEOUT_MS }
                }
                dead.forEach { onConnectionLost(it) }
            }
        }
    }

    /**
     * A connection died (stream ended or heartbeat timeout). On the host: free the dropped device's
     * locks so its units return to the pool and re-broadcast the round status. On a client: mark the
     * session [SessionState.Disconnected] — local play continues on the mirror until a manual rejoin
     * (whereupon the host's catch-up in [onClientConnected] resyncs it).
     */
    private fun onConnectionLost(connection: P2PConnection) {
        val deviceId = synchronized(lock) {
            val present = connections.remove(connection)
            if (!present) return // already reaped (finally + watchdog can both fire)
            connectionLastSeen.remove(connection)
            connectionDeviceId.remove(connection)
        }
        runCatching { connection.close() }
        if (isHost()) {
            deviceId?.let { lockManager.releaseAllHeldBy(it) }
            _sessionState.update { state ->
                if (state is SessionState.Hosting) {
                    state.copy(connectedDevices = state.connectedDevices.filterNot { it.deviceId == deviceId })
                } else {
                    state
                }
            }
            activeRoundId?.let { roundId ->
                scope.launch {
                    // Free the dropped device's in-progress drafts too, so its half-picked cards
                    // become available again on the others (it left its locks AND its draft behind).
                    val orphaned = synchronized(lock) {
                        liveDraftOwner.filterValues { it == deviceId }.keys.toList()
                    }
                    orphaned.forEach { clearDraftAndBroadcast(roundId, it) }
                    rebuildAndBroadcastStatus(roundId)
                }
            }
        } else {
            val gameId = (_sessionState.value as? SessionState.Connected)?.gameId ?: activeGameId.orEmpty()
            _sessionState.value = SessionState.Disconnected(
                role = SessionRole.CLIENT,
                gameId = gameId,
                reason = "connection lost",
            )
        }
    }

    override fun close() {
        heartbeatJob?.cancel()
        heartbeatJob = null
        synchronized(lock) {
            connections.forEach { it.close() }
            connections.clear()
            connectionLastSeen.clear()
            connectionDeviceId.clear()
        }
        runCatching { serverSocket?.close() }
        serverSocket = null
        handshake.closeHostSession()
        latestParticipants = emptyList()
        mySeatOriginDeviceId = null
        mySeatCanonicalId = null
        myDeviceId = null
        activeGameId = null
        activeRoundId = null
        lockManager.reset()
        clearAllDrafts()
        synchronized(lock) {
            deviceNamesById.clear()
            submissionLog.clear()
        }
        _roundOrderSeed.value = emptyMap()
        _roundStatus.value = null
        _incomingParticipants.value = emptyList()
        _joinedParticipants.value = emptyList()
        _sessionState.value = SessionState.Idle
    }

    private companion object {
        const val HEARTBEAT_INTERVAL_MS = 5_000L
        const val HEARTBEAT_TIMEOUT_MS = 30_000L
    }
}
