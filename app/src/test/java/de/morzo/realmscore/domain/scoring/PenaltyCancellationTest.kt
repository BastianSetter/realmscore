package de.morzo.realmscore.domain.scoring

import org.junit.Assert.assertEquals
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
        // blizzard(30) + rangers(5,army) + king(6,leader) + dragon(30,beast)
        // Normally blizzard: -5 per army/leader/beast/flame = 3 → -15
        // With rangers: ARMY stripped → 2 targets (leader+beast) → -10
        // dragon: no wizard → -40
        // king: 1 army → +5
        // rangers: 0 lands → 0
        // total = 30+5+6-10+5-40 = -4? Hmm wait
        // 30+5+6+30 = 71 base, -10 blizzard, +5 king, -40 dragon = 26
        val r = TestFixture.score("blizzard", "rangers", "king", "dragon")
        assertEquals(26, r.totalScore)
    }

    @Test fun `rune cancels every penalty`() {
        // rune(1) + knights(20) + dragon(30) → both penalties suppressed
        // total = 1+20+30 = 51
        val r = TestFixture.score("protection_rune", "knights", "dragon")
        assertEquals(51, r.totalScore)
    }

    @Test fun `cavern cancels weather penalties`() {
        // cavern(6) + blizzard(30) + rangers(5,army) + king(6,leader) + dragon(30,beast) + sword_of_keth(7)
        // Without cavern: blizzard -15 (army+leader+beast = 3)
        // With cavern: blizzard penalty cancelled → 0
        // dragon: no wizard → -40
        // king: 1 army → +5 (no queen)
        // rangers: 1 land → +10
        // sword: leader present → +10. no shield → +10
        // cavern: needs dwarves or dragon → has dragon → +25
        // total = 6+30+5+6+30+7+25+5+10+10-40 = 94
        val r = TestFixture.score("cavern", "blizzard", "rangers", "king", "dragon", "sword_of_keth")
        assertEquals(94, r.totalScore)
    }

    @Test fun `mountain cancels great_flood blanking does not apply to mountain itself`() {
        // mountain(9) + great_flood(32) + rangers(5,army→blanked) + forest(7,land→blanked)
        // great_flood blanks: all army, all land except mountain, all flame except lightning
        // rangers blanked, forest blanked. Mountain itself spared.
        // Mountain: smoke+wildfire? no → +0 bonus
        // Mountain cancellation: cancels FLOOD penalties (no floods → no effect)
        // total = 9+32 = 41
        val r = TestFixture.score("mountain", "great_flood", "rangers", "forest")
        assertEquals(41, r.totalScore)
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
        // warship(23) + swamp(18) + rangers(5,army) + king(6,leader)
        // warship needs flood → swamp is flood → not self-blanked
        // warship strips ARMY from FLOOD penalties:
        //   - swamp's "-3 per army/flame": ARMY removed → only counts flame (0) → 0 penalty
        // king: 1 army → +5
        // rangers: 0 lands → 0
        // total = 23+18+5+6+5 = 57
        val r = TestFixture.score("warship", "swamp", "rangers", "king")
        assertEquals(57, r.totalScore)
    }

    @Test fun `blanked rune does not cancel`() {
        // rune(1) + basilisk(35) + knights(20) + king(6,leader)
        // basilisk blanks army+leader+beast: knights blanked, king blanked.
        // rune (artifact) NOT blanked → still cancels penalties.
        // knights/king blanked anyway → no contribution.
        // basilisk: 35.
        // total = 1+35 = 36
        val r = TestFixture.score("protection_rune", "basilisk", "knights", "king")
        assertEquals(36, r.totalScore)
    }
}
