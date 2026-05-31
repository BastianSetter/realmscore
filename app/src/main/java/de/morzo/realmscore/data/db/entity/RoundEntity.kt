package de.morzo.realmscore.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import de.morzo.realmscore.domain.model.Round

@Entity(
    tableName = "rounds",
    foreignKeys = [
        ForeignKey(
            entity = GameEntity::class,
            parentColumns = ["id"],
            childColumns = ["gameId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("gameId")],
)
data class RoundEntity(
    @PrimaryKey val id: String,
    val gameId: String,
    val roundNumber: Int,
    val startedAt: Long,
    val completedAt: Long?,
    val discardScanned: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val originDeviceId: String,
) {
    fun toDomain(): Round = Round(
        id = id,
        gameId = gameId,
        roundNumber = roundNumber,
        startedAt = startedAt,
        completedAt = completedAt,
        discardScanned = discardScanned,
        createdAt = createdAt,
        updatedAt = updatedAt,
        originDeviceId = originDeviceId,
    )

    companion object {
        fun fromDomain(round: Round): RoundEntity = RoundEntity(
            id = round.id,
            gameId = round.gameId,
            roundNumber = round.roundNumber,
            startedAt = round.startedAt,
            completedAt = round.completedAt,
            discardScanned = round.discardScanned,
            createdAt = round.createdAt,
            updatedAt = round.updatedAt,
            originDeviceId = round.originDeviceId,
        )
    }
}
