package de.morzo.realmscore.domain.stats

import de.morzo.realmscore.domain.model.CardDefinition
import de.morzo.realmscore.domain.model.ClosedReason
import de.morzo.realmscore.domain.model.Game
import de.morzo.realmscore.domain.model.GameMode
import de.morzo.realmscore.domain.model.HandCard
import de.morzo.realmscore.domain.model.Profile
import de.morzo.realmscore.domain.model.Round
import de.morzo.realmscore.domain.model.RoundResult
import de.morzo.realmscore.domain.model.Suit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StatsCalculatorTest {

    @Test
    fun globalStats_countsGamesRoundsAndUniquePlayers() {
        val s = sampleSnapshot()
        val global = StatsCalculator.computeGlobalStats(s)
        assertEquals(2, global.totalGamesPlayed)
        // Game 1: 2 rounds; Game 2: 1 round → 3 total
        assertEquals(3, global.totalRoundsPlayed)
        // p1, p2 both in both games; p3 only in game 2
        assertEquals(3, global.uniquePlayers)
    }

    @Test
    fun winnerOfGame_returnsUniqueWinner_orNullOnTie() {
        val s = sampleSnapshot()
        // Game 1: p1 = 30+20 = 50, p2 = 10+15 = 25 → p1 wins
        assertEquals("p1", StatsCalculator.winnerOfGame(s, "g1"))
        // Game 2: p1 = 5, p2 = 5, p3 = 4 → tie at top → null
        assertNull(StatsCalculator.winnerOfGame(s, "g2"))
    }

    @Test
    fun playerStats_computesWinRateAndFavorites() {
        val s = sampleSnapshot()
        val stats = StatsCalculator.computePlayerStats(s, "p1")
        assertNotNull(stats)
        stats!!
        assertEquals(2, stats.gamesPlayed)
        // Game1 → p1 wins; Game2 → tie → 0.5 win rate
        assertEquals(1, stats.winCount)
        assertEquals(0.5, stats.winRate, 0.0001)
        assertEquals(30, stats.bestSingleHandScore)
        // p1 has 3 round results: scores 30, 20, 5
        assertEquals(55.0 / 3.0, stats.avgScorePerHand, 0.0001)
        // Favorite card should be the one p1 played most. p1 played:
        // game1/round1: rangers, elven_archers
        // game1/round2: rangers, dragon
        // game2/round1: rangers
        // → rangers appears 3x, others 1x
        assertEquals("rangers", stats.favoriteCards.first().card.key)
        assertEquals(3, stats.favoriteCards.first().count)
    }

    @Test
    fun playerStats_opponentRecord_andRecentGames() {
        val s = sampleSnapshot()
        val p1Stats = StatsCalculator.computePlayerStats(s, "p1")!!
        // p1's opponents: p2 (both games), p3 (only game 2)
        val byOpp = p1Stats.opponents.associateBy { it.profile.id }
        val vsP2 = byOpp.getValue("p2")
        assertEquals(2, vsP2.gamesTogether)
        // p1 won game1, game2 was a tie → 1 win, 0 losses against p2
        assertEquals(1, vsP2.winsForViewer)
        assertEquals(0, vsP2.winsForOpponent)

        // Recent games is sorted most recent first
        assertEquals(2, p1Stats.recentGames.size)
        assertEquals("g2", p1Stats.recentGames[0].gameId)
        assertEquals("g1", p1Stats.recentGames[1].gameId)
        assertEquals(true, p1Stats.recentGames[1].viewerWon)
        assertEquals(false, p1Stats.recentGames[0].viewerWon)
    }

    @Test
    fun cardStats_inHandCountAndPartners() {
        val s = sampleSnapshot()
        // rangers appears in:
        //   game1/round1: p1 (rangers, elven_archers)
        //   game1/round1: p2 (rangers, dragon)
        //   game1/round2: p1 (rangers, dragon)
        //   game2/round1: p1 (rangers)
        // → 4 appearances total
        val rangers = StatsCalculator.computeCardStats(s, "rangers")
        assertNotNull(rangers)
        rangers!!
        assertEquals(4, rangers.inHandCount)
        // Partners: elven_archers (1) + dragon (2)
        val partnerByKey = rangers.frequentPartners.associate { it.card.key to it.count }
        assertEquals(2, partnerByKey["dragon"])
        assertEquals(1, partnerByKey["elven_archers"])
    }

    @Test
    fun cardStatsOverview_sortByPopularity() {
        val s = sampleSnapshot()
        val rows = StatsCalculator.computeCardStatsOverview(s, CardStatsSort.POPULARITY)
        // rangers is most-played (4x). It must come first.
        assertEquals("rangers", rows.first().card.key)
        assertEquals(4, rows.first().inHandCount)
    }

    @Test
    fun headToHead_computesWinsAndAverages() {
        val s = sampleSnapshot()
        val h2h = StatsCalculator.computeHeadToHead(s, "p1", "p2")
        assertNotNull(h2h)
        h2h!!
        // Both share 2 games
        assertEquals(2, h2h.gamesTogether)
        // p1 wins game1; game2 is a tie → winsA=1 winsB=0
        assertEquals(1, h2h.winsA)
        assertEquals(0, h2h.winsB)
        // p1 totals: 50 + 5 = 55 → avg 27.5
        assertEquals(27.5, h2h.avgScoreA, 0.0001)
        // p2 totals: 25 + 5 = 30 → avg 15.0
        assertEquals(15.0, h2h.avgScoreB, 0.0001)
        // Shared history sorted by startedAt desc
        assertEquals("g2", h2h.sharedGameHistory.first().gameId)
    }

    @Test
    fun overview_rankingSortedByWinRate() {
        val s = sampleSnapshot()
        val overview = StatsCalculator.computeOverview(s)
        // p1 has the only outright win → highest win rate
        assertEquals("p1", overview.playerRanking.first().profile.id)
        // total closed games
        assertEquals(2, overview.totalClosedGames)
    }

    @Test
    fun overview_empty_returnsZeroStats() {
        val s = emptySnapshot()
        val overview = StatsCalculator.computeOverview(s)
        assertEquals(0, overview.global.totalGamesPlayed)
        assertEquals(0, overview.global.totalRoundsPlayed)
        assertTrue(overview.playerRanking.isEmpty())
    }

    @Test
    fun histogram_singleValueBucketsToOne() {
        val buckets = StatsCalculator.buildHistogram(listOf(7, 7, 7), bucketCount = 5)
        assertEquals(1, buckets.size)
        assertEquals(3, buckets[0].count)
        assertEquals(7, buckets[0].from)
    }

    // --- Snapshot builders -------------------------------------------------

    private fun emptySnapshot(): StatsSnapshot = StatsSnapshot(
        closedGames = emptyList(),
        participantsByGame = emptyMap(),
        completedRoundsByGame = emptyMap(),
        resultsByRoundResultId = emptyMap(),
        resultsByRoundId = emptyMap(),
        handCardsByResultId = emptyMap(),
        perCardContributionByResultId = emptyMap(),
        profilesById = emptyMap(),
        cardsByKey = emptyMap(),
    )

    private fun sampleSnapshot(): StatsSnapshot {
        val p1 = profile("p1", "Alice")
        val p2 = profile("p2", "Bob")
        val p3 = profile("p3", "Carol")

        val rangers = card("rangers", "Rangers")
        val elven = card("elven_archers", "Elbenschützen")
        val dragon = card("dragon", "Drache")

        val g1 = game("g1", startedAt = 1_000L, closedAt = 2_000L)
        val g2 = game("g2", startedAt = 3_000L, closedAt = 4_000L)

        // Game1 rounds
        val g1r1 = round("g1r1", "g1", 1)
        val g1r2 = round("g1r2", "g1", 2)
        // Game2 rounds
        val g2r1 = round("g2r1", "g2", 1)

        // Round results
        val rr_p1_g1r1 = result("rr1", "g1r1", "p1", 30)
        val rr_p2_g1r1 = result("rr2", "g1r1", "p2", 10)
        val rr_p1_g1r2 = result("rr3", "g1r2", "p1", 20)
        val rr_p2_g1r2 = result("rr4", "g1r2", "p2", 15)
        val rr_p1_g2r1 = result("rr5", "g2r1", "p1", 5)
        val rr_p2_g2r1 = result("rr6", "g2r1", "p2", 5)
        val rr_p3_g2r1 = result("rr7", "g2r1", "p3", 4)

        // Hand cards (only round results that need them)
        val handCards = mapOf(
            rr_p1_g1r1.id to listOf(handCard(rr_p1_g1r1.id, "rangers", 0), handCard(rr_p1_g1r1.id, "elven_archers", 1)),
            rr_p2_g1r1.id to listOf(handCard(rr_p2_g1r1.id, "rangers", 0), handCard(rr_p2_g1r1.id, "dragon", 1)),
            rr_p1_g1r2.id to listOf(handCard(rr_p1_g1r2.id, "rangers", 0), handCard(rr_p1_g1r2.id, "dragon", 1)),
            rr_p2_g1r2.id to listOf(handCard(rr_p2_g1r2.id, "elven_archers", 0)),
            rr_p1_g2r1.id to listOf(handCard(rr_p1_g2r1.id, "rangers", 0)),
            rr_p2_g2r1.id to listOf(handCard(rr_p2_g2r1.id, "dragon", 0)),
            rr_p3_g2r1.id to listOf(handCard(rr_p3_g2r1.id, "elven_archers", 0)),
        )

        val allResults = listOf(
            rr_p1_g1r1, rr_p2_g1r1, rr_p1_g1r2, rr_p2_g1r2,
            rr_p1_g2r1, rr_p2_g2r1, rr_p3_g2r1,
        )

        return StatsSnapshot(
            closedGames = listOf(g1, g2),
            participantsByGame = mapOf(
                "g1" to listOf(p1, p2),
                "g2" to listOf(p1, p2, p3),
            ),
            completedRoundsByGame = mapOf(
                "g1" to listOf(g1r1, g1r2),
                "g2" to listOf(g2r1),
            ),
            resultsByRoundResultId = allResults.associateBy { it.id },
            resultsByRoundId = allResults.groupBy { it.roundId },
            handCardsByResultId = handCards,
            perCardContributionByResultId = emptyMap(),
            profilesById = mapOf("p1" to p1, "p2" to p2, "p3" to p3),
            cardsByKey = mapOf(
                "rangers" to rangers,
                "elven_archers" to elven,
                "dragon" to dragon,
            ),
        )
    }

    private fun profile(id: String, name: String) = Profile(
        id = id,
        name = name,
        colorArgb = 0xFF000000.toInt(),
        isLocalOwner = id == "p1",
        isArchived = false,
        archivedAt = null,
        createdAt = 0L,
        updatedAt = 0L,
        originDeviceId = "d",
    )

    private fun card(key: String, name: String) = CardDefinition(
        key = key,
        nameDe = name,
        suit = Suit.ARMY,
        baseStrength = 5,
        ruleTextDe = "test",
        isJoker = false,
        jokerType = null,
    )

    private fun game(id: String, startedAt: Long, closedAt: Long?) = Game(
        id = id,
        displayName = null,
        mode = GameMode.FIXED_ROUNDS,
        targetRounds = 2,
        targetPoints = null,
        startedAt = startedAt,
        closedAt = closedAt,
        closedReason = closedAt?.let { ClosedReason.COMPLETED },
        createdAt = startedAt,
        updatedAt = closedAt ?: startedAt,
        originDeviceId = "d",
    )

    private fun round(id: String, gameId: String, number: Int) = Round(
        id = id,
        gameId = gameId,
        roundNumber = number,
        startedAt = 0L,
        completedAt = 1L,
        discardScanned = false,
        createdAt = 0L,
        updatedAt = 1L,
        originDeviceId = "d",
    )

    private fun result(id: String, roundId: String, profileId: String, score: Int) = RoundResult(
        id = id,
        roundId = roundId,
        profileId = profileId,
        totalScore = score,
        createdAt = 0L,
        updatedAt = 1L,
        originDeviceId = "d",
    )

    private fun handCard(rrId: String, cardKey: String, position: Int) = HandCard(
        id = "${rrId}_$cardKey",
        roundResultId = rrId,
        cardKey = cardKey,
        position = position,
        jokerTargetCardKey = null,
        jokerTargetSuit = null,
        createdAt = 0L,
        updatedAt = 0L,
    )
}
