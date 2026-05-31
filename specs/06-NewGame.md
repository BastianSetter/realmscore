# Phase 06 – Neues Spiel anlegen (NewGameScreen)

## Anweisung an Claude Code

Lies diese Datei vollstaendig. Lies anschliessend `00-vision.md` fuer den uebergreifenden Kontext. Setze danach diese Phase **vollstaendig** um.

Voraussetzung: Phase 05 ist abgeschlossen. Sandbox + Scoring-Engine funktionieren.

---

## Kontext (kurz)

In dieser Phase wird der `NewGamePlaceholderScreen` durch einen echten **`NewGameScreen`** ersetzt. Der User waehlt Spielmodus, Limit/Rundenzahl und Spieler aus. Spieler werden ueber **Autocomplete** auf bekannte Profile gesucht; ein **neuer Name = neues Profil** (wird implizit angelegt).

Nach dem Spielanlegen landet der User auf einem neuen Platzhalter `GameInProgressPlaceholderScreen`.

Volle Vision: siehe `00-vision.md`.

---

## Scope dieser Phase

### Drin
- Datenmodell `Game`, `GameParticipant` + Room-Entities + DAOs + Repository
- Erweiterung `ProfileRepository`: `searchByNamePrefix`, `existsByName`, `createProfile`
- `NewGameScreen` mit:
  - Spielmodus-Auswahl (Feste Runden / Punktelimit)
  - Limit/Rundenzahl-Input
  - Spieler-Liste mit Autocomplete + Hinzufuegen-Button
  - Owner ist vorausgewaehlt und immer dabei
  - "Spiel starten"-Button → legt Spiel an, navigiert zu Platzhalter
- `GameInProgressPlaceholderScreen` ("Spiel-Uebersicht kommt hier hin")
- Automatische Farbvergabe fuer neue Profile (Round-Robin aus Palette)

