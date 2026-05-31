# Phase 11 – Historie-Tab

## Anweisung an Claude Code

Lies diese Datei vollstaendig. Lies anschliessend `00-vision.md` fuer den uebergreifenden Kontext. Setze danach diese Phase **vollstaendig** um.

Voraussetzung: Phase 10 ist abgeschlossen. Spiele koennen abgeschlossen werden.

---

## Kontext (kurz)

Der `HistoryPlaceholderScreen` wird durch einen echten **`HistoryScreen`** ersetzt. Der User sieht eine chronologische Liste aller Spiele (offene + geschlossene) mit Filter-Moeglichkeiten.

Tap auf ein Spiel oeffnet das `GameSummaryScreen` (bei geschlossenen Spielen) oder das `GameInProgressScreen` (bei offenen Spielen).

Volle Vision: siehe `00-vision.md`.

---

## Scope dieser Phase

### Drin
- `HistoryScreen` mit:
  - Filter-Bar oben (Status-Chips: Alle / Laufend / Abgeschlossen / Abgebrochen)
  - Spieler-Filter (Multi-Select-Dropdown)
  - Volltext-Suche ueber `displayName` und auto-generierte Spiel-Namen
  - Liste aller Spiele, chronologisch absteigend
- Pro Listen-Eintrag:
  - Status-Tag (Farbcode)
  - Spiel-Name (falls displayName) oder Fallback "Spiel vom {Datum}"
  - Teilnehmer-Avatare (klein, max 5, dann "+N")
  - Datum + relative Zeit
  - Bei geschlossen: Sieger + Endpunkte
  - Bei laufend: aktueller Top-Stand
- Tap fuehrt zu `GameSummary` (geschlossen) bzw. `GameInProgress` (offen)
- `GameRepository.observeAllGames()` ergaenzen

### Explizit NICHT drin
- Keine Bearbeitungs-Funktion (Spiel umbenennen kommt evtl. Phase 2)
- Kein Loeschen (Phase 2)
- Keine Detail-Stats pro Spiel (Phase 12 hat die globalen Stats)

---

## Was am Ende funktionieren muss

**Klick-Pfad:**

1. Hauptmenue → Tap auf Historie-Tab (vorher Platzhalter)
2. `HistoryScreen` zeigt: Filter-Bar + Liste aller Spiele, neueste zuerst
3. Wenn keine Spiele existieren: "Noch keine Spiele gespielt"
4. Filter tippen: Liste aktualisiert sich
5. Spieler-Filter: zeigt nur Spiele in denen die ausgewaehlten Spieler dabei waren
6. Tap auf geschlossenes Spiel: navigiert zu `GameSummary` mit korrekter `gameId`
7. Tap auf offenes Spiel: navigiert zu `GameInProgress`
8. Aus `GameSummary` oder `GameInProgress` zurueck per Back → wieder im Historie-Tab

---

## Tech-Stack fuer diese Phase

Keine neuen Dependencies.

---

## Architektur-Vorgaben

### GameRepository ergaenzen

```kotlin
interface GameRepository {
    // bestehend...
    fun observeAllGames(): Flow<List<Game>>     // sortiert nach updatedAt desc / closedAt desc
    fun observeClosedGames(): Flow<List<Game>>
}
```

In DAO:
```kotlin
@Query("""
    SELECT * FROM games
    ORDER BY 
        CASE WHEN closedAt IS NULL THEN updatedAt ELSE closedAt END DESC
""")
fun observeAllGames(): Flow<List<GameEntity>>
```

### HistoryViewModel

```kotlin
data class HistoryItem(
    val gameId: String,
    val displayName: String?,             // null wenn nicht gesetzt
    val fallbackName: String,             // "Spiel vom 12.10.2025"
    val status: HistoryStatus,            // OPEN, COMPLETED, ABANDONED
    val participants: List<ParticipantBadge>,
    val startedAt: Long,
    val closedAt: Long?,
    val winner: WinnerInfo?,              // bei COMPLETED
    val currentTopStand: TopStandInfo?    // bei OPEN
)

data class ParticipantBadge(val profileId: String, val name: String, val colorArgb: Int)
data class WinnerInfo(val name: String, val score: Int)
data class TopStandInfo(val name: String, val score: Int)

enum class HistoryStatus { OPEN, COMPLETED, ABANDONED }

data class HistoryFilters(
    val statuses: Set<HistoryStatus> = HistoryStatus.values().toSet(),  // all selected
    val playerProfileIds: Set<String> = emptySet(),                     // empty = all
    val searchQuery: String = ""
)

data class HistoryUiState(
    val items: List<HistoryItem> = emptyList(),
    val filters: HistoryFilters = HistoryFilters(),
    val availablePlayers: List<ParticipantBadge> = emptyList(),
    val isLoading: Boolean = true
)

class HistoryViewModel(
    private val gameRepo: GameRepository,
    private val getGameStateUseCase: GetGameStateUseCase,
    private val profileRepo: ProfileRepository
) : ViewModel() {

    val uiState: StateFlow<HistoryUiState>

    fun setStatusFilter(statuses: Set<HistoryStatus>)
    fun togglePlayerFilter(profileId: String)
    fun setSearchQuery(query: String)  // debounced
}
```

### HistoryScreen

