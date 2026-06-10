package de.morzo.realmscore.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineBreak

/**
 * Enables automatic hyphenation app-wide (Phase 18.2, requires API 29+). The previous definition
 * only overrode `bodyLarge`, so the many widgets that use other styles (nav labels, titles, chips,
 * the score breakdown, …) never got hyphenated. We therefore start from the Material defaults and
 * apply [Hyphens.Auto] + [LineBreak.Paragraph] to *every* type-scale role so long German words break
 * at sensible syllable boundaries instead of being cut mid-word. The deutsch system locale supplies
 * the German hyphenation rules.
 */
private fun TextStyle.withHyphenation(): TextStyle =
    copy(hyphens = Hyphens.Auto, lineBreak = LineBreak.Paragraph)

private val Base = Typography()

val Typography = Base.copy(
    displayLarge = Base.displayLarge.withHyphenation(),
    displayMedium = Base.displayMedium.withHyphenation(),
    displaySmall = Base.displaySmall.withHyphenation(),
    headlineLarge = Base.headlineLarge.withHyphenation(),
    headlineMedium = Base.headlineMedium.withHyphenation(),
    headlineSmall = Base.headlineSmall.withHyphenation(),
    titleLarge = Base.titleLarge.withHyphenation(),
    titleMedium = Base.titleMedium.withHyphenation(),
    titleSmall = Base.titleSmall.withHyphenation(),
    bodyLarge = Base.bodyLarge.withHyphenation(),
    bodyMedium = Base.bodyMedium.withHyphenation(),
    bodySmall = Base.bodySmall.withHyphenation(),
    labelLarge = Base.labelLarge.withHyphenation(),
    labelMedium = Base.labelMedium.withHyphenation(),
    labelSmall = Base.labelSmall.withHyphenation(),
)
