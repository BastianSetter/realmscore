package de.morzo.realmscore.data.ocr

import android.graphics.Bitmap
import android.graphics.Rect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * F-Droid OCR engine (Phase 26.1): tuned for **one upright card filling the frame** (portrait), which
 * is the only layout the Tesseract engine handles reliably (no neural text detector → tilt/overlap
 * defeat it). Pipeline: locate the single red title banner near the top (shared [RedBannerDetector] /
 * [ScanImageOps]) → white-text binarization → Tesseract single-line OCR → shared [CardNameMatcher].
 * Always yields at most one card, regardless of [maxCards]. No ML Kit / Google libs.
 */
class TesseractCardScanner(
    private val tesseract: TesseractManager,
    private val matcher: CardNameMatcher,
) : CardScanner {

    override suspend fun warmUp() = tesseract.warmUp()

    override suspend fun recognizeMultiple(
        source: Bitmap,
        rotationDegrees: Int,
        maxCards: Int,
        excludedKeys: Set<String>,
    ): ScanResult =
        analyze(source, rotationDegrees, trace = null).regions.distinctBestCards(excludedKeys)

    override suspend fun recognizeDetailed(
        source: Bitmap,
        rotationDegrees: Int,
        @Suppress("UNUSED_PARAMETER") maxCards: Int,
    ): ScanReport = analyze(source, rotationDegrees, trace = mutableListOf())

    /**
     * Runs the single-card pipeline. When [trace] is non-null (debug screen) every intermediate image
     * is appended to it; in the production path it stays null so no debug bitmaps are allocated.
     */
    private suspend fun analyze(
        source: Bitmap,
        rotationDegrees: Int,
        trace: MutableList<ScanStage>?,
    ): ScanReport = withContext(Dispatchers.Default) {
        val upright = ScanImageOps.rotate(source, rotationDegrees)

        // New assumption (Phase 26.1): one upright card filling the frame. Find its single red title
        // banner; if none stands out (glare/washed-out red), fall back to the image's top name band.
        val banner = if (trace != null) {
            val t = RedBannerDetector.detectBestTraced(upright)
            trace += ScanStage("Rot-Maske", t.mask, "${t.candidateCount} Banner-Kandidat(en) · ${t.mask.width}×${t.mask.height}px")
            trace += ScanStage(
                "Banner-Auswahl", t.overlay,
                if (t.banner != null) "grün = gewählt, gelb = verworfen" else "⚠ kein roter Banner – Fallback auf Namensband",
            )
            t.banner
        } else {
            RedBannerDetector.detectBest(upright)
        }

        val region = if (banner != null) {
            bannerRegion(upright, banner, trace)
        } else {
            fallbackRegion(upright, Rect(0, 0, upright.width, upright.height), trace)
        }
        ScanReport(
            mode = if (banner != null) "Einzelkarte · Rot-Banner" else "Einzelkarte · Namensband",
            regionCount = 1,
            regions = listOf(region),
            stages = trace.orEmpty(),
        )
    }

    private suspend fun bannerRegion(bitmap: Bitmap, rect: Rect, trace: MutableList<ScanStage>?): ScanRegion {
        // Crop the red blob, then tighten vertically to the white title line (drops the ribbon's
        // tails and any illustration the box leaked in below the title); binarize white-on-red.
        val crop = ScanImageOps.cropRect(bitmap, rect)
        trace?.add(ScanStage("Crop (Banner-Blob)", crop, "${crop.width}×${crop.height}px"))
        trace?.add(
            ScanStage(
                "Zeilen-Profil (Rot/Weiß)", ScanImageOps.titleProfilePlot(crop),
                "rot = Rot-Anteil, blau = Weiß-Anteil · gestrichelt = Schwellen · orange = Bannerstart, grün = Textkanten",
            ),
        )
        val ribbon = ScanImageOps.tightenToTitleText(crop)
        trace?.add(
            ScanStage(
                "Titelzeile (Weißtext)", ribbon,
                if (ribbon.height >= crop.height) "⚠ unverändert – keine Titelschrift erkannt (Schwelle anpassen)"
                else "auf Titelzeile zugeschnitten · ${ribbon.width}×${ribbon.height}px",
            ),
        )
        val processed = ScanImageOps.binarizeWhite(ribbon, ScanImageOps.TITLE_TARGET_HEIGHT)
        trace?.add(ScanStage("Binarisiert → Tesseract", processed, "${processed.width}×${processed.height}px"))
        val line = tesseract.recognizeLine(processed)
        val text = line?.text.orEmpty()
        return ScanRegion("Banner (rot)", processed, text, line?.confidence ?: 0, matcher.scoredCandidates(text).take(3))
    }

    /** No banner: try the top name band, then the bottom; keep the better. */
    private suspend fun fallbackRegion(bitmap: Bitmap, rect: Rect, trace: MutableList<ScanStage>?): ScanRegion {
        val top = band(bitmap, rect, fromBottom = false, trace)
        if (top.topScore >= STRONG_MATCH) return top
        val bottom = band(bitmap, rect, fromBottom = true, trace)
        return if (bottom.topScore > top.topScore) bottom else top
    }

    private suspend fun band(bitmap: Bitmap, rect: Rect, fromBottom: Boolean, trace: MutableList<ScanStage>?): ScanRegion {
        val processed = ScanImageOps.preprocessForOcr(ScanImageOps.cropNameBand(bitmap, rect, fromBottom))
        trace?.add(ScanStage("Namensband ${if (fromBottom) "unten" else "oben"}", processed, "${processed.width}×${processed.height}px"))
        val line = tesseract.recognizeLine(processed)
        val text = line?.text.orEmpty()
        return ScanRegion(
            band = if (fromBottom) "unten" else "oben",
            crop = processed,
            ocrText = text,
            confidence = line?.confidence ?: 0,
            candidates = matcher.scoredCandidates(text).take(3),
        )
    }

    private companion object {
        const val STRONG_MATCH = 0.6f  // top band this good → skip the bottom-band retry
    }
}
