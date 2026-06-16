# Phase 26 – Kamera-Scan + Tesseract OCR

> **Umsetzungsplan:** `C:\Users\basti\.claude\plans\make-a-plan-write-humble-balloon.md`
> (lokale Umsetzung auf Branch `v1.3.0`).

## Anweisung an Claude Code

Lies diese Datei vollstaendig. Lies anschliessend `00-vision.md`. Setze danach diese Phase vollstaendig um.

Voraussetzung: Die manuelle Karten-Erfassung läuft (KartenPick-Flow aus 25.5) und die Mittelfeld-Erfassung existiert.

> **Diese Phase ist Post-Release-Arbeit ("Phase 2" laut `00-vision.md`).** Tesseract bringt ~20 MB
> Trainingsdaten und eine Kamera-Permission ins F-Droid-Paket. Reihenfolge im Repo: 26 (Kamera),
> 28 (P2P), 30 (Erweiterung); die Lücken 27/29 sind absichtlich für spontane Zwischen-Phasen frei.

---

## Stand der Codebasis (Juni 2026 – bei Erstellung dieser Revision)

Diese Phase wurde mehrfach überarbeitet. Aktueller, gültiger Stand des Erfassungs-Flows:

- Die Runden-Erfassung läuft über **`ui/game/RoundCaptureScreen.kt`** + **`RoundCaptureViewModel`**
  mit Inhalt aus **`ui/handentry/PlayerHandCaptureContent.kt`**. Spieler werden über ein
  **`PlayerDropdown`** in der TopAppBar durchgewechselt (`vm::switchToPlayer`).
- `PlayerHandCaptureContent` ist **ein Konstrukt in zwei Stages** (`enum CaptureStage`):
  - **`CardPick`** — überlappender, antippbarer `OverlappingHandStack` + eingebetteter
    `CardPickerContent` (füllt den nächsten leeren Slot). Bewusste Überlappung spart Platz neben
    dem Picker, macht aber **die vollen Kartennamen schwer lesbar**.
  - **`PlayerStage`** — die Hand **flach und nicht überlappend** (`HandSlotsRow`, Tap korrigiert eine
    Karte über den Vollbild-`CardPicker`) + **`JokerSection`** (Joker-/Totenbeschwörer-Auflösung,
    Optimizer) + „fertig erfasst"-Submit.
