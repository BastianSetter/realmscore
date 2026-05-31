# Phase 09 – Reveal + RoundSummary

## Anweisung an Claude Code

Lies diese Datei vollstaendig. Lies anschliessend `00-vision.md` fuer den uebergreifenden Kontext. Setze danach diese Phase **vollstaendig** um.

Voraussetzung: Phase 08 ist abgeschlossen. Karten koennen pro Spieler eingegeben werden.

---

## Kontext (kurz)

Wenn alle Spieler einer Runde ihre Karten erfasst haben, kann der User auf "Punkte enthuellen" tippen. Es oeffnet sich der **`RevealScreen`** mit dramatischer sequentieller Aufloesung: Spieler werden von niedrigster zu hoechster Punktzahl aufgedeckt, mit animiertem Count-Up.

Nach dem Reveal landet der User auf **`RoundSummaryScreen`** – die finale Rundenuebersicht mit Karten-Aufschluesselung pro Spieler, Mini-Diagrammen und Buttons "Naechste Runde" / "Spiel abschliessen".

Volle Vision: siehe `00-vision.md`.

---

## Scope dieser Phase

### Drin
- `RevealScreen` mit sequentieller Animation
  - Spieler von niedrig → hoch
  - Count-Up der Punkte
  - Krone beim Sieger
  - Skip-Optionen (Komplett-Skip + Tap-to-skip)
- `RoundSummaryScreen` mit:
  - Sieger-Krone
  - Spieler-Karten mit Punkten + Mini-Stacked-Bar
  - Tap auf Spieler → Karten-Aufschluesselung (Bottom-Sheet, gleiche Komponente wie Sandbox)
  - Mittelfeld-Sektion (im MVP optional, falls erfasst – ansonsten Hinweis)
  - Button "Naechste Runde starten" (wenn keine Punktelimit-Erreichung)
  - Button "Spiel abschliessen" (immer verfuegbar) → Platzhalter `GameSummaryPlaceholderScreen`
  - Button "Diese Runde bearbeiten" (nur fuer die letzte Runde, sofern keine neue gestartet wurde)
  - Button "Reveal erneut zeigen"
- Editing-Modus: wenn User "Bearbeiten" tippt, navigiert er zurueck zu RoundEntry, kann einzelne Spieler nochmal eingeben
- Round als "completed" markieren: `Round.completedAt` setzen wenn alle 7 Karten pro Spieler erfasst sind UND der User mindestens einmal das Reveal gesehen hat (oder "Naechste Runde"/"Spiel abschliessen" tippt)

### Explizit NICHT drin
- Kein GameSummary (Phase 10)
- Keine Move-to-Sandbox-Buttons (Phase 14)
- Keine Stats-Integration

---

## Was am Ende funktionieren muss

**Klick-Pfad:**

1. Alle Spieler einer Runde erfasst → "Punkte enthuellen"
2. `RevealScreen`: dunkler Hintergrund, Spieler werden einer nach dem anderen eingeblendet, Punkte zaehlen hoch
3. Sieger bekommt Krone-Animation
4. Button "Weiter zur Rundenuebersicht" oder Tap auf Bildschirm fuer naechsten Spieler
5. `RoundSummaryScreen`: alle Spieler mit Punkten + Mini-Diagramm
6. Tap auf einen Spieler → Bottom-Sheet mit Karten-Aufschluesselung
7. Tap auf "Naechste Runde starten" → neue Runde, `RoundEntryScreen`
8. Tap auf "Spiel abschliessen" → `GameSummaryPlaceholderScreen`
9. Tap auf "Diese Runde bearbeiten" → zurueck zu RoundEntry, Karten editierbar
10. Tap auf "Reveal erneut zeigen" → `RevealScreen` nochmal

---

## Tech-Stack fuer diese Phase

Keine neuen Dependencies. Animationen via `animate*AsState` aus Compose.

---

## Architektur-Vorgaben

### Routes

```kotlin
const val REVEAL = "round/{roundId}/reveal"
const val ROUND_SUMMARY = "round/{roundId}/summary"
const val GAME_SUMMARY_PLACEHOLDER = "game/{gameId}/summary_placeholder"

fun revealRoute(roundId: String) = "round/$roundId/reveal"
fun roundSummaryRoute(roundId: String) = "round/$roundId/summary"
fun gameSummaryPlaceholderRoute(gameId: String) = "game/$gameId/summary_placeholder"
```

Das alte `REVEAL_PLACEHOLDER` wird durch `REVEAL` ersetzt.

### RevealScreen

