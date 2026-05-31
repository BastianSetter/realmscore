package de.morzo.realmscore.domain.stats

import de.morzo.realmscore.domain.model.Game

/**
 * Pure-Kotlin stats computations. Takes a [StatsSnapshot] and produces the
 * various [PlayerStats] / [CardStats] / etc. domain objects. No Android imports —
 * fully unit-testable.
 */
object StatsCalculator {

    fun computeGlobalStats(snapshot: StatsSnapshot): GlobalStats {
        val gameCount = snapshot.closedGames.size
        val roundCount = snapshot.allCompletedRounds.size
        val uniqueProfileIds = snapshot.closedGames
            .flatMap { snapshot.participantsByGame[it.id].orEmpty() }
            .map { it.id }
            .toSet()
        return GlobalStats(
            totalGamesPlayed = gameCount,
            totalRoundsPlayed = roundCount,
            uniquePlayers = uniqueProfileIds.size,
        )
    }

    /**
     * Returns the profileId of the unique winner for [gameId] (highest summed score),
     * or null if there is no winner (no rounds played, or a tie at the top).
     */
    fun winnerOfGame(snapshot: StatsSnapshot, gameId: String): String? {
        val totals = totalsForGame(snapshot, gameId)
        val maxScore = totals.values.maxOrNull() ?: return null
        val winners = totals.filterValues { it == maxScore }.keys
        return if (winners.size == 1) winners.first() else null
    }

    fun totalsForGame(snapshot: StatsSnapshot, gameId: String): Map<String, Int> {
        val rounds = snapshot.completedRoundsByGame[gameId].orEmpty()
        val totals = mutableMapOf<String, Int>()
        for (round in rounds) {
            val results = snapshot.resultsByRoundId[round.id].orEmpty()
            for (result in results) {
                totals[result.profileId] = (totals[result.profileId] ?: 0) + result.totalScore
            }
        }
        return totals
    }

    fun computePlayerStats(snapshot: StatsSnapshot, profileId: String): PlayerStats? {
        val profile = snapshot.profilesById[profileId] ?: return null

        val gamesPlayed = snapshot.closedGames.filter { game ->
            snapshot.participantsByGame[game.id]?.any { it.id == profileId } == true
        }
        val gamesPlayedCount = gamesPlayed.size
        val winCount = gamesPlayed.count { winnerOfGame(snapshot, it.id) == profileId }
        val winRate = if (gamesPlayedCount > 0) winCount.toDouble() / gamesPlayedCount else 0.0

        val allResults = gamesPlayed.flatMap { game ->
            snapshot.completedRoundsByGame[game.id].orEmpty().flatMap { round ->
                snapshot.resultsByRoundId[round.id].orEmpty()
                    .filter { it.profileId == profileId }
            }
        }
        val handScores = allResults.map { it.totalScore }
        val avgScorePerHand = if (handScores.isNotEmpty()) handScores.average() else 0.0
        val bestSingleHandScore = handScores.maxOrNull() ?: 0

        val cardCounts = mutableMapOf<String, Int>()
        for (result in allResults) {
            val hand = snapshot.handCardsByResultId[result.id].orEmpty()
            for (card in hand) {
                cardCounts.merge(card.cardKey, 1, Int::plus)
            }
        }
        val favoriteCards = cardCounts.entries
            .sortedByDescending { it.value }
            .mapNotNull { entry ->
                snapshot.cardsByKey[entry.key]?.let { CardWithCount(it, entry.value) }
            }
            .take(5)

        val opponents = computeOpponentStats(snapshot, profileId, gamesPlayed)
        val recentGames = computeRecentGames(snapshot, profileId, gamesPlayed)
        val scoreTrend = gamesPlayed
            .sortedBy { it.startedAt }
            .map { game ->
                ScorePoint(
                    gameDisplayName = game.displayName,
                    startedAt = game.startedAt,
                    score = totalsForGame(snapshot, game.id)[profileId] ?: 0,
                )
            }
        val handValueBuckets = buildHistogram(handScores, bucketCount = 8)

        return PlayerStats(
            profile = profile,
            gamesPlayed = gamesPlayedCount,
            winCount = winCount,
            winRate = winRate,
            avgScorePerHand = avgScorePerHand,
            bestSingleHandScore = bestSingleHandScore,
            favoriteCards = favoriteCards,
            opponents = opponents,
            recentGames = recentGames,
            scoreTrend = scoreTrend,
            handValueBuckets = handValueBuckets,
        )
    }

