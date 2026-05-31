# Phase 08 – Manuelle Karteneingabe (PlayerHandEntryScreen)

## Anweisung an Claude Code

Lies diese Datei vollstaendig. Lies anschliessend `00-vision.md` fuer den uebergreifenden Kontext. Setze danach diese Phase **vollstaendig** um.

Voraussetzung: Phase 07 ist abgeschlossen. RoundEntryScreen verlinkt auf Platzhalter.

---

## Kontext (kurz)

Der Platzhalter `PlayerHandEntryPlaceholderScreen` wird durch einen echten **`PlayerHandEntryScreen`** ersetzt. Der User waehlt 7 Karten plus ggf. Joker-Belegungen.

**Wichtig:** Im MVP gilt der **Spannungs-Modus** – der Score wird waehrend der Eingabe **nicht** angezeigt. Nur "X / 7 Karten erfasst" als Status. Der `OptimalSolver` darf nur die Joker-Belegung setzen, aber keinen Score verraten.

Nach Eingabe der 7. Karte + aller Joker: grosser Button "Erfasst – an naechsten Spieler weitergeben" → zurueck zu RoundEntry, Status des Spielers ist nun "Erfasst".

Volle Vision: siehe `00-vision.md`.

---

## Scope dieser Phase

### Drin
- Datenmodell `HandCard` + DAO + Repository-Erweiterung
- `PlayerHandEntryScreen` mit:
  - 7 Karten-Slots (analog Sandbox aus Phase 05, ohne Score)
  - Joker-Bereich (sichtbar nur wenn Joker in der Hand)
  - "Optimal"-Button → setzt nur die Belegung, kein Score sichtbar
  - Button "Erfasst – an naechsten Spieler weitergeben" (deaktiviert bis vollstaendig)
- Hintergrund-Berechnung: Score wird **berechnet und gespeichert**, aber nicht angezeigt
- `RoundEntryScreen` lebt jetzt: zeigt Status "Erfasst" fuer Spieler die durch sind
- CardPicker wird wiederverwendet aus Phase 04

### Explizit NICHT drin
- Keine Score-Anzeige (Reveal kommt Phase 09)
- Keine RoundSummary (Phase 09)
- Kein Mittelfeld-Scan (Phase 09 oder spaeter)
- Kein Edit-Mode fuer abgeschlossene Erfassungen (Phase 09)

---

## Was am Ende funktionieren muss

**Klick-Pfad:**

1. Spiel → "Naechste Runde" → RoundEntry zeigt 3 Spieler "Noch nicht erfasst"
2. Tap auf Spieler 1 → `PlayerHandEntryScreen`
3. Header: "Spieler: Maria"
4. 7 leere Slots, Status: "0 / 7 Karten"
5. Karten auswaehlen via CardPicker (alle 53 verfuegbar)
6. Status updated: "1/7", "2/7", ... "7/7"
7. Wenn Joker dabei: Joker-Bereich erscheint, Joker muss aufgeloest werden
8. "Optimal"-Button: setzt Joker-Belegung, **keine Score-Anzeige**
9. Wenn alles vollstaendig: Button "Erfasst – an naechsten Spieler weitergeben" wird aktiv
10. Tap → zurueck zu RoundEntry, Spieler 1 zeigt jetzt "Erfasst"
11. Tap auf Spieler 2 → derselbe Flow
12. Nach allen 3 Spielern: alle "Erfasst"
13. RoundEntry zeigt jetzt aktivierten Button "Punkte enthuellen" → fuehrt zu Platzhalter `RevealPlaceholderScreen`

---

## Tech-Stack fuer diese Phase

Keine neuen Dependencies.

---

## Architektur-Vorgaben

### Neue Domain-Modelle

```kotlin
// domain/model/HandCard.kt
data class HandCard(
    val id: String,                  // UUID
    val roundResultId: String,
    val cardKey: String,
    val position: Int,               // 0..6
    val jokerTargetCardKey: String?, // null bei Nicht-Joker
    val jokerTargetSuit: String?,    // fuer Buch der Wandlungen
    val createdAt: Long,
    val updatedAt: Long
)
```

### Room-Entity

```kotlin
@Entity(
    tableName = "hand_cards",
    foreignKeys = [ForeignKey(RoundResultEntity::class, ["id"], ["roundResultId"], onDelete = CASCADE)],
    indices = [Index("roundResultId")]
)
data class HandCardEntity(...)
```

DB-Version 4.

### DAOs

