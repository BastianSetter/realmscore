package de.morzo.realmscore.data.repository

import de.morzo.realmscore.data.datastore.DeviceUuidProvider
import de.morzo.realmscore.data.db.dao.ProfileDao
import de.morzo.realmscore.data.db.entity.ProfileEntity
import de.morzo.realmscore.domain.model.Profile
import de.morzo.realmscore.domain.repository.ProfileRepository
import de.morzo.realmscore.domain.util.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.security.MessageDigest

class ProfileRepositoryImpl(
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

    private fun generateProfileId(deviceUuid: String, name: String): String {
        val input = "$deviceUuid|${name.lowercase()}"
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }.take(32)
    }

    companion object {
        private const val DEFAULT_OWNER_COLOR_ARGB: Int = 0xFF6750A4.toInt()
    }
}
