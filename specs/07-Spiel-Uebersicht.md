# Phase 07 – Spiel-Uebersicht (GameInProgressScreen + RoundEntryScreen)

## Anweisung an Claude Code

Lies diese Datei vollstaendig. Lies anschliessend `00-vision.md` fuer den uebergreifenden Kontext. Setze danach diese Phase **vollstaendig** um.

Voraussetzung: Phase 06 ist abgeschlossen. Spiele koennen angelegt werden.

---

## Kontext (kurz)

Der Platzhalter `GameInProgressPlaceholderScreen` wird durch einen echten **`GameInProgressScreen`** ersetzt. Dieser zeigt das aktuelle Spiel mit Spielstand-Tabelle und einem Button "Naechste Runde starten".

Beim Tap auf "Naechste Runde" wird eine neue `Round` in der DB angelegt und der **`RoundEntryScreen`** geoeffnet. Dieser zeigt alle Spieler als Eintraege mit Status "Noch nicht erfasst". Tap auf einen Spieler-Eintrag fuehrt zum neuen Platzhalter **`PlayerHandEntryPlaceholderScreen`** ("Karten-Eingabe kommt hier hin").

Volle Vision: siehe `00-vision.md`.

---

## Scope dieser Phase

### Drin
- Datenmodell `Round` + `RoundResult` (RoundResult vorbereiten, noch nicht befuellen)
- `RoundRepository` mit `startRound`, `getOpenRound`, `observeRoundsForGame`
- `GameInProgressScreen`:
  - Header: Spiel-Modus + Spieler-Avatare
  - Spielstand-Tabelle (leer am Anfang)
  - Button "Naechste Runde starten" → legt offene Runde an, navigiert zu RoundEntry
  - Wenn schon Runden existieren: Tabelle wird gefuellt
  - Wenn offene Runde existiert: prominenter "Runde fortsetzen"-Button
- `RoundEntryScreen`:
  - Liste aller Spieler mit Status "Noch nicht erfasst"
  - Tap fuehrt zu `PlayerHandEntryPlaceholderScreen` mit `roundId` + `profileId`
- `PlayerHandEntryPlaceholderScreen` ("Karten-Eingabe kommt hier hin")
- `GetGameStateUseCase` (berechnet Spielstand aus Rohdaten)

### Explizit NICHT drin
- Keine echte Karten-Eingabe (Phase 08)
- Kein Reveal (Phase 09)
- Keine RoundSummary (Phase 09)
- Kein Spielende-Flow (Phase 10)
- Keine Rundenstatus "Erfasst" – wird in Phase 08 sichtbar

---

## Was am Ende funktionieren muss

**Klick-Pfad:**

1. Hauptmenue → "Neues Spiel" → Spiel anlegen → landet auf `GameInProgressScreen`
2. Spielstand-Tabelle ist leer ("Noch keine Runden gespielt")
3. Tap auf "Naechste Runde starten" → neue `Round` in DB → Navigation zu `RoundEntryScreen`
4. `RoundEntryScreen` zeigt: "Runde 1" als Header + Liste der Spieler, alle mit Status "Noch nicht erfasst"
5. Tap auf einen Spieler → `PlayerHandEntryPlaceholderScreen` mit "Karten-Eingabe kommt hier hin" + Back-Button
6. Back fuehrt zurueck zu `RoundEntryScreen`
7. Back von `RoundEntryScreen` fuehrt zu `GameInProgressScreen`
8. **Wieder-Oeffnen:** Wenn man die App schliesst und ueber Home-Tab → (Phase 13 spaeter) erneut zu diesem Spiel navigiert, ist der Stand erhalten (Runde 1 ist noch offen)

> Da Phase 13 ("Games to continue" im Home-Tab) noch nicht implementiert ist, wird das Wieder-Oeffnen aktuell **nur ueber Deep-Link** moeglich sein. Im Test reicht: neues Spiel anlegen, Runde starten, App killen, neu starten → Onboarding wird uebersprungen, aber man landet im Hauptmenue **ohne** Direktlink. Das ist OK fuer diese Phase.

---

## Tech-Stack fuer diese Phase

Keine neuen Dependencies.

---

## Architektur-Vorgaben

### Neue Domain-Modelle