```kotlin
@Dao
interface HandCardDao {
    @Insert suspend fun insert(card: HandCardEntity)
    @Insert suspend fun insertAll(cards: List<HandCardEntity>)
    @Query("DELETE FROM hand_cards WHERE roundResultId = :rrId")
    suspend fun deleteAllForRoundResult(rrId: String)
    @Query("SELECT * FROM hand_cards WHERE roundResultId = :rrId ORDER BY position")
    suspend fun getForRoundResult(rrId: String): List<HandCardEntity>
}
```

### Repository

```kotlin
interface HandCardRepository {
    suspend fun saveHand(
        roundId: String,
        profileId: String,
        cards: List<HandCardEntry>,
        totalScore: Int
    )
    // legt ggf. RoundResult an, ersetzt vorhandene HandCards (transactional)

    suspend fun getHand(roundId: String, profileId: String): SavedHand?
}

data class HandCardEntry(
    val cardKey: String,
    val position: Int,
    val jokerTargetCardKey: String?,
    val jokerTargetSuit: String?
)

data class SavedHand(
    val cards: List<HandCardEntry>,
    val totalScore: Int
)
```

`saveHand` ist Transaktional:
- Wenn RoundResult fuer (roundId, profileId) existiert: update Score + delete-and-insert Karten
- Wenn nicht: insert RoundResult + insert Karten
- Setzt `Round.updatedAt` + `Game.updatedAt`

### PlayerHandEntryViewModel

```kotlin
data class PlayerHandEntryUiState(
    val roundId: String,
    val profileId: String,
    val playerName: String,
    val slots: List<CardSlot> = List(7) { CardSlot.Empty },
    val jokerAssignments: Map<String, JokerAssignment> = emptyMap(),
    val cardsCount: Int = 0,
    val allJokersResolved: Boolean = false,
    val canSubmit: Boolean = false,
    val isSaving: Boolean = false
)

class PlayerHandEntryViewModel(
    private val cardLookup: CardLookup,
    private val handCardRepo: HandCardRepository,
    private val engine: ScoringEngine,
    private val optimalSolver: OptimalSolver,
    private val roundId: String,
    private val profileId: String,
    private val profileRepo: ProfileRepository
) : ViewModel() {

    val uiState: StateFlow<PlayerHandEntryUiState>

    fun setCardInSlot(slotIndex: Int, card: CardDefinition)
    fun clearSlot(slotIndex: Int)
    fun setJokerAssignment(jokerKey: String, assignment: JokerAssignment)
    fun applyOptimal()  // setzt nur Assignments, kein Score-Update sichtbar
    fun submit(onSuccess: () -> Unit)  // berechnet Score, speichert via Repository
}
```

**`applyOptimal()`:**
- Ruft `OptimalSolver.findOptimal(currentHand)` auf
- Setzt nur `jokerAssignments` aus dem Resultat
- Score wird **nicht** in UI-State gespiegelt

**Score-Berechnung im Hintergrund (fuer DB-Persistenz):**
- Bei `submit()`: ScoringEngine wird einmal aufgerufen mit final Hand + Assignments
- `totalScore` aus Resultat wird in `saveHand` mitgegeben

### PlayerHandEntryScreen

```kotlin
@Composable
fun PlayerHandEntryScreen(
    roundId: String,
    profileId: String,
    onSubmitDone: () -> Unit,
    onBack: () -> Unit
) {
    val vm: PlayerHandEntryViewModel = viewModel(...)
    val state by vm.uiState.collectAsState()
    var pickerForSlot by remember { mutableStateOf<Int?>(null) }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.player_hand_title, state.playerName)) }, navigationIcon = { BackButton(onBack) }) }
    ) { padding ->
        Column(Modifier.padding(padding).padding(16.dp).verticalScroll(rememberScrollState())) {

            Text(
                text = stringResource(R.string.player_hand_count, state.cardsCount),
                style = MaterialTheme.typography.titleMedium
            )

            Spacer(Modifier.height(16.dp))

            HandSlotsRow(slots = state.slots, onSlotTap = { idx -> pickerForSlot = idx })

            if (state.slots.any { it is CardSlot.Filled && (it.card as CardDefinition).isJoker }) {
                Spacer(Modifier.height(24.dp))
                JokerSection(
                    jokers = state.slots.mapNotNull { (it as? CardSlot.Filled)?.card }.filter { it.isJoker },
                    assignments = state.jokerAssignments,
                    allCards = cardLookup.getAll(),
                    handCards = state.slots.mapNotNull { (it as? CardSlot.Filled)?.card },
                    onAssignmentChange = vm::setJokerAssignment,
                    onOptimal = vm::applyOptimal,
                    showScoreInOptimal = false  // <- entscheidend
                )
            }

            Spacer(Modifier.height(24.dp))

            Button(
                onClick = { vm.submit(onSubmitDone) },
                enabled = state.canSubmit && !state.isSaving,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.player_hand_submit))
            }
        }
    }

    pickerForSlot?.let { slotIdx ->
        CardPicker(
            allCards = cardLookup.getAll(),
            excludedKeys = state.slots.mapNotNull { (it as? CardSlot.Filled)?.card?.key }.toSet() - (state.slots[slotIdx] as? CardSlot.Filled)?.card?.key.orEmpty(),
            onCardChosen = { card ->
                vm.setCardInSlot(slotIdx, card)
                pickerForSlot = null
            },
            showClearButton = state.slots[slotIdx] is CardSlot.Filled,
            onClear = {
                vm.clearSlot(slotIdx)
                pickerForSlot = null
            },
            onDismiss = { pickerForSlot = null }
        )
    }
}
```