- Das **Mittelfeld ist kein eigener Screen, sondern ein Pseudo-Spieler** in derselben
  Capture-Rotation (10 bzw. 12 Karten), aktiviert über `SettingsRepository.discardCaptureEnabled`,
  vor dem Reveal verpflichtend. (Memory: *Mittelfeld as a pseudo-player*.)
  **Heutiger Sonderfall:** Das Mittelfeld bleibt in `CardPick` und submittet dort
  (`if (state.isDiscard)` in `PlayerHandCaptureContent`) — es bekommt **keine** PlayerStage. Genau
  das wird in dieser Phase geändert (siehe „Mittelfeld bekommt eine zweite Stage").
- **`CardLookup`** hat die Signatur `CardLookup(context)` und liefert die Karten über `getAll()` /
  `getByKey(key)` / `search(query)` / `filterBySuits(suits)`. Karten aus `assets/cards/base_game.json`.
- **`CardDefinition`** trägt `key, nameDe, suit, baseStrength, ruleTextDe, isJoker, jokerType,
  nameEn?, ruleTextEn?`. Es gibt also bereits **englische Namen** (`nameEn`, Phase 19) – relevant für
  das OCR-Matching, falls Karten in englischer Edition vorliegen.
- Kartenzahl: **53** im Grundspiel, **100** wenn die Erweiterung (Phase 30) aktiv ist. Nirgends
  „53+47" hartkodieren – immer gegen `cardLookup.getAll()` matchen.
- Paketname: `de.morzo.realmscore`.

---

## Kontext (kurz)

Eine ganze Hand wird durch **ein Foto** erfasst. Die App erkennt automatisch alle Karten auf dem Foto
(alle 7 Handkarten auf einmal; beim Mittelfeld entsprechend mehr) und übernimmt für **jede erkannte
Karte direkt die wahrscheinlichste Übereinstimmung** in die Hand.

**Kein Schwellenwert, keine Rückfrage, keine „Top-3 / unsicher"-UI.** Der Scanner trifft immer eine
Entscheidung (Best Match). Die **Korrektur falsch erkannter Karten passiert vollständig in Stage 2**
(`PlayerStage`), wo die Hand flach und lesbar liegt und jede Karte per Tap korrigiert werden kann.

Der Kamera-Scan ist eine **optionale Alternative zum manuellen KartenPick (Stage 1)** und wird über
eine Einstellung aktiviert. Ist die Einstellung aus, läuft alles wie heute (manuelles KartenPick).
Die manuelle Eingabe bleibt **immer** als Fallback verfügbar (Korrektur in Stage 2 nutzt sie ohnehin).

Tesseract OCR liest den Kartennamen-Text am unteren Rand jeder Karte. F-Droid-konform (kein ML Kit).

Gilt für Spieler-Hände **und** für das Mittelfeld (Pseudo-Spieler in derselben Rotation).

---

## Scope

### Drin
- `CameraScanScreen` mit Live-Preview und Capture-Button
- Multi-Karten-Erkennung via CameraX + Tesseract + Rechteck-Detektion
- **Best-Match-Übernahme pro erkannter Karte** (immer die wahrscheinlichste Karte, ohne Schwelle)
- Neue Boolean-Einstellung **„Kamera-Scan statt manuellem KartenPick"** in `SettingsRepository`
- Wenn aktiv: Der KartenPick-Schritt (Stage 1) wird durch den Kamera-Scan ersetzt; nach dem Scan
  füllt das ViewModel die Slots und springt direkt in **Stage 2** zur Korrektur
- **Mittelfeld bekommt eine zweite Stage** (flache, lesbare `HandSlotsRow` zur Bestätigung/Korrektur,
  **ohne** `JokerSection`), damit auch dort die vollen Kartennamen geprüft werden können
- Gilt für Spieler-Hände **und** den Mittelfeld-Pseudo-Spieler

### Explizit NICHT drin
- Kein ML Kit (nicht F-Droid-konform)
- Keine Video-basierte Echtzeit-Erkennung (nur Foto)
- **Kein Confidence-Schwellenwert, keine „Ambiguous/Top-3"-Auswahl, kein Bestätigungs-Sheet** —
  immer Best Match, Korrektur ausschliesslich in Stage 2
- **Keine Joker-Auflösung im Mittelfeld** (in dessen Stage 2 wird `JokerSection` nie gerendert)

---

## Tech-Stack

```kotlin
// libs.versions.toml - NEUE Dependencies (Versionen beim Umsetzen auf aktuelle Stände prüfen):
[versions]
camerax = "1.3.+"
tesseract4android = "4.7.+"

[libraries]
camerax-camera2 = { group = "androidx.camera", name = "camera-camera2", version.ref = "camerax" }
camerax-lifecycle = { group = "androidx.camera", name = "camera-lifecycle", version.ref = "camerax" }
camerax-view = { group = "androidx.camera", name = "camera-view", version.ref = "camerax" }
tesseract = { group = "cz.adaptech.tesseract4android", name = "tesseract4android", version.ref = "tesseract4android" }
```

`app/src/main/assets/tessdata/deu.traineddata` muss vorhanden sein.
Download: https://github.com/tesseract-ocr/tessdata/blob/main/deu.traineddata
(Falls englische Karten-Edition unterstützt werden soll, zusätzlich `eng.traineddata`.)

> **F-Droid-Check nach dem Hinzufügen** (muss leer bleiben), via PowerShell-Tool:
> `./gradlew.bat :app:dependencies | Select-String -Pattern "(gms|firebase|mlkit|google-services)"`

---

## Pipeline: Foto → Karten

```
Foto aufnehmen (CameraX)
       │
       ▼
EXIF-Rotation korrigieren
       │
       ▼
Karten-Rechtecke finden (mit Android Bitmap-Operationen, ohne OpenCV-Dependency)
│   - Konturdetektion auf Graustufen-Bitmap
│   - Rechtecke mit Seitenverhältnis ~0.65 (Standardkarten) isolieren
│   - Hand: max 7 Rechtecke; Mittelfeld: Limit hochsetzen (10/12)
       │
       ▼
Pro Karten-Rechteck: Pre-Processing
│   - Crop auf unteren 25% (Namens-Bereich)
│   - Grayscale + Kontrast erhöhen + Binarisierung
       │
       ▼
Tesseract OCR (PSM_SINGLE_LINE, deu)
│   - UTF-8-Text des Kartennamens
       │
       ▼
Fuzzy-Matching gegen cardLookup.getAll() (53 bzw. 100 Karten)
│   - Jaro-Winkler oder Levenshtein-Distanz
│   - gegen nameDe UND nameEn matchen (Phase 19 liefert beide)
│   - IMMER der beste Treffer (Top-1), kein Schwellenwert
       │
       ▼
Liste der erkannten CardDefinition → direkt in die Hand-Slots, weiter in Stage 2
```

---

## Architektur

```kotlin
// data/ocr/CardRecognizer.kt
class CardRecognizer(
    private val tessBaseAPI: TessBaseAPI,
    private val cardLookup: CardLookup
) {
    /** Erkennt alle Karten auf dem Foto und liefert pro Rechteck den besten Treffer. */
    suspend fun recognizeMultiple(bitmap: Bitmap, maxCards: Int): List<CardDefinition>
    /** Bester Treffer für einen einzelnen Karten-Ausschnitt. */
    suspend fun recognizeSingle(cardBitmap: Bitmap): CardDefinition?
}

// data/ocr/TesseractManager.kt - Singleton, initialisiert beim App-Start
class TesseractManager(private val context: Context) {
    private var api: TessBaseAPI? = null

    suspend fun getOrInit(): TessBaseAPI {
        if (api != null) return api!!
        return withContext(Dispatchers.IO) {
            copyTessDataIfNeeded()   // kopiert nach context.filesDir/tessdata/
            TessBaseAPI().apply {
                init(context.filesDir.absolutePath, "deu")
                pageSegMode = TessBaseAPI.PageSegMode.PSM_SINGLE_LINE
            }.also { api = it }
        }
    }
}
```

> Den `TesseractManager` als lazy Property in `di/AppContainer.kt` halten (gleiche Konvention wie
> `cardLookup`, `database`, `deviceUuidProvider`). Init beim App-Start im Hintergrund anstoßen.

Anmerkung: Da es keinen Schwellenwert und keine „Ambiguous"-Behandlung mehr gibt, entfällt die
frühere sealed class `SingleCardResult` (Confident/Ambiguous/NotRecognized) und das `CardWithScore`.
Liefert die OCR für ein Rechteck gar keinen plausiblen Treffer, bleibt der Slot leer und wird in
Stage 2 manuell gefüllt — kein Sonderfall-UI nötig.

---

## CameraScanScreen

```kotlin
@Composable
fun CameraScanScreen(
    scanTarget: ScanTarget,     // PLAYER_HAND(profileId) oder DISCARD_PILE (Mittelfeld-Pseudo-Spieler)
    roundId: String,
    onScanComplete: (List<CardDefinition>) -> Unit,   // → vm.setCardsFromScan(...) → Stage 2
    onBack: () -> Unit
) {
    val vm: CameraScanViewModel = viewModel(...)
    val state by vm.uiState.collectAsState()

    // Permission-Check (CAMERA). accompanist-permissions oder ActivityResult-API.
    // Bei Ablehnung: Hinweis + Rückkehr zur manuellen Eingabe (onBack).

    Scaffold(...) {
        Box(Modifier.fillMaxSize()) {
            AndroidView(factory = { ctx ->
                PreviewView(ctx).apply { vm.bindCamera(this, lifecycleOwner) }
            })
            CardGridOverlay()                       // Rahmen-Hilfslinien
            Box(Modifier.align(Alignment.BottomCenter).padding(32.dp)) {
                FloatingActionButton(onClick = { vm.captureAndRecognize() }) {
                    Icon(Icons.Default.CameraAlt, null)
                }
            }
            FlashToggle(state.flashMode, onToggle = vm::toggleFlash)
        }
        if (state.isRecognizing) { /* Ladeindikator */ }
    }

    // Sobald die Erkennung fertig ist: direkt zurück mit den erkannten Karten (kein Bestätigungs-Sheet).
    LaunchedEffect(state.recognizedCards) {
        state.recognizedCards?.let { onScanComplete(it) }
    }
}
```

**Kein `RecognitionResultSheet`.** Der frühere Bottom-Sheet-Bestätigungsschritt (grüne Haken,
Radio-Buttons, „NotRecognized"-Button) entfällt. Das Ergebnis des Scans geht unmittelbar in die Hand;
die Bestätigung ist die Stage-2-Ansicht.

---

## Integration in den bestehenden Erfassungs-Flow

Die Erfassung läuft über **`RoundCaptureScreen`** mit den zwei Stages aus 25.5. Der Scan **ersetzt
Stage 1 (KartenPick)**, wenn die Einstellung aktiv ist — er ist kein paralleler Extra-Pfad.

```kotlin
// In PlayerHandCaptureContent: ist cameraScanEnabled aktiv und die Hand noch leer/unvollständig,
// wird statt des eingebetteten CardPicks der CameraScanScreen geöffnet (bzw. ein "Karten scannen"-
// Einstieg gezeigt). Nach dem Scan:
vm.setCardsFromScan(scannedCards)   // füllt die Slots des aktuellen (Pseudo-)Spielers
// → LaunchedEffect/Stage-Logik schaltet auf CaptureStage.PlayerStage (Korrektur + ggf. Joker)
```

- Für eine **Spieler-Hand:** `scanTarget = PLAYER_HAND(profileId)` des im `PlayerDropdown` gewählten
  Spielers. Nach dem Scan: Stage 2 mit `HandSlotsRow` (Korrektur) + `JokerSection`.
- Für das **Mittelfeld:** derselbe Einstieg, wenn der Mittelfeld-Pseudo-Spieler aktiv ist
  (`discardCaptureEnabled`). `scanTarget = DISCARD_PILE`, Rechteck-Limit hochsetzen (10/12). Nach dem
  Scan: Stage 2 mit `HandSlotsRow` (Korrektur) **ohne** `JokerSection`.

**Die manuelle Eingabe bleibt jederzeit verfügbar** — bei aus­geschalteter Einstellung läuft Stage 1
wie heute, und die Korrektur in Stage 2 nutzt ohnehin den manuellen `CardPicker`.

---

## Mittelfeld bekommt eine zweite Stage (Flow-Umbau)

Damit auch das Mittelfeld die Karten lesbar bestätigen kann (überlappte Karten in Stage 1 sind
schwer zu prüfen — besonders bei fehleranfälligem Scan), wird der heutige Sonderfall entfernt:

- **Heute:** `PlayerHandCaptureContent` hält `isDiscard` in `CaptureStage.CardPick` und zeigt den
  Submit-Button dort; das Mittelfeld bekommt nie eine PlayerStage.
- **Neu:** Auch der Mittelfeld-Pseudo-Spieler durchläuft **beide Stages**. Stage 2 zeigt die flache,
  nicht überlappende Hand (Tap → Korrektur via Vollbild-`CardPicker`) und den Submit.
  **`JokerSection` wird im Mittelfeld nie gerendert** (irrelevant — das Mittelfeld hat keine Joker
  aufzulösen).
- **Auto-Advance wie bei der Spieler-Hand (kein „weiter"-Button):** Das Mittelfeld hat eine fest
  bekannte Soll-Kartenzahl — **10 bei zwei Spielern, 12 ab drei Spielern** (`requiredCountFor(DISCARD_ID)`
  → `DISCARD_SLOTS_TWO_PLAYERS` / `DISCARD_SLOTS_MULTI_PLAYER` in `RoundCaptureViewModel`). Dieser Wert
  liegt bereits in `state.requiredSlotCount` und wird im `PlayerDropdown` als `(x/10)` bzw. `(x/12)`
  angezeigt. Die Stage-Umschaltung läuft daher über **dieselbe** Bedingung wie bei der 7-Karten-Hand
  (`cardsCount >= requiredSlotCount` schaltet automatisch auf `PlayerStage`). Den heutigen
  `if (state.isDiscard)`-Sonderfall, der das Mittelfeld in `CardPick` festhält, entfernen.

> **Gilt unabhängig vom Kamera-Scan (Antwort des Users):** Das Mittelfeld erhält die zweite Stage
> **immer**, auch bei manuellem KartenPick — die Lesbarkeit ist in beiden Fällen der Gewinn.

> **Layout (Antwort des Users):** Die 10/12 Mittelfeld-Karten in Stage 2 werden **in mehreren Zeilen
> (umbrechend) flach dargestellt**. Da im Mittelfeld keine `JokerSection` gerendert wird, ist der Platz
> dafür vorhanden. `HandSlotsRow` (heute auf 7 Slots in einer Zeile ausgelegt) entsprechend um einen
> mehrzeiligen/umbrechenden Modus erweitern (z. B. `FlowRow`), ohne die 7-Karten-Spieler-Hand zu ändern.

Betroffen: `ui/handentry/PlayerHandCaptureContent.kt` (Stage-Logik, `isDiscard`-Sonderfall,
`JokerSection`-Gating), ggf. `ui/sandbox/components/HandSlotsRow.kt`.

---

## Settings-Erweiterung (neu)

`SettingsRepository` bekommt **ein Boolean-Flag** für den Kamera-Scan – analog zu den bestehenden
Flags (`discardCaptureEnabled`, `pickerSearchEnabled`). **Kein Float-Schwellenwert mehr.**

```kotlin
val cameraScanEnabled: Flow<Boolean>            // Default false
suspend fun setCameraScanEnabled(value: Boolean)
```

In `SettingsRepositoryImpl` (DataStore) + Settings-UI ergänzen (Toggle „Karten per Kamera scannen").

> Der früher hier vorgesehene `scanConfidenceThreshold: Flow<Float>` entfällt ersatzlos.

---

## Akzeptanzkriterien

- [ ] Einstellung „Karten per Kamera scannen" vorhanden, persistiert (DataStore), Default aus
- [ ] Bei aktiver Einstellung ersetzt der Kamera-Scan Stage 1 (KartenPick); bei inaktiver Einstellung
      läuft Stage 1 unverändert manuell
- [ ] `CameraScanScreen` aus dem Erfassungs-Flow erreichbar; Camera-Permission wird sauber abgefragt,
      bei Ablehnung Rückfall auf manuelle Eingabe
- [ ] Foto wird aufgenommen, Karten-Rechtecke werden extrahiert (Hand max 7, Mittelfeld 10/12)
- [ ] Tesseract liest Kartennamen, Fuzzy-Match gegen `cardLookup.getAll()` (nameDe + nameEn)
- [ ] **Pro erkannter Karte immer der beste Treffer** — kein Schwellenwert, keine Rückfrage,
      kein Bestätigungs-Sheet
- [ ] Nach dem Scan werden die Slots des aktuellen (Pseudo-)Spielers gefüllt und es geht **direkt in
      Stage 2** (`HandSlotsRow`) zur Korrektur
- [ ] In Stage 2 lässt sich jede falsch erkannte Karte per Tap über den Vollbild-`CardPicker` korrigieren
- [ ] **Mittelfeld durchläuft jetzt ebenfalls beide Stages**; seine Stage 2 zeigt die flache Hand
      + Submit, **ohne** `JokerSection`
- [ ] Mittelfeld erhält die zweite Stage **immer** (auch bei manuellem KartenPick)
- [ ] Mittelfeld-Stage 2 schaltet **automatisch** um, sobald `cardsCount >= requiredSlotCount`
      (10 bzw. 12 je nach Spielerzahl) — **kein manueller „weiter"-Button**, `isDiscard`-Sonderfall entfernt
- [ ] Die 10/12 Mittelfeld-Karten werden in Stage 2 **mehrzeilig/umbrechend** flach dargestellt;
      die 7-Karten-Spieler-Hand bleibt einzeilig
- [ ] Spieler-Hand-Stage 2 zeigt weiterhin die `JokerSection` (Joker/Totenbeschwörer + Optimizer)
- [ ] Manuelle Eingabe bleibt immer als Fallback verfügbar (kein erzwungener Scan)
- [ ] F-Droid-Check: keine Google-Libs dazugekommen
- [ ] `tessdata/deu.traineddata` ist im Assets-Ordner
- [ ] Initialisierung von Tesseract im Hintergrund beim App-Start (über `AppContainer`)

---

## Hinweise

- **Trainingsdaten:** `deu.traineddata` (~20 MB) wird in `app/src/main/assets/tessdata/` abgelegt und beim ersten Start nach `context.filesDir/tessdata/` kopiert
- **Karten-Rechteck-Detektion:** Keine OpenCV-Dependency nötig. Android `Bitmap` + manuelle Kantenerkennung reicht für Standard-Spielkarten. (RenderScript ist deprecated – falls Bildverarbeitung beschleunigt werden muss, eigene Vektor-/Kernel-Operationen statt RenderScript.)
- **Beleuchtung:** Hinweis-Text einblenden wenn Helligkeit der Live-Preview zu niedrig
- **Schräge Karten:** Perspective-Transformation kann Erkennungsqualität verbessern, erhöht aber die Komplexität – MVP ohne diese Optimierung
- **Englische Edition:** Falls relevant, `eng.traineddata` mitliefern und Tesseract mit `"deu+eng"` initialisieren; das Matching gegen `nameEn` ist bereits vorbereitet
- **Fehl-Erkennungen sind eingeplant:** Da immer der Best Match übernommen wird, ist die Stage-2-Korrektur der bewusste Mechanismus, um daneben­liegende Treffer schnell zu fixen — nicht ein Schwellenwert
