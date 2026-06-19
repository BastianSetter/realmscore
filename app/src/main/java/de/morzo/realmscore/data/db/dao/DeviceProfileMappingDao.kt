package de.morzo.realmscore.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import de.morzo.realmscore.data.db.entity.DeviceProfileMappingEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DeviceProfileMappingDao {

    /** Upsert: re-mapping a device overwrites the previous local profile. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(mapping: DeviceProfileMappingEntity)

    @Query("SELECT * FROM device_profile_mappings WHERE deviceId = :deviceId")
    suspend fun getByDevice(deviceId: String): DeviceProfileMappingEntity?

    @Query("SELECT * FROM device_profile_mappings")
    fun observeAll(): Flow<List<DeviceProfileMappingEntity>>

    @Query("DELETE FROM device_profile_mappings WHERE deviceId = :deviceId")
    suspend fun deleteByDevice(deviceId: String)
}
