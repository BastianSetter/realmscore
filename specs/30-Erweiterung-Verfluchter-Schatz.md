# Phase 30 – Erweiterung "Der verfluchte Schatz" (+47 Karten)

## Anweisung an Claude Code

Lies diese Datei vollstaendig. Lies anschliessend `00-vision.md`. Setze danach diese Phase vollstaendig um.

Voraussetzung: Das Karten-/Scoring-System des Grundspiels ist fertig (CardLookup, CardRuleRegistry,
Joker-Auflösung). Der User stellt die Kartendaten beim Umsetzen dieser Phase zur Verfügung.

> **Post-Release-Arbeit ("Phase 2" laut `00-vision.md`).** Reihenfolge im Repo: 26 (Kamera),
> 28 (P2P), 30 (Erweiterung).

---

## Stand der Codebasis (Juni 2026 – bei Erstellung dieser Revision)

Diese Phase wurde ursprünglich gegen ein vermutetes JSON-Schema mit `bonuses`/`penalties`/`specialKey`
geschrieben. **So funktioniert die Scoring-Engine nicht.** Aktueller Stand:

- **Karten-JSON ist rein deskriptiv.** `assets/cards/base_game.json` enthält pro Karte nur:
  `key, nameDe, suit, baseStrength, ruleTextDe, isJoker, jokerType`. **Keine** `bonuses`/`penalties`/
  `specialKey`-Felder. Das Mapping DTO→Domain ist `CardDto.toDomain()` in
  `data/cards/CardLookup.kt`.
- **Scoring ist code-basiert, nicht datengetrieben.** Jede Karte mit Effekt hat eine
  `CardScoringRule` (`domain/scoring/CardScoringRule.kt`), die in einer
  **`CardRuleRegistry`** (Map `cardKey → CardScoringRule`) registriert ist. Karten ohne Eintrag
  tragen nur ihre `baseStrength`. Bestehende Regel-Bausteine liegen unter
  `domain/scoring/rules/common/` (z. B. `FlatPenaltyIfMissingRule`, `SelfBlankIfRule`, `CompositeRule`)
  und `domain/scoring/rules/specials/`. **Neue Erweiterungs-Effekte werden als neue Rule-Klassen
  implementiert und in der Registry eingetragen** – nicht über JSON-Arrays.
- **`CardDefinition`** trägt zusätzlich `nameEn?` / `ruleTextEn?` (Phase 19). Englische Texte kommen aus
  einer **Override-Datei** `assets/cards/base_game_en.json` (siehe `loadEnOverrides()`), nicht inline.
- **`CardLookup` ist aktuell NICHT settings-abhängig:** Signatur `CardLookup(context)`, eine einzige
  lazy `cards`-Liste aus `base_game.json`, beim Laden sortiert nach `suit.ordinal`, dann `nameDe`
  (lowercase). Für die Erweiterung muss `CardLookup` settings-fähig werden (siehe unten).
- **`SettingsRepository`** kennt bisher u. a. `discardCaptureEnabled` und `pickerSearchEnabled`
  (jeweils `Flow<Boolean>` + Setter). **`expansionEnabled` existiert noch nicht** und muss ergänzt werden.
- **CardPicker-UI:** `ui/components/CardPicker.kt` ist ein Vollbild-`Dialog` mit `SuitColumn` +
  `CardColumn` (kein simples `Card`-Listenelement). Das Badge gehört in das Karten-Zellen-Composable
  von `CardColumn`. Daneben gibt es den eingebetteten KartenPick (25.5). **Beide** Darstellungen müssen
  das Badge zeigen.
- **Necromancer:** `CardLookup.getNecromancerEligibleCards()` filtert nach
  `NECROMANCER_SUITS = {ARMY, WIZARD, LEADER, BEAST}`. Erweiterungs-Karten dieser Suits werden dadurch
  automatisch berücksichtigt – sofern die Erweiterung keine **neuen Suits** einführt (dann
  `Suit`-Enum erweitern und Necromancer-Regel prüfen).
- Paketname: `de.morzo.realmscore`.

---

## Kontext (kurz)

Die Erweiterung "Der verfluchte Schatz" fügt 47 neue Karten zum Grundspiel hinzu. Sie ist über einen Settings-Toggle aktivierbar. Wenn aktiv, erscheinen die Erweiterungs-Karten im CardPicker eingebettet in die bestehende Logik – erkennbar an einem kleinen farbigen Badge oben rechts auf der Karte.

