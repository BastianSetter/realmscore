# Handoff — Phase 26: Camera Card Scan (+ fdroid/play OCR flavors)

**Branch:** `v1.3.0` (not pushed) · **Date:** 2026-06-16 (26.1 work: 2026-06-17) · **Spec:** `specs/26-Kamera-Tesseract.md`
**Plan:** `C:\Users\basti\.claude\plans\make-a-plan-write-humble-balloon.md`

Three commits on top of `a791847`:
- `03ab0b9` — Phase 26 camera scan + flavors (38 files, incl. ~38 MB tessdata).
- `5a47a68` — Tesseract banner-detection improvements.
- *(latest)* — Phase 26.1: Tesseract reworked for the **single upright card** case + a much‑expanded
  debug screen (per‑step stages, per‑row profile plot, four live tuning sliders). See "Phase 26.1" below.

---

## What this adds

An **optional camera scan** that replaces the manual stage‑1 card pick. Toggle it in
**Settings → "Karten per Kamera scannen"** (default off). With it on, opening an empty hand (or the
Mittelfeld) shows the camera *inside* the capture scaffold — the player dropdown stays usable, so you
choose which hand / the Mittelfeld to scan. One photo fills the hand with best‑match cards; you then
correct any mistakes in the readable **stage 2** (flat `HandSlotsRow`). No threshold, no pop‑ups.

Supporting changes:
- **Mittelfeld now runs both stages too** (flat layout, **no** joker section) — `PlayerHandCaptureContent`.
- **Card uniqueness within a round** is enforced on the scan path (cards already in another entry are
  skipped, duplicates within a scan dropped) — never auto‑substituted.
- **"Skipped" hint** (snackbar) when a scan drops an already‑used card, so the empty slot isn't a mystery.
- **Pre‑reveal duplicate safety net**: a warning banner + blocked reveal if any card sits in two entries.
- **Debug‑only Scanner‑Test** (Settings → Debug, debug builds only) to inspect the pipeline.

---

## Phase 26.1 — Tesseract single‑card rework

**Why:** Tesseract has no neural text detector, so it can't follow the multi‑card / tilt / overlap
layout ML Kit handles. The realistic Tesseract target is therefore **one upright card filling the
frame** (portrait). All 26.1 work narrows the fdroid path to that case and makes every step inspectable.

**Scanner (`fdroid/.../TesseractCardScanner.kt`):** `analyze()` always yields **at most one card**
(ignores `maxCards`). Pipeline: `rotate` → `RedBannerDetector.detectBest` (single best red banner by a
width+upper‑position score) → `cropRect` → `ScanImageOps.tightenToTitleText` → `binarizeWhite` →
Tesseract → matcher. If no red banner stands out it falls back to the top/bottom name band.

**The tightening journey (the crux — read this before touching it):** finding the banner is easy;
cropping vertically to *just the title line* is the hard part. Test card was the real "Kaiserin"
(Anführer): its title banner **is red**, but is a decorative pennant with downward tails, warm
illustration below the title, and a white card border above it. Four iterations:
1. `tightenToRedBand` (topmost run of *red* rows) — kept the ribbon tails + warm artwork → band far
   too tall (430→330px) → title tiny after upscale → OCR empty. **Removed.**
2. `tightenToTitleText` v1 (topmost run of *white* rows) — latched onto the **white card border / pale
   background above** the banner. **Superseded.**
3. v2 (white **and** red per row) — collapsed to a 9px sliver (the per‑row "both" test rarely held for
   enough contiguous rows). **Superseded.**
