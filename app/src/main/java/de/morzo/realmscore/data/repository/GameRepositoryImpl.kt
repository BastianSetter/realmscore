package de.morzo.realmscore.data.repository

import de.morzo.realmscore.data.datastore.DeviceUuidProvider
import de.morzo.realmscore.data.db.dao.GameDao
import de.morzo.realmscore.data.db.entity.GameEntity
import de.morzo.realmscore.data.db.entity.GameParticipantEntity
import de.morzo.realmscore.domain.model.ClosedReason
import de.morzo.realmscore.domain.model.Game
import de.morzo.realmscore.domain.model.GameMode
import de.morzo.realmscore.domain.model.GameParticipant
import de.morzo.realmscore.domain.repository.GameRepository
import de.morzo.realmscore.domain.util.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

class GameRepositoryImpl(
    private val dao: GameDao,
    private val deviceUuidProvider: DeviceUuidProvider,
    private val clock: Clock,
) : GameRepository {

    override fun observeOpenGames(): Flow<List<Game>> =
        dao.observeOpenGames().map { list -> list.map { it.toDomain() } }

    override fun observeAllGames(): Flow<List<Game>> =
        dao.observeAllGames().map { list -> list.map { it.toDomain() } }

    override fun observeClosedGames(): Flow<List<Game>> =
        dao.observeClosedGames().map { list -> list.map { it.toDomain() } }

    override suspend fun getById(id: String): Game? =
        dao.getById(id)?.toDomain()

    override suspend fun getParticipants(gameId: String): List<GameParticipant> =
        dao.getParticipants(gameId).map { it.toDomain() }

    override suspend fun getParticipantsForGames(gameIds: List<String>): List<GameParticipant> {
        if (gameIds.isEmpty()) return emptyList()
        return dao.getParticipantsForGames(gameIds).map { it.toDomain() }
    }

    override suspend fun startGame(
        mode: GameMode,
        target: Int,
        participantProfileIds: List<String>,
        displayName: String?,
    ): Game {
        require(participantProfileIds.size in 2..6) {
            "Game must have between 2 and 6 participants."
        }
        require(participantProfileIds.distinct().size == participantProfileIds.size) {
            "Participant list contains duplicates."
        }
        require(target > 0) { "Target must be > 0." }

        val now = clock.nowEpochMillis()
        val deviceUuid = deviceUuidProvider.get()
        val game = Game(
            id = UUID.randomUUID().toString(),
            displayName = displayName,
            mode = mode,
            targetRounds = if (mode == GameMode.FIXED_ROUNDS) target else null,
            targetPoints = if (mode == GameMode.POINT_LIMIT) target else null,
            startedAt = now,
            closedAt = null,
            closedReason = null,
            createdAt = now,
            updatedAt = now,
            originDeviceId = deviceUuid,
        )
        val participants = participantProfileIds.mapIndexed { index, profileId ->
            GameParticipantEntity(
                gameId = game.id,
                profileId = profileId,
                seatOrder = index,
                lastScanOrder = null,
            )
        }
        dao.insertGameWithParticipants(GameEntity.fromDomain(game), participants)
        return game
    }

    override suspend fun closeGame(gameId: String, reason: ClosedReason) {
        val now = clock.nowEpochMillis()
        dao.closeGame(gameId, now, reason.name)
    }

    override suspend fun reopenGame(gameId: String) {
        val now = clock.nowEpochMillis()
        dao.reopenGame(gameId, now)
    }
}
