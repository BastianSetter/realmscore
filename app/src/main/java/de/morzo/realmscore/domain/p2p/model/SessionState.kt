package de.morzo.realmscore.domain.p2p.model

/** Whether this device started the session (host) or joined an existing one (client). */
enum class SessionRole { HOST, CLIENT }

/** A peer currently connected to this device in a session. */
data class ConnectedDevice(
    val deviceId: String,
    val deviceName: String,
)

/**
 * High-level P2P session state exposed to the UI (Phase 28). Drives the "open for joins" host screen,
 * the join screen, and the connection-status indicators during play.
 */
sealed interface SessionState {

    /** No active session. */
    data object Idle : SessionState

    /** Host has opened the game for joins and is waiting for / accepting clients. */
    data class Hosting(
        val gameId: String,
        val payload: HandshakePayload,
        val sessionCode: String,
        val connectedDevices: List<ConnectedDevice> = emptyList(),
        val maxDevices: Int = MAX_DEVICES,
    ) : SessionState

    /** Client is in the middle of connecting to a host. */
    data object Connecting : SessionState

    /** Connected and synced. */
    data class Connected(
        val role: SessionRole,
        val gameId: String,
        val connectedDevices: List<ConnectedDevice> = emptyList(),
    ) : SessionState

    /** The link dropped; local play continues. Holds the last known role/game for reconnect. */
    data class Disconnected(
        val role: SessionRole,
        val gameId: String,
        val reason: String? = null,
    ) : SessionState

    /** A fatal setup error (Bluetooth off, permission denied, bad payload, …). */
    data class Error(val message: String) : SessionState

    companion object {
        /** Max simultaneous devices = max players (RFCOMM comfortably handles this). */
        const val MAX_DEVICES = 6
    }
}
