package de.morzo.realmscore.domain.scoring

import de.morzo.realmscore.domain.model.Suit

sealed class CardMatcher {
    abstract fun matches(card: ResolvedCard): Boolean

    data class BySuit(val suits: Set<Suit>) : CardMatcher() {
        constructor(vararg suits: Suit) : this(suits.toSet())

        override fun matches(card: ResolvedCard): Boolean = card.effectiveSuit in suits
    }

    data class ByKey(val keys: Set<String>) : CardMatcher() {
        constructor(vararg keys: String) : this(keys.toSet())

        override fun matches(card: ResolvedCard): Boolean = card.effectiveCardKey in keys
    }

    data class AnyOf(val matchers: List<CardMatcher>) : CardMatcher() {
        constructor(vararg matchers: CardMatcher) : this(matchers.toList())

        override fun matches(card: ResolvedCard): Boolean = matchers.any { it.matches(card) }
    }
}
