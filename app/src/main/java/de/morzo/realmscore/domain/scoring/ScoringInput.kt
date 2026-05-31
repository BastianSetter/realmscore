package de.morzo.realmscore.domain.scoring

import de.morzo.realmscore.domain.model.CardDefinition

data class ScoringInput(
    val hand: List<CardDefinition>,
    val jokerAssignments: Map<String, JokerAssignment> = emptyMap(),
    val playerChoices: PlayerChoices = PlayerChoices(),
    val discardPile: List<CardDefinition> = emptyList(),
)
