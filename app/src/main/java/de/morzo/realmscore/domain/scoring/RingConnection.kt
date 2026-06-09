package de.morzo.realmscore.domain.scoring

import de.morzo.realmscore.domain.model.CardDefinition

/**
 * A directed influence between two hand cards, used by the ring visualization (Phase 18).
 *
 * Indices reference the position in the `cards` list handed to [RingLayoutOptimizer] /
 * `HandRingView`. [weight] is the net point effect the source card exerts on the target card:
 * positive = bonus (green), negative = penalty (red).
 */
data class RingConnection(
    val fromCardIdx: Int,
    val toCardIdx: Int,
    val weight: Int,
    val isBlanking: Boolean = false,
)

/**
 * Derives the ring connections from a [ScoringResult] for the given hand `cards`.
 *
 * Each [EffectApplication.contributingCardKeys] lists the *other* cards that caused the effect
 * (the Lands a Ranger counts, the Queen that upgrades a King, …). Every such card forms a directed
 * edge `contributor → owner`, where `owner` is the card the effect is scored under. The effect's
 * `pointsDelta` is split evenly across its contributors so the per-edge weights sum back to the
 * effect total. Edges sharing the same (from, to) pair are aggregated. Effects without contributors
 * (base strength, absence penalties) and endpoints outside `cards` (e.g. the Necromancer's 8th
 * card) produce no edge.
 */
fun buildRingConnections(
    cards: List<CardDefinition>,
    scoringResult: ScoringResult,
): List<RingConnection> {
    val idxByKey = cards.mapIndexed { idx, card -> card.key to idx }.toMap()
    val summed = LinkedHashMap<Pair<Int, Int>, Int>()
    scoringResult.perCard.forEach { cardResult ->
        val toIdx = idxByKey[cardResult.cardKey] ?: return@forEach
        cardResult.effects.forEach { effect ->
            val contributors = effect.contributingCardKeys.mapNotNull { idxByKey[it] }
                .filter { it != toIdx }
            if (contributors.isEmpty()) return@forEach
            // Split the effect total evenly across its contributors (largest remainder first).
            val shares = splitEvenly(effect.pointsDelta, contributors.size)
            contributors.forEachIndexed { i, fromIdx ->
                val pair = fromIdx to toIdx
                summed[pair] = (summed[pair] ?: 0) + shares[i]
            }
        }
    }
    return summed.mapNotNull { (pair, weight) ->
        if (weight == 0) null
        else RingConnection(fromCardIdx = pair.first, toCardIdx = pair.second, weight = weight)
    }
}

/** Splits [total] into [parts] near-equal integer shares that sum back to [total]. */
private fun splitEvenly(total: Int, parts: Int): IntArray {
    if (parts <= 0) return IntArray(0)
    val base = total / parts
    val remainder = total - base * parts // signed; carries the sign of total
    val step = if (remainder >= 0) 1 else -1
    return IntArray(parts) { i ->
        if (i < kotlin.math.abs(remainder)) base + step else base
    }
}
