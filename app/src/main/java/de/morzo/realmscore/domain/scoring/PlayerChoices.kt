package de.morzo.realmscore.domain.scoring

/**
 * Scoring-time choices that are NOT modelled as a [JokerAssignment].
 *
 * The only remaining one is the Necromancer pick: it pulls a card from the discard pile, so its
 * target is not a hand card and cannot live in the joker-assignment map (which is keyed by hand
 * cards). Island/Fountain targets used to live here too — they are now ordinary
 * [JokerAssignment]s keyed by their card key (see [de.morzo.realmscore.domain.model.JokerType]).
 */
data class PlayerChoices(
    val necromancerPickKey: String? = null,
)
