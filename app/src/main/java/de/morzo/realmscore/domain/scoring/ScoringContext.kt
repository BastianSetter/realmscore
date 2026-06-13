package de.morzo.realmscore.domain.scoring

import de.morzo.realmscore.domain.model.CardDefinition
import de.morzo.realmscore.domain.scoring.penalty.PenaltyContext

/**
 * The pipeline state visible to a CardScoringRule when it computes its contribution.
 *
 * - hand is the post-joker, post-blanking list. self appears in hand for blanked-ness checks,
 *   but rules should only fire when !blanked(self).
 * - cardLookup gives access to immutable card metadata (base strength of any card in the game).
 * - penaltyContext is populated before penalty phase; null during the cancellation-collection
 *   phase (rules' cancellations() method is called before any penalty context exists).
 * - jokerAssignments carries the player's joker/Island/Fountain target picks so target-driven rules
 *   (Island cancel, Fountain of Life) can read their chosen card key.
 */
data class ScoringContext(
    val hand: List<ResolvedCard>,
    val blankedKeys: Set<String>,
    val playerChoices: PlayerChoices,
    val discardPile: List<CardDefinition>,
    val cardLookup: (String) -> CardDefinition?,
    val jokerAssignments: Map<String, JokerAssignment> = emptyMap(),
    val penaltyContext: PenaltyContext? = null,
) {
    fun isBlanked(card: ResolvedCard): Boolean = card.originalKey in blankedKeys

    fun nonBlankedHand(): List<ResolvedCard> = hand.filterNot { it.originalKey in blankedKeys }
}