```kotlin
@Composable
fun HistoryScreen(
    onOpenGame: (gameId: String, isClosed: Boolean) -> Unit
) {
    val vm: HistoryViewModel = viewModel(...)
    val state by vm.uiState.collectAsState()

    Scaffold(...) { padding ->
        Column(Modifier.padding(padding)) {

            // Sticky Filter-Bar
            Column(Modifier.padding(16.dp)) {
                OutlinedTextField(
                    value = state.filters.searchQuery,
                    onValueChange = vm::setSearchQuery,
                    label = { Text(stringResource(R.string.history_search)) },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                StatusFilterChips(state.filters.statuses, vm::setStatusFilter)
                Spacer(Modifier.height(8.dp))
                PlayerFilterDropdown(state.availablePlayers, state.filters.playerProfileIds, vm::togglePlayerFilter)
            }

            Divider()

            if (state.items.isEmpty() && !state.isLoading) {
                EmptyHistoryState(state.filters)
            } else {
                LazyColumn {
                    items(state.items, key = { it.gameId }) { item ->
                        HistoryListItem(item, onClick = { onOpenGame(item.gameId, item.status != HistoryStatus.OPEN) })
                    }
                }
            }
        }
    }
}
```

### HistoryListItem

```kotlin
@Composable
fun HistoryListItem(item: HistoryItem, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            StatusBadge(item.status)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = item.displayName ?: item.fallbackName,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = formatRelativeDate(item.startedAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                AvatarRow(item.participants, maxVisible = 5)
                item.winner?.let { winner ->
                    Text(
                        text = stringResource(R.string.history_winner_label, winner.name, winner.score),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                item.currentTopStand?.let { top ->
                    Text(
                        text = stringResource(R.string.history_top_stand_label, top.name, top.score),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

@Composable
fun StatusBadge(status: HistoryStatus) {
    val (color, label) = when (status) {
        HistoryStatus.OPEN -> MaterialTheme.colorScheme.tertiary to stringResource(R.string.history_status_open)
        HistoryStatus.COMPLETED -> MaterialTheme.colorScheme.primary to stringResource(R.string.history_status_completed)
        HistoryStatus.ABANDONED -> MaterialTheme.colorScheme.outline to stringResource(R.string.history_status_abandoned)
    }
    Box(
        modifier = Modifier
            .background(color, RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(label, color = Color.White, style = MaterialTheme.typography.labelSmall)
    }
}
```

### Routing-Anpassung in MainScaffold

```kotlin
composable(Routes.TAB_HISTORY) {
    HistoryScreen(
        onOpenGame = { gameId, isClosed ->
            if (isClosed) navController.navigate(gameSummaryRoute(gameId))
            else navController.navigate(gameRoute(gameId))
        }
    )
}
```

`HistoryPlaceholderScreen.kt` loeschen.

### formatRelativeDate Helper

```kotlin
// ui/util/DateFormatting.kt
fun formatRelativeDate(epochMillis: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - epochMillis
    val days = diff / (24 * 60 * 60 * 1000L)
    return when {
        days < 1 -> "heute"
        days < 2 -> "gestern"
        days < 7 -> "vor $days Tagen"
        days < 30 -> "vor ${days / 7} Wochen"
        else -> DateFormat.getDateInstance().format(Date(epochMillis))
    }
}
```

### Strings (Ergaenzungen)

```xml
<string name="history_title">Historie</string>
<string name="history_search">Suchen</string>
<string name="history_status_open">Läuft</string>
<string name="history_status_completed">Abgeschlossen</string>
<string name="history_status_abandoned">Abgebrochen</string>
<string name="history_fallback_name">Spiel vom %1$s</string>
<string name="history_winner_label">Gewinner: %1$s mit %2$d Punkten</string>
<string name="history_top_stand_label">Aktuell führend: %1$s mit %2$d Punkten</string>
<string name="history_empty">Noch keine Spiele gespielt</string>
<string name="history_empty_filtered">Keine Spiele entsprechen den Filtern</string>
<string name="history_player_filter">Spieler-Filter</string>
```

---

## Akzeptanzkriterien

- [ ] Historie-Tab zeigt `HistoryScreen` (nicht mehr Platzhalter)
- [ ] Alle Spiele werden geladen, chronologisch sortiert
- [ ] Filter "Status" funktioniert (Chips multi-select)
- [ ] Filter "Spieler" funktioniert (multi-select)
- [ ] Suchfeld filtert in Echtzeit (debounced)
- [ ] Listen-Eintraege zeigen alle Infos korrekt (Status, Name, Avatare, Datum, Sieger/TopStand)
- [ ] Tap auf abgeschlossenes Spiel → GameSummary
- [ ] Tap auf laufendes Spiel → GameInProgress
- [ ] Empty States (komplett leer / gefiltert leer) angezeigt
- [ ] Filter-State bleibt beim Tab-Wechsel erhalten (ViewModel)
- [ ] Alle Texte aus `strings.xml`
- [ ] Build erfolgreich

---

## Hinweise

- **`displayName`**: im MVP nie gesetzt (NewGameScreen hat kein Namens-Feld). Fallback wird also immer angezeigt. Spec fuer optionales Namens-Feld kommt evtl. spaeter.
- **Performance bei vielen Spielen**: `LazyColumn` mit stable `key`. Komplexe Berechnungen (Winner, TopStand) im ViewModel im Hintergrund-Thread.
- **Reactive Updates**: wenn ein Spiel im anderen Screen geschlossen wird, soll die Liste sich automatisch aktualisieren (via Flow).
