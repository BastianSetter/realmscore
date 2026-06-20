package de.morzo.realmscore.domain.backup

import kotlinx.serialization.Serializable

/**
 * Current backup format version. Bump this whenever the on-disk JSON structure changes in a way
 * that requires migration. The importer rejects backups whose [BackupData.schemaVersion] is higher
 * than this (a backup from a newer app version), but accepts equal or lower versions so old backups
 * stay importable after an app update.
 */
const val CURRENT_BACKUP_SCHEMA_VERSION = 1

/**
 * Versioned, self-contained snapshot of all app data (Phase 23). The DTOs intentionally carry
 * *every* entity field (including sync metadata like `originDeviceId` and `createdAt`/`updatedAt`)
 * so a restore is lossless and a backup can round-trip across devices. The nesting mirrors the
 * ownership graph: game → rounds → results → hand cards, plus per-round discard cards.
 */
@Serializable
data class BackupData(
    val schemaVersion: Int,
    val appVersion: String,
    val exportedAt: String,
    val deviceId: String,
    val profiles: List<BackupProfile>,
    val games: List<BackupGame>,
)

@Serializable
data class BackupProfile(
    val id: String,
    val name: String,
    val colorArgb: Int,
    val isLocalOwner: Boolean,
    val isArchived: Boolean,
    val archivedAt: Long? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val originDeviceId: String,
)

@Serializable
data class BackupGame(
    val id: String,
    val displayName: String? = null,
    val mode: String,
    val targetRounds: Int? = null,
    val targetPoints: Int? = null,
    val startedAt: Long,
    val closedAt: Long? = null,
    val closedReason: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val originDeviceId: String,
    val participants: List<BackupParticipant>,
    val rounds: List<BackupRound>,
)

@Serializable
data class BackupParticipant(
    val profileId: String,
    val seatOrder: Int,
    val lastScanOrder: Int? = null,
)

@Serializable
data class BackupRound(
    val id: String,
    val roundNumber: Int,
    val startedAt: Long,
    val completedAt: Long? = null,
    val discardScanned: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val originDeviceId: String,
    val discardCards: List<BackupDiscardCard>,
    val results: List<BackupResult>,
)

@Serializable
data class BackupDiscardCard(
    val id: String,
    val cardKey: String,
    val position: Int,
    val createdAt: Long,
    val updatedAt: Long,
    val originDeviceId: String,
)

@Serializable
data class BackupResult(
    val id: String,
    val profileId: String,
    val totalScore: Int,
    val createdAt: Long,
    val updatedAt: Long,
    val originDeviceId: String,
    val handCards: List<BackupHandCard>,
)

@Serializable
data class BackupHandCard(
    val id: String,
    val cardKey: String,
    val position: Int,
    val jokerTargetCardKey: String? = null,
    val jokerTargetSuit: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
)

/**
 * Self-contained snapshot of a *single* game plus the [BackupProfile]s its participants reference
 * (Phase 28, Stage B). Mirrors the payload of [de.morzo.realmscore.domain.p2p.model.SyncMessage.FullGameState]
 * so the live P2P distribution and the JSON backup share one export/merge path.
 */
data class GameSnapshot(
    val game: BackupGame,
    val profiles: List<BackupProfile>,
)

/**
 * Outcome of an import. The conflict strategy is merge-by-UUID at *round* granularity (Phase 24 M2):
 * profiles and games merge by id, but a game that already exists can still receive *new rounds* from
 * the backup. Hence a game is either freshly created or updated-with-new-rounds, and rounds are
 * counted separately.
 */
data class ImportResult(
    val profilesAdded: Int,
    val profilesSkipped: Int,
    /** Completely new games inserted (id was unknown locally). */
    val gamesCreated: Int,
    /** Pre-existing games into which at least one new round was merged. */
    val gamesUpdated: Int,
    /** New rounds inserted across all games (in both created and updated games). */
    val roundsAdded: Int,
    /** Rounds skipped because their id already existed locally. */
    val roundsSkipped: Int,
    /** Rounds skipped defensively because a referenced profile was absent (malformed backup). */
    val roundsSkippedMissingProfile: Int = 0,
    /**
     * Existing rounds whose subtree was overwritten by a newer copy (LWW on `updatedAt`). Only the
     * Stage-B [BackupRepository.mergeGame] path produces these; the skip-if-exists backup import never does.
     */
    val roundsUpdated: Int = 0,
)

/** The backup was written by a newer app version than this one understands. */
class BackupSchemaTooNewException(val foundVersion: Int) : Exception(
    "Backup schemaVersion $foundVersion is newer than supported $CURRENT_BACKUP_SCHEMA_VERSION",
)

/** The file could not be parsed as a valid backup (malformed JSON or wrong structure). */
class BackupInvalidException(cause: Throwable? = null) : Exception("Backup file is invalid", cause)
