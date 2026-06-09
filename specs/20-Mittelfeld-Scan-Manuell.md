# Phase 20 – Mittelfeld-Scan (manuell)

## Anweisung an Claude Code

Lies diese Datei vollstaendig. Lies anschliessend `00-vision.md`. Setze danach diese Phase vollstaendig um.

Voraussetzung: Phase 19 abgeschlossen.

---

## Kontext (kurz)

Das Mittelfeld (Discard-Pile) kann jetzt manuell erfasst werden – via CardPicker, ohne Kamera. Dafür bekommt der `RoundEntryScreen` einen eigenen "Mittelfeld erfassen"-Button neben den Spieler-Einträgen. Die Mittelfeld-Daten werden in der Statistik und für den Totenbeschwoerer-Effekt genutzt.

---

## Scope

### Drin
- "Mittelfeld erfassen"-Button auf `RoundEntryScreen`
- `DiscardEntryScreen`: CardPicker für variable Anzahl Karten
- Speichern in `discard_cards`-Tabelle, `Round.discardScanned = true`
- Status-Anzeige auf `RoundEntryScreen` ("Mittelfeld: X Karten erfasst" / "Nicht erfasst")
- Totenbeschwörer-Auswahl wird bei gescanntem Mittelfeld auf die erfassten Karten gefiltert + Optimal-Integration (Detail-Verfeinerung von Phase 17.1)
- Settings-Toggle "Mittelfeld-Scan vorschlagen" bleibt erhalten

### Explizit NICHT drin
- Kein Kamera-Scan (Phase 21)
- Keine automatische Erkennung

---

## Was am Ende funktionieren muss

1. `RoundEntryScreen` zeigt neben den Spieler-Einträgen: "Mittelfeld: Nicht erfasst" + Button "Erfassen"
2. Tap auf Button → `DiscardEntryScreen`
3. CardPicker: User wählt beliebig viele Karten (0–52 technisch, typisch 20–30)
4. "Fertig"-Button → zurück zu RoundEntry, Status wechselt auf "Mittelfeld: 28 Karten erfasst ✓"
5. "Bearbeiten"-Button wenn schon erfasst
6. Mittelfeld kann vor UND nach dem Reveal erfasst werden
7. Wenn Totenbeschwörer in einer Hand und Mittelfeld erfasst: die Totenbeschwörer-Auswahl ist auf die Mittelfeld-Karten gefiltert, und der OptimalSolver bezieht sie ein (Aufbau auf Phase 17.1)

---

## DiscardEntryScreen

```kotlin
@Composable
fun DiscardEntryScreen(
    roundId: String,
    onDone: () -> Unit,
    onBack: () -> Unit
) {
    val vm: DiscardEntryViewModel = viewModel(...)
    val state by vm.uiState.collectAsState()

    Scaffold(
        topBar = { TopAppBar(
            title = { Text(stringResource(R.string.discard_entry_title)) },
            navigationIcon = { BackButton(onBack) }
        )},
        bottomBar = {
            Button(
                onClick = { vm.save(onDone) },
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            ) {
                Text(stringResource(R.string.discard_entry_done, state.cards.size))
            }
        }
    ) { padding ->
        Column(Modifier.padding(padding)) {
            Text(stringResource(R.string.discard_entry_hint), Modifier.padding(16.dp))

            // Ausgewaehlte Karten als Chips (entfernbar)
            FlowRow(Modifier.padding(horizontal = 16.dp)) {
                state.cards.forEach { card ->
                    InputChip(
                        selected = true,
                        onClick = { vm.removeCard(card.key) },
                        label = { Text(card.nameDe) },
                        trailingIcon = { Icon(Icons.Default.Close, null) }
                    )
                }
                // "+" Chip zum Hinzufuegen
                InputChip(
                    selected = false,
                    onClick = { vm.openPicker() },
                    label = { Text(stringResource(R.string.discard_entry_add)) },
                    leadingIcon = { Icon(Icons.Default.Add, null) }
                )
            }
        }
    }

    if (state.pickerOpen) {
        CardPicker(
            allCards = state.allCards,
            excludedKeys = state.cards.map { it.key }.toSet(),
            onCardChosen = { card -> vm.addCard(card) },
            onDismiss = { vm.closePicker() }
        )
    }
}
```

