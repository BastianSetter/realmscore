package de.morzo.realmscore.domain.scoring

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OptimalSolverTest {

    @Test fun `solver picks best island target`() {
        // Island only cancels FLOOD or FLAME penalties. swamp has the largest such penalty here.
        // swamp(18, -3 per army+flame), elven_archers(10, army, +5 no weather),
        // candle(2, flame), island(14)
        // Without island choice: swamp counts 1 army (archers) + 1 flame (candle) → -6 → 12.
        //                       total = 12 + 15 + 2 + 14 = 43.
        // With island=swamp:    swamp penalty cancelled → 18.
        //                       total = 18 + 15 + 2 + 14 = 49.
        val seed = ScoringInput(
            hand = TestFixture.hand("swamp", "elven_archers", "candle", "island"),
        )
        val best = TestFixture.solver.findOptimal(seed)
        assertEquals("swamp", best.bestInput.jokerAssignments["island"]?.targetCardKey)
        assertEquals(49, best.bestResult.totalScore)
    }

    @Test fun `solver assigns mirage to maximise score`() {
        // hand: mirage + rangers + cavern + forest + knights(no leader) + magic_wand + bell_tower
        // Better assignment: mirage as a Land like swamp? Strength is irrelevant since mirage str=0.
        // mirage→swamp (FLOOD) doesn't gain anything obvious. mirage→rangers? same suit pattern.
        // Just verify the solver returns SOME assignment that beats unassigned.
        val seed = ScoringInput(
            hand = TestFixture.hand(
                "mirage",
                "rangers",
                "cavern",
                "forest",
                "knights",
                "magic_wand",
                "bell_tower",
            ),
        )
        val unassigned = TestFixture.engine.score(seed)
        val best = TestFixture.solver.findOptimal(seed)
        assertTrue(best.bestResult.totalScore >= unassigned.totalScore)
    }

    @Test fun `solver assigns a target even when the joker effect is irrelevant`() {
        // Same irrelevant-effect hand as above. The solver must still hand the mirage a valid
        // target instead of leaving it unset.
        val seed = ScoringInput(
            hand = TestFixture.hand(
                "mirage",
                "rangers",
                "cavern",
                "forest",
                "knights",
                "magic_wand",
                "bell_tower",
            ),
        )
        val best = TestFixture.solver.findOptimal(seed)
        assertTrue(
            "mirage should be assigned a target",
            best.bestInput.jokerAssignments["mirage"]?.targetCardKey != null,
        )
    }

    @Test fun `solver assigns island even when no penalty to cancel`() {
        // water_elemental(FLOOD) carries no penalty, so Island has nothing to cancel — its target
        // is irrelevant to the score. The solver should still hand Island a valid target instead of
        // leaving it unset.
        val seed = ScoringInput(
            hand = TestFixture.hand("island", "water_elemental"),
        )
        val best = TestFixture.solver.findOptimal(seed)
        assertEquals("water_elemental", best.bestInput.jokerAssignments["island"]?.targetCardKey)
    }

    @Test fun `solver picks highest-strength fountain source`() {
        // Note: fountain itself is FLOOD, so warship has its required flood and is NOT self-blanked.
        // Eligible sources (WEAPON/FLOOD/FLAME/LAND/WEATHER, strength>0): warship(23), sword(7), air(4).
        // Solver picks warship (highest base strength).
        val seed = ScoringInput(
            hand = TestFixture.hand("fountain_of_life", "warship", "sword_of_keth", "air_elemental"),
        )
        val best = TestFixture.solver.findOptimal(seed)
        assertEquals("warship", best.bestInput.jokerAssignments["fountain_of_life"]?.targetCardKey)
    }

    @Test fun `solver picks best necromancer card when discard is scanned`() {
        // necromancer(3) + beastmaster(9). Discard holds unicorn(BEAST,9) and rangers(ARMY,5).
        // Pulling unicorn: beastmaster sees 1 beast (+9) + unicorn base 9 → 3+9+9+9 = 30.
        // Pulling rangers: no beast bonus → 3+9+5 = 17. Solver must choose unicorn.
        val seed = ScoringInput(
            hand = TestFixture.hand("necromancer", "beastmaster"),
            discardPile = TestFixture.hand("unicorn", "rangers"),
            discardScanned = true,
        )
        val best = TestFixture.solver.findOptimal(seed)
        assertEquals("unicorn", best.bestInput.playerChoices.necromancerPickKey)
        assertEquals(30, best.bestResult.totalScore)
    }

    @Test fun `solver leaves necromancer pick untouched when discard is not scanned`() {
        // Without a scanned discard the pick is carried through unchanged (here: none).
        val seed = ScoringInput(
            hand = TestFixture.hand("necromancer", "beastmaster"),
            discardPile = TestFixture.hand("unicorn", "rangers"),
            discardScanned = false,
        )
        val best = TestFixture.solver.findOptimal(seed)
        assertEquals(null, best.bestInput.playerChoices.necromancerPickKey)
    }

    @Test fun `solver skips blanked fountain source`() {
        // smoke(WEATHER 27) self-blanks (no flame). Better source: air_elemental(4) — not blanked but tiny.
        // Even though smoke has higher base strength, Fountain rule excludes blanked sources.
        val seed = ScoringInput(
            hand = TestFixture.hand("fountain_of_life", "smoke", "air_elemental"),
        )
        val best = TestFixture.solver.findOptimal(seed)
        assertEquals("air_elemental", best.bestInput.jokerAssignments["fountain_of_life"]?.targetCardKey)
    }

    @Test fun `solver lets fountain draw from a doppelganger-resolved card`() {
        // Regression (Phase 23): Doppelganger→wildfire makes a Flame-40 card; the Fountain then
        // legally draws from that copy. The pick must surface as a jokerAssignment keyed by the
        // fountain (so the UI row resolves it via the effective hand) rather than being left unset.
        val seed = ScoringInput(
            hand = TestFixture.hand(
                "doppelganger",
                "protection_rune",
                "fountain_of_life",
                "mountain",
                "smoke",
                "lightning",
                "wildfire",
            ),
        )
        val best = TestFixture.solver.findOptimal(seed)
        assertEquals(219, best.bestResult.totalScore)
        assertEquals("wildfire", best.bestInput.jokerAssignments["doppelganger"]?.targetCardKey)
        assertEquals(
            "doppelganger",
            best.bestInput.jokerAssignments["fountain_of_life"]?.targetCardKey,
        )
    }
}
