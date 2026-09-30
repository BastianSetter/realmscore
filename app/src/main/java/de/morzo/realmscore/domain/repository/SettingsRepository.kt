package de.morzo.realmscore.domain.repository

import de.morzo.realmscore.domain.model.AppLanguage
import de.morzo.realmscore.domain.model.ThemeMode
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {

    val lastRandomStatKey: Flow<String?>
    suspend fun setLastRandomStatKey(key: String?)

    val appLanguage: Flow<AppLanguage>
    suspend fun setAppLanguage(lang: AppLanguage)

    val themeMode: Flow<ThemeMode>
    suspend fun setThemeMode(mode: ThemeMode)

    val useDynamicColors: Flow<Boolean>
    suspend fun setUseDynamicColors(value: Boolean)

    val defaultPointLimit: Flow<Int>
    suspend fun setDefaultPointLimit(value: Int)

    val defaultRoundCount: Flow<Int>
    suspend fun setDefaultRoundCount(value: Int)

    /** When true, the central discard pile (Mittelfeld) is captured like a player each round. */
    val discardCaptureEnabled: Flow<Boolean>
    suspend fun setDiscardCaptureEnabled(value: Boolean)

    /**
     * Phase 30: defaults for a new game's expansion switches ("Der verfluchte Schatz"). The actual
     * flags are chosen per game on the New-Game screen and stored on the game.
     */
    val defaultCursedItemsEnabled: Flow<Boolean>
    suspend fun setDefaultCursedItemsEnabled(value: Boolean)

    val defaultNewSuitsEnabled: Flow<Boolean>
    suspend fun setDefaultNewSuitsEnabled(value: Boolean)

    /** When true, the embedded KartenPick card picker shows its text-search field (default on). */
    val pickerSearchEnabled: Flow<Boolean>
    suspend fun setPickerSearchEnabled(value: Boolean)

    /**
     * When true, an empty hand opens the camera card scan (Phase 26) instead of the manual KartenPick;
     * the user can still drop to manual entry. Default off.
     */
    val cameraScanEnabled: Flow<Boolean>
    suspend fun setCameraScanEnabled(value: Boolean)

    /**
     * When true, the camera scan matches banners against stored bitmap templates (Phase 29) instead
     * of OCR. Experimental; only meaningful while [cameraScanEnabled] is on. Default off.
     */
    val bitmapMatchingEnabled: Flow<Boolean>
    suspend fun setBitmapMatchingEnabled(value: Boolean)

    suspend fun clearAll()

    companion object {
        const val DEFAULT_POINT_LIMIT = 1000
        const val DEFAULT_ROUND_COUNT = 3
    }
}
