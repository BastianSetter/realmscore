package de.morzo.realmscore.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
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

    /**
     * Phase 24 M2: ergänzt fehlende Teilnehmer beim Backup-Import in ein bereits existierendes Spiel,
     * ohne bei vorhandenen `(gameId, profileId)`-Schlüsseln eine PK-Verletzung auszulösen.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertParticipantsIgnore(participants: List<GameParticipantEntity>)

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

    /**
     * Phase 28 Stage B: overwrite the mutable game-header fields from a newer mirror copy (LWW).
     * Identity columns (id/mode/targets/startedAt/createdAt/originDeviceId) are never touched.
     */
    @Query(
        "UPDATE games SET displayName = :displayName, closedAt = :closedAt, " +
            "closedReason = :closedReason, updatedAt = :updatedAt WHERE id = :id"
    )
    suspend fun updateGameMeta(
        id: String,
        displayName: String?,
        closedAt: Long?,
        closedReason: String?,
        updatedAt: Long,
    )

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
        "UPDATE game_participants SET lastScanOrder = :order " +
            "WHERE gameId = :gameId AND profileId = :profileId"
    )
    suspend fun updateScanOrder(gameId: String, profileId: String, order: Int)

    @Query(
        "SELECT * FROM game_participants WHERE gameId IN (:gameIds) " +
            "ORDER BY gameId ASC, seatOrder ASC"
    )
    suspend fun getParticipantsForGames(gameIds: List<String>): List<GameParticipantEntity>

    @Query("SELECT COUNT(*) FROM games WHERE closedAt IS NULL")
    fun observeOpenGameCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM games WHERE closedAt IS NOT NULL")
    fun observeClosedGameCount(): Flow<Int>

    /** Phase 24 M1: cheap signal for the stats-snapshot cache fingerprint. */
    @Query("SELECT COUNT(*) FROM games WHERE closedAt IS NOT NULL")
    suspend fun getClosedGameCount(): Int

    @Query("DELETE FROM games")
    suspend fun deleteAll()
}
