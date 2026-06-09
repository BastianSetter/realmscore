package de.morzo.realmscore.domain.scoring

import de.morzo.realmscore.domain.model.CardDefinition

data class ScoringInput(
    val hand: List<CardDefinition>,
    val jokerAssignments: Map<String, JokerAssignment> = emptyMap(),
    val playerChoices: PlayerChoices = PlayerChoices(),
    val discardPile: List<CardDefinition> = emptyList(),
    /**
     * True when the round's discard pile (Mittelfeld) was captured (Phase 20). Only the
     * [OptimalSolver] reads this: it gates whether the Necromancer pick is brute-forced over the
     * captured [discardPile]. The [ScoringEngine] ignores it — scoring is identical either way.
     */
    val discardScanned: Boolean = false,
)
