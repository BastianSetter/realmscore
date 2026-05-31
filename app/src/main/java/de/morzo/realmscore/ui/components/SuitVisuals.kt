package de.morzo.realmscore.ui.components

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import de.morzo.realmscore.R
import de.morzo.realmscore.domain.model.Suit

internal fun suitColor(suit: Suit): Color = when (suit) {
    Suit.ARMY -> Color(0xFFFFAB91)
    Suit.ARTIFACT -> Color(0xFFFFF59D)
    Suit.BEAST -> Color(0xFFFFCDD2)
    Suit.FLAME -> Color(0xFFFFAB40)
    Suit.FLOOD -> Color(0xFF81D4FA)
    Suit.LAND -> Color(0xFFC8E6C9)
    Suit.LEADER -> Color(0xFFFFE0B2)
    Suit.WEAPON -> Color(0xFFB3E5FC)
    Suit.WEATHER -> Color(0xFFB0BEC5)
    Suit.WIZARD -> Color(0xFFD1C4E9)
    Suit.WILD -> Color(0xFFE1BEE7)
}

@StringRes
internal fun suitLabelRes(suit: Suit): Int = when (suit) {
    Suit.ARMY -> R.string.suit_army
    Suit.ARTIFACT -> R.string.suit_artifact
    Suit.BEAST -> R.string.suit_beast
    Suit.FLAME -> R.string.suit_flame
    Suit.FLOOD -> R.string.suit_flood
    Suit.LAND -> R.string.suit_land
    Suit.LEADER -> R.string.suit_leader
    Suit.WEAPON -> R.string.suit_weapon
    Suit.WEATHER -> R.string.suit_weather
    Suit.WIZARD -> R.string.suit_wizard
    Suit.WILD -> R.string.suit_wild
}
