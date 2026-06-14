package de.morzo.realmscore.ui.components

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import de.morzo.realmscore.R
import de.morzo.realmscore.domain.model.Suit
import de.morzo.realmscore.ui.theme.LocalIsDarkTheme
import de.morzo.realmscore.ui.theme.SuitColors

/**
 * The real card colour for [suit], resolved for the current light/dark theme. Single source of
 * truth is [SuitColors] (Phase 18.1, Punkt 1). For non-composable draw code (e.g. the ring canvas)
 * call [SuitColors.forSuit] directly with an explicitly threaded `darkTheme` flag (read it from
 * [LocalIsDarkTheme], which follows the in-app ThemeMode — not `isSystemInDarkTheme()`).
 */
@Composable
internal fun suitColor(suit: Suit): Color =
    SuitColors.forSuit(suit, LocalIsDarkTheme.current)

/**
 * A readable on-colour (black/white) for text/icons drawn on top of [suitColor]. Several suit
 * colours are dark even in light mode (Land, Flut, Armee, …), so a fixed colour would fail
 * contrast – pick by luminance instead.
 */
@Composable
internal fun suitOnColor(suit: Suit): Color =
    if (suitColor(suit).luminance() > 0.5f) Color.Black else Color.White

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
