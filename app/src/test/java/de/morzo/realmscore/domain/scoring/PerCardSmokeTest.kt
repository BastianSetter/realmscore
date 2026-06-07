package de.morzo.realmscore.domain.scoring

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * One probe per card. Each scenario builds a hand designed to fire that card's main rule
 * and asserts the total score. Builds confidence that the registry is wired correctly.
 *
 * Where a card has tiered behavior (King, Queen, Shield, Sword, Unicorn) further dedicated
 * test files cover the other tiers.
 */
class PerCardSmokeTest {

    // ── ARMY ────────────────────────────────────────────────────────────────
    @Test fun `rangers +10 per land`() {
        // rangers(5) + forest(7) + cavern(6) + mountain(9) = 27 base + 30 (3 lands) = 57
        val r = TestFixture.score("rangers", "forest", "cavern", "mountain")
        assertEquals(57, r.totalScore)
    }

    @Test fun `elven_archers +5 without weather`() {
        // 10 base + 5 (no weather)
        val r = TestFixture.score("elven_archers")
        assertEquals(15, r.totalScore)
    }

    @Test fun `dwarvish_infantry -2 per other army`() {
        // dwarvish(15) + elven_archers(10) + knights(20)
        // knights: no leader → -8 → 12
        // dwarvish: 2 other armies → -4 → 11
        // elven_archers: no weather → +5 → 15
        // total = 11 + 15 + 12 = 38
        val r = TestFixture.score("dwarvish_infantry", "elven_archers", "knights")
        assertEquals(38, r.totalScore)
    }

    @Test fun `light_cavalry -2 per land`() {
        // cavalry(17) + forest(7) + cavern(6) - 2*2 = 30 - 4 = 26
        val r = TestFixture.score("light_cavalry", "forest", "cavern")
        assertEquals(26, r.totalScore)
    }

    @Test fun `knights -8 without leader`() {
        // 20 - 8 = 12
        val r = TestFixture.score("knights")
        assertEquals(12, r.totalScore)
    }

    // ── ARTEFACT ────────────────────────────────────────────────────────────
    @Test fun `protection_rune cancels all penalties`() {
        // rune(1) + knights(20) — rune cancels knights' "no leader" penalty
        val r = TestFixture.score("protection_rune", "knights")
        assertEquals(21, r.totalScore)
    }

    @Test fun `world_tree +50 for unique suits`() {
        // 7 cards each different suit: world_tree(ARTIFACT 2), rangers(ARMY 5),
        // hydra(BEAST 12), candle(FLAME 2), island(FLOOD 14), mountain(LAND 9),
        // princess(LEADER 2). All unique. Base=46 + 50 = 96.
        // BUT hydra needs swamp for +28 (no), candle needs combo (no), etc.
        // mountain: needs smoke+wildfire (no), but its cancellation effect doesn't matter without floods
        // island: cancellation has no target → no effect
        // princess: +8 per army/wizard/leader: 1 army → +8
        // rangers: +10 per land: mountain → +10
        // total = 2+5+12+2+14+9+2 base = 46 + 50 (tree) + 8 (princess) + 10 (rangers) = 114
        val r = TestFixture.score("world_tree", "rangers", "hydra", "candle", "island", "mountain", "princess")
        assertEquals(114, r.totalScore)
    }

    @Test fun `shield_of_keth +15 with leader`() {
        // shield(4) + princess(2) + princess armies=0 → princess has no other army
        // shield gets +15 with leader. Total = 4+2+15 = 21
        val r = TestFixture.score("shield_of_keth", "princess")
        assertEquals(21, r.totalScore)
    }

    @Test fun `gem_of_order run of 3`() {
        // gem(5), warhorse(6), princess(2). Strengths 2,5,6. Longest run 5-6 = 2. No bonus.
        // Let's do 4,5,6: shield(4), gem(5), warhorse(6) → run of 3 → +10
        // shield needs leader → no bonus. warhorse needs leader/wizard → no bonus.
        // total = 4+5+6+10 = 25
        val r = TestFixture.score("shield_of_keth", "gem_of_order", "warhorse")
        assertEquals(25, r.totalScore)
    }

