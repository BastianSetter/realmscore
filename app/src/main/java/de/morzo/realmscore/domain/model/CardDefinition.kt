package de.morzo.realmscore.domain.model

data class CardDefinition(
    val key: String,
    val nameDe: String,
    val suit: Suit,
    val baseStrength: Int,
    val ruleTextDe: String,
    val isJoker: Boolean,
    val jokerType: JokerType?,
    /** Official English card name (Phase 19), null if no override is available → falls back to [nameDe]. */
    val nameEn: String? = null,
    /** Official English rule text (Phase 19), null if no override is available → falls back to [ruleTextDe]. */
    val ruleTextEn: String? = null,
)
