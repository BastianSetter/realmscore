package de.morzo.realmscore.domain.scoring

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PenaltyCancellationTest {

    @Test fun `rangers strips ARMY from swamp penalty`() {
        // swamp(18) + rangers(5,army) + candle(2,flame)
        // Normally swamp: -3 per army+flame = -6 → 14
        // With rangers: ARMY stripped → only flame counts → -3 → 17
        // rangers: 0 lands → 0 bonus.
        // total = 18+5+2-3 = 22
        val r = TestFixture.score("swamp", "rangers", "candle")
        assertEquals(22, r.totalScore)
    }

    @Test fun `rangers strips ARMY from blizzard penalty`() {
        // blizzard(30) + rangers(5,army) + king(8,leader) + dragon(30,beast)
        // Normally blizzard: -5 per army/leader/beast/flame = 3 → -15
        // With rangers: ARMY stripped → 2 targets (leader+beast) → -10
        // dragon: no wizard → -40
        // king: 1 army → +5
        // rangers: 0 lands → 0
        // 30+5+8+30 = 73 base, -10 blizzard, +5 king, -40 dragon = 28
        val r = TestFixture.score("blizzard", "rangers", "king", "dragon")
        assertEquals(28, r.totalScore)
    }

    @Test fun `rune cancels every penalty`() {
        // rune(1) + knights(20) + dragon(30) → both penalties suppressed
        // total = 1+20+30 = 51
        val r = TestFixture.score("protection_rune", "knights", "dragon")
        assertEquals(51, r.totalScore)
    }

    @Test fun `cavern cancels weather penalties`() {
        // cavern(6) + blizzard(30) + rangers(5,army) + king(8,leader) + dragon(30,beast) + sword_of_keth(7)
        // Without cavern: blizzard -15 (army+leader+beast = 3)
        // With cavern: blizzard penalty cancelled → 0
        // dragon: no wizard → -40
        // king: 1 army → +5 (no queen)
        // rangers: 1 land → +10
        // sword: leader present → +10. no shield → +10
        // cavern: needs dwarves or dragon → has dragon → +25
        // total = 6+30+5+8+30+7+25+5+10+10-40 = 96
        val r = TestFixture.score("cavern", "blizzard", "rangers", "king", "dragon", "sword_of_keth")
        assertEquals(96, r.totalScore)
    }

    @Test fun `mountain clears great_flood penalty so nothing is blanked`() {
        // mountain(9) + great_flood(32) + rangers(5,army) + forest(7,land)
        // Gebirge cancels every FLOOD penalty. Große Flut is a Flood and its penalty IS the
        // blanking, so it blanks nobody — rangers and forest both survive.
        // mountain: smoke+wildfire? no → +0 bonus
        // great_flood: penalty cleared → just base 32
        // rangers: +10 per Land → mountain + forest = 2 Lands → +20
        // forest: +12 per Beast/archer → none → 0
        // total = 9 + 32 + (5+20) + 7 = 73
        val r = TestFixture.score("mountain", "great_flood", "rangers", "forest")
        assertTrue(r.blankedKeys.isEmpty())
        assertEquals(73, r.totalScore)
    }

    @Test fun `island cancels chosen flood penalty`() {
        // swamp(18) + island(14) + rangers(5,army) + candle(2,flame)
        // Without island: swamp -6 (army+flame) → 12
        // With island target=swamp: swamp penalty cancelled → 18
        // rangers: 0 lands → 0
        // total = 18+14+5+2 = 39
        val input = ScoringInput(
            hand = TestFixture.hand("swamp", "island", "rangers", "candle"),
            playerChoices = PlayerChoices(islandTargetKey = "swamp"),
        )
        val r = TestFixture.engine.score(input)
        assertEquals(39, r.totalScore)
    }

    @Test fun `beastmaster cancels beast penalties`() {
        // beastmaster(9) + dragon(30) + dwarvish_infantry(15) — dragon no wizard but beastmaster IS wizard? YES wizard
        // dragon: wizard present → no penalty
        // dwarvish: 0 other armies → 0 penalty
        // beastmaster: 1 beast → +9
        // total = 9+30+15+9 = 63
        val r = TestFixture.score("beastmaster", "dragon", "dwarvish_infantry")
        assertEquals(63, r.totalScore)
    }

    @Test fun `warship strips ARMY from flood penalty only`() {
        // warship(23) + swamp(18) + rangers(5,army) + king(8,leader)
        // warship needs flood → swamp is flood → not self-blanked
        // warship strips ARMY from FLOOD penalties:
        //   - swamp's "-3 per army/flame": ARMY removed → only counts flame (0) → 0 penalty
        // king: 1 army → +5
        // rangers: 0 lands → 0
        // total = 23+18+5+8+5 = 59
        val r = TestFixture.score("warship", "swamp", "rangers", "king")
        assertEquals(59, r.totalScore)
    }

    @Test fun `rune clears basilisk penalty so nothing is blanked`() {
        // rune(1) + basilisk(35) + knights(20) + king(8,leader)
        // Rune des Schutzes clears every penalty. Basilisk's penalty IS its blanking, so it
        // blanks nothing → knights and king both survive (and their own penalties are cleared too).
        // knights: no-leader penalty cleared (and king is a leader anyway) → 20
        // king: +5 per Army → knights is the only Army → +5
        // total = 1 + 35 + 20 + (8+5) = 69
        val r = TestFixture.score("protection_rune", "basilisk", "knights", "king")
        assertTrue(r.blankedKeys.isEmpty())
        assertEquals(69, r.totalScore)
    }
}
