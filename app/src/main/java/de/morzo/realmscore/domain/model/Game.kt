package de.morzo.realmscore.domain.model

data class Game(
    val id: String,
    val displayName: String?,
    val mode: GameMode,
    val targetRounds: Int?,
    val targetPoints: Int?,
    val startedAt: Long,
    val closedAt: Long?,
    val closedReason: ClosedReason?,
    val createdAt: Long,
    val updatedAt: Long,
    val originDeviceId: String,
)

enum class GameMode { FIXED_ROUNDS, POINT_LIMIT }

enum class ClosedReason { COMPLETED, ABANDONED }
