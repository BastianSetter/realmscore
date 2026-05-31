# Phase 15 – Settings + Theme + Polish + Release-Prep

## Anweisung an Claude Code

Lies diese Datei vollstaendig. Lies anschliessend `00-vision.md` fuer den uebergreifenden Kontext. Setze danach diese Phase **vollstaendig** um.

Voraussetzung: Phase 14 ist abgeschlossen. Alle Features funktionieren.

---

## Kontext (kurz)

Die App ist funktional komplett. Diese Phase macht sie release-fertig:
- Settings-Tab mit allen Optionen (Username ändern, Theme, Daten-Reset, About)
- Konsistentes Material 3 Theme + App-Icon + Splash
- Empty/Error/Loading-States überall systematisch
- Accessibility (a11y)
- End-to-End-Tests
- F-Droid-Release-Vorbereitung

Volle Vision: siehe `00-vision.md`.

---

## Scope dieser Phase

### Drin
- **Settings-Tab Inhalte**:
  - Profil (Owner anzeigen + "Username ändern"-Button → UsernameChangeScreen aus Spec 07)
  - Spiel-Einstellungen (Default-Punktelimit, Default-Rundenanzahl, Mittelfeld-Scan-Hinweis)
  - Darstellung (Theme: System/Hell/Dunkel, Dynamische Farben)
  - Daten (Statistiken, Reset-Buttons)
  - Über die App (Version, Lizenz, Repo-Link)
- **Material 3 Theme** vollständig:
  - Eigene Farbpalette (Light + Dark)
  - Typography
  - Dynamische Farben honorieren (Android 12+)
- **App-Icon** (Adaptive Icon, Foreground + Background)
- **Splash-Screen** (Android 12+ API)
- **Empty/Error/Loading States** systematisch in allen Screens
- **Accessibility-Pass** (ContentDescription, Touch-Targets, Kontrast)
- **End-to-End-Tests** für kritische Flows
- **F-Droid-Vorbereitung** (Metadata, Lizenz, Reproducible Build)

### Explizit NICHT drin
- Kein In-App-Tutorial / Walkthrough
- Keine Animationen-Overhaul (was da ist, bleibt)
- Kein Custom-Loading-Skeleton-System

---

## Was am Ende funktionieren muss

**Komplette End-to-End-Tests grün:**

1. Onboarding → Profile anlegen → Home erreicht
2. Neues Spiel mit 3 Spielern → Runde eintragen → Reveal → RoundSummary
3. Spiel abschliessen → in Historie wiederfinden
4. Stats-Tab öffnen, PlayerStats aufrufen
5. Sandbox: Karten reinlegen, Score sehen
6. Move-to-Sandbox aus RoundSummary
7. Username ändern in Settings
8. Theme wechseln in Settings → Live-Anwendung
9. Daten-Reset → App wie neu

**Visuelle Konsistenz:** alle Screens nutzen dasselbe Theme, Buttons sehen einheitlich aus.

**App-Start:** Splash zeigt App-Icon kurz, dann Onboarding/Home.

---

## Tech-Stack fuer diese Phase

Neue/aktivierte Dependencies:
- **androidx.core:core-splashscreen** für Splash-Screen-API
- **Compose UI Testing** (war schon da, jetzt aktiv genutzt)

---

## Architektur-Vorgaben

### 1. Settings-Tab

`SettingsScreen.kt` (ersetzt `SettingsPlaceholderScreen`):

```kotlin
@Composable
fun SettingsScreen(navController: NavHostController) {
    val vm: SettingsViewModel = viewModel(...)
    val state by vm.uiState.collectAsState()

    LazyColumn(modifier = Modifier.fillMaxSize()) {

        item { SectionHeader(stringResource(R.string.settings_profile)) }
        item { ProfileSection(state.ownerProfile, onChangeUsername = { navController.navigate(USERNAME_CHANGE) }) }

        item { SectionHeader(stringResource(R.string.settings_game)) }
        item { DefaultPointLimitField(state.defaultPointLimit, vm::setDefaultPointLimit) }
        item { DefaultRoundCountField(state.defaultRoundCount, vm::setDefaultRoundCount) }
        item { ToggleRow(stringResource(R.string.settings_suggest_discard_scan), state.suggestDiscardScan, vm::setSuggestDiscardScan) }

        item { SectionHeader(stringResource(R.string.settings_appearance)) }
        item { ThemeModeRadioGroup(state.themeMode, vm::setThemeMode) }
        item { ToggleRow(stringResource(R.string.settings_dynamic_colors), state.useDynamicColors, vm::setUseDynamicColors) }

        item { SectionHeader(stringResource(R.string.settings_data)) }
        item { DataSection(state.dataInfo, onClearGameData = { ... }, onResetApp = { ... }) }

        item { SectionHeader(stringResource(R.string.settings_about)) }
        item { AboutSection() }
    }
}
```

