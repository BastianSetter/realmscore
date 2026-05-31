package de.morzo.realmscore.domain.scoring.penalty

import de.morzo.realmscore.domain.model.Suit
import de.morzo.realmscore.domain.scoring.ResolvedCard

/**
 * Snapshot of all penalty cancellations active after blanking has been resolved.
 * Rule authors query this to decide whether their own penalty fires and which suits
 * to ignore when counting.
 */
class PenaltyContext(private val cancellations: List<PenaltyCancellation>) {

    /** True iff the card's whole penalty is cancelled (Rune / Höhle / Mountain / Beastmaster / Insel). */
    fun isFullyCancelled(card: ResolvedCard): Boolean {
        return cancellations.any { c ->
            when (c) {
                is PenaltyCancellation.CancelAll -> true
                is PenaltyCancellation.CancelBySuit -> card.effectiveSuit in c.targetSuits
                is PenaltyCancellation.CancelOneOf -> c.targetCardKey != null && c.targetCardKey == card.originalKey
                is PenaltyCancellation.StripSuitWord -> false
            }
        }
    }

    /**
     * Suits that should be excluded from a per-card counting penalty on [card]
     * (e.g. Sumpf's "-3 per Army/Flame" with Rangers in hand → strippedSuits = {ARMY}).
     */
    fun strippedSuitsFor(card: ResolvedCard): Set<Suit> {
        val result = mutableSetOf<Suit>()
        for (c in cancellations) {
            if (c is PenaltyCancellation.StripSuitWord) {
                val inScope = c.scopeSuits == null || card.effectiveSuit in c.scopeSuits
                if (inScope) result += c.strippedSuits
            }
        }
        return result
    }
}
