package de.morzo.realmscore.ui.newgame

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import de.morzo.realmscore.domain.model.GameMode
import de.morzo.realmscore.domain.model.Profile
import de.morzo.realmscore.domain.p2p.P2PSessionRepository
import de.morzo.realmscore.domain.p2p.model.BluetoothStatus
import de.morzo.realmscore.domain.p2p.model.ParticipantInfo
import de.morzo.realmscore.domain.p2p.model.SessionState
import de.morzo.realmscore.domain.repository.GameRepository
import de.morzo.realmscore.domain.repository.ProfileRepository
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

data class ParticipantRow(
    val profileId: String,
    val name: String,
    val colorArgb: Int,
    val isOwner: Boolean,
    val originDeviceId: String,
)

enum class AddError {
    EMPTY_NAME,
    NAME_EXISTS,
    MAX_PLAYERS_REACHED,
}

data class NewGameUiState(
    val mode: GameMode = GameMode.FIXED_ROUNDS,
    val targetValueRounds: Int = DEFAULT_ROUNDS,
    val targetValuePoints: Int = DEFAULT_POINTS,
    val participants: List<ParticipantRow> = emptyList(),
    val ownerProfileId: String? = null,
    val addQuery: String = "",
    val suggestions: List<Profile> = emptyList(),
    val addError: AddError? = null,
    val isStarting: Boolean = false,
) {
    val targetValue: Int
        get() = when (mode) {
            GameMode.FIXED_ROUNDS -> targetValueRounds
            GameMode.POINT_LIMIT -> targetValuePoints
        }

    val canStart: Boolean
        get() = !isStarting &&
            participants.size in 2..6 &&
            targetValue > 0

    companion object {
        const val DEFAULT_ROUNDS = 3
        const val DEFAULT_POINTS = 1000
    }
}

