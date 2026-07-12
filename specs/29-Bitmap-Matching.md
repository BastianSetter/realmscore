# Phase 29 – Bitmap-Matching (Template-Vergleich statt OCR, experimentell)

## Anweisung an Claude Code

Lies diese Datei vollständig. Lies anschließend `specs/26-Kamera-Tesseract.md` (Kontext des
bestehenden Scan-Flows). Setze danach diese Phase vollständig um.

Voraussetzung: Der Kamera-Scan aus Phase 26 (Fächer-Layout, `RedBannerDetector`, Scanner-Test-Screen)
läuft.

---

## Kontext

Die OCR-Erkennung (Tesseract im `fdroid`-Flavor, ML Kit im `play`-Flavor) ist trotz Tuning
fehleranfällig. Da das Kartenset geschlossen ist und die roten Titel-Banner ein festes Layout haben
(fester Font, feste Ribbon-Grafik, gleiche Zahl-Bubble links), soll die OCR durch direktes
**Template-Matching auf Bitmap-Ebene** ersetzt werden.

Kern der Idee: Aus jedem Banner wird über eine **deterministische Anker-Konstruktion** immer exakt
derselbe Ausschnitt („Standardbox") gewonnen — Rotation aus der Banner-Oberkante, x-Anker aus dem
goldenen Ring der Zahl-Bubble, Skala aus der Bannerhöhe. Weil Referenz-Templates und Kamera-Crops
denselben Rahmen durchlaufen, genügt beim Vergleich normalisierte Kreuzkorrelation (NCC) mit einem
Mini-Suchfenster — kein Gleitfenster, keine Feature-Extraktion.

**Der neue Pfad ist zunächst experimentell:** eine neue Einstellung (Default aus) schaltet ihn ein;
die OCR-Pipeline bleibt unverändert der Default. Die Banner-Blob-Erkennung (`RedBannerDetector`)
teilen sich beide Pfade — sie funktioniert gut und wird nicht angefasst.

---

## Stand der Codebasis (verifiziert, Juli 2026)

- **Geteilte Erkennung** in `main`: `data/ocr/RedBannerDetector.kt` (achsenparallele `Rect`s der
  Banner-Blobs, inkl. `detectFannedTraced` mit Maske+Overlay für den Debug-Screen),
  `data/ocr/ScanImageOps.kt` (Bildoperationen, `isSaturatedRed`, + **Debug-Tuning-Knobs als `var`**),
  `data/ocr/ScanModels.kt` (`CardScanner`-Interface, `ScanRegion`/`ScanStage`/`ScanReport`,
  `CandidateScore`, `distinctBestCards`), `data/ocr/CardNameMatcher.kt`, `data/ocr/StringDistance.kt`.
- **Flavor-Seam:** `data/ocr/ScannerFactory.kt` existiert je Flavor (`fdroid` → `TesseractCardScanner`,
  `play` → `MlKitCardScanner`). `AppContainer.cardScanner` (lazy) ruft die Factory des aktiven Flavors.
- **Debug-Infrastruktur:** `ui/scan/ScanDebugScreen.kt` + `ScanDebugViewModel.kt` („Scanner-Test",
  erreichbar aus den Einstellungen, nur `BuildConfig.DEBUG`). Foto aus Galerie → `recognizeDetailed`
  → rendert `ScanStage`-Zwischenbilder und pro `ScanRegion` Crop, OCR-Text, Confidence und Top-3
  `CandidateScore`s. Tuning-Slider schreiben live in die `ScanImageOps`-Knobs, `reanalyze()` läuft
  auf demselben Bild neu.
- **Settings-Muster:** `SettingsRepository`/`SettingsRepositoryImpl` (DataStore, Boolean-Flows wie
  `cameraScanEnabled`), Toggle-UI in `ui/tabs/settings/SettingsScreen.kt` (`ToggleRow` +
  `stringResource`), Strings in `values/strings.xml` (DE) und `values-en/strings.xml`.
- **⚠ Es gibt KEINE Referenz-Kartenbilder im Projekt.** `assets/cards/` enthält nur JSON-Definitionen
  (`base_game.json`, `base_game_en.json`); der Repo-Ordner `cards/` nur XLSX/PDF. Die Templates
  müssen in dieser Phase erst erzeugt werden — siehe „Template-Erzeugung".
- Karten werden über `CardDefinition.key` identifiziert; Kartenzahl nie hartkodieren
  (Erweiterung Phase 30 → 100 Karten).

---

## Scope

### Drin
- `TemplateCardScanner` in `main` (flavor-unabhängig — nützt fdroid UND play)
- Reines Kotlin (kein OpenCV, keine neue Dependency — konsistent zu Phase 26)
- Deterministische Banner-Normalisierung (Algorithmus unten) + NCC-Matching
- Neue Boolean-Einstellung **„Bitmap-Erkennung (experimentell)"**, Default aus, nur sichtbar/wirksam
  wenn `cameraScanEnabled` an ist
- Umschaltung an einer Stelle: delegierender Scanner in `AppContainer`
- Volle Integration in den Scanner-Test-Screen inkl. **A/B-Umschalter OCR ↔ Bitmap** auf demselben Foto
- **Template-Export im Scanner-Test-Screen** zur erstmaligen Erzeugung der Vorlagen aus echten Fotos

### Explizit NICHT drin
- Kein Entfernen/Umbau der OCR-Pipeline oder des `RedBannerDetector`
- Kein ML-Modell, kein Training
- Kein Confidence-UI im Produktivpfad (Best-Match-Prinzip aus Phase 26 gilt weiter; Korrektur in Stage 2)
- Keine Erkennung stark verdeckter Banner
- Kein per-Region-Fallback Bitmap→OCR (als TODO markieren, siehe Hinweise)

---

## Normalisierungs-Algorithmus: Blob-Rect → Standardbox

Alle Schwellen/Faktoren als benannte, tunebare Konstanten anlegen (Muster: `ScanImageOps`-Knobs).
Pixel-Klassifikatoren: `isSaturatedRed` (vorhanden) und neu `isGoldenYellow` für den Ring der
Zahl-Bubble (Hue-basiert, analog aufgebaut; am Debug-Screen gegen echte Fotos justieren).

```
Schritt 0 — Crop vergrößern
  Blob-Rect des RedBannerDetector großzügiger ausschneiden: links um
  BUBBLE_PAD ≈ 1.3 × Blob-Höhe (die gold/farbige Bubble ist nicht rot und
  liegt daher teils außerhalb des Blobs), oben/unten/rechts je ~15 % Rand.
  Alles auf Bildgrenzen geklemmt. → „Roh-Crop“

Schritt 1 — Oberkante über zwei Probelinien
  Zwei vertikale Probelinien bei x = PROBE_X1 ≈ 45 % und PROBE_X2 ≈ 80 %
  der Roh-Crop-Breite (rechts der Bubble, links vom Ribbon-Ende). Pro Linie
  von oben nach unten das Rot/Weiß-Verhältnis verfolgen: Der erste Umschlag
  weiß→rot (erste stabile Rot-Sequenz von ≥ EDGE_MIN_RUN ≈ 3 px, gegen
  Rauschen/Kompressionsartefakte) markiert den Auftreffpunkt auf die
  Banner-Oberkante. Das ist die rote Border ÜBER der Schrift — per
  Konstruktion textfrei, daher ein sauberer Messpunkt.
  → zwei Punkte (x1,y1), (x2,y2)

Schritt 2 — Rotation
  Winkel = atan2(y2 − y1, x2 − x1). Bei |Winkel| > ROTATE_EPS ≈ 0.5° den
  Roh-Crop um sein Zentrum zurückdrehen (Bitmap + Matrix wie
  ScanImageOps.rotate, Kappung ±25°), danach die Oberkante einmal neu
  messen (eine Probelinie genügt jetzt). → Oberkante liegt horizontal.

Schritt 3 — Anker an der Zahl-Bubble
  Von der Oberkante DESCEND ≈ 0.35 × (grobe Blob-Höhe) nach unten → voll im
  Roten. Von dort nach LINKS laufen, bis isGoldenYellow anschlägt (stabiler
  Lauf ≥ EDGE_MIN_RUN): rechter Rand des goldenen Rings = bubbleRight.
  Von bubbleRight um SAFE_GAP ≈ 0.25 × Blob-Höhe nach rechts → Spalte x_a,
  die sicher schriftfrei zwischen Ring und erstem Buchstaben liegt.

Schritt 4 — Bannerhöhe an der schriftfreien Spalte
  In Spalte x_a vertikal scannen: zusammenhängender Rot-Lauf →
  bannerTop / bannerBottom → Bannerhöhe h. Da die Spalte schriftfrei ist,
  wird der Lauf nicht durch weiße Buchstaben unterbrochen.

Schritt 5 — Standardbox & Skalierung
  Alles relativ zu (bubbleRight, bannerTop, h) — die physischen Proportionen
  sind auf allen Karten identisch, also definieren drei Faktoren die Box:
    left   = bubbleRight − K_LEFT   × h     (Bubble inkl. Zahl liegt drin)
    top    = bannerTop   − K_TOP    × h
    width  =               K_WIDTH  × h
    height =               K_HEIGHT × h
  K_* einmalig aus einem Referenzfoto ableiten und als Konstanten festlegen.
  Box ausschneiden, auf kanonische Größe TEMPLATE_W × TEMPLATE_H
  (Startwert 320 × 64) skalieren, Graustufen (Luminanz als FloatArray).
  KEINE Binarisierung — NCC ist gegen lineare Helligkeits-/Kontrast-
  unterschiede invariant; genau das ersetzt die fehleranfälligen
  OCR-Schwellenwerte.
```

**Fehlerbehandlung:** Schlägt ein Schritt fehl (kein weiß→rot-Umschlag auf einer Probelinie, kein
Gold-Ring gefunden, kein Rot-Lauf in x_a), gilt die Region als nicht normalisierbar → kein Match,
Slot bleibt leer (Stage-2-Korrektur), und im Debug-Report eine ⚠-Stage mit dem gescheiterten Schritt.

---

## Matching

```
TemplateMatcher (pur Kotlin, arbeitet auf FloatArray — unit-testbar ohne Android)
  - Standardbox gegen jedes Template: NCC mit Mini-Suchfenster ±2 px in x/y
    (Restfehler der Anker-Konstruktion), Score = max. NCC.
  - Deterministischer Rahmen ⇒ kein Gleitfenster nötig; 53 Vergleiche à
    320×64 px inkl. ±2-px-Fenster liegen bei wenigen Millisekunden pro
    Banner. Dauer im Debug-Report ausgeben.
  - Ergebnis: List<CandidateScore> absteigend (bestehende Datenklasse!)
```

`ScanRegion` wie gehabt füllen (`candidates` = Top-3; `ocrText` leer; `confidence` =
`(topScore*100).toInt()`) → `distinctBestCards` übernimmt Duplikat-/Exclude-Logik unverändert.
Sanity-Floor analog `MIN_MATCH_SCORE`: `MIN_TEMPLATE_SCORE = 0.5f` (nur Müll-Abweisung, kein
Ambiguity-Gate — Startwert, per Debug-Screen tunen).

Kein Banner im Foto → leeres Ergebnis (Slots bleiben leer, Stage 2 füllt manuell). Der
OCR-Namensband-Fallback der Tesseract-Pipeline wird im Template-Pfad **nicht** nachgebaut.

---

## Architektur

```kotlin
// data/ocr/BannerNormalizer.kt  (main)
// Implementiert Schritt 0–5. Android-Bitmap rein, GrayImage (Standardbox) raus.
// Liefert zusätzlich ein Trace-Objekt für den Debug-Screen: Probelinien-Punkte,
// Winkel, bubbleRight/x_a, bannerTop/Bottom, Zwischenbilder.

// data/ocr/TemplateMatcher.kt  (main, pures Kotlin, keine Android-Typen)
class TemplateMatcher(private val templates: List<BannerTemplate>) {
    fun scoredCandidates(box: GrayImage): List<Pair<String /*cardKey*/, Float>>
}
/** Graubild als FloatArray + Breite/Höhe — hält den Matcher Android-frei und unit-testbar. */
data class GrayImage(val pixels: FloatArray, val width: Int, val height: Int)
data class BannerTemplate(val cardKey: String, val image: GrayImage)

// data/ocr/BannerTemplateStore.kt  (main)
// Lädt Templates: zuerst assets/scan_templates/<cardKey>.png, dann Overrides aus
// context.filesDir/scan_templates/ (dorthin exportiert der Scanner-Test-Screen neue
// Vorlagen — Testen ohne Rebuild). Fehlende Templates sind erlaubt: die Karte ist
// dann schlicht nicht per Bitmap erkennbar.

// data/ocr/TemplateCardScanner.kt  (main) — implementiert CardScanner
// usesFanLayout = true (gleicher Fächer-Workflow wie Tesseract).
// recognizeMultiple/recognizeDetailed analog TesseractCardScanner:
// detectFanned → pro Rect BannerNormalizer → TemplateMatcher;
// recognizeDetailed hängt ScanStages an. warmUp() lädt den TemplateStore.

// di/AppContainer.kt — Umschaltstelle (die EINZIGE):
// cardScanner wird zu einem delegierenden Scanner, der pro Aufruf
// settingsRepository.bitmapMatchingEnabled liest und an TemplateCardScanner
// bzw. den Flavor-Scanner (ScannerFactory.create wie bisher) weiterreicht.
// usesFanLayout beider Pfade ist true → CameraScanScreen bleibt unberührt.
```

Die tuning-relevanten Konstanten (`PROBE_X1/2`, `BUBBLE_PAD`, `SAFE_GAP`, `MIN_TEMPLATE_SCORE`,
Gold-Schwellen) nach dem Muster der bestehenden Knobs als `var` anlegen, damit der
Scanner-Test-Screen sie live ändern kann. Die `K_*`-Boxfaktoren sind dagegen feste Konstanten
(einmal kalibriert; ändern würde alle Templates invalidieren — als Kommentar dazuschreiben).

---

## Template-Erzeugung (Bootstrapping — es gibt noch keine Vorlagen!)

Die Referenz-Banner entstehen aus **echten Fotos über den Scanner-Test-Screen**, mit exakt derselben
Normalisierung wie zur Laufzeit (gleiche Standardbox, gleiche Druck-Textur → maximale Scores):

1. Im Scanner-Test-Screen bekommt jede erkannte Region einen Button **„Als Vorlage speichern"**.
   Gespeichert wird die *Standardbox* (Schritt 5) als PNG nach `filesDir/scan_templates/<cardKey>.png`.
2. Der `cardKey` kommt aus einer Auswahl: Vorbelegung ist der beste OCR-Kandidat der Region
   (der OCR-Pfad läuft im Debug-A/B ohnehin), korrigierbar über den bestehenden Karten-Picker
   oder eine einfache Suchliste (`CardLookup.search`).
3. Ein Abschnitt „Vorlagen: x/53" im Scanner-Test-Screen zeigt den Bestand und listet fehlende
   Karten (gegen `cardLookup.getAll()`), plus „Alle exportieren"-Aktion (Share/SAF als ZIP oder
   Ordner), damit der User die fertigen Templates einmalig nach
   `app/src/main/assets/scan_templates/` ins Repo übernehmen kann.
