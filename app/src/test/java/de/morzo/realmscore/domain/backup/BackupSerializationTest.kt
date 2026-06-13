package de.morzo.realmscore.domain.backup

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Locks the backup JSON format: a [BackupData] tree must round-trip losslessly and carry the
 * current schema version. Pure JVM test — the merge/DB logic is exercised separately.
 */
class BackupSerializationTest {

    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    private fun sample() = BackupData(
        schemaVersion = CURRENT_BACKUP_SCHEMA_VERSION,
        appVersion = "1.2.0",
        exportedAt = "2026-01-15T14:30:00Z",
        deviceId = "device-abc",
        profiles = listOf(
            BackupProfile(
                id = "p1",
                name = "Maria",
                colorArgb = -10185235,
                isLocalOwner = true,
                isArchived = false,
                archivedAt = null,
                createdAt = 1705320600000,
                updatedAt = 1705320600000,
                originDeviceId = "device-abc",
            ),
        ),
        games = listOf(
            BackupGame(
                id = "g1",
                displayName = null,
                mode = "FIXED_ROUNDS",
                targetRounds = 3,
                targetPoints = null,
                startedAt = 1705320600000,
                closedAt = 1705327800000,
                closedReason = "COMPLETED",
                createdAt = 1705320600000,
                updatedAt = 1705327800000,
                originDeviceId = "device-abc",
                participants = listOf(BackupParticipant("p1", 0, lastScanOrder = 0)),
                rounds = listOf(
                    BackupRound(
                        id = "r1",
                        roundNumber = 1,
                        startedAt = 1705321000000,
                        completedAt = 1705323600000,
                        discardScanned = false,
                        createdAt = 1705321000000,
                        updatedAt = 1705323600000,
                        originDeviceId = "device-abc",
                        discardCards = emptyList(),
                        results = listOf(
                            BackupResult(
                                id = "rr1",
                                profileId = "p1",
                                totalScore = 87,
                                createdAt = 1705323600000,
                                updatedAt = 1705323600000,
                                originDeviceId = "device-abc",
                                handCards = listOf(
                                    BackupHandCard(
                                        id = "hc1",
                                        cardKey = "dragon",
                                        position = 0,
                                        jokerTargetCardKey = null,
                                        jokerTargetSuit = null,
                                        createdAt = 1705323600000,
                                        updatedAt = 1705323600000,
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        ),
    )

    @Test
    fun roundTrips_losslessly() {
        val original = sample()
        val encoded = json.encodeToString(original)
        val decoded = json.decodeFromString<BackupData>(encoded)
        assertEquals(original, decoded)
    }

    @Test
    fun encodes_currentSchemaVersion() {
        val encoded = json.encodeToString(sample())
        assertTrue(encoded.contains("\"schemaVersion\": $CURRENT_BACKUP_SCHEMA_VERSION"))
    }

    @Test
    fun importResult_defaultsMissingProfileToZero() {
        val result = ImportResult(
            profilesAdded = 2,
            profilesSkipped = 1,
            gamesCreated = 3,
            gamesUpdated = 1,
            roundsAdded = 5,
            roundsSkipped = 4,
        )
        assertEquals(0, result.roundsSkippedMissingProfile)
    }
}
