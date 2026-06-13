package de.morzo.realmscore

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import de.morzo.realmscore.data.datastore.DeviceUuidProvider
import de.morzo.realmscore.data.db.AppDatabase
import de.morzo.realmscore.data.repository.BackupRepositoryImpl
import de.morzo.realmscore.domain.backup.BackupData
import de.morzo.realmscore.domain.backup.BackupGame
import de.morzo.realmscore.domain.backup.BackupHandCard
import de.morzo.realmscore.domain.backup.BackupParticipant
import de.morzo.realmscore.domain.backup.BackupProfile
import de.morzo.realmscore.domain.backup.BackupResult
import de.morzo.realmscore.domain.backup.BackupRound
import de.morzo.realmscore.domain.backup.CURRENT_BACKUP_SCHEMA_VERSION
import de.morzo.realmscore.domain.util.Clock
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Phase 24 M2 — verifies UUID-based merge at *round* granularity: idempotent re-import, merging new
 * rounds into an existing game, and defensive skip of rounds referencing a missing profile.
 */
@RunWith(AndroidJUnit4::class)
class BackupImportMergeTest {

    private lateinit var db: AppDatabase
    private lateinit var repo: BackupRepositoryImpl

    private val fixedClock = object : Clock {
        override fun nowEpochMillis(): Long = 1_000L
    }

    @Before
    fun setup() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repo = BackupRepositoryImpl(db, DeviceUuidProvider(ctx), fixedClock)
    }

    @After
    fun teardown() {
        db.close()
    }

    private val json = Json { ignoreUnknownKeys = true }

    private fun profile(id: String) = BackupProfile(
        id = id,
        name = "P-$id",
        colorArgb = 0xFF6750A4.toInt(),
        isLocalOwner = false,
        isArchived = false,
        archivedAt = null,
        createdAt = 0,
        updatedAt = 0,
        originDeviceId = "dev",
    )

    private fun round(id: String, number: Int, profileId: String) = BackupRound(
        id = id,
        roundNumber = number,
        startedAt = 0,
        completedAt = 0,
        discardScanned = false,
        createdAt = 0,
        updatedAt = 0,
        originDeviceId = "dev",
        discardCards = emptyList(),
        results = listOf(
            BackupResult(
                id = "rr-$id",
                profileId = profileId,
                totalScore = 42,
                createdAt = 0,
                updatedAt = 0,
                originDeviceId = "dev",
                handCards = listOf(
                    BackupHandCard("hc-$id", "dragon", 0, null, null, 0, 0),
                ),
            ),
        ),
    )

    private fun backup(profiles: List<BackupProfile>, rounds: List<BackupRound>) = BackupData(
        schemaVersion = CURRENT_BACKUP_SCHEMA_VERSION,
        appVersion = "test",
        exportedAt = "2026-01-01T00:00:00Z",
        deviceId = "dev",
        profiles = profiles,
        games = listOf(
            BackupGame(
                id = "g1",
                displayName = null,
                mode = "FIXED_ROUNDS",
                targetRounds = 5,
                targetPoints = null,
                startedAt = 0,
                closedAt = null,
                closedReason = null,
                createdAt = 0,
                updatedAt = 0,
                originDeviceId = "dev",
                participants = listOf(BackupParticipant("p1", 0, null)),
                rounds = rounds,
            ),
        ),
    )

    private suspend fun importOf(data: BackupData) = repo.importFromJson(json.encodeToString(data))

    @Test
    fun reImport_isIdempotent() = runBlocking {
        val data = backup(listOf(profile("p1")), listOf(round("r1", 1, "p1"), round("r2", 2, "p1")))

        val first = importOf(data)
        assertEquals(1, first.gamesCreated)
        assertEquals(2, first.roundsAdded)

        val second = importOf(data)
        assertEquals(0, second.gamesCreated)
        assertEquals(0, second.gamesUpdated)
        assertEquals(0, second.roundsAdded)
        assertEquals(2, second.roundsSkipped)

        // Genau 2 Runden, keine Duplikate.
        assertEquals(2, db.roundDao().getAll().size)
    }

    @Test
    fun mergesNewRoundsIntoExistingGame() = runBlocking {
        // Gerät A: Spiel G mit Runden 1–3.
        importOf(backup(listOf(profile("p1")), (1..3).map { round("r$it", it, "p1") }))

        // Gerät B exportiert dasselbe Spiel mit Runden 1–5.
        val merged = importOf(backup(listOf(profile("p1")), (1..5).map { round("r$it", it, "p1") }))

        assertEquals(0, merged.gamesCreated)
        assertEquals(1, merged.gamesUpdated)
        assertEquals(2, merged.roundsAdded)
        assertEquals(3, merged.roundsSkipped)
        assertEquals(5, db.roundDao().getAll().size)
    }

    @Test
    fun roundWithMissingProfile_isSkipped_withoutRollback() = runBlocking {
        // r1 referenziert das mitgelieferte Profil p1; r2 referenziert ein nicht vorhandenes p_ghost.
        val data = backup(
            profiles = listOf(profile("p1")),
            rounds = listOf(round("r1", 1, "p1"), round("r2", 2, "p_ghost")),
        )

        val result = importOf(data)

        assertEquals(1, result.gamesCreated)
        assertEquals(1, result.roundsAdded)
        assertEquals(1, result.roundsSkippedMissingProfile)
        // Der Rest lief durch (kein globales Rollback): Spiel + die gültige Runde sind da.
        assertEquals(1, db.gameDao().getAllGames().size)
        assertEquals(1, db.roundDao().getAll().size)
    }
}
