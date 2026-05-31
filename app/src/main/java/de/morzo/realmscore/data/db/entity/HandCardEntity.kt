package de.morzo.realmscore.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import de.morzo.realmscore.domain.model.HandCard

@Entity(
    tableName = "hand_cards",
    foreignKeys = [
        ForeignKey(
            entity = RoundResultEntity::class,
            parentColumns = ["id"],
            childColumns = ["roundResultId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("roundResultId")],
)
data class HandCardEntity(
    @PrimaryKey val id: String,
    val roundResultId: String,
    val cardKey: String,
    val position: Int,
    val jokerTargetCardKey: String?,
    val jokerTargetSuit: String?,
    val createdAt: Long,
    val updatedAt: Long,
) {
    fun toDomain(): HandCard = HandCard(
        id = id,
        roundResultId = roundResultId,
        cardKey = cardKey,
        position = position,
        jokerTargetCardKey = jokerTargetCardKey,
        jokerTargetSuit = jokerTargetSuit,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    companion object {
        fun fromDomain(card: HandCard): HandCardEntity = HandCardEntity(
            id = card.id,
            roundResultId = card.roundResultId,
            cardKey = card.cardKey,
            position = card.position,
            jokerTargetCardKey = card.jokerTargetCardKey,
            jokerTargetSuit = card.jokerTargetSuit,
            createdAt = card.createdAt,
            updatedAt = card.updatedAt,
        )
    }
}
