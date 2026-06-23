package de.morzo.realmscore.ui.game

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import de.morzo.realmscore.data.cards.CardLookup
import de.morzo.realmscore.data.datastore.DeviceUuidProvider
import de.morzo.realmscore.domain.game.CaptureOrdering
import de.morzo.realmscore.domain.game.DistributedAssignOrder
import de.morzo.realmscore.domain.model.CardDefinition
import de.morzo.realmscore.domain.p2p.P2PSessionRepository
import de.morzo.realmscore.domain.p2p.model.HandCardSyncData
import de.morzo.realmscore.domain.p2p.model.SessionState
import de.morzo.realmscore.domain.p2p.model.SyncMessage
import de.morzo.realmscore.domain.repository.GameRepository
import de.morzo.realmscore.domain.repository.HandCardEntry
import de.morzo.realmscore.domain.repository.HandCardRepository
import de.morzo.realmscore.domain.repository.ProfileRepository
import de.morzo.realmscore.domain.repository.RoundRepository
import de.morzo.realmscore.domain.repository.SettingsRepository
import de.morzo.realmscore.domain.scoring.JokerAssignment
import de.morzo.realmscore.domain.scoring.ScoringEngine
import de.morzo.realmscore.domain.scoring.ScoringInput
import de.morzo.realmscore.domain.scoring.solver.OptimalSolver
import de.morzo.realmscore.domain.scoring.toScoringChoices
import de.morzo.realmscore.ui.handentry.PLAYER_HAND_SLOT_COUNT
import de.morzo.realmscore.ui.handentry.PlayerHandEntryUiState
import de.morzo.realmscore.ui.sandbox.CardSlot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val NECROMANCER_KEY = "necromancer"

/** Sentinel id for the synthetic Mittelfeld (discard) entry in the capture rotation. */
private const val DISCARD_ID = "__discard__"

/** Neutral blue-grey dot for the Mittelfeld entry in the dropdown. */
private const val DISCARD_COLOR = 0xFF607D8B.toInt()

/** Mittelfeld card counts: 10 for a two-player game, 12 with more players. */
private const val DISCARD_SLOTS_TWO_PLAYERS = 10
private const val DISCARD_SLOTS_MULTI_PLAYER = 12

/** One player chip in the capture dropdown, with its "(x/y)" capture progress. */
data class CapturePlayer(
    val profileId: String,
    val name: String,
    val colorArgb: Int,
    val captured: Boolean,
    val cardsCount: Int,
    val requiredCount: Int,
    /** P2P (Stage B): the name of another device currently capturing this unit, else null. */
    val lockedByName: String? = null,
    /** P2P (Stage B): this unit has been finished on some device (shown as done across all phones). */
    val isDone: Boolean = false,
)

data class RoundCaptureUiState(
    val isLoading: Boolean = true,
    val roundNumber: Int = 0,
    val orderedPlayers: List<CapturePlayer> = emptyList(),
    val currentProfileId: String? = null,
    /** Whether the embedded KartenPick picker shows its text-search field (spec 25.5). */
    val pickerSearchEnabled: Boolean = true,
    /** Whether an empty hand opens the camera card scan instead of the manual picker (Phase 26). */
    val cameraScanEnabled: Boolean = false,
    /** Names of cards placed in more than one entry this round — must be empty before the reveal. */
    val duplicateCardNames: List<String> = emptyList(),
    /** UI shape consumed by the shared PlayerHandCaptureContent for the current entry. */
    val current: PlayerHandEntryUiState = PlayerHandEntryUiState(isLoading = false),
    /** P2P (Stage B): true when this device finished its share and is waiting for the rest of the round. */
    val waitingForOthers: Boolean = false,
    /** P2P (Stage B): the session is distributing this round across devices (locks/auto-assign active). */
    val isDistributed: Boolean = false,
    /** P2P (§6 #4): re-entered an already-revealed round to correct it — show the edit affordances. */
    val isEditingCompletedRound: Boolean = false,
) {
    val allCaptured: Boolean
        get() = orderedPlayers.isNotEmpty() && orderedPlayers.all { it.captured }

    /** A physical card can sit in only one entry; block the reveal until any clash is resolved. */
    val hasDuplicates: Boolean get() = duplicateCardNames.isNotEmpty()
}

