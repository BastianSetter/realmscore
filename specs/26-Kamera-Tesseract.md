# Phase 26 – Kamera-Scan + Tesseract OCR

## Anweisung an Claude Code

Lies diese Datei vollstaendig. Lies anschliessend `00-vision.md`. Setze danach diese Phase vollstaendig um.

Voraussetzung: Die manuelle Karten-Erfassung läuft (KartenPick-Flow aus 25.5) und die Mittelfeld-Erfassung existiert.

> **Diese Phase ist Post-Release-Arbeit ("Phase 2" laut `00-vision.md`).** Vor Beginn klären,
> ob sie überhaupt noch gewünscht ist – Tesseract bringt ~20 MB Trainingsdaten und eine Kamera-
> Permission ins F-Droid-Paket. Reihenfolge im Repo: 26 (Kamera), 28 (P2P), 30 (Erweiterung);
> die Lücken 27/29 sind absichtlich für spontane Zwischen-Phasen frei.

---

## Stand der Codebasis (Juni 2026 – bei Erstellung dieser Revision)

Diese Phase wurde ursprünglich gegen einen älteren Erfassungs-Flow geschrieben. Der hat sich
durch **25.5 (Erfassungs-Flow)** und **25.6 (Sandbox-UI)** grundlegend geändert. Aktueller Stand:

- **Es gibt keinen `PlayerHandEntryScreen`/`DiscardEntryScreen`-Paar mehr** als getrennte Screens.
  Die Runden-Erfassung läuft über **`ui/game/RoundCaptureScreen.kt`** + **`RoundCaptureViewModel`**
  mit Inhalt aus **`ui/handentry/PlayerHandCaptureContent.kt`**. Spieler werden über ein
  **`PlayerDropdown`** in der TopAppBar durchgewechselt (`vm::switchToPlayer`).
- Der eigentliche **KartenPick** (Karten auswählen) ist ein eingebetteter, gestapelter Picker;
  der **Vollbild-`CardPicker`** (`ui/components/CardPicker.kt`) bleibt als Korrektur-/Fallback-
  Dialog erhalten (`PickerMode.ContinuousFill` / `SingleEdit`).
- Das **Mittelfeld ist kein eigener Screen, sondern ein Pseudo-Spieler** in derselben
  Capture-Rotation (10 bzw. 12 Karten), aktiviert über `SettingsRepository.discardCaptureEnabled`,
  vor dem Reveal verpflichtend. (Memory: *Mittelfeld as a pseudo-player*.)
- **`CardLookup`** hat die Signatur `CardLookup(context)` (kein Settings-Parameter) und liefert die
  Karten über `getAll()` / `getByKey(key)` / `search(query)` / `filterBySuits(suits)`. Die Liste ist
  beim Laden nach Suit + deutschem Namen sortiert. Karten kommen aus `assets/cards/base_game.json`.
- **`CardDefinition`** trägt `key, nameDe, suit, baseStrength, ruleTextDe, isJoker, jokerType,
  nameEn?, ruleTextEn?`. Es gibt also bereits **englische Namen** (`nameEn`, Phase 19) – relevant für
  das OCR-Matching, falls Karten in englischer Edition vorliegen.
- Kartenzahl: **53** im Grundspiel, **100** wenn die Erweiterung (Phase 30) aktiv ist. Nirgends
  „53+47" hartkodieren – immer gegen `cardLookup.getAll()` matchen.
- Paketname: `de.morzo.realmscore`.

---

## Kontext (kurz)

Karten werden durch **ein Foto** erfasst. Die App erkennt automatisch alle Karten auf dem Foto (alle 7 Handkarten auf einmal). Bei hoher Confidence werden Karten direkt übernommen, bei Unsicherheit wählt der User manuell. Manuelle Eingabe (KartenPick) bleibt immer als Fallback verfügbar.

Tesseract OCR liest den Kartennamen-Text am unteren Rand jeder Karte. F-Droid-konform (kein ML Kit).

Gilt für Spieler-Hände **und** für das Mittelfeld (das ja als Pseudo-Spieler in derselben Rotation erfasst wird).

---

## Scope

