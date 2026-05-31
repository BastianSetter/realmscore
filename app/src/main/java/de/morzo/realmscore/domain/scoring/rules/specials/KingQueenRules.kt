package de.morzo.realmscore.domain.scoring.rules.specials

import de.morzo.realmscore.domain.model.Suit
import de.morzo.realmscore.domain.scoring.CardScoringRule
import de.morzo.realmscore.domain.scoring.EffectApplication
import de.morzo.realmscore.domain.scoring.ResolvedCard
import de.morzo.realmscore.domain.scoring.ScoringContext

/** König: +5 per Army; upgrades to +20 per Army if Queen also in non-blanked hand. */
object KingRule : CardScoringRule {
    override fun bonuses(self: ResolvedCard, ctx: ScoringContext): List<EffectApplication> =
        royalArmyBonus(self, ctx, partnerKey = "queen", descriptionKey = "effect_king_armies")
}

/** Königin: +5 per Army; upgrades to +20 per Army if King also in non-blanked hand. */
object QueenRule : CardScoringRule {
    override fun bonuses(self: ResolvedCard, ctx: ScoringContext): List<EffectApplication> =
        royalArmyBonus(self, ctx, partnerKey = "king", descriptionKey = "effect_queen_armies")
}

private fun royalArmyBonus(
    self: ResolvedCard,
    ctx: ScoringContext,
    partnerKey: String,
    descriptionKey: String,
): List<EffectApplication> {
    val pool = ctx.nonBlankedHand().filter { it.originalKey != self.originalKey }
    val armyCount = pool.count { it.effectiveSuit == Suit.ARMY }
    if (armyCount == 0) return emptyList()
    val partnerPresent = pool.any { it.effectiveCardKey == partnerKey }
    val per = if (partnerPresent) 20 else 5
    return listOf(
        EffectApplication(
            sourceCardKey = self.originalKey,
            descriptionKey = descriptionKey,
            descriptionArgs = listOf(per.toString(), armyCount.toString()),
            pointsDelta = per * armyCount,
        )
    )
}