    // ── BEAST ───────────────────────────────────────────────────────────────
    @Test fun `warhorse +14 with wizard`() {
        // warhorse(6) + enchantress(5) +14 (wizard present)
        // enchantress +5 per land/weather/flood/flame = 0. Total = 6+5+14 = 25
        val r = TestFixture.score("warhorse", "enchantress")
        assertEquals(25, r.totalScore)
    }

    @Test fun `unicorn +30 with princess`() {
        // unicorn(9) + princess(2) + 30 (princess bonus) + 8 (princess for unicorn? no, unicorn is BEAST not army/wizard/leader)
        // princess +8 per army/wizard/other-leader = 0
        // total = 9+2+30 = 41
        val r = TestFixture.score("unicorn", "princess")
        assertEquals(41, r.totalScore)
    }

    @Test fun `hydra +28 with swamp`() {
        // hydra(12) + swamp(18 -3*0 since no army/flame) + 28 = 12+18+28 = 58
        val r = TestFixture.score("hydra", "swamp")
        assertEquals(58, r.totalScore)
    }

    @Test fun `dragon -40 without wizard`() {
        // 30 - 40 = -10
        val r = TestFixture.score("dragon")
        assertEquals(-10, r.totalScore)
    }

    @Test fun `basilisk blanks armies`() {
        // basilisk(35) + rangers(5) + knights(20). Rangers + knights blanked.
        // basilisk: 35. Rangers: 0. Knights: 0. Total = 35.
        val r = TestFixture.score("basilisk", "rangers", "knights")
        assertEquals(35, r.totalScore)
    }

    // ── FLAME ───────────────────────────────────────────────────────────────
    @Test fun `candle +100 with combo`() {
        // candle(2) + book(3) + bell_tower(8) + enchantress(5)
        // bell_tower +15 with wizard = +15
        // enchantress +5 per land/weather/flood/flame: bell_tower=LAND, candle=FLAME → 2 → +10
        // candle: +100
        // total = 2+3+8+5+100+15+10 = 143
        val r = TestFixture.score("candle", "book_of_changes", "bell_tower", "enchantress")
        assertEquals(143, r.totalScore)
    }

    @Test fun `fire_elemental +15 per other flame`() {
        // fire(4) + candle(2) + lightning(11). 2 other flames → +30.
        // candle needs combo → 0; lightning needs rainstorm → 0
        // total = 4+2+11+30 = 47
        val r = TestFixture.score("fire_elemental", "candle", "lightning")
        assertEquals(47, r.totalScore)
    }

    @Test fun `forge +9 per weapon or artifact`() {
        // forge(9) + magic_wand(1) + sword_of_keth(7)
        // forge: 2 weapons → +18
        // wand: no wizard → 0; sword: no leader → 0
        // total = 9+1+7+18 = 35
        val r = TestFixture.score("forge", "magic_wand", "sword_of_keth")
        assertEquals(35, r.totalScore)
    }

    @Test fun `lightning +30 with rainstorm`() {
        // lightning(11) + rainstorm(8). rainstorm +10 per flood = 0. lightning +30.
        // rainstorm's blanking of flames-except-lightning doesn't affect lightning itself.
        // total = 11+8+30 = 49
        val r = TestFixture.score("lightning", "rainstorm")
        assertEquals(49, r.totalScore)
    }

    @Test fun `wildfire blanks most cards`() {
        // wildfire(40) + rangers(5) [ARMY, NOT in allowed → blanked]
        //              + magic_wand(1) [WEAPON, allowed]
        // wand needs wizard → 0. rangers: blanked.
        // total = 40+1 = 41
        val r = TestFixture.score("wildfire", "rangers", "magic_wand")
        assertEquals(41, r.totalScore)
    }

    // ── FLOOD ───────────────────────────────────────────────────────────────
    @Test fun `fountain_of_life adds chosen base strength`() {
        // fountain(1) + dragon(30 BEAST → not eligible) + swamp(18 FLOOD → eligible)
        // dragon without wizard → -40. swamp -3 per army/flame = 0.
        // Choose swamp for fountain → +18
        // total without choice = 1 + 30 - 40 + 18 = 9
        // with choice = 9 + 18 = 27
        val input = ScoringInput(
            hand = TestFixture.hand("fountain_of_life", "dragon", "swamp"),
            playerChoices = PlayerChoices(fountainSourceKey = "swamp"),
        )
        val r = TestFixture.engine.score(input)
        assertEquals(27, r.totalScore)
    }

