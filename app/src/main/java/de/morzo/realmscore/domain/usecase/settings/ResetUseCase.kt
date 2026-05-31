package de.morzo.realmscore.domain.usecase.settings

import androidx.room.withTransaction
import de.morzo.realmscore.data.datastore.DeviceUuidProvider
import de.morzo.realmscore.data.db.AppDatabase
import de.morzo.realmscore.domain.repository.SettingsRepository

class ResetUseCase(
    private val db: AppDatabase,
    private val deviceUuidProvider: DeviceUuidProvider,
    private val settings: SettingsRepository,
) {
    suspend fun clearGameData() {
        db.withTransaction {
            db.handCardDao().deleteAll()
            db.roundResultDao().deleteAll()
            db.roundDao().deleteAll()
            db.gameDao().deleteAll()
        }
    }

    suspend fun resetApp() {
        clearGameData()
        db.profileDao().deleteAll()
        deviceUuidProvider.clear()
        settings.clearAll()
    }
}
