package de.morzo.realmscore.data.p2p

import de.morzo.realmscore.domain.p2p.model.SyncMessage
import kotlinx.coroutines.flow.Flow

/**
 * A single point-to-point link to one peer (Phase 28). The host holds one [P2PConnection] per
 * connected client; a client holds exactly one (to the host). Transport-agnostic so the session
 * logic never touches Bluetooth APIs directly.
 */
interface P2PConnection {

    /** Human-readable name of the peer (for "Gerät von Tom verbunden"), if known. */
    val remoteDeviceName: String?

    /** Sends one message (one newline-terminated JSON line). Suspends on the IO dispatcher. */
    suspend fun send(message: SyncMessage)

    /**
     * Cold flow of inbound messages. Collecting it blocks on the socket's input stream; the flow
     * completes when the peer closes or the link drops. Malformed lines are skipped, not fatal.
     */
    fun receive(): Flow<SyncMessage>

    fun isConnected(): Boolean

    fun close()
}
