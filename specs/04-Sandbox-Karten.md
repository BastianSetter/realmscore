# Phase 04 – Sandbox mit echten Karten (53 Karten + CardPicker)

## Anweisung an Claude Code

Lies diese Datei vollstaendig. Lies anschliessend `00-vision.md` fuer den uebergreifenden Kontext. Setze danach diese Phase **vollstaendig** um.

Voraussetzung: Phase 03 ist abgeschlossen. Sandbox hat Slot-UI und Dummy-Karten.

---

## Kontext (kurz)

In dieser Phase wird die **echte Karten-Datenquelle** aufgebaut: ein JSON-File mit allen **53 Grundspiel-Karten**, geladen via kotlinx.serialization in eine `CardLookup`-Klasse. Der Dummy-Picker wird durch einen vollwertigen **CardPicker** ersetzt – mit Such-Funktion, Suit-Filter und Anzeige aller Karten.

Der Score bleibt in dieser Phase weiterhin auf `0` hartkodiert (die Scoring-Engine folgt erst in Phase 05).

Volle Vision: siehe `00-vision.md`.

---

## Scope dieser Phase

### Drin
- `assets/cards/base_game.json` mit allen 53 Grundspiel-Karten
- Domain-Klasse `CardDefinition`
- `CardLookup` (laedt JSON beim App-Start, bietet `getAll()`, `getByKey()`, `searchByName(query)`)
- `CardPicker`-Composable als wiederverwendbare Komponente (wird spaeter auch in Spec 08 verwendet)
- Refactor von Sandbox: `DummyCard` → `CardDefinition`, `DummyCardPicker` → `CardPicker`

### Explizit NICHT drin
- Keine Scoring-Logik (Phase 05)
- Keine Karten-Bilder (nur Name + Suit-Indikator als Text/Farbe; Bilder kommen optional in Polish-Phase 15)
- Keine Erweiterungs-Karten "Der verfluchte Schatz" (Phase 2)

---

## Was am Ende funktionieren muss

**Klick-Pfad:**

1. Sandbox oeffnen
2. Tap auf leeren Slot → CardPicker oeffnet sich
3. CardPicker zeigt alle 53 Karten in einem Grid (z.B. 2-3 Spalten)
4. Such-Feld oben: Tippen filtert in Echtzeit (case-insensitive Praefix-Match auf Name)
5. Suit-Filter-Chips: tippen filtert auf eine oder mehrere Suits
6. Tap auf eine Karte → Slot wird gefuellt, Picker schliesst
7. Score bleibt "0 Punkte"

---

## Tech-Stack fuer diese Phase

Neue Aktivierung:
- **kotlinx.serialization** (war in Phase 01 schon im Catalog, jetzt erstmals genutzt)
- **Compose `LazyVerticalGrid`** + **`FilterChip`** + **`OutlinedTextField`** fuer Suche

---

## Architektur-Vorgaben

### Karten-JSON-Schema

`app/src/main/assets/cards/base_game.json`:

```json
{
  "version": 1,
  "cards": [
    {
      "key": "dragon",
      "nameDe": "Drache",
      "suit": "BEAST",
      "baseStrength": 30,
      "ruleTextDe": "BLANKT: alle Karten ohne Schwert oder Magie",
      "bonuses": [],
      "penalties": [],
      "isJoker": false,
      "specialKey": null
    },
    ...
  ]
}
```

> Wichtig: Diese Phase bringt die **strukturellen** 53 Eintraege ins JSON. Die `bonuses`, `penalties` und `specialKey` koennen erstmal **leer/null** bleiben – die werden erst in Phase 05 mit der Scoring-Engine verbunden. **Aber:** alle 53 `key`, `nameDe`, `suit`, `baseStrength` und `ruleTextDe` muessen korrekt aus der offiziellen deutschen Anleitung uebernommen werden.

### Suits

Die 5 Suits sind:
- `BEAST` (Bestien)
- `LEADER` (Heerfuehrer)
- `LAND` (Land)
- `WIZARD` (Magier)
- `WEAPON` (Waffen)
- `WILD` (Joker/Wildcards) – fuer Doppelgaenger, Spiegelung, Gestaltenwandler

Plus die zwei thematischen Suits, die als `LAND`-Sub-Kategorie oder eigenstaendig modelliert sein koennen (siehe `05_kartendaten_grundspiel.md` im Specs-Ordner fuer Details). Im Zweifelsfall einen `Suit`-Enum mit allen 7 Werten machen.

### Domain-Modell

```kotlin
// domain/model/CardDefinition.kt
data class CardDefinition(
    val key: String,
    val nameDe: String,
    val suit: Suit,
    val baseStrength: Int,
    val ruleTextDe: String,
    val isJoker: Boolean,
    val jokerType: JokerType?,        // null bei Nicht-Jokern
    val bonuses: List<Bonus>,         // in dieser Phase leer
    val penalties: List<Penalty>,     // in dieser Phase leer
    val specialKey: String?           // in dieser Phase null
)

enum class Suit { BEAST, LEADER, LAND, WIZARD, WEAPON, WILD, FLOOD, FLAME }  // ggf. anpassen

enum class JokerType { DOPPELGANGER, MIRAGE, SHAPESHIFTER, BOOK_OF_CHANGES }
```

Die `Bonus`-/`Penalty`-Klassen werden in Phase 05 ausgefuehrt. Hier reichen leere Listen.

### CardLookup