/**
 * Drives the full-screen round capture (Phase 18.1): one ViewModel orchestrates every entry of a
 * round, holding an in-memory draft per entry so switching between them (via the dropdown) never
 * loses entered cards. Submitting saves the current entry and auto-advances to the next not-yet
 * captured one; once everything is captured it persists the scan order and signals the caller to
 * move on to the reveal.
 *
 * Phase 20: when the "Mittelfeld erfassen" setting is on, the central discard pile is added as an
 * extra entry (10 cards for two players, 12 otherwise) that is captured exactly like a player and
 * is mandatory before the reveal. It records card identities only — no jokers, Necromancer or
 * scoring — and its saved cards then scope the Necromancer pick for the player hands.
 */
class RoundCaptureViewModel(
    private val cardLookup: CardLookup,
    private val handCardRepo: HandCardRepository,
    private val profileRepo: ProfileRepository,
    private val gameRepo: GameRepository,
    private val roundRepo: RoundRepository,
    private val settingsRepo: SettingsRepository,
    private val engine: ScoringEngine,
    private val optimalSolver: OptimalSolver,
    private val p2p: P2PSessionRepository,
    private val deviceUuidProvider: DeviceUuidProvider,
    private val roundId: String,
) : ViewModel() {

    val allCards: List<CardDefinition> = cardLookup.getAll()

    private data class Draft(
        val slots: List<CardSlot> = List(PLAYER_HAND_SLOT_COUNT) { CardSlot.Empty },
        val jokerAssignments: Map<String, JokerAssignment> = emptyMap(),
    )

    private var gameId: String = ""
    private val drafts = mutableMapOf<String, Draft>()
    private val nameById = mutableMapOf<String, String>()
    private val colorById = mutableMapOf<String, Int>()
    private var orderedIds: List<String> = emptyList()
    private val captured = mutableSetOf<String>()
    // Scan order: 0-based position assigned the first time a player is submitted (excludes Mittelfeld).
    private val scanOrder = LinkedHashMap<String, Int>()

    private var isSaving = false
    private var isOptimalRunning = false

    private var discardEnabled = false
    private var discardSlotCount = DISCARD_SLOTS_MULTI_PLAYER

    // --- P2P distributed capture (Stage B). Inert when no session is active. ---
    private var isDistributed = false
    private var isHost = false
    /**
     * Re-entered an already-revealed round to correct it (§6 #4, post-reveal). The host then must NOT
     * auto-reveal again on every submit — corrections propagate via the mirror and refresh each device's
     * (reactive) round summary in place; the corrector returns to the summary via "Fertig".
     */
    private var isEditingCompletedRound = false
    /** Host-only guard so the reveal is computed + broadcast exactly once per round. */
    private var roundFinishRequested = false
    private var myDeviceId: String = ""
    /** Device-specific steal order: own unit first, then [Mittelfeld?, hands by seatOrder]. */
    private var assignOrder: List<String> = emptyList()
    /** The unit we last asked the host to lock for us (awaiting confirmation via roundStatus). */
    private var pendingLockUnit: String? = null
    /** An explicit user target (manual grab / correction) awaiting the host's lock grant. */
    private var pendingSwitchUnit: String? = null
    private var latestStatus: SyncMessage.RoundStatus? = null

    // Saved discard state, kept in sync via the observer below; powers the Necromancer filtering.
    private var discardScanned = false
    private var discardCards: List<CardDefinition> = emptyList()

    // P2P (Stage B): hand cards captured on OTHER devices, from the synced mirror — they are not in this
    // VM's in-memory drafts, so they must be excluded from the picker separately. profileId -> cardKeys.
    private var syncedCardsByProfile: Map<String, Set<String>> = emptyMap()

    // P2P (Stage B+): live, uncommitted selections of units being captured on OTHER devices, from the
    // transient draft channel — folded into the picker so a card can't be picked on two phones at once
    // before either hand is submitted (the mirror only catches up on submit). unitId -> cardKeys.
    private var liveDraftsByUnit: Map<String, Set<String>> = emptyMap()

    private val _uiState = MutableStateFlow(RoundCaptureUiState())
    val uiState: StateFlow<RoundCaptureUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val round = roundRepo.getRoundById(roundId) ?: error("Round not found: $roundId")
            gameId = round.gameId
            val participants = gameRepo.getParticipants(round.gameId)

            // Default order: previous round's scan order (lastScanOrder asc), brand-new players
            // (null) last by seatOrder.
            val ordered = CaptureOrdering.order(participants)

            ordered.forEach { participant ->
                val profile = profileRepo.getById(participant.profileId) ?: return@forEach
                nameById[profile.id] = profile.name
                colorById[profile.id] = profile.colorArgb
                drafts[profile.id] = loadDraft(profile.id)
            }
            val playerIds = ordered.map { it.profileId }.filter { drafts.containsKey(it) }

            discardEnabled = settingsRepo.discardCaptureEnabled.first()
            val pickerSearchEnabled = settingsRepo.pickerSearchEnabled.first()
            val cameraScanEnabled = settingsRepo.cameraScanEnabled.first()
            discardSlotCount =
                if (playerIds.size <= 2) DISCARD_SLOTS_TWO_PLAYERS else DISCARD_SLOTS_MULTI_PLAYER

            orderedIds = if (discardEnabled) {
                nameById[DISCARD_ID] = "Mittelfeld"
                colorById[DISCARD_ID] = DISCARD_COLOR
                drafts[DISCARD_ID] = loadDiscardDraft()
                // Mittelfeld first so it is known before the player hands (Necromancer filtering).
                listOf(DISCARD_ID) + playerIds
            } else {
                playerIds
            }

            // Already fully recorded entries count as captured (e.g. when re-entering to correct).
            // Mirror the full submit validity (canSubmit), not just slot count, so an entry with all
            // slots filled but an unassigned joker is not wrongly treated as done and skipped (L6).
            orderedIds.forEach { id ->
                if (canSubmit(id)) captured += id
            }

            // P2P (Stage B): a live host/client session distributes this round across devices. The
            // current unit is then chosen by the lock flow (own hand first) rather than locally.
            val session = p2p.sessionState.value
            isHost = session is SessionState.Hosting
            isDistributed = session is SessionState.Hosting || session is SessionState.Connected
            // A completed round re-opened for capture means we're correcting an already-revealed round.
            isEditingCompletedRound = isDistributed && round.completedAt != null
            val first = if (isDistributed) {
                myDeviceId = deviceUuidProvider.get()
                assignOrder = buildAssignOrder(session, participants)
                null // set once a lock is granted
            } else {
                orderedIds.firstOrNull { it !in captured } ?: orderedIds.firstOrNull()
            }

            _uiState.update {
                it.copy(
                    isLoading = false,
                    roundNumber = round.roundNumber,
                    currentProfileId = first,
                    pickerSearchEnabled = pickerSearchEnabled,
                    cameraScanEnabled = cameraScanEnabled,
                    isDistributed = isDistributed,
                    isEditingCompletedRound = isEditingCompletedRound,
                )
            }
            rebuild()

            if (isDistributed) {
                // Only adopt status that belongs to THIS round — a stale previous-round status carries
                // bare unitIds (profileIds) that would otherwise look "done" in the new round.
                latestStatus = p2p.roundStatus.value?.takeIf { it.roundId == roundId }
                advanceToNextUnit(latestStatus) // claim our own unit first
                launch { p2p.roundStatus.collect { onRoundStatus(it) } }
                // Keep the picker's "used elsewhere" set current with hands captured on other devices.
                launch {
                    handCardRepo.observeHandCardKeysByProfile(roundId).collect { byProfile ->
                        syncedCardsByProfile = byProfile
                        rebuild()
                    }
                }
                // And with the *in-progress* selections still being typed on other devices (Stage B+).
                launch {
                    p2p.liveDrafts.collect { drafts ->
                        liveDraftsByUnit = drafts
                        rebuild()
                    }
                }
            }

            launch {
                combine(
                    roundRepo.observeRoundById(roundId),
                    roundRepo.observeDiscardCards(roundId),
                ) { r, keys -> (r?.discardScanned ?: false) to keys }
                    .collect { (scanned, keys) ->
                        discardScanned = scanned
                        discardCards = keys.mapNotNull { cardLookup.getByKey(it) }
                        // Keep the Mittelfeld draft in sync with the persisted pile, except while it is
                        // the entry currently being edited (don't clobber in-progress input). This VM
                        // is the only writer, so in practice this re-hydration is a no-op after our own
                        // save — but it stops the draft from going stale against the DB (L5).
                        if (discardEnabled && _uiState.value.currentProfileId != DISCARD_ID) {
                            drafts[DISCARD_ID] = loadDiscardDraft()
                        }
                        rebuild()
                    }
            }
        }
    }

    // --- P2P distributed-capture engine (Stage B) ---

    /**
     * Device-specific steal order. Round 1 (no prior data): this device's own unit first (the host
     * takes the Mittelfeld first so the discard is known before any Necromancer pick), then the global
     * order [Mittelfeld?, hands by seatOrder]. Round 2+: replay the previous round's submit history —
     * this device's own list first, then the shared round-robin combined list (see
     * [DistributedAssignOrder]). All mirrors share the same seatOrder + seed, so the order is consistent.
     */
    private suspend fun buildAssignOrder(
        session: SessionState,
        participants: List<de.morzo.realmscore.domain.model.GameParticipant>,
    ): List<String> {
        val seatOrderIds = participants.map { it.profileId }.filter { drafts.containsKey(it) }
        val globalOrder = if (discardEnabled) listOf(DISCARD_ID) + seatOrderIds else seatOrderIds
        val isHostDiscard = session is SessionState.Hosting && discardEnabled
        val ownSeat = when (session) {
            is SessionState.Hosting -> profileRepo.getLocalOwner()?.id
            else -> p2p.ownSeatUnitId()
        }
        val ownFirst = when {
            isHostDiscard -> DISCARD_ID
            ownSeat != null && ownSeat in globalOrder -> ownSeat
            else -> globalOrder.firstOrNull()
        }
        return DistributedAssignOrder.build(
            priorSubmissions = p2p.roundOrderSeed.value,
            myDeviceId = myDeviceId,
            globalOrder = globalOrder,
            ownFirst = ownFirst,
            // Keep the host's Mittelfeld pinned first across every round (Necromancer correctness).
            forcedFirst = if (isHostDiscard) DISCARD_ID else null,
        )
    }

    /** Reacts to the host's authoritative lock/done broadcast: repaint, then keep this device busy. */
    private fun onRoundStatus(status: SyncMessage.RoundStatus?) {
        if (status != null && status.roundId != roundId) return
        latestStatus = status
        rebuild()
        if (!isDistributed) return

        // Honor an explicit user target (manual grab from the dropdown / correction of a finished
        // hand) once the host grants us its lock; until then don't let auto-assign steal focus.
        val target = pendingSwitchUnit
        if (target != null) {
            if (isLockedByMe(target, status) && !isUnitDone(target, status)) {
                pendingSwitchUnit = null
                viewModelScope.launch {
                    // A hand captured on another device lives in our Room mirror but not in this VM's
                    // in-memory drafts — reload so a corrector starts from the latest entered cards.
                    drafts[target] = if (target == DISCARD_ID) loadDiscardDraft() else loadDraft(target)
                    selectUnit(target)
                }
            }
            return
        }

        val allDone = orderedIds.isNotEmpty() && orderedIds.all { isUnitDone(it, status) }
        if (isHost && !isEditingCompletedRound) {
            // Host is the sole reveal authority: compute + broadcast it once every unit is done.
            // Re-arm if a correction re-opens a unit (un-revealed window, §6 #4), so the reveal
            // fires again after the corrected hand is re-submitted. Skipped when correcting an
            // already-revealed round — those corrections refresh each summary in place, no re-reveal.
            if (allDone && !roundFinishRequested) {
                roundFinishRequested = true
                viewModelScope.launch { p2p.finishRound(roundId) }
                return
            }
            if (!allDone) roundFinishRequested = false
        }
        if (isSaving) return
        val current = _uiState.value.currentProfileId
        // Stay on the unit we hold until it is finished; otherwise grab the next one.
        if (current != null && isLockedByMe(current, status) && !isUnitDone(current, status)) return
        advanceToNextUnit(status)
    }

    /** Moves to the next free+unfinished unit (requesting its lock), or shows the waiting screen. */
    private fun advanceToNextUnit(status: SyncMessage.RoundStatus?) {
        val next = nextAssignableUnit(status)
        if (next == null) {
            pendingLockUnit = null
            _uiState.update { it.copy(currentProfileId = null, waitingForOthers = true) }
            rebuild()
            return
        }
        if (isLockedByMe(next, status)) {
            pendingLockUnit = null
        } else if (pendingLockUnit != next) {
            pendingLockUnit = next
            viewModelScope.launch { p2p.requestLock(roundId, next) }
        }
        selectUnit(next)
    }

    /** Focus [id] for capture and leave the waiting screen. */
    private fun selectUnit(id: String) {
        _uiState.update { it.copy(currentProfileId = id, waitingForOthers = false) }
        rebuild()
    }

    private fun nextAssignableUnit(status: SyncMessage.RoundStatus?): String? {
        val lockedBy = status?.locks?.associate { it.unitId to it.deviceId } ?: emptyMap()
        val done = status?.doneUnitIds?.toSet() ?: emptySet()
        return assignOrder.firstOrNull { unit ->
            unit !in done && (lockedBy[unit] == null || lockedBy[unit] == myDeviceId)
        }
    }

    private fun isLockedByMe(unit: String, status: SyncMessage.RoundStatus?): Boolean =
        status?.locks?.any { it.unitId == unit && it.deviceId == myDeviceId } == true

    private fun isLockedByOther(unit: String, status: SyncMessage.RoundStatus?): Boolean =
        status?.locks?.any { it.unitId == unit && it.deviceId != myDeviceId } == true

    private fun isUnitDone(unit: String, status: SyncMessage.RoundStatus?): Boolean =
        status?.doneUnitIds?.contains(unit) == true

    /** Force-release a unit stuck on another (idle) device, then let auto-assign grab it (B3 "Übernehmen"). */
    fun takeOverUnit(unitId: String) {
        if (!isDistributed) return
        viewModelScope.launch { p2p.forceUnlock(roundId, unitId) }
    }

    private fun requiredCountFor(id: String): Int =
        if (id == DISCARD_ID) discardSlotCount else PLAYER_HAND_SLOT_COUNT

    private suspend fun loadDraft(profileId: String): Draft {
        val existing = handCardRepo.getHand(roundId, profileId) ?: return Draft()
        val slots = MutableList<CardSlot>(PLAYER_HAND_SLOT_COUNT) { CardSlot.Empty }
        existing.cards.forEach { entry ->
            val card = cardLookup.getByKey(entry.cardKey) ?: return@forEach
            if (entry.position in 0 until PLAYER_HAND_SLOT_COUNT) {
                slots[entry.position] = CardSlot.Filled(card)
            }
        }
        // Necromancer pick and Island/Fountain choices are persisted on their own card entry's
        // jokerTargetCardKey column; the shared mapper rebuilds them all as joker assignments.
        val reconstructed = existing.cards.toScoringChoices()
        return Draft(
            slots = slots,
            jokerAssignments = reconstructed.jokerAssignments,
        )
    }

    private suspend fun loadDiscardDraft(): Draft {
        val keys = roundRepo.getDiscardCards(roundId)
        val slots = MutableList<CardSlot>(discardSlotCount) { CardSlot.Empty }
        keys.forEachIndexed { index, key ->
            if (index < discardSlotCount) {
                cardLookup.getByKey(key)?.let { slots[index] = CardSlot.Filled(it) }
            }
        }
        return Draft(slots = slots)
    }

    /** Recomputes [RoundCaptureUiState.orderedPlayers] and [RoundCaptureUiState.current]. */
    private fun rebuild() {
        val currentId = _uiState.value.currentProfileId
        val lockByUnit = latestStatus?.locks?.associateBy { it.unitId } ?: emptyMap()
        val doneUnits = latestStatus?.doneUnitIds?.toSet() ?: emptySet()
        val players = orderedIds.map { id ->
            val draft = drafts[id] ?: Draft()
            CapturePlayer(
                profileId = id,
                name = nameById[id] ?: "",
                colorArgb = colorById[id] ?: 0,
                captured = id in captured,
                cardsCount = draft.slots.count { it is CardSlot.Filled },
                requiredCount = requiredCountFor(id),
                lockedByName = lockByUnit[id]?.takeIf { it.deviceId != myDeviceId }?.deviceName,
                isDone = id in doneUnits,
            )
        }
        _uiState.update {
            it.copy(
                orderedPlayers = players,
                current = buildCurrent(currentId),
                duplicateCardNames = duplicateCardNames(),
            )
        }
    }

    /** Card names that appear in more than one entry (a physical card can be in only one place). */
    private fun duplicateCardNames(): List<String> {
        val counts = HashMap<String, Int>()
        drafts.values.forEach { draft ->
            draft.slots.forEach { slot ->
                (slot as? CardSlot.Filled)?.card?.key?.let { counts[it] = (counts[it] ?: 0) + 1 }
            }
        }
        return counts.filter { it.value > 1 }
            .keys
            .mapNotNull { cardLookup.getByKey(it)?.nameDe }
            .sorted()
    }

    private fun buildCurrent(profileId: String?): PlayerHandEntryUiState {
        if (profileId == null) return PlayerHandEntryUiState(isLoading = false)
        val draft = drafts[profileId] ?: Draft()
        val isDiscard = profileId == DISCARD_ID
        // A physical card can be in only one place, so exclude everything already placed elsewhere:
        // this device's other in-memory drafts, plus — for a distributed round — hands captured on
        // other devices (synced mirror) and the Mittelfeld captured elsewhere.
        val usedByOthers = buildSet {
            // This device's other in-memory drafts.
            drafts.asSequence()
                .filter { it.key != profileId }
                .forEach { (_, d) ->
                    d.slots.forEach { slot -> (slot as? CardSlot.Filled)?.card?.key?.let { add(it) } }
                }
            // Hands captured on other devices (synced mirror). A unit currently being edited elsewhere
            // is skipped here and taken from its live draft instead, so a card freed mid-edit (e.g. a
            // correction swapping one card for another) is released live — the mirror still holds the
            // pre-correction hand until it is re-submitted.
            syncedCardsByProfile.asSequence()
                .filter { it.key != profileId && it.key !in liveDraftsByUnit }
                .forEach { addAll(it.value) }
            // In-progress selections on other devices (live draft channel): the authoritative current keys.
            liveDraftsByUnit.asSequence()
                .filter { it.key != profileId }
                .forEach { addAll(it.value) }
            // The Mittelfeld: its live draft while being edited, else the persisted pile.
            if (profileId != DISCARD_ID && DISCARD_ID !in liveDraftsByUnit) {
                discardCards.forEach { add(it.key) }
            }
        }
        return PlayerHandEntryUiState(
            isLoading = false,
            playerName = nameById[profileId] ?: "",
            slots = draft.slots,
            jokerAssignments = draft.jokerAssignments,
            cardsUsedByOthers = usedByOthers,
            mittelfeldScanned = discardScanned,
            isOptimalRunning = isOptimalRunning,
            isSaving = isSaving,
            isDiscard = isDiscard,
            requiredSlotCount = requiredCountFor(profileId),
        )
    }

    private fun updateCurrentDraft(transform: (Draft) -> Draft) {
        val id = _uiState.value.currentProfileId ?: return
        val current = drafts[id] ?: Draft()
        val updated = transform(current).pruned()
        drafts[id] = updated
        rebuild()
        // P2P (Stage B+): stream the in-progress selection for the unit we hold so the other devices
        // grey these cards out live. The current key set (empty after clearing the last card) is the
        // truth; lifecycle clears (commit / release / disconnect) are host-driven.
        if (isDistributed) {
            val keys = updated.slots.mapNotNull { (it as? CardSlot.Filled)?.card?.key }
            viewModelScope.launch { p2p.pushHandDraft(roundId, id, keys) }
        }
    }

    /**
     * Switch the capture focus to [profileId]. In a distributed round this routes through the host:
     * a unit we already hold is selected directly; a free (unlocked + unfinished) unit is grabbed; a
     * finished unit is re-opened for correction (any device may, §6 #4); a unit locked by another
     * device is left alone (the UI disables it). The grab/correction completes asynchronously once the
     * host's [RoundStatus] grants us the lock (see [onRoundStatus]).
     */
    fun switchToPlayer(profileId: String) {
        if (profileId !in drafts) return
        if (!isDistributed) {
            _uiState.update { it.copy(currentProfileId = profileId) }
            rebuild()
            return
        }
        val status = latestStatus
        when {
            isLockedByMe(profileId, status) -> {
                pendingSwitchUnit = null
                selectUnit(profileId)
            }
            isUnitDone(profileId, status) -> {
                pendingSwitchUnit = profileId
                viewModelScope.launch { p2p.reopenUnit(roundId, profileId) }
            }
            isLockedByOther(profileId, status) -> Unit // not switchable while another device holds it
            else -> {
                pendingSwitchUnit = profileId
                viewModelScope.launch { p2p.requestLock(roundId, profileId) }
            }
        }
    }

    fun setCardInSlot(slotIndex: Int, card: CardDefinition) {
        updateCurrentDraft { draft ->
            if (slotIndex !in draft.slots.indices) draft
            else draft.copy(slots = draft.slots.toMutableList().also { it[slotIndex] = CardSlot.Filled(card) })
        }
    }

    /**
     * Replaces the current entry's slots with the scanned cards (Phase 26). Extra cards beyond the
     * entry's slot count are dropped and any remaining slots cleared; the user corrects mis-reads in
     * the player stage. Filling the slots makes the hand non-empty, which advances out of the scan.
     */
    fun setCardsFromScan(cards: List<CardDefinition>) {
        updateCurrentDraft { draft ->
            val slots = MutableList<CardSlot>(draft.slots.size) { CardSlot.Empty }
            cards.take(slots.size).forEachIndexed { index, card -> slots[index] = CardSlot.Filled(card) }
            draft.copy(slots = slots)
        }
    }

    fun clearSlot(slotIndex: Int) {
        updateCurrentDraft { draft ->
            if (slotIndex !in draft.slots.indices) draft
            else draft.copy(slots = draft.slots.toMutableList().also { it[slotIndex] = CardSlot.Empty })
        }
    }

    fun setJokerAssignment(jokerKey: String, assignment: JokerAssignment?) {
        updateCurrentDraft { draft ->
            val newAssignments = draft.jokerAssignments.toMutableMap()
            if (assignment == null) newAssignments.remove(jokerKey) else newAssignments[jokerKey] = assignment
            draft.copy(jokerAssignments = newAssignments)
        }
    }

    fun setNecromancerPick(cardKey: String) =
        setJokerAssignment(NECROMANCER_KEY, JokerAssignment(NECROMANCER_KEY, cardKey))

    fun clearNecromancerPick() = setJokerAssignment(NECROMANCER_KEY, null)

    fun applyOptimal() {
        val id = _uiState.value.currentProfileId ?: return
        if (id == DISCARD_ID) return
        val draft = drafts[id] ?: return
        val filled = draft.slots.mapNotNull { (it as? CardSlot.Filled)?.card }
        if (filled.isEmpty()) return
        val seed = ScoringInput(
            hand = filled,
            jokerAssignments = draft.jokerAssignments,
            discardPile = discardCards,
            discardScanned = discardScanned,
        )
        isOptimalRunning = true
        rebuild()
        viewModelScope.launch {
            val best = withContext(Dispatchers.Default) { optimalSolver.findOptimal(seed) }
            drafts[id] = (drafts[id] ?: draft).copy(
                jokerAssignments = best.bestInput.jokerAssignments,
            )
            isOptimalRunning = false
            rebuild()
        }
    }

    /**
     * Saves the current entry (a player's hand or the Mittelfeld), records the scan position for
     * real players and advances to the next not-yet-captured entry. If everything is captured,
     * persists the scan order and calls [onAllDone].
     */
    fun submitCurrentAndAdvance(onAllDone: () -> Unit) {
        val id = _uiState.value.currentProfileId ?: return
        val draft = drafts[id] ?: return
        if (!canSubmit(id) || isSaving) return
        isSaving = true
        rebuild()
        viewModelScope.launch {
            if (id == DISCARD_ID) {
                val keys = draft.slots.mapNotNull { (it as? CardSlot.Filled)?.card?.key }
                roundRepo.saveDiscardCards(roundId, keys)
            } else {
                saveHand(id, draft)
                if (id !in scanOrder) scanOrder[id] = scanOrder.size
            }
            captured += id

            isSaving = false

            if (isDistributed) {
                // Live-sync the capture into every other device's mirror BEFORE announcing it done, so
                // the host has the cards before it can detect "all done" and compute the reveal. Order
                // is preserved per connection, so the host applies the cards first.
                if (id == DISCARD_ID) {
                    p2p.pushDiscard(roundId, draft.slots.mapNotNull { (it as? CardSlot.Filled)?.card?.key })
                } else {
                    p2p.pushHandCards(roundId, id, draft.toSyncData())
                }
                // Hand off to the host: mark this unit done (it drops the lock + re-broadcasts status).
                // onRoundStatus then auto-grabs the next free unit, or shows the waiting screen. The
                // host owns the reveal (B4), so this device never navigates there itself.
                p2p.markUnitDone(roundId, id)
                rebuild()
                return@launch
            }

            val next = orderedIds.firstOrNull { it !in captured }
            when {
                // All captured but a card sits in two entries — don't proceed to the reveal; the
                // warning banner shows so the user can correct it, then tap "reveal" explicitly.
                next == null && duplicateCardNames().isNotEmpty() -> rebuild()
                next == null -> {
                    gameRepo.updateScanOrder(gameId, scanOrder.toMap())
                    rebuild()
                    onAllDone()
                }
                else -> {
                    _uiState.update { it.copy(currentProfileId = next) }
                    rebuild()
                }
            }
        }
    }

    private suspend fun saveHand(profileId: String, draft: Draft) {
        val filled = draft.slots.mapNotNull { (it as? CardSlot.Filled)?.card }
        // discardPile/discardScanned are intentionally omitted: the ScoringEngine resolves the
        // Necromancer pick via cardLookup and never reads ctx.discardPile (only the OptimalSolver
        // does). The reveal and stats re-scoring paths omit it the same way, so the persisted score
        // here matches what those recompute. Keep all three canonical paths identical (L2).
        val input = ScoringInput(
            hand = filled,
            jokerAssignments = draft.jokerAssignments,
        )
        val totalScore = withContext(Dispatchers.Default) { engine.score(input).totalScore }
        val entries = draft.slots.mapIndexedNotNull { idx, slot ->
            val card = (slot as? CardSlot.Filled)?.card ?: return@mapIndexedNotNull null
            // Every target — jokers, Island, Fountain and the Necromancer pull — is a jokerAssignment
            // keyed by the card and persists to its own entry's jokerTargetCardKey column.
            val assignment = draft.jokerAssignments[card.key]
            HandCardEntry(
                cardKey = card.key,
                position = idx,
                jokerTargetCardKey = assignment?.targetCardKey,
                jokerTargetSuit = assignment?.targetSuit?.name,
            )
        }
        handCardRepo.saveHand(
            roundId = roundId,
            profileId = profileId,
            cards = entries,
            totalScore = totalScore,
        )
    }

    private fun canSubmit(id: String): Boolean {
        val draft = drafts[id] ?: return false
        val filled = draft.slots.mapNotNull { (it as? CardSlot.Filled)?.card }
        if (filled.size != requiredCountFor(id)) return false
        if (id == DISCARD_ID) return true
        val jokers = filled.filter { it.isJoker }
        return jokers.all { joker -> draft.jokerAssignments[joker.key]?.targetCardKey != null }
    }

    /** Maps the current draft to the wire shape for live card sync (Stage B), mirroring [saveHand]. */
    private fun Draft.toSyncData(): List<HandCardSyncData> =
        slots.mapIndexedNotNull { idx, slot ->
            val card = (slot as? CardSlot.Filled)?.card ?: return@mapIndexedNotNull null
            val assignment = jokerAssignments[card.key]
            HandCardSyncData(card.key, idx, assignment?.targetCardKey, assignment?.targetSuit?.name)
        }

    private fun Draft.pruned(): Draft {
        val handKeys = slots.mapNotNull { (it as? CardSlot.Filled)?.card?.key }.toSet()
        // Every assignment (jokers, Island, Fountain, Necromancer) is keyed by its hand card, so they
        // all prune away automatically once that card leaves the hand.
        return copy(jokerAssignments = jokerAssignments.filterKeys { it in handKeys })
    }

    /**
     * Every card currently placed in a player hand (all seats, all sources) — never the Mittelfeld.
     * This is the Necromancer's exclusion set: a card held by anyone (incl. the puller) can't be in
     * the discard. The Mittelfeld is handled separately by [getNecromancerCandidates] — as the
     * candidate pool once submitted, ignored while un-submitted — so it must not appear here (§6 #5).
     * (Contrast [buildCurrent]'s `usedByOthers`, which is per-hand and DOES include the Mittelfeld,
     * because the normal picker / scan must also keep discard cards out of a hand.)
     */
    private fun cardsInHands(): Set<String> = buildSet {
        drafts.asSequence()
            .filter { it.key != DISCARD_ID }
            .forEach { (_, d) -> d.slots.forEach { s -> (s as? CardSlot.Filled)?.card?.key?.let(::add) } }
        syncedCardsByProfile.asSequence()
            .filter { it.key != DISCARD_ID && it.key !in liveDraftsByUnit }
            .forEach { addAll(it.value) }
        liveDraftsByUnit.asSequence()
            .filter { it.key != DISCARD_ID }
            .forEach { addAll(it.value) }
    }

    /**
     * Necromancer pull candidates. Excludes every card in any hand; a scanned Mittelfeld then narrows
     * the pool to the discard itself (selection), an un-scanned one leaves the full eligible pool sans
     * hands (the discard plays no part until it is complete and submitted). §6 #5.
     */
    fun necromancerCandidates(): List<CardDefinition> =
        cardLookup.getNecromancerCandidates(
            handKeys = cardsInHands(),
            discardScanned = discardScanned,
            discardKeys = discardCards.map { it.key }.toSet(),
        )

    class Factory(
        private val cardLookup: CardLookup,
        private val handCardRepo: HandCardRepository,
        private val profileRepo: ProfileRepository,
        private val gameRepo: GameRepository,
        private val roundRepo: RoundRepository,
        private val settingsRepo: SettingsRepository,
        private val engine: ScoringEngine,
        private val optimalSolver: OptimalSolver,
        private val p2p: P2PSessionRepository,
        private val deviceUuidProvider: DeviceUuidProvider,
        private val roundId: String,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return RoundCaptureViewModel(
                cardLookup = cardLookup,
                handCardRepo = handCardRepo,
                profileRepo = profileRepo,
                gameRepo = gameRepo,
                roundRepo = roundRepo,
                settingsRepo = settingsRepo,
                engine = engine,
                optimalSolver = optimalSolver,
                p2p = p2p,
                deviceUuidProvider = deviceUuidProvider,
                roundId = roundId,
            ) as T
        }
    }
}
