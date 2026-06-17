package de.morzo.realmscore.ui.util

import android.content.Context
import de.morzo.realmscore.domain.model.CardDefinition
import de.morzo.realmscore.domain.model.displayName
import de.morzo.realmscore.ui.components.suitLabelRes
import java.text.Collator

/**
 * Cards ordered by their *localized* suit label, then alphabetically by their localized card name,
 * both collated in [context]'s active locale. The camera scan returns cards in OCR detection order,
 * which looks random to the user; this gives the resulting tokens a stable, language-aware order
 * (mirrors [sortedByLocalizedLabel] for suits).
 */
fun List<CardDefinition>.sortedByLocalizedSuitAndName(context: Context): List<CardDefinition> {
    val locale = context.resources.configuration.locales[0]
    val collator = Collator.getInstance(locale)
    fun suitLabel(card: CardDefinition) = context.getString(suitLabelRes(card.suit))
    return sortedWith(
        Comparator { a, b ->
            val bySuit = collator.compare(suitLabel(a), suitLabel(b))
            if (bySuit != 0) bySuit else collator.compare(a.displayName(locale), b.displayName(locale))
        },
    )
}