```kotlin
// data/cards/CardLookup.kt
class CardLookup(private val context: Context) {
    private val cards: List<CardDefinition> by lazy { loadFromAssets() }

    fun getAll(): List<CardDefinition> = cards
    fun getByKey(key: String): CardDefinition? = cards.find { it.key == key }
    fun search(query: String): List<CardDefinition> {
        if (query.isBlank()) return cards
        val q = query.trim().lowercase()
        return cards.filter { it.nameDe.lowercase().contains(q) }
    }
    fun filterBySuits(suits: Set<Suit>): List<CardDefinition> {
        if (suits.isEmpty()) return cards
        return cards.filter { it.suit in suits }
    }

    private fun loadFromAssets(): List<CardDefinition> {
        val json = context.assets.open("cards/base_game.json").bufferedReader().use { it.readText() }
        return Json.decodeFromString<CardDataFile>(json).cards.map { it.toDomain() }
    }
}

@Serializable
private data class CardDataFile(val version: Int, val cards: List<CardDto>)

@Serializable
private data class CardDto(
    val key: String,
    val nameDe: String,
    val suit: String,
    val baseStrength: Int,
    val ruleTextDe: String,
    val bonuses: List<JsonElement> = emptyList(),
    val penalties: List<JsonElement> = emptyList(),
    val isJoker: Boolean = false,
    val specialKey: String? = null
) {
    fun toDomain(): CardDefinition = ...
}
```

`CardLookup` wird in `AppContainer` als lazy property registriert.

### CardPicker (wiederverwendbare Komponente)

```kotlin
// ui/components/CardPicker.kt
@Composable
fun CardPicker(
    allCards: List<CardDefinition>,
    excludedKeys: Set<String> = emptySet(),    // z.B. schon gewaehlte Karten ausblenden – im MVP nicht zwingend
    onCardChosen: (CardDefinition) -> Unit,
    onDismiss: () -> Unit,
    showClearButton: Boolean = false,
    onClear: (() -> Unit)? = null
) {
    var query by remember { mutableStateOf("") }
    var selectedSuits by remember { mutableStateOf(emptySet<Suit>()) }

    val filtered = remember(query, selectedSuits, allCards) {
        allCards
            .filter { it.key !in excludedKeys }
            .filter { selectedSuits.isEmpty() || it.suit in selectedSuits }
            .filter { query.isBlank() || it.nameDe.lowercase().contains(query.trim().lowercase()) }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(16.dp)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text(stringResource(R.string.card_picker_search)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            SuitFilterChips(
                selected = selectedSuits,
                onToggle = { suit ->
                    selectedSuits = if (suit in selectedSuits) selectedSuits - suit else selectedSuits + suit
                }
            )
            Spacer(Modifier.height(8.dp))
            if (showClearButton && onClear != null) {
                TextButton(onClick = onClear) {
                    Text(stringResource(R.string.sandbox_remove_card))
                }
                Spacer(Modifier.height(8.dp))
            }
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp)
            ) {
                items(filtered, key = { it.key }) { card ->
                    CardPickerItem(card = card, onClick = { onCardChosen(card) })
                }
            }
        }
    }
}
```

### Sandbox-Refactor

In `SandboxViewModel`:
- `DummyCard` raus → `CardDefinition` rein
- `CardSlot.Filled(val card: CardDefinition)` statt `Filled(val card: DummyCard)`

In `SandboxScreen`:
- `DummyCardPicker` raus → `CardPicker` rein
- `CardLookup` ueber den `AppContainer` zur Verfuegung stellen
- `viewModel(factory = SandboxViewModelFactory(cardLookup = container.cardLookup))` als Factory

`SandboxViewModel.kt` bekommt `CardLookup` injiziert:

```kotlin
class SandboxViewModel(
    private val cardLookup: CardLookup
) : ViewModel() {
    val allCards: List<CardDefinition> = cardLookup.getAll()
    ...
}
```

### Strings (Ergaenzungen)

```xml
<string name="card_picker_search">Karte suchen</string>
<string name="card_picker_title">Karte wählen</string>
<string name="suit_beast">Bestien</string>
<string name="suit_leader">Heerführer</string>
<string name="suit_land">Land</string>
<string name="suit_wizard">Magier</string>
<string name="suit_weapon">Waffen</string>
<string name="suit_wild">Joker</string>
```

---

## Akzeptanzkriterien

- [ ] `assets/cards/base_game.json` enthaelt **alle 53 Karten** mit korrektem `key`, `nameDe`, `suit`, `baseStrength`, `ruleTextDe`
- [ ] `bonuses`, `penalties`, `specialKey` koennen leer/null sein – aber alle Karten sind strukturell vorhanden
- [ ] `CardLookup` laedt das JSON erfolgreich, liefert `getAll().size == 53`
- [ ] CardPicker oeffnet sich beim Tap auf leeren Slot
- [ ] Such-Feld filtert in Echtzeit
- [ ] Suit-Filter-Chips funktionieren (Mehrfach-Auswahl moeglich)
- [ ] Tap auf eine Karte fuellt den Slot
- [ ] Tap auf belegten Slot: CardPicker mit "Entfernen"-Option
- [ ] Score bleibt "0 Punkte"
- [ ] `DummyCard.kt` und `DummyCardPicker.kt` sind geloescht
- [ ] Alle Texte aus `strings.xml`
- [ ] Build erfolgreich

---

## Hinweise

- **Kartennamen-Quelle:** unbedingt aus der offiziellen Strohmann-Anleitung uebernehmen, nicht aus dem Gedaechtnis. Bei Unsicherheit: Liste an User ausgeben und nachfragen.
- **`suit` als String im JSON, als Enum in Domain:** Mapping in `CardDto.toDomain()`
- **`CardPicker` ist explizit als wiederverwendbare Komponente in `ui/components/`** angesiedelt – wird in Phase 08 (PlayerHandEntry) nochmal gebraucht
