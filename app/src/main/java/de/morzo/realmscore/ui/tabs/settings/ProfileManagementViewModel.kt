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
import kotlinx.coroutines.launch

data class ProfileRow(
    val profile: Profile,
    val gameCount: Int,
    /** Für gemergte Profile: Anzeigename des kanonischen Ziels (null wenn unbekannt). */
    val mergeTargetName: String? = null,
)

data class ProfileManagementUiState(
    val owner: ProfileRow? = null,
    val active: List<ProfileRow> = emptyList(),
    val merged: List<ProfileRow> = emptyList(),
    val archived: List<ProfileRow> = emptyList(),
    val isLoaded: Boolean = false,
)

enum class RenameResult { SUCCESS, EMPTY, ERROR }

class ProfileManagementViewModel(
    private val repo: ProfileRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileManagementUiState())
    val uiState: StateFlow<ProfileManagementUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            // Vier disjunkte Buckets (Profil-Rework): Owner / Aktiv / Merged / Archiviert.
            combine(
                repo.observeLocalOwner(),
                repo.observeActiveProfiles(),
                repo.observeMergedProfiles(),
                repo.observeArchivedProfiles(),
            ) { owner, active, merged, archived ->
                val nameById = buildMap {
                    owner?.let { put(it.id, it.name) }
                    active.forEach { put(it.id, it.name) }
                    merged.forEach { put(it.id, it.name) }
                    archived.forEach { put(it.id, it.name) }
                }
                ProfileManagementUiState(
                    owner = owner?.let { ProfileRow(it, repo.countGamesForProfile(it.id)) },
                    active = active.map { ProfileRow(it, repo.countGamesForProfile(it.id)) },
                    merged = merged.map {
                        ProfileRow(
                            profile = it,
                            gameCount = repo.countGamesForProfile(it.id),
                            mergeTargetName = it.mergeTargetId?.let { t -> nameById[t] },
                        )
                    },
                    archived = archived.map { ProfileRow(it, repo.countGamesForProfile(it.id)) },
                    isLoaded = true,
                )
            }.collect { state -> _uiState.value = state }
        }
    }

    fun rename(profile: Profile, newName: String, onResult: (RenameResult) -> Unit) {
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) {
            onResult(RenameResult.EMPTY)
            return
        }
        viewModelScope.launch {
            try {
                // Namens-Eindeutigkeit gelockert (Phase 1): Duplikate erlaubt.
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

    /** Verschmilzt [sourceId] non-destruktiv in [targetId] (Zeiger-Merge). */
    fun setMergeTarget(sourceId: String, targetId: String) {
        viewModelScope.launch { repo.setMergeTarget(sourceId, targetId) }
    }

    /** Hebt einen Merge wieder auf. */
    fun unmerge(profileId: String) {
        viewModelScope.launch { repo.clearMergeTarget(profileId) }
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
