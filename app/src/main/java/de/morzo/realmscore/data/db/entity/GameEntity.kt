package de.morzo.realmscore.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import de.morzo.realmscore.domain.model.ClosedReason
import de.morzo.realmscore.domain.model.Game
import de.morzo.realmscore.domain.model.GameMode

@Entity(tableName = "games")
data class GameEntity(
    @PrimaryKey val id: String,
    val displayName: String?,
    val mode: String,
    val targetRounds: Int?,
    val targetPoints: Int?,
    val startedAt: Long,
    val closedAt: Long?,
    val closedReason: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val originDeviceId: String,
) {
    fun toDomain(): Game = Game(
        id = id,
        displayName = displayName,
        mode = GameMode.valueOf(mode),
        targetRounds = targetRounds,
        targetPoints = targetPoints,
        startedAt = startedAt,
        closedAt = closedAt,
        closedReason = closedReason?.let(ClosedReason::valueOf),
        createdAt = createdAt,
        updatedAt = updatedAt,
        originDeviceId = originDeviceId,
    )

    companion object {
        fun fromDomain(game: Game): GameEntity = GameEntity(
            id = game.id,
            displayName = game.displayName,
            mode = game.mode.name,
            targetRounds = game.targetRounds,
            targetPoints = game.targetPoints,
            startedAt = game.startedAt,
            closedAt = game.closedAt,
            closedReason = game.closedReason?.name,
            createdAt = game.createdAt,
            updatedAt = game.updatedAt,
            originDeviceId = game.originDeviceId,
        )
    }
}
