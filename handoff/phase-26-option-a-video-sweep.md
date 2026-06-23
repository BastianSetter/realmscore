# Option A — Tesseract via continuous video sweep (parked exploration)

**Status:** *parked / not built.* We are pursuing **Option B (fanned-stack single photo)** first
(see `handoff/phase-26-camera-scan.md` and the decision discussion 2026-06-17). This file records
Option A so the analysis isn't lost if we revisit it — e.g. if B's per-band tightening or two-column
split proves too brittle, or if we want a "no alignment needed" mode later.

Context: this is **only** about the **fdroid / Tesseract** path. The **play / ML Kit** path already
scans a whole-hand photo with no cropping and is considered done.

---

## The idea

Instead of forcing a layout and taking one picture (Option B), turn the camera into a **live feed**
and let the user **sweep cards past it** — *no shutter button* (a button is pointless; the manual
picker already covers tap-to-pick). The user holds one card so it fills the frame, it locks, a
haptic/check confirms, they move to the next. Throughput is paced by the user's hand.

This reuses the **26.1 single-card pipeline verbatim, at its best resolution** (one card fills the
frame = the tuned sweet spot). The framing problem of "one perfect photo" disappears.

## Why it's *not* "OCR every frame"

Tesseract is ~200–500 ms per card, serialized, and heats the CPU. OCR-ing every frame would be
thermal death (~2–4 OCR/s). The design is a **two-speed loop**:

- **Every analysis frame (cheap, ~15–50 ms):** run `RedBannerDetector.detectBest` *only*. Drives live
  framing feedback ("Banner erkannt ✓") and the capture trigger.
- **Fire Tesseract once, only when the gate opens:** a banner is **stable across K frames** AND the
  match score clears a **strong floor** AND the card isn't **already in the accumulated set**. While
  that one serialized OCR runs, drop incoming frames (`STRATEGY_KEEP_ONLY_LATEST`).

Net: OCR fires ~7× per hand (once per card), not 7 × fps. The thermal worry evaporates.

## What's reused unchanged

`detectBest`, `tightenToTitleText`, `binarizeWhite`, `recognizeLine`, the matcher, and the
`distinctBestCards` dedup (lifted to **persist across frames** instead of being scoped to one image).

## What's new (UI/VM only — no fragile new CV)

- Swap `ImageCapture.takePicture` (`ui/scan/CameraScanScreen.kt`) for **`ImageAnalysis`** with
  `STRATEGY_KEEP_ONLY_LATEST`; feed `InputImage.fromMediaImage` directly (skip the bitmap copy on the
  ML Kit side; on Tesseract side keep the existing bitmap path).
- A **stability state-machine** in the VM (track last detected banner box + match across K frames).
- A **cross-frame accumulator** (`bestPerCard`) + a live "**5/7 erkannt**" list, with captured cards
  shown so a wrong one can be tapped away.
- Keep `TessBaseAPI` warm for the session (`warmUp` already exists).

## The honest crux

Quality lives or dies on the **auto-trigger tuning** (K frames, the score floor) — without a shutter,
the risk is premature/false captures. Mitigations, all cheap: require banner stable across K frames,
require score ≥ strong floor, dedup against captured cards, debounce, show captures live, and keep
stage 2 as the final safety net. This can only be *felt* on-device, so it needs measured OCR latency
(see below) and live tuning.

## Before building A (if we ever do)

Add per-stage timing instrumentation: wrap the `analyze()` stages in `System.nanoTime()` + `Log.d`
(debug-only, no behavioral change). One scan then prints real per-stage ms on the target phone, which
sets the achievable sweep cadence and how long "hold steady" actually feels. The production path
already allocates zero debug bitmaps (`trace = null`), so these numbers are the no-debug path.

## A vs B — when to prefer A

- **B** wins when the user accepts arranging 1–2 fanned stacks (confirmed acceptable 2026-06-17, with
  photos): one photo, no video, full per-title horizontal resolution.
- **A** wins if we want a *zero-arrangement* mode ("just wave cards at it"), or if B's two-column split
  / per-band tightening turns out too brittle across suits/banner shapes.
