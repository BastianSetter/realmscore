# Phase 03 – Sandbox mit Dummy-Karten

## Anweisung an Claude Code

Lies diese Datei vollstaendig. Lies anschliessend `00-vision.md` fuer den uebergreifenden Kontext. Setze danach diese Phase **vollstaendig** um.

Voraussetzung: Phase 02 ist abgeschlossen. Es gibt das Hauptmenue mit Bottom Nav und einen `SandboxPlaceholderScreen`.

---

## Kontext (kurz)

In dieser Phase wird der `SandboxPlaceholderScreen` durch einen echten **`SandboxScreen`** ersetzt. Der Screen hat das UI-Geruest fuer Karten-Slots, Action-Buttons und einen Score-Bereich. Karten werden noch nicht aus einer echten Datenquelle gewaehlt – es gibt **5 hartkodierte Dummy-Karten** zum Befuellen, der Score ist hartkodiert auf `0`.

Diese Phase prueft das **Compose-UI** und die **State-Logik** der Sandbox isoliert, ohne dass die Karten-Datenquelle oder die Scoring-Engine schon existieren muessen.

Volle Vision: siehe `00-vision.md`.

---

## Scope dieser Phase

### Drin
- `SandboxScreen` Composable + `SandboxViewModel`
- 7 Karten-Slots horizontal angeordnet (UI)
- Dummy-CardPicker mit 5 hartkodierten Karten
- Reset-Button (leert alle Slots)
- Score-Bereich (zeigt hartkodiert "0")
- Drag & Drop **NICHT** in dieser Phase – nur Tap-basiertes Befuellen/Entfernen

### Explizit NICHT drin
- Keine echten Karten aus JSON (kommt in Phase 04)
- Keine Scoring-Engine (kommt in Phase 05)
- Kein Joker-Bereich (kommt in Phase 05 mit Engine)
- Kein Mittelfeld-Bereich
- Keine Move-to-Sandbox-Funktionalitaet
- Keine "Aufschluesselung"-Button

---

## Was am Ende funktionieren muss

**Klick-Pfad:**

1. Hauptmenue → Tap auf "Sandbox"-Card
2. `SandboxScreen` oeffnet sich mit 7 leeren Slots
3. Tap auf einen leeren Slot → Bottom-Sheet mit 5 Dummy-Karten
4. Tap auf eine Dummy-Karte → Karte landet im Slot, Bottom-Sheet schliesst
5. Tap auf einen belegten Slot → Bottom-Sheet mit Optionen "Entfernen" und "Andere Karte waehlen"
6. Score-Bereich zeigt immer "0 Punkte"
7. Tap auf "Reset" → alle Slots wieder leer
8. Back-Button → zurueck zum Hauptmenue

---

## Tech-Stack fuer diese Phase

Keine neuen Dependencies. Verwendung von:
- **Jetpack Compose** + **Material 3** (`ModalBottomSheet`, `Card`)
- **kotlinx.coroutines** (StateFlow im ViewModel)

---

## Architektur-Vorgaben

### Neue Ordnerstruktur

```
ui/sandbox/
├── SandboxScreen.kt
├── SandboxViewModel.kt
├── components/
│   ├── HandSlotsRow.kt          # 7 Slots horizontal
│   ├── CardSlot.kt              # einzelner Slot (empty/filled)
│   ├── ScoreFooter.kt           # Sticky Footer mit Score
│   └── DummyCardPicker.kt       # Bottom-Sheet mit Dummy-Karten
└── model/
    └── DummyCard.kt
```

### Dummy-Daten

```kotlin
// ui/sandbox/model/DummyCard.kt
data class DummyCard(
    val id: String,
    val name: String,
    val suit: String   // "Bestien", "Heerführer", "Land", "Magier", "Waffen"
)

val DummyCards = listOf(
    DummyCard("dummy_dragon", "Drache", "Bestien"),
    DummyCard("dummy_king", "König", "Heerführer"),
    DummyCard("dummy_forest", "Wald", "Land"),
    DummyCard("dummy_wizard", "Magier", "Magier"),
    DummyCard("dummy_sword", "Schwert", "Waffen")
)
```

### State-Modell

```kotlin
// ui/sandbox/SandboxViewModel.kt
sealed class CardSlot {
    object Empty : CardSlot()
    data class Filled(val card: DummyCard) : CardSlot()
}

data class SandboxUiState(
    val slots: List<CardSlot> = List(7) { CardSlot.Empty },
    val score: Int = 0  // hartkodiert 0 in dieser Phase
)

class SandboxViewModel : ViewModel() {
    val uiState: StateFlow<SandboxUiState>

    fun setCardInSlot(slotIndex: Int, card: DummyCard)
    fun clearSlot(slotIndex: Int)
    fun reset()
}
```

