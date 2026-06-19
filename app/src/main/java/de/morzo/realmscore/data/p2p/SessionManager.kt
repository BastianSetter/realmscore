package de.morzo.realmscore.data.p2p

import android.bluetooth.BluetoothServerSocket
import de.morzo.realmscore.data.datastore.DeviceUuidProvider
import de.morzo.realmscore.domain.p2p.P2PSessionRepository
import de.morzo.realmscore.domain.p2p.model.BluetoothStatus
import de.morzo.realmscore.domain.p2p.model.ConnectedDevice
import de.morzo.realmscore.domain.p2p.model.HandshakePayload
import de.morzo.realmscore.domain.p2p.model.ParticipantInfo
import de.morzo.realmscore.domain.p2p.model.SessionRole
import de.morzo.realmscore.domain.p2p.model.SessionState
import de.morzo.realmscore.domain.p2p.model.SyncMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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

    /** Open connections: many (one per client) on the host, exactly one on a client. */
    private val connections = mutableListOf<P2PConnection>()
    private val lock = Any()

    private var serverSocket: BluetoothServerSocket? = null
    private var latestParticipants: List<ParticipantInfo> = emptyList()

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
        val deviceId = deviceUuidProvider.get()
        val name = bluetooth.localBluetoothName() ?: "RealmScore"
        val hostSession = handshake.openHostSession(gameId, deviceId, name)
        latestParticipants = participants

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
        Unit
    }

    private fun onClientConnected(connection: P2PConnection) {
        synchronized(lock) { connections += connection }
        // Send the joining client the current roster immediately.
        scope.launch { runCatching { connection.send(SyncMessage.PlayerListUpdate(latestParticipants)) } }
        scope.launch {
            connection.receive().collect { message -> routeMessage(message, connection) }
        }
    }

    override suspend fun broadcastParticipants(participants: List<ParticipantInfo>) {
        latestParticipants = participants
        broadcast(SyncMessage.PlayerListUpdate(participants))
    }

    override suspend fun connectToHost(
        payload: HandshakePayload,
        macAddress: String,
        selfParticipant: ParticipantInfo,
    ): Result<Unit> = runCatching {
        check(bluetoothStatus() == BluetoothStatus.READY) { "Bluetooth not ready" }
        _sessionState.value = SessionState.Connecting
        val connection = bluetooth.connect(macAddress)
        synchronized(lock) { connections += connection }

        val deviceId = deviceUuidProvider.get()
        val name = bluetooth.localBluetoothName() ?: "RealmScore"
        connection.send(SyncMessage.DeviceJoined(deviceId, name, selfParticipant))

        _sessionState.value = SessionState.Connected(
            role = SessionRole.CLIENT,
            gameId = payload.gameId,
        )
        scope.launch {
            connection.receive().collect { message -> routeMessage(message, connection) }
        }
        Unit
    }.onFailure {
        _sessionState.value = SessionState.Error(it.message ?: "connect failed")
    }

    /** Single inbound-message dispatch. Stage B hangs lock / card-sync / full-state handling here. */
    private suspend fun routeMessage(message: SyncMessage, from: P2PConnection) {
        when (message) {
            is SyncMessage.DeviceJoined -> {
                addConnectedDevice(ConnectedDevice(message.deviceId, message.deviceName))
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

            is SyncMessage.PlayerListUpdate ->
                _incomingParticipants.value = message.participants

            SyncMessage.Ping -> runCatching { from.send(SyncMessage.Pong) }

            else -> Unit // Stage B: locks, card updates, full game state, …
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

    override fun close() {
        synchronized(lock) {
            connections.forEach { it.close() }
            connections.clear()
        }
        runCatching { serverSocket?.close() }
        serverSocket = null
        handshake.closeHostSession()
        latestParticipants = emptyList()
        _incomingParticipants.value = emptyList()
        _joinedParticipants.value = emptyList()
        _sessionState.value = SessionState.Idle
    }
}
