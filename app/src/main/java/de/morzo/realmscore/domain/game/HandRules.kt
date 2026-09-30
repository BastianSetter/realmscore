package de.morzo.realmscore.domain.game

/**
 * Hand and Mittelfeld sizes (Phase 30). The base game plays 7-card hands; the expansion's new suits
 * raise that to 8. Kobold / Dschinn (new suits) and the cursed item Portal add one more card, but a
 * hand may never exceed [HAND_CAP] — the Necromancer's pulled card included (rulebook p. 5).
 */
object HandRules {

    const val BASE_HAND = 7
    const val NEW_SUITS_HAND = 8
    const val HAND_CAP = 9

    const val LEPRECHAUN_KEY = "expansion_leprechaun"
    const val GENIE_KEY = "expansion_genie"
    const val PORTAL_KEY = "cursed_portal"

    /** Cards a hand must hold. */
    fun minHand(newSuits: Boolean): Int = if (newSuits) NEW_SUITS_HAND else BASE_HAND

    /** Slots shown for a hand: the minimum plus one optional slot when an extra card is possible at all. */
    fun slotCount(newSuits: Boolean, cursedItems: Boolean): Int =
        minHand(newSuits) + if (newSuits || cursedItems) 1 else 0

    /**
     * The largest legal hand for this capture: one extra card when a Kobold/Dschinn is held or the
     * Portal was used (several extenders still only reach the cap, the surplus is discarded).
     */
    fun maxHand(newSuits: Boolean, handKeys: Collection<String>, cursedItemKeys: Collection<String>): Int {
        val extender = (newSuits && (LEPRECHAUN_KEY in handKeys || GENIE_KEY in handKeys)) ||
            PORTAL_KEY in cursedItemKeys
        return (minHand(newSuits) + if (extender) 1 else 0).coerceAtMost(HAND_CAP)
    }

    /**
     * Whether [handCount] captured cards (plus an optional Necromancer pull) form a legal hand.
     */
    fun isValidHandSize(
        newSuits: Boolean,
        handKeys: Collection<String>,
        cursedItemKeys: Collection<String>,
        hasNecromancerPull: Boolean,
    ): Boolean {
        val count = handKeys.size
        if (count < minHand(newSuits) || count > maxHand(newSuits, handKeys, cursedItemKeys)) return false
        return count + (if (hasNecromancerPull) 1 else 0) <= HAND_CAP
    }

    /** Mittelfeld size that ends the game: base 10 (2 players) / 12; with new suits 14 (2 players) / 12. */
    fun discardTarget(newSuits: Boolean, playerCount: Int): Int = when {
        newSuits -> if (playerCount <= 2) 14 else 12
        else -> if (playerCount <= 2) 10 else 12
    }

    /**
     * Upper bound of Mittelfeld cards (= slots shown): with the new suits, players over the hand cap
     * discard their surplus into the Mittelfeld at game end, so a few extra cards may lie there.
     */
    fun discardMax(newSuits: Boolean, playerCount: Int): Int =
        discardTarget(newSuits, playerCount) + if (newSuits) DISCARD_OVERFLOW_SLOTS else 0

    private const val DISCARD_OVERFLOW_SLOTS = 3
}
