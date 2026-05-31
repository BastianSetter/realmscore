package de.morzo.realmscore.domain.scoring.rules.specials

import de.morzo.realmscore.domain.scoring.CardScoringRule
import de.morzo.realmscore.domain.scoring.EffectApplication
import de.morzo.realmscore.domain.scoring.ResolvedCard
import de.morzo.realmscore.domain.scoring.ScoringContext

/**
 * Juwel der Ordnung:
 *   +10 for a run of 3 (consecutive base strengths)
 *   +30 for 4
 *   +60 for 5
 *   +100 for 6
 *   +150 for 7
 *
 * Counts the longest run of distinct consecutive base strengths among all non-blanked cards
 * (Gem itself is included). Base strength of jokers is 0 unless substituted in.
 */
object GemOfOrderRule : CardScoringRule {

    private val tiers = mapOf(
        3 to 10,
        4 to 30,
        5 to 60,
        6 to 100,
        7 to 150,
    )

    override fun bonuses(self: ResolvedCard, ctx: ScoringContext): List<EffectApplication> {
        val strengths = ctx.nonBlankedHand()
            .map { it.effectiveStrength }
            .filter { it > 0 } // unsubstituted jokers have 0; ignore them
            .distinct()
            .sorted()
        val longestRun = longestConsecutiveRun(strengths)
        val bonus = tiers[longestRun] ?: return emptyList()
        return listOf(
            EffectApplication(
                sourceCardKey = self.originalKey,
                descriptionKey = "effect_gem_of_order",
                descriptionArgs = listOf(longestRun.toString()),
                pointsDelta = bonus,
            )
        )
    }

    private fun longestConsecutiveRun(sortedDistinct: List<Int>): Int {
        if (sortedDistinct.isEmpty()) return 0
        var best = 1
        var current = 1
        for (i in 1 until sortedDistinct.size) {
            current = if (sortedDistinct[i] == sortedDistinct[i - 1] + 1) current + 1 else 1
            if (current > best) best = current
        }
        return best
    }
}
