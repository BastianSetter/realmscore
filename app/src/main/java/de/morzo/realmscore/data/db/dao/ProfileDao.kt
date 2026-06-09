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

    /**
     * Active (non-archived) profiles whose name starts with [prefix] (empty prefix = all active).
     * Phase 18.1, Punkt 3: feeds the relevance-ranked autocomplete in NewGame.
     */
    @Query(
        "SELECT * FROM profile " +
            "WHERE isArchived = 0 " +
            "AND name LIKE :prefix || '%' COLLATE NOCASE"
    )
    suspend fun getActiveByPrefix(prefix: String): List<ProfileEntity>

    /**
     * For every game the owner played, the (other) participant ids and that game's start time.
     * Used to compute each profile's relevance score (frequency + recency) for autocomplete.
     */
    @Query(
        "SELECT other.profileId AS profileId, g.startedAt AS startedAt " +
            "FROM game_participants other " +
            "JOIN game_participants owner_p " +
            "ON owner_p.gameId = other.gameId AND owner_p.profileId = :ownerId " +
            "JOIN games g ON g.id = other.gameId " +
            "WHERE other.profileId != :ownerId"
    )
    suspend fun getSharedGamesWithOwner(ownerId: String): List<ProfileSharedGame>

    @Query("SELECT colorArgb FROM profile")
    suspend fun getAllColors(): List<Int>

    @Query("SELECT * FROM profile WHERE id = :id")
    suspend fun getById(id: String): ProfileEntity?

    @Query("SELECT COUNT(*) FROM profile")
    fun observeProfileCount(): Flow<Int>

    @Query("UPDATE profile SET name = :name, updatedAt = :ts WHERE id = :id")
    suspend fun updateName(id: String, name: String, ts: Long)

    // --- Phase 17: Profilverwaltung ---

    @Query("SELECT * FROM profile WHERE isArchived = 0 ORDER BY isLocalOwner DESC, name COLLATE NOCASE")
    fun observeActive(): Flow<List<ProfileEntity>>

    @Query("SELECT * FROM profile WHERE isArchived = 1 ORDER BY name COLLATE NOCASE")
    fun observeArchived(): Flow<List<ProfileEntity>>

    @Query("UPDATE profile SET colorArgb = :color, updatedAt = :ts WHERE id = :id")
    suspend fun updateColor(id: String, color: Int, ts: Long)

    @Query("UPDATE profile SET updatedAt = :ts WHERE id = :id")
    suspend fun touch(id: String, ts: Long)

    @Query("UPDATE profile SET isArchived = 1, archivedAt = :ts, updatedAt = :ts WHERE id = :id")
    suspend fun archive(id: String, ts: Long)

    @Query("UPDATE profile SET isArchived = 0, archivedAt = NULL, updatedAt = :ts WHERE id = :id")
    suspend fun unarchive(id: String, ts: Long)

    @Query("SELECT COUNT(*) FROM game_participants WHERE profileId = :profileId")
    suspend fun countGamesForProfile(profileId: String): Int

    @Query(
        "SELECT COUNT(DISTINCT gameId) FROM game_participants " +
            "WHERE profileId IN (:keepId, :discardId)"
    )
    suspend fun countCombinedGames(keepId: String, discardId: String): Int

    /**
     * Entfernt die Teilnehmer-Zeilen des zu verwerfenden Profils für Spiele, in denen
     * das Behalten-Profil bereits Teilnehmer ist. Verhindert eine Primary-Key-Kollision
     * auf (gameId, profileId) beim anschließenden Umschreiben.
     */
    @Query(
        "DELETE FROM game_participants WHERE profileId = :discardId AND gameId IN (" +
            "SELECT gameId FROM game_participants WHERE profileId = :keepId)"
    )
    suspend fun deleteConflictingParticipants(keepId: String, discardId: String)

    @Query("UPDATE game_participants SET profileId = :keepId WHERE profileId = :discardId")
    suspend fun reassignParticipants(keepId: String, discardId: String)

    @Query("UPDATE round_results SET profileId = :keepId WHERE profileId = :discardId")
    suspend fun reassignRoundResults(keepId: String, discardId: String)

    @Query("DELETE FROM profile")
    suspend fun deleteAll()
}
