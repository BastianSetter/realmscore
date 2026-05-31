package de.morzo.realmscore.domain.scoring

import de.morzo.realmscore.domain.model.Suit

/**
 * Hand-position-level card after joker substitution. Joker carriers (Doppelganger, Mirage,
 * Shapeshifter, Book of Changes) produce a ResolvedCard that may differ from their
 * underlying CardDefinition.
 *
 * - originalKey identifies the physical card in the hand (Joker keeps its own key here).
 * - effectiveCardKey selects which CardScoringRule applies after substitution.
 * - bonusEnabled / penaltyEnabled control whether the resolved rule contributes bonus/penalty
 *   effects. Mirage/Shapeshifter copy only name+suit → both false. Doppelganger copies penalty
 *   only → bonus=false, penalty=true. Book of Changes only overrides suit on the carrier of
 *   the changed card → bonus/penalty enabled stay as on the underlying card.
 */
data class ResolvedCard(
    val originalKey: String,
    val effectiveCardKey: String,
    val effectiveName: String,
    val effectiveSuit: Suit,
    val effectiveStrength: Int,
    val bonusEnabled: Boolean,
    val penaltyEnabled: Boolean,
)
