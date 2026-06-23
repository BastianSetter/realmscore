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
            "AND mergeTargetId IS NULL " +
            "ORDER BY name COLLATE NOCASE"
    )
    suspend fun searchByNamePrefix(prefix: String): List<ProfileEntity>

    /**
     * Active (non-archived) profiles whose name starts with [prefix] (empty prefix = all active).
     * Phase 18.1, Punkt 3: feeds the relevance-ranked autocomplete in NewGame.
     */
    @Query(
        "SELECT * FROM profile " +
            "WHERE isArchived = 0 " +
            "AND mergeTargetId IS NULL " +
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

    @Query("SELECT * FROM profile")
    suspend fun getAll(): List<ProfileEntity>

    @Query("SELECT COUNT(*) FROM profile")
    fun observeProfileCount(): Flow<Int>

    // --- Phase 24 M1: cheap signals for the stats-snapshot cache fingerprint (catches renames) ---

    @Query("SELECT COUNT(*) FROM profile")
    suspend fun getProfileCount(): Int

    @Query("SELECT MAX(updatedAt) FROM profile")
    suspend fun getMaxUpdatedAt(): Long?

    @Query("UPDATE profile SET name = :name, updatedAt = :ts WHERE id = :id")
    suspend fun updateName(id: String, name: String, ts: Long)

    // --- Phase 17: Profilverwaltung ---

    // Profil-Rework: vier disjunkte Buckets. Aktiv = nicht archiviert, nicht gemergt, nicht Owner.
    @Query(
        "SELECT * FROM profile " +
            "WHERE isArchived = 0 AND mergeTargetId IS NULL AND isLocalOwner = 0 " +
            "ORDER BY name COLLATE NOCASE"
    )
    fun observeActive(): Flow<List<ProfileEntity>>

    @Query("SELECT * FROM profile WHERE isArchived = 1 AND mergeTargetId IS NULL ORDER BY name COLLATE NOCASE")
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

    @Query("DELETE FROM profile")
    suspend fun deleteAll()

    // --- Phase 2 (Profil-Rework): non-destruktiver Zeiger-Merge ---

    @Query("UPDATE profile SET mergeTargetId = :target, updatedAt = :ts WHERE id = :id")
    suspend fun setMergeTarget(id: String, target: String, ts: Long)

    @Query("UPDATE profile SET mergeTargetId = NULL, updatedAt = :ts WHERE id = :id")
    suspend fun clearMergeTarget(id: String, ts: Long)

    /** Zieht vorhandene Zeiger aufs neue Kettenende nach (verhindert Merge-Ketten). */
    @Query("UPDATE profile SET mergeTargetId = :newTarget, updatedAt = :ts WHERE mergeTargetId = :oldId")
    suspend fun repointPointers(oldId: String, newTarget: String, ts: Long)

    @Query("SELECT * FROM profile WHERE mergeTargetId IS NOT NULL ORDER BY name COLLATE NOCASE")
    fun observeMerged(): Flow<List<ProfileEntity>>
}
