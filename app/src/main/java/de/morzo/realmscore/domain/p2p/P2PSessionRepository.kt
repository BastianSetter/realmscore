package de.morzo.realmscore.domain.p2p

import de.morzo.realmscore.domain.p2p.model.BluetoothStatus
import de.morzo.realmscore.domain.p2p.model.HandshakePayload
import de.morzo.realmscore.domain.p2p.model.HandCardSyncData
import de.morzo.realmscore.domain.p2p.model.NavSignal
import de.morzo.realmscore.domain.p2p.model.ParticipantInfo
import de.morzo.realmscore.domain.p2p.model.RejoinInfo
import de.morzo.realmscore.domain.p2p.model.SessionState
import de.morzo.realmscore.domain.p2p.model.SyncMessage
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * High-level P2P session API (Phase 28, Stage A scope: setup + live player list). Hides all Bluetooth
 * / handshake machinery from the UI; ViewModels observe [sessionState] and [incomingParticipants].
 */
interface P2PSessionRepository {

    /** Drives the host "open for joins" screen, the join flow and the connection indicators. */
    val sessionState: StateFlow<SessionState>

    /** Client-side: the live roster the host broadcasts during game setup. */
    val incomingParticipants: StateFlow<List<ParticipantInfo>>

    /**
     * Host-side: players contributed by joined clients (one per device, deduped by `originDeviceId`).
     * The new-game screen merges these into its roster so a join shows up as a startable player.
     */
    val joinedParticipants: StateFlow<List<ParticipantInfo>>

    /**
     * Drop any leftover joined players ([joinedParticipants]) WITHOUT tearing down live connections.
     * A fresh new game ("Neues Spiel", not "Neues Spiel starten") calls this so a previous session's
     * stale joiners don't pollute the new roster when [close] was skipped to guard an active game.
     */
    fun resetJoinedRoster()

    /**
     * One-shot navigation commands (Stage B): clients follow the host into round capture / reveal.
     * A `SharedFlow` so each command fires its navigation exactly once (host emits via broadcasts).
     */
    val navSignals: SharedFlow<NavSignal>

    /**
     * The host-authoritative lock + done state of the active round (Stage B), or null when no round is
     * being captured. Every device's round-capture screen observes this to paint lock indicators and to
     * drive its deterministic auto-assignment of free units.
     */
    val roundStatus: StateFlow<SyncMessage.RoundStatus?>

    /**
     * The *previous* round's per-device ordered submit lists (deviceId → ordered unitIds), set whenever
     * a round opens (host: in [startSharedSession]; client: on [SyncMessage.StartRound]). The
     * round-capture screen reads it to build the adaptive round-2+ auto-assign order. Empty for the
     * first round of a game (and when no session is active).
     */
    val roundOrderSeed: StateFlow<Map<String, List<String>>>

    /**
     * Live, uncommitted card selections of the units currently being captured on *other* devices
     * (Stage B+): unitId → its in-progress card keys. The round-capture picker folds these into its
     * "used elsewhere" greying so a physical card can't be picked twice while two hands are edited at
     * once — before either is submitted. Empty when no session/round is active.
     */
    val liveDrafts: StateFlow<Map<String, Set<String>>>

    /**
     * The host this device last joined as a client, persisted across an app restart (Phase 28, §6 #2),
     * or null when there's nothing to rejoin (never joined, or the joined game has since closed). The
     * home / join screens read it to relabel "Session beitreten" → "Session erneut beitreten" and to
     * reconnect via [connectToHost] without a fresh QR scan. Cleared on [SyncMessage.GameClosed].
     */
    val rejoinInfo: StateFlow<RejoinInfo?>

    /**
     * Forget the persisted last host ([rejoinInfo] → null, DataStore cleared) so the join flow stops
     * auto-reconnecting to it and the user can scan a *different* session. Needed because a game that
     * was abandoned / killed (not formally closed) never fired [SyncMessage.GameClosed], leaving the
     * stored host pinned. Does not touch the live connection — call [close] first to drop a stale one.
     */
    fun clearRejoinInfo()

    /**
     * True while a shared game is in progress (a round has been distributed and the game isn't closed).
     * The new-game / join screens use this so merely visiting them doesn't tear down a live session.
     */
    fun isInActiveGame(): Boolean