    @Test fun `water_elemental +15 per other flood`() {
        // water(4) + island(14) + swamp(18 −3*0) = 36 + 30 = 66
        val r = TestFixture.score("water_elemental", "island", "swamp")
        assertEquals(66, r.totalScore)
    }

    @Test fun `island cancels chosen flood penalty`() {
        // swamp(18) + island(14) + elven_archers(10,army) + candle(2,flame)
        // Without island: swamp -3*2 (army+flame) = -6 → 12
        // With island target=swamp → swamp penalty cancelled → 18
        // elven_archers: no weather → +5
        // total = 18+14+10+2+5 = 49
        val input = ScoringInput(
            hand = TestFixture.hand("swamp", "island", "elven_archers", "candle"),
            playerChoices = PlayerChoices(islandTargetKey = "swamp"),
        )
        val r = TestFixture.engine.score(input)
        assertEquals(49, r.totalScore)
    }

    @Test fun `swamp -3 per army and flame`() {
        // swamp(18) + elven_archers(10,army) + candle(2,flame). 1 army + 1 flame → -6
        // elven_archers: no weather → +5
        // total = 18+10+2-6+5 = 29
        val r = TestFixture.score("swamp", "elven_archers", "candle")
        assertEquals(29, r.totalScore)
    }

    @Test fun `great_flood blanks armies and lands but spares lightning`() {
        // flood(32) + rangers(5,army→blanked) + forest(7,land→blanked) + lightning(11,flame→spared)
        // No Gebirge here, so the Flood's penalty is NOT cleared and its blanking applies.
        // rangers blanked, forest blanked, lightning spared (named flame exception).
        // lightning: rainstorm? no → 0
        // total = 32 + 11 = 43
        val r = TestFixture.score("great_flood", "rangers", "forest", "lightning")
        assertTrue("rangers" in r.blankedKeys)
        assertTrue("forest" in r.blankedKeys)
        assertTrue("lightning" !in r.blankedKeys)
        assertEquals(43, r.totalScore)
    }

    // ── LAND ────────────────────────────────────────────────────────────────
    @Test fun `earth_elemental +15 per other land`() {
        // earth(4) + forest(7) + cavern(6). 2 other lands → +30
        // forest +12 per beast/archer = 0. cavern +25 needs dwarvish OR dragon = 0
        // total = 4+7+6+30 = 47
        val r = TestFixture.score("earth_elemental", "forest", "cavern")
        assertEquals(47, r.totalScore)
    }

    @Test fun `cavern +25 with dwarves`() {
        // cavern(6) + dwarvish(15). dwarvish: 0 other armies → 0 penalty. cavern +25.
        // total = 6+15+25 = 46
        val r = TestFixture.score("cavern", "dwarvish_infantry")
        assertEquals(46, r.totalScore)
    }

    @Test fun `forest +12 per beast`() {
        // forest(7) + dragon(30 no wizard −40 = -10) + hydra(12 no swamp = 0)
        // forest: 2 beasts → +24
        // total = 7-10+12+24 = 33
        val r = TestFixture.score("forest", "dragon", "hydra")
        assertEquals(33, r.totalScore)
    }

    @Test fun `bell_tower +15 with wizard`() {
        // bell(8) + enchantress(5). enchantress +5 per land/weather/flood/flame: 1 land → +5
        // total = 8+5+15+5 = 33
        val r = TestFixture.score("bell_tower", "enchantress")
        assertEquals(33, r.totalScore)
    }

    @Test fun `mountain +50 with smoke and wildfire`() {
        // mountain(9) + smoke(27) + wildfire(40)
        // wildfire blanks all except allowed: smoke(WEATHER ok), mountain(LAND not in allowed-suit but in allowed-keys → kept)
        // smoke: self-blank without flame → wildfire IS flame → smoke NOT blanked
        // smoke: not blanked. mountain not blanked.
        // mountain: +50 with smoke+wildfire combo
        // wildfire: 40 base. mountain: 9 + 50 = 59. smoke: 27.
        // total = 40 + 59 + 27 = 126
        val r = TestFixture.score("mountain", "smoke", "wildfire")
        assertEquals(126, r.totalScore)
    }

