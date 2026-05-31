package de.morzo.realmscore.domain.scoring.blanking

import de.morzo.realmscore.domain.scoring.CardScoringRule
import de.morzo.realmscore.domain.scoring.ResolvedCard
import de.morzo.realmscore.domain.scoring.ScoringContext

/**
 * Computes which originalKeys are blanked, by fixpoint iteration.
 *
 * Two kinds of blanking:
 *   • SelfBlank ("ist blockiert wenn …") — the source card blanks itself if its condition
 *     holds on the **currently-active** (non-blanked) hand. A self-blanker still self-blanks
 *     even if it's already in the blanked set (otherwise we oscillate).
 *   • Blank-others (BlankBySuit / BlankBySuitExcept / BlankAllExcept) — the source only
 *     emits these while it is itself **not** blanked.
 *
 * Base-game cycles are impossible, so the loop converges within a few rounds; we cap at 10.
 */
class BlankingResolver(
    private val ruleFor: (String) -> CardScoringRule?,
) {

    fun resolve(initialContext: ScoringContext): Set<String> {
        val hand = initialContext.hand
        var blanked: Set<String> = emptySet()
        repeat(MAX_ROUNDS) {
            val ctxThisRound = initialContext.copy(blankedKeys = blanked)
            val newBlanked = computeBlanked(hand, ctxThisRound)
            if (newBlanked == blanked) return blanked
            blanked = newBlanked
        }
        return blanked
    }

    private fun computeBlanked(
        hand: List<ResolvedCard>,
        ctx: ScoringContext,
    ): Set<String> {
        // Pass conditions a hand that doesn't include already-blanked cards.
        val activeHand = hand.filter { it.originalKey !in ctx.blankedKeys }
        val result = mutableSetOf<String>()
        for (target in hand) {
            for (source in hand) {
                if (!source.penaltyEnabled) continue
                val rule = ruleFor(source.effectiveCardKey) ?: continue
                val effects = rule.blanking(source, ctx)
                for (effect in effects) {
                    val isSelf = effect is BlankingEffect.SelfBlank
                    // Blank-others effects from a blanked source are inert.
                    if (!isSelf && source.originalKey in ctx.blankedKeys) continue
                    if (effect.blanks(self = source, target = target, hand = activeHand)) {
                        result += target.originalKey
                        break
                    }
                }
                if (target.originalKey in result) break
            }
        }
        return result
    }

    companion object {
        private const val MAX_ROUNDS = 10
    }
}
