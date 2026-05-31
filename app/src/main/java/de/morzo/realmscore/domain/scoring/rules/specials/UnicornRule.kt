package de.morzo.realmscore.domain.scoring.rules.specials

import de.morzo.realmscore.domain.scoring.CardScoringRule
import de.morzo.realmscore.domain.scoring.EffectApplication
import de.morzo.realmscore.domain.scoring.ResolvedCard
import de.morzo.realmscore.domain.scoring.ScoringContext

/**
 * Einhorn:
 *   +30 with Princess; OR
 *   +15 with Empress or Queen or Enchantress
 *
 * Rules: take the BEST applicable tier (not both). Princess wins.
 */
object UnicornRule : CardScoringRule {
    override fun bonuses(self: ResolvedCard, ctx: ScoringContext): List<EffectApplication> {
        val keys = ctx.nonBlankedHand().map { it.effectiveCardKey }.toSet()
        return when {
            "princess" in keys -> listOf(
                EffectApplication(
                    sourceCardKey = self.originalKey,
                    descriptionKey = "effect_unicorn_princess",
                    pointsDelta = 30,
                )
            )
            keys.any { it in setOf("empress", "queen", "enchantress") } -> listOf(
                EffectApplication(
                    sourceCardKey = self.originalKey,
                    descriptionKey = "effect_unicorn_royal",
                    pointsDelta = 15,
                )
            )
            else -> emptyList()
        }
    }
}
