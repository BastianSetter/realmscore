package de.morzo.realmscore.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import de.morzo.realmscore.domain.model.AppLanguage
import de.morzo.realmscore.domain.repository.ProfileRepository
import de.morzo.realmscore.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class OnboardingUiState(
    val name: String = "",
    val language: AppLanguage = AppLanguage.SYSTEM,
    val isSubmitting: Boolean = false,
    val error: String? = null,
)

class OnboardingViewModel(
    private val profileRepo: ProfileRepository,
    private val settingsRepo: SettingsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    init {
        // Keep the selected flag in sync with the persisted setting; selecting a language
        // writes through here and MainActivity re-localizes the whole tree immediately.
        viewModelScope.launch {
            settingsRepo.appLanguage.collect { lang ->
                _uiState.update { it.copy(language = lang) }
            }
        }
    }

    fun onNameChange(name: String) {
        _uiState.update { it.copy(name = name, error = null) }
    }

    fun onLanguageSelected(lang: AppLanguage) {
        viewModelScope.launch { settingsRepo.setAppLanguage(lang) }
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
        private val settingsRepo: SettingsRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return OnboardingViewModel(profileRepo, settingsRepo) as T
        }
    }
}
