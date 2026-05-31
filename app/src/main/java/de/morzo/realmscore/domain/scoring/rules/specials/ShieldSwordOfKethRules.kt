package de.morzo.realmscore.domain.scoring.rules.specials

import de.morzo.realmscore.domain.model.Suit
import de.morzo.realmscore.domain.scoring.CardScoringRule
import de.morzo.realmscore.domain.scoring.EffectApplication
import de.morzo.realmscore.domain.scoring.ResolvedCard
import de.morzo.realmscore.domain.scoring.ScoringContext

/**
 * Schild von Keth: +15 with any Leader, +40 with Leader AND Schwert von Keth.
 * Schwert von Keth: +10 with any Leader, +40 with Leader AND Schild von Keth.
 * Both pick BEST tier (not both).
 */
object ShieldOfKethRule : CardScoringRule {
    override fun bonuses(self: ResolvedCard, ctx: ScoringContext): List<EffectApplication> =
        kethTierBonus(self, ctx, partnerKey = "sword_of_keth", lowAmount = 15)
}

object SwordOfKethRule : CardScoringRule {
    override fun bonuses(self: ResolvedCard, ctx: ScoringContext): List<EffectApplication> =
        kethTierBonus(self, ctx, partnerKey = "shield_of_keth", lowAmount = 10)
}

private fun kethTierBonus(
    self: ResolvedCard,
    ctx: ScoringContext,
    partnerKey: String,
    lowAmount: Int,
): List<EffectApplication> {
    val pool = ctx.nonBlankedHand().filter { it.originalKey != self.originalKey }
    val hasLeader = pool.any { it.effectiveSuit == Suit.LEADER }
    if (!hasLeader) return emptyList()
    val hasPartner = pool.any { it.effectiveCardKey == partnerKey }
    val amount = if (hasPartner) 40 else lowAmount
    val descKey = if (hasPartner) "effect_keth_combo" else "effect_keth_leader_only"
    return listOf(
        EffectApplication(
            sourceCardKey = self.originalKey,
            descriptionKey = descKey,
            pointsDelta = amount,
        )
    )
}
