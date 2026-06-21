package de.morzo.realmscore.data.repository

import androidx.room.withTransaction
import de.morzo.realmscore.data.datastore.DeviceUuidProvider
import de.morzo.realmscore.data.db.AppDatabase
import de.morzo.realmscore.data.db.entity.DiscardCardEntity
import de.morzo.realmscore.data.db.entity.GameEntity
import de.morzo.realmscore.data.db.entity.GameParticipantEntity
import de.morzo.realmscore.data.db.entity.HandCardEntity
import de.morzo.realmscore.data.db.entity.ProfileEntity
import de.morzo.realmscore.data.db.entity.RoundEntity
import de.morzo.realmscore.data.db.entity.RoundResultEntity
import de.morzo.realmscore.domain.backup.BackupData
import de.morzo.realmscore.domain.backup.BackupDiscardCard
import de.morzo.realmscore.domain.backup.BackupGame
import de.morzo.realmscore.domain.backup.BackupHandCard
import de.morzo.realmscore.domain.backup.BackupInvalidException
import de.morzo.realmscore.domain.backup.BackupParticipant
import de.morzo.realmscore.domain.backup.BackupProfile
import de.morzo.realmscore.domain.backup.BackupResult
import de.morzo.realmscore.domain.backup.BackupRound
import de.morzo.realmscore.domain.backup.BackupSchemaTooNewException
import de.morzo.realmscore.domain.backup.CURRENT_BACKUP_SCHEMA_VERSION
import de.morzo.realmscore.domain.backup.GameSnapshot
import de.morzo.realmscore.domain.backup.ImportResult
import de.morzo.realmscore.domain.repository.BackupRepository
import de.morzo.realmscore.domain.util.Clock
import kotlinx.serialization.json.Json
import java.time.Instant

