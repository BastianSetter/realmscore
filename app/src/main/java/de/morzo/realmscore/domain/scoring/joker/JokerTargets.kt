package de.morzo.realmscore.domain.scoring.joker

import de.morzo.realmscore.domain.model.CardDefinition
import de.morzo.realmscore.domain.model.JokerType
import de.morzo.realmscore.domain.model.Suit

/**
 * Single source of truth for which suits a target-picking card may choose from (Phase 30).
 *
 * The expansion "Der verfluchte Schatz" replaces Mirage, Shapeshifter, Necromancer and Fountain of
 * Life with versions (`expansion_*` keys) that additionally reach the new suits. Everything that
 * offers or validates a target — the engine rules, the [de.morzo.realmscore.domain.scoring.solver.OptimalSolver]
 * and the joker pickers in the UI — asks here instead of keeping its own suit set, keyed by the
 * concrete card so the base and expansion versions can coexist.
 */
object JokerTargets {

    const val EXPANSION_PREFIX = "expansion_"

    fun isExpansion(cardKey: String): Boolean = cardKey.startsWith(EXPANSION_PREFIX)

    /** Suits a Spiegelung (Mirage) may copy a card from — official rule (the other five suits). */
    private val MIRAGE_SUITS = setOf(Suit.ARMY, Suit.LAND, Suit.WEATHER, Suit.FLOOD, Suit.FLAME)

    /** Suits a Gestaltwandler (Shapeshifter) may copy a card from — official rule. */
    private val SHAPESHIFTER_SUITS = setOf(Suit.ARTIFACT, Suit.LEADER, Suit.WIZARD, Suit.WEAPON, Suit.BEAST)

    /** Suits the Necromancer may pull from the discard pile (official rule). */
    private val NECROMANCER_SUITS = setOf(Suit.ARMY, Suit.WIZARD, Suit.LEADER, Suit.BEAST)

    /** Suits whose base strength the Fountain of Life may add. */
    private val FOUNTAIN_SUITS = setOf(Suit.WEAPON, Suit.FLOOD, Suit.FLAME, Suit.LAND, Suit.WEATHER)

    /** Suits whose penalty the Island may clear. */
    val ISLAND_SUITS = setOf(Suit.FLOOD, Suit.FLAME)

    /** Copy-target suits of a substitution joker (Mirage/Shapeshifter); empty for any other card. */
    fun substitutionSuits(card: CardDefinition): Set<Suit> = when (card.jokerType) {
        JokerType.MIRAGE ->
            if (isExpansion(card.key)) MIRAGE_SUITS + Suit.BUILDING else MIRAGE_SUITS
        JokerType.SHAPESHIFTER ->
            if (isExpansion(card.key)) SHAPESHIFTER_SUITS + Suit.UNDEAD else SHAPESHIFTER_SUITS
        else -> emptySet()
    }

    fun necromancerSuits(necromancerKey: String): Set<Suit> =
        if (isExpansion(necromancerKey)) NECROMANCER_SUITS + Suit.UNDEAD else NECROMANCER_SUITS

    fun fountainSuits(fountainKey: String): Set<Suit> =
        if (isExpansion(fountainKey)) FOUNTAIN_SUITS + Suit.BUILDING else FOUNTAIN_SUITS

    /** The first card of [jokerType] in [hand], e.g. the (base or expansion) Necromancer. */
    fun firstOfType(hand: List<CardDefinition>, jokerType: JokerType): CardDefinition? =
        hand.firstOrNull { it.jokerType == jokerType }
}