4. **v3 (current, works):** a top‑down state machine that **decouples** the cues (user's design) —
   **banner start** = first row with ≥`titleRowRedFraction` red (skips the white border above) →
   **text top** = first row after that with ≥`titleRowWhiteFraction` white → **text bottom** = where
   that white run ends → crop + symmetric padding (`titlePadFraction`). On Kaiserin this gives a clean
   ~73px title band and Tesseract reads "Kaiserin". `analyzeTitleRows` is the shared core; the debug
   **Zeilen‑Profil** plot draws the per‑row red/white fractions against the two gates and the detected
   edges, so tuning is visual.

   > If a title is missed, Stage 4 shows "⚠ unverändert" — check the profile plot and adjust the
   > **Rot‑Gate** (banner start) or **Weiß‑Gate** (text edges).

**Tuned defaults (production *and* slider start values):** Rot‑Gate `0.50`, Weiß‑Gate `0.20`,
Weiß‑Helligkeit `0.35`, Rand um Titel `0.60`. `binarizeWhite` and the white‑row count share a
`brightLevel()` helper (98th‑percentile min‑channel), so **Weiß‑Helligkeit** tunes detection *and*
binarization together.

**Debug screen (`ui/scan/ScanDebug*`):** defaults to the single‑card case (the 1/7/12 chips are gone).
For the one card it shows **every intermediate image as its own stage**: `Rot‑Maske` (mask + candidate
count) → `Banner‑Auswahl` (boxes: chosen green / rejected yellow) → `Crop (Banner‑Blob)` →
`Zeilen‑Profil (Rot/Weiß)` (the per‑row plot) → `Titelzeile (Weißtext)` → `Binarisiert → Tesseract`,
then the `Ergebnis` (OCR + candidates). Four live sliders (Rot‑Gate, Weiß‑Gate, Weiß‑Helligkeit, Rand
um Titel) re‑run on change. Wired generically: `ScanReport.stages: List<ScanStage>` (default empty,
filled only by `recognizeDetailed` via a threaded trace list; the production `recognizeMultiple` path
passes `null` → zero debug‑bitmap overhead). `RedBannerDetector.detectBestTraced()` shares its core
(`analyzeRed`) with `detectBest`, so production detection output is unchanged.

---

## Architecture (the important part)

OCR is split into two **product flavors** behind one interface. ~99% of the code is shared; only the
OCR engine differs.

```
data/ocr/  (main, shared)
  CardScanner            ← interface: recognizeMultiple / recognizeDetailed / warmUp
  ScanModels.kt          ← CardScanner, ScanRegion, ScanReport, ScanResult, distinctBestCards()
  CardNameMatcher        ← fuzzy match OCR text → card (full-ratio, nameDe + nameEn)
  StringDistance         ← Levenshtein + normalize (letters only, umlaut folding)
  ScanImageOps           ← rotate/crop/tighten/binarize/Otsu, isSaturatedRed, redFraction
  RedBannerDetector      ← finds red title-banner blobs (Tesseract path)
  CardRectangleDetector  ← generic card-rectangle fallback
  ConnectedComponents    ← shared flood-fill

src/fdroid/data/ocr/     (Tesseract — FOSS, what F-Droid ships)
  TesseractCardScanner   ← red-banner detect → tighten → white-binarize → Tesseract → match
  TesseractManager       ← single TessBaseAPI, deu+eng, serialized
  ScannerFactory         ← create() → TesseractCardScanner
  + assets/tessdata/{deu,eng}.traineddata (~38 MB)

src/play/data/ocr/       (ML Kit — better engine, for GitHub/Play)
  MlKitCardScanner       ← ML Kit reads ALL text → keep only lines on a red background → match
  ScannerFactory         ← create() → MlKitCardScanner
```

`AppContainer.cardScanner` calls `ScannerFactory.create(...)` — the active flavor's implementation is
the one compiled in. UI (`CameraScanScreen`, `ScanDebugScreen`, `RoundCaptureScreen`) only ever sees
the `CardScanner` interface.

**Why two strategies:** ML Kit has a neural text detector, so it reads the whole (tilted/overlapping)
photo and we filter lines to those on a **red banner** (titles are white‑on‑red; rule text is
dark‑on‑white → excluded — this is what stopped "Regensturm" matching off a bonus line). Tesseract has
no detector, so it must find the red banners itself (blob detection), crop, and binarize white text per
crop.

---

## Build / run

- Flavored Gradle tasks (there is **no plain `debug` variant**):
  - `./gradlew.bat :app:compileFdroidDebugKotlin` / `:app:assembleFdroidDebug`
  - `./gradlew.bat :app:compilePlayDebugKotlin` / `:app:assemblePlayDebug`
  - `assembleDebug` still works as an aggregate (builds **both**).
- Android Studio: **View → Tool Windows → Build Variants** → pick `fdroidDebug` or `playDebug`.
- **F‑Droid check** must target the fdroid config (the play config legitimately has ML Kit):
  `./gradlew.bat :app:dependencies --configuration fdroidDebugRuntimeClasspath | Select-String "(gms|firebase|mlkit|google-services)"` → must be empty (verified ✅).

---

## Current state

- **play (ML Kit):** works well — **7/7** on the test hand photo after tuning. This is the recommended
  engine for reliability.
- **fdroid (Tesseract):** reworked for the single upright card (Phase 26.1, above). Banner detection,
  crop, the **v3 state‑machine title tightening**, binarization and matching work end‑to‑end on the
  "Kaiserin" test card (reads correctly). Still needs a **wider card set** (other suits / banner
  shapes) to confirm the tuned gate defaults. Fundamentally limited to the one‑card layout — no
  tilt/overlap like ML Kit.
- Both flavors compile and assemble; F‑Droid check clean.

### Tuning knobs (all in `data/ocr/`)
- ML Kit titles missed / junk: `MlKitCardScanner.RED_FRACTION` (lower) / `BANNER_FLOOR` (raise).
- Tesseract banner *missed entirely*: `RedBannerDetector.MIN_AREA_FRACTION` / `MIN_ASPECT`;
  `ScanImageOps.isSaturatedRed` thresholds.
- Tesseract title *cropped wrong* (too tall / wrong band): all four are **live sliders** in the debug
  screen — `titleRowRedFraction` (Rot‑Gate = banner start), `titleRowWhiteFraction` (Weiß‑Gate = text
  edges), `whiteTextBrightFraction` (Weiß‑Helligkeit = white cut, shared with binarization), and
  `titlePadFraction` (Rand um Titel = symmetric padding above+below). The debug **Zeilen‑Profil** stage
  plots the per‑row red/white fractions vs. these gates so you can see where they cut.
- Match acceptance floor: `MIN_MATCH_SCORE` in `ScanModels.kt` (shared).

---

## How to test (device — yours)

1. Settings → enable "Karten per Kamera scannen". Pick the `playDebug` variant.
2. **Scanner‑Test** (Settings → Debug): pick a gallery photo of a hand → check **Modus** is
   "Banner‑Text (ML Kit)" with one region per title, correct top matches.
3. Capture flow: New game → start round → an empty hand auto‑opens the camera; shoot the hand; verify
   it lands in stage 2 with the right cards; tap a wrong one to correct.
4. **Uniqueness:** scan the Mittelfeld, then scan a player whose photo includes one of those cards →
   expect the "… bereits vergeben – übersprungen" snackbar + an empty slot (no duplicate).
5. Repeat step 2 in the **`fdroidDebug`** variant to evaluate the Tesseract engine.

---

## Open items / decisions

- [ ] **Validate Phase 26.1 on more cards** (different suits / banner shapes) and adjust the tuned gate
      defaults if needed — the debug `Zeilen‑Profil` plot + four sliders are the tool for this.
- [ ] **Validate the fdroid (Tesseract) engine on device** broadly and tune if needed.
- [ ] **Distribution decision.** F‑Droid ships the `fdroid` flavor. The `play` (ML Kit) flavor is for
      Play Store (one‑time ~$25, *not* €29/mo) **or** a free GitHub release. ML Kit does **not** require
      the Play Store.
- [ ] **F‑Droid + Tesseract caveat:** `Tesseract4Android` is **JitPack‑only**
      (`com.github.adaptech-cz.Tesseract4Android:tesseract4android:4.8.0`; 4.7.0/4.9.0 fail to build on
      JitPack). F‑Droid builds from source and generally won't pull JitPack binaries, so the official
      F‑Droid build will likely need it vendored / built from source. Local Gradle build is fine.
- [ ] **Push `v1.3.0` / open PR** when ready (nothing pushed yet).
- [ ] Optional polish: visible re‑scan button in stage 2; low‑light detection in the camera overlay.

## Known limitations
- Tesseract can't match ML Kit on tilted/overlapping cards (no neural detector).
- Camera needs a real device — the emulator's synthetic camera can't validate OCR.
- The 7‑card hand photo must show each card's red title banner (top of card) for banner mode.