```kotlin
// domain/model/Round.kt
data class Round(
    val id: String,                 // UUID
    val gameId: String,
    val roundNumber: Int,           // 1-basiert
    val startedAt: Long,
    val completedAt: Long?,         // null = noch offen
    val discardScanned: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val originDeviceId: String
)

// domain/model/RoundResult.kt
data class RoundResult(
    val id: String,                 // UUID
    val roundId: String,
    val profileId: String,
    val totalScore: Int,
    val createdAt: Long,
    val updatedAt: Long,
    val originDeviceId: String
)
```

### Room-Entities

Analog zu Domain-Modellen, mit ForeignKeys auf `games` und `rounds` jeweils mit CASCADE-Delete. DB-Version 3.

```kotlin
@Entity(tableName = "rounds", foreignKeys = [...], indices = [Index("gameId")])
data class RoundEntity(...)

@Entity(tableName = "round_results", foreignKeys = [...], indices = [Index("roundId"), Index("profileId")])
data class RoundResultEntity(...)
```

### DAOs

```kotlin
@Dao
interface RoundDao {
    @Insert suspend fun insert(round: RoundEntity)
    @Query("SELECT * FROM rounds WHERE gameId = :gameId AND completedAt IS NULL")
    suspend fun getOpenRound(gameId: String): RoundEntity?
    @Query("SELECT * FROM rounds WHERE gameId = :gameId ORDER BY roundNumber ASC")
    fun observeRoundsForGame(gameId: String): Flow<List<RoundEntity>>
    @Query("SELECT MAX(roundNumber) FROM rounds WHERE gameId = :gameId")
    suspend fun getMaxRoundNumber(gameId: String): Int?
}

@Dao
interface RoundResultDao {
    @Query("SELECT * FROM round_results WHERE roundId = :roundId")
    fun observeResultsForRound(roundId: String): Flow<List<RoundResultEntity>>
}
```

### Repositories

```kotlin
interface RoundRepository {
    fun observeRoundsForGame(gameId: String): Flow<List<Round>>
    suspend fun getOpenRound(gameId: String): Round?
    suspend fun startRound(gameId: String): Round
    fun observeResults(roundId: String): Flow<List<RoundResult>>
}
```

`startRound`:
- maxRoundNumber + 1 als neue `roundNumber`
- Falls offene Runde existiert: returns diese statt eine neue zu starten
- Setzt `Game.updatedAt`

### GetGameStateUseCase

```kotlin
// domain/usecase/game/GetGameStateUseCase.kt
data class GameState(
    val game: Game,
    val participants: List<GameParticipantWithProfile>,
    val rounds: List<Round>,
    val resultsByRoundAndProfile: Map<Pair<String, String>, Int>, // (roundId, profileId) → score
    val totalScoresByProfile: Map<String, Int>,
    val leadingProfileId: String?,
    val isPointLimitReached: Boolean,
    val hasOpenRound: Boolean
)

data class GameParticipantWithProfile(
    val participant: GameParticipant,
    val profile: Profile
)

class GetGameStateUseCase(
    private val gameRepo: GameRepository,
    private val roundRepo: RoundRepository,
    private val profileRepo: ProfileRepository
) {
    fun observe(gameId: String): Flow<GameState>
}
```

### GameInProgressScreen

```kotlin
@Composable
fun GameInProgressScreen(
    gameId: String,
    onStartRound: (roundId: String) -> Unit,
    onBack: () -> Unit
) {
    val vm: GameInProgressViewModel = viewModel(...)
    val state by vm.uiState.collectAsState()

    Scaffold(...) { padding ->
        Column(Modifier.padding(padding).padding(16.dp)) {
            GameHeader(state.game, state.participants)
            ScoreTable(state)

            Spacer(Modifier.height(16.dp))

            if (state.hasOpenRound) {
                Button(onClick = { vm.continueOpenRound(onStartRound) }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.game_continue_round))
                }
            } else {
                Button(onClick = { vm.startNextRound(onStartRound) }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.game_start_round))
                }
            }
        }
    }
}
```

### ScoreTable

Einfache `LazyColumn` oder `Column`:
- Header-Zeile: "Spieler | R1 | R2 | ... | Gesamt"
- Pro Spieler eine Zeile, sortiert nach Gesamt absteigend
- Bei `POINT_LIMIT`-Modus: extra Spalte "Fortschritt"
- Leere Zellen mit "—"

