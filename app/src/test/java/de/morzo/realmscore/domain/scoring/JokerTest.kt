package de.morzo.realmscore.domain.scoring

import de.morzo.realmscore.domain.model.Suit
import org.junit.Assert.assertEquals
import org.junit.Test

class JokerTest {

    @Test fun `unassigned joker contributes 0`() {
        // mirage alone: no bonus/penalty, no base strength
        val r = TestFixture.score("mirage")
        assertEquals(0, r.totalScore)
    }

    @Test fun `mirage copies suit but not bonus`() {
        // mirage as rangers (army, str 5) — copies only name+suit, no bonus, no base strength
        // hand: mirage(→rangers ARMY), rangers, knights(20, leader present? no → −8 = 12)
        // mirage doesn't fire rangers' "+10 per land". But its ARMY identity affects other counters.
        // king would be relevant. Use simpler: mirage→rangers, dwarvish, knights(no leader → 12)
        // dwarvish: 2 other armies (rangers? no rangers — only mirage-as-rangers and knights) → -4
        // mirage str 0, no bonus. knights: no leader → -8 (-> 12)
        // total: 0(mirage) + 15-4(dwarvish) + 12(knights) = 23
        val input = ScoringInput(
            hand = TestFixture.hand("mirage", "dwarvish_infantry", "knights"),
            jokerAssignments = mapOf(
                "mirage" to JokerAssignment("mirage", "rangers"),
            ),
        )
        val r = TestFixture.engine.score(input)
        assertEquals(23, r.totalScore)
    }

    @Test fun `doppelganger copies penalty not bonus`() {
        // Doppelganger must target a card IN HAND. Use light_cavalry as target.
        // hand: doppelganger→light_cavalry(17), light_cavalry(17), forest(7,land), cavern(6,land)
        // doppelganger-as-light_cavalry: bonusEnabled=false, penaltyEnabled=true, strength=17
        //   penalty: -2 per land. 2 lands → -4
        // real light_cavalry: -2 per land. 2 lands → -4
        // forest: +12 per beast/archer = 0
        // cavern: +25 with dwarves/dragon? no → 0
        // total = 17-4 + 17-4 + 7 + 6 = 39
        val input = ScoringInput(
            hand = TestFixture.hand("doppelganger", "light_cavalry", "forest", "cavern"),
            jokerAssignments = mapOf(
                "doppelganger" to JokerAssignment("doppelganger", "light_cavalry"),
            ),
        )
        val r = TestFixture.engine.score(input)
        assertEquals(39, r.totalScore)
    }

    @Test fun `doppelganger does not copy bonus`() {
        // Sanity: if doppelganger COULD copy bonus, this would explode. Verify it doesn't.
        // hand: doppelganger→forest, forest, dragon (no wizard → -40), hydra (no swamp → 0)
        // forest: +12 per beast/archer; non-blanked beasts = dragon, hydra → +24
        // doppelganger-as-forest: NO bonus, NO penalty (forest has neither). base 7.
        // dragon: -40. hydra: 0. forest: 7+24.
        // total = 7 + (7+24) + (30-40) + 12 = 40
        val input = ScoringInput(
            hand = TestFixture.hand("doppelganger", "forest", "dragon", "hydra"),
            jokerAssignments = mapOf(
                "doppelganger" to JokerAssignment("doppelganger", "forest"),
            ),
        )
        val r = TestFixture.engine.score(input)
        assertEquals(40, r.totalScore)
    }

    @Test fun `book_of_changes changes target suit`() {
        // book + rangers (army) → change rangers to LAND
        // Now rangers is LAND. king: 0 armies → 0 (rangers no longer army), base 8
        // rangers bonus: PerOtherCountRule(LAND). rangers' rule still fires but its SUIT is now LAND.
        // Wait, ranger sees other LAND cards. Its filter is BySuit(LAND). 0 other lands → 0.
        // But rangers itself is a LAND now → does it count itself? excludeSelf=true → no.
        // total = 3(book) + 5(rangers) + 8(king) + 0 = 16
        // Without book: king would give +5 (rangers is army)
        val input = ScoringInput(
            hand = TestFixture.hand("book_of_changes", "rangers", "king"),
            jokerAssignments = mapOf(
                "book_of_changes" to JokerAssignment("book_of_changes", "rangers", targetSuit = Suit.LAND),
            ),
        )
        val r = TestFixture.engine.score(input)
        assertEquals(16, r.totalScore)
    }

    @Test fun `shapeshifter copies suit only`() {
        // shapeshifter→dragon (beast)
        // hand: shapeshifter, hydra, forest, swamp
        // shapeshifter as dragon: ARMY? no, BEAST. Base 0, no bonus/penalty.
        // forest +12 per beast/archer: shapeshifter(beast) + hydra(beast) = 2 → +24
        // hydra: swamp present → +28
        // swamp: 0 army + 0 flame → 0 penalty
        // total = 0+12+7+18+24+28 = 89
        val input = ScoringInput(
            hand = TestFixture.hand("shapeshifter", "hydra", "forest", "swamp"),
            jokerAssignments = mapOf(
                "shapeshifter" to JokerAssignment("shapeshifter", "dragon"),
            ),
        )
        val r = TestFixture.engine.score(input)
        assertEquals(89, r.totalScore)
    }
}
