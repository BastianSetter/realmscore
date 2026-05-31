# Phase 02 – Hauptmenue (Bottom Navigation)

## Anweisung an Claude Code

Lies diese Datei vollstaendig. Lies anschliessend `00-vision.md` fuer den uebergreifenden Kontext. Setze danach diese Phase **vollstaendig** um.

Voraussetzung: Phase 01 ist abgeschlossen. Es gibt ein lauffaehiges Projekt mit Onboarding und `HomePlaceholderScreen`.

---

## Kontext (kurz)

In dieser Phase wird der `HomePlaceholderScreen` durch ein echtes **Hauptmenue mit Bottom Navigation** ersetzt. Die Bottom Nav hat 4 Tabs: **Home**, **Historie**, **Statistik**, **Settings**.

Alle Tabs sind initial **leer** (Platzhalter), nur der **Home-Tab** bekommt Inhalte: zwei Buttons "Neues Spiel" und "Sandbox", die jeweils auf weitere Platzhalter fuehren.

Volle Vision: siehe `00-vision.md`.

---

## Scope dieser Phase

### Drin
- Single-Activity-Scaffold mit Bottom Navigation
- 4 Tabs (Home, Historie, Statistik, Settings) als verschachtelte NavGraphs
- Tab-State bleibt beim Wechsel erhalten (Scroll-Position etc.)
- Home-Tab-Inhalt:
  - Begruessung "Hallo {Owner-Name}!"
  - Button "Neues Spiel" → `NewGamePlaceholderScreen`
  - Card "Sandbox" → `SandboxPlaceholderScreen`
- Historie-Tab, Statistik-Tab, Settings-Tab: jeweils leerer Platzhalter mit Tab-Titel
- Navigation aus Onboarding fuehrt jetzt zur Bottom-Nav-Activity (nicht mehr zum `HomePlaceholderScreen`)

### Explizit NICHT drin
- Keine echten Spielfunktionen
- Keine Sandbox-Implementierung
- Kein Settings-Inhalt (kein Theme-Toggle etc.)
- Keine "Games to continue"-Liste (kommt in Phase 13)
- Kein Random-Stat-Graph (kommt in Phase 13)

---

## Was am Ende funktionieren muss

**Klick-Pfad nach Abschluss:**

1. App-Start → falls Owner existiert: direkt Hauptmenue (Home-Tab aktiv)
2. Home-Tab zeigt: "Hallo {Name}!" + Button "Neues Spiel" + Card "Sandbox"
3. Tap auf "Neues Spiel" → `NewGamePlaceholderScreen` ("Neues Spiel kommt hier hin"), Back fuehrt zurueck zum Home-Tab
4. Tap auf "Sandbox" → `SandboxPlaceholderScreen` ("Sandbox kommt hier hin"), Back fuehrt zurueck
5. Tap auf Historie-Tab → leerer Platzhalter "Historie kommt hier hin"
6. Tap auf Statistik-Tab → leerer Platzhalter "Statistik kommt hier hin"
7. Tap auf Settings-Tab → leerer Platzhalter "Settings kommt hier hin"
8. Tab-Wechsel: bleibt visuell konsistent, jeweils Bottom Nav Bar bleibt sichtbar

---

## Tech-Stack fuer diese Phase

Keine neuen Dependencies noetig. Verwendung der schon in Phase 01 eingerichteten:
- **Jetpack Compose** + **Material 3** (`NavigationBar`, `NavigationBarItem`, `Scaffold`)
- **AndroidX Navigation Compose** (`NavHost`, verschachtelte Graphs)

---

## Architektur-Vorgaben

### Erweiterte Ordnerstruktur

