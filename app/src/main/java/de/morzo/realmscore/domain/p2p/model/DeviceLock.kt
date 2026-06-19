package de.morzo.realmscore.domain.p2p.model

/**
 * An active optimistic lock: device [deviceId] is currently entering cards for [profileId] in
 * [roundId]. Held in-memory by the LockManager and surfaced to the capture UI so other devices show
 * "wird bearbeitet von …".
 */
data class DeviceLock(
    val roundId: String,
    val profileId: String,
    val deviceId: String,
) {
    companion object {
        /** Stable key for the lock map: one lock per (round, player). */
        fun key(roundId: String, profileId: String): String = "$roundId:$profileId"
    }
}
