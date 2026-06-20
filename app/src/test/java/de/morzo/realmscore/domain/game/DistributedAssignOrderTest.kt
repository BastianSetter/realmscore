package de.morzo.realmscore.domain.game

import org.junit.Assert.assertEquals
import org.junit.Test

class DistributedAssignOrderTest {

    private val allEight = (1..8).map { it.toString() }

    @Test
    fun `combined list interleaves by position then is walked in reverse`() {
        // A:1,7,6,3  B:5,2  C:4,8  -> forward 1,5,4,7,2,8,6,3  -> reversed 3,6,8,2,7,4,5,1
        val prior = mapOf(
            "A" to listOf("1", "7", "6", "3"),
            "B" to listOf("5", "2"),
            "C" to listOf("4", "8"),
        )
        val order = DistributedAssignOrder.build(
            priorSubmissions = prior,
            myDeviceId = "Z", // not a contributor -> no own-list prefix, pure (reversed) combined list
            globalOrder = allEight,
            ownFirst = null,
            forcedFirst = null,
        )
        assertEquals(listOf("3", "6", "8", "2", "7", "4", "5", "1"), order)
    }

    @Test
    fun `own list comes first forward, then the reversed combined list, de-duplicated`() {
        val prior = mapOf(
            "A" to listOf("1", "7", "6", "3"),
            "B" to listOf("5", "2"),
            "C" to listOf("4", "8"),
        )
        val order = DistributedAssignOrder.build(
            priorSubmissions = prior,
            myDeviceId = "C", // own list = 4, 8 (forward); then reversed combined 3,6,8,2,7,4,5,1
            globalOrder = allEight,
            ownFirst = null,
            forcedFirst = null,
        )
        assertEquals(listOf("4", "8", "3", "6", "2", "7", "5", "1"), order)
    }

    @Test
    fun `two-phone case steals from the back of the combined list`() {
        // Spec from the user's device test: A:1,3,4  B:2 -> forward combined 1,2,3,4 -> reversed 4,3,2,1.
        val prior = mapOf("A" to listOf("1", "3", "4"), "B" to listOf("2"))
        val global = listOf("1", "2", "3", "4")

        val orderB = DistributedAssignOrder.build(prior, "B", global, ownFirst = "2", forcedFirst = null)
        // B re-grabs its own seat (2), then steals 4 before 3 (back of the combined list).
        assertEquals(listOf("2", "4", "3", "1"), orderB)

        val orderA = DistributedAssignOrder.build(prior, "A", global, ownFirst = "1", forcedFirst = null)
        // A re-grabs its own hands forward (1,3,4), then 2.
        assertEquals(listOf("1", "3", "4", "2"), orderA)
    }

    @Test
    fun `round 1 with no prior data uses ownFirst then global order`() {
        val order = DistributedAssignOrder.build(
            priorSubmissions = emptyMap(),
            myDeviceId = "me",
            globalOrder = listOf("me", "a", "b"),
            ownFirst = "me",
            forcedFirst = null,
        )
        assertEquals(listOf("me", "a", "b"), order)
    }

    @Test
    fun `round 1 host pins Mittelfeld first`() {
        val order = DistributedAssignOrder.build(
            priorSubmissions = emptyMap(),
            myDeviceId = "host",
            globalOrder = listOf("__discard__", "h1", "h2"),
            ownFirst = "__discard__",
            forcedFirst = "__discard__",
        )
        assertEquals(listOf("__discard__", "h1", "h2"), order)
    }

    @Test
    fun `a player who joined this round is appended in global order`() {
        val prior = mapOf("A" to listOf("p1"), "B" to listOf("p2"))
        val order = DistributedAssignOrder.build(
            priorSubmissions = prior,
            myDeviceId = "A",
            globalOrder = listOf("p1", "p2", "p3"), // p3 has no submit history
            ownFirst = null,
            forcedFirst = null,
        )
        assertEquals(listOf("p1", "p2", "p3"), order)
    }

    @Test
    fun `a unit absent this round is filtered out`() {
        val prior = mapOf("A" to listOf("p1", "pX"))
        val order = DistributedAssignOrder.build(
            priorSubmissions = prior,
            myDeviceId = "A",
            globalOrder = listOf("p1"), // pX left the game
            ownFirst = null,
            forcedFirst = null,
        )
        assertEquals(listOf("p1"), order)
    }

    @Test
    fun `round 2 plus keeps the host Mittelfeld pinned first even for a client`() {
        val prior = mapOf(
            "H" to listOf("__discard__", "h1"),
            "C" to listOf("h2"),
        )
        val order = DistributedAssignOrder.build(
            priorSubmissions = prior,
            myDeviceId = "C",
            globalOrder = listOf("__discard__", "h1", "h2"),
            ownFirst = "h2",
            forcedFirst = "__discard__",
        )
        assertEquals(listOf("__discard__", "h2", "h1"), order)
    }
}