### 2. SettingsViewModel

```kotlin
data class SettingsUiState(
    val ownerProfile: Profile? = null,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val useDynamicColors: Boolean = true,
    val defaultPointLimit: Int = 1000,
    val defaultRoundCount: Int = 3,
    val suggestDiscardScan: Boolean = true,
    val dataInfo: DataInfo = DataInfo()
)

data class DataInfo(
    val openGamesCount: Int = 0,
    val closedGamesCount: Int = 0,
    val totalRoundsCount: Int = 0,
    val profilesCount: Int = 0
)
```

### 3. Reset-Logik

```kotlin
class ResetUseCase(
    private val db: AppDatabase,
    private val deviceUuidProvider: DeviceUuidProvider,
    private val settings: SettingsRepository
) {
    suspend fun clearGameData() {
        db.runInTransaction {
            db.handCardDao().deleteAll()
            db.roundResultDao().deleteAll()
            db.roundDao().deleteAll()
            db.gameParticipantDao().deleteAll()
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
```

Confirmation-Dialog mit zweistufiger Bestätigung:
1. AlertDialog "Wirklich löschen? Nicht rückgängig machbar."
2. TextField: "Tippe LÖSCHEN um zu bestätigen"

### 4. Material 3 Theme

```kotlin
// ui/theme/Color.kt
val PrimaryLight = Color(0xFF6750A4)       // Lila
val OnPrimaryLight = Color.White
val SecondaryLight = Color(0xFFB58900)      // Gold
val TertiaryLight = Color(0xFFDC322F)       // Rot (Strafen-Akzent)
val ErrorLight = Color(0xFFB3261E)
// ... vollständige Palette

val PrimaryDark = Color(0xFFD0BCFF)
// ... vollständige Dark-Palette

// ui/theme/Theme.kt
@Composable
fun FantasyRealmsTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    useDynamicColors: Boolean = true,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val colorScheme = when {
        useDynamicColors && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val ctx = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = FantasyRealmsTypography,
        content = content
    )
}
```

### 5. App-Icon

- Adaptive Icon (mipmap-anydpi-v26):
  - Foreground: stilisiertes Karten-Symbol (z.B. drei gefächerte Karten)
  - Background: Theme-Primärfarbe als solid
- Generierung mit Android Studio's Image Asset Studio
- Round + Square Varianten
- Splash-Icon (Android 12+): nutzt dasselbe Foreground

### 6. Splash-Screen

```kotlin
// MainActivity.kt
override fun onCreate(savedInstanceState: Bundle?) {
    installSplashScreen()
    super.onCreate(savedInstanceState)
    // ...
}
```

`themes.xml`:
```xml
<style name="Theme.App.Starting" parent="Theme.SplashScreen">
    <item name="windowSplashScreenBackground">@color/primary</item>
    <item name="windowSplashScreenAnimatedIcon">@drawable/ic_splash_logo</item>
    <item name="postSplashScreenTheme">@style/Theme.FantasyRealms</item>
</style>
```

### 7. Empty/Error/Loading States

Systematischer Pass durch alle Screens:
- Wo eine Liste leer sein kann: Empty-Composable mit Icon + Text
- Wo ein Fehler passieren kann: Snackbar oder Inline-Hinweis
- Wo eine Operation > 200 ms dauert: Loading-Indicator
- Wo eine ganze Liste lädt: Skeleton-Placeholders (mindestens für Historie, Stats)

```kotlin
// ui/components/EmptyState.kt
@Composable
fun EmptyState(icon: ImageVector, title: String, body: String) { ... }

// ui/components/ErrorState.kt
@Composable
fun ErrorState(message: String, onRetry: (() -> Unit)?) { ... }
```

### 8. Accessibility-Pass

- Alle `IconButton`s mit `contentDescription`
- Alle interaktiven Elemente >= 48dp Touch-Target
- Text-Skalierung: `sp` statt `dp` für alle Text-Sizes
- TalkBack-Test mindestens für Onboarding, Home, NewGame, PlayerHandEntry, Settings
- Kontrast-Check: Theme-Farben gegen WCAG AA prüfen

### 9. End-to-End-Tests

`androidTest/`:
```kotlin
@RunWith(AndroidJUnit4::class)
class CriticalFlowTest {
    @get:Rule val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun onboarding_to_first_game_completion() {
        // Onboarding eingeben
        // Neues Spiel mit 3 Spielern starten
        // Runde eingeben für alle Spieler
        // Reveal überspringen
        // RoundSummary → Spiel abschliessen
        // Historie öffnen → Spiel sichtbar
    }

    @Test
    fun sandbox_basic_flow() { ... }

    @Test
    fun stats_navigation() { ... }

    @Test
    fun move_to_sandbox_from_history() { ... }
}
```