### Drin
- `CameraScanScreen` mit Live-Preview und Capture-Button
- Multi-Karten-Erkennung via CameraX + Tesseract + Rechteck-Detektion
- Confidence-basierte automatische Übernahme
- Korrektur-UI bei unklaren/fehlenden Erkennungen
- Einstieg aus dem **KartenPick-Schritt** des `RoundCaptureScreen` (Button „Karten scannen"),
  für Spieler-Hände **und** den Mittelfeld-Pseudo-Spieler
- Neue Einstellung „Confidence-Schwelle" in `SettingsRepository`

### Explizit NICHT drin
- Kein ML Kit (nicht F-Droid-konform)
- Keine Video-basierte Echtzeit-Erkennung (nur Foto)

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
Karten-Rechtecke finden (OpenCV-ähnlich mit Android Bitmap-Operationen)
│   - Konturdetektion auf Graustufen-Bitmap
│   - Rechtecke mit Seitenverhältnis ~0.65 (Standardkarten) isolieren
│   - Max 7 Rechtecke extrahieren (Mittelfeld: mehr – Limit dort hochsetzen)
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
│   - Top-3 Kandidaten pro Karte
       │
       ▼
RecognitionResult: Confident / Ambiguous / NotRecognized
```

---

## Architektur

```kotlin
// data/ocr/CardRecognizer.kt
class CardRecognizer(
    private val tessBaseAPI: TessBaseAPI,
    private val cardLookup: CardLookup
) {
    suspend fun recognizeMultiple(bitmap: Bitmap): List<SingleCardResult>
    suspend fun recognizeSingle(cardBitmap: Bitmap): SingleCardResult
}

sealed class SingleCardResult {
    data class Confident(val card: CardDefinition, val confidence: Float) : SingleCardResult()
    data class Ambiguous(val candidates: List<CardWithScore>) : SingleCardResult()
    object NotRecognized : SingleCardResult()
}

data class CardWithScore(val card: CardDefinition, val score: Float)

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

---

## CameraScanScreen

```kotlin
@Composable
fun CameraScanScreen(
    scanTarget: ScanTarget,     // PLAYER_HAND(profileId) oder DISCARD_PILE (Mittelfeld-Pseudo-Spieler)
    roundId: String,
    onScanComplete: (List<CardDefinition>) -> Unit,
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
    }

    state.recognitionResult?.let { results ->
        RecognitionResultSheet(
            results = results,
            onConfirm = { confirmedCards -> onScanComplete(confirmedCards) },
            onRescan = { vm.clearResult() }
        )
    }
}
```

---

## RecognitionResultSheet

Nach dem Foto erscheint ein BottomSheet mit den erkannten Karten:

- **Confident:** Karte mit grünem Haken, direkt angenommen
- **Ambiguous:** Top-3 Kandidaten als Radio-Buttons
- **NotRecognized:** Button, der den bestehenden Vollbild-`CardPicker` (`ui/components/CardPicker.kt`) für die manuelle Auswahl öffnet
- **„Übernehmen"-Button:** aktiv wenn alle Slots belegt; übergibt die finalisierten Karten

**Confidence-Threshold:** Default 0.70. Über 0.85 → „Confident". 0.70–0.85 → „Ambiguous". Unter 0.70 → „NotRecognized". Schwelle einstellbar (siehe Settings).

---

## Integration in den bestehenden Erfassungs-Flow

Die Erfassung läuft über **`RoundCaptureScreen`** mit dem KartenPick-Schritt aus 25.5. Der Scan ist
eine **Alternative zum manuellen KartenPick**, kein eigener Pfad parallel dazu.

```kotlin
// Im KartenPick-Schritt (PlayerHandCaptureContent / RoundCaptureScreen):
// Ein Button "Karten scannen" neben/über dem eingebetteten Picker.
IconButton(onClick = { navController.navigate(cameraScanRoute(roundId, scanTarget)) }) {
    Icon(Icons.Default.CameraAlt, contentDescription = stringResource(R.string.scan_cards))
}
```

- Für eine **Spieler-Hand:** `scanTarget = PLAYER_HAND(profileId)` des gerade im `PlayerDropdown`
  gewählten Spielers.
- Für das **Mittelfeld:** derselbe Button, wenn der Mittelfeld-Pseudo-Spieler aktiv ist
  (`discardCaptureEnabled`). `scanTarget = DISCARD_PILE`. Rechteck-Limit dort hochsetzen (>7).

Nach erfolgreichem Scan füllt das ViewModel die Slots des aktuellen (Pseudo-)Spielers, genau wie der
manuelle KartenPick es tun würde (`vm.setCardsFromScan(scannedCards)`), und springt in die normale
Spieler-Stage (Joker-Auflösung etc.). **Die manuelle Eingabe bleibt jederzeit verfügbar** – Scan ist
nie erzwungen.

---

## Settings-Erweiterung (neu)

`SettingsRepository` bekommt eine Schwelle für die Auto-Übernahme – analog zu den bestehenden
Flags (`discardCaptureEnabled`, `pickerSearchEnabled`):

```kotlin
val scanConfidenceThreshold: Flow<Float>           // Default 0.70
suspend fun setScanConfidenceThreshold(value: Float)
```

In `SettingsRepositoryImpl` (DataStore) + Settings-UI ergänzen.

---

## Akzeptanzkriterien

- [ ] `CameraScanScreen` aus dem KartenPick-Schritt des `RoundCaptureScreen` erreichbar
- [ ] Camera-Permission wird sauber abgefragt; bei Ablehnung Rückfall auf manuelle Eingabe
- [ ] Foto wird aufgenommen, Karten-Rechtecke werden extrahiert (Hand max 7, Mittelfeld mehr)
- [ ] Tesseract liest Kartennamen, Fuzzy-Match gegen `cardLookup.getAll()` (nameDe + nameEn)
- [ ] Confident-Karten werden grün mit Haken angezeigt
- [ ] Ambiguous: Top-3 zur Auswahl
- [ ] NotRecognized: bestehender Vollbild-`CardPicker` für manuelle Auswahl
- [ ] Alle Karten bestätigt → Slots des aktuellen Spielers werden gefüllt, weiter in die Spieler-Stage
- [ ] Scan auch für den Mittelfeld-Pseudo-Spieler nutzbar
- [ ] Confidence-Schwelle in Settings einstellbar (persistiert)
- [ ] Manuelle Eingabe (KartenPick) bleibt immer als Fallback (kein erzwungener Scan)
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
