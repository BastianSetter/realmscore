package de.morzo.realmscore.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalConfiguration
import de.morzo.realmscore.domain.model.CardDefinition
import de.morzo.realmscore.domain.model.displayName
import de.morzo.realmscore.domain.model.displayRuleText
import java.util.Locale

/** The locale currently active in this composition (Phase 19 language switcher). */
@Composable
@ReadOnlyComposable
fun currentLocale(): Locale = LocalConfiguration.current.locales[0]

/** Card name in the currently active locale, with fallback to German. */
@Composable
@ReadOnlyComposable
fun CardDefinition.displayName(): String = displayName(currentLocale())

/** Card rule text in the currently active locale, with fallback to German. */
@Composable
@ReadOnlyComposable
fun CardDefinition.displayRuleText(): String = displayRuleText(currentLocale())
