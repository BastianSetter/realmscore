package de.morzo.realmscore.domain.model

/**
 * Cards that need a player choice before they can be scored.
 *
 * The first four are *substitution* jokers: the [de.morzo.realmscore.domain.scoring.joker.JokerResolver]
 * rewrites the resolved card (name/suit/strength) before scoring. They carry `isJoker = true` (no own
 * value).
 *
 * [ISLAND], [FOUNTAIN_OF_LIFE] and [NECROMANCER] are ordinary cards (`isJoker = false`, they keep
 * their base strength and rules) that additionally let the player pick a *target*: the Flood/Flame
 * penalty to cancel, resp. the card whose strength to copy, resp. the discard-pile card to pull as
 * the 8th card. Modelling those picks as a [de.morzo.realmscore.domain.scoring.JokerAssignment]
 * (instead of ad-hoc PlayerChoices fields) means they share the joker resolution pipeline, the
 * optimiser and the persistence path with the substitution jokers. For Island/Fountain the candidate
 * target is taken from the *resolved* hand, so a Doppelganger that has become an eligible card can be
 * chosen; the Necromancer's pull is materialised into the resolved hand BEFORE Book of Changes, so a
 * subsequent joker (e.g. Book of Changes on the pulled card) sees it (spec 25.4).
 */
enum class JokerType {
    DOPPELGANGER,
    MIRAGE,
    SHAPESHIFTER,
    BOOK_OF_CHANGES,
    ISLAND,
    FOUNTAIN_OF_LIFE,
    NECROMANCER,

    /**
     * Engel (Phase 30, expansion): an ordinary card that can never be blanked and protects one
     * chosen other card of the resolved hand from being blanked. Target-only, like [ISLAND].
     */
    ANGEL;

    /** True for the four wild substitution jokers handled by the JokerResolver / OptimalSolver combos. */
    val isSubstitution: Boolean
        get() = this == DOPPELGANGER || this == MIRAGE || this == SHAPESHIFTER || this == BOOK_OF_CHANGES
}