4. `BannerTemplateStore` bevorzugt `filesDir`-Overrides vor Assets (siehe oben), sodass
   Tuning-Iterationen ohne Rebuild möglich sind.

> Der User fotografiert damit einmal alle 53 Karten in den üblichen Fächer-Reihen und hat nach
> wenigen Durchgängen den vollen Template-Satz. Deutsche Edition zuerst; die englische Edition
> bräuchte einen eigenen Satz (siehe Hinweise).

---

## Settings-Erweiterung

`SettingsRepository` + `SettingsRepositoryImpl` (DataStore), analog `cameraScanEnabled`:

```kotlin
val bitmapMatchingEnabled: Flow<Boolean>            // Default false
suspend fun setBitmapMatchingEnabled(value: Boolean)
```

`SettingsScreen`: `ToggleRow` direkt unter dem Kamera-Scan-Toggle, nur eingeblendet wenn
`state.cameraScanEnabled == true`. Strings (DE + EN, mit `_desc` wie beim Kamera-Scan-Toggle):

- DE: „Bitmap-Erkennung (experimentell)" / „Vergleicht die Kartenbanner direkt mit gespeicherten
  Vorlagen statt Texterkennung. Benötigt einmalig erzeugte Vorlagen."
- EN: „Bitmap matching (experimental)" / „Matches card banners directly against stored templates
  instead of text recognition. Requires templates to be created once."

