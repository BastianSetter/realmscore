# Handoff — Phase 26: Camera Card Scan (+ fdroid/play OCR flavors)

**Branch:** `v1.3.0` (not pushed) · **Date:** 2026-06-16 (26.1: 2026-06-17 · 26.2: 2026-06-17) · **Spec:** `specs/26-Kamera-Tesseract.md`
**Plan:** `C:\Users\basti\.claude\plans\make-a-plan-write-humble-balloon.md`

Commits on top of `a791847`:
- `03ab0b9` — Phase 26 camera scan + flavors (38 files, incl. ~38 MB tessdata).
- `5a47a68` — Tesseract banner-detection improvements.
- `2cdc194` — Phase 26.1: Tesseract reworked for the **single upright card** case + a much‑expanded
  debug screen (per‑step stages, per‑row profile plot, four live tuning sliders). See "Phase 26.1" below.
- *(latest)* — Phase 26.2: Tesseract reworked for a **single-column fan** of up to 7 cards + the
  border‑framing title detection and a left/right side‑cut. See "Phase 26.2" below.

> **Decision (2026-06-17):** pursue **Option B** (lay cards in a fanned stack, one photo, multi‑crop)
> for the Tesseract path. Option A (continuous video sweep, no shutter) is parked but analysed in
> `handoff/phase-26-option-a-video-sweep.md`. Rationale: the fan only steals *vertical* pixels, so each
> title keeps near‑full *horizontal* resolution (the dimension Tesseract needs) — sidestepping the
> low‑resolution problem that rules out a free multi‑card layout.

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

## Phase 26.2 — Tesseract single-column **fan** + border-framing title detection

**Why:** one card per photo is not a viable hand-capture flow, and a *free* multi-card layout defeats
Tesseract (no neural detector, and each title would get ~1/N of both dimensions). A **single-column
fan** (cards overlapped top→bottom in one stack) solves both: every card shows its own white top edge
+ red title ribbon (cards run white→red top to bottom), so each contributes exactly one red banner
blob, and the fan only costs *vertical* pixels — titles keep near-full horizontal resolution. This is
the **7-card hand** version; the two-column (Mittelfeld 10/12) layout + an on-camera dashed guide
overlay are **not built yet** (next).

**Detection (`RedBannerDetector`):** new `detectFanned(bitmap, maxCards)` / `detectFannedTraced(...)`
replace the old unused `detect`. They reuse `analyzeRed`, take the largest `maxCards` qualifying red
blobs and order them **top→bottom** (a pure top-sort, so regions line up with the physical card order).
The traced overlay numbers each chosen banner 1..N (green) over the rejected candidates (yellow).

**Scanner (`fdroid/.../TesseractCardScanner.kt`):** `analyze()` now loops the fanned banners and runs
the proven per-card pipeline on each; stages are prefixed `Karte i/N`. No banner anywhere → the old
whole-image name-band fallback. `recognizeMultiple`/`recognizeDetailed` pass `maxCards` through;
`distinctBestCards` maps the ordered regions to slots (unchanged).

