package de.morzo.realmscore.domain.scoring

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BlankingTest {

    @Test fun `basilisk blanks armies leaders and other beasts`() {
        val r = TestFixture.score("basilisk", "rangers", "king", "warhorse")
        assertTrue("rangers" in r.blankedKeys)
        assertTrue("king" in r.blankedKeys)
        assertTrue("warhorse" in r.blankedKeys)
        assertEquals(35, r.totalScore)
    }

    @Test fun `wildfire blanks most cards but spares allowed`() {
        // wildfire(40) + dragon(30 beast, allowed-key) + king(6 leader, NOT allowed → blanked)
        //              + magic_wand(1 weapon, allowed by suit)
        // dragon: no wizard → -40
        // wand: no wizard → 0 bonus
        // king blanked
        // total = 40+30-40+1 = 31
        val r = TestFixture.score("wildfire", "dragon", "king", "magic_wand")
        assertTrue("king" in r.blankedKeys)
        assertEquals(31, r.totalScore)
    }

    @Test fun `great_flood spares mountain and lightning`() {
        // flood(32) + mountain(9) + lightning(11) + rangers(5,army→blanked)
        // mountain spared (named exception). lightning spared (named exception).
        // rangers blanked.
        // mountain: smoke+wildfire? no → 0
        // lightning: rainstorm? no → 0
        // total = 32+9+11 = 52
        val r = TestFixture.score("great_flood", "mountain", "lightning", "rangers")
        assertTrue("mountain" !in r.blankedKeys)
        assertTrue("lightning" !in r.blankedKeys)
        assertTrue("rangers" in r.blankedKeys)
        assertEquals(52, r.totalScore)
    }

    @Test fun `self-blank warship without flood`() {
        val r = TestFixture.score("warship", "rangers")
        assertTrue("warship" in r.blankedKeys)
        // rangers: 0 lands → 0
        assertEquals(5, r.totalScore)
    }

    @Test fun `dirigible blanked when weather present`() {
        // Use blizzard (weather, not self-blanking) for the weather presence.
        // dirigible: NotContains(ARMY) false (rangers); Contains(WEATHER) true (blizzard) → blanked.
        // blizzard: -5 per army/leader/beast/flame. BUT rangers strips ARMY → 0 targets → 0 penalty.
        // rangers: 0 lands → 0.
        val r = TestFixture.score("war_dirigible", "rangers", "blizzard")
        assertTrue("war_dirigible" in r.blankedKeys)
        // total = 0(dirigible) + 5(rangers) + 30(blizzard) = 35
        assertEquals(35, r.totalScore)
    }

    @Test fun `smoke alive with any flame`() {
        // smoke + candle. smoke survives. candle: combo? no → 0.
        val r = TestFixture.score("smoke", "candle")
        assertTrue("smoke" !in r.blankedKeys)
        assertEquals(29, r.totalScore)
    }

    @Test fun `blanking removes cancellation source effect`() {
        // rune(1) + basilisk(35) + knights(20)
        // basilisk blanks knights AND rune? rune is ARTIFACT, basilisk blanks army+leader+beast → rune spared.
        // rune cancels knights → but knights blanked anyway.
        val r = TestFixture.score("protection_rune", "basilisk", "knights")
        assertTrue("knights" in r.blankedKeys)
        assertEquals(36, r.totalScore)
    }
}
