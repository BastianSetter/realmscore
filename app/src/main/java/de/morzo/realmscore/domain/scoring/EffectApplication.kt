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
    /**
     * Other hand cards that *caused* this effect (e.g. the Lands a Ranger counts, the Queen that
     * upgrades a King). Empty when the effect has no pairwise source — absence penalties ("no
     * Leader") or pure self bonuses. Used by the ring visualization (Phase 18) to draw the
     * influence lines; does not affect scoring. `sourceCardKey` stays the *owner* of the effect.
     */
    val contributingCardKeys: List<String> = emptyList(),
)
