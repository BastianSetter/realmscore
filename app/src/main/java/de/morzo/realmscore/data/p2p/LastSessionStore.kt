package de.morzo.realmscore.data.p2p

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.p2pSessionDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "p2p_session_prefs",
)

/**
 * Persists the last client session's reconnect coordinates (host MAC + [HandshakePayload]) across an
 * app restart so a dropped client can silently rejoin (Phase 28, §6 #2). Survives an app kill (unlike
 * the in-memory [SessionManager] state); deliberately *not* touched by [SessionManager.close] (which
 * fires on every new-game / join-screen visit) — only [save]d on a successful join and [clear]ed when
 * the joined game closes.
 */
class LastSessionStore(applicationContext: Context) {

    private val dataStore = applicationContext.p2pSessionDataStore

    suspend fun save(macAddress: String, payloadJson: String) {
        dataStore.edit { prefs ->
            prefs[KEY_MAC] = macAddress
            prefs[KEY_PAYLOAD] = payloadJson
        }
    }

    /** The stored reconnect info as the raw MAC + payload JSON, or null if none is saved. */
    suspend fun get(): StoredSession? {
        val prefs = dataStore.data.first()
        val mac = prefs[KEY_MAC] ?: return null
        val payloadJson = prefs[KEY_PAYLOAD] ?: return null
        return StoredSession(mac, payloadJson)
    }

    suspend fun clear() {
        dataStore.edit { prefs ->
            prefs.remove(KEY_MAC)
            prefs.remove(KEY_PAYLOAD)
        }
    }

    /** Raw persisted form; [SessionManager] decodes [payloadJson] into a [RejoinInfo]. */
    data class StoredSession(val macAddress: String, val payloadJson: String)

    companion object {
        private val KEY_MAC = stringPreferencesKey("last_host_mac")
        private val KEY_PAYLOAD = stringPreferencesKey("last_handshake_payload")
    }
}
