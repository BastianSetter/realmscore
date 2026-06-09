package de.morzo.realmscore.data.repository

import androidx.room.withTransaction
import de.morzo.realmscore.data.datastore.DeviceUuidProvider
import de.morzo.realmscore.data.db.AppDatabase
import de.morzo.realmscore.data.db.dao.ProfileDao
import de.morzo.realmscore.data.db.entity.ProfileEntity
import de.morzo.realmscore.domain.model.Profile
import de.morzo.realmscore.domain.profile.ProfileRelevance
import de.morzo.realmscore.domain.repository.ProfileRepository
import de.morzo.realmscore.domain.util.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.security.MessageDigest

class ProfileRepositoryImpl(
    private val database: AppDatabase,
    private val dao: ProfileDao,
    private val deviceUuidProvider: DeviceUuidProvider,
    private val clock: Clock,
) : ProfileRepository {

    override suspend fun getLocalOwner(): Profile? =
        dao.getLocalOwner()?.toDomain()

    override fun observeLocalOwner(): Flow<Profile?> =
        dao.observeLocalOwner().map { it?.toDomain() }

    override suspend fun createOwner(name: String): Profile {
        require(dao.getLocalOwner() == null) {
            "Local owner already exists – createOwner must be called only once."
        }
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "Owner name must not be blank." }

        val deviceUuid = deviceUuidProvider.get()
        val now = clock.nowEpochMillis()
        val profile = Profile(
            id = generateProfileId(deviceUuid, trimmed),
            name = trimmed,
            colorArgb = DEFAULT_OWNER_COLOR_ARGB,
            isLocalOwner = true,
            isArchived = false,
            archivedAt = null,
            createdAt = now,
            updatedAt = now,
            originDeviceId = deviceUuid,
        )
        dao.insert(ProfileEntity.fromDomain(profile))
        return profile
    }

    override suspend fun updateOwnerName(newName: String): Profile {
        val trimmed = newName.trim()
        require(trimmed.isNotEmpty()) { "Owner name must not be blank." }
        val current = dao.getLocalOwner()
            ?: throw IllegalStateException("No local owner present – cannot rename.")
        if (current.name == trimmed) return current.toDomain()
        require(dao.countByName(trimmed) == 0) {
            "A profile with name '$trimmed' already exists."
        }
        val now = clock.nowEpochMillis()
        dao.updateName(current.id, trimmed, now)
        return current.copy(name = trimmed, updatedAt = now).toDomain()
    }

    override suspend fun searchByNamePrefix(prefix: String): List<Profile> {
        val trimmed = prefix.trim()
        if (trimmed.isEmpty()) return emptyList()
        return dao.searchByNamePrefix(trimmed).map { it.toDomain() }
    }

    override suspend fun suggestProfiles(
        prefix: String,
        excludeProfileIds: Set<String>,
        ownerId: String,
    ): List<Profile> {
        val candidates = dao.getActiveByPrefix(prefix.trim())
            .filter { it.id != ownerId && it.id !in excludeProfileIds }
        if (candidates.isEmpty()) return emptyList()

        // Relevance: sum over shared games of exp(-ageDays / HALF_LIFE). More & more recent games
        // rank higher. Profiles with no shared history score 0 and fall back to alphabetical order.
        val now = clock.nowEpochMillis()
        val scoreById = dao.getSharedGamesWithOwner(ownerId)
            .groupBy { it.profileId }
            .mapValues { (_, games) ->
                ProfileRelevance.score(games.map { it.startedAt }, now)
            }

        return candidates
            .sortedWith(
                compareByDescending<ProfileEntity> { scoreById[it.id] ?: 0.0 }
                    .thenBy { it.name.lowercase() },
            )
            .map { it.toDomain() }
    }

    override suspend fun existsByName(name: String): Boolean {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return false
        return dao.countByName(trimmed) > 0
    }

    override suspend fun createProfile(name: String): Profile {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "Profile name must not be blank." }
        require(dao.countByName(trimmed) == 0) {
            "Profile with name '$trimmed' already exists."
        }

        val deviceUuid = deviceUuidProvider.get()
        val now = clock.nowEpochMillis()
        val color = ProfilePalette.pickNextColor(dao.getAllColors())
        val profile = Profile(
            id = generateProfileId(deviceUuid, trimmed),
            name = trimmed,
            colorArgb = color,
            isLocalOwner = false,
            isArchived = false,
            archivedAt = null,
            createdAt = now,
            updatedAt = now,
            originDeviceId = deviceUuid,
        )
        dao.insert(ProfileEntity.fromDomain(profile))
        return profile
    }

    override suspend fun getById(id: String): Profile? =
        dao.getById(id)?.toDomain()

    override fun observeAll(): Flow<List<Profile>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    override fun observeActiveProfiles(): Flow<List<Profile>> =
        dao.observeActive().map { list -> list.map { it.toDomain() } }

    override fun observeArchivedProfiles(): Flow<List<Profile>> =
        dao.observeArchived().map { list -> list.map { it.toDomain() } }

    override suspend fun countGamesForProfile(id: String): Int =
        dao.countGamesForProfile(id)

    override suspend fun countCombinedGames(keepId: String, discardId: String): Int =
        dao.countCombinedGames(keepId, discardId)

    override suspend fun updateName(id: String, newName: String) {
        val trimmed = newName.trim()
        require(trimmed.isNotEmpty()) { "Profile name must not be blank." }
        val current = dao.getById(id)
            ?: throw IllegalStateException("Profile '$id' not found – cannot rename.")
        if (current.name == trimmed) return
        require(dao.countByName(trimmed) == 0) {
            "A profile with name '$trimmed' already exists."
        }
        dao.updateName(id, trimmed, clock.nowEpochMillis())
    }

    override suspend fun updateColor(id: String, colorArgb: Int) {
        dao.updateColor(id, colorArgb, clock.nowEpochMillis())
    }

    override suspend fun archiveProfile(id: String) {
        dao.archive(id, clock.nowEpochMillis())
    }

    override suspend fun unarchiveProfile(id: String) {
        dao.unarchive(id, clock.nowEpochMillis())
    }

    override suspend fun mergeProfiles(keepId: String, discardId: String) {
        require(keepId != discardId) { "Cannot merge a profile into itself." }
        database.withTransaction {
            val now = clock.nowEpochMillis()
            // Reihenfolge wichtig: erst Kollisionen entfernen, dann umschreiben.
            dao.deleteConflictingParticipants(keepId, discardId)
            dao.reassignParticipants(keepId, discardId)
            dao.reassignRoundResults(keepId, discardId)
            dao.archive(discardId, now)
            dao.touch(keepId, now)
        }
    }

    private fun generateProfileId(deviceUuid: String, name: String): String {
        val input = "$deviceUuid|${name.lowercase()}"
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }.take(32)
    }

    companion object {
        private const val DEFAULT_OWNER_COLOR_ARGB: Int = 0xFF6750A4.toInt()
    }
}