class BackupRepositoryImpl(
    private val db: AppDatabase,
    private val deviceUuidProvider: DeviceUuidProvider,
    private val clock: Clock,
) : BackupRepository {

    private val json = Json {
        prettyPrint = true
        // Tolerate fields added by future minor format tweaks within the same schema version.
        ignoreUnknownKeys = true
    }

    override suspend fun exportToJson(appVersion: String): String {
        val data = buildBackup(appVersion)
        return json.encodeToString(data)
    }

    private suspend fun buildBackup(appVersion: String): BackupData {
        val profiles = db.profileDao().getAll()
        val games = db.gameDao().getAllGames()
        val gameIds = games.map { it.id }

        val participantsByGame = db.gameDao().getParticipantsForGames(gameIds).groupBy { it.gameId }
        val rounds = db.roundDao().getAll()
        val roundsByGame = rounds.groupBy { it.gameId }
        val roundIds = rounds.map { it.id }

        val discardsByRound = db.discardCardDao().getAll().groupBy { it.roundId }
        val results = db.roundResultDao().getResultsForRounds(roundIds)
        val resultsByRound = results.groupBy { it.roundId }
        val resultIds = results.map { it.id }
        val handCardsByResult = db.handCardDao().getForRoundResults(resultIds).groupBy { it.roundResultId }

        return BackupData(
            schemaVersion = CURRENT_BACKUP_SCHEMA_VERSION,
            appVersion = appVersion,
            exportedAt = Instant.ofEpochMilli(clock.nowEpochMillis()).toString(),
            deviceId = deviceUuidProvider.get(),
            profiles = profiles.map { it.toBackup() },
            games = games.map { game ->
                game.toBackup(
                    participants = participantsByGame[game.id].orEmpty(),
                    rounds = (roundsByGame[game.id].orEmpty()).map { round ->
                        round.toBackup(
                            discards = discardsByRound[round.id].orEmpty(),
                            results = (resultsByRound[round.id].orEmpty()).map { result ->
                                result.toBackup(handCardsByResult[result.id].orEmpty())
                            },
                        )
                    },
                )
            },
        )
    }

    override suspend fun importFromJson(json: String): ImportResult {
        val data = try {
            this.json.decodeFromString<BackupData>(json)
        } catch (e: Exception) {
            throw BackupInvalidException(e)
        }
        if (data.schemaVersion > CURRENT_BACKUP_SCHEMA_VERSION) {
            throw BackupSchemaTooNewException(data.schemaVersion)
        }

        // One enclosing transaction = all-or-nothing on the device. The profile pre-check below keeps
        // a malformed result row from throwing an FK exception that would roll the whole import back.
        return db.withTransaction {
            // --- Step 1: profiles (by id, skip-if-exists, owner flag dropped) ---
            val existingProfileIds = db.profileDao().getAll().mapTo(HashSet()) { it.id }
            var profilesAdded = 0
            var profilesSkipped = 0
            for (profile in data.profiles) {
                if (profile.id in existingProfileIds) {
                    profilesSkipped++
                } else {
                    // isLocalOwner from the backup is intentionally dropped: the current device
                    // keeps its own owner. Imported profiles are always non-owner.
                    db.profileDao().insert(profile.toEntity(isLocalOwner = false))
                    existingProfileIds += profile.id
                    profilesAdded++
                }
            }

            // --- Step 2: existence sets, loaded once to avoid PK/FK collisions on insert ---
            val existingGameIds = db.gameDao().getAllGames().mapTo(HashSet()) { it.id }
            val existingRoundIds = db.roundDao().getAll().mapTo(HashSet()) { it.id }

            var gamesCreated = 0
            var gamesUpdated = 0
            var roundsAdded = 0
            var roundsSkipped = 0
            var roundsSkippedMissingProfile = 0

            // --- Step 3 + 4: per game, merge rounds by id ---
            for (game in data.games) {
                val gameIsNew = game.id !in existingGameIds
                // Only reference participants whose profile actually exists (defensive against a
                // malformed backup; for a well-formed one every profile was inserted in step 1).
                val participants = game.participants
                    .filter { it.profileId in existingProfileIds }
                    .map { it.toEntity(game.id) }
                if (gameIsNew) {
                    db.gameDao().insert(game.toEntity())
                    db.gameDao().insertParticipants(participants)
                    existingGameIds += game.id
                } else {
                    // Existing game row wins (local displayName/closedReason/seatOrder/lastScanOrder
                    // are not touched). Only *add* participants that are missing.
                    db.gameDao().insertParticipantsIgnore(participants)
                }

                var roundsAddedForGame = 0
                for (round in game.rounds) {
                    if (round.id in existingRoundIds) {
                        // NOTE: edits to an already-imported round are intentionally NOT propagated
                        // (skip-if-exists). Last-writer-wins arrives with Spec 29 (P2P-Sync).
                        roundsSkipped++
                        continue
                    }
                    if (round.results.any { it.profileId !in existingProfileIds }) {
                        roundsSkippedMissingProfile++
                        continue
                    }
                    insertRoundSubtree(round, game.id)
                    existingRoundIds += round.id
                    roundsAdded++
                    roundsAddedForGame++
                }

                if (gameIsNew) {
                    gamesCreated++
                } else if (roundsAddedForGame > 0) {
                    gamesUpdated++
                }
            }

            ImportResult(
                profilesAdded = profilesAdded,
                profilesSkipped = profilesSkipped,
                gamesCreated = gamesCreated,
                gamesUpdated = gamesUpdated,
                roundsAdded = roundsAdded,
                roundsSkipped = roundsSkipped,
                roundsSkippedMissingProfile = roundsSkippedMissingProfile,
            )
        }
    }

    /** Inserts a round and its whole subtree (discards → results → hand cards), parent-before-child. */
    private suspend fun insertRoundSubtree(round: BackupRound, gameId: String) {
        db.roundDao().insert(round.toEntity(gameId))
        if (round.discardCards.isNotEmpty()) {
            db.discardCardDao().insertAll(round.discardCards.map { it.toEntity(round.id) })
        }
        for (result in round.results) {
            db.roundResultDao().insert(result.toEntity(round.id))
            if (result.handCards.isNotEmpty()) {
                db.handCardDao().insertAll(result.handCards.map { it.toEntity(result.id) })
            }
        }
    }

    // --- Phase 28 Stage B: single-game export, LWW merge, self-seat reconcile ---

    override suspend fun exportGame(gameId: String): GameSnapshot? {
        val gameEntity = db.gameDao().getById(gameId) ?: return null
        val participants = db.gameDao().getParticipants(gameId)
        val rounds = db.roundDao().getAll().filter { it.gameId == gameId }.sortedBy { it.roundNumber }
        val roundIds = rounds.map { it.id }.toSet()

        val discardsByRound = db.discardCardDao().getAll()
            .filter { it.roundId in roundIds }
            .groupBy { it.roundId }
        val results = db.roundResultDao().getResultsForRounds(roundIds.toList())
        val resultsByRound = results.groupBy { it.roundId }
        val resultIds = results.map { it.id }
        val handCardsByResult = db.handCardDao().getForRoundResults(resultIds).groupBy { it.roundResultId }

        val game = gameEntity.toBackup(
            participants = participants,
            rounds = rounds.map { round ->
                round.toBackup(
                    discards = discardsByRound[round.id].orEmpty(),
                    results = (resultsByRound[round.id].orEmpty()).map { result ->
                        result.toBackup(handCardsByResult[result.id].orEmpty())
                    },
                )
            },
        )
        val profileIds = participants.mapTo(HashSet()) { it.profileId }
        val profiles = db.profileDao().getAll().filter { it.id in profileIds }.map { it.toBackup() }
        return GameSnapshot(game = game, profiles = profiles)
    }

    override suspend fun mergeGame(snapshot: GameSnapshot): ImportResult {
        val game = snapshot.game
        return db.withTransaction {
            // --- Profiles: skip-if-exists (a player's name/colour is stable; never clobber the owner) ---
            val existingProfileIds = db.profileDao().getAll().mapTo(HashSet()) { it.id }
            var profilesAdded = 0
            var profilesSkipped = 0
            for (profile in snapshot.profiles) {
                if (profile.id in existingProfileIds) {
                    profilesSkipped++
                } else {
                    db.profileDao().insert(profile.toEntity(isLocalOwner = false))
                    existingProfileIds += profile.id
                    profilesAdded++
                }
            }

            // --- Game header: insert-if-new, else LWW on updatedAt (carries closedAt at game end) ---
            val existingGame = db.gameDao().getById(game.id)
            val participants = game.participants
                .filter { it.profileId in existingProfileIds }
                .map { it.toEntity(game.id) }
            var gamesCreated = 0
            if (existingGame == null) {
                db.gameDao().insert(game.toEntity())
                db.gameDao().insertParticipants(participants)
                gamesCreated++
            } else {
                db.gameDao().insertParticipantsIgnore(participants)
                participants.forEach { p ->
                    p.lastScanOrder?.let { db.gameDao().updateScanOrder(game.id, p.profileId, it) }
                }
                if (game.updatedAt > existingGame.updatedAt) {
                    db.gameDao().updateGameMeta(
                        id = game.id,
                        displayName = game.displayName,
                        closedAt = game.closedAt,
                        closedReason = game.closedReason,
                        updatedAt = game.updatedAt,
                    )
                }
            }

            // --- Rounds: insert new subtrees; LWW-overwrite existing ones ---
            val existingRounds = db.roundDao().getAll()
                .filter { it.gameId == game.id }
                .associateBy { it.id }
            var roundsAdded = 0
            var roundsUpdated = 0
            var roundsSkipped = 0
            var roundsSkippedMissingProfile = 0
            for (round in game.rounds) {
                if (round.results.any { it.profileId !in existingProfileIds }) {
                    roundsSkippedMissingProfile++
                    continue
                }
                val existing = existingRounds[round.id]
                when {
                    existing == null -> {
                        insertRoundSubtree(round, game.id)
                        roundsAdded++
                    }
                    mergeExistingRound(round, existing) -> roundsUpdated++
                    else -> roundsSkipped++
                }
            }

            val gamesUpdated = if (existingGame != null && (roundsAdded > 0 || roundsUpdated > 0)) 1 else 0
            ImportResult(
                profilesAdded = profilesAdded,
                profilesSkipped = profilesSkipped,
                gamesCreated = gamesCreated,
                gamesUpdated = gamesUpdated,
                roundsAdded = roundsAdded,
                roundsSkipped = roundsSkipped,
                roundsSkippedMissingProfile = roundsSkippedMissingProfile,
                roundsUpdated = roundsUpdated,
            )
        }
    }

    /**
     * LWW-overwrites an existing round from [round]. The round meta (and its discard pile, which travels
     * as a unit with the round) is replaced only when the incoming round is newer; each result is
     * compared independently by id so a single freshly-captured hand updates without touching siblings.
     * Returns true if anything changed.
     */
    private suspend fun mergeExistingRound(round: BackupRound, existing: RoundEntity): Boolean {
        var changed = false
        if (round.updatedAt > existing.updatedAt) {
            db.roundDao().updateRoundMeta(
                id = round.id,
                roundNumber = round.roundNumber,
                startedAt = round.startedAt,
                completedAt = round.completedAt,
                discardScanned = round.discardScanned,
                updatedAt = round.updatedAt,
            )
            db.discardCardDao().deleteAllForRound(round.id)
            if (round.discardCards.isNotEmpty()) {
                db.discardCardDao().insertAll(round.discardCards.map { it.toEntity(round.id) })
            }
            changed = true
        }
        // Reconcile results by (roundId, profileId), NOT by result id: every device mints its own
        // round_result UUID in HandCardRepositoryImpl.saveHand, so the incoming snapshot's id won't match
        // the local incremental row. Keying by id would insert a *second* row for the same seat — the
        // mirror would then report a card both before and after a correction (and double scores in stats).
        // Overwrite the existing local row in place (keeping its id) so a seat always has exactly one row.
        val existingResults =
            db.roundResultDao().getResultsForRounds(listOf(round.id)).associateBy { it.profileId }
        for (result in round.results) {
            val existingResult = existingResults[result.profileId]
            when {
                existingResult == null -> {
                    db.roundResultDao().insert(result.toEntity(round.id))
                    if (result.handCards.isNotEmpty()) {
                        db.handCardDao().insertAll(result.handCards.map { it.toEntity(result.id) })
                    }
                    changed = true
                }
                result.updatedAt > existingResult.updatedAt -> {
                    db.roundResultDao().updateScore(existingResult.id, result.totalScore, result.updatedAt)
                    db.handCardDao().deleteAllForRoundResult(existingResult.id)
                    if (result.handCards.isNotEmpty()) {
                        db.handCardDao().insertAll(result.handCards.map { it.toEntity(existingResult.id) })
                    }
                    changed = true
                }
            }
        }
        return changed
    }

    override suspend fun reconcileSelfSeat(canonicalProfileId: String) {
        val ownerProfileId = db.profileDao().getLocalOwner()?.id ?: return
        if (canonicalProfileId == ownerProfileId) return // host's own seat is already its owner
        db.withTransaction {
            val now = clock.nowEpochMillis()
            val dao = db.profileDao()
            // Mirror ProfileRepositoryImpl.mergeProfiles: clear PK collisions, reassign, then retire the
            // now-empty canonical profile (archive rather than delete to stay FK-safe).
            dao.deleteConflictingParticipants(keepId = ownerProfileId, discardId = canonicalProfileId)
            dao.reassignParticipants(keepId = ownerProfileId, discardId = canonicalProfileId)
            dao.reassignRoundResults(keepId = ownerProfileId, discardId = canonicalProfileId)
            dao.archive(canonicalProfileId, now)
            dao.touch(ownerProfileId, now)
        }
    }
}

