package de.morzo.realmscore.domain.model

/**
 * A saved Sandbox hand (Phase 22). Favorites are auto-numbered ([number]) in creation order and may
 * additionally carry a free-text [name] (spec 25.6): when null/blank the UI falls back to the number.
 * Only the cards and their joker assignments are persisted; the Island/Fountain/Necromancer player
 * choices are intentionally not stored (re-derivable on load).
 */
data class SandboxFavorite(
    val id: String,
    val number: Int,
    val name: String?,
    val handCards: List<FavoriteCard>,
    val createdAt: Long,
)

data class FavoriteCard(
    val position: Int,
    val cardKey: String,
    val jokerTargetCardKey: String?,
    val jokerTargetSuit: String?,
)