---

## Debugger-Integration (Scanner-Test-Screen)

1. **A/B-Umschalter:** `FilterChip`-Reihe „OCR | Bitmap" (unabhängig von der Produktiv-Einstellung).
   Wechsel triggert `reanalyze()` auf demselben Foto — direkter Qualitätsvergleich beider Pfade.
   Der `ScanDebugScreen` bekommt dazu beide Scanner (oder den delegierenden mit erzwingbarem Modus)
   statt des einen `scanner`-Parameters.
2. **Stages des Template-Pfads** (pro Region, über die bestehende `ScanStage`-Mechanik):
   - Roh-Crop mit eingezeichneten Probelinien und den zwei Auftreffpunkten (Note: Winkel in Grad)
   - rotierter Crop mit eingezeichnetem Anker (bubbleRight, x_a) und bannerTop/Bottom-Linien
   - Standardbox (das tatsächlich gematchte Graubild), daneben das beste Template
     (Label: Kartenname + Score)
   - ⚠-Notes bei gescheiterten Schritten (kein Umschlag, kein Gold-Ring, kein Rot-Lauf, Template
     fehlt) — analog zu den ⚠-Notes der OCR-Stages
3. **Region-Karten** rendern wie bisher — `candidates: List<CandidateScore>` wird wiederverwendet,
   die Top-3-Anzeige funktioniert ohne UI-Änderung. Zusätzlich in der Report-Kopfzeile:
   Template-Bestand (x/53) und Matching-Dauer gesamt.
