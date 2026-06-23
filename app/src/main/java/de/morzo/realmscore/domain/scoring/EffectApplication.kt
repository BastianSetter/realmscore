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
    /**
     * Language-neutral key of a card whose localized NAME is the leading format argument of
     * [descriptionKey] (`%1$s`), resolved by the UI via `cardLookup(it).displayName(locale)`. The
     * resolved name is prepended to [descriptionArgs], so a string like "Base strength %1$s" or
     * "Copies strength of %1$s (+%2$s)" gets its name as `%1$s` and the numbers as the following
     * args. Null when the effect text has no card-name placeholder. Keeps the engine Android-free:
     * it emits keys, never finished German text (spec 25.7, Ursache B).
     */
    val nameCardKey: String? = null,
    val pointsDelta: Int,
    /**
     * Other hand cards that *caused* this effect (e.g. the Lands a Ranger counts, the Queen that
     * upgrades a King). Empty when the effect has no pairwise source — absence penalties ("no
     * Leader") or pure self bonuses. Used by the ring visualization (Phase 18) to draw the
     * influence lines; does not affect scoring. `sourceCardKey` stays the *owner* of the effect.
     */
    val contributingCardKeys: List<String> = emptyList(),
    /**
     * Optional per-contributor point weights, parallel to [contributingCardKeys]. When set (and the
     * sizes match), the ring visualization draws each contributor's edge with its own weight instead
     * of splitting [pointsDelta] evenly across contributors — e.g. the Warlord, whose bonus is the
     * sum of the armies' strengths, so each army's line should carry that army's own strength. Has
     * no effect on scoring or the textual breakdown (which still shows the [pointsDelta] total).
     */
    val contributorWeights: List<Int>? = null,
)
