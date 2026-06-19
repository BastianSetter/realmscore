package de.morzo.realmscore.ui

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import de.morzo.realmscore.FantasyRealmsApp
import de.morzo.realmscore.domain.model.AppLanguage
import de.morzo.realmscore.domain.model.ThemeMode
import de.morzo.realmscore.ui.nav.AppNavHost
import de.morzo.realmscore.ui.theme.FantasyRealmsTheme
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.Locale

class MainActivity : ComponentActivity() {

    // The language the localized base context was built from in attachBaseContext(). A Configuration
    // override is fixed at attach time, so a later settings change can only take effect by
    // recreating the Activity — this field lets onCreate() detect that case.
    private var appliedLanguage: AppLanguage = AppLanguage.SYSTEM

    override fun attachBaseContext(newBase: Context) {
        // Apply the chosen language so EVERY window the Activity spawns — Dialog, ModalBottomSheet,
        // Popup, DropdownMenu — inherits it (spec 25.7, Ursache A).
        //
        // We do this via applyOverrideConfiguration() rather than replacing the base context with
        // createConfigurationContext(): the latter detaches the base context's *outer context* from
        // the Activity, which breaks system services that cast their context back to an Activity
        // (CompanionDeviceManager.associate(), Phase 28, crashed with ClassCastException). Keeping the
        // system-provided base context and only overriding its configuration fixes that while still
        // overriding the locale for the Activity and all the windows it creates.
        // settingsRepository stays the single source of truth; reading it once synchronously here is
        // acceptable for a one-shot startup read of a tiny DataStore value.
        val settings = (newBase.applicationContext as FantasyRealmsApp).container.settingsRepository
        val language = runBlocking { settings.appLanguage.first() }
        appliedLanguage = language
        super.attachBaseContext(newBase)
        language.toLocaleOrNull()?.let { locale ->
            applyOverrideConfiguration(Configuration().apply { setLocale(locale) })
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        val container = (application as FantasyRealmsApp).container

        // Recreate when the language actually changes. The flow re-emits its current value first,
        // which already equals appliedLanguage, so the initial emission is a no-op.
        lifecycleScope.launch {
            container.settingsRepository.appLanguage
                .distinctUntilChanged()
                .collect { language ->
                    if (language != appliedLanguage) recreate()
                }
        }

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

/** The explicit [Locale] for [language], or null to follow the device locale ([AppLanguage.SYSTEM]). */
private fun AppLanguage.toLocaleOrNull(): Locale? = when (this) {
    AppLanguage.SYSTEM -> null
    AppLanguage.GERMAN -> Locale.forLanguageTag("de")
    AppLanguage.ENGLISH -> Locale.forLanguageTag("en")
}
