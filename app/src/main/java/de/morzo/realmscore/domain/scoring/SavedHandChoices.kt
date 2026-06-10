package de.morzo.realmscore.domain.scoring

import de.morzo.realmscore.domain.model.Suit
import de.morzo.realmscore.domain.repository.HandCardEntry

/** Card keys whose persisted [HandCardEntry.jokerTargetCardKey] is a [PlayerChoices] value, not a joker. */
private const val NECROMANCER_KEY = "necromancer"
private const val ISLAND_KEY = "island"
private const val FOUNTAIN_KEY = "fountain_of_life"

private val CHOICE_KEYS = setOf(NECROMANCER_KEY, ISLAND_KEY, FOUNTAIN_KEY)

/** Result of reconstructing the scoring inputs from a saved hand. */
data class ReconstructedChoices(
    val jokerAssignments: Map<String, JokerAssignment>,
    val playerChoices: PlayerChoices,
)

/**
 * Rebuilds the [JokerAssignment]s and [PlayerChoices] from a persisted hand. The Necromancer pick
 * and the Island/Fountain choices reuse the [HandCardEntry.jokerTargetCardKey] column on their own
 * card entry, so they must be mapped back into [PlayerChoices] rather than treated as joker
 * assignments. This is the single source of truth shared by the capture, reveal and breakdown paths
 * so the same hand always yields the same score *and* the same breakdown.
 */
fun List<HandCardEntry>.toScoringChoices(): ReconstructedChoices {
    val jokerAssignments = asSequence()
        .filter { it.cardKey !in CHOICE_KEYS }
        .mapNotNull { entry ->
            val target = entry.jokerTargetCardKey ?: return@mapNotNull null
            val suit = entry.jokerTargetSuit?.let { runCatching { Suit.valueOf(it) }.getOrNull() }
            entry.cardKey to JokerAssignment(
                jokerKey = entry.cardKey,
                targetCardKey = target,
                targetSuit = suit,
            )
        }.toMap()
    val playerChoices = PlayerChoices(
        islandTargetKey = firstOrNull { it.cardKey == ISLAND_KEY }?.jokerTargetCardKey,
        fountainSourceKey = firstOrNull { it.cardKey == FOUNTAIN_KEY }?.jokerTargetCardKey,
        necromancerPickKey = firstOrNull { it.cardKey == NECROMANCER_KEY }?.jokerTargetCardKey,
    )
    return ReconstructedChoices(jokerAssignments, playerChoices)
}
