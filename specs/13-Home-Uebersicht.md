# Phase 13 – Home-Tab lebendig ("Games to continue" + Random-Stat)

## Anweisung an Claude Code

Lies diese Datei vollstaendig. Lies anschliessend `00-vision.md` fuer den uebergreifenden Kontext. Setze danach diese Phase **vollstaendig** um.

Voraussetzung: Phase 12 ist abgeschlossen. Statistiken sind verfuegbar.

---

## Kontext (kurz)

Der `HomeScreen` aus Phase 02 zeigt aktuell nur Begrüssung + zwei Buttons. In dieser Phase wird er um zwei Bereiche **erweitert**:

1. **"Laufende Spiele"** – horizontal-scrollbare Liste aller offenen Spiele mit Tap-to-Continue
2. **"Wusstest du..."** – ein zufaeliger Stat-Block, der sich bei jedem Home-Besuch aendert (nicht 2x hintereinander gleich)

Volle Vision: siehe `00-vision.md`.

---

## Scope dieser Phase

### Drin
- "Games to continue"-Sektion im HomeScreen:
  - Horizontal scrollende Liste aller offenen Spiele
  - Pro Card: Spiel-Name, Teilnehmer-Avatare, aktueller Top-Stand, relative Zeit
  - Tap → öffnet `GameInProgressScreen`
  - Empty State, wenn keine offenen Spiele
- "Wusstest du..."-Sektion (Random-Stat):
  - Ein zufällig gewählter Stat-Block mit Mini-Visualisierung
  - Pool umfasst: eigene Stats, andere Spieler-Stats, Karten-Stats, Spielgruppen-Stats
  - Nicht 2x hintereinander der gleiche Stat → letzte Auswahl in DataStore
  - Bei zu wenig Daten: Empty State "Spiele ein paar Runden..."
  - Tap → führt zum entsprechenden Detail-Screen
- `PickRandomStatUseCase` mit Pool von `RandomStatProvider`s
- `SettingsRepository.lastRandomStatKey` für die Ausschluss-Regel

### Explizit NICHT drin
- Keine Tageszeiten-Begrüssung
- Keine animierten Übergänge
- Keine "Heute haben Spielgruppe X gespielt"-Features

---

## Was am Ende funktionieren muss

**Klick-Pfad:**

1. Hauptmenü → Home-Tab
2. Begrüssung sichtbar wie bisher
3. **"Neues Spiel"** + **Sandbox**-Card wie bisher
4. **Sektion "Laufende Spiele"** (falls offene existieren): horizontale Liste der Spiele
5. Tap auf ein Spiel → `GameInProgressScreen`
6. **Sektion "Wusstest du..."**: zufälliger Stat, z.B. "Maria hat die höchste Siegquote: 67%"
7. Tap auf Stat → Navigation zum entsprechenden Detail-Screen
8. Wechsel auf anderen Tab und zurück: anderer Stat wird angezeigt (nicht der vorherige)

---

## Tech-Stack fuer diese Phase

Keine neuen Dependencies.

---

## Architektur-Vorgaben

### SettingsRepository ergaenzen

```kotlin
interface SettingsRepository {
    // bestehend...
    val lastRandomStatKey: Flow<String?>
    suspend fun setLastRandomStatKey(key: String?)
}
```

### Random-Stat-System

```kotlin
// domain/stats/random/RandomStat.kt
data class RandomStat(
    val key: String,                    // z.B. "owner_avg_score"
    val titleResKey: String,            // String-Resource-Key fuer Titel
    val titleArgs: List<String>,        // Format-Args
    val visualization: StatVisualization,
    val tapDestination: StatDestination?
)

sealed class StatVisualization {
    data class BigNumber(val value: String) : StatVisualization()
    data class BarChart(val values: List<Float>, val labels: List<String>) : StatVisualization()
    data class LineChart(val points: List<Float>) : StatVisualization()
}

sealed class StatDestination {
    data class Player(val profileId: String) : StatDestination()
    data class Card(val cardKey: String) : StatDestination()
    data class HeadToHead(val profileIdA: String, val profileIdB: String) : StatDestination()
    object Overview : StatDestination()
}

// domain/stats/random/RandomStatProvider.kt
interface RandomStatProvider {
    val key: String
    suspend fun provide(): RandomStat?    // null wenn nicht genug Daten
}
```