Mindestens 4 E2E-Tests, jeder bestanden.

### 10. F-Droid-Vorbereitung

**`fastlane/metadata/android/de-DE/`:**
- `title.txt`: "Fantastische Reiche Scoring"
- `short_description.txt`: < 80 Zeichen
- `full_description.txt`: < 4000 Zeichen
- `changelogs/{versionCode}.txt`: aktuelle Version
- `images/icon.png`, `images/featureGraphic.png`, `images/phoneScreenshots/*.png`

**`build.gradle.kts`:**
- ProGuard-Regeln (Compose, Room, Kotlinx-Serialization)
- Signing-Config getrennt vom Source
- Reproducible Build aktiviert

**LICENSE-Datei** im Repo-Root (GPL-3.0 oder Apache-2.0).

**README.md** im Repo:
- Beschreibung
- Build-Anleitung
- Screenshot
- Lizenz
- F-Droid-Badge (nach Aufnahme)

### 11. Strings (Ergaenzungen)

```xml
<string name="settings_title">Einstellungen</string>
<string name="settings_profile">Profil</string>
<string name="settings_game">Spiel-Einstellungen</string>
<string name="settings_appearance">Darstellung</string>
<string name="settings_data">Daten</string>
<string name="settings_about">Über die App</string>

<string name="settings_change_username">Username ändern</string>
<string name="settings_default_point_limit">Standard-Punktelimit</string>
<string name="settings_default_round_count">Standard-Rundenanzahl</string>
<string name="settings_suggest_discard_scan">Mittelfeld-Hinweis am Rundenende</string>

<string name="settings_theme_mode">Farbschema</string>
<string name="settings_theme_system">System</string>
<string name="settings_theme_light">Hell</string>
<string name="settings_theme_dark">Dunkel</string>
<string name="settings_dynamic_colors">Dynamische Farben (Android 12+)</string>

<string name="settings_data_open_games">Laufende Spiele: %1$d</string>
<string name="settings_data_closed_games">Abgeschlossene Spiele: %1$d</string>
<string name="settings_data_rounds">Gespielte Runden: %1$d</string>
<string name="settings_data_profiles">Profile: %1$d</string>

<string name="settings_clear_game_data">Alle Spieldaten löschen</string>
<string name="settings_reset_app">App zurücksetzen</string>
<string name="settings_confirm_clear_data_title">Wirklich alle Spieldaten löschen?</string>
<string name="settings_confirm_clear_data_body">Diese Aktion kann nicht rückgängig gemacht werden.</string>
<string name="settings_confirm_type_delete">Tippe LÖSCHEN um zu bestätigen</string>

<string name="about_version">Version %1$s</string>
<string name="about_license">Lizenz: GPL-3.0</string>
<string name="about_repo">Quellcode</string>
<string name="about_disclaimer">Diese App ist eine Fan-Implementierung und nicht mit WizKids/Strohmann Games assoziiert.</string>
```

---

## Akzeptanzkriterien

- [ ] Settings-Tab ist voll funktional (alle Sektionen sichtbar und nutzbar)
- [ ] Username ändern funktioniert end-to-end
- [ ] Theme-Wechsel wird sofort angewendet
- [ ] Dynamische Farben werden bei Android 12+ unterstützt
- [ ] Daten-Reset funktioniert (clearGameData behält Profile)
- [ ] App-Reset bringt zurück zum Onboarding
- [ ] App-Icon ist Adaptive Icon, rendert auf Pixel 9
- [ ] Splash-Screen zeigt App-Icon kurz beim Start
- [ ] Mindestens 4 E2E-Tests grün
- [ ] F-Droid-Konformitäts-Check passt (`./gradlew app:dependencies | grep -E "(gms|firebase|mlkit)"` leer)
- [ ] ProGuard/R8-Release-Build funktioniert
- [ ] Fastlane-Metadaten vorhanden (mind. de-DE)
- [ ] LICENSE + README vorhanden
- [ ] Accessibility: TalkBack durchläuft Onboarding + NewGame + PlayerHandEntry ohne Probleme
- [ ] Alle Empty-/Error-/Loading-States sind angemessen
- [ ] Alle Texte aus `strings.xml`

---

## Hinweise

- **App-Icon**: einfaches Design ist OK – wichtig ist die technische Adaptive-Icon-Struktur, nicht künstlerische Perfektion
- **Splash-Screen**: < 1 Sekunde, kein Branding-Marathon
- **Reproducible Build**: Gradle Wrapper version fixieren, Build-Tools fixieren
- **Repository-URL**: muss vor Release tatsächlich gesetzt werden (z.B. GitHub oder Codeberg)
- **F-Droid Merge Request**: separater Schritt nach Release-Prep, nicht Teil dieser Phase
