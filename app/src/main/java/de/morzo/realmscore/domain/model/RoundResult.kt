package de.morzo.realmscore.domain.model

data class RoundResult(
    val id: String,
    val roundId: String,
    val profileId: String,
    val totalScore: Int,
    val createdAt: Long,
    val updatedAt: Long,
    val originDeviceId: String,
    /** Phase 30: summed points of the cursed items used this round (already included in [totalScore]). */
    val cursedPoints: Int = 0,
    /** Phase 30: keys of the cursed items used this round. */
    val cursedItemKeys: List<String> = emptyList(),
)

/** Score of the cards alone, without the cursed items (Phase 30) — for hand-quality statistics. */
val RoundResult.handScore: Int get() = totalScore - cursedPoints
