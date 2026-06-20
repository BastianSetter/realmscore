package de.morzo.realmscore.domain.repository

import de.morzo.realmscore.domain.backup.GameSnapshot
import de.morzo.realmscore.domain.backup.ImportResult

/**
 * Full-backup export/import of all app data as versioned JSON (Phase 23).
 */
interface BackupRepository {

    /**
     * Aggregates all data into a [de.morzo.realmscore.domain.backup.BackupData] and serializes it to
     * a pretty-printed JSON string. [appVersion] is recorded in the file for diagnostics only.
     */
    suspend fun exportToJson(appVersion: String): String

    /**
     * Parses, validates and merges [json] into the database in a single transaction.
     *
     * Conflict strategy: merge by UUID. Profiles and games whose id already exists locally are
     * skipped (the local copy wins); new ids are inserted together with their whole subtree. The
     * backup's `isLocalOwner` flag is always ignored — the current device keeps its own owner.
     *
     * @throws de.morzo.realmscore.domain.backup.BackupSchemaTooNewException if the backup is newer.
     * @throws de.morzo.realmscore.domain.backup.BackupInvalidException if the file cannot be read.
     */
    suspend fun importFromJson(json: String): ImportResult

    /**
     * Phase 28 Stage B: export the subtree of a single game plus the [GameSnapshot.profiles] its
     * participants reference, so the live P2P layer can distribute one game (initial mirror, reveal,
     * reconnect delta, end-of-game) through the same DTOs as a backup. Returns null if [gameId] is unknown.
     */
    suspend fun exportGame(gameId: String): GameSnapshot?

    /**
     * Phase 28 Stage B: merge a single [GameSnapshot] into the local mirror by UUID. Unlike
     * [importFromJson] (skip-if-exists), round/result/discard conflicts are resolved by **last-writer-
     * wins on `updatedAt`**, so a newer copy overwrites the existing subtree. Idempotent: re-merging
     * the same snapshot is a no-op. Profiles are still skip-if-exists (a player's name/colour is stable
     * and the local owner must never be clobbered).
     */
    suspend fun mergeGame(snapshot: GameSnapshot): ImportResult

    /**
     * Phase 28 Stage B: after a game finishes, reattribute this device's own seat from the canonical
     * (host-assigned) [canonicalProfileId] to the local owner profile so the game shows under the owner
     * in stats. Reassigns participants + round results, then archives the now-empty canonical profile.
     * A no-op when there is no local owner or it already is the canonical id (e.g. on the host).
     */
    suspend fun reconcileSelfSeat(canonicalProfileId: String)
}
