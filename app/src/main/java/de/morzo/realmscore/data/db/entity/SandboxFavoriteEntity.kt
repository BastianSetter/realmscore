package de.morzo.realmscore.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A saved Sandbox hand (Phase 22). The cards are stored as a JSON-serialized list of
 * `FavoriteCardDto` in [cardsJson]; this keeps the variable-length hand in a single row without a
 * child table. Sync-ready: UUID primary key + origin device + timestamps.
 */
@Entity(tableName = "sandbox_favorites")
data class SandboxFavoriteEntity(
    @PrimaryKey val id: String,
    val number: Int,
    val cardsJson: String,
    val createdAt: Long,
    val updatedAt: Long,
    val originDeviceId: String,
)
