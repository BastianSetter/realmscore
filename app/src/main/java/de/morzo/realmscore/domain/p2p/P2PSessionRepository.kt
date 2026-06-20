package de.morzo.realmscore.domain.p2p

import de.morzo.realmscore.domain.p2p.model.BluetoothStatus
import de.morzo.realmscore.domain.p2p.model.HandshakePayload
import de.morzo.realmscore.domain.p2p.model.HandCardSyncData
import de.morzo.realmscore.domain.p2p.model.NavSignal
import de.morzo.realmscore.domain.p2p.model.ParticipantInfo
import de.morzo.realmscore.domain.p2p.model.SessionState
import de.morzo.realmscore.domain.p2p.model.SyncMessage
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * High-level P2P session API (Phase 28, Stage A scope: setup + live player list). Hides all Bluetooth
 * / handshake machinery from the UI; ViewModels observe [sessionState] and [incomingParticipants].
 */
interface P2PSessionRepository {

    /** Drives the host "open for joins" screen, the join flow and the connection indicators. */
    val sessionState: StateFlow<SessionState>

    /** Client-side: the live roster the host broadcasts during game setup. */
    val incomingParticipants: StateFlow<List<ParticipantInfo>>

    /**
     * Host-side: players contributed by joined clients (one per device, deduped by `originDeviceId`).
     * The new-game screen merges these into its roster so a join shows up as a startable player.
     */
    val joinedParticipants: StateFlow<List<ParticipantInfo>>

    /**
     * One-shot navigation commands (Stage B): clients follow the host into round capture / reveal.
     * A `SharedFlow` so each command fires its navigation exactly once (host emits via broadcasts).
     */
    val navSignals: SharedFlow<NavSignal>

    /**
     * The host-authoritative lock + done state of the active round (Stage B), or null when no round is
     * being captured. Every device's round-capture screen observes this to paint lock indicators and to
     * drive its deterministic auto-assignment of free units.
     */
    val roundStatus: StateFlow<SyncMessage.RoundStatus?>

    fun bluetoothStatus(): BluetoothStatus

    /** The local Bluetooth adapter name the host advertises (so the joiner can find it in CDM). */
    fun localBluetoothName(): String?

    /**
     * Host: open [gameId] for joins, advertising the current [participants]. Starts the RFCOMM server
     * and the accept loop. On success [sessionState] transitions to [SessionState.Hosting].
     */
    suspend fun openForJoins(gameId: String, participants: List<ParticipantInfo>): Result<Unit>

    /** Host: re-broadcast the roster after it changed in the new-game screen. */
    suspend fun broadcastParticipants(participants: List<ParticipantInfo>)

    /**
     * Host: the game [gameId] has been created locally — mint its first round, distribute the whole
     * game ([SyncMessage.FullGameState]) so every client builds the same-UUID mirror, then signal
     * [SyncMessage.StartRound] so all devices enter round capture together. Returns the new round id
     * for the host's own navigation. No-op-friendly: only meaningful while [SessionState.Hosting].
     */
    suspend fun startSharedSession(gameId: String): Result<String>

    /** Client-side: this device's own canonical (host-assigned) seat profile id, or null on the host/idle. */
    fun ownSeatUnitId(): String?

    /** Claim [unitId] in [roundId] for this device (host arbitrates; result arrives via [roundStatus]). */
    suspend fun requestLock(roundId: String, unitId: String)

    /** Voluntarily give up a lock this device holds (without finishing the unit). */
    suspend fun releaseLock(roundId: String, unitId: String)

    /** Force-release a unit locked by another device (manual "Übernehmen"). */
    suspend fun forceUnlock(roundId: String, unitId: String)

    /** Mark [unitId] finished (captured): the host records it done and re-broadcasts [roundStatus]. */
    suspend fun markUnitDone(roundId: String, unitId: String)

    /** Propagate a freshly captured player hand to the other devices' mirrors (Stage B live sync). */
    suspend fun pushHandCards(roundId: String, unitId: String, cards: List<HandCardSyncData>)

    /** Propagate the freshly captured Mittelfeld (discard pile) to the other devices' mirrors. */
    suspend fun pushDiscard(roundId: String, cards: List<String>)

    /**
     * Host: every unit of [roundId] is captured — distribute the canonical mirror ([FullGameState]) and
     * signal [SyncMessage.RoundComplete] so all devices open the reveal together (host is the sole
     * scoring authority). No-op on a client.
     */
    suspend fun finishRound(roundId: String)

    /**
     * Host: the game [gameId] is finished — distribute the final [FullGameState] and a
     * [SyncMessage.GameClosed] so every client merges the complete game and reattributes its own seat to
     * its local owner profile. No-op on a client.
     */
    suspend fun closeSharedGame(gameId: String)

    /**
     * Client: connect to the host at [macAddress] (resolved via CompanionDeviceManager) using the
     * handshake [payload]. Announces this device as [selfParticipant] (the joiner's own player) and
     * starts receiving roster updates.
     */
    suspend fun connectToHost(
        payload: HandshakePayload,
        macAddress: String,
        selfParticipant: ParticipantInfo,
    ): Result<Unit>

    /** Tear down the session (close all sockets), returning to [SessionState.Idle]. */
    fun close()
}
