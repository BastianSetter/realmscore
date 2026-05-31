package de.morzo.realmscore.data.repository

import de.morzo.realmscore.data.datastore.DeviceUuidProvider
import de.morzo.realmscore.data.db.dao.GameDao
import de.morzo.realmscore.data.db.dao.RoundDao
import de.morzo.realmscore.data.db.dao.RoundResultDao
import de.morzo.realmscore.data.db.entity.RoundEntity
import de.morzo.realmscore.domain.model.Round
import de.morzo.realmscore.domain.model.RoundResult
import de.morzo.realmscore.domain.repository.RoundRepository
import de.morzo.realmscore.domain.util.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

class RoundRepositoryImpl(
    private val roundDao: RoundDao,
    private val roundResultDao: RoundResultDao,
    private val gameDao: GameDao,
    private val deviceUuidProvider: DeviceUuidProvider,
    private val clock: Clock,
) : RoundRepository {

    override fun observeRoundsForGame(gameId: String): Flow<List<Round>> =
        roundDao.observeRoundsForGame(gameId).map { list -> list.map { it.toDomain() } }

    override suspend fun getOpenRound(gameId: String): Round? =
        roundDao.getOpenRound(gameId)?.toDomain()

    override suspend fun startRound(gameId: String): Round {
        roundDao.getOpenRound(gameId)?.let { return it.toDomain() }

        val now = clock.nowEpochMillis()
        val nextNumber = (roundDao.getMaxRoundNumber(gameId) ?: 0) + 1
        val round = Round(
            id = UUID.randomUUID().toString(),
            gameId = gameId,
            roundNumber = nextNumber,
            startedAt = now,
            completedAt = null,
            discardScanned = false,
            createdAt = now,
            updatedAt = now,
            originDeviceId = deviceUuidProvider.get(),
        )
        roundDao.insert(RoundEntity.fromDomain(round))
        gameDao.touch(gameId, now)
        return round
    }

    override fun observeResults(roundId: String): Flow<List<RoundResult>> =
        roundResultDao.observeResultsForRound(roundId).map { list -> list.map { it.toDomain() } }

    override fun observeResultsForGame(gameId: String): Flow<List<RoundResult>> =
        roundResultDao.observeResultsForGame(gameId).map { list -> list.map { it.toDomain() } }

    override suspend fun getRoundById(id: String): Round? =
        roundDao.getById(id)?.toDomain()

    override suspend fun getDiscardCards(roundId: String): List<String> = emptyList()

    override suspend fun markRoundCompleted(roundId: String) {
        val now = clock.nowEpochMillis()
        val round = roundDao.getById(roundId) ?: return
        roundDao.markCompleted(roundId, now)
        gameDao.touch(round.gameId, now)
    }

    override suspend fun getScoreTotalsForGames(
        gameIds: List<String>,
    ): Map<String, Map<String, Int>> {
        if (gameIds.isEmpty()) return emptyMap()
        val rows = roundResultDao.getScoreTotalsForGames(gameIds)
        return rows.groupBy { it.gameId }
            .mapValues { (_, list) -> list.associate { it.profileId to it.totalScore } }
    }
}
