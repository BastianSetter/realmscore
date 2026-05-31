package de.morzo.realmscore.domain.scoring

/**
 * Single line-item shown in the breakdown sheet.
 *
 * descriptionKey is a string-resource name (without R. prefix) — the UI layer turns it into
 * R.string.<key>. Engine stays Android-free this way.
 */
data class EffectApplication(
    val sourceCardKey: String,
    val descriptionKey: String,
    val descriptionArgs: List<String> = emptyList(),
    val pointsDelta: Int,
)
