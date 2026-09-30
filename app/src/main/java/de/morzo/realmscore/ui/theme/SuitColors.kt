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
 *   LEADER→Anführer, BEAST→Bestie, WEAPON→Waffe, ARTIFACT→Artefakt, WILD→Joker, BUILDING→Gebäude, OUTSIDER→Outsider, UNDEAD→Untote.
 */
object SuitColors {
    // Light Mode — base values from the official suit bar.
    val landLight = Color(0xFF4B3225)
    val floodLight = Color(0xFF353F71)
    val weatherLight = Color(0xFF67A7DA)
    val flameLight = Color(0xFFB63C34)
    val armyLight = Color(0xFF2F3233)
    val wizardLight = Color(0xFFDA3F8B)
    val leaderLight = Color(0xFF7A4393)
    val beastLight = Color(0xFF4EA054)
    val weaponLight = Color(0xFF7A7B7A)
    val artifactLight = Color(0xFFF16033)
    val wildLight = Color(0xFFC7BDB9)
    // Phase 30 expansion suits.
    val buildingLight = Color(0xFF463C78)
    val outsiderLight = Color(0xFFF2B01E)
    val undeadLight = Color(0xFF1B5E5B)

    // Dark Mode — each light colour lightened ~30 % toward white (out = c + (255-c)*0.30).
    val landDark = Color(0xFF817066)
    val floodDark = Color(0xFF72799C)
    val weatherDark = Color(0xFF95C1E5)
    val flameDark = Color(0xFFCC7771)
    val armyDark = Color(0xFF6D7070)
    val wizardDark = Color(0xFFE579AE)
    val leaderDark = Color(0xFFA27BB3)
    val beastDark = Color(0xFF83BD87)
    val weaponDark = Color(0xFFA2A3A2)
    val artifactDark = Color(0xFFF59070)
    val wildDark = Color(0xFFD8D1CE)
    val buildingDark = Color(0xFF7E77A0)
    val outsiderDark = Color(0xFFF6C861)
    val undeadDark = Color(0xFF5F8E8C)

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
            Suit.BUILDING -> buildingDark
            Suit.OUTSIDER -> outsiderDark
            Suit.UNDEAD -> undeadDark
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
            Suit.BUILDING -> buildingLight
            Suit.OUTSIDER -> outsiderLight
            Suit.UNDEAD -> undeadLight
        }
    }
}
