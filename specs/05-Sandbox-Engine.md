# Phase 05 – Sandbox mit Scoring-Engine + Punkte-Anzeige

## Anweisung an Claude Code

Lies diese Datei vollstaendig. Lies anschliessend `00-vision.md` fuer den uebergreifenden Kontext. Setze danach diese Phase **vollstaendig** um.

Voraussetzung: Phase 04 ist abgeschlossen. Sandbox hat 53 echte Karten und CardPicker.

---

## Kontext (kurz)

In dieser Phase wird die **Scoring-Engine** implementiert und in die Sandbox eingebunden. Die Engine berechnet Punkte aus einer Hand von 7 Karten unter Beachtung von Boni, Strafen, Multiplikatoren, Blanking-Effekten und Joker-Auflösung.

Die Sandbox bekommt:
- Live-Score-Anzeige (statt hartkodiert 0)
- Joker-Auswahl-UI mit "Optimal"-Button
- Aufschluesselungs-Bottom-Sheet (welche Karte gibt wieviele Punkte)

**Wichtig:** Diese Phase ist die **erste, in der die Scoring-Engine sichtbar funktioniert**. Sie ist gleichzeitig der Verifikations-Zeitpunkt fuer die Engine-Logik.

Volle Vision: siehe `00-vision.md`.

---

## Scope dieser Phase

### Drin
- Karten-JSON um echte `bonuses`, `penalties`, `specialKey` ergaenzen (alle 53 Karten regeltechnisch korrekt)
- Bonus-/Penalty-/Effect-Domain-Typen
- `ScoringEngine` als pure Kotlin-Klasse mit Order-of-Operations
- `OptimalSolver` (Brute-Force fuer Joker-Belegung)
- `SpecialEffectRegistry` fuer komplexe Karten (Edelstein der Ordnung, Welteneschen, Daemon, etc.)
- Unit-Tests fuer Engine (mindestens 10 Test-Klassen mit Bekannten-Hand-Szenarien)
- Sandbox-UI-Erweiterungen:
  - Joker-Bereich (Auswahl + "Optimal"-Button)
  - Live-Score-Update bei jeder Aenderung
  - "Aufschluesselung"-Button → Bottom-Sheet mit Karten-Details

### Explizit NICHT drin
- Kein Mittelfeld-Bereich in der Sandbox (kommt evtl. spaeter, hier nicht zwingend)
- Keine Persistierung der Sandbox-Hand (Sandbox bleibt In-Memory)
- Kein Move-to-Sandbox aus Historie (Phase 14)

---

## Was am Ende funktionieren muss

**Klick-Pfad:**

1. Sandbox oeffnen
2. 7 Karten wahllos waehlen (z.B. Drache, Koenig, Wald, Magier, Schwert, Bestienmeister, Inseln)
3. Score-Footer zeigt **berechneten Score** in Echtzeit
4. Eine Joker-Karte einbauen (z.B. Doppelgaenger) → Joker-Bereich erscheint mit Dropdown
5. Joker manuell setzen → Score updated sich
6. "Optimal"-Button → Joker wird auf die punktemaximierende Karte gesetzt, Score updated
7. Tap auf "Aufschluesselung" → Bottom-Sheet zeigt Karten-Liste mit Beitraegen pro Karte
8. Reset funktioniert weiterhin

---

## Tech-Stack fuer diese Phase

Keine neuen Dependencies. Pure Kotlin Logik + bestehende Compose-Komponenten.

---

## Architektur-Vorgaben

### Erweiterte Karten-JSON

Jetzt mit echten Boni, Strafen, Specials. Beispiel:

```json
{
  "key": "king",
  "nameDe": "König",
  "suit": "LEADER",
  "baseStrength": 8,
  "ruleTextDe": "+5 für jede Armee. +20 wenn Königin in der Hand ist.",
  "bonuses": [
    {"type": "PER_OTHER_CARD", "filter": {"key": "army"}, "amount": 5},
    {"type": "IF_HAND_CONTAINS", "filter": {"key": "queen"}, "amount": 20}
  ],
  "penalties": [],
  "isJoker": false,
  "specialKey": null
}
```