### Konkrete Provider (mindestens 6 im MVP)

```kotlin
class OwnerAvgScoreProvider(...) : RandomStatProvider {
    override val key = "owner_avg_score"
    override suspend fun provide(): RandomStat? {
        val owner = profileRepo.getLocalOwner() ?: return null
        val stats = statsRepo.getPlayerStats(owner.id)
        if (stats.gamesPlayed < 3) return null
        return RandomStat(
            key = key,
            titleResKey = "random_stat_owner_avg",
            titleArgs = listOf(stats.avgScorePerHand.toInt().toString()),
            visualization = StatVisualization.BigNumber(stats.avgScorePerHand.toInt().toString()),
            tapDestination = StatDestination.Player(owner.id)
        )
    }
}

class MostPopularCardProvider(...) : RandomStatProvider { ... }
class MostDiscardedCardProvider(...) : RandomStatProvider { ... }
class HighestWinRatePlayerProvider(...) : RandomStatProvider { ... }
class ClosestRoundEverProvider(...) : RandomStatProvider { ... }
class MostPlayedTogetherProvider(...) : RandomStatProvider { ... }
```

### PickRandomStatUseCase

```kotlin
class PickRandomStatUseCase(
    private val providers: List<RandomStatProvider>,
    private val settings: SettingsRepository
) {
    suspend fun execute(): RandomStatResult {
        val lastKey = settings.lastRandomStatKey.first()
        val candidates = providers
            .filter { it.key != lastKey }
            .mapNotNull { it.provide() }
        if (candidates.isEmpty()) {
            // Fallback: erneut versuchen ohne Ausschluss
            val fallback = providers.mapNotNull { it.provide() }
            if (fallback.isEmpty()) return RandomStatResult.NotEnoughData
            val picked = fallback.random()
            settings.setLastRandomStatKey(picked.key)
            return RandomStatResult.Found(picked)
        }
        val picked = candidates.random()
        settings.setLastRandomStatKey(picked.key)
        return RandomStatResult.Found(picked)
    }
}

sealed class RandomStatResult {
    object NotEnoughData : RandomStatResult()
    data class Found(val stat: RandomStat) : RandomStatResult()
}
```

### HomeViewModel erweitern

```kotlin
data class HomeUiState(
    val ownerName: String? = null,
    val openGames: List<OpenGameCard> = emptyList(),
    val randomStat: RandomStatResult = RandomStatResult.NotEnoughData,
    val isLoading: Boolean = true
)

data class OpenGameCard(
    val gameId: String,
    val displayName: String?,
    val fallbackName: String,
    val participants: List<ParticipantBadge>,
    val topStand: TopStandInfo,
    val updatedAt: Long
)

class HomeViewModel(
    private val gameRepo: GameRepository,
    private val getGameStateUseCase: GetGameStateUseCase,
    private val pickRandomStatUseCase: PickRandomStatUseCase,
    private val profileRepo: ProfileRepository
) : ViewModel() {
    val uiState: StateFlow<HomeUiState>

    init {
        viewModelScope.launch {
            // Owner laden
            // Open Games observieren
            // Random Stat picken
        }
    }

    fun onResume() {
        // Random Stat neu picken bei jedem Home-Tab-Besuch
        viewModelScope.launch {
            val newStat = pickRandomStatUseCase.execute()
            _uiState.update { it.copy(randomStat = newStat) }
        }
    }
}
```

`onResume()` wird im `LaunchedEffect` im HomeScreen aufgerufen wenn der Tab betreten wird.

### HomeScreen erweitern

