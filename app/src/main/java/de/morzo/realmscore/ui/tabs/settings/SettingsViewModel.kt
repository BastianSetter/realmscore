package de.morzo.realmscore.ui.tabs.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import de.morzo.realmscore.data.db.AppDatabase
import de.morzo.realmscore.domain.model.AppLanguage
import de.morzo.realmscore.domain.model.Profile
import de.morzo.realmscore.domain.model.ThemeMode
import de.morzo.realmscore.domain.repository.ProfileRepository
import de.morzo.realmscore.domain.repository.SettingsRepository
import de.morzo.realmscore.domain.usecase.settings.ResetUseCase
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DataInfo(
    val openGamesCount: Int = 0,
    val closedGamesCount: Int = 0,
    val totalRoundsCount: Int = 0,
    val profilesCount: Int = 0,
)

data class SettingsUiState(
    val ownerProfile: Profile? = null,
    val appLanguage: AppLanguage = AppLanguage.SYSTEM,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val useDynamicColors: Boolean = true,
    val defaultPointLimit: Int = SettingsRepository.DEFAULT_POINT_LIMIT,
    val defaultRoundCount: Int = SettingsRepository.DEFAULT_ROUND_COUNT,
    val discardCaptureEnabled: Boolean = false,
    val dataInfo: DataInfo = DataInfo(),
)

class SettingsViewModel(
    private val settings: SettingsRepository,
    private val profileRepo: ProfileRepository,
    private val resetUseCase: ResetUseCase,
    db: AppDatabase,
) : ViewModel() {

    private val preferencesFlow = combine(
        settings.themeMode,
        settings.useDynamicColors,
        settings.defaultPointLimit,
        settings.defaultRoundCount,
        settings.discardCaptureEnabled,
    ) { mode, dyn, pts, rounds, discard ->
        PrefsSnapshot(mode, dyn, pts, rounds, discard)
    }

    private val dataInfoFlow = combine(
        db.gameDao().observeOpenGameCount(),
        db.gameDao().observeClosedGameCount(),
        db.roundDao().observeRoundCount(),
        db.profileDao().observeProfileCount(),
    ) { open, closed, rounds, profiles ->
        DataInfo(open, closed, rounds, profiles)
    }

    val uiState: StateFlow<SettingsUiState> = combine(
        profileRepo.observeLocalOwner(),
        preferencesFlow,
        dataInfoFlow,
        settings.appLanguage,
    ) { owner, prefs, dataInfo, language ->
        SettingsUiState(
            ownerProfile = owner,
            appLanguage = language,
            themeMode = prefs.themeMode,
            useDynamicColors = prefs.useDynamicColors,
            defaultPointLimit = prefs.defaultPointLimit,
            defaultRoundCount = prefs.defaultRoundCount,
            discardCaptureEnabled = prefs.discardCaptureEnabled,
            dataInfo = dataInfo,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SettingsUiState(),
    )

    fun setAppLanguage(lang: AppLanguage) {
        viewModelScope.launch { settings.setAppLanguage(lang) }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settings.setThemeMode(mode) }
    }

    fun setUseDynamicColors(value: Boolean) {
        viewModelScope.launch { settings.setUseDynamicColors(value) }
    }

    fun setDefaultPointLimit(value: Int) {
        viewModelScope.launch { settings.setDefaultPointLimit(value) }
    }

    fun setDefaultRoundCount(value: Int) {
        viewModelScope.launch { settings.setDefaultRoundCount(value) }
    }

    fun setDiscardCaptureEnabled(value: Boolean) {
        viewModelScope.launch { settings.setDiscardCaptureEnabled(value) }
    }

    fun clearGameData(onDone: () -> Unit = {}) {
        viewModelScope.launch {
            resetUseCase.clearGameData()
            onDone()
        }
    }

    fun resetApp(onDone: () -> Unit = {}) {
        viewModelScope.launch {
            resetUseCase.resetApp()
            onDone()
        }
    }

    private data class PrefsSnapshot(
        val themeMode: ThemeMode,
        val useDynamicColors: Boolean,
        val defaultPointLimit: Int,
        val defaultRoundCount: Int,
        val discardCaptureEnabled: Boolean,
    )

    class Factory(
        private val settings: SettingsRepository,
        private val profileRepo: ProfileRepository,
        private val resetUseCase: ResetUseCase,
        private val db: AppDatabase,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SettingsViewModel(settings, profileRepo, resetUseCase, db) as T
        }
    }
}