```kotlin
@Composable
fun RevealScreen(
    roundId: String,
    onDone: () -> Unit,
    onSkip: () -> Unit
) {
    val vm: RevealViewModel = viewModel(...)
    val state by vm.uiState.collectAsState()

    Box(
        modifier = Modifier.fillMaxSize()
            .background(Color.Black)
            .clickable { vm.revealNext() }
    ) {
        // TopBar mit "Ueberspringen"-Button
        // In der Mitte: aktuell aufgedeckter Spieler
        AnimatedContent(
            targetState = state.currentRevealIndex,
            transitionSpec = { slideInVertically() + fadeIn() with slideOutVertically() + fadeOut() }
        ) { index ->
            if (index < state.players.size) {
                PlayerRevealCard(state.players[index], isWinner = (index == state.players.size - 1))
            } else {
                CompletionContent(onContinue = onDone)
            }
        }
    }
}

data class PlayerReveal(
    val profileId: String,
    val name: String,
    val colorArgb: Int,
    val finalScore: Int,
    val topThreeCards: List<String>  // Kartennamen
)

class RevealViewModel(...) : ViewModel() {
    val uiState: StateFlow<RevealUiState>
    fun revealNext()  // Index +1, max bei players.size
}

data class RevealUiState(
    val players: List<PlayerReveal> = emptyList(),  // sortiert aufsteigend nach Score
    val currentRevealIndex: Int = -1  // -1 = noch nichts aufgedeckt
)
```

**PlayerRevealCard:**
- Avatar + Name gross
- Count-Up von 0 zum finalen Score (Animation 1.5 s via `animateIntAsState`)
- Mini-Cards mit Top-3-Karten als Chips
- Bei `isWinner`: Krone-Icon mit Scale-Animation

**Skip-Logik:**
- Top-Bar "Ueberspringen" → springt direkt zu `onSkip()` (= Navigation zu RoundSummary)
- Tap auf Hintergrund → `revealNext()`
- Nach dem letzten Spieler: Button "Weiter zur Rundenuebersicht"

### RoundSummaryScreen

```kotlin
@Composable
fun RoundSummaryScreen(
    roundId: String,
    onNextRound: () -> Unit,
    onCompleteGame: () -> Unit,
    onEditRound: () -> Unit,
    onShowRevealAgain: () -> Unit,
    onBackToGame: () -> Unit
) {
    val vm: RoundSummaryViewModel = viewModel(...)
    val state by vm.uiState.collectAsState()
    var breakdownProfileId by remember { mutableStateOf<String?>(null) }

    Scaffold(...) { padding ->
        Column(Modifier.padding(padding).padding(16.dp).verticalScroll(rememberScrollState())) {

            Text(stringResource(R.string.round_summary_title, state.roundNumber), style = MaterialTheme.typography.headlineSmall)

            Spacer(Modifier.height(16.dp))

            state.players.forEach { player ->
                PlayerSummaryCard(
                    player = player,
                    isWinner = player.profileId == state.winnerId,
                    onTap = { breakdownProfileId = player.profileId }
                )
                Spacer(Modifier.height(8.dp))
            }

            // Optional: Mittelfeld
            if (state.discardScanned) {
                DiscardSection(state.discardCards)
            } else {
                DiscardScanHint()
            }

            Spacer(Modifier.height(24.dp))

            // Buttons
            if (state.isLastRound && !state.canEditRound) {
                // sollte selten passieren
            }

            if (state.canStartNextRound) {
                Button(onClick = onNextRound, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.round_summary_next_round))
                }
                Spacer(Modifier.height(8.dp))
            }

            OutlinedButton(onClick = onCompleteGame, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.round_summary_complete_game))
            }

            if (state.canEditRound) {
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onEditRound) {
                    Text(stringResource(R.string.round_summary_edit_round))
                }
            }

            TextButton(onClick = onShowRevealAgain) {
                Text(stringResource(R.string.round_summary_reveal_again))
            }
        }
    }

    breakdownProfileId?.let { profileId ->
        ScoreBreakdownSheet(
            roundId = roundId,
            profileId = profileId,
            onDismiss = { breakdownProfileId = null }
        )
    }
}

data class PlayerSummary(
    val profileId: String,
    val name: String,
    val colorArgb: Int,
    val totalScore: Int,
    val positiveContribution: Int,
    val negativeContribution: Int,
    val blankedScore: Int
)

data class RoundSummaryUiState(
    val roundNumber: Int,
    val players: List<PlayerSummary>,  // sortiert nach Score absteigend
    val winnerId: String?,
    val discardScanned: Boolean,
    val discardCards: List<CardDefinition>,
    val canStartNextRound: Boolean,
    val canEditRound: Boolean,
    val isLastRound: Boolean
)
```

**`canEditRound` Logik:**
- True wenn diese Runde die zuletzt-gestartete ist UND keine neue Runde nach ihr existiert
- False sobald eine neue Runde gestartet wurde

**`canStartNextRound`:**
- Bei FIXED_ROUNDS: `currentRoundNumber < game.targetRounds`
- Bei POINT_LIMIT: kein Spieler ueber Limit oder User entscheidet ueber "Spiel abschliessen"

