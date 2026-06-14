package de.morzo.realmscore.ui.sandbox.favorites

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import de.morzo.realmscore.domain.model.SandboxFavorite
import de.morzo.realmscore.domain.repository.SandboxFavoriteRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SandboxFavoritesUiState(
    val favorites: List<SandboxFavorite> = emptyList(),
    val loaded: Boolean = false,
)

class SandboxFavoritesViewModel(
    private val repo: SandboxFavoriteRepository,
) : ViewModel() {

    val uiState: StateFlow<SandboxFavoritesUiState> =
        repo.observeAll()
            .map { SandboxFavoritesUiState(favorites = it, loaded = true) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = SandboxFavoritesUiState(),
            )

    fun delete(id: String) {
        viewModelScope.launch { repo.delete(id) }
    }

    /** Renames a favorite (spec 25.6); blank clears the name back to the numbered default. */
    fun rename(id: String, name: String?) {
        viewModelScope.launch { repo.updateName(id, name) }
    }

    class Factory(
        private val repo: SandboxFavoriteRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
            return SandboxFavoritesViewModel(repo) as T
        }
    }
}
