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
import de.morzo.realmscore.domain.repository.DeviceProfileMappingRepository
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
    private val mappingRepo: DeviceProfileMappingRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(NewGameUiState())
    val uiState: StateFlow<NewGameUiState> = _uiState.asStateFlow()

    private val queryFlow = MutableStateFlow("")
    private var suggestionsJob: Job? = null

    /** Live P2P session state (Phase 28): drives the inline "Für Beitritt öffnen" / QR section. */
    val sessionState: StateFlow<SessionState> = p2p.sessionState

    /** Provisional session id used while the game doesn't exist yet (roster-only in Stage A). */
    private val sessionGameId: String = UUID.randomUUID().toString()

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
        // P2P session is a process-wide singleton; a prior game may have left it Hosting. Reset it so
        // a fresh new-game setup starts Idle (button shown again) instead of resurrecting the old QR/
        // code — and so the next openForJoins mints a fresh code + re-triggers the discoverable flow.
        p2p.close()
        viewModelScope.launch {
            val owner = profileRepo.getLocalOwner()
                ?: error("Local owner not found – onboarding must run first.")
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

    private suspend fun mergeJoinedParticipants(joined: List<ParticipantInfo>) {
        if (joined.isEmpty()) return
        val knownDeviceIds = _uiState.value.participants.mapNotNull { it.originDeviceId.ifBlank { null } }.toSet()
        val newcomers = joined.filter { it.originDeviceId !in knownDeviceIds }
        if (newcomers.isEmpty()) return

        val rows = newcomers.map { participant ->
            val localProfileId = resolveLocalProfile(participant)
            ParticipantRow(
                profileId = localProfileId,
                name = participant.name,
                colorArgb = participant.colorArgb,
                isOwner = false,
                originDeviceId = participant.originDeviceId,
            )
        }
        _uiState.update { state ->
            // Re-check inside the update in case the roster changed while resolving profiles.
            val existing = state.participants.map { it.originDeviceId }.toSet()
            state.copy(participants = state.participants + rows.filter { it.originDeviceId !in existing })
        }
        scheduleSuggestionRefresh()
        broadcastRoster()
    }

    /**
     * Map a joined device to a local profile id (host-side reconciliation). Reuses a remembered
     * mapping when present; otherwise mirrors the remote player as a new local profile and records the
     * mapping so the same device maps to the same profile in future sessions.
     */
    private suspend fun resolveLocalProfile(participant: ParticipantInfo): String {
        mappingRepo.getProfileFor(participant.originDeviceId)?.let { mapped ->
            if (profileRepo.getById(mapped) != null) return mapped
        }
        val created = profileRepo.createProfile(participant.name)
        profileRepo.updateColor(created.id, participant.colorArgb)
        mappingRepo.map(participant.originDeviceId, created.id)
        return created.id
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
            if (profileRepo.existsByName(name)) {
                _uiState.update { it.copy(addError = AddError.NAME_EXISTS) }
                return@launch
            }
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

    fun startGame(onSuccess: (String) -> Unit) {
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
                onSuccess(game.id)
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
        private val mappingRepo: DeviceProfileMappingRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return NewGameViewModel(profileRepo, gameRepo, p2p, mappingRepo) as T
        }
    }
}
