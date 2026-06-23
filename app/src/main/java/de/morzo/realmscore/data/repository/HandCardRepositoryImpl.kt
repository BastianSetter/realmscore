package de.morzo.realmscore.data.repository

import androidx.room.withTransaction
import de.morzo.realmscore.data.datastore.DeviceUuidProvider
import de.morzo.realmscore.data.db.AppDatabase
import de.morzo.realmscore.data.db.dao.GameDao
import de.morzo.realmscore.data.db.dao.HandCardDao
import de.morzo.realmscore.data.db.dao.RoundDao
import de.morzo.realmscore.data.db.dao.RoundResultDao
import de.morzo.realmscore.data.db.entity.HandCardEntity
import de.morzo.realmscore.data.db.entity.RoundResultEntity
import de.morzo.realmscore.domain.repository.HandCardEntry
import de.morzo.realmscore.domain.repository.HandCardRepository
import de.morzo.realmscore.domain.repository.SavedHand
import de.morzo.realmscore.domain.util.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

class HandCardRepositoryImpl(
    private val database: AppDatabase,
    private val handCardDao: HandCardDao,
    private val roundResultDao: RoundResultDao,
    private val roundDao: RoundDao,
    private val gameDao: GameDao,
    private val deviceUuidProvider: DeviceUuidProvider,
    private val clock: Clock,
) : HandCardRepository {

    override suspend fun saveHand(
        roundId: String,
        profileId: String,
        cards: List<HandCardEntry>,
        totalScore: Int,
    ) {
        val now = clock.nowEpochMillis()
        val deviceId = deviceUuidProvider.get()

        database.withTransaction {
            val round = roundDao.getById(roundId)
                ?: error("Round not found: $roundId")

            val existing = roundResultDao.getForRoundAndProfile(roundId, profileId)
            val roundResultId = if (existing != null) {
                roundResultDao.updateScore(existing.id, totalScore, now)
                handCardDao.deleteAllForRoundResult(existing.id)
                existing.id
            } else {
                val newId = UUID.randomUUID().toString()
                roundResultDao.insert(
                    RoundResultEntity(
                        id = newId,
                        roundId = roundId,
                        profileId = profileId,
                        totalScore = totalScore,
                        createdAt = now,
                        updatedAt = now,
                        originDeviceId = deviceId,
                    )
                )
                newId
            }

            val cardEntities = cards.map { entry ->
                HandCardEntity(
                    id = UUID.randomUUID().toString(),
                    roundResultId = roundResultId,
                    cardKey = entry.cardKey,
                    position = entry.position,
                    jokerTargetCardKey = entry.jokerTargetCardKey,
                    jokerTargetSuit = entry.jokerTargetSuit,
                    createdAt = now,
                    updatedAt = now,
                )
            }
            handCardDao.insertAll(cardEntities)

            roundDao.touch(roundId, now)
            gameDao.touch(round.gameId, now)
        }
    }

    override suspend fun getHand(roundId: String, profileId: String): SavedHand? {
        val result = roundResultDao.getForRoundAndProfile(roundId, profileId) ?: return null
        val cards = handCardDao.getForRoundResult(result.id).map { e ->
            HandCardEntry(
                cardKey = e.cardKey,
                position = e.position,
                jokerTargetCardKey = e.jokerTargetCardKey,
                jokerTargetSuit = e.jokerTargetSuit,
            )
        }
        return SavedHand(cards = cards, totalScore = result.totalScore)
    }

    override fun observeHandCardCountByProfile(roundId: String): Flow<Map<String, Int>> =
        handCardDao.observeHandCountsByProfile(roundId).map { rows ->
            rows.associate { it.profileId to it.handCardCount }
        }

    override fun observeCardKeysUsedByOtherProfiles(
        roundId: String,
        excludeProfileId: String,
    ): Flow<Set<String>> =
        handCardDao.observeCardKeysForRoundExcluding(roundId, excludeProfileId)
            .map { it.toSet() }

    override fun observeHandCardKeysByProfile(roundId: String): Flow<Map<String, Set<String>>> =
        handCardDao.observeHandCardKeysByProfile(roundId).map { rows ->
            rows.groupBy({ it.profileId }, { it.cardKey }).mapValues { it.value.toSet() }
        }
}