**Title tightening reworked (the crux — `ScanImageOps`):** the v3 white-run heuristic was replaced by
a **two-red-border** rule (user's design) that's much more robust on the fan. Per banner crop, top→bottom
on per-row red/white **fractions**:
1. **Top border** = first row that is *plain red ribbon*: `red > titleBorderRed` AND `white <
   titleBorderWhite`. The card's white top edge above (white, little red) is skipped.
2. **Text row** = first row after that with `white > titleTextWhite` — confirms lettering began (blocks
   a second border row sitting right next to the first).
3. **Bottom border** = the next plain-red row after the text (fallback: end of the text run).
4. Crop `[topBorder..bottomBorder]` ± **independent** top/bottom padding (negative = bite inward).

Then a **side-cut** (`tightenToTitleSides`): per-column red count; from the left the first column with
`red > titleSideRed` and from the right likewise → crop `[left..right]`. Drops the number disc / suit
edge on the left and the ribbon tail on the right, so OCR sees only the red title band. Falls back to
the full band if no solid-red column / a degenerate span.

**Per-card pipeline:** Crop (Banner-Blob) → Zeilen-Profil (Rot/Weiß) → Titelzeile (Weißtext) →
**Seiten beschnitten** → Binarisiert → Tesseract → match.

**Tuned defaults (production *and* slider starts):** `titleBorderRed` 0.70, `titleBorderWhite` 0.08,
`titleTextWhite` 0.20, `whiteTextBrightFraction` 0.65, `titlePadTopFraction` **−0.15**,
`titlePadBottomFraction` 0.20, `titleSideRed` 0.60. (`whiteTextBrightFraction` still feeds both the
white-row detection and `binarizeWhite`.)

**Debug screen:** runs the fan with `FAN_MAX_CARDS = 7` (gallery/camera). Now **seven** live sliders:
Rot-Gate Rand, Weiß-Min Rand, Weiß-Max Text, Weiß-Helligkeit, Rand oben, Rand unten, Seiten-Cut. The
**Zeilen-Profil** plot draws all three vertical gates (red / faint-blue white-min / blue white-max) and
the detected edges (orange = the two borders, green = the confirming text row).

> ⚠️ Still **device-validated on a narrow card set only.** The defaults above were tuned on the fan test
> photos (`AndroidApps/DebugScreenshots/16-18.jpg`); confirm across more suits/banner shapes. Build
> succeeds; only behaviour-on-device remains to verify.

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

- **play (ML Kit):** works well — **7/7** on the test hand photo after tuning. Whole-hand photo, no
  arranging, no cropping. Recommended engine for reliability.
- **fdroid (Tesseract):** reworked for the **single-column fan of up to 7 cards** (Phase 26.2, above):
  fan detection (top→bottom), the **two-red-border** title tightening, the left/right side-cut,
  binarization and matching all compile and run end-to-end. Tuned on the fan test photos; still needs a
  **wider card set** on device to confirm the gate defaults. Two-column (10/12) layout + dashed guide
  overlay **not built yet**.
- Both flavors compile and assemble; F‑Droid check clean.

### Tuning knobs (all in `data/ocr/`)
- ML Kit titles missed / junk: `MlKitCardScanner.RED_FRACTION` (lower) / `BANNER_FLOOR` (raise).
- Tesseract banner *missed / merged*: `RedBannerDetector.MIN_AREA_FRACTION` / `MIN_ASPECT`;
  `ScanImageOps.isSaturatedRed` thresholds. (Merged = two fanned ribbons touching in the red mask.)
- Tesseract title *cropped wrong*: **seven live sliders** in the debug screen, all backed by
  `ScanImageOps` vars — `titleBorderRed` (Rot-Gate Rand), `titleBorderWhite` (Weiß-Min Rand),
  `titleTextWhite` (Weiß-Max Text), `whiteTextBrightFraction` (Weiß-Helligkeit, shared with
  binarization), `titlePadTopFraction` (Rand oben, ±), `titlePadBottomFraction` (Rand unten, ±),
  `titleSideRed` (Seiten-Cut). The **Zeilen-Profil** plot shows the three gates + detected edges.
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

- [ ] **Validate Phase 26.2 (fan) on more cards** (different suits / banner shapes) on device and adjust
      the tuned gate defaults if needed — the debug `Zeilen‑Profil` plot + seven sliders are the tool.
- [ ] **Build the two-column (Mittelfeld 10/12) layout** — x-cluster banner blobs into 1 vs 2 columns
      (count auto from hand size), read each column top→bottom. Single-column (7) is done.
- [ ] **Dashed guide overlay** in `CameraScanScreen`, sized to the hand (1 vs 2 stacks).
- [ ] **Wire the fan into the live capture flow** — `CameraScanScreen` still uses the single-shot
      `takePicture`; the recognizer is fan-ready, the camera UX/guide is not.
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
- Tesseract can't match ML Kit on free tilted/overlapping cards (no neural detector) — it needs the
  controlled **single-column fan**; ML Kit takes a plain whole-hand photo.
- Camera needs a real device — the emulator's synthetic camera can't validate OCR.
- The fan photo must show each card's white top edge + red title banner; ribbons must stay separated in
  the red mask (don't overlap two ribbons), or they merge into one region.