    fun bluetoothStatus(): BluetoothStatus

    /** The local Bluetooth adapter name the host advertises (so the joiner can find it in CDM). */
    fun localBluetoothName(): String?

    /**
     * Host: open [gameId] for joins, advertising the current [participants]. Starts the RFCOMM server
     * and the accept loop. On success [sessionState] transitions to [SessionState.Hosting].
     */
    suspend fun openForJoins(gameId: String, participants: List<ParticipantInfo>): Result<Unit>

    /** Host: re-broadcast the roster after it changed in the new-game screen. */
    suspend fun broadcastParticipants(participants: List<ParticipantInfo>)

    /**
     * Host: the game [gameId] has been created locally — mint its first round, distribute the whole
     * game ([SyncMessage.FullGameState]) so every client builds the same-UUID mirror, then signal
     * [SyncMessage.StartRound] so all devices enter round capture together. Returns the new round id
     * for the host's own navigation. No-op-friendly: only meaningful while [SessionState.Hosting].
     */
    suspend fun startSharedSession(gameId: String): Result<String>

    /** Client-side: this device's own canonical (host-assigned) seat profile id, or null on the host/idle. */
    fun ownSeatUnitId(): String?

    /** Claim [unitId] in [roundId] for this device (host arbitrates; result arrives via [roundStatus]). */
    suspend fun requestLock(roundId: String, unitId: String)

    /** Voluntarily give up a lock this device holds (without finishing the unit). */
    suspend fun releaseLock(roundId: String, unitId: String)

    /** Force-release a unit locked by another device (manual "Übernehmen"). */
    suspend fun forceUnlock(roundId: String, unitId: String)

    /**
     * Re-open a finished [unitId] for correction before the reveal (§6 #4): the host un-dones it and
     * locks it to this device, then re-broadcasts [roundStatus]. Any device may correct a finished unit.
     */
    suspend fun reopenUnit(roundId: String, unitId: String)

    /** Mark [unitId] finished (captured): the host records it done and re-broadcasts [roundStatus]. */
    suspend fun markUnitDone(roundId: String, unitId: String)

    /** Propagate a freshly captured player hand to the other devices' mirrors (Stage B live sync). */
    suspend fun pushHandCards(roundId: String, unitId: String, cards: List<HandCardSyncData>)

    /** Propagate the freshly captured Mittelfeld (discard pile) to the other devices' mirrors. */
    suspend fun pushDiscard(roundId: String, cards: List<String>)

    /**
     * Stream this device's *in-progress* selection for [unitId] (card keys only) so the other devices
     * can grey those cards out live (Stage B+). Sent on every pick/clear; the host relays it. Clearing
     * is host-driven, so callers only ever push non-empty selections.
     */
    suspend fun pushHandDraft(roundId: String, unitId: String, cardKeys: List<String>)

    /**
     * Host: every unit of [roundId] is captured — distribute the canonical mirror ([FullGameState]) and
     * signal [SyncMessage.RoundComplete] so all devices open the reveal together (host is the sole
     * scoring authority). No-op on a client.
     */
    suspend fun finishRound(roundId: String)

    /**
     * Host: the game [gameId] is finished — distribute the final [FullGameState] and a
     * [SyncMessage.GameClosed] so every client merges the complete game and reattributes its own seat to
     * its local owner profile. No-op on a client.
     */
    suspend fun closeSharedGame(gameId: String)

    /**
     * Host: announce that the next game is being set up with this group (the host tapped "Neues Spiel
     * starten" on the game-end screen) so every client shows a waiting screen. The actual next round
     * still arrives via [startSharedSession] → [SyncMessage.StartRound]. No-op on a client.
     */
    suspend fun announceNewGameSetup()

    /**
     * Client: connect to the host at [macAddress] (resolved via CompanionDeviceManager) using the
     * handshake [payload]. Announces this device as [selfParticipant] (the joiner's own player) and
     * starts receiving roster updates.
     */
    suspend fun connectToHost(
        payload: HandshakePayload,
        macAddress: String,
        selfParticipant: ParticipantInfo,
    ): Result<Unit>

    /** Tear down the session (close all sockets), returning to [SessionState.Idle]. */
    fun close()
}
