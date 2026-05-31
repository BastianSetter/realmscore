package de.morzo.realmscore.domain.scoring.rules.common

import de.morzo.realmscore.domain.scoring.CardMatcher
import de.morzo.realmscore.domain.scoring.CardScoringRule
import de.morzo.realmscore.domain.scoring.EffectApplication
import de.morzo.realmscore.domain.scoring.ResolvedCard
import de.morzo.realmscore.domain.scoring.ScoringContext

/**
 * "+amountPer per (other) card matching matcher".
 *
 * If [isPenalty] is true, the rule contributes a penalty line item (negative amountPer)
 * AND respects PenaltyContext: full cancellation drops the whole effect; StripSuitWord
 * removes matched cards whose effective suit is in the stripped set from the count.
 */
class PerOtherCountRule(
    private val matcher: CardMatcher,
    private val amountPer: Int,
    private val descriptionKey: String,
    private val excludeSelf: Boolean = true,
    private val isPenalty: Boolean = false,
) : CardScoringRule {

    override fun bonuses(self: ResolvedCard, ctx: ScoringContext): List<EffectApplication> {
        if (isPenalty) return emptyList()
        val count = countMatches(self, ctx, strippedSuits = emptySet())
        if (count == 0) return emptyList()
        return listOf(line(self, count))
    }

    override fun penalties(self: ResolvedCard, ctx: ScoringContext): List<EffectApplication> {
        if (!isPenalty) return emptyList()
        val pc = ctx.penaltyContext
        if (pc != null && pc.isFullyCancelled(self)) return emptyList()
        val stripped = pc?.strippedSuitsFor(self).orEmpty()
        val count = countMatches(self, ctx, strippedSuits = stripped)
        if (count == 0) return emptyList()
        return listOf(line(self, count))
    }

    private fun countMatches(
        self: ResolvedCard,
        ctx: ScoringContext,
        strippedSuits: Set<de.morzo.realmscore.domain.model.Suit>,
    ): Int {
        return ctx.nonBlankedHand().count { c ->
            if (excludeSelf && c.originalKey == self.originalKey) return@count false
            if (c.effectiveSuit in strippedSuits) return@count false
            matcher.matches(c)
        }
    }

    private fun line(self: ResolvedCard, count: Int): EffectApplication = EffectApplication(
        sourceCardKey = self.originalKey,
        descriptionKey = descriptionKey,
        descriptionArgs = listOf(count.toString()),
        pointsDelta = amountPer * count,
    )
}
