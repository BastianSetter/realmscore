package de.morzo.realmscore.domain.scoring.rules

import de.morzo.realmscore.domain.scoring.CardScoringRule

/**
 * Lookup from card key to its scoring rule. Cards without an entry contribute base strength
 * only.
 */
class CardRuleRegistry(private val rules: Map<String, CardScoringRule>) {

    fun get(cardKey: String): CardScoringRule? = rules[cardKey]

    fun getOrEmpty(cardKey: String): CardScoringRule = rules[cardKey] ?: EmptyRule

    private object EmptyRule : CardScoringRule
}
