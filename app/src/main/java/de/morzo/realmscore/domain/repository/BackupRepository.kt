package de.morzo.realmscore.domain.repository

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
}
