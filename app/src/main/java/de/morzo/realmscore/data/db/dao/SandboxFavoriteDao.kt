package de.morzo.realmscore.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import de.morzo.realmscore.data.db.entity.SandboxFavoriteEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SandboxFavoriteDao {

    @Insert
    suspend fun insert(favorite: SandboxFavoriteEntity)

    @Query("SELECT * FROM sandbox_favorites ORDER BY number ASC")
    fun observeAll(): Flow<List<SandboxFavoriteEntity>>

    @Query("SELECT * FROM sandbox_favorites WHERE id = :id")
    suspend fun getById(id: String): SandboxFavoriteEntity?

    @Query("SELECT COALESCE(MAX(number), 0) FROM sandbox_favorites")
    suspend fun maxNumber(): Int

    @Query("DELETE FROM sandbox_favorites WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM sandbox_favorites")
    suspend fun deleteAll()
}