    private fun computeOpponentStats(
        snapshot: StatsSnapshot,
        profileId: String,
        gamesPlayed: List<Game>,
    ): List<OpponentStat> {
        data class Acc(var gamesTogether: Int = 0, var wins: Int = 0, var losses: Int = 0)

        val byOpponent = mutableMapOf<String, Acc>()
        for (game in gamesPlayed) {
            val winner = winnerOfGame(snapshot, game.id)
            val others = snapshot.participantsByGame[game.id].orEmpty()
                .filter { it.id != profileId }
            for (opp in others) {
                val acc = byOpponent.getOrPut(opp.id) { Acc() }
                acc.gamesTogether++
                when (winner) {
                    profileId -> acc.wins++
                    opp.id -> acc.losses++
                }
            }
        }
        return byOpponent.entries
            .sortedByDescending { it.value.gamesTogether }
            .mapNotNull { (oppId, acc) ->
                snapshot.profilesById[oppId]?.let { p ->
                    OpponentStat(
                        profile = p,
                        gamesTogether = acc.gamesTogether,
                        winsForViewer = acc.wins,
                        winsForOpponent = acc.losses,
                    )
                }
            }
    }

    private fun computeRecentGames(
        snapshot: StatsSnapshot,
        profileId: String,
        gamesPlayed: List<Game>,
    ): List<RecentGameEntry> = gamesPlayed
        .sortedByDescending { it.closedAt ?: it.startedAt }
        .take(5)
        .map { game ->
            val totals = totalsForGame(snapshot, game.id)
            val viewerScore = totals[profileId] ?: 0
            val winnerId = winnerOfGame(snapshot, game.id)
            val winnerName = winnerId?.let { snapshot.profilesById[it]?.name }
            RecentGameEntry(
                gameId = game.id,
                gameDisplayName = game.displayName,
                startedAt = game.startedAt,
                closedAt = game.closedAt,
                viewerScore = viewerScore,
                winnerName = winnerName,
                viewerWon = winnerId == profileId,
                sandboxRoundId = bestRoundForPlayerInGame(snapshot, game.id, profileId),
            )
        }

    /**
     * Picks the round in [gameId] with the highest score for [profileId]. Ties are
     * broken by latest round number. Used to seed Move-to-Sandbox from recent games.
     */
    private fun bestRoundForPlayerInGame(
        snapshot: StatsSnapshot,
        gameId: String,
        profileId: String,
    ): String? {
        val rounds = snapshot.completedRoundsByGame[gameId].orEmpty()
        var bestRoundId: String? = null
        var bestScore = Int.MIN_VALUE
        var bestRoundNumber = Int.MIN_VALUE
        for (round in rounds) {
            val result = snapshot.resultsByRoundId[round.id]
                ?.firstOrNull { it.profileId == profileId }
                ?: continue
            if (
                result.totalScore > bestScore ||
                (result.totalScore == bestScore && round.roundNumber > bestRoundNumber)
            ) {
                bestScore = result.totalScore
                bestRoundNumber = round.roundNumber
                bestRoundId = round.id
            }
        }
        return bestRoundId
    }

