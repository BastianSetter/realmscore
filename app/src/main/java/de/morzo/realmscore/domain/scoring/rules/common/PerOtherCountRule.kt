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
        val matched = matchedKeys(self, ctx, strippedSuits = emptySet())
        if (matched.isEmpty()) return emptyList()
        return listOf(line(self, matched))
    }

    override fun penalties(self: ResolvedCard, ctx: ScoringContext): List<EffectApplication> {
        if (!isPenalty) return emptyList()
        val pc = ctx.penaltyContext
        if (pc != null && pc.isFullyCancelled(self)) return emptyList()
        val stripped = pc?.strippedSuitsFor(self).orEmpty()
        val matched = matchedKeys(self, ctx, strippedSuits = stripped)
        if (matched.isEmpty()) return emptyList()
        return listOf(line(self, matched))
    }

    private fun matchedKeys(
        self: ResolvedCard,
        ctx: ScoringContext,
        strippedSuits: Set<de.morzo.realmscore.domain.model.Suit>,
    ): List<String> {
        return ctx.nonBlankedHand().mapNotNull { c ->
            if (excludeSelf && c.originalKey == self.originalKey) return@mapNotNull null
            if (c.effectiveSuit in strippedSuits) return@mapNotNull null
            if (matcher.matches(c)) c.originalKey else null
        }
    }

    private fun line(self: ResolvedCard, matched: List<String>): EffectApplication = EffectApplication(
        sourceCardKey = self.originalKey,
        descriptionKey = descriptionKey,
        descriptionArgs = listOf(matched.size.toString()),
        pointsDelta = amountPer * matched.size,
        contributingCardKeys = matched,
    )
}
