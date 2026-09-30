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
    /** Phase 30: expansion part 1 — players record the cursed items they used (fixed points). */
    val cursedItemsEnabled: Boolean = false,
    /** Phase 30: expansion part 2 — new suits (Building/Outsider/Undead), 8-card hands, bigger Mittelfeld. */
    val newSuitsEnabled: Boolean = false,
)

enum class GameMode { FIXED_ROUNDS, POINT_LIMIT }

enum class ClosedReason { COMPLETED, ABANDONED }
