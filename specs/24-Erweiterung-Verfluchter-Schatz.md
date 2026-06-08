# Phase 24 – Erweiterung "Der verfluchte Schatz" (+47 Karten)

## Anweisung an Claude Code

Lies diese Datei vollstaendig. Lies anschliessend `00-vision.md`. Setze danach diese Phase vollstaendig um.

Voraussetzung: Phase 23 abgeschlossen.

---

## Kontext (kurz)

Die Erweiterung "Der verfluchte Schatz" fügt 47 neue Karten zum Grundspiel hinzu. Sie ist über einen Settings-Toggle aktivierbar. Wenn aktiv, erscheinen die Erweiterungs-Karten im CardPicker eingebettet in die bestehende Logik – erkennbar an einem kleinen farbigen Badge oben rechts auf der Karte.

Der User stellt die Kartendaten beim Umsetzen dieser Phase zur Verfügung.

---

## Scope

### Drin
- `assets/cards/expansion_cursed_hoard.json` mit allen 47 Karten
- Settings-Toggle "Erweiterung aktiviert" (Default: aus)
- Wenn aktiv: Erweiterungs-Karten im CardPicker eingemischt
- Badge auf Erweiterungs-Karten: kleiner farbiger Streifen oben rechts ("DE" oder eigene Farbe)
- Scoring-Engine unterstützt Erweiterungs-Karten (neue Bonus-/Penalty-Typen falls nötig)

### Explizit NICHT drin
- Keine separaten Statistiken für Erweiterungs-Karten (integriert in bestehende)
- Kein separater "nur Erweiterung"-Modus

---

## Was am Ende funktionieren muss

1. Settings → Toggle "Erweiterung 'Der verfluchte Schatz'" (Default: aus)
2. Wenn ein, CardPicker zeigt 53 + 47 = 100 Karten
3. Erweiterungs-Karten haben einen kleinen badge-artigen Streifen oben rechts
4. Scoring funktioniert korrekt mit gemischten Hands (Grundspiel + Erweiterung)
5. Wenn deaktiviert: CardPicker zeigt wieder nur 53 Karten

---

## Karten-JSON

`assets/cards/expansion_cursed_hoard.json`:
```json
{
  "version": 1,
  "expansion": "cursed_hoard",
  "cards": [
    {
      "key": "expansion_...",
      "nameDe": "...",
      "suit": "...",
      "baseStrength": ...,
      "ruleTextDe": "...",
      "bonuses": [...],
      "penalties": [...],
      "isJoker": false,
      "specialKey": null
    }
  ]
}
```

**Key-Präfix:** Alle Erweiterungs-Karten beginnen mit `expansion_` damit es keine Kollision mit Grundspiel-Karten gibt.

---

## CardLookup-Erweiterung

```kotlin
class CardLookup(private val context: Context, private val settings: SettingsRepository) {
    private val baseCards: List<CardDefinition> by lazy { loadFromAssets("cards/base_game.json") }
    private val expansionCards: List<CardDefinition> by lazy { loadFromAssets("cards/expansion_cursed_hoard.json") }

    fun getAll(): List<CardDefinition> {
        // Synchron: prüft ob Erweiterung aktiv
        // Besser: als Flow oder mit Parameter
        return if (expansionEnabled) baseCards + expansionCards else baseCards
    }

    val allCards: Flow<List<CardDefinition>> = settings.expansionEnabled.map { enabled ->
        if (enabled) baseCards + expansionCards else baseCards
    }
}
```

**Erweiterung in SettingsRepository:**
```kotlin
val expansionEnabled: Flow<Boolean>
suspend fun setExpansionEnabled(enabled: Boolean)
```

---

## CardPickerItem: Expansion-Badge

```kotlin
@Composable
fun CardPickerItem(card: CardDefinition, onClick: () -> Unit) {
    val isExpansion = card.key.startsWith("expansion_")

    Box {
        Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
            // bestehender Inhalt: Name, Suit, Basisstärke
        }
        if (isExpansion) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .width(12.dp)
                    .fillMaxHeight()
                    .background(
                        color = MaterialTheme.colorScheme.tertiary,
                        shape = RoundedCornerShape(topEnd = 8.dp, bottomEnd = 0.dp)
                    )
            )
        }
    }
}
```

Alternativ als diagonaler Streifen oben rechts – visuell prägnanter:
```kotlin
// Canvas-basierter Dreieck-Badge oben rechts auf der Karte
drawPath(
    path = Path().apply {
        moveTo(size.width - 32f, 0f)
        lineTo(size.width, 0f)
        lineTo(size.width, 32f)
        close()
    },
    color = expansionColor
)
```

---

## Scoring-Engine

Die Scoring-Engine muss die Erweiterungs-Karten transparent unterstützen. Falls die Erweiterung neue Bonus-/Penalty-Typen einführt:
- Neue `sealed class Bonus`-/`Penalty`-Subtypen
- Neue Handler in `SpecialEffectRegistry` falls nötig

Der User stellt die genauen Regel-Texte bei der Umsetzung zur Verfügung – Claude Code implementiert die entsprechende Logik basierend auf dem JSON.

---

## Akzeptanzkriterien

- [ ] `assets/cards/expansion_cursed_hoard.json` enthält alle 47 Karten
- [ ] Settings-Toggle "Erweiterung aktiviert" vorhanden (Default: aus)
- [ ] Wenn aktiviert: CardPicker zeigt 100 Karten
- [ ] Wenn deaktiviert: CardPicker zeigt 53 Karten
- [ ] Erweiterungs-Karten haben das Badge sichtbar
- [ ] Scoring funktioniert korrekt mit gemischten Händen
- [ ] Neue Bonus-/Penalty-Typen der Erweiterung sind implementiert
- [ ] Bestehende Unit-Tests laufen weiterhin grün
- [ ] Neue Unit-Tests für Erweiterungs-Karten mit bekannten Regelszenarien

---

## Strings (Ergänzungen)

```xml
<string name="settings_expansion_title">Erweiterung</string>
<string name="settings_expansion_cursed_hoard">Der verfluchte Schatz aktivieren</string>
<string name="settings_expansion_cursed_hoard_desc">Fügt 47 zusätzliche Karten zum Kartenpool hinzu</string>
<string name="card_badge_expansion">Erweiterung</string>
```

---

## Hinweis für die Umsetzung

Der User stellt die Kartendaten beim Start dieser Phase zur Verfügung. Claude Code soll:
1. Die übergebenen Karten-Daten in das JSON-Format einpflegen
2. Unbekannte Regel-Typen identifizieren und beim User nachfragen
3. Erst nach Klärung aller Regeln die Engine erweitern
4. Eine bekannte Referenz-Hand mit Erweiterungs-Karten als Unit-Test anlegen
