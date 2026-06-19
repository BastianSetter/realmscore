package de.morzo.realmscore.data.p2p

import android.graphics.Bitmap
import de.morzo.realmscore.domain.p2p.model.HandshakePayload
import java.util.UUID
import kotlin.random.Random

/**
 * Builds and verifies the out-of-band handshake (Phase 28).
 *
 * **Host** opens a session ([openHostSession]): we mint a random session token and a short 6-digit
 * code, wrap them with the host's Bluetooth name + game id into a [HandshakePayload], and expose it
 * both as a QR code ([qrBitmap]) and as the printed 6-digit code.
 *
 * **Client** either scans the QR ([parseScannedPayload]) — getting the full payload incl. the host
 * Bluetooth name to feed the CompanionDeviceManager filter — or, with no camera, picks the host in
 * the CDM device list and types the 6-digit code, which the host validates in-band ([isValidCode]).
 */
class HandshakeManager {

    /** Active host session state, or null when this device is not hosting. */
    @Volatile
    var current: HostSession? = null
        private set

    data class HostSession(
        val payload: HandshakePayload,
        val sessionCode: String,
    )

    /** Host: start advertising [gameId]. [hostBluetoothName] comes from the local BT adapter. */
    fun openHostSession(
        gameId: String,
        hostDeviceId: String,
        hostBluetoothName: String,
    ): HostSession {
        val session = HostSession(
            payload = HandshakePayload(
                hostDeviceId = hostDeviceId,
                hostBluetoothName = hostBluetoothName,
                gameId = gameId,
                sessionToken = UUID.randomUUID().toString(),
            ),
            sessionCode = generateSessionCode(),
        )
        current = session
        return session
    }

    fun closeHostSession() {
        current = null
    }

    /** The QR content the host displays (the serialized payload). */
    fun qrContent(session: HostSession): String =
        SyncProtocol.encodeHandshake(session.payload)

    /** Renders the host's QR code to a [Bitmap] for display. */
    fun qrBitmap(session: HostSession, sizePx: Int): Bitmap =
        QrCodeHelper.generate(qrContent(session), sizePx)

    /** Client: turn raw scanned QR text into a [HandshakePayload], or null if it isn't ours. */
    fun parseScannedPayload(scannedText: String): HandshakePayload? = try {
        SyncProtocol.decodeHandshake(scannedText)
    } catch (_: Exception) {
        null
    }

    /** Host: verify a 6-digit code typed by a camera-less client. */
    fun isValidCode(code: String): Boolean =
        current?.sessionCode == code.trim()

    /** Host: verify a session token echoed back by a connecting client. */
    fun isValidToken(token: String): Boolean =
        current?.payload?.sessionToken == token

    private fun generateSessionCode(): String =
        Random.nextInt(0, 1_000_000).toString().padStart(CODE_LENGTH, '0')

    companion object {
        const val CODE_LENGTH = 6
    }
}
