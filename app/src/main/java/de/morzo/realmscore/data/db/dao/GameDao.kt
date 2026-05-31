package de.morzo.realmscore.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import de.morzo.realmscore.data.db.entity.GameEntity
import de.morzo.realmscore.data.db.entity.GameParticipantEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface GameDao {

    @Insert
    suspend fun insert(game: GameEntity)

    @Insert
    suspend fun insertParticipants(participants: List<GameParticipantEntity>)

    @Transaction
    suspend fun insertGameWithParticipants(
        game: GameEntity,
        participants: List<GameParticipantEntity>,
    ) {
        insert(game)
        insertParticipants(participants)
    }

    @Query("SELECT * FROM games WHERE closedAt IS NULL ORDER BY updatedAt DESC")
    fun observeOpenGames(): Flow<List<GameEntity>>

    @Query(
        "SELECT * FROM games " +
            "ORDER BY CASE WHEN closedAt IS NULL THEN updatedAt ELSE closedAt END DESC"
    )
    fun observeAllGames(): Flow<List<GameEntity>>

    @Query("SELECT * FROM games WHERE closedAt IS NOT NULL ORDER BY closedAt DESC")
    fun observeClosedGames(): Flow<List<GameEntity>>

    @Query("SELECT * FROM games WHERE id = :id")
    suspend fun getById(id: String): GameEntity?

    @Query("SELECT * FROM games WHERE closedAt IS NOT NULL")
    suspend fun getClosedGames(): List<GameEntity>

    @Query("SELECT * FROM games")
    suspend fun getAllGames(): List<GameEntity>

    @Query("UPDATE games SET updatedAt = :ts WHERE id = :id")
    suspend fun touch(id: String, ts: Long)

    @Query(
        "UPDATE games SET closedAt = :ts, closedReason = :reason, updatedAt = :ts " +
            "WHERE id = :id AND closedAt IS NULL"
    )
    suspend fun closeGame(id: String, ts: Long, reason: String)

    @Query(
        "UPDATE games SET closedAt = NULL, closedReason = NULL, updatedAt = :ts " +
            "WHERE id = :id"
    )
    suspend fun reopenGame(id: String, ts: Long)

    @Query("SELECT * FROM game_participants WHERE gameId = :gameId ORDER BY seatOrder ASC")
    suspend fun getParticipants(gameId: String): List<GameParticipantEntity>

    @Query(
        "SELECT * FROM game_participants WHERE gameId IN (:gameIds) " +
            "ORDER BY gameId ASC, seatOrder ASC"
    )
    suspend fun getParticipantsForGames(gameIds: List<String>): List<GameParticipantEntity>

    @Query("SELECT COUNT(*) FROM games WHERE closedAt IS NULL")
    fun observeOpenGameCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM games WHERE closedAt IS NOT NULL")
    fun observeClosedGameCount(): Flow<Int>

    @Query("DELETE FROM games")
    suspend fun deleteAll()
}
