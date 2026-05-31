package de.morzo.realmscore.domain.scoring.rules.specials

import de.morzo.realmscore.domain.scoring.CardScoringRule
import de.morzo.realmscore.domain.scoring.EffectApplication
import de.morzo.realmscore.domain.scoring.ResolvedCard
import de.morzo.realmscore.domain.scoring.ScoringContext

/**
 * Sammler: +10/+40/+100 for 3/4/5 distinct cards of the same suit. Includes the Collector itself.
 *
 * "Distinct" → distinct effectiveCardKey (so two jokers copying the same suit don't compound).
 * Counts each suit independently; the bonus uses the BEST tier across all suits.
 */
object CollectorRule : CardScoringRule {

    private val tiers = mapOf(
        3 to 10,
        4 to 40,
        5 to 100,
    )

    override fun bonuses(self: ResolvedCard, ctx: ScoringContext): List<EffectApplication> {
        val pool = ctx.nonBlankedHand()
        val bestEntry = pool
            .groupBy { it.effectiveSuit }
            .mapValues { (_, cards) -> cards.distinctBy { it.effectiveCardKey }.size }
            .filterValues { it in tiers.keys }
            .maxByOrNull { tiers[it.value]!! }
            ?: return emptyList()
        val (suit, count) = bestEntry
        val bonus = tiers[count]!!
        return listOf(
            EffectApplication(
                sourceCardKey = self.originalKey,
                descriptionKey = "effect_collector",
                descriptionArgs = listOf(count.toString(), suit.name),
                pointsDelta = bonus,
            )
        )
    }
}
