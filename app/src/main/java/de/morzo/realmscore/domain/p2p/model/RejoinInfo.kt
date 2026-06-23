package de.morzo.realmscore.domain.p2p.model

/**
 * Everything needed to silently reconnect a client to the host it last joined (Phase 28, §6 #2) —
 * the CompanionDeviceManager-resolved host [macAddress] and the original [payload]. Persisted on a
 * successful join and cleared when that game closes, so after an app kill / BT drop the "Session
 * erneut beitreten" button can re-run [P2PSessionRepository.connectToHost] without a fresh QR scan.
 */
data class RejoinInfo(
    val macAddress: String,
    val payload: HandshakePayload,
)
