package de.morzo.realmscore.ui.tabs.settings

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import de.morzo.realmscore.R
import de.morzo.realmscore.data.db.AppDatabase
import de.morzo.realmscore.domain.backup.BackupSchemaTooNewException
import de.morzo.realmscore.domain.backup.ImportResult
import de.morzo.realmscore.domain.model.AppLanguage
import de.morzo.realmscore.domain.model.Profile
import de.morzo.realmscore.domain.model.ThemeMode
import de.morzo.realmscore.domain.repository.BackupRepository
import de.morzo.realmscore.domain.repository.ProfileRepository
import de.morzo.realmscore.domain.repository.SettingsRepository
import de.morzo.realmscore.domain.usecase.settings.ResetUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate

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

/** One-shot results of a backup export/import, surfaced to the UI as a snackbar or share-sheet. */
sealed interface BackupEvent {
    /** Export succeeded; the UI should launch a share-sheet for [uri]. */
    data class ShareFile(val uri: Uri) : BackupEvent
    data object ExportFailed : BackupEvent
    data class ImportSucceeded(val result: ImportResult) : BackupEvent
    data object ImportSchemaTooNew : BackupEvent
    data object ImportInvalid : BackupEvent
}

class SettingsViewModel(
    private val settings: SettingsRepository,
    private val profileRepo: ProfileRepository,
    private val resetUseCase: ResetUseCase,
    private val backupRepo: BackupRepository,
    db: AppDatabase,
) : ViewModel() {

    private val backupEvents = Channel<BackupEvent>(Channel.BUFFERED)
    val events = backupEvents.receiveAsFlow()

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

    /**
     * Builds the backup JSON, writes it to a temp file in [cacheDir/backups] and emits a
     * [BackupEvent.ShareFile] so the UI can open the Android share-sheet.
     */
    fun exportBackup(context: Context) {
        val appContext = context.applicationContext
        viewModelScope.launch {
            val uri = runCatching {
                val appVersion = appVersionOf(appContext)
                val json = backupRepo.exportToJson(appVersion)
                withContext(Dispatchers.IO) {
                    val dir = File(appContext.cacheDir, "backups").apply { mkdirs() }
                    val fileName = appContext.getString(
                        R.string.backup_filename,
                        LocalDate.now().toString(),
                    )
                    val file = File(dir, fileName)
                    file.writeText(json)
                    FileProvider.getUriForFile(
                        appContext,
                        "${appContext.packageName}.fileprovider",
                        file,
                    )
                }
            }.getOrNull()

            backupEvents.send(
                if (uri != null) BackupEvent.ShareFile(uri) else BackupEvent.ExportFailed,
            )
        }
    }

    /** Reads the picked file, imports it, and emits a success/failure [BackupEvent]. */
    fun importBackup(context: Context, uri: Uri) {
        val appContext = context.applicationContext
        viewModelScope.launch {
            val event = try {
                val json = withContext(Dispatchers.IO) {
                    appContext.contentResolver.openInputStream(uri)?.use { stream ->
                        stream.bufferedReader().readText()
                    }
                } ?: throw IllegalStateException("Could not open backup file")
                val result = backupRepo.importFromJson(json)
                BackupEvent.ImportSucceeded(result)
            } catch (e: BackupSchemaTooNewException) {
                BackupEvent.ImportSchemaTooNew
            } catch (e: Exception) {
                BackupEvent.ImportInvalid
            }
            backupEvents.send(event)
        }
    }

    private fun appVersionOf(context: Context): String = runCatching {
        @Suppress("DEPRECATION")
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull() ?: "?"

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
        private val backupRepo: BackupRepository,
        private val db: AppDatabase,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SettingsViewModel(settings, profileRepo, resetUseCase, backupRepo, db) as T
        }
    }
}