```kotlin
@Composable
fun HomeScreen(navController: NavHostController) {
    val vm: HomeViewModel = viewModel(...)
    val state by vm.uiState.collectAsState()

    LaunchedEffect(Unit) {
        vm.onResume()
    }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        item { Greeting(state.ownerName) }
        item { Spacer(Modifier.height(16.dp)) }
        item { NewGameButton(navController) }
        item { Spacer(Modifier.height(16.dp)) }
        item { SandboxCard(navController) }

        if (state.openGames.isNotEmpty()) {
            item { Spacer(Modifier.height(24.dp)) }
            item { Text(stringResource(R.string.home_games_to_continue), style = MaterialTheme.typography.titleMedium) }
            item { OpenGamesRow(state.openGames, navController) }
        }

        item { Spacer(Modifier.height(24.dp)) }
        item { Text(stringResource(R.string.home_did_you_know), style = MaterialTheme.typography.titleMedium) }
        item { RandomStatBlock(state.randomStat, navController) }
    }
}

@Composable
fun OpenGamesRow(games: List<OpenGameCard>, navController: NavHostController) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(games, key = { it.gameId }) { game ->
            OpenGameCard(
                game = game,
                onClick = { navController.navigate(gameRoute(game.gameId)) }
            )
        }
    }
}

@Composable
fun RandomStatBlock(result: RandomStatResult, navController: NavHostController) {
    when (result) {
        is RandomStatResult.NotEnoughData -> {
            Card(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.home_stats_not_enough_title))
                    Text(stringResource(R.string.home_stats_not_enough_body), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        is RandomStatResult.Found -> {
            Card(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                onClick = { navigateForStat(navController, result.stat.tapDestination) }
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(
                            getStringResIdByName(result.stat.titleResKey),
                            *result.stat.titleArgs.toTypedArray()
                        )
                    )
                    Spacer(Modifier.height(8.dp))
                    StatVisualizationView(result.stat.visualization)
                }
            }
        }
    }
}
```

### Strings (Ergaenzungen)

```xml
<string name="home_games_to_continue">Laufende Spiele</string>
<string name="home_did_you_know">Wusstest du …</string>
<string name="home_stats_not_enough_title">Noch keine Insights</string>
<string name="home_stats_not_enough_body">Spiele ein paar Runden, dann gibt's hier interessante Statistiken.</string>

<string name="random_stat_owner_avg">Du hast im Schnitt %1$s Punkte pro Hand</string>
<string name="random_stat_most_popular_card">%1$s ist die beliebteste Karte aller Spieler</string>
<string name="random_stat_most_discarded_card">%1$s wird am häufigsten ins Mittelfeld gelegt</string>
<string name="random_stat_highest_winrate_player">%1$s hat die höchste Siegquote: %2$s%%</string>
<string name="random_stat_closest_round_ever">Engste Runde aller Zeiten: %1$s vs %2$s mit nur %3$d Punkten Unterschied</string>
<string name="random_stat_most_played_together">%1$s und %2$s spielen am häufigsten zusammen (%3$d Spiele)</string>
```

---

## Akzeptanzkriterien

- [ ] HomeScreen zeigt "Laufende Spiele"-Sektion bei offenen Spielen
- [ ] Bei keinen offenen Spielen: Sektion wird ausgeblendet (keine leere Section)
- [ ] Tap auf Open-Game-Card öffnet `GameInProgressScreen`
- [ ] "Wusstest du..."-Block ist immer sichtbar (entweder Stat oder Empty State)
- [ ] Bei jedem Home-Tab-Besuch (auch Wechsel + zurück) wird ein neuer Stat gewählt
- [ ] Stat wechselt nicht 2x hintereinander zum gleichen
- [ ] `lastRandomStatKey` wird in DataStore persistiert
- [ ] Mindestens 6 Random-Stat-Provider implementiert
- [ ] Tap auf Stat-Block navigiert zum entsprechenden Detail-Screen
- [ ] Bei zu wenig Daten: Empty State
- [ ] Alle Texte aus `strings.xml`
- [ ] Build erfolgreich

---

## Hinweise

- **Stat-Frequenz "bei jedem Home-Besuch"**: realisierbar via `LaunchedEffect(Unit)` im HomeScreen, das beim Re-Composition feuert. Alternative: `currentBackStackEntryAsState` observieren.
- **Wenn alle Provider null liefern**: Empty State zeigen, `lastRandomStatKey` nicht ändern
- **Edge Case: nur 1 Provider hat Daten**: Ausschlusslogik schliesst diesen aus → keine Kandidaten → Fallback ohne Ausschluss
- **Provider-Liste** ist im AppContainer registriert, kann später leicht erweitert werden