---

## RoundEntryScreen Erweiterung

```kotlin
// Bestehender RoundEntryScreen bekommt eine neue Sektion:

// Mittelfeld-Status
Surface(
    modifier = Modifier.fillMaxWidth().padding(16.dp),
    color = MaterialTheme.colorScheme.surfaceVariant,
    shape = RoundedCornerShape(8.dp)
) {
    Row(
        modifier = Modifier.padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.round_entry_discard_label))
            Text(
                text = if (state.discardCardCount > 0)
                    stringResource(R.string.round_entry_discard_count, state.discardCardCount)
                else
                    stringResource(R.string.round_entry_discard_empty),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        OutlinedButton(onClick = { navController.navigate(discardEntryRoute(state.roundId)) }) {
            Text(
                if (state.discardCardCount > 0)
                    stringResource(R.string.action_edit)
                else
                    stringResource(R.string.round_entry_discard_capture)
            )
        }
    }
}
```

---

## Repository-Erweiterung

```kotlin
interface RoundRepository {
    // NEU:
    suspend fun getDiscardCards(roundId: String): List<String>  // cardKeys
    suspend fun saveDiscardCards(roundId: String, cardKeys: List<String>)
    // setzt auch Round.discardScanned = true
}
```

---

## Totenbeschwörer-Integration (baut auf Phase 17.1 auf)

> **Regel-Korrektur (Stand Implementierung):** Der Totenbeschwörer darf in dieser App Karten der
> Suits **Armee, Magier, Heerführer oder Bestie** holen (`CardLookup.NECROMANCER_SUITS =
> {ARMY, WIZARD, LEADER, BEAST}`). Die früher in diesem Dokument verwendete Formulierung
> „Nicht-Magier" ist damit hinfällig – Magier sind ausdrücklich **erlaubt**, andere Suits (Land,
> Wetter, Flut, Flamme, Waffe, Artefakt, WILD-Joker) sind ausgeschlossen. Überall unten, wo
> „Nicht-Magier" steht, gilt stattdessen „eine Karte aus `NECROMANCER_SUITS`".

> **Wichtig:** Der Totenbeschwörer wurde bereits in **Phase 17.1** als Joker-artige Karte umgesetzt – er funktioniert dort **immer**, auch ohne Mittelfeld-Scan, über die volle Auswahl der erfassbaren Suits. Diese Phase ergänzt nur den **gefilterten Fall** und aktiviert die **Optimal-Berechnung**.

Phase 17.1 hat einen Hook hinterlassen, der hier aktiviert wird: die Methode `getNecromancerCandidates(...)` bzw. die markierte Stelle `// PHASE 20: bei gescanntem Mittelfeld auf discardKeys filtern`.

### Was diese Phase am Totenbeschwörer ändert

**1. Gefilterte Auswahl bei gescanntem Mittelfeld**

Die Totenbeschwörer-Kartenauswahl (das Feld unter der Hand aus Phase 17.1) verhält sich jetzt abhängig vom Scan-Status:
- **Mittelfeld NICHT gescannt** → wie bisher: volle CardPicker-UI mit allen Nicht-Magier-Karten
- **Mittelfeld gescannt** → nur die erfassten Mittelfeld-Karten, gefiltert auf Nicht-Magier (kurze Liste statt voller Picker)

```kotlin
// Den in 17.1 vorbereiteten Hook implementieren:
fun getNecromancerCandidates(
    discardScanned: Boolean,
    discardKeys: List<String>,
    handKeys: Set<String>
): List<CardDefinition> {
    return if (discardScanned) {
        // NEU in Phase 20: nur erfasste Mittelfeld-Karten, ohne Magier, ohne Handkarten
        discardKeys
            .mapNotNull { cardLookup.getByKey(it) }
            .filter { it.suit != Suit.WIZARD && it.key !in handKeys }
    } else {
        // wie Phase 17.1: alle Nicht-Magier-Karten
        cardLookup.getNonWizardCards(excludeKeys = handKeys)
    }
}
```