// --- Entity → Backup ---

private fun ProfileEntity.toBackup() = BackupProfile(
    id = id,
    name = name,
    colorArgb = colorArgb,
    isLocalOwner = isLocalOwner,
    isArchived = isArchived,
    archivedAt = archivedAt,
    createdAt = createdAt,
    updatedAt = updatedAt,
    originDeviceId = originDeviceId,
)

private fun GameEntity.toBackup(
    participants: List<GameParticipantEntity>,
    rounds: List<BackupRound>,
) = BackupGame(
    id = id,
    displayName = displayName,
    mode = mode,
    targetRounds = targetRounds,
    targetPoints = targetPoints,
    startedAt = startedAt,
    closedAt = closedAt,
    closedReason = closedReason,
    createdAt = createdAt,
    updatedAt = updatedAt,
    originDeviceId = originDeviceId,
    participants = participants.map {
        BackupParticipant(it.profileId, it.seatOrder, it.lastScanOrder)
    },
    rounds = rounds,
)

private fun RoundEntity.toBackup(
    discards: List<DiscardCardEntity>,
    results: List<BackupResult>,
) = BackupRound(
    id = id,
    roundNumber = roundNumber,
    startedAt = startedAt,
    completedAt = completedAt,
    discardScanned = discardScanned,
    createdAt = createdAt,
    updatedAt = updatedAt,
    originDeviceId = originDeviceId,
    discardCards = discards.map {
        BackupDiscardCard(it.id, it.cardKey, it.position, it.createdAt, it.updatedAt, it.originDeviceId)
    },
    results = results,
)

