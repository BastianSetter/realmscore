package de.morzo.realmscore.data.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import java.util.UUID

private val Context.devicePreferencesDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "device_prefs"
)

class DeviceUuidProvider(private val applicationContext: Context) {

    private val dataStore = applicationContext.devicePreferencesDataStore

    suspend fun get(): String {
        val existing = dataStore.data.first()[KEY_DEVICE_UUID]
        if (existing != null) return existing

        val generated = UUID.randomUUID().toString()
        dataStore.edit { prefs ->
            val raced = prefs[KEY_DEVICE_UUID]
            if (raced == null) {
                prefs[KEY_DEVICE_UUID] = generated
            }
        }
        return dataStore.data.first()[KEY_DEVICE_UUID] ?: generated
    }

    suspend fun clear() {
        dataStore.edit { it.remove(KEY_DEVICE_UUID) }
    }

    companion object {
        private val KEY_DEVICE_UUID = stringPreferencesKey("device_uuid")
    }
}
