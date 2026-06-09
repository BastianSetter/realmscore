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
    val armies = pool.filter { it.effectiveSuit == Suit.ARMY }
    val armyCount = armies.size
    if (armyCount == 0) return emptyList()
    val partner = pool.firstOrNull { it.effectiveCardKey == partnerKey }
    val per = if (partner != null) 20 else 5
    // The armies drive the bonus; the partner royal that upgrades it counts too.
    val contributors = armies.map { it.originalKey } + listOfNotNull(partner?.originalKey)
    return listOf(
        EffectApplication(
            sourceCardKey = self.originalKey,
            descriptionKey = descriptionKey,
            descriptionArgs = listOf(per.toString(), armyCount.toString()),
            pointsDelta = per * armyCount,
            contributingCardKeys = contributors,
        )
    )
}