Der User stellt die Kartendaten beim Umsetzen dieser Phase zur Verfügung.

---

## Scope

### Drin
- `assets/cards/expansion_cursed_hoard.json` mit allen 47 Karten (gleiches Schema wie `base_game.json`)
- optional `assets/cards/expansion_cursed_hoard_en.json` für englische Namen/Regeltexte
- Settings-Toggle „Erweiterung aktiviert" (Default: aus)
- Wenn aktiv: Erweiterungs-Karten im CardPicker eingemischt (korrekt mitsortiert)
- Badge auf Erweiterungs-Karten (kleiner farbiger Streifen oben rechts)
- Scoring-Engine unterstützt Erweiterungs-Karten über neue `CardScoringRule`-Klassen + Registry-Einträge

### Explizit NICHT drin
- Keine separaten Statistiken für Erweiterungs-Karten (integriert in bestehende)
- Kein separater „nur Erweiterung"-Modus

---

## Was am Ende funktionieren muss

1. Settings → Toggle „Erweiterung 'Der verfluchte Schatz'" (Default: aus)
2. Wenn ein, CardPicker zeigt 53 + 47 = 100 Karten (korrekt nach Suit/Name sortiert)
3. Erweiterungs-Karten haben einen kleinen badge-artigen Streifen oben rechts
4. Scoring funktioniert korrekt mit gemischten Händen (Grundspiel + Erweiterung)
5. Wenn deaktiviert: CardPicker zeigt wieder nur 53 Karten

---

## Karten-JSON

`assets/cards/expansion_cursed_hoard.json` — **gleiches Schema wie `base_game.json`** (siehe `CardDto`):
```json
{
  "version": 1,
  "cards": [
    {
      "key": "expansion_<name>",
      "nameDe": "...",
      "suit": "ARMY",
      "baseStrength": 0,
      "ruleTextDe": "Bonus: ... / Strafe: ...",
      "isJoker": false,
      "jokerType": null
    }
  ]
}
```

- **Kein** `bonuses`/`penalties`/`specialKey` im JSON – die Effekt-Logik steckt im Code (Registry).
- **Key-Präfix:** Alle Erweiterungs-Karten beginnen mit `expansion_`, damit es keine Kollision mit
  Grundspiel-Karten gibt und das Badge sich am Key erkennen lässt.
- **Suit:** muss ein gültiger `Suit`-Enum-Wert sein. Bringt die Erweiterung neue Suits mit → `Suit`-Enum
  erweitern (und alle Stellen prüfen, die über Suits iterieren: Picker-`SuitColumn`, Ring-Diagramm,
  Necromancer-Filter, Farben in `ui/components/SuitVisuals.kt`).
- **Englisch (optional):** `expansion_cursed_hoard_en.json` im Format von `base_game_en.json`
  (`{key, nameEn, ruleTextEn}`).

---

## CardLookup-Erweiterung (settings-fähig machen)

`CardLookup` lädt heute nur `base_game.json`. Es muss um die Erweiterung erweitert und settings-abhängig
gemacht werden. Da heute `getAll()` synchron ist, gibt es zwei sinnvolle Wege – **mit dem User klären**,
welcher zu den Aufrufern passt:

```kotlin
class CardLookup(
    private val context: Context,
    private val settings: SettingsRepository,   // NEU
) {
    private val baseCards by lazy { loadFromAssets(ASSET_PATH, ASSET_PATH_EN) }
    private val expansionCards by lazy { loadFromAssets(EXP_PATH, EXP_PATH_EN) }

    // Reaktiv: bevorzugt, wenn Aufrufer einen Flow konsumieren können.
    val allCards: Flow<List<CardDefinition>> = settings.expansionEnabled.map { enabled ->
        sortCards(if (enabled) baseCards + expansionCards else baseCards)
    }

    // Synchron: nur wenn nötig (z. B. Scoring), Zustand zwischenspeichern.
    fun getAll(): List<CardDefinition> = sortCards(
        if (expansionEnabledCached) baseCards + expansionCards else baseCards
    )
}
```

