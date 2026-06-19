package de.morzo.realmscore.data.repository

import de.morzo.realmscore.data.db.dao.DeviceProfileMappingDao
import de.morzo.realmscore.data.db.entity.DeviceProfileMappingEntity
import de.morzo.realmscore.domain.repository.DeviceProfileMappingRepository
import de.morzo.realmscore.domain.util.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class DeviceProfileMappingRepositoryImpl(
    private val dao: DeviceProfileMappingDao,
    private val clock: Clock,
) : DeviceProfileMappingRepository {

    override suspend fun map(deviceId: String, profileId: String) =
        dao.upsert(
            DeviceProfileMappingEntity(
                deviceId = deviceId,
                profileId = profileId,
                createdAt = clock.nowEpochMillis(),
            ),
        )

    override suspend fun getProfileFor(deviceId: String): String? =
        dao.getByDevice(deviceId)?.profileId

    override fun observeAll(): Flow<Map<String, String>> =
        dao.observeAll().map { list -> list.associate { it.deviceId to it.profileId } }

    override suspend fun remove(deviceId: String) = dao.deleteByDevice(deviceId)
}
