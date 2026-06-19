package de.morzo.realmscore.domain.p2p

import de.morzo.realmscore.domain.p2p.model.BluetoothStatus
import de.morzo.realmscore.domain.p2p.model.HandshakePayload
import de.morzo.realmscore.domain.p2p.model.ParticipantInfo
import de.morzo.realmscore.domain.p2p.model.SessionState
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
