package de.morzo.realmscore.domain.repository

import de.morzo.realmscore.domain.model.Profile
import kotlinx.coroutines.flow.Flow

interface ProfileRepository {
    suspend fun getLocalOwner(): Profile?
    fun observeLocalOwner(): Flow<Profile?>
    suspend fun createOwner(name: String): Profile
    suspend fun updateOwnerName(newName: String): Profile
    suspend fun searchByNamePrefix(prefix: String): List<Profile>

    /**
     * Phase 18.1, Punkt 3: relevance-ranked suggestions for the NewGame player picker.
     * Returns active (non-archived) profiles whose name starts with [prefix] (empty = all),
     * excluding [excludeProfileIds] (already chosen players + the owner), sorted by a relevance
     * score combining how often and how recently they played with [ownerId]. Falls back to
     * alphabetical order when there is no shared history.
     */
    suspend fun suggestProfiles(
        prefix: String,
        excludeProfileIds: Set<String>,
        ownerId: String,
    ): List<Profile>

    suspend fun createProfile(name: String): Profile

    /**
     * Phase 4 (Profil-Rework): stellt sicher, dass ein per P2P empfangenes **fremdes** Profil mit
     * seiner globalen Identität ([id] = "$deviceId:$profileId") lokal existiert. Legt es bei Bedarf
     * unverändert an (kein Remap auf ein lokales Profil mehr). Gibt das vorhandene/erzeugte zurück.
     */
    suspend fun ensureRemoteProfile(
        id: String,
        name: String,
        colorArgb: Int,
        originDeviceId: String,
    ): Profile
    suspend fun getById(id: String): Profile?
    fun observeAll(): Flow<List<Profile>>

    // --- Phase 17: Profilverwaltung ---
    fun observeActiveProfiles(): Flow<List<Profile>>
    fun observeArchivedProfiles(): Flow<List<Profile>>
    suspend fun countGamesForProfile(id: String): Int
    suspend fun updateName(id: String, newName: String)
    suspend fun updateColor(id: String, colorArgb: Int)
    suspend fun archiveProfile(id: String)
    suspend fun unarchiveProfile(id: String)

    // --- Phase 2 (Profil-Rework): non-destruktiver Zeiger-Merge ---
    fun observeMergedProfiles(): Flow<List<Profile>>

    /** Verschmilzt [id] non-destruktiv in [targetId] (Kettenende, ohne Ketten/Zyklen). */
    suspend fun setMergeTarget(id: String, targetId: String)

    /** Hebt einen Merge wieder auf (Unmerge). */
    suspend fun clearMergeTarget(id: String)
}
