# Phase 21 – Kamera-Scan + Tesseract OCR

## Anweisung an Claude Code

Lies diese Datei vollstaendig. Lies anschliessend `00-vision.md`. Setze danach diese Phase vollstaendig um.

Voraussetzung: Phase 20 abgeschlossen. Mittelfeld-Scan (manuell) funktioniert.

---

## Kontext (kurz)

Karten werden durch **ein Foto** erfasst. Die App erkennt automatisch alle Karten auf dem Foto (alle 7 Handkarten auf einmal). Bei hoher Confidence werden Karten direkt übernommen, bei Unsicherheit wählt der User manuell. Manuelle Eingabe bleibt immer als Fallback verfügbar.

Tesseract OCR liest den Kartennamen-Text am unteren Rand jeder Karte. F-Droid-konform (kein ML Kit).

Gilt für Spieler-Hände **und** für das Mittelfeld (ersetzt die manuelle Erfassung aus Phase 20).

---

## Scope

### Drin
- `CameraScanScreen` mit Live-Preview und Capture-Button
- Multi-Karten-Erkennung via CameraX + Tesseract + Rechteck-Detektion
- Confidence-basierte automatische Übernahme
- Korrektur-UI bei unklaren/fehlenden Erkennungen
- Integration in `PlayerHandEntryScreen` (Button "Karten scannen")
- Integration in `DiscardEntryScreen` (Button "Mittelfeld scannen")

### Explizit NICHT drin
- Kein ML Kit (nicht F-Droid-konform)
- Keine Video-basierte Echtzeit-Erkennung (nur Foto)

---

## Tech-Stack

```kotlin
// libs.versions.toml - NEUE Dependencies:
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
│   - Max 7 Rechtecke extrahieren
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
Fuzzy-Matching gegen 53+47 Kartennamen
│   - Jaro-Winkler oder Levenshtein-Distanz
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
            copyTessDataIfNeeded()
            TessBaseAPI().apply {
                init(context.filesDir.absolutePath, "deu")
                pageSegMode = TessBaseAPI.PageSegMode.PSM_SINGLE_LINE
            }.also { api = it }
        }
    }
}
```

---

## CameraScanScreen

```kotlin
@Composable
fun CameraScanScreen(
    scanTarget: ScanTarget,     // PLAYER_HAND(profileId) oder DISCARD_PILE
    roundId: String,
    onScanComplete: (List<CardDefinition>) -> Unit,
    onBack: () -> Unit
) {
    val vm: CameraScanViewModel = viewModel(...)
    val state by vm.uiState.collectAsState()

    // Permission-Check
    val cameraPermission = rememberPermissionState(Manifest.permission.CAMERA)
    if (!cameraPermission.status.isGranted) {
        PermissionRequestScreen(
            onRequest = { cameraPermission.launchPermissionRequest() },
            onManualInput = onBack
        )
        return
    }

    Scaffold(...) {
        Box(Modifier.fillMaxSize()) {
            // Live-Vorschau
            AndroidView(factory = { ctx ->
                PreviewView(ctx).apply {
                    vm.bindCamera(this, lifecycleOwner)
                }
            })

            // Overlay: Rahmen für 7 Karten
            CardGridOverlay()

            // Capture-Button unten
            Box(Modifier.align(Alignment.BottomCenter).padding(32.dp)) {
                FloatingActionButton(onClick = { vm.captureAndRecognize() }) {
                    Icon(Icons.Default.CameraAlt, null)
                }
            }

            // Blitz-Toggle oben links
            FlashToggle(state.flashMode, onToggle = vm::toggleFlash)
        }
    }

    // Result-Sheet nach Capture
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

```kotlin
@Composable
fun RecognitionResultSheet(
    results: List<SingleCardResult>,
    onConfirm: (List<CardDefinition>) -> Unit,
    onRescan: () -> Unit
) {
    // Liste der Ergebnisse:
    // Confident: Karte mit grünem Haken, direkt angenommen
    // Ambiguous: Top-3 Kandidaten als Radio-Buttons
    // NotRecognized: CardPicker-Button für manuelle Auswahl

    // "Übernehmen"-Button:
    // - Aktiv wenn alle Slots belegt
    // - Übergibt die finalisierten Karten
}
```

**Confidence-Threshold:** Standard 0.70. Alles über 0.85 → automatisch als "Confident". Zwischen 0.70-0.85 → "Ambiguous". Unter 0.70 → "NotRecognized". Threshold einstellbar in Settings.

---

## Integration in bestehende Screens

### PlayerHandEntryScreen
```kotlin
// Neuer Button in der TopBar oder als Floating Button:
IconButton(onClick = { navController.navigate(cameraScanRoute(roundId, profileId)) }) {
    Icon(Icons.Default.CameraAlt, contentDescription = stringResource(R.string.player_hand_scan))
}
```

Nach erfolgreichem Scan: `vm.setCardsFromScan(scannedCards)` → füllt alle Slots.

### DiscardEntryScreen
```kotlin
// Neben dem "+" Chip:
IconButton(onClick = { navController.navigate(discardCameraScanRoute(roundId)) }) {
    Icon(Icons.Default.CameraAlt, null)
}
```

---

## Akzeptanzkriterien

- [ ] `CameraScanScreen` aus `PlayerHandEntryScreen` erreichbar
- [ ] Camera-Permission wird sauber abgefragt
- [ ] Foto wird aufgenommen, Karten-Rechtecke werden extrahiert (max 7)
- [ ] Tesseract liest Kartennamen, Fuzzy-Match gegen alle Karten
- [ ] Confident-Karten werden grün mit Haken angezeigt
- [ ] Ambiguous: Top-3 zur Auswahl
- [ ] NotRecognized: CardPicker für manuelle Auswahl
- [ ] Alle Karten bestätigt → Übergabe an PlayerHandEntry
- [ ] Scan auch für Mittelfeld nutzbar
- [ ] Confidence-Threshold in Settings einstellbar
- [ ] Manuelle Eingabe bleibt immer als Fallback (kein erzwungener Scan)
- [ ] F-Droid-Check: keine Google-Libs dazugekommen
- [ ] `tessdata/deu.traineddata` ist im Assets-Ordner
- [ ] Initialisierung von Tesseract im Hintergrund beim App-Start

---

## Hinweise

- **Trainingsdaten:** `deu.traineddata` (~20MB) wird in `app/src/main/assets/tessdata/` abgelegt und beim ersten Start in den App-internen Speicher kopiert
- **Karten-Rechteck-Detektion:** Keine OpenCV-Dependency nötig. Android `Bitmap` + manuelle Kantenerkennung (Canny-Algorithmus mit `RenderScript` oder einfaches Schwellenwert-Verfahren) reicht für Standard-Spielkarten
- **Beleuchtung:** Hinweis-Text einblenden wenn Helligkeit der Live-Preview zu niedrig
- **Schräge Karten:** Perspective-Transformation kann Erkennungsqualität verbessern, aber erhöht Komplexität erheblich – MVP ohne diese Optimierung