private fun RoundResultEntity.toBackup(handCards: List<HandCardEntity>) = BackupResult(
    id = id,
    profileId = profileId,
    totalScore = totalScore,
    createdAt = createdAt,
    updatedAt = updatedAt,
    originDeviceId = originDeviceId,
    handCards = handCards.map {
        BackupHandCard(
            id = it.id,
            cardKey = it.cardKey,
            position = it.position,
            jokerTargetCardKey = it.jokerTargetCardKey,
            jokerTargetSuit = it.jokerTargetSuit,
            createdAt = it.createdAt,
            updatedAt = it.updatedAt,
        )
    },
)

// --- Backup → Entity ---

private fun BackupProfile.toEntity(isLocalOwner: Boolean) = ProfileEntity(
    id = id,
    name = name,
    colorArgb = colorArgb,
    isLocalOwner = isLocalOwner,
    isArchived = isArchived,
    archivedAt = archivedAt,
    createdAt = createdAt,
    updatedAt = updatedAt,
    originDeviceId = originDeviceId,
)

private fun BackupGame.toEntity() = GameEntity(
    id = id,
    displayName = displayName,
    mode = mode,
    targetRounds = targetRounds,
    targetPoints = targetPoints,
    startedAt = startedAt,
    closedAt = closedAt,
    closedReason = closedReason,
    createdAt = createdAt,
    updatedAt = updatedAt,
    originDeviceId = originDeviceId,
)

