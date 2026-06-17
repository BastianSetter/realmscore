package de.morzo.realmscore.domain.scoring

import de.morzo.realmscore.domain.model.CardDefinition

/**
 * A directed influence between two hand cards, used by the ring visualization (Phase 18).
 *
 * Indices reference the position in the `cards` list handed to [RingLayoutOptimizer] /
 * `HandRingView`. [weight] is the net point effect the source card exerts on the target card:
 * positive = bonus (green), negative = penalty (red).
 *
 * When [isBlanking] is true the edge represents `source blanks target`; its [weight] is 0 (a
 * blanked card scores nothing) and it is rendered as a black line.
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
            val explicit = effect.contributorWeights
                ?.takeIf { it.size == effect.contributingCardKeys.size }
            if (explicit != null) {
                // Per-contributor weights given (e.g. Warlord): each key keeps its own weight.
                effect.contributingCardKeys.forEachIndexed { i, key ->
                    val fromIdx = idxByKey[key] ?: return@forEachIndexed
                    if (fromIdx == toIdx) return@forEachIndexed
                    val pair = fromIdx to toIdx
                    summed[pair] = (summed[pair] ?: 0) + explicit[i]
                }
                return@forEach
            }
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
    val edges = summed.mapNotNull { (pair, weight) ->
        if (weight == 0) null
        else RingConnection(fromCardIdx = pair.first, toCardIdx = pair.second, weight = weight)
    }.toMutableList()

    // Blanking edges (black line source → target). A blanked card has no other edges, so this is
    // the only line that ties it back into the ring.
    scoringResult.blankedBy.forEach { (targetKey, sourceKeys) ->
        val toIdx = idxByKey[targetKey] ?: return@forEach
        sourceKeys.forEach { sourceKey ->
            val fromIdx = idxByKey[sourceKey] ?: return@forEach
            if (fromIdx == toIdx) return@forEach
            edges += RingConnection(
                fromCardIdx = fromIdx,
                toCardIdx = toIdx,
                weight = 0,
                isBlanking = true,
            )
        }
    }
    return edges
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