    fun computeCardStats(snapshot: StatsSnapshot, cardKey: String): CardStats? {
        val card = snapshot.cardsByKey[cardKey] ?: return null

        var totalAppearances = 0
        val playerCounts = mutableMapOf<String, Int>()
        val partnerCounts = mutableMapOf<String, Int>()

        for ((rrId, cards) in snapshot.handCardsByResultId) {
            val occurrences = cards.count { it.cardKey == cardKey }
            if (occurrences <= 0) continue
            totalAppearances += occurrences
            val result = snapshot.resultsByRoundResultId[rrId]
            if (result != null) {
                playerCounts.merge(result.profileId, occurrences, Int::plus)
            }
            for (other in cards) {
                if (other.cardKey == cardKey) continue
                partnerCounts.merge(other.cardKey, 1, Int::plus)
            }
        }

        val playersWhoPlayedIt = playerCounts.entries
            .sortedByDescending { it.value }
            .mapNotNull { (id, count) ->
                snapshot.profilesById[id]?.let { PlayerWithCount(it, count) }
            }
        val frequentPartners = partnerCounts.entries
            .sortedByDescending { it.value }
            .mapNotNull { (key, count) ->
                snapshot.cardsByKey[key]?.let { CardWithCount(it, count) }
            }
            .take(5)

        val contributions = mutableListOf<Pair<String, Int>>()
        for ((rrId, byKey) in snapshot.perCardContributionByResultId) {
            val value = byKey[cardKey] ?: continue
            contributions += rrId to value
        }
        val avgContribution = if (contributions.isNotEmpty()) {
            contributions.map { it.second }.average()
        } else 0.0
        val highest = contributions.maxByOrNull { it.second }
        val highestContribution = highest?.second ?: 0
        val highestContext = highest?.let { (rrId, _) ->
            buildContributionContext(snapshot, rrId)
        }

        val totalRoundsConsidered = snapshot.allCompletedRounds.size
        val scannedRoundsCount = snapshot.allCompletedRounds.count { it.discardScanned }
        val totalHandsRecorded = snapshot.handCardsByResultId.size
        val handShare = if (totalHandsRecorded > 0) {
            totalAppearances.toDouble() / totalHandsRecorded
        } else 0.0

        val contributionBuckets = buildHistogram(
            values = contributions.map { it.second },
            bucketCount = 6,
        )

        return CardStats(
            card = card,
            inHandCount = totalAppearances,
            inDiscardCount = 0,
            handShare = handShare,
            avgContribution = avgContribution,
            highestSingleContribution = highestContribution,
            highestContext = highestContext,
            playersWhoPlayedIt = playersWhoPlayedIt,
            frequentPartners = frequentPartners,
            contributionBuckets = contributionBuckets,
            totalRoundsConsidered = totalRoundsConsidered,
            scannedRoundsCount = scannedRoundsCount,
        )
    }

    private fun buildContributionContext(
        snapshot: StatsSnapshot,
        roundResultId: String,
    ): ContributionContext? {
        val result = snapshot.resultsByRoundResultId[roundResultId] ?: return null
        val round = snapshot.roundsByRoundId[result.roundId] ?: return null
        val game = snapshot.gamesById[round.gameId] ?: return null
        val profileName = snapshot.profilesById[result.profileId]?.name.orEmpty()
        val handCards = snapshot.handCardsByResultId[roundResultId].orEmpty().map { it.cardKey }
        return ContributionContext(
            gameId = game.id,
            gameDisplayName = game.displayName,
            roundId = round.id,
            roundNumber = round.roundNumber,
            profileId = result.profileId,
            profileName = profileName,
            handCardKeys = handCards,
            totalHandScore = result.totalScore,
        )
    }

    fun computeCardStatsOverview(
        snapshot: StatsSnapshot,
        sortBy: CardStatsSort,
    ): List<CardStatsRow> {
        val rows = snapshot.cardsByKey.values.map { card ->
            val stats = computeCardStats(snapshot, card.key) ?: return@map CardStatsRow(
                card = card,
                inHandCount = 0,
                inDiscardCount = 0,
                handShare = 0.0,
                avgContribution = 0.0,
            )
            CardStatsRow(
                card = stats.card,
                inHandCount = stats.inHandCount,
                inDiscardCount = stats.inDiscardCount,
                handShare = stats.handShare,
                avgContribution = stats.avgContribution,
            )
        }
        return when (sortBy) {
            CardStatsSort.POPULARITY -> rows.sortedByDescending { it.inHandCount }
            CardStatsSort.AVG_CONTRIBUTION -> rows.sortedByDescending { it.avgContribution }
            CardStatsSort.NAME -> rows.sortedBy { it.card.nameDe.lowercase() }
        }
    }

