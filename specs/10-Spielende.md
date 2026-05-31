# Phase 10 – Spielende (GameSummaryScreen)

## Anweisung an Claude Code

Lies diese Datei vollstaendig. Lies anschliessend `00-vision.md` fuer den uebergreifenden Kontext. Setze danach diese Phase **vollstaendig** um.

Voraussetzung: Phase 09 ist abgeschlossen. Reveal + RoundSummary funktionieren.

---

## Kontext (kurz)

Der Platzhalter `GameSummaryPlaceholderScreen` wird durch einen echten **`GameSummaryScreen`** ersetzt. Hier sieht der User die Endabrechnung des Spiels: Sieger-Podest, Rundentabelle ueber das ganze Spiel, kompakte Spiel-Statistiken.

Beim Tap auf "Spiel abschliessen" wird `Game.closedAt` gesetzt und der User landet zurueck im Home-Tab.

Volle Vision: siehe `00-vision.md`.

---

## Scope dieser Phase

### Drin
- `GameSummaryScreen` mit:
  - Sieger-Podest (3 Treppen, animiert)
  - Rundentabelle (ueber alle Runden des Spiels)
  - Spiel-Statistiken-Block (Spieldauer, Anzahl Runden, hoechste Einzelhand, engste Runde)
  - Button "Spiel abschliessen" → `Game.closedAt = now`, navigiert zu Home-Tab
  - Button "Zur Spielübersicht" (falls Spiel noch nicht geschlossen)
  - Button "Statistiken ansehen" (deaktiviert / fuehrt zu Platzhalter `StatsPlaceholderScreen` – Phase 12 macht's lebendig)
- `GameRepository.closeGame(gameId, reason)` implementieren

### Explizit NICHT drin
- Keine Stats-Detail-Screens (Phase 12)
- Keine Move-to-Sandbox (Phase 14)
- Kein Spiel-Reopen (theoretisch via `reopenGame`-Methode, im MVP UI-mässig nicht verfuegbar)

---

## Was am Ende funktionieren muss

**Klick-Pfad:**

1. RoundSummary → "Spiel abschliessen" → `GameSummaryScreen`
2. Sieger-Podest zeigt 1./2./3. Platz mit Avataren + Punkten
3. Rundentabelle zeigt alle Runden + Gesamtsumme
4. Statistik-Block: "Spieldauer: 23 Min", "Anzahl Runden: 5", "Höchste Hand: Maria mit 187 Punkten in Runde 3", "Engste Runde: Runde 2 mit nur 3 Punkten Unterschied"
5. Tap auf "Spiel abschliessen" → `Game.closedAt` gesetzt, Navigation zu Home-Tab
6. Tap auf "Statistiken ansehen" → fuehrt zum Stats-Tab (Bottom Nav)

---

## Tech-Stack fuer diese Phase

Keine neuen Dependencies.

---

## Architektur-Vorgaben

### Routes

```kotlin
const val GAME_SUMMARY = "game/{gameId}/summary"
fun gameSummaryRoute(gameId: String) = "game/$gameId/summary"
```

`GAME_SUMMARY_PLACEHOLDER` raus, `GAME_SUMMARY` rein.

### GameRepository ergaenzen

```kotlin
interface GameRepository {
    // bestehend...
    suspend fun closeGame(gameId: String, reason: ClosedReason)
    suspend fun reopenGame(gameId: String)  // setzt closedAt = null, closedReason = null
}
```

### GameSummaryScreen

```kotlin
@Composable
fun GameSummaryScreen(
    gameId: String,
    onCloseGameDone: () -> Unit,
    onShowStats: () -> Unit,
    onBackToGame: () -> Unit
) {
    val vm: GameSummaryViewModel = viewModel(...)
    val state by vm.uiState.collectAsState()

    Scaffold(...) { padding ->
        Column(Modifier.padding(padding).padding(16.dp).verticalScroll(rememberScrollState())) {

            WinnerPodium(state.podium)

            Spacer(Modifier.height(24.dp))

            GameStatsBlock(state.gameStats)

            Spacer(Modifier.height(24.dp))

            RoundsTable(state.rounds, state.players)

            Spacer(Modifier.height(24.dp))

            if (state.isClosed) {
                OutlinedButton(onClick = onShowStats, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.game_summary_show_stats))
                }
                Spacer(Modifier.height(8.dp))
                Button(onClick = onCloseGameDone, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.game_summary_back_home))
                }
            } else {
                Button(onClick = { vm.closeAndNavigate(onCloseGameDone) }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.game_summary_close_game))
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onBackToGame, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.game_summary_back_to_game))
                }
            }
        }
    }
}

data class PodiumEntry(
    val rank: Int,           // 1, 2, 3
    val profileId: String,
    val name: String,
    val colorArgb: Int,
    val totalScore: Int
)

data class GameStats(
    val durationMinutes: Long,
    val roundCount: Int,
    val highestSingleHandScore: Int,
    val highestSingleHandPlayer: String,
    val highestSingleHandRound: Int,
    val closestRoundDifference: Int,
    val closestRoundNumber: Int
)

data class RoundsTableRow(
    val roundNumber: Int,
    val scoresByProfile: Map<String, Int>,
    val winnerProfileId: String?
)

data class GameSummaryUiState(
    val isLoading: Boolean = true,
    val isClosed: Boolean = false,
    val podium: List<PodiumEntry> = emptyList(),
    val rounds: List<RoundsTableRow> = emptyList(),
    val players: List<Profile> = emptyList(),
    val gameStats: GameStats? = null
)
```

### WinnerPodium

Drei Boxen mit unterschiedlichen Hoehen:
- 1. Platz: 120dp hoch, Mitte
- 2. Platz: 90dp hoch, links
- 3. Platz: 70dp hoch, rechts

Bei < 3 Spielern: entsprechend weniger Stufen.

Bei Gleichstand: gleiche Stufenhoehe fuer alle Beteiligten.

```kotlin
@Composable
fun WinnerPodium(entries: List<PodiumEntry>) {
    Row(
        modifier = Modifier.fillMaxWidth().height(200.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        entries.sortedBy { it.rank }.forEach { entry ->
            val height = when (entry.rank) {
                1 -> 120.dp
                2 -> 90.dp
                3 -> 70.dp
                else -> 50.dp
            }
            PodiumStep(entry, height)
        }
    }
}

@Composable
private fun PodiumStep(entry: PodiumEntry, height: Dp) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Avatar(color = Color(entry.colorArgb), initials = entry.name.take(2).uppercase())
        Text(entry.name)
        Text(stringResource(R.string.podium_score, entry.totalScore))
        Spacer(Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .height(height)
                .width(80.dp)
                .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
        ) {
            Text(
                text = "${entry.rank}.",
                modifier = Modifier.align(Alignment.Center),
                style = MaterialTheme.typography.headlineLarge
            )
        }
    }
}
```

### GameStatsBlock

Einfache Sektion mit Labels:
- "Spieldauer: 23 Min"
- "Anzahl Runden: 5"
- "Höchste Hand: Maria mit 187 Punkten in Runde 3"
- "Engste Runde: Runde 2 (3 Punkte Unterschied)"

### RoundsTable

Tabelle mit:
- Spalten: Spieler-Avatare als Header
- Zeilen: pro Runde, mit Krone-Icon bei Rundensieger
- Letzte Zeile: Gesamt

### closeGame-Logik

```kotlin
fun closeAndNavigate(callback: () -> Unit) {
    viewModelScope.launch {
        gameRepo.closeGame(gameId, ClosedReason.COMPLETED)
        callback()
    }
}
```

Nach `closeGame` ist das Spiel nicht mehr in `observeOpenGames`. Die Navigation springt zum Home-Tab.

### Strings (Ergaenzungen)

```xml
<string name="game_summary_title">Spielende</string>
<string name="podium_score">%1$d Punkte</string>
<string name="game_stats_duration">Spieldauer: %1$d Min</string>
<string name="game_stats_rounds">Anzahl Runden: %1$d</string>
<string name="game_stats_highest_hand">Höchste Hand: %1$s mit %2$d Punkten in Runde %3$d</string>
<string name="game_stats_closest_round">Engste Runde: Runde %1$d (%2$d Punkte Unterschied)</string>
<string name="game_summary_close_game">Spiel abschließen</string>
<string name="game_summary_back_to_game">Zurück zum Spiel</string>
<string name="game_summary_back_home">Zurück zum Hauptmenü</string>
<string name="game_summary_show_stats">Statistiken ansehen</string>
```

---

## Akzeptanzkriterien

- [ ] `GameSummaryScreen` aus RoundSummary erreichbar
- [ ] Sieger-Podest zeigt 1./2./3. mit korrekten Avataren + Punkten
- [ ] Bei < 3 Spielern: entsprechend weniger Stufen
- [ ] Bei Gleichstand: gleiche Stufen-Hoehe
- [ ] Rundentabelle zeigt alle Runden korrekt
- [ ] Spiel-Statistik-Block zeigt Duration, Rundenanzahl, höchste Hand, engste Runde
- [ ] "Spiel abschliessen"-Button setzt `closedAt` + `closedReason`
- [ ] Nach Abschluss: Navigation zu Home-Tab
- [ ] Bei bereits geschlossenem Spiel (z.B. via Historie spaeter): andere Button-Konfiguration
- [ ] Alle Texte aus `strings.xml`
- [ ] Build + Tests gruen

---

## Hinweise

- **Stats-Tab-Navigation**: Tap auf "Statistiken ansehen" navigiert zur Bottom-Nav-Tab `tab_stats`. Aktuell ist der noch ein Platzhalter – das ist OK.
- **closeGame ist idempotent**: ein bereits geschlossenes Spiel sollte erneut closeGame nicht aendern (Implementierung defensiv).
- **Rundentabelle bei vielen Runden**: horizontales Scrollen erlauben (LazyRow oder normales scrollable)
