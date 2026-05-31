package de.morzo.realmscore.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import de.morzo.realmscore.FantasyRealmsApp
import de.morzo.realmscore.domain.model.ThemeMode
import de.morzo.realmscore.ui.nav.AppNavHost
import de.morzo.realmscore.ui.theme.FantasyRealmsTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        val container = (application as FantasyRealmsApp).container
        setContent {
            val themeMode by container.settingsRepository.themeMode
                .collectAsState(initial = ThemeMode.SYSTEM)
            val useDynamicColors by container.settingsRepository.useDynamicColors
                .collectAsState(initial = true)
            FantasyRealmsTheme(
                themeMode = themeMode,
                useDynamicColors = useDynamicColors,
            ) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppNavHost(container = container)
                }
            }
        }
    }
}