    fun computeHeadToHead(
        snapshot: StatsSnapshot,
        profileIdA: String,
        profileIdB: String,
    ): HeadToHeadStats? {
        if (profileIdA == profileIdB) return null
        val pA = snapshot.profilesById[profileIdA] ?: return null
        val pB = snapshot.profilesById[profileIdB] ?: return null

        val sharedGames = snapshot.closedGames.filter { game ->
            val parts = snapshot.participantsByGame[game.id].orEmpty().map { it.id }
            profileIdA in parts && profileIdB in parts
        }

        var winsA = 0
        var winsB = 0
        var sumA = 0
        var sumB = 0
        val cardCountsA = mutableMapOf<String, Int>()
        val cardCountsB = mutableMapOf<String, Int>()
        val sharedEntries = mutableListOf<SharedGameEntry>()

        for (game in sharedGames) {
            val totals = totalsForGame(snapshot, game.id)
            val scoreA = totals[profileIdA] ?: 0
            val scoreB = totals[profileIdB] ?: 0
            val winner = winnerOfGame(snapshot, game.id)
            when (winner) {
                profileIdA -> winsA++
                profileIdB -> winsB++
            }
            sumA += scoreA
            sumB += scoreB

            for (round in snapshot.completedRoundsByGame[game.id].orEmpty()) {
                val results = snapshot.resultsByRoundId[round.id].orEmpty()
                for (result in results) {
                    val target = when (result.profileId) {
                        profileIdA -> cardCountsA
                        profileIdB -> cardCountsB
                        else -> null
                    } ?: continue
                    for (card in snapshot.handCardsByResultId[result.id].orEmpty()) {
                        target.merge(card.cardKey, 1, Int::plus)
                    }
                }
            }

            sharedEntries += SharedGameEntry(
                gameId = game.id,
                gameDisplayName = game.displayName,
                startedAt = game.startedAt,
                scoreA = scoreA,
                scoreB = scoreB,
                winnerProfileId = winner,
            )
        }

        val games = sharedGames.size
        val avgA = if (games > 0) sumA.toDouble() / games else 0.0
        val avgB = if (games > 0) sumB.toDouble() / games else 0.0

        return HeadToHeadStats(
            playerA = pA,
            playerB = pB,
            gamesTogether = games,
            winsA = winsA,
            winsB = winsB,
            avgScoreA = avgA,
            avgScoreB = avgB,
            cardCountsA = cardCountsA,
            cardCountsB = cardCountsB,
            sharedGameHistory = sharedEntries.sortedByDescending { it.startedAt },
        )
    }