```
ui/
├── MainActivity.kt
├── nav/
│   ├── AppNavHost.kt                    # bestehend, wird angepasst
│   ├── Routes.kt                        # bestehend, wird erweitert
│   └── MainScaffold.kt                  # NEU: Scaffold mit Bottom Nav
├── tabs/
│   ├── home/
│   │   ├── HomeScreen.kt                # NEU
│   │   └── HomeViewModel.kt             # NEU
│   ├── history/
│   │   └── HistoryPlaceholderScreen.kt  # NEU
│   ├── stats/
│   │   └── StatsPlaceholderScreen.kt    # NEU
│   └── settings/
│       └── SettingsPlaceholderScreen.kt # NEU
└── placeholder/
    ├── NewGamePlaceholderScreen.kt      # NEU
    └── SandboxPlaceholderScreen.kt      # NEU
```

Der alte `HomePlaceholderScreen` aus Phase 01 wird **geloescht**.

### Routes

```kotlin
// ui/nav/Routes.kt
object Routes {
    const val SPLASH = "splash"
    const val ONBOARDING = "onboarding"
    const val MAIN = "main"

    // Tab-Routen (innerhalb MAIN)
    const val TAB_HOME = "tab_home"
    const val TAB_HISTORY = "tab_history"
    const val TAB_STATS = "tab_stats"
    const val TAB_SETTINGS = "tab_settings"

    // Sub-Routen vom Home-Tab aus
    const val NEW_GAME_PLACEHOLDER = "new_game_placeholder"
    const val SANDBOX_PLACEHOLDER = "sandbox_placeholder"
}
```

### Navigation-Struktur

```
AppNavHost (start: SPLASH)
├── splash         → routing logic (existiert ↘ Owner-Check)
├── onboarding     → OnboardingScreen
└── main           → MainScaffold (mit eigenem inneren NavHost)
    ├── tab_home
    │   ├── HomeScreen
    │   ├── new_game_placeholder → NewGamePlaceholderScreen
    │   └── sandbox_placeholder  → SandboxPlaceholderScreen
    ├── tab_history → HistoryPlaceholderScreen
    ├── tab_stats   → StatsPlaceholderScreen
    └── tab_settings → SettingsPlaceholderScreen
```

### MainScaffold

```kotlin
@Composable
fun MainScaffold(rootNavController: NavHostController) {
    val tabNavController = rememberNavController()
    Scaffold(
        bottomBar = { AppBottomNavigation(tabNavController) }
    ) { padding ->
        NavHost(
            navController = tabNavController,
            startDestination = Routes.TAB_HOME,
            modifier = Modifier.padding(padding)
        ) {
            composable(Routes.TAB_HOME) { HomeScreen(tabNavController) }
            composable(Routes.NEW_GAME_PLACEHOLDER) { NewGamePlaceholderScreen() }
            composable(Routes.SANDBOX_PLACEHOLDER) { SandboxPlaceholderScreen() }
            composable(Routes.TAB_HISTORY) { HistoryPlaceholderScreen() }
            composable(Routes.TAB_STATS) { StatsPlaceholderScreen() }
            composable(Routes.TAB_SETTINGS) { SettingsPlaceholderScreen() }
        }
    }
}
```

**Wichtig** zur Bottom Nav Logik:
- Tab-Wechsel mit `launchSingleTop = true` und `popUpTo(graph.findStartDestination().id) { saveState = true }` plus `restoreState = true`
- So bleibt der Sub-Route-Stack pro Tab erhalten

### HomeScreen Inhalt

```kotlin
@Composable
fun HomeScreen(navController: NavHostController) {
    val vm: HomeViewModel = viewModel(factory = ...)
    val state by vm.uiState.collectAsState()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(
            text = stringResource(R.string.home_greeting, state.ownerName.orEmpty()),
            style = MaterialTheme.typography.headlineSmall
        )
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = { navController.navigate(Routes.NEW_GAME_PLACEHOLDER) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.home_new_game))
        }
        Spacer(Modifier.height(16.dp))
        Card(
            onClick = { navController.navigate(Routes.SANDBOX_PLACEHOLDER) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(stringResource(R.string.home_sandbox_title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.home_sandbox_body), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
```

### HomeViewModel

