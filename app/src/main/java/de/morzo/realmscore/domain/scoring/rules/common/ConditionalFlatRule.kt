package de.morzo.realmscore.domain.scoring.rules.common

import de.morzo.realmscore.domain.scoring.CardScoringRule
import de.morzo.realmscore.domain.scoring.EffectApplication
import de.morzo.realmscore.domain.scoring.HandCondition
import de.morzo.realmscore.domain.scoring.ResolvedCard
import de.morzo.realmscore.domain.scoring.ScoringContext

/**
 * "+amount if condition holds". Uses non-blanked hand for the check (rules of the game).
 */
class ConditionalFlatRule(
    private val condition: HandCondition,
    private val amount: Int,
    private val descriptionKey: String,
) : CardScoringRule {

    override fun bonuses(self: ResolvedCard, ctx: ScoringContext): List<EffectApplication> {
        if (!condition.evaluate(ctx.nonBlankedHand(), self)) return emptyList()
        return listOf(
            EffectApplication(
                sourceCardKey = self.originalKey,
                descriptionKey = descriptionKey,
                pointsDelta = amount,
            )
        )
    }
}
