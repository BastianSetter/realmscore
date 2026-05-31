package de.morzo.realmscore.domain.scoring.blanking

import de.morzo.realmscore.domain.model.Suit
import de.morzo.realmscore.domain.scoring.HandCondition
import de.morzo.realmscore.domain.scoring.ResolvedCard

/**
 * Card-emitted blanking. sourceKey lets the resolver drop the effect once its source is blanked.
 */
sealed class BlankingEffect {
    abstract val sourceKey: String

    /** Basilisk: blanks all Army/Leader/other-Beasts. */
    data class BlankBySuit(
        override val sourceKey: String,
        val targetSuits: Set<Suit>,
        val excludeSelf: Boolean = true,
    ) : BlankingEffect()

    /** Same as BlankBySuit but with explicit key exceptions (Große Flut: all Land except Mountain). */
    data class BlankBySuitExcept(
        override val sourceKey: String,
        val targetSuits: Set<Suit>,
        val exceptKeys: Set<String> = emptySet(),
        val excludeSelf: Boolean = true,
    ) : BlankingEffect()

    /**
     * Buschfeuer: blanks every card whose suit is not in [allowedSuits] and whose key is not
     * in [allowedKeys].
     */
    data class BlankAllExcept(
        override val sourceKey: String,
        val allowedSuits: Set<Suit>,
        val allowedKeys: Set<String> = emptySet(),
        val excludeSelf: Boolean = true,
    ) : BlankingEffect()

    /** Self-blank when [condition] holds. Kriegsschiff / Kampfzeppelin / Rauch. */
    data class SelfBlank(
        override val sourceKey: String,
        val condition: HandCondition,
    ) : BlankingEffect()

    fun blanks(self: ResolvedCard, target: ResolvedCard, hand: List<ResolvedCard>): Boolean {
        return when (this) {
            is BlankBySuit -> {
                if (excludeSelf && target.originalKey == self.originalKey) false
                else target.effectiveSuit in targetSuits
            }
            is BlankBySuitExcept -> {
                if (excludeSelf && target.originalKey == self.originalKey) false
                else target.effectiveSuit in targetSuits && target.effectiveCardKey !in exceptKeys
            }
            is BlankAllExcept -> {
                if (excludeSelf && target.originalKey == self.originalKey) false
                else target.effectiveSuit !in allowedSuits && target.effectiveCardKey !in allowedKeys
            }
            is SelfBlank -> {
                target.originalKey == self.originalKey && condition.evaluate(hand, self)
            }
        }
    }
}
