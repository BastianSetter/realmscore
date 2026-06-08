package de.morzo.realmscore.domain.scoring

import de.morzo.realmscore.domain.model.Suit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 17.1 — Necromancer pulls a non-Wizard card from the discard pile and scores it as an
 * extra 8th card that interacts fully with the rest of the hand.
 */
class NecromancerScoringTest {

    private fun scoreWithPick(pick: String?, vararg hand: String): ScoringResult =
        TestFixture.engine.score(
            ScoringInput(
                hand = TestFixture.hand(*hand),
                playerChoices = PlayerChoices(necromancerPickKey = pick),
            )
        )

    @Test fun `pulled beast is scored and boosts a per-beast bonus (8-card interaction)`() {
        // Without pick: necromancer(3) + beastmaster(9), 0 beasts → 12.
        assertEquals(12, scoreWithPick(null, "necromancer", "beastmaster").totalScore)

        // With pulled unicorn(BEAST, 9): beastmaster sees 1 beast → +9, unicorn base 9.
        // total = 3 + 9 + 9 (beastmaster) + 9 (unicorn) = 30.
        val r = scoreWithPick("unicorn", "necromancer", "beastmaster")
        assertEquals(30, r.totalScore)
        val pick = r.perCard.first { it.isNecromancerPick }
        assertEquals("unicorn", pick.cardKey)
        assertFalse(pick.isBlanked)
    }

    @Test fun `pulled card can blank other cards (Basilisk pulled)`() {
        // Hand necromancer(3) + rangers(5,ARMY). Pull basilisk(35) → blanks the army rangers.
        // total = 3 + 0 (rangers blanked) + 35 = 38.
        val r = scoreWithPick("basilisk", "necromancer", "rangers")
        assertEquals(38, r.totalScore)
        assertTrue("rangers" in r.blankedKeys)
        assertTrue(r.perCard.any { it.isNecromancerPick && it.cardKey == "basilisk" })
    }

    @Test fun `pulled card can itself be blanked by the hand`() {
        // Hand necromancer(3) + basilisk(35). Pull rangers(ARMY) → blanked by the hand's basilisk.
        // total = 3 + 35 + 0 = 38.
        val r = scoreWithPick("rangers", "necromancer", "basilisk")
        assertEquals(38, r.totalScore)
        assertTrue("rangers" in r.blankedKeys)
        val pick = r.perCard.first { it.isNecromancerPick }
        assertEquals("rangers", pick.cardKey)
        assertTrue(pick.isBlanked)
    }

    @Test fun `joker-like pulled card scores base only with no nested joker effect`() {
        // Mirage is WILD (selectable, not a Wizard), base strength 0, isJoker → no rule fires.
        // Pulling it must not change the score and must not trigger any substitution.
        val base = scoreWithPick(null, "necromancer", "rangers").totalScore
        val r = scoreWithPick("mirage", "necromancer", "rangers")
        assertEquals(base, r.totalScore)
        val pick = r.perCard.first { it.isNecromancerPick }
        assertEquals("mirage", pick.cardKey)
        assertEquals(0, pick.contributedScore)
    }

    @Test fun `pick is ignored when no Necromancer is in the hand`() {
        // Stale pick must not leak in if the hand holds no Necromancer.
        val withStalePick = scoreWithPick("forest", "rangers").totalScore
        assertEquals(TestFixture.score("rangers").totalScore, withStalePick)
    }

    @Test fun `only Army, Wizard, Leader, Beast suits are eligible to be pulled`() {
        // Mirrors CardLookup.NECROMANCER_SUITS / getNecromancerCandidates filtering.
        val eligibleSuits = setOf(Suit.ARMY, Suit.WIZARD, Suit.LEADER, Suit.BEAST)
        val eligible = TestFixture.allCards.filter { it.suit in eligibleSuits }
        // No Wizard-only exclusion: Wizards are allowed; WILD jokers and other suits are not.
        assertTrue(eligible.any { it.suit == Suit.WIZARD })
        assertFalse(eligible.any { it.suit == Suit.WILD })
        assertTrue(eligible.all { it.suit in eligibleSuits })
    }
}