    // ── LEADER ──────────────────────────────────────────────────────────────
    @Test fun `princess +8 per target`() {
        // princess(2) + rangers(5,army) + enchantress(5,wizard) + king(6,leader)
        // princess: 1 army + 1 wizard + 1 leader = 3 → +24
        // rangers +10 per land = 0
        // enchantress +5 per land/weather/flood/flame = 0
        // king: 1 army, no queen → +5
        // total = 2+5+5+6+24+5 = 47
        val r = TestFixture.score("princess", "rangers", "enchantress", "king")
        assertEquals(47, r.totalScore)
    }

    @Test fun `warlord adds army strengths`() {
        // warlord(4) + rangers(5) + knights(20-8 = 12 no leader? wait, warlord IS leader)
        // warlord IS leader → knights penalty doesn't fire. knights = 20.
        // warlord bonus: sum of army base strengths = 5 + 20 = 25
        // total = 4+5+20+25 = 54
        val r = TestFixture.score("warlord", "rangers", "knights")
        assertEquals(54, r.totalScore)
    }

    @Test fun `king alone +5 per army`() {
        // king(6) + rangers(5) → king +5 → total = 6+5+5 = 16
        val r = TestFixture.score("king", "rangers")
        assertEquals(16, r.totalScore)
    }

    @Test fun `queen alone +5 per army`() {
        // queen(8) + rangers(5) → queen +5 → 8+5+5 = 18
        val r = TestFixture.score("queen", "rangers")
        assertEquals(18, r.totalScore)
    }

    @Test fun `empress +10 per army -5 per leader`() {
        // empress(15) + rangers(5) + king(6)
        // empress: +10 per army = +10. -5 per other leader = -5.
        // king: 1 army, no queen → +5
        // total = 15+5+6+10-5+5 = 36
        val r = TestFixture.score("empress", "rangers", "king")
        assertEquals(36, r.totalScore)
    }

    // ── WEAPON ──────────────────────────────────────────────────────────────
    @Test fun `magic_wand +25 with wizard`() {
        // wand(1) + enchantress(5,land/weather/flood/flame count=0) → 1+5+25 = 31
        val r = TestFixture.score("magic_wand", "enchantress")
        assertEquals(31, r.totalScore)
    }

    @Test fun `elven_longbow +30 with warlord`() {
        // bow(3) + warlord(4 sum-of-army=0) → 3+4+30 = 37
        val r = TestFixture.score("elven_longbow", "warlord")
        assertEquals(37, r.totalScore)
    }

    @Test fun `sword_of_keth +10 with leader`() {
        // sword(7) + princess(2,bonus=0) → 7+2+10 = 19
        val r = TestFixture.score("sword_of_keth", "princess")
        assertEquals(19, r.totalScore)
    }

    @Test fun `warship blanked without flood`() {
        // warship(23) alone → blanked → 0
        val r = TestFixture.score("warship")
        assertEquals(0, r.totalScore)
    }

    @Test fun `war_dirigible blanked without army`() {
        // dirigible(35) alone → blanked → 0
        val r = TestFixture.score("war_dirigible")
        assertEquals(0, r.totalScore)
    }

    // ── WEATHER ─────────────────────────────────────────────────────────────
    @Test fun `air_elemental +15 per other weather`() {
        // air(4) + smoke(27,self-blanked no flame) + blizzard(30)
        // smoke blanked → 0; air's count uses non-blanked hand → only blizzard = 1 → +15
        // blizzard: -5 per army/leader/beast/flame = 0. Blanks all floods (none).
        // total = 4 + 0 + 30 + 15 = 49
        val r = TestFixture.score("air_elemental", "smoke", "blizzard")
        assertEquals(49, r.totalScore)
    }