### RoundEntryScreen

```kotlin
@Composable
fun RoundEntryScreen(
    roundId: String,
    onEnterPlayer: (profileId: String) -> Unit,
    onBack: () -> Unit
) {
    val vm: RoundEntryViewModel = viewModel(...)
    val state by vm.uiState.collectAsState()

    Scaffold(...) { padding ->
        Column(Modifier.padding(padding).padding(16.dp)) {
            Text(stringResource(R.string.round_entry_title, state.roundNumber), style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(16.dp))
            state.players.forEach { player ->
                PlayerRow(player, onClick = { onEnterPlayer(player.profileId) })
            }
        }
    }
}

data class PlayerEntryRow(
    val profileId: String,
    val name: String,
    val colorArgb: Int,
    val status: PlayerEntryStatus  // NOT_STARTED, COMPLETED (in dieser Phase nur NOT_STARTED)
)

enum class PlayerEntryStatus { NOT_STARTED, COMPLETED }
```

In dieser Phase ist `status` immer `NOT_STARTED`, da noch keine Karten eingegeben werden koennen. Phase 08 macht's lebendig.

### Routing

```kotlin
const val GAME_IN_PROGRESS = "game/{gameId}"
const val ROUND_ENTRY = "round/{roundId}"
const val PLAYER_HAND_PLACEHOLDER = "round/{roundId}/player/{profileId}/placeholder"

fun gameRoute(gameId: String) = "game/$gameId"
fun roundEntryRoute(roundId: String) = "round/$roundId"
fun playerHandPlaceholderRoute(roundId: String, profileId: String) = "round/$roundId/player/$profileId/placeholder"
```

`GAME_IN_PROGRESS_PLACEHOLDER` raus, `GAME_IN_PROGRESS` rein.

### Strings (Ergaenzungen)

```xml
<string name="game_start_round">Nächste Runde starten</string>
<string name="game_continue_round">Runde fortsetzen</string>
<string name="game_no_rounds_yet">Noch keine Runden gespielt</string>
<string name="game_total">Gesamt</string>
<string name="game_progress_to_limit">%1$d / %2$d</string>
<string name="round_entry_title">Runde %1$d</string>
<string name="round_entry_status_not_started">Noch nicht erfasst</string>
<string name="round_entry_status_completed">Erfasst</string>
<string name="placeholder_player_hand">Karten-Eingabe kommt hier hin</string>
```

---

## Akzeptanzkriterien

- [ ] DB-Schema erweitert um `rounds` + `round_results` (Version 3)
- [ ] `RoundRepository` funktional
- [ ] `GetGameStateUseCase` berechnet State korrekt
- [ ] Nach Spielanlegen landet User auf `GameInProgressScreen` (nicht Platzhalter)
- [ ] Spielstand-Tabelle wird angezeigt (leer am Anfang)
- [ ] Spieler-Liste im Header korrekt
- [ ] "Naechste Runde starten" legt offene Runde in DB an
- [ ] Navigation zu `RoundEntryScreen` mit korrekter `roundId`
- [ ] `RoundEntryScreen` zeigt Runden-Nummer + alle Spieler mit Status "Noch nicht erfasst"
- [ ] Tap auf Spieler navigiert zu `PlayerHandEntryPlaceholderScreen` mit Parametern
- [ ] Back von RoundEntry → GameInProgress; Back von GameInProgress → Hauptmenue
- [ ] Wenn schon eine offene Runde existiert, zeigt GameInProgress "Runde fortsetzen" statt "Neue Runde"
- [ ] Alle Texte aus `strings.xml`
- [ ] Build + bestehende Tests gruen

---

## Hinweise

- **`GetGameStateUseCase`** macht den State-aus-Historie-Ansatz greifbar. In allen folgenden Phasen wird dieser UseCase weiter genutzt.
- **Punktelimit-Pruefung** in `isPointLimitReached`: Default false. In Phase 10 wird das den "Spiel beenden"-Button beeinflussen.
- **Mehrere offene Runden in einem Game** sollte es nicht geben. `startRound` prueft das via `getOpenRound`.
