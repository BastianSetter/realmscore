package de.morzo.realmscore.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Persistent reconciliation between a foreign device (its UUID, [deviceId]) and the **local** profile
 * the user mapped it to (Phase 28). Lets the app remember "Maria's phone is my local profile Maria"
 * across sessions, so a returning player is recognised without re-mapping.
 *
 * One row per foreign device — [deviceId] is the primary key (a device maps to exactly one local
 * profile).
 */
@Entity(tableName = "device_profile_mappings")
data class DeviceProfileMappingEntity(
    @PrimaryKey val deviceId: String,
    val profileId: String,
    val createdAt: Long,
)
