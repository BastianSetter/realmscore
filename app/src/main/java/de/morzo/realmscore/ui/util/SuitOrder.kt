package de.morzo.realmscore.ui.util

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalContext
import de.morzo.realmscore.domain.model.Suit
import de.morzo.realmscore.ui.components.suitLabelRes
import java.text.Collator

/**
 * Suits ordered alphabetically by their *localized* label (Armee/Army …), using [context]'s active
 * locale both to resolve the label text and to collate it. This replaces the former fixed
 * enum/JSON order so every suit list follows the in-app language (Anführer/Armee/Artefakt… in
 * German, Army/Artifact/Beast… in English).
 *
 * `sortedWith` is stable, so any pre-existing secondary order in the receiver is preserved within a
 * suit — callers (e.g. a card list pre-sorted by name) keep that tie-break.
 */
fun List<Suit>.sortedByLocalizedLabel(context: Context): List<Suit> {
    val collator = Collator.getInstance(context.resources.configuration.locales[0])
    val byLabel = Comparator<String> { a, b -> collator.compare(a, b) }
    return sortedWith(compareBy(byLabel) { context.getString(suitLabelRes(it)) })
}

/** Composable shortcut for [sortedByLocalizedLabel]; reads the locale-aware [LocalContext]. */
@Composable
@ReadOnlyComposable
fun List<Suit>.sortedByLocalizedLabel(): List<Suit> =
    sortedByLocalizedLabel(LocalContext.current)
