package de.morzo.realmscore.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import de.morzo.realmscore.data.db.entity.ProfileEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: ProfileEntity)

    @Query("SELECT * FROM profile WHERE isLocalOwner = 1 LIMIT 1")
    suspend fun getLocalOwner(): ProfileEntity?

    @Query("SELECT * FROM profile WHERE isLocalOwner = 1 LIMIT 1")
    fun observeLocalOwner(): Flow<ProfileEntity?>

    @Query("SELECT * FROM profile ORDER BY createdAt ASC")
    fun observeAll(): Flow<List<ProfileEntity>>

    @Query(
        "SELECT * FROM profile " +
            "WHERE name LIKE :prefix || '%' COLLATE NOCASE " +
            "AND isArchived = 0 " +
            "ORDER BY name COLLATE NOCASE"
    )
    suspend fun searchByNamePrefix(prefix: String): List<ProfileEntity>

    @Query("SELECT COUNT(*) FROM profile WHERE name = :name COLLATE NOCASE")
    suspend fun countByName(name: String): Int

    @Query("SELECT colorArgb FROM profile")
    suspend fun getAllColors(): List<Int>

    @Query("SELECT * FROM profile WHERE id = :id")
    suspend fun getById(id: String): ProfileEntity?

    @Query("SELECT COUNT(*) FROM profile")
    fun observeProfileCount(): Flow<Int>

    @Query("UPDATE profile SET name = :name, updatedAt = :ts WHERE id = :id")
    suspend fun updateName(id: String, name: String, ts: Long)

    @Query("DELETE FROM profile")
    suspend fun deleteAll()
}
