# Phase 19 – Englische Übersetzung

## Anweisung an Claude Code

Lies diese Datei vollstaendig. Lies anschliessend `00-vision.md`. Setze danach diese Phase vollstaendig um.

Voraussetzung: Phase 18 abgeschlossen.

---

## Kontext (kurz)

Die App ist seit Phase 01 i18n-ready (alle Texte in `strings.xml`). In dieser Phase wird die englische Übersetzung tatsächlich befüllt: sowohl UI-Strings als auch die 53+47 Karten-Daten.

Der User übersetzt selbst (nativ). Claude Code bereitet die Struktur vor, befüllt die technischen Strings maschinell und markiert alle inhaltlichen Strings die menschliche Überprüfung brauchen.

---

## Scope

### Drin
- `res/values-en/strings.xml` vollständig anlegen
- Alle technischen/kurzen Strings direkt übersetzen (Labels, Buttons, Status-Meldungen)
- Inhaltliche Strings (Effekt-Beschreibungen, Karten-Regeltexte) mit `TODO(TRANSLATE)` markieren
- `assets/cards/base_game_en.json` Struktur anlegen (53 Karten, `nameEn` + `ruleTextEn`)
- Fallback-Logik: wenn englische Karten-Übersetzung fehlt, wird der deutsche Text angezeigt
- Language-Switcher in Settings (System / Deutsch / English)

### Explizit NICHT drin
- Kein maschinelles Übersetzen von Regeltext (zu fehlerhaft bei Spiel-Fachbegriffen)
- Keine weiteren Sprachen

---

## Was am Ende funktionieren muss

1. Settings → "Sprache" → System / Deutsch / English
2. Bei "English": alle UI-Texte auf Englisch
3. Karten-Namen im CardPicker auf Englisch (wo `nameEn` vorhanden)
4. Fehlende Übersetzungen: Fallback auf Deutsch (kein leerer String)
5. Bei "System": Android-Systemsprache wird verwendet

---

## Datei-Struktur

```
res/
├── values/
│   └── strings.xml          (DE - bestehend)
└── values-en/
    └── strings.xml          (EN - NEU)

assets/cards/
├── base_game.json           (DE - bestehend)
└── base_game_en.json        (EN - NEU, optional override)
```

---

## strings.xml (EN) – Strategie

Claude Code übersetzt alle technischen Strings direkt:

```xml
<!-- res/values-en/strings.xml -->
<resources>
    <string name="app_name">RealmScore</string>
    <string name="onboarding_headline">Welcome!</string>
    <string name="onboarding_body">This app helps you keep score in Fantasy Realms. What\'s your name?</string>
    <string name="onboarding_name_label">Your name</string>
    <string name="onboarding_continue">Continue</string>
    <string name="home_new_game">New Game</string>
    <string name="home_sandbox_title">Sandbox</string>
    <string name="home_sandbox_body">Freely combine cards and explore synergies</string>
    <!-- ... alle weiteren Strings ... -->

    <!-- Effekt-Beschreibungen: TODO-Markierung fuer manuelle Uebersetzung -->
    <!-- TODO(TRANSLATE): effect_king_bonus_per_army -->
    <string name="effect_king_bonus_per_army">+5 per Army (×%1$s) <!-- NEEDS REVIEW --></string>
</resources>
```

**Regel für Claude Code:**
- Alles was ein Label, Button-Text, Statusmeldung oder Navigationselement ist: direkt übersetzen
- Alles was einen Spiel-Regeltext enthält (Effekt-Beschreibungen, Karten-Regeln): übersetzen und mit `<!-- NEEDS REVIEW -->` markieren
- Die vollständige Liste aller `TODO(TRANSLATE)`-Strings am Ende der Datei als Kommentar zusammenfassen

---

## Karten-Daten EN

`assets/cards/base_game_en.json`:
```json
{
  "version": 1,
  "note": "English names for Fantasy Realms (original WizKids edition). Loaded as override on top of base_game.json.",
  "cards": [
    {
      "key": "dragon",
      "nameEn": "Dragon",
      "ruleTextEn": "BLANKS all cards without a Sword or a Magic"
    },
    {
      "key": "king",
      "nameEn": "King",
      "ruleTextEn": "+5 for each Army. +20 if you also have the Queen"
    }
    // ... alle 53 Karten
  ]
}
```

Die englischen Namen entsprechen den offiziellen englischen Kartennamen aus der WizKids-Edition von Fantasy Realms.

---

## CardLookup-Erweiterung

```kotlin
class CardLookup(private val context: Context) {
    // NEU: englische Overrides laden
    private val enOverrides: Map<String, CardEnOverride> by lazy { loadEnOverrides() }

    fun getDisplayName(card: CardDefinition, locale: Locale): String {
        return if (locale.language == "en") {
            enOverrides[card.key]?.nameEn ?: card.nameDe
        } else {
            card.nameDe
        }
    }

    fun getRuleText(card: CardDefinition, locale: Locale): String {
        return if (locale.language == "en") {
            enOverrides[card.key]?.ruleTextEn ?: card.ruleTextDe
        } else {
            card.ruleTextDe
        }
    }
}

data class CardEnOverride(val nameEn: String, val ruleTextEn: String)
```

---

## Language-Switcher

In `SettingsRepository`:
```kotlin
enum class AppLanguage { SYSTEM, GERMAN, ENGLISH }
val appLanguage: Flow<AppLanguage>
suspend fun setAppLanguage(lang: AppLanguage)
```

In `MainActivity`:
```kotlin
val language by settingsRepo.appLanguage.collectAsState(initial = AppLanguage.SYSTEM)

LaunchedEffect(language) {
    val locale = when (language) {
        AppLanguage.GERMAN -> Locale("de")
        AppLanguage.ENGLISH -> Locale("en")
        AppLanguage.SYSTEM -> Resources.getSystem().configuration.locales[0]
    }
    val config = Configuration(resources.configuration)
    config.setLocale(locale)
    resources.updateConfiguration(config, resources.displayMetrics)
}
```

---

## Akzeptanzkriterien

- [ ] `res/values-en/strings.xml` enthält alle Strings aus `res/values/strings.xml`
- [ ] Alle technischen Strings sind sauber übersetzt
- [ ] Regeltext-Strings sind mit `<!-- NEEDS REVIEW -->` markiert
- [ ] Zusammenfassende TODO-Liste am Ende der Datei
- [ ] `assets/cards/base_game_en.json` enthält alle 53 Karten mit `nameEn` und `ruleTextEn`
- [ ] Language-Switcher in Settings funktioniert (System / DE / EN)
- [ ] Beim Wechsel auf EN: UI-Texte und Karten-Namen wechseln sofort
- [ ] Fallback auf DE wenn EN-Übersetzung fehlt (kein leerer String)
- [ ] Kein Absturz bei fehlendem EN-Override

---

## Hinweise

- **Karten-Namen**: die englischen Originalnamen aus der WizKids-Edition verwenden (Dragon, King, Queen, Army, etc.) – nicht neu übersetzen
- **Regeltext**: maschinenübersetzt, wird vom User geprüft. Auf Korrektheit der Spiel-Fachbegriffe achten.
- **`TODO(TRANSLATE)`-Liste**: Claude Code gibt am Ende einen klaren Block mit allen zu überprüfenden Strings aus, damit der User schnell findet was er lesen muss
