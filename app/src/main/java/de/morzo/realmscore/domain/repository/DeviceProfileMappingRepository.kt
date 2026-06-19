package de.morzo.realmscore.domain.repository

import kotlinx.coroutines.flow.Flow

/**
 * Persistent reconciliation between foreign device UUIDs and local profile ids (Phase 28). Used by
 * the profile-reconciliation UI so a player's device is remembered across sessions.
 */
interface DeviceProfileMappingRepository {

    /** Map (or re-map) [deviceId] to local [profileId]. */
    suspend fun map(deviceId: String, profileId: String)

    /** The local profile previously mapped to [deviceId], or null. */
    suspend fun getProfileFor(deviceId: String): String?

    /** Live view of all mappings as deviceId → profileId. */
    fun observeAll(): Flow<Map<String, String>>

    suspend fun remove(deviceId: String)
}
