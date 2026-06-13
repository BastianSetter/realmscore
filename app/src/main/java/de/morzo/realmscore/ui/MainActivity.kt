package de.morzo.realmscore.ui

import android.content.res.Configuration
import android.content.res.Resources
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import de.morzo.realmscore.FantasyRealmsApp
import de.morzo.realmscore.domain.model.AppLanguage
import de.morzo.realmscore.domain.model.ThemeMode
import de.morzo.realmscore.ui.nav.AppNavHost
import de.morzo.realmscore.ui.theme.FantasyRealmsTheme
import java.util.Locale

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
            val language by container.settingsRepository.appLanguage
                .collectAsState(initial = AppLanguage.SYSTEM)

            // Re-localize the whole tree when the user picks a language. Providing both
            // LocalContext and LocalConfiguration keeps stringResource() and LocalConfiguration
            // (used by card-name display helpers) in sync, so the switch is immediate.
            LocalizedContent(language) {
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
}

@Composable
private fun LocalizedContent(
    language: AppLanguage,
    content: @Composable () -> Unit,
) {
    val baseContext = LocalContext.current
    // Resolve the registry owner while the context chain still leads back to the
    // ComponentActivity. The localized context below is a fresh createConfigurationContext()
    // that no longer wraps the Activity, which would otherwise break
    // rememberLauncherForActivityResult() in descendants (e.g. the Settings import picker).
    val activityResultRegistryOwner = LocalActivityResultRegistryOwner.current
    val localizedContext = remember(language, baseContext) {
        val locale = when (language) {
            AppLanguage.GERMAN -> Locale.forLanguageTag("de")
            AppLanguage.ENGLISH -> Locale.forLanguageTag("en")
            AppLanguage.SYSTEM -> Resources.getSystem().configuration.locales[0]
        }
        val config = Configuration(baseContext.resources.configuration).apply {
            setLocale(locale)
        }
        baseContext.createConfigurationContext(config)
    }
    val providedValues = buildList {
        add(LocalContext provides localizedContext)
        add(LocalConfiguration provides localizedContext.resources.configuration)
        activityResultRegistryOwner?.let {
            add(LocalActivityResultRegistryOwner provides it)
        }
    }.toTypedArray()
    CompositionLocalProvider(values = providedValues, content = content)
}
