package de.morzo.realmscore.domain.scoring

import de.morzo.realmscore.domain.model.JokerType
import de.morzo.realmscore.domain.model.Suit
import de.morzo.realmscore.domain.scoring.joker.JokerTargets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Phase 30: scoring of the expansion "Der verfluchte Schatz" (new suits + replacement cards). */
class ExpansionScoringTest {

    private fun score(
        vararg keys: String,
        discard: List<String> = emptyList(),
        players: Int? = null,
        assignments: Map<String, JokerAssignment> = emptyMap(),
    ): ScoringResult = TestFixture.engine.score(
        ScoringInput(
            hand = TestFixture.hand(*keys),
            jokerAssignments = assignments,
            discardPile = TestFixture.hand(*discard.toTypedArray()),
            playerCount = players,
        ),
    )

    private fun ScoringResult.cardScore(key: String): Int = perCard.first { it.cardKey == key }.contributedScore

    @Test
    fun newSuitsPoolReplacesEightBaseCards() {
        val pool = TestFixture.newSuitsCards
        assertEquals(68, pool.size)
        assertEquals(23, TestFixture.expansionCards.size)
        assertFalse(pool.any { it.key == "rangers" })
        assertTrue(pool.any { it.key == "expansion_rangers" })
        assertEquals(Suit.BUILDING, TestFixture.card("expansion_bell_tower").suit)
    }

    @Test
    fun deathKnightScoresWeaponsAndArmiesInTheDiscardArea() {
        val r = score("expansion_death_knight", discard = listOf("knights", "magic_wand", "unicorn"))
        assertEquals(14 + 2 * 7, r.totalScore)
    }

    @Test
    fun jokerInTheDiscardAreaDoesNotCountAsAnotherSuit() {
        val r = score("expansion_ghoul", discard = listOf("doppelganger", "king"))
        assertEquals(8 + 4, r.totalScore)
    }

    @Test
    fun darkQueenCountsUnicornByName() {
        val r = score("expansion_dark_queen", discard = listOf("unicorn", "forest", "warhorse"))
        assertEquals(10 + 2 * 5, r.totalScore)
    }

    @Test
    fun lichCountsNecromancerAndOtherUndead() {
        val r = score("expansion_lich", "expansion_ghoul", "expansion_necromancer")
        assertEquals(13 + 20 + 8 + 3, r.totalScore)
    }

    @Test
    fun lichMakesUndeadUnblankable() {
        val withLich = score("wildfire", "expansion_lich", "expansion_ghoul")
        assertTrue(withLich.blankedKeys.isEmpty())
        assertEquals(40 + 23 + 8, withLich.totalScore)

        val withoutLich = score("wildfire", "expansion_ghoul")
        assertTrue("expansion_ghoul" in withoutLich.blankedKeys)
        assertEquals(40, withoutLich.totalScore)
    }

    @Test
    fun demonBlanksCardsAloneInTheirSuit() {
        val r = score("expansion_demon", "knights", "light_cavalry", "king")
        assertTrue("king" in r.blankedKeys)
        assertEquals(listOf("expansion_demon"), r.blankedBy["king"])
        // Knights lose their Leader → −8.
        assertEquals(45 + (20 - 8) + 17, r.totalScore)
    }

    @Test
    fun angelProtectsItsTargetFromTheDemon() {
        val assignments = mapOf("expansion_angel" to JokerAssignment("expansion_angel", "king"))
        val r = score("expansion_demon", "expansion_angel", "knights", "light_cavalry", "king", assignments = assignments)
        assertTrue(r.blankedKeys.isEmpty())
        assertEquals(45 + 16 + 20 + 17 + (8 + 10), r.totalScore)
    }

    @Test
    fun solverPicksTheAngelTarget() {
        val seed = ScoringInput(
            hand = TestFixture.hand("expansion_demon", "expansion_angel", "knights", "light_cavalry", "king"),
            newSuits = true,
        )
        val best = TestFixture.solver.findOptimal(seed)
        assertEquals("king", best.bestInput.jokerAssignments["expansion_angel"]?.targetCardKey)
        assertEquals(116, best.bestResult.totalScore)
    }

    @Test
    fun clearedDemonPenaltyBlanksNothing() {
        val r = score("expansion_demon", "protection_rune", "king")
        assertTrue(r.blankedKeys.isEmpty())
        assertEquals(45 + 1 + 8, r.totalScore)
    }

    @Test
    fun judgeCountsUnclearedPenaltiesEvenWhenPartlyCleared() {
        // Knights (−8, no Leader) and Dwarvish Infantry (Army word stripped by Rangers) both count.
        val r = score("expansion_judge", "knights", "dwarvish_infantry", "rangers")
        assertEquals(11 + 20, r.cardScore("expansion_judge"))
        assertEquals(31 + 12 + 15 + 5, r.totalScore)
    }

    @Test
    fun genieScoresTenPerOpponent() {
        assertEquals(-50 + 30, score("expansion_genie", players = 4).totalScore)
        assertEquals(-50 + 10, score("expansion_genie", players = 2).totalScore)
        assertEquals(-50, score("expansion_genie").totalScore)
    }

