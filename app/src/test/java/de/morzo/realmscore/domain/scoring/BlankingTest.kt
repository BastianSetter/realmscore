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

    @Test fun `beastmaster clears basilisk penalty so no beasts are blanked`() {
        // Reported bug: beastmaster(9,wizard) + basilisk(35,beast) + dragon(30,beast)
        // Herr der Bestien "hebt die Strafe auf allen Bestien auf". Basilisk's penalty IS its
        // blanking, so once cleared the Basilisk blanks nothing — beasts (and armies/leaders)
        // all survive.
        // beastmaster: +9 per Beast → basilisk + dragon = 2 → +18 → 27
        // basilisk: penalty cleared → 35
        // dragon: wizard present (beastmaster) → no penalty → 30
        // total = 27 + 35 + 30 = 92
        val r = TestFixture.score("beastmaster", "basilisk", "dragon")
        assertTrue(r.blankedKeys.isEmpty())
        assertEquals(92, r.totalScore)
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

    @Test fun `mountain clears great_flood penalty so it blanks nothing`() {
        // flood(32) + mountain(9) + lightning(11) + rangers(5,army)
        // Gebirge "hebt die Strafen auf allen Fluten auf" → Große Flut (Flood) penalty cleared.
        // Blanking IS that penalty, so the Flood blanks nobody — not just the named exceptions.
        // mountain: smoke+wildfire? no → 0 bonus
        // lightning: rainstorm? no → 0
        // rangers: +10 per Land → mountain is the only Land → +10
        // total = 32+9+11+(5+10) = 67
        val r = TestFixture.score("great_flood", "mountain", "lightning", "rangers")
        assertTrue(r.blankedKeys.isEmpty())
        assertEquals(67, r.totalScore)
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

    @Test fun `clearing is permanent even when the clearer is blanked`() {
        // Official rulebook example: cavern(6,land) + blizzard(30,weather) + great_flood(32,flood)
        // + wildfire(40,flame).
        // Clearing first: Höhle clears all Weather penalties → Blizzard's penalty (incl. its
        // flood-blanking) is cleared. THEN penalties: Große Flut (not cleared) blanks all Flames
        // (wildfire) and all Land except Gebirge (cavern). Wildfire would blank Land too, but it
        // gets drowned first; great_flood is a named exception for wildfire anyway.
        // Even though the Höhle is now blanked, it has still cleared the Blizzard.
        // Active cards: blizzard + great_flood. Blanked: cavern + wildfire.
        val r = TestFixture.score("cavern", "blizzard", "great_flood", "wildfire")
        assertTrue("cavern" in r.blankedKeys)
        assertTrue("wildfire" in r.blankedKeys)
        assertTrue("blizzard" !in r.blankedKeys)
        assertTrue("great_flood" !in r.blankedKeys)
        // blizzard: penalty cleared by cavern → 0; no floods left active anyway. base 30.
        // great_flood: base 32, its penalty is the (now spent) blanking.
        // total = 30 + 32 = 62
        assertEquals(62, r.totalScore)
    }
}
