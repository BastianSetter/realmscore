package de.morzo.realmscore.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import de.morzo.realmscore.data.db.entity.RoundEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RoundDao {

    @Insert
    suspend fun insert(round: RoundEntity)

    @Query("SELECT * FROM rounds WHERE id = :id")
    suspend fun getById(id: String): RoundEntity?

    @Query("SELECT * FROM rounds WHERE id = :id")
    fun observeById(id: String): Flow<RoundEntity?>

    @Query("UPDATE rounds SET discardScanned = :scanned, updatedAt = :ts WHERE id = :id")
    suspend fun setDiscardScanned(id: String, scanned: Boolean, ts: Long)

    @Query("SELECT * FROM rounds WHERE gameId = :gameId AND completedAt IS NULL LIMIT 1")
    suspend fun getOpenRound(gameId: String): RoundEntity?

    @Query("SELECT * FROM rounds WHERE gameId = :gameId ORDER BY roundNumber ASC")
    fun observeRoundsForGame(gameId: String): Flow<List<RoundEntity>>

    @Query("SELECT MAX(roundNumber) FROM rounds WHERE gameId = :gameId")
    suspend fun getMaxRoundNumber(gameId: String): Int?

    @Query("SELECT * FROM rounds WHERE gameId IN (:gameIds) AND completedAt IS NOT NULL")
    suspend fun getCompletedRoundsForGames(gameIds: List<String>): List<RoundEntity>

    @Query("UPDATE rounds SET updatedAt = :ts WHERE id = :id")
    suspend fun touch(id: String, ts: Long)

    @Query(
        "UPDATE rounds SET completedAt = :ts, updatedAt = :ts " +
            "WHERE id = :id AND completedAt IS NULL"
    )
    suspend fun markCompleted(id: String, ts: Long)

    @Query("SELECT COUNT(*) FROM rounds")
    fun observeRoundCount(): Flow<Int>

    @Query("DELETE FROM rounds")
    suspend fun deleteAll()
}