4. **Tuning-Slider** für `PROBE_X1/2`, `SAFE_GAP`, Gold-Schwellen und `MIN_TEMPLATE_SCORE` neben den
   bestehenden OCR-Knobs (gleiche Slider-Mechanik, `reanalyze()` bei Änderung).
5. **Template-Erzeugung** wie oben beschrieben (Speichern-Button pro Region, Bestandsanzeige, Export).

---

## Tests

- `TemplateMatcher`-Unit-Tests in `app/src/test/` (pur Kotlin, `GrayImage` aus synthetischen
  FloatArrays — kein Robolectric nötig): Identität scored ~1.0; Verschiebung ±2 px wird gefunden;
  globale Helligkeits-/Kontraständerung ändert den Score nicht (NCC-Invarianz); Zufallsrauschen
  bleibt unter `MIN_TEMPLATE_SCORE`; das korrekte von zwei ähnlichen Templates gewinnt.
- `BannerNormalizer`-Kernlogik testbar machen, indem die Geometrie-Schritte (Probelinien-Umschlag,
  Winkel aus zwei Punkten, Rot-Lauf-Suche) auf Pixel-Arrays statt Bitmaps operieren: synthetisches
  Banner (weiße Fläche, rotes Rechteck mit bekannter Neigung ±10°, goldener Kreis) → Winkel auf
  ±1° genau, bubbleRight/bannerTop/h auf ±2 px genau.