    @Test fun `rainstorm +10 per flood and blanks flames`() {
        // rainstorm(8) + great_flood(32) + candle(2,FLAME→blanked) + lightning(11,FLAME→spared)
        // rainstorm: 1 flood → +10
        // great_flood: blanks rangers (none here). lightning +30 (rainstorm present) → spared by rainstorm.
        // candle: blanked. lightning: 11+30 = 41.
        // total = 8 + 32 + 0 + 41 + 10 = 91
        val r = TestFixture.score("rainstorm", "great_flood", "candle", "lightning")
        assertEquals(91, r.totalScore)
    }

    @Test fun `whirlwind +40 with rainstorm and blizzard`() {
        // whirlwind(13) + rainstorm(8) + blizzard(30).
        // rainstorm +10 per flood = 0. blizzard: -5 per army/leader/beast/flame = 0. blanks floods (none).
        // whirlwind +40.
        // total = 13+8+30+40 = 91
        val r = TestFixture.score("whirlwind", "rainstorm", "blizzard")
        assertEquals(91, r.totalScore)
    }

    @Test fun `smoke blanked without flame`() {
        val r = TestFixture.score("smoke")
        assertEquals(0, r.totalScore)
    }

    @Test fun `blizzard -5 per target`() {
        // blizzard(30) + elven_archers(10,army) + king(6,leader) + dragon(30,beast)
        // blizzard: 3 targets (army+leader+beast) → -15
        // elven_archers: no weather → blizzard IS weather → 0
        // king: 1 army (elven_archers) → +5 (no queen)
        // dragon: no wizard → -40
        // total = 30+10+6+30 - 15 + 0 + 5 - 40 = 26
        val r = TestFixture.score("blizzard", "elven_archers", "king", "dragon")
        assertEquals(26, r.totalScore)
    }

    // ── WILD (jokers) covered by JokerSubstitutionTest ──────────────────────

    // ── WIZARD ──────────────────────────────────────────────────────────────
    @Test fun `necromancer stub contributes base only`() {
        val r = TestFixture.score("necromancer")
        assertEquals(3, r.totalScore)
    }

    @Test fun `enchantress +5 per land weather flood flame`() {
        // ench(5) + forest(7,land) + smoke(27 self-blank without flame, blizzard is weather → smoke is weather not flame → blanked)
        // hmm: smoke needs FLAME, not WEATHER. So smoke blanked.
        // Let's avoid that. ench + forest + island + lightning(no rainstorm = 0)
        // forest: 0 beasts → 0
        // island: cancellation no target → no effect
        // lightning: 0 (no rainstorm)
        // ench: 1 land + 1 flood + 1 flame = 3 → +15
        // total = 5+7+14+11+15 = 52
        val r = TestFixture.score("enchantress", "forest", "island", "lightning")
        assertEquals(52, r.totalScore)
    }

    @Test fun `collector tiered`() {
        // collector(7) + warhorse(6,beast) + unicorn(9,beast) + hydra(12,beast)
        // 4 cards: collector(WIZARD), 3 beasts. Best suit = BEAST with 3 distinct → +10
        // warhorse: leader/wizard present? collector is WIZARD → +14
        // unicorn: princess no, queen/empress/enchantress no → 0
        // hydra: swamp no → 0
        // total = 7+6+9+12+10+14 = 58
        val r = TestFixture.score("collector", "warhorse", "unicorn", "hydra")
        assertEquals(58, r.totalScore)
    }

    @Test fun `beastmaster +9 per beast`() {
        // beastmaster(9) + warhorse(6) + dragon(30 wizard present → no penalty)
        // warhorse +14 (wizard present)
        // dragon: wizard present → no -40
        // beastmaster: 2 beasts → +18
        // total = 9+6+30+14+18 = 77
        val r = TestFixture.score("beastmaster", "warhorse", "dragon")
        assertEquals(77, r.totalScore)
    }

    @Test fun `warlock -10 per leader and wizard`() {
        // warlock(25) + princess(2) + enchantress(5).
        // warlock: 1 leader (princess) + 1 other wizard (enchantress) = 2 → -20
        // princess +8 per army/wizard/leader: 2 wizards (warlock+enchantress) = +16
        // ench: 0 land/weather/flood/flame = 0
        // total = 25+2+5-20+16 = 28
        val r = TestFixture.score("warlock", "princess", "enchantress")
        assertEquals(28, r.totalScore)
    }
}
