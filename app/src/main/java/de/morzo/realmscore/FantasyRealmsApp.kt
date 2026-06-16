package de.morzo.realmscore

import android.app.Application
import de.morzo.realmscore.di.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class FantasyRealmsApp : Application() {

    lateinit var container: AppContainer
        private set

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(applicationContext)
        // Phase 26: warm up the OCR engine off the main thread so the first camera scan is responsive.
        // Harmless if the feature is never used.
        applicationScope.launch { container.cardScanner.warmUp() }
    }
}
