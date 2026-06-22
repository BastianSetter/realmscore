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
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Profil-Rework: der Merge ist jetzt ein **non-destruktiver Zeiger** (`mergeTargetId`). Diese Tests
 * decken Zielsetzung, Kettenkollaps, Zyklenschutz, Unmerge und die Unversehrtheit der Historie ab.
 */
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

    private fun profile(id: String, name: String, mergeTargetId: String? = null) = ProfileEntity(
        id = id,
        name = name,
        colorArgb = 0xFF6750A4.toInt(),
        isLocalOwner = false,
        isArchived = false,
        archivedAt = null,
        createdAt = 0,
        updatedAt = 0,
        originDeviceId = "dev",
        deviceId = "dev",
        profileId = id,
        mergeTargetId = mergeTargetId,
    )

    @Test
    fun setMergeTarget_points_source_at_target_without_touching_target() = runBlocking {
        val dao = db.profileDao()
        dao.insert(profile("A", "Anna"))
        dao.insert(profile("B", "Ana"))

        repo.setMergeTarget("B", "A")

        assertEquals("A", dao.getById("B")!!.mergeTargetId)
        assertNull(dao.getById("A")!!.mergeTargetId)
    }

    @Test
    fun setMergeTarget_does_not_rewrite_history() = runBlocking {
        val dao = db.profileDao()
        dao.insert(profile("A", "Anna"))
        dao.insert(profile("B", "Ana"))
        db.gameDao().insert(game("g1"))
        db.gameDao().insertParticipants(listOf(GameParticipantEntity("g1", "B", 0, null)))
        db.roundDao().insert(round("r1", "g1"))
        db.roundResultDao().insert(result("rr1", "r1", "B"))

        repo.setMergeTarget("B", "A")

        // Historie bleibt unangetastet — die Auflösung passiert zur Laufzeit, nicht beim Schreiben.
        assertEquals("B", db.roundResultDao().getResultsForRounds(listOf("r1")).single().profileId)
        assertEquals(1, dao.countGamesForProfile("B"))
        assertEquals(0, dao.countGamesForProfile("A"))
    }

    @Test
    fun setMergeTarget_follows_chain_to_end() = runBlocking {
        val dao = db.profileDao()
        dao.insert(profile("A", "Anna"))
        dao.insert(profile("B", "B", mergeTargetId = "A")) // B ist bereits → A gemergt
        dao.insert(profile("C", "C"))

        repo.setMergeTarget("C", "B") // Ziel B zeigt auf A → C muss direkt auf A zeigen (keine Kette)

        assertEquals("A", dao.getById("C")!!.mergeTargetId)
    }

    @Test
    fun setMergeTarget_repoints_existing_pointers() = runBlocking {
        val dao = db.profileDao()
        dao.insert(profile("A", "Anna"))
        dao.insert(profile("B", "B"))
        dao.insert(profile("C", "C"))

        repo.setMergeTarget("A", "B") // A → B
        repo.setMergeTarget("B", "C") // B → C: A wird aufs neue Ende C nachgezogen

        assertEquals("C", dao.getById("A")!!.mergeTargetId)
        assertEquals("C", dao.getById("B")!!.mergeTargetId)
    }

    @Test
    fun setMergeTarget_rejects_cycle() = runBlocking {
        val dao = db.profileDao()
        dao.insert(profile("A", "Anna"))
        dao.insert(profile("B", "B"))
        repo.setMergeTarget("A", "B")

        try {
            repo.setMergeTarget("B", "A")
            fail("expected a cycle to be rejected")
        } catch (e: IllegalArgumentException) {
            // erwartet
        }
        Unit
    }

    @Test
    fun clearMergeTarget_unmerges() = runBlocking {
        val dao = db.profileDao()
        dao.insert(profile("A", "Anna"))
        dao.insert(profile("B", "B"))
        repo.setMergeTarget("B", "A")

        repo.clearMergeTarget("B")

        assertNull(dao.getById("B")!!.mergeTargetId)
    }

    private fun game(id: String) = GameEntity(
        id = id,
        displayName = null,
        mode = "FIXED_ROUNDS",
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
}
