package de.morzo.realmscore.data.repository

import de.morzo.realmscore.data.cards.CardLookup
import de.morzo.realmscore.data.db.dao.GameDao
import de.morzo.realmscore.data.db.dao.HandCardDao
import de.morzo.realmscore.data.db.dao.ProfileDao
import de.morzo.realmscore.data.db.dao.RoundDao
import de.morzo.realmscore.data.db.dao.RoundResultDao
import de.morzo.realmscore.domain.model.HandCard
import de.morzo.realmscore.domain.model.Profile
import de.morzo.realmscore.domain.model.Round
import de.morzo.realmscore.domain.model.RoundResult
import de.morzo.realmscore.domain.model.Suit
import de.morzo.realmscore.domain.repository.StatsRepository
import de.morzo.realmscore.domain.scoring.JokerAssignment
import de.morzo.realmscore.domain.scoring.ScoringEngine
import de.morzo.realmscore.domain.scoring.ScoringInput
import de.morzo.realmscore.domain.stats.CardStats
import de.morzo.realmscore.domain.stats.CardStatsRow
import de.morzo.realmscore.domain.stats.CardStatsSort
import de.morzo.realmscore.domain.stats.ClosestRoundInfo
import de.morzo.realmscore.domain.stats.GlobalStats
import de.morzo.realmscore.domain.stats.HeadToHeadStats
import de.morzo.realmscore.domain.stats.PlayerPairInfo
import de.morzo.realmscore.domain.stats.PlayerStats
import de.morzo.realmscore.domain.stats.StatsCalculator
import de.morzo.realmscore.domain.stats.StatsOverview
import de.morzo.realmscore.domain.stats.StatsSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class StatsRepositoryImpl(
    private val gameDao: GameDao,
    private val roundDao: RoundDao,
    private val roundResultDao: RoundResultDao,
    private val handCardDao: HandCardDao,
    private val profileDao: ProfileDao,
    private val cardLookup: CardLookup,
    private val scoringEngine: ScoringEngine,
) : StatsRepository {

    private suspend fun buildSnapshot(): StatsSnapshot = withContext(Dispatchers.Default) {
        val games = gameDao.getClosedGames().map { it.toDomain() }
        val gameIds = games.map { it.id }

        val participantsRaw = if (gameIds.isEmpty()) emptyList()
        else gameDao.getParticipantsForGames(gameIds)

        val profilesById = mutableMapOf<String, Profile>()
        for (id in participantsRaw.map { it.profileId }.toSet()) {
            profileDao.getById(id)?.toDomain()?.let { profilesById[id] = it }
        }

        val participantsByGame = participantsRaw
            .groupBy { it.gameId }
            .mapValues { (_, list) ->
                list.sortedBy { it.seatOrder }
                    .mapNotNull { profilesById[it.profileId] }
            }

        val rounds = if (gameIds.isEmpty()) emptyList()
        else roundDao.getCompletedRoundsForGames(gameIds).map { it.toDomain() }
        val completedRoundsByGame: Map<String, List<Round>> = rounds
            .groupBy { it.gameId }
            .mapValues { (_, list) -> list.sortedBy { it.roundNumber } }

        val roundIds = rounds.map { it.id }
        val results: List<RoundResult> = if (roundIds.isEmpty()) emptyList()
        else roundResultDao.getResultsForRounds(roundIds).map { it.toDomain() }
        val resultsByRoundId: Map<String, List<RoundResult>> = results.groupBy { it.roundId }
        val resultsByRoundResultId: Map<String, RoundResult> = results.associateBy { it.id }

        val resultIds = results.map { it.id }
        val handCards: List<HandCard> = if (resultIds.isEmpty()) emptyList()
        else handCardDao.getForRoundResults(resultIds).map { it.toDomain() }
        val handCardsByResultId: Map<String, List<HandCard>> = handCards
            .groupBy { it.roundResultId }
            .mapValues { (_, list) -> list.sortedBy { it.position } }

        val cardsByKey = cardLookup.getAll().associateBy { it.key }

        val perCardContribution = computePerCardContributions(handCardsByResultId, cardsByKey)

        StatsSnapshot(
            closedGames = games,
            participantsByGame = participantsByGame,
            completedRoundsByGame = completedRoundsByGame,
            resultsByRoundResultId = resultsByRoundResultId,
            resultsByRoundId = resultsByRoundId,
            handCardsByResultId = handCardsByResultId,
            perCardContributionByResultId = perCardContribution,
            profilesById = profilesById,
            cardsByKey = cardsByKey,
        )
    }

    private fun computePerCardContributions(
        handCardsByResultId: Map<String, List<HandCard>>,
        cardsByKey: Map<String, de.morzo.realmscore.domain.model.CardDefinition>,
    ): Map<String, Map<String, Int>> {
        val result = mutableMapOf<String, Map<String, Int>>()
        for ((rrId, cards) in handCardsByResultId) {
            val hand = cards.mapNotNull { cardsByKey[it.cardKey] }
            if (hand.size != cards.size) continue
            val assignments = cards.mapNotNull { entry ->
                val target = entry.jokerTargetCardKey ?: return@mapNotNull null
                val suit = entry.jokerTargetSuit
                    ?.let { runCatching { Suit.valueOf(it) }.getOrNull() }
                entry.cardKey to JokerAssignment(
                    jokerKey = entry.cardKey,
                    targetCardKey = target,
                    targetSuit = suit,
                )
            }.toMap()
            try {
                val scoring = scoringEngine.score(
                    ScoringInput(hand = hand, jokerAssignments = assignments),
                )
                result[rrId] = scoring.perCard.associate { it.cardKey to it.contributedScore }
            } catch (e: Exception) {
                // If scoring fails for any reason, skip this hand silently —
                // stats should never crash because of a malformed legacy row.
                continue
            }
        }
        return result
    }

    override suspend fun getGlobalStats(): GlobalStats =
        StatsCalculator.computeGlobalStats(buildSnapshot())

    override suspend fun getOverview(): StatsOverview =
        StatsCalculator.computeOverview(buildSnapshot())

    override suspend fun getPlayerStats(profileId: String): PlayerStats? =
        StatsCalculator.computePlayerStats(buildSnapshot(), profileId)

    override suspend fun getCardStats(cardKey: String): CardStats? =
        StatsCalculator.computeCardStats(buildSnapshot(), cardKey)

    override suspend fun getCardStatsOverview(sortBy: CardStatsSort): List<CardStatsRow> =
        StatsCalculator.computeCardStatsOverview(buildSnapshot(), sortBy)

    override suspend fun getHeadToHeadStats(
        profileIdA: String,
        profileIdB: String,
    ): HeadToHeadStats? = StatsCalculator.computeHeadToHead(
        buildSnapshot(),
        profileIdA,
        profileIdB,
    )

    override suspend fun getClosestRoundEver(): ClosestRoundInfo? =
        StatsCalculator.computeClosestRound(buildSnapshot())

    override suspend fun getMostPlayedPair(): PlayerPairInfo? =
        StatsCalculator.computeMostPlayedPair(buildSnapshot())
}
