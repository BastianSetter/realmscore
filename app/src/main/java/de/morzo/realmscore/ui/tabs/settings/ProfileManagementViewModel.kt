package de.morzo.realmscore.ui.tabs.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import de.morzo.realmscore.domain.model.Profile
import de.morzo.realmscore.domain.repository.ProfileRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProfileRow(
    val profile: Profile,
    val gameCount: Int,
)

data class ProfileManagementUiState(
    val active: List<ProfileRow> = emptyList(),
    val archived: List<ProfileRow> = emptyList(),
    val isLoaded: Boolean = false,
)

enum class RenameResult { SUCCESS, EMPTY, EXISTS, ERROR }

class ProfileManagementViewModel(
    private val repo: ProfileRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileManagementUiState())
    val uiState: StateFlow<ProfileManagementUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                repo.observeActiveProfiles(),
                repo.observeArchivedProfiles(),
            ) { active, archived -> active to archived }
                .collect { (active, archived) ->
                    val activeRows = active.map { ProfileRow(it, repo.countGamesForProfile(it.id)) }
                    val archivedRows = archived.map { ProfileRow(it, repo.countGamesForProfile(it.id)) }
                    _uiState.update {
                        it.copy(active = activeRows, archived = archivedRows, isLoaded = true)
                    }
                }
        }
    }

    fun rename(profile: Profile, newName: String, onResult: (RenameResult) -> Unit) {
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) {
            onResult(RenameResult.EMPTY)
            return
        }
        viewModelScope.launch {
            val isSameName = trimmed.lowercase() == profile.name.lowercase()
            if (!isSameName && repo.existsByName(trimmed)) {
                onResult(RenameResult.EXISTS)
                return@launch
            }
            try {
                repo.updateName(profile.id, trimmed)
                onResult(RenameResult.SUCCESS)
            } catch (t: Throwable) {
                onResult(RenameResult.ERROR)
            }
        }
    }

    fun changeColor(profileId: String, colorArgb: Int) {
        viewModelScope.launch { repo.updateColor(profileId, colorArgb) }
    }

    fun archive(profileId: String) {
        viewModelScope.launch { repo.archiveProfile(profileId) }
    }

    fun unarchive(profileId: String) {
        viewModelScope.launch { repo.unarchiveProfile(profileId) }
    }

    fun merge(keepId: String, discardId: String) {
        viewModelScope.launch { repo.mergeProfiles(keepId, discardId) }
    }

    /** Lädt die Gesamtzahl der Spiele nach dem Merge (Vereinigung, ohne Doppelzählung). */
    fun loadCombinedGameCount(keepId: String, discardId: String, onLoaded: (Int) -> Unit) {
        viewModelScope.launch { onLoaded(repo.countCombinedGames(keepId, discardId)) }
    }

    class Factory(
        private val repo: ProfileRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ProfileManagementViewModel(repo) as T
        }
    }
}
