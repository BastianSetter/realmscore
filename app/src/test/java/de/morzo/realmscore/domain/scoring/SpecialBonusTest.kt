package de.morzo.realmscore.domain.scoring

import org.junit.Assert.assertEquals
import org.junit.Test

class SpecialBonusTest {

    // ── King + Queen Synergy ────────────────────────────────────────────────
    @Test fun `king plus queen gives +20 per army each`() {
        // king(8) + queen(6) + rangers(5,army) + knights(20,army)
        // knights: leader present → no penalty (20)
        // king: 2 armies × 20 = +40 (queen present)
        // queen: 2 armies × 20 = +40 (king present)
        // rangers: 0 lands → 0
        // total = 8+6+5+20+40+40 = 119
        val r = TestFixture.score("king", "queen", "rangers", "knights")
        assertEquals(119, r.totalScore)
    }

    @Test fun `king without queen gives +5 per army`() {
        val r = TestFixture.score("king", "rangers", "knights")
        // knights: leader present → 20
        // king: 2 armies × 5 = +10
        // total = 8+5+20+10 = 43
        assertEquals(43, r.totalScore)
    }

    // ── Shield/Sword of Keth Tiers ──────────────────────────────────────────
    @Test fun `shield without sword +15 with leader`() {
        // shield(4) + princess(2) → +15
        val r = TestFixture.score("shield_of_keth", "princess")
        assertEquals(21, r.totalScore)
    }

    @Test fun `shield with sword and leader +40`() {
        // shield(4) + sword(7) + princess(2)
        // shield: leader+sword → +40
        // sword: leader+shield → +40
        // princess: 0 targets → 0
        // total = 4+7+2+40+40 = 93
        val r = TestFixture.score("shield_of_keth", "sword_of_keth", "princess")
        assertEquals(93, r.totalScore)
    }

    @Test fun `keth combo without leader gives nothing`() {
        val r = TestFixture.score("shield_of_keth", "sword_of_keth")
        assertEquals(11, r.totalScore)
    }

    // ── Gem of Order Runs ───────────────────────────────────────────────────
    @Test fun `gem run of 4`() {
        // strengths 3,4,5,6 → run of 4 → +30
        // book(3) + shield(4) + gem(5) + warhorse(6) — book is joker, no own bonus
        // shield: no leader → 0. warhorse: no leader/wizard → 0
        // total = 3+4+5+6+30 = 48
        val r = TestFixture.score("book_of_changes", "shield_of_keth", "gem_of_order", "warhorse")
        assertEquals(48, r.totalScore)
    }

    @Test fun `gem run of 7`() {
        // strengths 1,2,3,4,5,6,7 → run of 7 → +150
        // wand(1) + candle(2) + book(3) + shield(4) + gem(5) + warhorse(6) + sword(7)
        // wand: no wizard → 0
        // candle: no combo → 0
        // shield: no leader → 0
        // warhorse: no leader/wizard → 0
        // sword: no leader → 0
        // forge would help but not here.
        // total = 1+2+3+4+5+6+7+150 = 178
        val r = TestFixture.score("magic_wand", "candle", "book_of_changes", "shield_of_keth", "gem_of_order", "warhorse", "sword_of_keth")
        assertEquals(178, r.totalScore)
    }

    @Test fun `gem no run`() {
        // strengths 5,9,15: gem(5), mountain(9), empress(15) → no consecutive run of >=3
        val r = TestFixture.score("gem_of_order", "mountain", "empress")
        assertEquals(5 + 9 + 15, r.totalScore)
    }

    // ── Collector Tiers ─────────────────────────────────────────────────────
    @Test fun `collector tier 5`() {
        // collector(7) + necromancer(3) + enchantress(5) + beastmaster(9) + warlock(25)
        // All WIZARD. 5 distinct → +100
        // beastmaster: 0 beasts → 0 bonus
        // warlock: 4 other wizards → -40
        // enchantress: 0 land/weather/flood/flame → 0
        // total = 7+3+5+9+25+100-40 = 109
        val r = TestFixture.score("collector", "necromancer", "enchantress", "beastmaster", "warlock")
        assertEquals(109, r.totalScore)
    }

    // ── Unicorn ─────────────────────────────────────────────────────────────
    @Test fun `unicorn +15 with queen`() {
        // unicorn(9) + queen(6) + rangers(5,army)
        // queen: 1 army, no king → +5
        // unicorn: queen present → +15
        // rangers: 0 lands → 0
        // total = 9+6+5+5+15 = 40
        val r = TestFixture.score("unicorn", "queen", "rangers")
        assertEquals(40, r.totalScore)
    }

    // ── Warlord ─────────────────────────────────────────────────────────────
    @Test fun `warlord sums all army strengths`() {
        // warlord(4) + rangers(5) + elven_archers(10) + dwarvish(15) + light_cavalry(17) + knights(20)
        // knights: leader (warlord) present → 20
        // dwarvish: per-other-army would be -8, BUT rangers strips ARMY → 0 penalty
        // light_cav: 0 lands → 0
        // rangers: 0 lands → 0
        // elven_archers: 0 weather → +5
        // warlord sum = 5+10+15+17+20 = 67
        // total = 4+5+10+15+17+20+67+5 = 143
        val r = TestFixture.score("warlord", "rangers", "elven_archers", "dwarvish_infantry", "light_cavalry", "knights")
        assertEquals(143, r.totalScore)
    }
}
