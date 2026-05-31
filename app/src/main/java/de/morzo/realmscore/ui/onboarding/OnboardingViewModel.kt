package de.morzo.realmscore.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import de.morzo.realmscore.domain.repository.ProfileRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class OnboardingUiState(
    val name: String = "",
    val isSubmitting: Boolean = false,
    val error: String? = null,
)

class OnboardingViewModel(
    private val profileRepo: ProfileRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    fun onNameChange(name: String) {
        _uiState.update { it.copy(name = name, error = null) }
    }

    fun onContinue(onSuccess: () -> Unit) {
        val current = _uiState.value
        val trimmed = current.name.trim()
        if (trimmed.isEmpty() || current.isSubmitting) return

        _uiState.update { it.copy(isSubmitting = true, error = null) }
        viewModelScope.launch {
            try {
                profileRepo.createOwner(trimmed)
                onSuccess()
            } catch (t: Throwable) {
                _uiState.update {
                    it.copy(isSubmitting = false, error = t.message)
                }
            }
        }
    }

    class Factory(
        private val profileRepo: ProfileRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return OnboardingViewModel(profileRepo) as T
        }
    }
}