### ScoreBreakdownSheet (Wiederverwendung)

Komponente wurde in Phase 05 fuer die Sandbox gebaut. Hier wird sie ueber ein **eigenes ViewModel** wiederverwendet:

```kotlin
@Composable
fun ScoreBreakdownSheet(
    roundId: String,
    profileId: String,
    onDismiss: () -> Unit
) {
    val vm: BreakdownViewModel = viewModel(...)
    val result by vm.scoringResult.collectAsState()
    val cards = LocalAppContainer.current.cardLookup

    ModalBottomSheet(onDismissRequest = onDismiss) {
        result?.let { CardBreakdownList(it, cards) }
    }
}

class BreakdownViewModel(
    private val handCardRepo: HandCardRepository,
    private val engine: ScoringEngine,
    private val cardLookup: CardLookup,
    private val roundId: String,
    private val profileId: String
) : ViewModel() {
    val scoringResult: StateFlow<ScoringResult?>

    init {
        viewModelScope.launch {
            val saved = handCardRepo.getHand(roundId, profileId)
            if (saved != null) {
                val hand = saved.cards.map { cardLookup.getByKey(it.cardKey)!! }
                val jokers = saved.cards.filter { it.jokerTargetCardKey != null }
                    .associate { it.cardKey to JokerAssignment(it.cardKey, it.jokerTargetCardKey!!) }
                val result = engine.score(ScoringInput(hand, jokers))
                scoringResult.value = result
            }
        }
    }
}
```

> Score wird hier **neu berechnet** zur Aufschluesselung. Das ist OK, weil idempotent.

### Round-Completion-Logik

`Round.completedAt` wird gesetzt wenn:
- alle Spieler einer Runde haben ihre HandCards UND
- User hat mindestens einmal "Punkte enthuellen" oder "Naechste Runde" oder "Spiel abschliessen" gedrueckt

Implementation: `vm.markRoundAsCompleted(roundId)` im `RevealViewModel.onInit` oder `RoundSummaryViewModel.onInit`.

### Strings (Ergaenzungen)

```xml
<string name="round_summary_title">Runde %1$d</string>
<string name="round_summary_next_round">Nächste Runde starten</string>
<string name="round_summary_complete_game">Spiel abschließen</string>
<string name="round_summary_edit_round">Diese Runde bearbeiten</string>
<string name="round_summary_reveal_again">Reveal erneut zeigen</string>
<string name="round_summary_skip">Überspringen</string>
<string name="round_summary_continue_to_summary">Weiter zur Rundenübersicht</string>
<string name="round_summary_discard_hint">Mittelfeld wurde nicht erfasst</string>
<string name="placeholder_game_summary">Spielende kommt hier hin</string>
```

---

## Akzeptanzkriterien

- [ ] "Punkte enthuellen" navigiert zu `RevealScreen`
- [ ] RevealScreen zeigt Spieler in aufsteigender Score-Reihenfolge
- [ ] Count-Up-Animation funktioniert fluessig
- [ ] Sieger erhaelt Krone-Animation
- [ ] Skip-Button (komplett) + Tap-to-skip (pro Spieler) funktionieren
- [ ] Nach Reveal landet User auf `RoundSummaryScreen`
- [ ] RoundSummary zeigt: Header, Spieler-Cards mit Punkten + Mini-Diagrammen, Sieger markiert
- [ ] Tap auf Spieler-Card oeffnet `ScoreBreakdownSheet`
- [ ] Aufschluesselung zeigt korrekte Beitraege pro Karte mit lokalisierten Effekt-Beschreibungen
- [ ] Geblankete Karten sind markiert
- [ ] Joker zeigen ihr Ziel
- [ ] "Naechste Runde starten" legt neue Runde an, navigiert zu RoundEntry
- [ ] "Spiel abschliessen" navigiert zu `GameSummaryPlaceholderScreen`
- [ ] "Diese Runde bearbeiten" navigiert zurueck zu RoundEntry
- [ ] "Reveal erneut zeigen" oeffnet RevealScreen nochmal
- [ ] `Round.completedAt` wird gesetzt nach erstem Reveal/Aktion
- [ ] Alle Texte aus `strings.xml`
- [ ] Build + Tests gruen

---

## Hinweise

- **Animations-Performance**: Compose Hot-Reload nutzen waehrend Entwicklung. Falls Reveal ruckelt: `key()` um die `AnimatedContent` setzen
- **Score in RevealScreen**: kommt aus `RoundResult.totalScore` (in Phase 08 persistiert). Nicht neu berechnen.
- **Aufschluesselung**: Neu berechnen ist OK (idempotent)
- **Mittelfeld**: Im MVP ohne Kamera ist `discardScanned` immer false. Hinweis "wurde nicht erfasst" wird also immer angezeigt. Das ist OK – die UI ist trotzdem konsistent.