    fun computeOverview(snapshot: StatsSnapshot): StatsOverview {
        val global = computeGlobalStats(snapshot)

        // Player ranking
        val ranking = snapshot.profilesById.values
            .map { profile ->
                val gamesPlayed = snapshot.closedGames.count { game ->
                    snapshot.participantsByGame[game.id]?.any { it.id == profile.id } == true
                }
                val wins = snapshot.closedGames.count { game ->
                    winnerOfGame(snapshot, game.id) == profile.id
                }
                val winRate = if (gamesPlayed > 0) wins.toDouble() / gamesPlayed else 0.0
                PlayerRankingEntry(
                    profile = profile,
                    gamesPlayed = gamesPlayed,
                    wins = wins,
                    winRate = winRate,
                )
            }
            .filter { it.gamesPlayed > 0 }
            .sortedWith(
                compareByDescending<PlayerRankingEntry> { it.winRate }
                    .thenByDescending { it.gamesPlayed }
                    .thenBy { it.profile.name.lowercase() },
            )

        // Card hits: most played, rarest played (lowest non-zero count), most valuable
        val overviewRows = computeCardStatsOverview(snapshot, CardStatsSort.POPULARITY)
        val playedRows = overviewRows.filter { it.inHandCount > 0 }
        val mostPlayed = playedRows.maxByOrNull { it.inHandCount }?.let {
            CardWithCount(it.card, it.inHandCount)
        }
        val rarestPlayed = playedRows.minByOrNull { it.inHandCount }?.let {
            CardWithCount(it.card, it.inHandCount)
        }?.takeIf { mostPlayed?.card?.key != it.card.key }
        val mostValuable = overviewRows
            .filter { it.inHandCount > 0 }
            .maxByOrNull { it.avgContribution }
            ?.let { Triple(it.card, it.avgContribution, it.inHandCount) }

        return StatsOverview(
            global = global,
            playerRanking = ranking,
            cardHits = CardHits(
                mostPlayed = mostPlayed,
                rarestPlayed = rarestPlayed,
                mostValuable = mostValuable,
            ),
            totalClosedGames = snapshot.closedGames.size,
        )
    }

    fun computeClosestRound(snapshot: StatsSnapshot): ClosestRoundInfo? {
        var best: ClosestRoundInfo? = null
        for (round in snapshot.allCompletedRounds) {
            val results = snapshot.resultsByRoundId[round.id].orEmpty()
            if (results.size < 2) continue
            val sorted = results.sortedBy { it.totalScore }
            for (i in 0 until sorted.size - 1) {
                val a = sorted[i]
                val b = sorted[i + 1]
                val diff = b.totalScore - a.totalScore
                if (best != null && diff >= best.difference) continue
                val pA = snapshot.profilesById[a.profileId] ?: continue
                val pB = snapshot.profilesById[b.profileId] ?: continue
                val game = snapshot.gamesById[round.gameId]
                best = ClosestRoundInfo(pA, pB, diff, game?.displayName)
            }
        }
        return best
    }

    fun computeMostPlayedPair(snapshot: StatsSnapshot): PlayerPairInfo? {
        val counts = mutableMapOf<Pair<String, String>, Int>()
        for (game in snapshot.closedGames) {
            val ids = snapshot.participantsByGame[game.id].orEmpty().map { it.id }.sorted()
            for (i in 0 until ids.size - 1) {
                for (j in i + 1 until ids.size) {
                    val key = ids[i] to ids[j]
                    counts[key] = (counts[key] ?: 0) + 1
                }
            }
        }
        val top = counts.maxByOrNull { it.value } ?: return null
        val pA = snapshot.profilesById[top.key.first] ?: return null
        val pB = snapshot.profilesById[top.key.second] ?: return null
        return PlayerPairInfo(pA, pB, top.value)
    }

    /**
     * Buckets [values] into [bucketCount] equal-width ranges between min..max.
     * Returns an empty list if input is empty.
     */
    fun buildHistogram(values: List<Int>, bucketCount: Int): List<HandValueBucket> {
        if (values.isEmpty() || bucketCount <= 0) return emptyList()
        val min = values.min()
        val max = values.max()
        if (min == max) {
            // Single bucket
            return listOf(HandValueBucket(from = min, toExclusive = min + 1, count = values.size))
        }
        val span = max - min + 1
        val bucketSize = maxOf(1, (span + bucketCount - 1) / bucketCount)
        val actualBucketCount = ((span + bucketSize - 1) / bucketSize)
        val counts = IntArray(actualBucketCount)
        for (v in values) {
            val idx = ((v - min) / bucketSize).coerceIn(0, actualBucketCount - 1)
            counts[idx]++
        }
        return (0 until actualBucketCount).map { i ->
            HandValueBucket(
                from = min + i * bucketSize,
                toExclusive = min + (i + 1) * bucketSize,
                count = counts[i],
            )
        }
    }
}