```kotlin
data class HomeUiState(
    val ownerName: String? = null
)

class HomeViewModel(
    private val profileRepo: ProfileRepository
) : ViewModel() {
    val uiState: StateFlow<HomeUiState>
}
```

Liest Owner-Name beim Init via `profileRepo.getLocalOwner()`.

### Bottom Navigation Bar

```kotlin
data class TabItem(val route: String, val labelRes: Int, val iconRes: Int)

val tabs = listOf(
    TabItem(Routes.TAB_HOME, R.string.tab_home, R.drawable.ic_home),
    TabItem(Routes.TAB_HISTORY, R.string.tab_history, R.drawable.ic_history),
    TabItem(Routes.TAB_STATS, R.string.tab_stats, R.drawable.ic_stats),
    TabItem(Routes.TAB_SETTINGS, R.string.tab_settings, R.drawable.ic_settings)
)
```

Icons koennen Material Symbols sein (z.B. via `androidx.compose.material.icons.Icons.Default.Home` etc., damit sparen wir uns Vector Drawables in dieser Phase).

### Platzhalter-Composables

Alle vier Tab-Platzhalter und die zwei Sub-Platzhalter sind sehr aehnlich:

```kotlin
@Composable
fun SandboxPlaceholderScreen() {
    PlaceholderContent(stringResource(R.string.placeholder_sandbox))
}

@Composable
fun PlaceholderContent(text: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}
```

### Strings (Ergaenzungen)

```xml
<string name="home_greeting">Hallo %1$s!</string>
<string name="home_new_game">Neues Spiel</string>
<string name="home_sandbox_title">Sandbox</string>
<string name="home_sandbox_body">Karten frei kombinieren und ausprobieren</string>

<string name="tab_home">Start</string>
<string name="tab_history">Historie</string>
<string name="tab_stats">Statistik</string>
<string name="tab_settings">Einstellungen</string>

<string name="placeholder_new_game">Neues Spiel kommt hier hin</string>
<string name="placeholder_sandbox">Sandbox kommt hier hin</string>
<string name="placeholder_history">Historie kommt hier hin</string>
<string name="placeholder_stats">Statistik kommt hier hin</string>
<string name="placeholder_settings">Einstellungen kommen hier hin</string>
```

---

## Akzeptanzkriterien

- [ ] Nach Onboarding (oder bei vorhandenem Owner direkt) landet die App im Hauptmenue mit Home-Tab aktiv
- [ ] Bottom Navigation Bar ist permanent sichtbar mit 4 Tabs
- [ ] Tap auf jeden Tab wechselt sichtbar, aktiver Tab ist hervorgehoben
- [ ] Tab-State bleibt erhalten beim Wechsel (Scroll-Position etc.)
- [ ] Home-Tab zeigt: Begruessung mit Owner-Name + "Neues Spiel"-Button + Sandbox-Card
- [ ] Tap auf "Neues Spiel" navigiert zu `NewGamePlaceholderScreen`, Bottom Nav bleibt sichtbar
- [ ] Tap auf Sandbox-Card navigiert zu `SandboxPlaceholderScreen`, Bottom Nav bleibt sichtbar
- [ ] Back-Button auf Platzhaltern fuehrt zurueck zum Home-Tab
- [ ] System Back von Home-Tab oder anderen Tabs schliesst die App (kein Sprung zurueck zum Onboarding)
- [ ] Alle Texte aus `strings.xml`
- [ ] Build laeuft ohne Fehler

---

## Hinweise

- **Compose `BackHandler`** auf den Tabs nicht noetig – die NavHost-Konfiguration regelt das
- **Tab-Icons via `Icons.Default.*`** statt eigener Vector Drawables – Vector Drawables koennen in der Polish-Phase 15 ergaenzt werden
- **Onboarding-Navigation**: Nach erfolgreichem `createOwner` mit `popUpTo(Routes.SPLASH) { inclusive = true }` zur `main`-Route, sodass kein Zurueck mehr moeglich ist
