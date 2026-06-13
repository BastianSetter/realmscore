package de.morzo.realmscore.domain.scoring

import de.morzo.realmscore.domain.model.HandCard
import de.morzo.realmscore.domain.model.Suit
import de.morzo.realmscore.domain.repository.HandCardEntry

/** Result of reconstructing the scoring inputs from a saved hand. */
data class ReconstructedChoices(
    val jokerAssignments: Map<String, JokerAssignment>,
)

/**
 * Rebuilds the [JokerAssignment]s from a persisted hand. Every card stores its chosen target in the
 * `jokerTargetCardKey` column on its own entry — substitution jokers, the Island (Flood/Flame to
 * cancel), the Fountain of Life (source to copy) **and the Necromancer** (the discard card it pulls,
 * spec 25.4) all map back into [JokerAssignment]s keyed by their card key. Since the Necromancer is a
 * hand card, its pull was already persisted in the same column, so no DB migration is needed — only
 * the interpretation changed: it is no longer routed into a separate PlayerChoices field.
 *
 * This is the single source of truth shared by the capture, reveal, breakdown **and stats** paths so
 * the same hand always yields the same score *and* the same breakdown. Both the repository DTO
 * [HandCardEntry] and the persisted domain [HandCard] feed the same [reconstructScoringChoices] core
 * (Phase 24, H1/L1).
 */
fun List<HandCardEntry>.toScoringChoices(): ReconstructedChoices =
    map { SavedCardChoice(it.cardKey, it.jokerTargetCardKey, it.jokerTargetSuit) }
        .reconstructScoringChoices()

/** Same reconstruction for persisted domain [HandCard]s (used by the stats re-scoring path). */
@JvmName("toScoringChoicesFromHandCards")
fun List<HandCard>.toScoringChoices(): ReconstructedChoices =
    map { SavedCardChoice(it.cardKey, it.jokerTargetCardKey, it.jokerTargetSuit) }
        .reconstructScoringChoices()

/** Minimal projection of a persisted hand card: just the fields that drive scoring choices. */
private data class SavedCardChoice(
    val cardKey: String,
    val jokerTargetCardKey: String?,
    val jokerTargetSuit: String?,
)

private fun List<SavedCardChoice>.reconstructScoringChoices(): ReconstructedChoices {
    val jokerAssignments = asSequence()
        .mapNotNull { entry ->
            val target = entry.jokerTargetCardKey ?: return@mapNotNull null
            val suit = entry.jokerTargetSuit?.let { runCatching { Suit.valueOf(it) }.getOrNull() }
            entry.cardKey to JokerAssignment(
                jokerKey = entry.cardKey,
                targetCardKey = target,
                targetSuit = suit,
            )
        }.toMap()
    return ReconstructedChoices(jokerAssignments)
}