private fun BackupParticipant.toEntity(gameId: String) = GameParticipantEntity(
    gameId = gameId,
    profileId = profileId,
    seatOrder = seatOrder,
    lastScanOrder = lastScanOrder,
)

private fun BackupRound.toEntity(gameId: String) = RoundEntity(
    id = id,
    gameId = gameId,
    roundNumber = roundNumber,
    startedAt = startedAt,
    completedAt = completedAt,
    discardScanned = discardScanned,
    createdAt = createdAt,
    updatedAt = updatedAt,
    originDeviceId = originDeviceId,
)

private fun BackupDiscardCard.toEntity(roundId: String) = DiscardCardEntity(
    id = id,
    roundId = roundId,
    cardKey = cardKey,
    position = position,
    createdAt = createdAt,
    updatedAt = updatedAt,
    originDeviceId = originDeviceId,
)

private fun BackupResult.toEntity(roundId: String) = RoundResultEntity(
    id = id,
    roundId = roundId,
    profileId = profileId,
    totalScore = totalScore,
    createdAt = createdAt,
    updatedAt = updatedAt,
    originDeviceId = originDeviceId,
)

private fun BackupHandCard.toEntity(roundResultId: String) = HandCardEntity(
    id = id,
    roundResultId = roundResultId,
    cardKey = cardKey,
    position = position,
    jokerTargetCardKey = jokerTargetCardKey,
    jokerTargetSuit = jokerTargetSuit,
    createdAt = createdAt,
    updatedAt = updatedAt,
)
