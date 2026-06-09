package de.morzo.realmscore.domain.scoring

import de.morzo.realmscore.domain.model.CardDefinition
import de.morzo.realmscore.domain.model.Suit
import kotlin.math.abs
import kotlin.math.min
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RingLayoutOptimizerTest {

    private fun card(key: String): CardDefinition = CardDefinition(
        key = key,
        nameDe = key,
        suit = Suit.ARMY,
        baseStrength = 0,
        ruleTextDe = "",
        isJoker = false,
        jokerType = null,
    )

    private fun cards(vararg keys: String): List<CardDefinition> = keys.map(::card)

    private fun arcDist(a: Int, b: Int, n: Int): Int {
        val d = abs(a - b)
        return min(d, n - d)
    }

    /** position of a card index within an ordering (ordering[pos] = cardIdx). */
    private fun List<Int>.posOf(cardIdx: Int): Int = indexOf(cardIdx)

    @Test
    fun `highest scoring card is anchored on top`() {
        val c = cards("a", "b", "c", "d", "e", "f", "g")
        val scores = listOf(1, 1, 1, 99, 1, 1, 1) // index 3 is highest
        val order = RingLayoutOptimizer.optimize(c, scores, connections = emptyList())
        assertEquals(3, order[0])
    }

    @Test
    fun `strongly connected cards end up adjacent`() {
        val c = cards("a", "b", "c", "d", "e", "f", "g")
        val scores = listOf(99, 0, 0, 0, 0, 0, 0) // anchor = a (idx 0)
        // One heavy edge between idx 1 and idx 5; everything else unconnected.
        val conns = listOf(RingConnection(fromCardIdx = 1, toCardIdx = 5, weight = 100))
        val order = RingLayoutOptimizer.optimize(c, scores, conns)
        assertEquals(0, order[0]) // anchor stays on top
        assertEquals(1, arcDist(order.posOf(1), order.posOf(5), 7))
    }

    @Test
    fun `with no connections the order is deterministic and alphabetical`() {
        val c = cards("g", "f", "e", "d", "c", "b", "a")
        val scores = List(7) { 0 } // all tied -> anchor = alphabetically smallest key = "a"
        val order = RingLayoutOptimizer.optimize(c, scores, connections = emptyList())
        val keysInOrder = order.map { c[it].key }
        // Anchor "a" first, then the rest arranged for the lexicographically smallest sequence.
        assertEquals(listOf("a", "b", "c", "d", "e", "f", "g"), keysInOrder)
    }

    @Test
    fun `result is a valid permutation of all indices`() {
        val c = cards("a", "b", "c", "d", "e", "f", "g")
        val scores = listOf(5, 3, 8, 1, 9, 2, 4)
        val conns = listOf(
            RingConnection(0, 2, 10),
            RingConnection(2, 4, -6),
            RingConnection(1, 6, 3),
        )
        val order = RingLayoutOptimizer.optimize(c, scores, conns)
        assertEquals(c.indices.toSet(), order.toSet())
        assertEquals(7, order.size)
    }

    @Test
    fun `two heavy edges are both kept short`() {
        val c = cards("a", "b", "c", "d", "e", "f", "g")
        val scores = listOf(99, 0, 0, 0, 0, 0, 0) // anchor a
        val conns = listOf(
            RingConnection(1, 2, 50),
            RingConnection(3, 4, 50),
        )
        val order = RingLayoutOptimizer.optimize(c, scores, conns)
        assertEquals(1, arcDist(order.posOf(1), order.posOf(2), 7))
        assertEquals(1, arcDist(order.posOf(3), order.posOf(4), 7))
    }

    @Test
    fun `single card returns trivially`() {
        val c = cards("a")
        val order = RingLayoutOptimizer.optimize(c, listOf(0), emptyList())
        assertEquals(listOf(0), order)
    }

    @Test
    fun `buildRingConnections maps contributors to owners and skips self and foreign cards`() {
        val c = cards("king", "queen", "knight")
        val result = ScoringResult(
            totalScore = 0,
            perCard = listOf(
                CardScoreResult(
                    cardKey = "king",
                    effectiveName = "king",
                    contributedScore = 8,
                    isBlanked = false,
                    effects = listOf(
                        // base strength has no contributors -> no edge
                        EffectApplication("king", "effect_base_strength", listOf("king"), 8),
                        // king's bonus is driven by queen (+6) and a foreign card (skipped)
                        EffectApplication(
                            sourceCardKey = "king",
                            descriptionKey = "x",
                            pointsDelta = 6,
                            contributingCardKeys = listOf("queen", "ghost"),
                        ),
                    ),
                ),
                CardScoreResult(
                    cardKey = "queen",
                    effectiveName = "queen",
                    contributedScore = -3,
                    isBlanked = false,
                    effects = listOf(
                        EffectApplication(
                            sourceCardKey = "queen",
                            descriptionKey = "p",
                            pointsDelta = -3,
                            contributingCardKeys = listOf("knight"),
                        ),
                    ),
                ),
            ),
            blankedKeys = emptySet(),
        )
        val conns = buildRingConnections(c, result)
        assertEquals(2, conns.size)
        // queen -> king. Foreign "ghost" is dropped, so the full +6 lands on the queen edge.
        val kingEdge = conns.first { it.toCardIdx == 0 }
        assertEquals(1, kingEdge.fromCardIdx) // queen
        assertEquals(6, kingEdge.weight)
        // knight -> queen, penalty edge
        val queenEdge = conns.first { it.toCardIdx == 1 }
        assertEquals(2, queenEdge.fromCardIdx) // knight
        assertEquals(-3, queenEdge.weight)
        assertTrue(conns.none { it.fromCardIdx == it.toCardIdx })
    }

    @Test
    fun `buildRingConnections splits an effect evenly across contributors`() {
        val c = cards("rangers", "forest", "mountain", "swamp")
        val result = ScoringResult(
            totalScore = 0,
            perCard = listOf(
                CardScoreResult(
                    cardKey = "rangers",
                    effectiveName = "rangers",
                    contributedScore = 30,
                    isBlanked = false,
                    effects = listOf(
                        EffectApplication(
                            sourceCardKey = "rangers",
                            descriptionKey = "effect_rangers_per_land",
                            descriptionArgs = listOf("3"),
                            pointsDelta = 30,
                            contributingCardKeys = listOf("forest", "mountain", "swamp"),
                        ),
                    ),
                ),
            ),
            blankedKeys = emptySet(),
        )
        val conns = buildRingConnections(c, result)
        assertEquals(3, conns.size)
        assertTrue(conns.all { it.toCardIdx == 0 && it.weight == 10 })
        assertEquals(30, conns.sumOf { it.weight })
    }
}
