package de.morzo.realmscore.domain.repository

import de.morzo.realmscore.domain.model.ThemeMode
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {

    val lastRandomStatKey: Flow<String?>
    suspend fun setLastRandomStatKey(key: String?)

    val themeMode: Flow<ThemeMode>
    suspend fun setThemeMode(mode: ThemeMode)

    val useDynamicColors: Flow<Boolean>
    suspend fun setUseDynamicColors(value: Boolean)

    val defaultPointLimit: Flow<Int>
    suspend fun setDefaultPointLimit(value: Int)

    val defaultRoundCount: Flow<Int>
    suspend fun setDefaultRoundCount(value: Int)

    val suggestDiscardScan: Flow<Boolean>
    suspend fun setSuggestDiscardScan(value: Boolean)

    suspend fun clearAll()

    companion object {
        const val DEFAULT_POINT_LIMIT = 1000
        const val DEFAULT_ROUND_COUNT = 3
    }
}