### Explizit NICHT drin
- Keine echte Spiel-Uebersicht (Phase 07)
- Keine Spiel-Loesch-Funktion im Hauptmenue
- Kein Spiel-Namens-Feld (Spec deferred zu Phase 13, dort wird's vielleicht im Home-Tab gepflegt)

---

## Was am Ende funktionieren muss

**Klick-Pfad:**

1. Hauptmenue → "Neues Spiel"
2. `NewGameScreen` oeffnet sich
3. Spielmodus waehlen: "Feste Runden" oder "Punktelimit"
4. Bei "Feste Runden": Number-Input "Anzahl Runden" (Default 3)
5. Bei "Punktelimit": Number-Input "Zielpunkte" (Default 1000)
6. Spieler-Liste: Owner ist drin (nicht entfernbar)
7. Text-Feld "Spieler hinzufuegen" – Tippen schlaegt bekannte Profile vor
8. Tap auf Vorschlag: Profil wird hinzugefuegt
9. Enter / "Hinzufuegen" mit neuem Namen: legt neues Profil an, fuegt es hinzu
10. Bei Duplikat-Namen: Fehlermeldung
11. Min. 2, max. 6 Spieler (sonst "Spiel starten" deaktiviert)
12. "Spiel starten" → Spiel + GameParticipants in DB, Navigation zu Platzhalter
13. Platzhalter zeigt "Spiel-Uebersicht kommt hier hin" + Back-Button
14. Back fuehrt zurueck zum Home-Tab (NICHT zu NewGameScreen)

---

## Tech-Stack fuer diese Phase

Keine neuen Dependencies. Verwendung von:
- **Room** (neue Entities + DAOs)
- **Compose Material 3** (`OutlinedTextField`, `RadioButton`, `Chip`, `DropdownMenu`)

---

## Architektur-Vorgaben

### Neue Domain-Modelle

```kotlin
// domain/model/Game.kt
data class Game(
    val id: String,                  // UUID
    val displayName: String?,        // optional, Phase 2 editierbar
    val mode: GameMode,
    val targetRounds: Int?,          // bei FIXED_ROUNDS
    val targetPoints: Int?,          // bei POINT_LIMIT
    val startedAt: Long,
    val closedAt: Long?,             // null = offen
    val closedReason: ClosedReason?, // null = offen
    val createdAt: Long,
    val updatedAt: Long,
    val originDeviceId: String
)

enum class GameMode { FIXED_ROUNDS, POINT_LIMIT }
enum class ClosedReason { COMPLETED, ABANDONED }

// domain/model/GameParticipant.kt
data class GameParticipant(
    val gameId: String,
    val profileId: String,
    val seatOrder: Int,              // 0-basiert
    val lastScanOrder: Int?          // wird spaeter genutzt
)
```

### Room-Entities

```kotlin
@Entity(tableName = "games")
data class GameEntity(
    @PrimaryKey val id: String,
    val displayName: String?,
    val mode: String,                // GameMode als String
    val targetRounds: Int?,
    val targetPoints: Int?,
    val startedAt: Long,
    val closedAt: Long?,
    val closedReason: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val originDeviceId: String
)

@Entity(
    tableName = "game_participants",
    primaryKeys = ["gameId", "profileId"],
    foreignKeys = [
        ForeignKey(GameEntity::class, ["id"], ["gameId"], onDelete = CASCADE),
        ForeignKey(ProfileEntity::class, ["id"], ["profileId"])
    ],
    indices = [Index("profileId")]
)
data class GameParticipantEntity(
    val gameId: String,
    val profileId: String,
    val seatOrder: Int,
    val lastScanOrder: Int?
)
```

DB-Version auf 2 erhoehen, Migration definieren (oder destructive falls nur Dev).

### DAOs

```kotlin
@Dao
interface GameDao {
    @Insert suspend fun insert(game: GameEntity)
    @Insert suspend fun insertParticipants(participants: List<GameParticipantEntity>)
    @Transaction
    suspend fun insertGameWithParticipants(game: GameEntity, participants: List<GameParticipantEntity>)
    @Query("SELECT * FROM games WHERE closedAt IS NULL ORDER BY updatedAt DESC")
    fun observeOpenGames(): Flow<List<GameEntity>>
    @Query("SELECT * FROM games WHERE id = :id")
    suspend fun getById(id: String): GameEntity?
    @Query("UPDATE games SET updatedAt = :ts WHERE id = :id")
    suspend fun touch(id: String, ts: Long)
}

@Dao
interface ProfileDao {
    // bestehende Methoden + NEUE:
    @Query("SELECT * FROM profiles WHERE name LIKE :prefix || '%' COLLATE NOCASE ORDER BY name COLLATE NOCASE")
    suspend fun searchByNamePrefix(prefix: String): List<ProfileEntity>
    @Query("SELECT COUNT(*) FROM profiles WHERE name = :name COLLATE NOCASE")
    suspend fun countByName(name: String): Int
    @Query("SELECT colorArgb FROM profiles")
    suspend fun getAllColors(): List<Int>
    @Query("SELECT * FROM profiles WHERE id = :id")
    suspend fun getById(id: String): ProfileEntity?
}
```

### Repositories

`GameRepository` neu, `ProfileRepository` erweitern:

```kotlin
interface GameRepository {
    fun observeOpenGames(): Flow<List<Game>>
    suspend fun getById(id: String): Game?
    suspend fun startGame(
        mode: GameMode,
        target: Int,
        participantProfileIds: List<String>,
        displayName: String? = null
    ): Game
}

interface ProfileRepository {
    // bestehend:
    suspend fun getLocalOwner(): Profile?
    suspend fun createOwner(name: String): Profile

    // NEU:
    suspend fun searchByNamePrefix(prefix: String): List<Profile>
    suspend fun existsByName(name: String): Boolean
    suspend fun createProfile(name: String): Profile
    suspend fun getById(id: String): Profile?
}
```

`ProfileRepositoryImpl.createProfile`:
- Validierung: nicht leer, nicht duplikat (case-insensitive)
- Farbe via Round-Robin aus 8er-Palette (siehe Spec 07 im Specs-Ordner)
- ID generieren wie bei Owner
- `isLocalOwner = false`

### NewGameScreen UI

```kotlin
@Composable
fun NewGameScreen(
    onGameStarted: (gameId: String) -> Unit,
    onBack: () -> Unit
) {
    val vm: NewGameViewModel = viewModel(factory = ...)
    val state by vm.uiState.collectAsState()

    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.new_game_title)) }, navigationIcon = { BackButton(onBack) }) }) { padding ->
        Column(modifier = Modifier.padding(padding).padding(16.dp).verticalScroll(rememberScrollState())) {
            // Mode-Auswahl (RadioButtons)
            ModeSection(state.mode, onModeChange = vm::setMode)

            // Limit/Rundenzahl
            TargetSection(state.mode, state.targetValue, onChange = vm::setTarget)

            Spacer(Modifier.height(16.dp))

            // Spieler-Liste
            PlayersSection(
                participants = state.participants,
                ownerProfileId = state.ownerProfileId,
                onRemove = vm::removeParticipant
            )

            // Hinzufuegen
            AddPlayerField(
                query = state.addQuery,
                suggestions = state.suggestions,
                error = state.addError,
                onQueryChange = vm::onQueryChange,
                onAddExisting = vm::addExistingProfile,
                onAddNew = vm::addNewProfile
            )

            Spacer(Modifier.height(24.dp))

            Button(
                onClick = { vm.startGame(onGameStarted) },
                enabled = state.canStart,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.new_game_start))
            }
        }
    }
}
```

### NewGameViewModel

```kotlin
data class ParticipantRow(
    val profileId: String,
    val name: String,
    val colorArgb: Int,
    val isOwner: Boolean
)

data class NewGameUiState(
    val mode: GameMode = GameMode.FIXED_ROUNDS,
    val targetValue: Int = 3,
    val participants: List<ParticipantRow> = emptyList(),
    val ownerProfileId: String? = null,
    val addQuery: String = "",
    val suggestions: List<Profile> = emptyList(),
    val addError: String? = null,
    val canStart: Boolean = false
)

class NewGameViewModel(
    private val profileRepo: ProfileRepository,
    private val gameRepo: GameRepository
) : ViewModel() {
    val uiState: StateFlow<NewGameUiState>

    init {
        viewModelScope.launch {
            val owner = profileRepo.getLocalOwner()!!
            // initial state mit Owner als ersten Participant
        }
    }

    fun setMode(mode: GameMode)
    fun setTarget(value: Int)
    fun onQueryChange(query: String)  // debounced 150 ms -> suggestions aktualisieren
    fun addExistingProfile(profile: Profile)
    fun addNewProfile(name: String)
    fun removeParticipant(profileId: String)
    fun startGame(onSuccess: (String) -> Unit)
}
```

**`canStart`-Logik:**
- Min 2 Participants, max 6
- Mode + Target sind gesetzt
- Target > 0

**Autocomplete-Logik:**
- Bei jedem Tastendruck: debounced 150 ms
- `profileRepo.searchByNamePrefix(query)` → Vorschlaege
- Aus den Vorschlaegen werden bereits gewaehlte Participants entfernt

**`addNewProfile(name)`:**
- Trim
- Validation: nicht leer, nicht duplikat (`existsByName` case-insensitive)
- Bei Fehler: `addError` setzen
- Bei Erfolg: `profileRepo.createProfile(name)` → in Liste aufnehmen, `addQuery` leeren

### Routing-Anpassungen

In `Routes.kt`:
```kotlin
const val NEW_GAME = "new_game"
const val GAME_IN_PROGRESS_PLACEHOLDER = "game_in_progress_placeholder/{gameId}"
fun gameInProgressRoute(gameId: String) = "game_in_progress_placeholder/$gameId"
```

In `MainScaffold.kt`:
- `NEW_GAME_PLACEHOLDER` durch `NEW_GAME` ersetzen
- `composable(NEW_GAME) { NewGameScreen(onGameStarted = ..., onBack = ...) }`
- `composable(GAME_IN_PROGRESS_PLACEHOLDER) { GameInProgressPlaceholderScreen() }`
- Nach `onGameStarted(gameId)`: `navController.navigate(gameInProgressRoute(gameId)) { popUpTo(TAB_HOME) }`

`NewGamePlaceholderScreen.kt` loeschen.

### Strings (Ergaenzungen)

```xml
<string name="new_game_title">Neues Spiel</string>
<string name="new_game_mode_fixed_rounds">Feste Rundenanzahl</string>
<string name="new_game_mode_point_limit">Punktelimit</string>
<string name="new_game_target_rounds">Anzahl Runden</string>
<string name="new_game_target_points">Zielpunkte</string>
<string name="new_game_players_section">Spieler</string>
<string name="new_game_add_player_label">Spieler hinzufügen</string>
<string name="new_game_add_button">Hinzufügen</string>
<string name="new_game_owner_badge">(Du)</string>
<string name="new_game_start">Spiel starten</string>
<string name="new_game_error_min_players">Mindestens 2 Spieler erforderlich</string>
<string name="new_game_error_max_players">Maximal 6 Spieler erlaubt</string>
<string name="new_game_error_name_exists">Ein Profil mit diesem Namen existiert bereits</string>
<string name="new_game_error_name_empty">Bitte einen Namen eingeben</string>
<string name="placeholder_game_in_progress">Spiel-Übersicht kommt hier hin</string>
```

---

## Akzeptanzkriterien

- [ ] Datenmodell `Game` + `GameParticipant` in DB (Version 2)
- [ ] `GameRepository` und erweitertes `ProfileRepository` funktional
- [ ] `NewGameScreen` aus Home-Tab erreichbar
- [ ] Modus-Auswahl + Target-Input funktioniert
- [ ] Owner ist vorausgewaehlt und nicht entfernbar
- [ ] Autocomplete schlaegt bekannte Profile case-insensitive vor
- [ ] Tap auf Vorschlag uebernimmt das Profil
- [ ] Neuer Name + Hinzufuegen legt neues Profil an (mit auto-Farbe)
- [ ] Duplikat-Namen werden mit Fehler abgewiesen
- [ ] "Spiel starten" disabled bei < 2 oder > 6 Spielern
- [ ] Tap auf "Spiel starten" legt `Game` + alle `GameParticipants` in einer Transaktion an
- [ ] Navigation fuehrt zu `GameInProgressPlaceholderScreen` mit `gameId`
- [ ] Back vom Platzhalter fuehrt zum Home-Tab (nicht zu NewGame)
- [ ] Alle Texte aus `strings.xml`
- [ ] Build erfolgreich

---

## Hinweise

- **DB-Migration vs. destructive:** im Dev-Zustand kann destructive akzeptiert werden. Pflicht ist nur, dass die neue Version 2 laeuft.
- **Owner-Profile-Filter im Autocomplete:** bereits gewaehlte Participants ausblenden, Owner inklusive
- **Farb-Round-Robin:** `pickNextColor()`-Logik aus Spec 07 verwenden
- **Spiel-Anlegen als Transaktion:** `@Transaction`-Methode in `GameDao` verwenden, damit Game + Participants atomar landen
