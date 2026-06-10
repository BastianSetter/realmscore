package de.morzo.realmscore.data.repository

import de.morzo.realmscore.data.datastore.DeviceUuidProvider
import de.morzo.realmscore.data.db.dao.SandboxFavoriteDao
import de.morzo.realmscore.data.db.entity.SandboxFavoriteEntity
import de.morzo.realmscore.domain.model.FavoriteCard
import de.morzo.realmscore.domain.model.SandboxFavorite
import de.morzo.realmscore.domain.repository.SandboxFavoriteRepository
import de.morzo.realmscore.domain.util.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.UUID

class SandboxFavoriteRepositoryImpl(
    private val dao: SandboxFavoriteDao,
    private val deviceUuidProvider: DeviceUuidProvider,
    private val clock: Clock,
) : SandboxFavoriteRepository {

    override fun observeAll(): Flow<List<SandboxFavorite>> =
        dao.observeAll().map { rows -> rows.map { it.toDomain() } }

    override suspend fun getById(id: String): SandboxFavorite? =
        dao.getById(id)?.toDomain()

    override suspend fun save(cards: List<FavoriteCard>): Int {
        val now = clock.nowEpochMillis()
        val number = dao.maxNumber() + 1
        val entity = SandboxFavoriteEntity(
            id = UUID.randomUUID().toString(),
            number = number,
            cardsJson = json.encodeToString(cards.map { it.toDto() }),
            createdAt = now,
            updatedAt = now,
            originDeviceId = deviceUuidProvider.get(),
        )
        dao.insert(entity)
        return number
    }

    override suspend fun delete(id: String) = dao.deleteById(id)

    private fun SandboxFavoriteEntity.toDomain(): SandboxFavorite = SandboxFavorite(
        id = id,
        number = number,
        handCards = runCatching {
            json.decodeFromString<List<FavoriteCardDto>>(cardsJson).map { it.toDomain() }
        }.getOrDefault(emptyList()),
        createdAt = createdAt,
    )

    companion object {
        private val json = Json { ignoreUnknownKeys = true }
    }
}

@Serializable
private data class FavoriteCardDto(
    val position: Int,
    val cardKey: String,
    val jokerTargetCardKey: String? = null,
    val jokerTargetSuit: String? = null,
) {
    fun toDomain(): FavoriteCard = FavoriteCard(
        position = position,
        cardKey = cardKey,
        jokerTargetCardKey = jokerTargetCardKey,
        jokerTargetSuit = jokerTargetSuit,
    )
}

private fun FavoriteCard.toDto(): FavoriteCardDto = FavoriteCardDto(
    position = position,
    cardKey = cardKey,
    jokerTargetCardKey = jokerTargetCardKey,
    jokerTargetSuit = jokerTargetSuit,
)
