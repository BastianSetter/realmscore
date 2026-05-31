package de.morzo.realmscore.domain.scoring.rules.common

import de.morzo.realmscore.domain.scoring.CardMatcher
import de.morzo.realmscore.domain.scoring.CardScoringRule
import de.morzo.realmscore.domain.scoring.EffectApplication
import de.morzo.realmscore.domain.scoring.ResolvedCard
import de.morzo.realmscore.domain.scoring.ScoringContext

/**
 * "-amount when no card matching matcher is in hand" — Knights, Dragon, etc.
 * Respects full cancellation. The matcher considers non-blanked hand only.
 *
 * amount should be positive; it is subtracted internally.
 */
class FlatPenaltyIfMissingRule(
    private val matcher: CardMatcher,
    private val amount: Int,
    private val descriptionKey: String,
) : CardScoringRule {

    override fun penalties(self: ResolvedCard, ctx: ScoringContext): List<EffectApplication> {
        if (ctx.penaltyContext?.isFullyCancelled(self) == true) return emptyList()
        val others = ctx.nonBlankedHand().filter { it.originalKey != self.originalKey }
        if (others.any { matcher.matches(it) }) return emptyList()
        return listOf(
            EffectApplication(
                sourceCardKey = self.originalKey,
                descriptionKey = descriptionKey,
                pointsDelta = -amount,
            )
        )
    }
}