    @Test
    fun chapelNeedsExactlyTwoCardsOfTheListedSuits() {
        assertEquals(2 + 40 + 8 + 8, score("expansion_chapel", "king", "expansion_ghoul").totalScore)
        val three = score("expansion_chapel", "king", "queen", "expansion_ghoul")
        assertEquals(2, three.cardScore("expansion_chapel"))
    }

    @Test
    fun cryptSumsUndeadInHandAndBlanksLeaders() {
        val r = score("expansion_crypt", "expansion_lich", "expansion_ghoul", "king")
        assertTrue("king" in r.blankedKeys)
        assertEquals(21 + 13 + 8, r.cardScore("expansion_crypt"))
        assertEquals(42 + 23 + 8, r.totalScore)
    }

    @Test
    fun castleCountsFirstOfEachSuitAndFurtherBuildings() {
        val r = score(
            "expansion_castle", "king", "knights", "light_cavalry", "forest",
            "expansion_chapel", "expansion_bell_tower",
        )
        assertEquals(10 + 4 * 10 + 5, r.cardScore("expansion_castle"))
    }

    @Test
    fun dungeonCountsFirstsFurtherCardsAndNamedCards() {
        val r = score(
            "expansion_dungeon", "expansion_lich", "expansion_ghoul", "unicorn", "warlock",
            "expansion_necromancer",
        )
        assertEquals(7 + 10 + 5 + 10 + 5 + 5, r.cardScore("expansion_dungeon"))
    }

    @Test
    fun gardenIsBlankedByUndead() {
        val r = score("expansion_garden", "expansion_ghoul")
        assertTrue("expansion_garden" in r.blankedKeys)
        assertEquals(8, r.totalScore)
        assertEquals(11 + 11 + 8, score("expansion_garden", "king").totalScore)
    }

    @Test
    fun expansionGreatFloodBlanksBuildings() {
        val r = score("expansion_great_flood", "expansion_chapel", "lightning")
        assertTrue("expansion_chapel" in r.blankedKeys)
        assertFalse("lightning" in r.blankedKeys)
        assertEquals(32 + 11, r.totalScore)
    }

    @Test
    fun expansionWorldTreeGivesSeventy() {
        val r = score("expansion_world_tree", "king", "expansion_ghoul")
        assertEquals(2 + 70 + 8 + 8, r.totalScore)
    }

    @Test
    fun expansionBellTowerCountsUndead() {
        assertEquals(8 + 15 + 8, score("expansion_bell_tower", "expansion_ghoul").totalScore)
    }

    @Test
    fun expansionRangersCountBuildings() {
        assertEquals(5 + 20 + 2 + 7, score("expansion_rangers", "expansion_chapel", "forest").totalScore)
    }

    @Test
    fun expansionNecromancerPullsUndeadAndIsFoundByType() {
        val assignments = mapOf(
            "expansion_necromancer" to JokerAssignment("expansion_necromancer", "expansion_lich"),
        )
        val r = score("expansion_necromancer", "king", assignments = assignments)
        // Necromancer 3 + King 8 + pulled Lich 13 (+10 for the Necromancer).
        assertEquals(3 + 8 + 23, r.totalScore)
        assertTrue(r.perCard.first { it.cardKey == "expansion_lich" }.isNecromancerPick)
    }

    @Test
    fun expansionFountainAddsBuildingStrength() {
        val assignments = mapOf(
            "expansion_fountain_of_life" to JokerAssignment("expansion_fountain_of_life", "expansion_crypt"),
        )
        val r = score("expansion_fountain_of_life", "expansion_crypt", assignments = assignments)
        assertEquals(1 + 21 + 21, r.totalScore)
    }

    @Test
    fun expansionJokersReachTheNewSuits() {
        val mirage = TestFixture.card("expansion_mirage")
        val shapeshifter = TestFixture.card("expansion_shapeshifter")
        assertTrue(Suit.BUILDING in JokerTargets.substitutionSuits(mirage))
        assertTrue(Suit.UNDEAD in JokerTargets.substitutionSuits(shapeshifter))
        assertFalse(Suit.BUILDING in JokerTargets.substitutionSuits(TestFixture.card("mirage")))
        assertEquals(JokerType.ANGEL, TestFixture.card("expansion_angel").jokerType)
        assertTrue(Suit.UNDEAD in JokerTargets.necromancerSuits("expansion_necromancer"))
        assertFalse(Suit.UNDEAD in JokerTargets.necromancerSuits("necromancer"))
    }

    @Test
    fun solverOffersNewSuitTargetsOnlyInNewSuitGames() {
        // Leader/Army/Land are covered, so the Mirage is worth most as a Building (Castle +10 for
        // the first other building, vs. +5 from the King as an Army).
        val seed = ScoringInput(
            hand = TestFixture.hand("expansion_mirage", "expansion_castle", "king", "knights", "expansion_garden"),
            newSuits = true,
        )
        val best = TestFixture.solver.findOptimal(seed)
        val target = best.bestInput.jokerAssignments["expansion_mirage"]?.targetCardKey
        assertEquals(Suit.BUILDING, target?.let(TestFixture::card)?.suit)
    }
}
