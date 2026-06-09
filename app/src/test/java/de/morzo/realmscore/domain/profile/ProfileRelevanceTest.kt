package de.morzo.realmscore.domain.profile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileRelevanceTest {

    private val now = 1_000_000_000_000L
    private val day = 86_400_000L

    @Test
    fun `no shared games scores zero`() {
        assertEquals(0.0, ProfileRelevance.score(emptyList(), now), 1e-9)
    }

    @Test
    fun `a game today scores about one`() {
        assertEquals(1.0, ProfileRelevance.score(listOf(now), now), 1e-6)
    }

    @Test
    fun `one game at the half-life decays to exp minus one`() {
        val halfLifeAgo = now - (ProfileRelevance.HALF_LIFE_DAYS.toLong() * day)
        // exp(-30/30) = exp(-1) ≈ 0.3679
        assertEquals(kotlin.math.exp(-1.0), ProfileRelevance.score(listOf(halfLifeAgo), now), 1e-3)
    }

    @Test
    fun `more recent games rank higher than older ones`() {
        val recent = ProfileRelevance.score(listOf(now - day), now)
        val old = ProfileRelevance.score(listOf(now - 200 * day), now)
        assertTrue("recent ($recent) should outrank old ($old)", recent > old)
    }

    @Test
    fun `many recent games outrank a single old game`() {
        val frequentAndRecent = ProfileRelevance.score(
            listOf(now - day, now - 2 * day, now - 3 * day),
            now,
        )
        val singleOld = ProfileRelevance.score(listOf(now - 365 * day), now)
        assertTrue(frequentAndRecent > singleOld)
    }

    @Test
    fun `score accumulates across games`() {
        val single = ProfileRelevance.score(listOf(now), now)
        val triple = ProfileRelevance.score(listOf(now, now, now), now)
        assertEquals(single * 3, triple, 1e-9)
    }
}
