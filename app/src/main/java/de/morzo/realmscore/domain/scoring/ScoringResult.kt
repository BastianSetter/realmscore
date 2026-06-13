package de.morzo.realmscore.domain.scoring

import de.morzo.realmscore.domain.model.Suit

data class ScoringResult(
    val totalScore: Int,
    val perCard: List<CardScoreResult>,
    val blankedKeys: Set<String>,
    /**
     * For each blanked card (originalKey), the originalKeys of the *other* cards that blank it.
     * Self-blanks (Kriegsschiff, Rauch, …) contribute an empty list — the card is still in
     * [blankedKeys] but has no foreign blanker. Used by the ring visualization to draw the black
     * blanking edge and by the list breakdown to show "geblankt von …".
     */
    val blankedBy: Map<String, List<String>> = emptyMap(),
)

data class CardScoreResult(
    val cardKey: String,
    /**
     * Language-neutral key of the card whose name should be DISPLAYED for this slot — the
     * substituted card for a Doppelganger/Mirage/Shapeshifter, otherwise the card itself. The UI
     * resolves it to a localized name via `cardLookup(effectiveCardKey).displayName(locale)`; the
     * engine deliberately never carries a finished (German) name (spec 25.7, Ursache B).
     */
    val effectiveCardKey: String,
    val contributedScore: Int,
    val isBlanked: Boolean,
    val effects: List<EffectApplication>,
    /** True for the extra card the Necromancer pulled from the discard pile (8th scored card). */
    val isNecromancerPick: Boolean = false,
    /**
     * The suit this card was re-coloured to by the Book of Changes, or null if its suit is
     * unchanged. Set only when the card keeps its own identity (a pure suit override, not a
     * Doppelganger/Mirage/Shapeshifter substitution). The ring visualization paints half the node
     * in this suit's colour to flag the change.
     */
    val bookOfChangesSuit: Suit? = null,
)
