package de.morzo.realmscore.ui.newgame

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import de.morzo.realmscore.domain.model.GameMode
import de.morzo.realmscore.domain.model.Profile
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

data class ParticipantRow(
    val profileId: String,
    val name: String,
    val colorArgb: Int,
    val isOwner: Boolean,
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
) : ViewModel() {

    private val _uiState = MutableStateFlow(NewGameUiState())
    val uiState: StateFlow<NewGameUiState> = _uiState.asStateFlow()

    private val queryFlow = MutableStateFlow("")
    private var suggestionsJob: Job? = null

    init {
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
                ),
                addQuery = "",
                suggestions = emptyList(),
                addError = null,
            )
        }
        queryFlow.value = ""
        scheduleSuggestionRefresh()
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
                    ),
                    addQuery = "",
                    suggestions = emptyList(),
                    addError = null,
                )
            }
            queryFlow.value = ""
            scheduleSuggestionRefresh()
        }
    }

    fun removeParticipant(profileId: String) {
        val current = _uiState.value
        if (profileId == current.ownerProfileId) return
        _uiState.update {
            it.copy(participants = it.participants.filterNot { p -> p.profileId == profileId })
        }
        scheduleSuggestionRefresh()
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
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return NewGameViewModel(profileRepo, gameRepo) as T
        }
    }
}
