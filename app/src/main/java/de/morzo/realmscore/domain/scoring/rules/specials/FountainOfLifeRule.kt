package de.morzo.realmscore.domain.scoring.rules.specials

import de.morzo.realmscore.domain.model.Suit
import de.morzo.realmscore.domain.scoring.CardScoringRule
import de.morzo.realmscore.domain.scoring.EffectApplication
import de.morzo.realmscore.domain.scoring.ResolvedCard
import de.morzo.realmscore.domain.scoring.ScoringContext

/**
 * Quelle des Lebens: adds base strength of one chosen Weapon/Flood/Flame/Land/Weather
 * (from non-blanked hand). The player's pick is the Fountain's joker target ([FOUNTAIN_KEY]); the
 * source is matched against the *resolved* hand, so a Doppelganger/Book-of-Changes card that has
 * become an eligible suit/strength is a valid source.
 *
 * When no choice is set, OptimalSolver will iterate; if neither set nor optimised,
 * we just contribute 0.
 */
object FountainOfLifeRule : CardScoringRule {

    const val FOUNTAIN_KEY = "fountain_of_life"

    val eligibleSuits: Set<Suit> = setOf(Suit.WEAPON, Suit.FLOOD, Suit.FLAME, Suit.LAND, Suit.WEATHER)

    override fun bonuses(self: ResolvedCard, ctx: ScoringContext): List<EffectApplication> {
        val pickKey = ctx.jokerAssignments[FOUNTAIN_KEY]?.targetCardKey ?: return emptyList()
        if (pickKey == self.originalKey) return emptyList()
        val source = ctx.nonBlankedHand().firstOrNull { it.originalKey == pickKey } ?: return emptyList()
        if (source.effectiveSuit !in eligibleSuits) return emptyList()
        if (source.effectiveStrength == 0) return emptyList()
        return listOf(
            EffectApplication(
                sourceCardKey = self.originalKey,
                descriptionKey = "effect_fountain_of_life",
                descriptionArgs = listOf(source.effectiveName, source.effectiveStrength.toString()),
                pointsDelta = source.effectiveStrength,
            )
        )
    }
}
