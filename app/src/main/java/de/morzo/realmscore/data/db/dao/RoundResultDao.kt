package de.morzo.realmscore.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import de.morzo.realmscore.data.db.entity.RoundResultEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RoundResultDao {

    @Insert
    suspend fun insert(result: RoundResultEntity)

    @Query("SELECT * FROM round_results WHERE roundId = :roundId AND profileId = :profileId LIMIT 1")
    suspend fun getForRoundAndProfile(roundId: String, profileId: String): RoundResultEntity?

    @Query("UPDATE round_results SET totalScore = :score, updatedAt = :ts WHERE id = :id")
    suspend fun updateScore(id: String, score: Int, ts: Long)

    @Query("SELECT * FROM round_results WHERE roundId = :roundId")
    fun observeResultsForRound(roundId: String): Flow<List<RoundResultEntity>>

    @Query(
        "SELECT rr.* FROM round_results rr " +
            "INNER JOIN rounds r ON rr.roundId = r.id " +
            "WHERE r.gameId = :gameId"
    )
    fun observeResultsForGame(gameId: String): Flow<List<RoundResultEntity>>

    @Query(
        "SELECT r.gameId AS gameId, rr.profileId AS profileId, " +
            "SUM(rr.totalScore) AS totalScore " +
            "FROM round_results rr " +
            "INNER JOIN rounds r ON rr.roundId = r.id " +
            "WHERE r.gameId IN (:gameIds) " +
            "GROUP BY r.gameId, rr.profileId"
    )
    suspend fun getScoreTotalsForGames(gameIds: List<String>): List<GameScoreTotal>

    @Query("SELECT * FROM round_results WHERE roundId IN (:roundIds)")
    suspend fun getResultsForRounds(roundIds: List<String>): List<RoundResultEntity>

    // --- Phase 24 M1: cheap signals for the stats-snapshot cache fingerprint ---

    @Query("SELECT COUNT(*) FROM round_results")
    suspend fun getResultCount(): Int

    @Query("SELECT MAX(updatedAt) FROM round_results")
    suspend fun getMaxUpdatedAt(): Long?

    @Query("DELETE FROM round_results")
    suspend fun deleteAll()
}
