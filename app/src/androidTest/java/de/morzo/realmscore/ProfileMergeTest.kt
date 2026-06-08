package de.morzo.realmscore

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import de.morzo.realmscore.data.datastore.DeviceUuidProvider
import de.morzo.realmscore.data.db.AppDatabase
import de.morzo.realmscore.data.db.entity.GameEntity
import de.morzo.realmscore.data.db.entity.GameParticipantEntity
import de.morzo.realmscore.data.db.entity.ProfileEntity
import de.morzo.realmscore.data.db.entity.RoundEntity
import de.morzo.realmscore.data.db.entity.RoundResultEntity
import de.morzo.realmscore.data.repository.ProfileRepositoryImpl
import de.morzo.realmscore.domain.util.Clock
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProfileMergeTest {

    private lateinit var db: AppDatabase
    private lateinit var repo: ProfileRepositoryImpl

    private val fixedClock = object : Clock {
        override fun nowEpochMillis(): Long = 1_000L
    }

    @Before
    fun setup() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repo = ProfileRepositoryImpl(
            database = db,
            dao = db.profileDao(),
            deviceUuidProvider = DeviceUuidProvider(ctx),
            clock = fixedClock,
        )
    }

    @After
    fun teardown() {
        db.close()
    }

    private fun profile(id: String, name: String, owner: Boolean = false) = ProfileEntity(
        id = id,
        name = name,
        colorArgb = 0xFF6750A4.toInt(),
        isLocalOwner = owner,
        isArchived = false,
        archivedAt = null,
        createdAt = 0,
        updatedAt = 0,
        originDeviceId = "dev",
    )

    private fun game(id: String) = GameEntity(
        id = id,
        displayName = null,
        mode = "ROUND_LIMIT",
        targetRounds = 3,
        targetPoints = null,
        startedAt = 0,
        closedAt = null,
        closedReason = null,
        createdAt = 0,
        updatedAt = 0,
        originDeviceId = "dev",
    )

    private fun round(id: String, gameId: String) = RoundEntity(
        id = id,
        gameId = gameId,
        roundNumber = 1,
        startedAt = 0,
        completedAt = null,
        discardScanned = false,
        createdAt = 0,
        updatedAt = 0,
        originDeviceId = "dev",
    )

    private fun result(id: String, roundId: String, profileId: String) = RoundResultEntity(
        id = id,
        roundId = roundId,
        profileId = profileId,
        totalScore = 10,
        createdAt = 0,
        updatedAt = 0,
        originDeviceId = "dev",
    )

    @Test
    fun mergeProfiles_reassigns_data_and_archives_discarded() = runBlocking {
        val profileDao = db.profileDao()
        val gameDao = db.gameDao()
        val roundDao = db.roundDao()
        val roundResultDao = db.roundResultDao()

        // Profile A (keep) und B (discard)
        profileDao.insert(profile("A", "Anna", owner = true))
        profileDao.insert(profile("B", "Ana"))

        // game1: A und B spielen gemeinsam (Konflikt-Fall für game_participants)
        gameDao.insert(game("g1"))
        gameDao.insertParticipants(
            listOf(
                GameParticipantEntity("g1", "A", 0, null),
                GameParticipantEntity("g1", "B", 1, null),
            ),
        )
        roundDao.insert(round("r1", "g1"))
        roundResultDao.insert(result("rr1a", "r1", "A"))
        roundResultDao.insert(result("rr1b", "r1", "B"))

        // game2: nur B spielt → wird auf A umgeschrieben
        gameDao.insert(game("g2"))
        gameDao.insertParticipants(listOf(GameParticipantEntity("g2", "B", 0, null)))
        roundDao.insert(round("r2", "g2"))
        roundResultDao.insert(result("rr2b", "r2", "B"))

        // Vorschau: Vereinigung der Spiele = g1 + g2 = 2
        assertEquals(2, repo.countCombinedGames("A", "B"))

        repo.mergeProfiles(keepId = "A", discardId = "B")

        // B ist archiviert
        val b = profileDao.getById("B")
        assertNotNull(b)
        assertTrue(b!!.isArchived)
        assertEquals(1_000L, b.archivedAt)

        // B hat keine Teilnahmen mehr, A ist jetzt in beiden Spielen
        assertEquals(0, repo.countGamesForProfile("B"))
        assertEquals(2, repo.countGamesForProfile("A"))

        // Keine round_results mehr auf B
        val results = roundResultDao.getResultsForRounds(listOf("r1", "r2"))
        assertTrue(results.none { it.profileId == "B" })
        assertEquals(3, results.size)
    }
}
