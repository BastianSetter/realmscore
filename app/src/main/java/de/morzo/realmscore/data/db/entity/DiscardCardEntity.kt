package de.morzo.realmscore.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One card observed in the central discard area (Mittelfeld) of a round. Captured manually via the
 * DiscardEntryScreen (Phase 20). Used for card statistics and to scope the Necromancer's pull to
 * the actually-discarded cards. Sync-ready: UUID primary key + origin device + timestamps.
 */
@Entity(
    tableName = "discard_cards",
    foreignKeys = [
        ForeignKey(
            entity = RoundEntity::class,
            parentColumns = ["id"],
            childColumns = ["roundId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("roundId")],
)
data class DiscardCardEntity(
    @PrimaryKey val id: String,
    val roundId: String,
    val cardKey: String,
    val position: Int,
    val createdAt: Long,
    val updatedAt: Long,
    val originDeviceId: String,
)
