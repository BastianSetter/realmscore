package de.morzo.realmscore.ui.theme

import androidx.compose.ui.graphics.Color
import de.morzo.realmscore.domain.model.Suit

/**
 * Central source of truth for suit colours (Phase 18.1, Punkt 1).
 *
 * Light-mode values are read 1:1 from the official suit bar of the physical game. Dark-mode values
 * are pre-computed (not mixed at runtime, to stay deterministic) by lightening each light colour
 * ~30 % toward white so the hue stays recognizable but reads comfortably on a dark surface.
 *
 * The internal [Suit] enum maps 1:1 onto these colours:
 *   LAND→Land, FLOOD→Flut, WEATHER→Wetter, FLAME→Flamme, ARMY→Armee, WIZARD→Zauberer,
 *   LEADER→Anführer, BEAST→Bestie, WEAPON→Waffe, ARTIFACT→Artefakt, WILD→Joker.
 */
object SuitColors {
    // Light Mode — base values from the official suit bar.
    val landLight = Color(0xFF6B4A2B)
    val floodLight = Color(0xFF2E4A8A)
    val weatherLight = Color(0xFF7FA8C9)
    val flameLight = Color(0xFFC0392B)
    val armyLight = Color(0xFF3A3A3A)
    val wizardLight = Color(0xFFC0398C)
    val leaderLight = Color(0xFF7A3FA0)
    val beastLight = Color(0xFF4A8A3F)
    val weaponLight = Color(0xFF8A8F98)
    val artifactLight = Color(0xFFD67A2E)
    val wildLight = Color(0xFFC9A23F)

    // Dark Mode — each light colour lightened ~30 % toward white (out = c + (255-c)*0.30).
    val landDark = Color(0xFF97806B)
    val floodDark = Color(0xFF6D80AD)
    val weatherDark = Color(0xFFA5C2D9)
    val flameDark = Color(0xFFD3746B)
    val armyDark = Color(0xFF757575)
    val wizardDark = Color(0xFFD374AF)
    val leaderDark = Color(0xFFA279BD)
    val beastDark = Color(0xFF80AD79)
    val weaponDark = Color(0xFFADB1B7)
    val artifactDark = Color(0xFFE2A26D)
    val wildDark = Color(0xFFD9BE79)

    fun forSuit(suit: Suit, darkTheme: Boolean): Color = if (darkTheme) {
        when (suit) {
            Suit.LAND -> landDark
            Suit.FLOOD -> floodDark
            Suit.WEATHER -> weatherDark
            Suit.FLAME -> flameDark
            Suit.ARMY -> armyDark
            Suit.WIZARD -> wizardDark
            Suit.LEADER -> leaderDark
            Suit.BEAST -> beastDark
            Suit.WEAPON -> weaponDark
            Suit.ARTIFACT -> artifactDark
            Suit.WILD -> wildDark
        }
    } else {
        when (suit) {
            Suit.LAND -> landLight
            Suit.FLOOD -> floodLight
            Suit.WEATHER -> weatherLight
            Suit.FLAME -> flameLight
            Suit.ARMY -> armyLight
            Suit.WIZARD -> wizardLight
            Suit.LEADER -> leaderLight
            Suit.BEAST -> beastLight
            Suit.WEAPON -> weaponLight
            Suit.ARTIFACT -> artifactLight
            Suit.WILD -> wildLight
        }
    }
}