**Achtung:** Hand-Karten sollten typischerweise **nicht doppelt** auswaehlbar sein (eine Karte gibt's nur einmal im Spiel). `excludedKeys`-Logik wendet das an.

### RoundEntryScreen lebt jetzt

`RoundEntryViewModel` observiert `RoundResult`-Eintraege via Repository und mappt sie auf den Status:
- Eintrag existiert + hat 7 HandCards → `COMPLETED`
- sonst → `NOT_STARTED`

Wenn alle `COMPLETED`: zeigt zusaetzlich Button "Punkte enthuellen" → fuehrt zu Platzhalter `RevealPlaceholderScreen`.

### Routing-Anpassungen

```kotlin
const val PLAYER_HAND_ENTRY = "round/{roundId}/player/{profileId}"
const val REVEAL_PLACEHOLDER = "round/{roundId}/reveal_placeholder"

fun playerHandEntryRoute(roundId: String, profileId: String) = "round/$roundId/player/$profileId"
fun revealPlaceholderRoute(roundId: String) = "round/$roundId/reveal_placeholder"
```

`PLAYER_HAND_PLACEHOLDER` und `PlayerHandEntryPlaceholderScreen.kt` loeschen.

### Strings (Ergaenzungen)

```xml
<string name="player_hand_title">Spieler: %1$s</string>
<string name="player_hand_count">%1$d / 7 Karten</string>
<string name="player_hand_submit">Erfasst – an nächsten Spieler weitergeben</string>
<string name="player_hand_joker_section">Joker auflösen</string>
<string name="player_hand_optimal">Optimal setzen</string>
<string name="round_entry_reveal_button">Punkte enthüllen</string>
<string name="placeholder_reveal">Reveal kommt hier hin</string>
```

---

## Akzeptanzkriterien

- [ ] DB-Schema erweitert um `hand_cards` (Version 4)
- [ ] `HandCardRepository.saveHand` ist transaktional (RoundResult + HandCards)
- [ ] `PlayerHandEntryScreen` aus RoundEntry erreichbar
- [ ] 7 Karten via CardPicker waehlbar
- [ ] Karten koennen nicht doppelt gewaehlt werden (excludedKeys)
- [ ] Status "X / 7 Karten" wird live aktualisiert
- [ ] **Kein Score wird angezeigt** waehrend Eingabe
- [ ] Joker-Bereich erscheint wenn Joker in der Hand
- [ ] "Optimal"-Button setzt Belegung, zeigt **keinen Score**
- [ ] Submit-Button deaktiviert bis Hand vollstaendig + alle Joker aufgeloest
- [ ] Submit speichert Hand + berechneten Score in DB
- [ ] Nach Submit: zurueck zu RoundEntry, Spieler-Status "Erfasst"
- [ ] Wenn alle Spieler erfasst: "Punkte enthuellen"-Button aktiv → Platzhalter
- [ ] Alle Texte aus `strings.xml`
- [ ] Build + Tests gruen

---

## Hinweise

- **Engine wird im Hintergrund aufgerufen**, der berechnete Score wird in der DB persistiert (fuer spaeteren Reveal). UI zeigt ihn nicht.
- **Editing nach Submit** im PlayerHandEntry: wenn ein Spieler nochmal eingegeben wird (Tap im RoundEntry auf "Erfasst"-Spieler), wird die vorhandene Hand geladen → `vm.loadExistingHand()` im Init.
- **Wichtig fuer spaeter:** Der Score wird zwar gespeichert, ist aber im Reveal die "Wahrheit" – nicht neu berechnet. Das stellt sicher dass Joker-Auswahl konsistent bleibt.
