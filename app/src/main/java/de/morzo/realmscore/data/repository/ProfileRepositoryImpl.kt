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
import java.util.UUID

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
        // Owner-Identität: profileId == deviceId (Phase-1-Konvention) → an einem joinenden Profil
        // sofort als Owner des anderen Geräts erkennbar.
        val profile = Profile(
            id = surrogateId(deviceUuid, deviceUuid),
            name = trimmed,
            colorArgb = DEFAULT_OWNER_COLOR_ARGB,
            isLocalOwner = true,
            isArchived = false,
            archivedAt = null,
            createdAt = now,
            updatedAt = now,
            originDeviceId = deviceUuid,
            deviceId = deviceUuid,
            profileId = deviceUuid,
            mergeTargetId = null,
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
        // Namens-Eindeutigkeit gelockert (Phase 1): Duplikate sind erlaubt, Merges regeln Dubletten.
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

    override suspend fun createProfile(name: String): Profile {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "Profile name must not be blank." }
        // Namens-Eindeutigkeit gelockert (Phase 1): Duplikate erlaubt; Identität = (deviceId, profileId).

        val deviceUuid = deviceUuidProvider.get()
        val newProfileId = UUID.randomUUID().toString()
        val now = clock.nowEpochMillis()
        val color = ProfilePalette.pickNextColor(dao.getAllColors())
        val profile = Profile(
            id = surrogateId(deviceUuid, newProfileId),
            name = trimmed,
            colorArgb = color,
            isLocalOwner = false,
            isArchived = false,
            archivedAt = null,
            createdAt = now,
            updatedAt = now,
            originDeviceId = deviceUuid,
            deviceId = deviceUuid,
            profileId = newProfileId,
            mergeTargetId = null,
        )
        dao.insert(ProfileEntity.fromDomain(profile))
        return profile
    }

    override suspend fun ensureRemoteProfile(
        id: String,
        name: String,
        colorArgb: Int,
        originDeviceId: String,
    ): Profile {
        dao.getById(id)?.let { return it.toDomain() }
        val now = clock.nowEpochMillis()
        val profile = Profile(
            id = id,
            name = name,
            colorArgb = colorArgb,
            isLocalOwner = false,
            isArchived = false,
            archivedAt = null,
            createdAt = now,
            updatedAt = now,
            originDeviceId = originDeviceId,
            deviceId = originDeviceId,
            // id-Konvention "$deviceId:$profileId" → profileId-Spalte aus der Surrogat-id ableiten.
            profileId = id.substringAfter(':', id),
            mergeTargetId = null,
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

    override suspend fun updateName(id: String, newName: String) {
        val trimmed = newName.trim()
        require(trimmed.isNotEmpty()) { "Profile name must not be blank." }
        val current = dao.getById(id)
            ?: throw IllegalStateException("Profile '$id' not found – cannot rename.")
        if (current.name == trimmed) return
        // Namens-Eindeutigkeit gelockert (Phase 1): Duplikate erlaubt.
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

    // --- Phase 2: non-destruktiver Zeiger-Merge ---

    override fun observeMergedProfiles(): Flow<List<Profile>> =
        dao.observeMerged().map { list -> list.map { it.toDomain() } }

    override suspend fun setMergeTarget(id: String, targetId: String) {
        require(id != targetId) { "Cannot merge a profile into itself." }
        database.withTransaction {
            val source = dao.getById(id)
                ?: throw IllegalStateException("Profile '$id' not found – cannot merge.")
            require(!source.isLocalOwner) { "Owner profile cannot be merged." }

            // Kettenende auflösen: zeigt das gewählte Ziel selbst auf etwas, folgen wir bis zum Ende
            // ("keine Ketten, eine Wahrheit"). Dabei Zyklen erkennen (darf nie auf [id] zurückführen).
            val visited = linkedSetOf(id)
            var cursor = targetId
            var depth = 0
            while (true) {
                require(cursor !in visited) { "Merge would create a cycle." }
                visited += cursor
                val node = dao.getById(cursor) ?: break // dangling target → cursor ist das Ende
                val next = node.mergeTargetId ?: break
                cursor = next
                if (++depth > MAX_MERGE_DEPTH) break
            }
            val finalTarget = cursor
            require(finalTarget != id) { "Merge would create a cycle." }

            val now = clock.nowEpochMillis()
            dao.setMergeTarget(id, finalTarget, now)
            // Vorhandene Zeiger, die auf dieses (jetzt selbst gemergte) Profil zeigen, aufs neue Ende
            // nachziehen, damit keine Ketten entstehen.
            dao.repointPointers(oldId = id, newTarget = finalTarget, ts = now)
            dao.touch(finalTarget, now)
        }
    }

    override suspend fun clearMergeTarget(id: String) {
        dao.clearMergeTarget(id, clock.nowEpochMillis())
    }

    private fun surrogateId(deviceId: String, profileId: String): String = "$deviceId:$profileId"

    companion object {
        private const val DEFAULT_OWNER_COLOR_ARGB: Int = 0xFF6750A4.toInt()
        private const val MAX_MERGE_DEPTH = 32
    }
}
