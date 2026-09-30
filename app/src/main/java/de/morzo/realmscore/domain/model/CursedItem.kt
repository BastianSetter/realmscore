package de.morzo.realmscore.domain.model

import java.util.Locale

/**
 * A cursed item of the expansion "Der verfluchte Schatz" (Phase 30, part 1). Only its end-of-game
 * points matter to the app; its in-game effect is played at the table.
 */
data class CursedItem(
    val key: String,
    val nameDe: String,
    val nameEn: String,
    val points: Int,
    /** Overrides [points] in a two-player game (Fernglas: −10). */
    val pointsTwoPlayer: Int? = null,
) {
    fun pointsFor(playerCount: Int): Int =
        if (playerCount == 2 && pointsTwoPlayer != null) pointsTwoPlayer else points

    fun displayName(locale: Locale): String = if (locale.language == "de") nameDe else nameEn
}
