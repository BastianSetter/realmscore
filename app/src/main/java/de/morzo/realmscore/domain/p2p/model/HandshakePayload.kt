package de.morzo.realmscore.domain.p2p.model

import kotlinx.serialization.Serializable

/**
 * Connection info the host hands to a joining client during the P2P handshake (Phase 28). Encoded as
 * JSON inside the QR code (and recoverable via the 6-digit session code).
 *
 * **Why a Bluetooth *name*, not a MAC:** since Android 6 an app cannot read its own Bluetooth MAC
 * (`BluetoothAdapter.getAddress()` returns the dummy `02:00:00:00:00:00`), so the host can't put its
 * real address into the QR. Instead the host advertises its [hostBluetoothName]; the client feeds
 * that as a name filter to the **CompanionDeviceManager**, which lets the system find and associate
 * the device (no `ACCESS_FINE_LOCATION` needed, no in-app scanning). The resolved MAC from the
 * association is then used to open the RFCOMM socket.
 *
 * The [sessionToken] is a random UUID the client echoes back so the host can verify the join belongs
 * to the QR it just showed. [gameId] identifies the game being shared.
 */
@Serializable
data class HandshakePayload(
    val hostDeviceId: String,
    val hostBluetoothName: String,
    val gameId: String,
    val sessionToken: String,
)