ViewModel braucht in dieser Phase **kein Repository** – nur In-Memory-State.

### UI-Layout

```kotlin
@Composable
fun SandboxScreen() {
    val vm: SandboxViewModel = viewModel()
    val state by vm.uiState.collectAsState()
    var pickerForSlot by remember { mutableStateOf<Int?>(null) }

    Scaffold(
        topBar = { /* Title + BackButton */ },
        bottomBar = { ScoreFooter(state.score) }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).padding(16.dp)) {
            HandSlotsRow(
                slots = state.slots,
                onSlotTap = { idx -> pickerForSlot = idx }
            )
            Spacer(Modifier.height(16.dp))
            Button(onClick = { vm.reset() }) {
                Text(stringResource(R.string.sandbox_reset))
            }
        }
    }

    pickerForSlot?.let { slotIdx ->
        DummyCardPicker(
            currentSlot = state.slots[slotIdx],
            onCardChosen = { card ->
                vm.setCardInSlot(slotIdx, card)
                pickerForSlot = null
            },
            onClear = {
                vm.clearSlot(slotIdx)
                pickerForSlot = null
            },
            onDismiss = { pickerForSlot = null }
        )
    }
}
```

### HandSlotsRow

- Horizontal scrollable Row mit 7 `CardSlot`-Composables
- Jeder Slot: ca. 80dp breit, 120dp hoch
- Empty: gestrichelter Rahmen + "+"-Icon zentriert
- Filled: Karten-Card (Hintergrundfarbe pro Suit, Name als Text, "x" Icon oben rechts zum Entfernen ist optional – im MVP reicht Tap)

### DummyCardPicker

`ModalBottomSheet` von Material 3:
- Wenn `currentSlot` = `Empty`: 5 Dummy-Karten als Liste/Grid
- Wenn `currentSlot` = `Filled`: zusaetzlich oben "Entfernen"-Button

### ScoreFooter

```kotlin
@Composable
fun ScoreFooter(score: Int) {
    Surface(
        tonalElevation = 4.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.sandbox_score, score),
                style = MaterialTheme.typography.titleLarge
            )
        }
    }
}
```

### Strings (Ergaenzungen)

```xml
<string name="sandbox_title">Sandbox</string>
<string name="sandbox_reset">Zurücksetzen</string>
<string name="sandbox_score">%1$d Punkte</string>
<string name="sandbox_empty_slot">Karte wählen</string>
<string name="sandbox_remove_card">Entfernen</string>
<string name="sandbox_picker_title">Karte wählen</string>
```

### Navigation anpassen

In `MainScaffold.kt` den Eintrag `composable(Routes.SANDBOX_PLACEHOLDER) { SandboxPlaceholderScreen() }` ersetzen durch `composable(Routes.SANDBOX) { SandboxScreen() }`. Route-Name in `Routes.kt` umbenennen: `SANDBOX_PLACEHOLDER` → `SANDBOX`. `SandboxPlaceholderScreen.kt` loeschen.

---

## Akzeptanzkriterien

- [ ] Sandbox aus Home-Tab erreichbar
- [ ] 7 leere Slots werden horizontal angezeigt
- [ ] Tap auf leeren Slot oeffnet Bottom-Sheet mit 5 Dummy-Karten
- [ ] Auswahl einer Karte fuellt den Slot, Bottom-Sheet schliesst
- [ ] Tap auf belegten Slot bietet Entfernen / Wechsel
- [ ] Score zeigt hartkodiert "0 Punkte"
- [ ] Reset leert alle Slots
- [ ] Back fuehrt zurueck zum Hauptmenue, Bottom Nav bleibt sichtbar
- [ ] Alle Texte aus `strings.xml`
- [ ] Build erfolgreich

---

## Hinweise

- **Dummy-Karten** sind explizit als `DummyCard` modelliert (eigener Typ, nicht `CardDefinition`). Das macht den Refactor in Phase 04 sauber: `DummyCard` wird durch `CardDefinition` ersetzt.
- **Slot-Modell** `sealed class CardSlot` bleibt strukturell gleich, nur der `Filled`-Typ wechselt seinen Inhalt.
- **Kein StateFlow-Persistieren** – ViewModel-State ueberlebt nur Configuration Changes, nicht Process Death. Das ist fuer Sandbox OK.