- Kein Instrumented-Test nötig; End-to-End validiert der User über den Scanner-Test-Screen
  (siehe CLAUDE.md: manuelle UI-Tests macht der User im Emulator/am Gerät).

---

## Akzeptanzkriterien

- [ ] Einstellung „Bitmap-Erkennung (experimentell)" vorhanden (DataStore, Default aus,
      DE+EN-Strings), nur sichtbar wenn Kamera-Scan aktiv
- [ ] Einstellung aus → Verhalten identisch zu heute (OCR-Pfad unberührt, keine Regressions)
- [ ] Einstellung an → Scan läuft Ende-zu-Ende über `TemplateCardScanner`; Slots werden gefüllt,
      Stage-2-Korrektur wie gehabt; `excludedKeys`/Duplikate über `distinctBestCards` unverändert
- [ ] `TemplateCardScanner` liegt in `main` und funktioniert in BEIDEN Flavors
      (F-Droid-Check bleibt leer)
- [ ] Normalisierung folgt dem Anker-Algorithmus: Probelinien-Oberkante → Rotation →
      Gold-Ring-Anker → Bannerhöhe an schriftfreier Spalte → Standardbox relativ zu Anker+Höhe
- [ ] Standardbox enthält die Zahl-Bubble; Rotationen bis ±25° werden ausgeglichen
      (Debug-Stage zeigt Probelinien, Punkte und Winkel)