Im UI: Wenn `discardScanned`, wird die Auswahl als kompakte Liste/kleiner Picker dargestellt (wenige Karten). Sonst die volle CardPicker-UI wie in 17.1.

**2. Optimal-Button bezieht Totenbeschwörer ein (nur bei gescanntem Mittelfeld)**

Der in Phase 17.1 deaktivierte Optimal-Pfad wird jetzt aktiviert – aber **nur** wenn `discardScanned == true`:

```kotlin
// OptimalSolver – necromancerPick jetzt optimierbar bei gescanntem Mittelfeld
fun findOptimal(
    hand: List<CardDefinition>,
    discardCards: List<CardDefinition> = emptyList(),
    discardScanned: Boolean = false,
    currentNecromancerPick: String? = null
): OptimalResult {
    // Joker wie gehabt brute-forcen.
    // NEU: wenn discardScanned UND Totenbeschwörer in Hand:
    //   über alle Nicht-Magier-Karten des Mittelfelds brute-forcen,
    //   in Kombination mit den Joker-Belegungen.
    // wenn NICHT discardScanned: currentNecromancerPick unverändert übernehmen (wie 17.1).
}
```

> **Performance-Hinweis:** Bei gescanntem Mittelfeld ist die Totenbeschwörer-Kandidatenliste typisch klein (die erfassten Discard-Karten minus Magier). Die Kombination mit Joker-Permutationen bleibt damit beherrschbar. Falls dennoch viele Joker + großes Mittelfeld zusammenkommen: Berechnung in `Dispatchers.Default` mit Loading-Indicator (wie in Phase 05 für den OptimalSolver).

**3. `OptimalResult` um Totenbeschwörer-Wahl erweitern**

```kotlin
data class OptimalResult(
    val bestAssignments: Map<String, JokerAssignment>,
    val bestNecromancerPick: String? = null,   // NEU: beste Totenbeschwörer-Karte (nur bei discardScanned)
    val bestScoring: ScoringResult
)
```

Bei "Optimal" im UI wird `bestNecromancerPick` dann in den State übernommen (analog zu den Joker-Assignments).

---

## Akzeptanzkriterien

- [ ] "Mittelfeld erfassen"-Button auf RoundEntryScreen sichtbar
- [ ] Status zeigt korrekt "Nicht erfasst" / "X Karten erfasst ✓"
- [ ] `DiscardEntryScreen` öffnet sich mit CardPicker
- [ ] Karten können hinzugefügt und entfernt werden
- [ ] "Fertig" speichert in DB und setzt `discardScanned = true`
- [ ] Bearbeitbar nach dem ersten Erfassen
- [ ] Bearbeitbar auch nach dem Reveal (bis neue Runde startet)
- [ ] `RoundSummaryScreen`: Mittelfeld-Sektion zeigt erfasste Karten
- [ ] Totenbeschwörer-Auswahl ist bei gescanntem Mittelfeld auf die erfassten Nicht-Magier-Karten gefiltert (kurze Liste)
- [ ] Totenbeschwörer-Auswahl ohne Scan zeigt weiter die volle Nicht-Magier-Auswahl (wie Phase 17.1)
- [ ] "Optimal" bezieht die Totenbeschwörer-Karte ein, sobald Mittelfeld gescannt ist
- [ ] "Optimal" lässt die Totenbeschwörer-Wahl unangetastet, wenn Mittelfeld nicht gescannt ist
- [ ] Settings-Toggle "Mittelfeld vorschlagen" funktioniert noch

---

## Strings (Ergänzungen)

```xml
<string name="discard_entry_title">Mittelfeld erfassen</string>
<string name="discard_entry_hint">Wähle alle Karten aus, die im Mittelfeld liegen</string>
<string name="discard_entry_add">Hinzufügen</string>
<string name="discard_entry_done">Fertig (%1$d Karten)</string>
<string name="round_entry_discard_label">Mittelfeld</string>
<string name="round_entry_discard_empty">Noch nicht erfasst</string>
<string name="round_entry_discard_count">%1$d Karten erfasst</string>
<string name="round_entry_discard_capture">Erfassen</string>
```
