package de.morzo.realmscore.domain.model

data class CardDefinition(
    val key: String,
    val nameDe: String,
    val suit: Suit,
    val baseStrength: Int,
    val ruleTextDe: String,
    val isJoker: Boolean,
    val jokerType: JokerType?,
)
