package de.morzo.realmscore.data.ocr

import android.graphics.Bitmap
import android.graphics.Rect
import de.morzo.realmscore.domain.model.CardDefinition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * F-Droid OCR engine (Phase 26): red-banner detection + crop (shared [ScanImageOps]) → white-text
 * binarization → Tesseract single-line OCR → shared [CardNameMatcher]. No ML Kit / Google libs.
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
        analyze(source, rotationDegrees, maxCards).regions.distinctBestCards(excludedKeys)

    override suspend fun recognizeDetailed(
        source: Bitmap,
        rotationDegrees: Int,
        maxCards: Int,
    ): ScanReport = analyze(source, rotationDegrees, maxCards)

    private suspend fun analyze(
        source: Bitmap,
        rotationDegrees: Int,
        maxCards: Int,
    ): ScanReport = withContext(Dispatchers.Default) {
        val upright = ScanImageOps.rotate(source, rotationDegrees)

        val banners = RedBannerDetector.detect(upright, maxCards)
        if (banners.isNotEmpty()) {
            return@withContext ScanReport(
                mode = "Rot-Banner",
                regionCount = banners.size,
                regions = banners.map { bannerRegion(upright, it) },
            )
        }

        val contours = CardRectangleDetector.detect(upright, maxCards)
        if (contours.isNotEmpty()) {
            return@withContext ScanReport(
                mode = "Kontur-Fallback",
                regionCount = contours.size,
                regions = contours.map { fallbackRegion(upright, it) },
            )
        }

        ScanReport(
            mode = "Ganzes Bild",
            regionCount = 1,
            regions = listOf(fallbackRegion(upright, Rect(0, 0, upright.width, upright.height))),
        )
    }

    private suspend fun bannerRegion(bitmap: Bitmap, rect: Rect): ScanRegion {
        val title = ScanImageOps.tightenToTitleBand(ScanImageOps.cropRect(bitmap, rect))
        val processed = ScanImageOps.binarizeWhite(title, ScanImageOps.TITLE_TARGET_HEIGHT)
        val line = tesseract.recognizeLine(processed)
        val text = line?.text.orEmpty()
        return ScanRegion("Banner (rot)", processed, text, line?.confidence ?: 0, matcher.scoredCandidates(text).take(3))
    }

    /** No banner: try the top name band, then the bottom; keep the better. */
    private suspend fun fallbackRegion(bitmap: Bitmap, rect: Rect): ScanRegion {
        val top = band(bitmap, rect, fromBottom = false)
        if (top.topScore >= STRONG_MATCH) return top
        val bottom = band(bitmap, rect, fromBottom = true)
        return if (bottom.topScore > top.topScore) bottom else top
    }

    private suspend fun band(bitmap: Bitmap, rect: Rect, fromBottom: Boolean): ScanRegion {
        val processed = ScanImageOps.preprocessForOcr(ScanImageOps.cropNameBand(bitmap, rect, fromBottom))
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
