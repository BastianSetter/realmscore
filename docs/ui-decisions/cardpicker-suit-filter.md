# CardPicker — Suit-Filter-Verhalten

**Status:** Single-Select ist der Default (Stand 2026-05).
**Ort:** `app/src/main/java/de/basti/fantasyrealms/ui/components/CardPicker.kt`

## Aktuelles Verhalten (Default)

Im linken Spalten-Layout des CardPicker ist immer **genau eine** Suit aktiv:

- `selectedSuit: Suit?` (`null` = "Alle").
- Tap auf eine Suit deselektiert die vorher aktive automatisch.
- "Alle" entfernt den Filter komplett.

Begründung: schneller Filter beim Karteneintrag — beim Erfassen einer Hand will
man meist nur eine bestimmte Suit auf einen Blick durchgehen, ohne erst die
vorherige Auswahl manuell zurücknehmen zu müssen.

## Alternative für die Zukunft: Multi-Select

In früheren Iterationen (vor 2026-05) war der Filter eine Mehrfachauswahl
(`Set<Suit>`), realisiert über `FilterChip`s in einer horizontalen Reihe. Das
Verhalten kann später als optionale Einstellung wieder verfügbar gemacht
werden — z. B. in der Settings-Phase (15-Polish).

### Skizze für eine spätere Settings-Option

```kotlin
enum class SuitFilterMode { SINGLE_SELECT, MULTI_SELECT }

// In DataStore Preferences:
val suitFilterMode: Flow<SuitFilterMode>
```

CardPicker würde dann zwei State-Varianten anbieten:

- `SINGLE_SELECT`: `Suit?` (aktuelles Default-Verhalten).
- `MULTI_SELECT`: `Set<Suit>` (alte Logik mit OR-Verknüpfung über alle aktiven Suits).

Falls beide Modi koexistieren sollen, das Filter-Predikat als
`(CardDefinition) -> Boolean` abstrahieren, damit der Picker selbst nicht
verzweigt.

## Warum keine Settings-Option im MVP

Die Settings-Phase ist auf Phase 15 terminiert (siehe `specs/15-Polish.md`). Bis
dahin würde eine vorgezogene Option nur Komplexität ohne Nutzen bringen — die
Mehrheit der Spieler erfasst Hände linear, und der Single-Select-Default
schneidet sowohl bei "schnell durchsuchen" als auch bei "alles sehen" gut ab.
