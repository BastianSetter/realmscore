package de.morzo.realmscore.domain.scoring.rules.specials

import de.morzo.realmscore.domain.model.Suit
import de.morzo.realmscore.domain.scoring.CardScoringRule
import de.morzo.realmscore.domain.scoring.EffectApplication
import de.morzo.realmscore.domain.scoring.ResolvedCard
import de.morzo.realmscore.domain.scoring.ScoringContext

/** Kriegsherr: bonus = sum of base strengths of all non-blanked Army cards in hand. */
object WarlordRule : CardScoringRule {
    override fun bonuses(self: ResolvedCard, ctx: ScoringContext): List<EffectApplication> {
        val sum = ctx.nonBlankedHand()
            .filter { it.effectiveSuit == Suit.ARMY }
            .sumOf { it.effectiveStrength }
        if (sum == 0) return emptyList()
        return listOf(
            EffectApplication(
                sourceCardKey = self.originalKey,
                descriptionKey = "effect_warlord",
                descriptionArgs = listOf(sum.toString()),
                pointsDelta = sum,
            )
        )
    }
}
