package de.morzo.realmscore.domain.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Phase 30: hand and Mittelfeld sizes with the expansion. */
class HandRulesTest {

    private val eight = (1..8).map { "card$it" }

    @Test
    fun baseGameKeepsSevenCards() {
        assertEquals(7, HandRules.minHand(newSuits = false))
        assertEquals(7, HandRules.slotCount(newSuits = false, cursedItems = false))
        assertEquals(8, HandRules.slotCount(newSuits = false, cursedItems = true))
    }

    @Test
    fun newSuitsPlayEightCardsAndLeprechaunOrGenieAllowNine() {
        assertEquals(8, HandRules.minHand(newSuits = true))
        assertEquals(8, HandRules.maxHand(true, eight, emptyList()))
        assertEquals(9, HandRules.maxHand(true, eight + HandRules.LEPRECHAUN_KEY, emptyList()))
        // Both extenders still reach only the cap of nine.
        assertEquals(9, HandRules.maxHand(true, eight + HandRules.LEPRECHAUN_KEY + HandRules.GENIE_KEY, emptyList()))
    }

    @Test
    fun portalMakesOneMoreCardMandatory() {
        val portal = listOf(HandRules.PORTAL_KEY)
        assertEquals(8, HandRules.maxHand(false, eight.take(7), portal))
        assertEquals(8, HandRules.minHand(false, portal))
        assertEquals(9, HandRules.minHand(true, portal))
        assertFalse(HandRules.isValidHandSize(true, eight, portal, hasNecromancerPull = false))
        assertTrue(HandRules.isValidHandSize(true, eight + "card9", portal, hasNecromancerPull = false))
        assertFalse(HandRules.isValidHandSize(false, eight.take(7), portal, hasNecromancerPull = false))
    }

    @Test
    fun necromancerPullCountsTowardsTheCapOfNine() {
        val nine = eight.take(7) + HandRules.LEPRECHAUN_KEY + HandRules.GENIE_KEY
        assertTrue(HandRules.isValidHandSize(true, nine, emptyList(), hasNecromancerPull = false))
        assertFalse(HandRules.isValidHandSize(true, nine, emptyList(), hasNecromancerPull = true))
        assertTrue(HandRules.isValidHandSize(true, eight, emptyList(), hasNecromancerPull = true))
        assertFalse(HandRules.isValidHandSize(true, eight.take(7), emptyList(), hasNecromancerPull = false))
    }

    @Test
    fun mittelfeldTargets() {
        assertEquals(10, HandRules.discardTarget(newSuits = false, playerCount = 2))
        assertEquals(12, HandRules.discardTarget(newSuits = false, playerCount = 4))
        assertEquals(14, HandRules.discardTarget(newSuits = true, playerCount = 2))
        assertEquals(12, HandRules.discardTarget(newSuits = true, playerCount = 3))
        assertEquals(10, HandRules.discardMax(newSuits = false, playerCount = 2))
        assertTrue(HandRules.discardMax(newSuits = true, playerCount = 2) > 14)
    }
}