**Bonus-/Penalty-Sprache** wird in `06_scoring_logik.md` (im Specs-Ordner) detailliert beschrieben. Die wichtigsten Typen:

- `PER_OTHER_CARD` – Bonus pro Karte mit Filter
- `IF_HAND_CONTAINS` – einmaliger Bonus wenn Karte/Suit in Hand
- `IF_HAND_DOES_NOT_CONTAIN` – einmaliger Bonus wenn fehlt
- `PER_SUIT_COUNT` – Bonus pro Karten eines Suits
- `MULTIPLY_OWN_STRENGTH` – Multiplikator auf eigene Staerke
- `BLANK` – blankt andere Karten (mit Filter)

`specialKey`-Werte (verweisen auf `SpecialEffectRegistry`):
- `gem_of_order` – Edelstein der Ordnung (Sequenz-Bonus)
- `world_tree` – Welteneschen
- `demon` – Daemon (Blanking-Logik)
- `necromancer` – Totenbeschwoerer (Mittelfeld-Interaktion)

> Im Specs-Ordner: `06_scoring_logik.md` enthaelt die volle Beschreibung der Engine, Order-of-Operations und der Special-Handler-Registry. Diese Datei darf ergaenzend referenziert werden.

### Domain-Typen

```kotlin
// domain/scoring/Bonus.kt
sealed class Bonus {
    data class PerOtherCard(val filter: CardFilter, val amount: Int) : Bonus()
    data class IfHandContains(val filter: CardFilter, val amount: Int) : Bonus()
    data class IfHandDoesNotContain(val filter: CardFilter, val amount: Int) : Bonus()
    data class PerSuitCount(val suit: Suit, val amount: Int) : Bonus()
    data class MultiplyOwnStrength(val factor: Int, val condition: Condition?) : Bonus()
}

sealed class Penalty {
    data class FixedIfMissing(val filter: CardFilter, val amount: Int) : Penalty()  // negative amount
    data class BlankSelf(val condition: Condition) : Penalty()
    ...
}

data class CardFilter(
    val key: String? = null,
    val suit: Suit? = null,
    val anySuit: Set<Suit> = emptySet()
)

sealed class Condition {
    data class HandContains(val filter: CardFilter) : Condition()
    data class HandDoesNotContain(val filter: CardFilter) : Condition()
    ...
}

// domain/scoring/ScoringResult.kt
data class ScoringResult(
    val totalScore: Int,
    val perCard: List<CardScoreResult>
)

data class CardScoreResult(
    val cardKey: String,
    val contributedScore: Int,
    val isBlanked: Boolean,
    val effects: List<EffectApplication>
)

data class EffectApplication(
    val sourceCardKey: String,
    val descriptionKey: String,
    val descriptionArgs: List<String>,
    val pointsDelta: Int
)
```

### ScoringEngine

```kotlin
// domain/scoring/ScoringEngine.kt
class ScoringEngine(
    private val specialRegistry: SpecialEffectRegistry
) {
    fun score(input: ScoringInput): ScoringResult {
        // 1. Joker aufloesen (substitute Karten)
        // 2. Buch der Wandlungen anwenden (Suit-Aenderung)
        // 3. Strafen-Aufheben pruefen (Karten die Strafen anderer aufheben)
        // 4. Blanking ermitteln (Daemon, Drache, etc.)
        // 5. Boni anwenden auf nicht-geblankte Karten
        // 6. Strafen anwenden auf nicht-geblankte Karten
        // 7. Specials anwenden (ueber Registry)
        // 8. Summe bilden
    }
}

data class ScoringInput(
    val hand: List<CardDefinition>,
    val jokerAssignments: Map<String, JokerAssignment>,
    val discardPile: List<CardDefinition> = emptyList()
)

data class JokerAssignment(
    val jokerCardKey: String,
    val targetCardKey: String,
    val targetSuit: Suit? = null  // fuer Buch der Wandlungen
)
```