**Wichtig:** Die heutige Sortierung (`compareBy(suit.ordinal, nameDe.lowercase())`) muss **nach dem
Mergen** angewandt werden, sonst stehen die Erweiterungs-Karten ans Ende sortiert. Die bestehenden
Konsumenten (`getByKey`, `search`, `filterBySuits`, `getNecromancerEligibleCards`) entsprechend auf die
gemergte Liste umstellen.

**Erweiterung in `SettingsRepository`** (analog `discardCaptureEnabled`):
```kotlin
val expansionEnabled: Flow<Boolean>            // Default false
suspend fun setExpansionEnabled(enabled: Boolean)
```
In `SettingsRepositoryImpl` (DataStore) + Settings-UI ergänzen.

---

## Badge auf Erweiterungs-Karten

Erkennung: `card.key.startsWith("expansion_")`. Das Badge muss in **beiden** Karten-Darstellungen
auftauchen:
1. dem Karten-Zellen-Composable in `CardColumn` (Vollbild-`CardPicker.kt`)
2. der eingebetteten KartenPick-Darstellung (25.5)

Variante A – farbiger Streifen oben rechts:
```kotlin
if (card.key.startsWith("expansion_")) {
    Box(
        modifier = Modifier
            .align(Alignment.TopEnd)
            .width(8.dp).fillMaxHeight()
            .background(MaterialTheme.colorScheme.tertiary)
    )
}
```

Variante B – diagonaler Dreieck-Badge (visuell prägnanter, via `Canvas`/`drawPath` oben rechts).
Variante mit dem User abstimmen, damit sie zum aktuellen Karten-Look passt.

---

## Scoring-Engine

Die Engine ist code-basiert. Für jede Erweiterungs-Karte mit Effekt:
1. Passende vorhandene Rule wiederverwenden, wenn der Effekt einem Grundspiel-Muster entspricht
   (z. B. `FlatPenaltyIfMissingRule`, `SelfBlankIfRule`, `CompositeRule` aus
   `domain/scoring/rules/common/`).
2. Sonst eine neue `CardScoringRule`-Klasse unter `domain/scoring/rules/specials/` anlegen.
3. Die Regel in der **`CardRuleRegistry`**-Map registrieren (`cardKey → rule`).
4. Bei neuen Blanking-/Penalty-Mechaniken die entsprechenden Stellen in
   `domain/scoring/blanking/` bzw. `domain/scoring/penalty/` erweitern.

Der User stellt die genauen Regel-Texte bei der Umsetzung zur Verfügung. Vorgehen:
1. Übergebene Karten-Daten ins JSON einpflegen.
2. Unbekannte/neuartige Regel-Typen identifizieren und beim User nachfragen.
3. Erst nach Klärung aller Regeln die Engine erweitern.
4. Eine bekannte Referenz-Hand mit Erweiterungs-Karten als Unit-Test anlegen.

---

## Akzeptanzkriterien

- [ ] `assets/cards/expansion_cursed_hoard.json` enthält alle 47 Karten im `base_game.json`-Schema
- [ ] (optional) `expansion_cursed_hoard_en.json` vorhanden, englische Texte greifen
- [ ] Settings-Toggle „Erweiterung aktiviert" vorhanden (Default: aus), `expansionEnabled` persistiert
- [ ] Wenn aktiviert: CardPicker zeigt 100 Karten, korrekt nach Suit/Name sortiert
- [ ] Wenn deaktiviert: CardPicker zeigt 53 Karten
- [ ] Erweiterungs-Karten haben das Badge sichtbar – im Vollbild-Picker **und** im eingebetteten KartenPick
- [ ] Scoring funktioniert korrekt mit gemischten Händen (neue Rules in `CardRuleRegistry` registriert)
- [ ] Necromancer/Joker-Auflösung berücksichtigt passende Erweiterungs-Karten
- [ ] Bestehende Unit-Tests laufen weiterhin grün
- [ ] Neue Unit-Tests für Erweiterungs-Karten mit bekannten Regelszenarien
- [ ] F-Droid-Check unverändert sauber

---

## Strings (Ergänzungen)

```xml
<string name="settings_expansion_title">Erweiterung</string>
<string name="settings_expansion_cursed_hoard">Der verfluchte Schatz aktivieren</string>
<string name="settings_expansion_cursed_hoard_desc">Fügt 47 zusätzliche Karten zum Kartenpool hinzu</string>
<string name="card_badge_expansion">Erweiterung</string>
```
