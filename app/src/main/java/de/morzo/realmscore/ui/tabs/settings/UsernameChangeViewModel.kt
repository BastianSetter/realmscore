package de.morzo.realmscore.ui.tabs.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import de.morzo.realmscore.domain.repository.ProfileRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class UsernameChangeError { EMPTY, EXISTS, UNKNOWN }

data class UsernameChangeUiState(
    val initialName: String = "",
    val name: String = "",
    val isSubmitting: Boolean = false,
    val error: UsernameChangeError? = null,
    val isLoaded: Boolean = false,
)

class UsernameChangeViewModel(
    private val profileRepo: ProfileRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(UsernameChangeUiState())
    val uiState: StateFlow<UsernameChangeUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val owner = profileRepo.getLocalOwner()
            val name = owner?.name.orEmpty()
            _uiState.update {
                it.copy(initialName = name, name = name, isLoaded = true)
            }
        }
    }

    fun onNameChange(name: String) {
        _uiState.update { it.copy(name = name, error = null) }
    }

    fun onSubmit(onSuccess: () -> Unit) {
        val current = _uiState.value
        val trimmed = current.name.trim()
        if (current.isSubmitting) return
        if (trimmed.isEmpty()) {
            _uiState.update { it.copy(error = UsernameChangeError.EMPTY) }
            return
        }
        if (trimmed == current.initialName) {
            onSuccess()
            return
        }
        _uiState.update { it.copy(isSubmitting = true, error = null) }
        viewModelScope.launch {
            try {
                profileRepo.updateOwnerName(trimmed)
                onSuccess()
            } catch (t: IllegalArgumentException) {
                _uiState.update {
                    it.copy(isSubmitting = false, error = UsernameChangeError.EXISTS)
                }
            } catch (t: Throwable) {
                _uiState.update {
                    it.copy(isSubmitting = false, error = UsernameChangeError.UNKNOWN)
                }
            }
        }
    }

    class Factory(
        private val profileRepo: ProfileRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return UsernameChangeViewModel(profileRepo) as T
        }
    }
}