### OptimalSolver

```kotlin
class OptimalSolver(
    private val engine: ScoringEngine,
    private val allCards: List<CardDefinition>
) {
    fun findOptimal(
        hand: List<CardDefinition>,
        discardCards: List<CardDefinition> = emptyList()
    ): OptimalResult {
        // Brute-Force ueber alle Joker-Belegungen
        // Bei Totenbeschwoerer: zusaetzlich Mittelfeld-Swaps testen
    }
}

data class OptimalResult(
    val bestAssignments: Map<String, JokerAssignment>,
    val bestScoring: ScoringResult
)
```

### SpecialEffectRegistry

```kotlin
interface SpecialEffectHandler {
    fun apply(card: CardDefinition, context: ScoringContext): SpecialEffect
}

class SpecialEffectRegistry {
    private val handlers = mapOf(
        "gem_of_order" to GemOfOrderHandler(),
        "world_tree" to WorldTreeHandler(),
        "demon" to DemonHandler(),
        "necromancer" to NecromancerHandler()
    )

    fun get(specialKey: String): SpecialEffectHandler? = handlers[specialKey]
}
```

### Unit-Tests (Pflicht!)

`domain/src/test/kotlin/scoring/`:

- `ScoringEngineBasicTest` – einfache Boni/Strafen
- `ScoringEngineJokerTest` – Joker-Aufloesung
- `ScoringEngineBlankingTest` – Daemon-/Drachen-Blanking
- `ScoringEngineMultiplyTest` – Multiplikatoren (Sumpf etc.)
- `ScoringEngineSpecialGemOfOrderTest`
- `ScoringEngineSpecialWorldTreeTest`
- `ScoringEngineSpecialNecromancerTest`
- `OptimalSolverTest`
- `EdgeCaseTest` – leere Hand, < 7 Karten, alle Joker

Mindestens 30 Test-Faelle insgesamt. Bekannte Hand-Szenarien aus der offiziellen Anleitung als Test-Cases verwenden, falls verfuegbar.

### Sandbox-UI-Erweiterungen

In `SandboxScreen`:

**Joker-Bereich (sichtbar nur wenn mind. 1 Joker in der Hand):**
```kotlin
@Composable
fun JokerSection(
    jokers: List<CardDefinition>,
    assignments: Map<String, JokerAssignment>,
    allCards: List<CardDefinition>,
    onAssignmentChange: (String, JokerAssignment) -> Unit,
    onOptimal: () -> Unit
) {
    Column {
        Text("Joker-Auflösung", style = MaterialTheme.typography.titleMedium)
        jokers.forEach { joker ->
            JokerRow(joker, assignments[joker.key], allCards, onAssignmentChange)
        }
        Button(onClick = onOptimal) { Text(stringResource(R.string.sandbox_optimal)) }
    }
}
```

**Live-Score-Aufruf:** ViewModel berechnet bei jedem State-Change den Score:

```kotlin
class SandboxViewModel(
    private val cardLookup: CardLookup,
    private val engine: ScoringEngine,
    private val optimalSolver: OptimalSolver
) : ViewModel() {
    private val _uiState = MutableStateFlow(SandboxUiState())
    val uiState: StateFlow<SandboxUiState>

    init {
        viewModelScope.launch {
            // beobachte Slots + JokerAssignments, berechne Score reaktiv
        }
    }

    fun setCardInSlot(slotIndex: Int, card: CardDefinition)
    fun setJokerAssignment(jokerKey: String, assignment: JokerAssignment)
    fun applyOptimal()
    fun openBreakdown()
}

data class SandboxUiState(
    val slots: List<CardSlot> = List(7) { CardSlot.Empty },
    val jokerAssignments: Map<String, JokerAssignment> = emptyMap(),
    val score: Int = 0,
    val scoringResult: ScoringResult? = null,
    val breakdownOpen: Boolean = false
)
```

