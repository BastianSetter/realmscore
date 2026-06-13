package de.morzo.realmscore.domain.model

/**
 * Cards that need a player choice before they can be scored.
 *
 * The first four are *substitution* jokers: the [de.morzo.realmscore.domain.scoring.joker.JokerResolver]
 * rewrites the resolved card (name/suit/strength) before scoring. They carry `isJoker = true` (no own
 * value).
 *
 * [ISLAND] and [FOUNTAIN_OF_LIFE] are ordinary cards (`isJoker = false`, they keep their base
 * strength and rules) that additionally let the player pick a *target* — the Flood/Flame penalty to
 * cancel, resp. the card whose strength to copy. Modelling that pick as a
 * [de.morzo.realmscore.domain.scoring.JokerAssignment] (instead of the old ad-hoc PlayerChoices
 * fields) means they share the joker section UI, the optimiser and the persistence path with the
 * substitution jokers — and, crucially, their candidate target is taken from the *resolved* hand, so
 * a Doppelganger that has become an eligible card can be chosen.
 */
enum class JokerType {
    DOPPELGANGER,
    MIRAGE,
    SHAPESHIFTER,
    BOOK_OF_CHANGES,
    ISLAND,
    FOUNTAIN_OF_LIFE;

    /** True for the four wild substitution jokers handled by the JokerResolver / OptimalSolver combos. */
    val isSubstitution: Boolean
        get() = this == DOPPELGANGER || this == MIRAGE || this == SHAPESHIFTER || this == BOOK_OF_CHANGES
}
