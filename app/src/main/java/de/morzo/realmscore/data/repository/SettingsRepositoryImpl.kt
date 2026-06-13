package de.morzo.realmscore.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import de.morzo.realmscore.domain.model.AppLanguage
import de.morzo.realmscore.domain.model.ThemeMode
import de.morzo.realmscore.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "settings_prefs",
)

class SettingsRepositoryImpl(applicationContext: Context) : SettingsRepository {

    private val dataStore = applicationContext.settingsDataStore

    override val lastRandomStatKey: Flow<String?> =
        dataStore.data.map { prefs -> prefs[KEY_LAST_RANDOM_STAT] }

    override suspend fun setLastRandomStatKey(key: String?) {
        dataStore.edit { prefs ->
            if (key == null) prefs.remove(KEY_LAST_RANDOM_STAT)
            else prefs[KEY_LAST_RANDOM_STAT] = key
        }
    }

    override val appLanguage: Flow<AppLanguage> =
        dataStore.data.map { prefs -> AppLanguage.fromName(prefs[KEY_APP_LANGUAGE]) }

    override suspend fun setAppLanguage(lang: AppLanguage) {
        dataStore.edit { prefs -> prefs[KEY_APP_LANGUAGE] = lang.name }
    }

    override val themeMode: Flow<ThemeMode> =
        dataStore.data.map { prefs -> ThemeMode.fromName(prefs[KEY_THEME_MODE]) }

    override suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { prefs -> prefs[KEY_THEME_MODE] = mode.name }
    }

    override val useDynamicColors: Flow<Boolean> =
        dataStore.data.map { prefs -> prefs[KEY_USE_DYNAMIC_COLORS] ?: true }

    override suspend fun setUseDynamicColors(value: Boolean) {
        dataStore.edit { prefs -> prefs[KEY_USE_DYNAMIC_COLORS] = value }
    }

    override val defaultPointLimit: Flow<Int> =
        dataStore.data.map { prefs ->
            prefs[KEY_DEFAULT_POINT_LIMIT] ?: SettingsRepository.DEFAULT_POINT_LIMIT
        }

    override suspend fun setDefaultPointLimit(value: Int) {
        dataStore.edit { prefs -> prefs[KEY_DEFAULT_POINT_LIMIT] = value.coerceAtLeast(1) }
    }

    override val defaultRoundCount: Flow<Int> =
        dataStore.data.map { prefs ->
            prefs[KEY_DEFAULT_ROUND_COUNT] ?: SettingsRepository.DEFAULT_ROUND_COUNT
        }

    override suspend fun setDefaultRoundCount(value: Int) {
        dataStore.edit { prefs -> prefs[KEY_DEFAULT_ROUND_COUNT] = value.coerceAtLeast(1) }
    }

    override val discardCaptureEnabled: Flow<Boolean> =
        dataStore.data.map { prefs -> prefs[KEY_DISCARD_CAPTURE_ENABLED] ?: false }

    override suspend fun setDiscardCaptureEnabled(value: Boolean) {
        dataStore.edit { prefs -> prefs[KEY_DISCARD_CAPTURE_ENABLED] = value }
    }

    override val pickerSearchEnabled: Flow<Boolean> =
        dataStore.data.map { prefs -> prefs[KEY_PICKER_SEARCH_ENABLED] ?: true }

    override suspend fun setPickerSearchEnabled(value: Boolean) {
        dataStore.edit { prefs -> prefs[KEY_PICKER_SEARCH_ENABLED] = value }
    }

    override suspend fun clearAll() {
        dataStore.edit { it.clear() }
    }

    companion object {
        private val KEY_LAST_RANDOM_STAT = stringPreferencesKey("last_random_stat_key")
        private val KEY_APP_LANGUAGE = stringPreferencesKey("app_language")
        private val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        private val KEY_USE_DYNAMIC_COLORS = booleanPreferencesKey("use_dynamic_colors")
        private val KEY_DEFAULT_POINT_LIMIT = intPreferencesKey("default_point_limit")
        private val KEY_DEFAULT_ROUND_COUNT = intPreferencesKey("default_round_count")
        private val KEY_DISCARD_CAPTURE_ENABLED = booleanPreferencesKey("discard_capture_enabled")
        private val KEY_PICKER_SEARCH_ENABLED = booleanPreferencesKey("picker_search_enabled")
    }
}
