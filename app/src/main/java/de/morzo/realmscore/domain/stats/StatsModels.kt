package de.morzo.realmscore.domain.stats

import de.morzo.realmscore.domain.model.CardDefinition
import de.morzo.realmscore.domain.model.Profile

data class GlobalStats(
    val totalGamesPlayed: Int,
    val totalRoundsPlayed: Int,
    val uniquePlayers: Int,
)

data class CardWithCount(
    val card: CardDefinition,
    val count: Int,
)

data class PlayerWithCount(
    val profile: Profile,
    val count: Int,
)

data class OpponentStat(
    val profile: Profile,
    val gamesTogether: Int,
    val winsForViewer: Int,
    val winsForOpponent: Int,
)

data class RecentGameEntry(
    val gameId: String,
    val gameDisplayName: String?,
    val startedAt: Long,
    val closedAt: Long?,
    val viewerScore: Int,
    val winnerName: String?,
    val viewerWon: Boolean,
    val sandboxRoundId: String?,
)

data class HandValueBucket(
    val from: Int,
    val toExclusive: Int,
    val count: Int,
)

data class ScorePoint(
    val gameDisplayName: String?,
    val startedAt: Long,
    val score: Int,
)

data class PlayerStats(
    val profile: Profile,
    val gamesPlayed: Int,
    val winCount: Int,
    val winRate: Double,
    val avgScorePerHand: Double,
    val bestSingleHandScore: Int,
    val favoriteCards: List<CardWithCount>,
    val opponents: List<OpponentStat>,
    val recentGames: List<RecentGameEntry>,
    val scoreTrend: List<ScorePoint>,
    val handValueBuckets: List<HandValueBucket>,
)

data class ContributionContext(
    val gameId: String,
    val gameDisplayName: String?,
    val roundId: String,
    val roundNumber: Int,
    val profileId: String,
    val profileName: String,
    val handCardKeys: List<String>,
    val totalHandScore: Int,
)

data class CardStats(
    val card: CardDefinition,
    val inHandCount: Int,
    val inDiscardCount: Int,
    val handShare: Double,
    val avgContribution: Double,
    val highestSingleContribution: Int,
    val highestContext: ContributionContext?,
    val playersWhoPlayedIt: List<PlayerWithCount>,
    val frequentPartners: List<CardWithCount>,
    val contributionBuckets: List<HandValueBucket>,
    val totalRoundsConsidered: Int,
    val scannedRoundsCount: Int,
)

data class CardStatsRow(
    val card: CardDefinition,
    val inHandCount: Int,
    val inDiscardCount: Int,
    val handShare: Double,
    val avgContribution: Double,
)

enum class CardStatsSort {
    POPULARITY,
    AVG_CONTRIBUTION,
    NAME,
}

data class SharedGameEntry(
    val gameId: String,
    val gameDisplayName: String?,
    val startedAt: Long,
    val scoreA: Int,
    val scoreB: Int,
    val winnerProfileId: String?,
)

data class HeadToHeadStats(
    val playerA: Profile,
    val playerB: Profile,
    val gamesTogether: Int,
    val winsA: Int,
    val winsB: Int,
    val avgScoreA: Double,
    val avgScoreB: Double,
    val cardCountsA: Map<String, Int>,
    val cardCountsB: Map<String, Int>,
    val sharedGameHistory: List<SharedGameEntry>,
)

data class CardHits(
    val mostPlayed: CardWithCount?,
    val rarestPlayed: CardWithCount?,
    val mostValuable: Triple<CardDefinition, Double, Int>?,
)

data class PlayerRankingEntry(
    val profile: Profile,
    val gamesPlayed: Int,
    val wins: Int,
    val winRate: Double,
)

data class StatsOverview(
    val global: GlobalStats,
    val playerRanking: List<PlayerRankingEntry>,
    val cardHits: CardHits,
    val totalClosedGames: Int,
)

data class ClosestRoundInfo(
    val playerA: Profile,
    val playerB: Profile,
    val difference: Int,
    val gameDisplayName: String?,
)

data class PlayerPairInfo(
    val playerA: Profile,
    val playerB: Profile,
    val gamesTogether: Int,
)
