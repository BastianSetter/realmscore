package de.morzo.realmscore.domain.scoring.rules.specials

import de.morzo.realmscore.domain.scoring.CardScoringRule
import de.morzo.realmscore.domain.scoring.EffectApplication
import de.morzo.realmscore.domain.scoring.ResolvedCard
import de.morzo.realmscore.domain.scoring.ScoringContext

/** Weltenbaum: +50 if every non-blanked card has a unique effective suit. */
object WorldTreeRule : CardScoringRule {
    override fun bonuses(self: ResolvedCard, ctx: ScoringContext): List<EffectApplication> {
        val cards = ctx.nonBlankedHand()
        val suits = cards.map { it.effectiveSuit }
        if (suits.size < 2 || suits.size != suits.toSet().size) return emptyList()
        return listOf(
            EffectApplication(
                sourceCardKey = self.originalKey,
                descriptionKey = "effect_world_tree",
                pointsDelta = 50,
            )
        )
    }
}
