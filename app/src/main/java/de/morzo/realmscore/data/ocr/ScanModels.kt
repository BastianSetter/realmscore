package de.morzo.realmscore.data.ocr

import android.graphics.Bitmap
import de.morzo.realmscore.domain.model.CardDefinition

/** Sanity floor for accepting a best match — not a confidence/ambiguity gate (spec: best match wins). */
internal const val MIN_MATCH_SCORE = 0.34f

/** Below this OCR length the text is treated as noise and produces no candidates. */
internal const val MIN_OCR_LENGTH = 2

/** One card name candidate with its fuzzy-match score against the OCR text. */
data class CandidateScore(val card: CardDefinition, val score: Float)

/** Diagnostics for one detected card region (used by the scan-debug screen). */
data class ScanRegion(
    /** Where the crop came from — e.g. "Banner (rot)", "oben", "unten". */
    val band: String,
    /** The (processed) crop that was actually fed to the OCR engine. */
    val crop: Bitmap,
    val ocrText: String,
    val confidence: Int,
    val candidates: List<CandidateScore>,
) {
    val topScore: Float get() = candidates.firstOrNull()?.score ?: 0f
    val bestCard: CardDefinition?
        get() = candidates.firstOrNull()?.takeIf { it.score >= MIN_MATCH_SCORE }?.card
}

/**
 * One labeled intermediate image of the single-card pipeline, for the scan-debug screen. Lets the
 * developer see every step *before* the final binarized crop (red mask, banner pick, raw crop,
 * tightened ribbon, …), not just the image handed to OCR. Only populated by `recognizeDetailed`.
 */
data class ScanStage(
    val label: String,
    val image: Bitmap,
    /** Short caption: dimensions, candidate counts, or a warning when a step degenerated. */
    val note: String = "",
)

/** Full diagnostics of one photo: which engine/path ran, how many regions, and per-region results. */
data class ScanReport(
    val mode: String,
    val regionCount: Int,
    val regions: List<ScanRegion>,
    /** Ordered intermediate images of the pipeline (debug screen only; empty in the production path). */
    val stages: List<ScanStage> = emptyList(),
)

/**
 * Recognizes the cards in a photo (Phase 26). The detection, cropping and name-matching are shared
 * (`main`); only the OCR engine behind this interface is flavour-specific — Tesseract in the `fdroid`
 * flavour, ML Kit in the `play` flavour, each provided via that flavour's `ScannerFactory`.
 */
interface CardScanner {
    /**
     * True when this engine needs the cards laid out as an overlapping **fan** (Tesseract: no neural
     * detector, so it relies on the controlled single-/two-column fan). False when a free whole-hand
     * photo works (ML Kit). The camera screen draws its dashed fan guide + matching hint only when true.
     */
    val usesFanLayout: Boolean

    /** Optional background warm-up (model load); safe to call repeatedly. */
    suspend fun warmUp()

    suspend fun recognizeMultiple(
        source: Bitmap,
        rotationDegrees: Int,
        maxCards: Int,
        /** Cards already placed in other entries this round — never assigned again (cards are unique). */
        excludedKeys: Set<String> = emptySet(),
    ): ScanResult

    /** Same pipeline, but returns full diagnostics for the scan-debug screen. */
    suspend fun recognizeDetailed(
        source: Bitmap,
        rotationDegrees: Int,
        maxCards: Int,
    ): ScanReport
}

/**
 * Result of a scan: the distinct cards to place, plus how many recognised cards were **dropped as
 * conflicts** (already used in another entry, or a duplicate region) — surfaced to the user so the
 * resulting empty slot isn't a mystery.
 */
data class ScanResult(val cards: List<CardDefinition>, val skippedConflicts: Int)

/**
 * The best card per region, most-confident first, skipping [excludedKeys] and de-duplicating so each
 * physical card is assigned at most once. A card already in another entry (or matched by two regions)
 * is dropped rather than substituted, leaving that slot empty for the user to fill in stage 2.
 */
internal fun List<ScanRegion>.distinctBestCards(excludedKeys: Set<String>): ScanResult {
    val used = excludedKeys.toMutableSet()
    val result = mutableListOf<CardDefinition>()
    var skipped = 0
    for (region in sortedByDescending { it.topScore }) {
        val card = region.bestCard ?: continue
        if (!used.add(card.key)) { skipped++; continue }  // already used by another entry or earlier region
        result += card
    }
    return ScanResult(result, skipped)
}