- [ ] Gescheiterte Normalisierung (fehlender Umschlag/Ring/Rot-Lauf) lässt den Slot leer und
      erzeugt eine ⚠-Stage — kein Crash, kein Falsch-Match
- [ ] Scanner-Test: A/B-Chip OCR↔Bitmap re-analysiert dasselbe Foto; Template-Pfad zeigt alle
      Stages + Top-3-Scores + Matching-Dauer
- [ ] Scanner-Test: „Als Vorlage speichern" pro Region (Key-Auswahl mit OCR-Vorbelegung),
      Bestandsanzeige x/53 mit Fehlliste, Export-Aktion; `filesDir`-Templates überstimmen Assets
- [ ] Fehlende Templates crashen nichts — Karten ohne Vorlage sind schlicht nicht matchbar
- [ ] Matching-Dauer < 20 ms pro Banner bei 53 Templates (±2-px-Fenster), gemessen im Debug-Report
- [ ] Unit-Tests für `TemplateMatcher` und `BannerNormalizer`-Geometrie grün;
      `:app:compileFdroidDebugKotlin` und `:app:assembleFdroidDebug` grün

---

## Hinweise

- **Keine neue Dependency:** Alles in purem Kotlin, konsistent zur Phase-26-Entscheidung gegen OpenCV.
- **Probelinien-Positionen:** 45 %/80 % sind Startwerte. Sie müssen rechts der Bubble und links vom
  Ribbon-Schwalbenschwanz liegen — am Debug-Screen gegen echte Fotos (verschiedene Kartennamen-Längen!)
  verifizieren, dafür sind sie Slider.
- **Warum der weiß→rot-Umschlag zuverlässig ist:** Im Fächer zeigt jede Karte ihre weiße Oberkante
  über dem eigenen Banner (Layout-Konvention aus Phase 26.2), und die Ribbon-Border über der Schrift
  ist reines Rot — der erste stabile Umschlag trifft also immer die Oberkante, nie einen Buchstaben.
- **Warum keine Binarisierung:** Die Schwellenwert-Knobs der OCR-Pipeline existieren, weil Tesseract
  Binärbilder braucht. NCC auf Graustufen umgeht genau diese Fehlerquelle — nicht nachbauen.
- **Zahl-Bubble:** steckt bewusst mit in der Standardbox (Unterscheidungssignal). Keine separate
  Ziffern-Auswertung; als TODO vermerken, falls sich Namens-Zwillinge als Problem zeigen.
- **Per-Region-Fallback Bitmap→OCR** (Region ohne Template-Treffer nochmal durch die OCR schicken):
  bewusst nicht im MVP — als TODO markieren, Entscheidung nach den ersten Praxis-Scores.
- **Englische Edition:** bräuchte einen eigenen Template-Satz (`scan_templates_en/` o. ä.). Nicht in
  dieser Phase; der Store sollte den Asset-Pfad aber als eine Konstante führen.
- **Erweiterung (Phase 30):** Templates sind per `cardKey` offen für 100 Karten; Bestandsanzeige
  rechnet gegen `cardLookup.getAll()`, nie gegen eine hartkodierte 53.
- **Speicher:** 53 Graustufen-Templates à 320×64 px ≈ 4 MB als FloatArray zur Laufzeit — unkritisch;
  Assets als PNG wenige hundert KB.