@OptIn(FlowPreview::class)
class NewGameViewModel(
    private val profileRepo: ProfileRepository,
    private val gameRepo: GameRepository,
    private val p2p: P2PSessionRepository,
    /** When set, pre-fill players + settings from this previous game ("Neues Spiel starten"). */
    private val seedGameId: String = "",
    /** When true, keep the live host session alive and bring the joined phones into the next game. */
    private val continueSession: Boolean = false,
) : ViewModel() {

    private val _uiState = MutableStateFlow(NewGameUiState())
    val uiState: StateFlow<NewGameUiState> = _uiState.asStateFlow()

    private val queryFlow = MutableStateFlow("")
    private var suggestionsJob: Job? = null

    /** Live P2P session state (Phase 28): drives the inline "Für Beitritt öffnen" / QR section. */
    val sessionState: StateFlow<SessionState> = p2p.sessionState

    /** Provisional session id used while the game doesn't exist yet (roster-only in Stage A). */
    private val sessionGameId: String = UUID.randomUUID().toString()

    /**
     * This host's own device id (the local owner's `originDeviceId`), captured once at init. A locally
     * added player is *captured by the host*, so its roster row must carry this id — never the added
     * profile's stored `originDeviceId`, which for a profile synced in from another device is that
     * device's id and would (a) block that device from joining and (b) misdirect client seat-resolution.
     */
    private var hostDeviceId: String = ""

    fun bluetoothStatus(): BluetoothStatus = p2p.bluetoothStatus()

    /** Host: open the in-progress roster for joins. Safe to call once Bluetooth is ready. */
    fun openForJoins() {
        viewModelScope.launch {
            p2p.openForJoins(sessionGameId, currentParticipants())
        }
    }

    private fun currentParticipants(): List<ParticipantInfo> =
        _uiState.value.participants.mapIndexed { index, row ->
            ParticipantInfo(
                profileId = row.profileId,
                name = row.name,
                colorArgb = row.colorArgb,
                seatOrder = index,
                originDeviceId = row.originDeviceId,
            )
        }

    /** Push the current roster to joined clients — only when a host session is live. */
    private fun broadcastRoster() {
        if (p2p.sessionState.value is SessionState.Hosting) {
            viewModelScope.launch { p2p.broadcastParticipants(currentParticipants()) }
        }
    }

    init {
        // P2P session is a process-wide singleton; a prior *finished* game may have left it Hosting.
        // Reset it so a fresh new-game setup starts Idle (button shown again) instead of resurrecting
        // the old QR/code. But do NOT close a session that is still guarding an in-progress game — the
        // user may just be glancing at this screen mid-game (closing it would drop everyone) — and do
        // NOT close it when we're intentionally continuing with the same group ("Neues Spiel starten").
        if (!continueSession && !p2p.isInActiveGame()) p2p.close()
        // A fresh new game never inherits a previous session's joined players. close() above already
        // clears them, but it is skipped when an active game lingers (e.g. abandoned without a formal
        // close) — without this, stale joiners would occupy device slots and block the real join.
        if (!continueSession) p2p.resetJoinedRoster()
        viewModelScope.launch {
            val owner = profileRepo.getLocalOwner()
                ?: error("Local owner not found – onboarding must run first.")
            hostDeviceId = owner.originDeviceId
            _uiState.update { state ->
                state.copy(
                    ownerProfileId = owner.id,
                    participants = listOf(
                        ParticipantRow(
                            profileId = owner.id,
                            name = owner.name,
                            colorArgb = owner.colorArgb,
                            isOwner = true,
                            originDeviceId = owner.originDeviceId,
                        )
                    ),
                )
            }
            // "Neues Spiel starten": pre-fill the previous game's settings + players.
            if (seedGameId.isNotBlank()) seedFromPreviousGame(seedGameId, owner.id)
            // Now that the owner is known, pre-compute suggestions so the empty/on-focus picker
            // already has relevance-ranked entries to show without any typing.
            refreshSuggestions(_uiState.value.addQuery.trim())
        }
        suggestionsJob = viewModelScope.launch {
            queryFlow
                .debounce(150)
                .distinctUntilChanged()
                .collect { query ->
                    refreshSuggestions(query)
                }
        }
        // "Join adds a player": when a client announces itself, reconcile it to a local profile and
        // append it to the roster so the host can start with the joined player.
        viewModelScope.launch {
            p2p.joinedParticipants.collect { joined -> mergeJoinedParticipants(joined) }
        }
    }

    /**
     * Pre-fill the roster + settings from [seedGameId] (the just-finished game). Settings always carry
     * over. Players carry over too, except that when [continueSession] is set the device-mapped (remote)
     * seats are left out here — they repopulate from the still-live [P2PSessionRepository.joinedParticipants]
     * with their correct remote `originDeviceId`, which is what lets each client re-resolve its own seat.
     */
    private suspend fun seedFromPreviousGame(gameId: String, ownerId: String) {
        val game = gameRepo.getById(gameId) ?: return
        setMode(game.mode)
        when (game.mode) {
            GameMode.FIXED_ROUNDS -> game.targetRounds?.let { setTarget(it) }
            GameMode.POINT_LIMIT -> game.targetPoints?.let { setTarget(it) }
        }

        val seededRows = mutableListOf<ParticipantRow>()
        for (participant in gameRepo.getParticipants(gameId).sortedBy { it.seatOrder }) {
            if (participant.profileId == ownerId) continue
            val profile = profileRepo.getById(participant.profileId) ?: continue
            // Beim Weiterspielen mit der Gruppe werden Remote-Sitze (anderes Gerät) NICHT hier geseedet
            // — sie repopulieren live aus joinedParticipants mit korrekter remote-originDeviceId.
            if (continueSession && profile.deviceId != hostDeviceId) continue
            seededRows += ParticipantRow(
                profileId = profile.id,
                name = profile.name,
                colorArgb = profile.colorArgb,
                isOwner = false,
                originDeviceId = profile.originDeviceId,
            )
        }
        if (seededRows.isNotEmpty()) {
            _uiState.update { state ->
                val taken = state.participants.map { it.profileId }.toSet()
                state.copy(participants = state.participants + seededRows.filter { it.profileId !in taken })
            }
        }
        // Continuing a live host session: push the seeded roster so connected clients re-resolve seats.
        if (continueSession) broadcastRoster()
    }

    private suspend fun mergeJoinedParticipants(joined: List<ParticipantInfo>) {
        if (joined.isEmpty()) return
        val knownDeviceIds = _uiState.value.participants.mapNotNull { it.originDeviceId.ifBlank { null } }.toSet()
        val newcomers = joined.filter { it.originDeviceId.isNotBlank() && it.originDeviceId !in knownDeviceIds }
        if (newcomers.isEmpty()) return

        val rows = mutableListOf<ParticipantRow>()
        for (participant in newcomers) {
            // Phase 4 (Profil-Rework): das fremde Profil wird mit seiner global eindeutigen Identität
            // unverändert übernommen (kein Remap auf ein lokales Profil, kein DeviceProfileMapping).
            // Zwei verschiedene Geräte können nie dieselbe id liefern → kein Sitz-Kollaps möglich.
            val taken = (_uiState.value.participants.map { it.profileId } + rows.map { it.profileId }).toSet()
            if (participant.profileId in taken) continue
            runCatching {
                profileRepo.ensureRemoteProfile(
                    id = participant.profileId,
                    name = participant.name,
                    colorArgb = participant.colorArgb,
                    originDeviceId = participant.originDeviceId,
                )
            }.getOrNull() ?: continue
            rows += ParticipantRow(
                profileId = participant.profileId,
                name = participant.name,
                colorArgb = participant.colorArgb,
                isOwner = false,
                originDeviceId = participant.originDeviceId,
            )
        }
        if (rows.isEmpty()) return
        _uiState.update { state ->
            // Re-check inside the update in case the roster changed while resolving profiles.
            val existingDevices = state.participants.map { it.originDeviceId }.toSet()
            val existingProfiles = state.participants.map { it.profileId }.toSet()
            state.copy(
                participants = state.participants + rows.filter {
                    it.originDeviceId !in existingDevices && it.profileId !in existingProfiles
                },
            )
        }
        scheduleSuggestionRefresh()
        broadcastRoster()
    }

    fun setMode(mode: GameMode) {
        _uiState.update { it.copy(mode = mode) }
    }

    fun setTarget(value: Int) {
        val sanitized = value.coerceAtLeast(0)
        _uiState.update {
            when (it.mode) {
                GameMode.FIXED_ROUNDS -> it.copy(targetValueRounds = sanitized)
                GameMode.POINT_LIMIT -> it.copy(targetValuePoints = sanitized)
            }
        }
    }

    fun onQueryChange(query: String) {
        _uiState.update { it.copy(addQuery = query, addError = null) }
        queryFlow.value = query.trim()
    }

    fun addExistingProfile(profile: Profile) {
        val current = _uiState.value
        if (current.participants.any { it.profileId == profile.id }) return
        if (current.participants.size >= MAX_PLAYERS) {
            _uiState.update { it.copy(addError = AddError.MAX_PLAYERS_REACHED) }
            return
        }
        _uiState.update {
            it.copy(
                participants = it.participants + ParticipantRow(
                    profileId = profile.id,
                    name = profile.name,
                    colorArgb = profile.colorArgb,
                    isOwner = profile.id == it.ownerProfileId,
                    // Locally added → captured by this host device, not the profile's origin device.
                    originDeviceId = hostDeviceId.ifBlank { profile.originDeviceId },
                ),
                addQuery = "",
                suggestions = emptyList(),
                addError = null,
            )
        }
        queryFlow.value = ""
        scheduleSuggestionRefresh()
        broadcastRoster()
    }

    fun addNewProfile(rawName: String) {
        val name = rawName.trim()
        if (name.isEmpty()) {
            _uiState.update { it.copy(addError = AddError.EMPTY_NAME) }
            return
        }
        val current = _uiState.value
        if (current.participants.size >= MAX_PLAYERS) {
            _uiState.update { it.copy(addError = AddError.MAX_PLAYERS_REACHED) }
            return
        }
        viewModelScope.launch {
            // Namens-Eindeutigkeit gelockert (Phase 1): Duplikate erlaubt, Merges regeln Dubletten.
            val profile = profileRepo.createProfile(name)
            _uiState.update {
                it.copy(
                    participants = it.participants + ParticipantRow(
                        profileId = profile.id,
                        name = profile.name,
                        colorArgb = profile.colorArgb,
                        isOwner = false,
                        originDeviceId = profile.originDeviceId,
                    ),
                    addQuery = "",
                    suggestions = emptyList(),
                    addError = null,
                )
            }
            queryFlow.value = ""
            scheduleSuggestionRefresh()
            broadcastRoster()
        }
    }

    fun removeParticipant(profileId: String) {
        val current = _uiState.value
        if (profileId == current.ownerProfileId) return
        _uiState.update {
            it.copy(participants = it.participants.filterNot { p -> p.profileId == profileId })
        }
        scheduleSuggestionRefresh()
        broadcastRoster()
    }

    /**
     * Starts the game. Without a live host session this behaves as before: [onGameStarted] navigates to
     * the in-progress game hub. **While hosting a P2P session** (Stage B), it instead mints round 1,
     * distributes the game to all joined devices and routes the host straight into round capture via
     * [onSharedRoundStarted] — every phone enters the same round together on shared UUIDs.
     */
    fun startGame(
        onGameStarted: (gameId: String) -> Unit,
        onSharedRoundStarted: (roundId: String) -> Unit,
    ) {
        val current = _uiState.value
        if (!current.canStart || current.isStarting) return
        _uiState.update { it.copy(isStarting = true) }
        viewModelScope.launch {
            try {
                val game = gameRepo.startGame(
                    mode = current.mode,
                    target = current.targetValue,
                    participantProfileIds = current.participants.map { it.profileId },
                )
                if (p2p.sessionState.value is SessionState.Hosting) {
                    p2p.startSharedSession(game.id)
                        .onSuccess { roundId -> onSharedRoundStarted(roundId) }
                        // Distribution failed (e.g. a client dropped mid-start): fall back to solo flow
                        // so the host can still play; clients simply won't follow.
                        .onFailure { onGameStarted(game.id) }
                } else {
                    onGameStarted(game.id)
                }
            } catch (t: Throwable) {
                _uiState.update { it.copy(isStarting = false) }
            }
        }
    }

    private suspend fun refreshSuggestions(query: String) {
        val state = _uiState.value
        val ownerId = state.ownerProfileId
        if (ownerId == null) {
            _uiState.update { it.copy(suggestions = emptyList()) }
            return
        }
        val takenIds = state.participants.map { it.profileId }.toSet()
        val matches = profileRepo.suggestProfiles(
            prefix = query,
            excludeProfileIds = takenIds,
            ownerId = ownerId,
        )
        _uiState.update { it.copy(suggestions = matches) }
    }

    /** Recompute suggestions for the current query off the back of a participant-list change. */
    private fun scheduleSuggestionRefresh() {
        viewModelScope.launch { refreshSuggestions(_uiState.value.addQuery.trim()) }
    }

    companion object {
        const val MAX_PLAYERS = 6
    }

    class Factory(
        private val profileRepo: ProfileRepository,
        private val gameRepo: GameRepository,
        private val p2p: P2PSessionRepository,
        private val seedGameId: String = "",
        private val continueSession: Boolean = false,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return NewGameViewModel(
                profileRepo, gameRepo, p2p, seedGameId, continueSession,
            ) as T
        }
    }
}