**Aufschluesselungs-Bottom-Sheet:**
```kotlin
@Composable
fun ScoreBreakdownSheet(
    result: ScoringResult,
    cardLookup: CardLookup,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn {
            items(result.perCard.sortedByDescending { it.contributedScore }) { cardResult ->
                CardBreakdownItem(cardResult, cardLookup)
            }
        }
    }
}

@Composable
fun CardBreakdownItem(result: CardScoreResult, lookup: CardLookup) {
    val card = lookup.getByKey(result.cardKey) ?: return
    var expanded by remember { mutableStateOf(false) }
    Card(onClick = { expanded = !expanded }) {
        Column(Modifier.padding(12.dp)) {
            Row {
                Text(card.nameDe, modifier = Modifier.weight(1f))
                Text(formatScore(result.contributedScore))
            }
            if (expanded) {
                result.effects.forEach { effect ->
                    Text(stringResource(effect.descriptionKey, *effect.descriptionArgs.toTypedArray()))
                }
            }
        }
    }
}
```

### Strings (Ergaenzungen, neben den dynamischen Effect-Strings)

```xml
<string name="sandbox_optimal">Optimal</string>
<string name="sandbox_breakdown">Aufschlüsselung</string>
<string name="sandbox_joker_section_title">Joker-Auflösung</string>
<string name="sandbox_joker_target">Spiegelt</string>
```

Plus alle Effect-Beschreibungen, z.B.:
```xml
<string name="effect_king_bonus_per_army">+5 pro Armee (×%1$s)</string>
<string name="effect_king_bonus_with_queen">+20 da Königin in der Hand</string>
...
```

(Diese Strings werden nach und nach angelegt waehrend der Engine-Implementierung)

---

## Akzeptanzkriterien

- [ ] `assets/cards/base_game.json` enthaelt alle 53 Karten mit korrekten Boni, Strafen, Specials laut offizieller Anleitung
- [ ] `ScoringEngine.score(input)` liefert fuer Test-Haende korrekte Punkte (siehe Unit-Tests)
- [ ] Mindestens 30 Test-Faelle gruen
- [ ] Sandbox-UI:
  - [ ] Score wird live aktualisiert bei jeder Aenderung
  - [ ] Joker-Bereich erscheint wenn Joker in der Hand
  - [ ] Joker-Auswahl funktioniert (Dropdown mit allen passenden Karten)
  - [ ] "Optimal"-Button setzt Joker-Belegung
  - [ ] "Aufschluesselung" oeffnet Bottom-Sheet mit Karten-Details
  - [ ] Effekt-Beschreibungen werden lokalisiert angezeigt (aus strings.xml)
- [ ] Geblankete Karten werden in der Aufschluesselung als solche markiert
- [ ] Reset leert auch Joker-Belegungen
- [ ] F-Droid-Check: keine Google-Dependencies dazugekommen
- [ ] Build + Tests gruen

---

## Hinweise

- **Engine ist pure Kotlin**: keine Android-Imports. Liegt in `domain/scoring/`. Unit-Tests via `kotlin-test` oder JUnit, kein Robolectric noetig.
- **Engine ist reentrant + idempotent**: gleicher Input → gleicher Output, kein Seiten-Effekt.
- **OptimalSolver-Performance**: bei 2 Jokern in der Hand kann das mehrere Sekunden dauern. Im UI: Loading-Indicator anzeigen, Berechnung in `Dispatchers.Default`.
- **Spec im Spec-Ordner:** `06_scoring_logik.md` enthaelt die volle Engine-Spec. Diese Datei darf ergaenzend als Referenz dienen.
- **Karten-Regel-Quelle:** unbedingt aus der offiziellen deutschen Strohmann-Anleitung uebernehmen. Bei Unsicherheit oder Mehrdeutigkeit: User fragen statt raten.
