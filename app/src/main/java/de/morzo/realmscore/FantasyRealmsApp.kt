package de.morzo.realmscore

import android.app.Application
import de.morzo.realmscore.di.AppContainer

class FantasyRealmsApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(applicationContext)
    }
}
