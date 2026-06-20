package de.morzo.realmscore.domain.p2p.model

import de.morzo.realmscore.domain.backup.BackupGame
import de.morzo.realmscore.domain.backup.BackupProfile
import kotlinx.serialization.Serializable

/**
 * Wire protocol for a live P2P session (Phase 28). A sealed hierarchy serialized polymorphically by
 * kotlinx.serialization (a `type` discriminator is added by [SyncProtocol]). Each message is sent as
 * one newline-terminated JSON line over the Bluetooth RFCOMM stream.
 *
 * [FullGameState] reuses the backup DTOs ([BackupGame] + the referenced [BackupProfile]s) so the
 * receiver can merge it through the very same merge-by-UUID logic the JSON backup/restore uses. The
 * profiles travel alongside the game so a receiver that has never seen these players can still insert
 * the rounds (otherwise they would be defensively skipped as "missing profile").
 *
 * Parameterless messages are `object`s, not `data class`es (a data class with no properties is
 * invalid Kotlin).
 */
@Serializable
sealed class SyncMessage {

    /** Host → clients: the current participant roster (live during game setup). */
    @Serializable
    data class PlayerListUpdate(val participants: List<ParticipantInfo>) : SyncMessage()

    /** A device claims the right to enter cards for one player in one round (optimistic lock). */
    @Serializable
    data class LockRequest(
        val profileId: String,
        val roundId: String,
        val deviceId: String,
    ) : SyncMessage()

    /** The holding device voluntarily releases its lock. */
    @Serializable
    data class LockRelease(val profileId: String, val roundId: String) : SyncMessage()

    /** A forced release (someone hit the "Entsperren" button on another device). */
    @Serializable
    data class UnlockRequest(val profileId: String, val roundId: String) : SyncMessage()

    /** Live hand-card entry for one player in one round. */
    @Serializable
    data class HandCardUpdate(
        val roundId: String,
        val profileId: String,
        val cards: List<HandCardSyncData>,
    ) : SyncMessage()

    /** Live Mittelfeld (discard pile) entry for one round (list of card keys). */
    @Serializable
    data class DiscardUpdate(val roundId: String, val cards: List<String>) : SyncMessage()

    /**
     * Live, *uncommitted* card selection for a unit currently being captured (Stage B+). Streamed on
     * every pick/clear while a hand is in progress so the other devices can grey out cards a physical
     * deck can only hold once. Carries card keys only — never persisted, never scored (an incomplete
     * hand must not enter the mirror). The host relays it and is the sole authority for clearing it
     * (on commit / release / disconnect), emitting an empty [cardKeys] so every device clears in step.
     */
    @Serializable
    data class HandDraftUpdate(
        val roundId: String,
        val unitId: String,
        val deviceId: String,
        val cardKeys: List<String>,
    ) : SyncMessage()

    /**
     * Host → all: a round is now open for capture (Stage B). Sent right after the [FullGameState] that
     * seeds every device's mirror with the shared game/round/profile UUIDs, so all phones navigate into
     * the round-capture screen together. Reused for each subsequent round.
     */
    @Serializable
    data class StartRound(val gameId: String, val roundId: String) : SyncMessage()

    /**
     * Client → host: this device finished capturing [unitId] (a player hand or the Mittelfeld) in
     * [roundId]. The host marks the unit done in its authoritative registry (and drops its lock), then
     * re-broadcasts [RoundStatus]. A finished unit is never reassigned (MVP).
     */
    @Serializable
    data class UnitDone(val roundId: String, val unitId: String) : SyncMessage()

    /**
     * Host → all: the authoritative lock + done state of a round (Stage B). Drives the capture-screen
     * lock indicators ("wird bearbeitet von X"), the deterministic auto-assignment of free units, and
     * the pre-reveal waiting screen. A unit absent from both lists is free to grab.
     */
    @Serializable
    data class RoundStatus(
        val roundId: String,
        val locks: List<UnitLock>,
        val doneUnitIds: List<String>,
    ) : SyncMessage()

    /** A round was completed (all hands captured) on the sending device. */
    @Serializable
    data class RoundComplete(val roundId: String) : SyncMessage()

    /** The host closed the game; full data distribution follows via [FullGameState]. */
    @Serializable
    data class GameClosed(val gameId: String, val closedAt: Long) : SyncMessage()

    /**
     * Host → all: the host is setting up the *next* game with this group (it tapped "Neues Spiel
     * starten" on the game-end screen). Clients show a waiting screen until the host starts the next
     * round (then the usual [StartRound] pulls them into capture).
     */
    @Serializable
    data object NewGameSetup : SyncMessage()

    /** Full game snapshot for reconnect delta-sync and end-of-game distribution. */
    @Serializable
    data class FullGameState(
        val game: BackupGame,
        val profiles: List<BackupProfile>,
    ) : SyncMessage()

    /**
     * Client → host right after connecting, announcing itself. [participant] carries the joiner's own
     * player (its local profile) so the host adds it straight into the game roster ("join adds a
     * player" model). Nullable for forward-compat / the bare device-announce case.
     */
    @Serializable
    data class DeviceJoined(
        val deviceId: String,
        val deviceName: String,
        val participant: ParticipantInfo? = null,
    ) : SyncMessage()

    /** Heartbeat. */
    @Serializable
    data object Ping : SyncMessage()

    /** Heartbeat reply. */
    @Serializable
    data object Pong : SyncMessage()
}

/** One held lock in [SyncMessage.RoundStatus]: [unitId] is being captured by [deviceName]. */
@Serializable
data class UnitLock(
    val unitId: String,
    val deviceId: String,
    val deviceName: String,
)

/** One participant as broadcast in [SyncMessage.PlayerListUpdate]. */
@Serializable
data class ParticipantInfo(
    val profileId: String,
    val name: String,
    val colorArgb: Int,
    val seatOrder: Int,
    val originDeviceId: String,
)

/** A single hand card in [SyncMessage.HandCardUpdate] (mirrors the persisted joker selections). */
@Serializable
data class HandCardSyncData(
    val cardKey: String,
    val position: Int,
    val jokerTargetCardKey: String? = null,
    val jokerTargetSuit: String? = null,
)
