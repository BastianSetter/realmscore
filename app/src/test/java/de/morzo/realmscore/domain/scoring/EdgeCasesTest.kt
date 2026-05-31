package de.morzo.realmscore.domain.scoring

import org.junit.Assert.assertEquals
import org.junit.Test

class EdgeCasesTest {

    @Test fun `empty hand scores 0`() {
        val r = TestFixture.engine.score(ScoringInput(hand = emptyList()))
        assertEquals(0, r.totalScore)
        assertEquals(0, r.perCard.size)
    }

    @Test fun `single card under 7`() {
        val r = TestFixture.score("rangers")
        // rangers: 0 lands → 0 bonus
        assertEquals(5, r.totalScore)
    }

    @Test fun `all four jokers unassigned`() {
        val r = TestFixture.score("doppelganger", "mirage", "shapeshifter", "book_of_changes")
        // book_of_changes still has base 3; others base 0
        assertEquals(3, r.totalScore)
    }

    @Test fun `engine is deterministic`() {
        val a = TestFixture.score("warlord", "rangers", "knights", "king", "queen", "dwarvish_infantry", "elven_archers")
        val b = TestFixture.score("warlord", "rangers", "knights", "king", "queen", "dwarvish_infantry", "elven_archers")
        assertEquals(a.totalScore, b.totalScore)
    }

    @Test fun `score breakdown sums to total`() {
        val r = TestFixture.score("king", "queen", "rangers", "knights")
        val sumOfDeltas = r.perCard.flatMap { it.effects }.sumOf { it.pointsDelta }
        assertEquals(r.totalScore, sumOfDeltas)
    }
}
