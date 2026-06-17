package de.morzo.realmscore.data.ocr

import android.graphics.Bitmap
import android.graphics.Rect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * F-Droid OCR engine (Phase 26.2): scans a **single-column fan** of cards. Tesseract has no neural
 * text detector, so a free tilt/overlap layout defeats it; a fan does not, because each card shows its
 * own white top edge + red title ribbon (cards run white→red top to bottom), giving one red banner blob
 * per card at near-full horizontal resolution. Pipeline: find all red title banners top→bottom (shared
 * [RedBannerDetector]), then per banner crop → tighten to the title line → white-text binarization →
 * Tesseract single-line OCR → shared [CardNameMatcher]. No ML Kit / Google libs.
 */
class TesseractCardScanner(
    private val tesseract: TesseractManager,
    private val matcher: CardNameMatcher,
) : CardScanner {

    override val usesFanLayout: Boolean = true

    override suspend fun warmUp() = tesseract.warmUp()

    override suspend fun recognizeMultiple(
        source: Bitmap,
        rotationDegrees: Int,
        maxCards: Int,
        excludedKeys: Set<String>,
    ): ScanResult =
        analyze(source, rotationDegrees, maxCards, trace = null).regions.distinctBestCards(excludedKeys)

    override suspend fun recognizeDetailed(
        source: Bitmap,
        rotationDegrees: Int,
        maxCards: Int,
    ): ScanReport = analyze(source, rotationDegrees, maxCards, trace = mutableListOf())

    /**
     * Runs the fan pipeline. When [trace] is non-null (debug screen) every intermediate image is
     * appended to it; in the production path it stays null so no debug bitmaps are allocated.
     */
    private suspend fun analyze(
        source: Bitmap,
        rotationDegrees: Int,
        maxCards: Int,
        trace: MutableList<ScanStage>?,
    ): ScanReport = withContext(Dispatchers.Default) {
        val upright = ScanImageOps.rotate(source, rotationDegrees)

        // Phase 26.2: a single-column fan. Every card shows its own white top edge + red title ribbon,
        // so detect all red banners top→bottom and OCR each title with the proven per-card pipeline.
        val banners = if (trace != null) {
            val t = RedBannerDetector.detectFannedTraced(upright, maxCards)
            trace += ScanStage("Rot-Maske", t.mask, "${t.banners.size} Banner · ${t.mask.width}×${t.mask.height}px")
            trace += ScanStage(
                "Banner-Auswahl", t.overlay,
                if (t.banners.isEmpty()) "⚠ keine Banner – Fallback auf Namensband"
                else "grün = gewählt (in Lesereihenfolge nummeriert: je Spalte oben→unten), gelb = verworfen",
            )
            t.banners
        } else {
            RedBannerDetector.detectFanned(upright, maxCards)
        }

        if (banners.isEmpty()) {
            // No red banner anywhere (glare/washed-out red): fall back to the whole-image name band.
            val region = fallbackRegion(upright, Rect(0, 0, upright.width, upright.height), trace)
            return@withContext ScanReport("Fächer · kein Banner", 1, listOf(region), trace.orEmpty())
        }

        val regions = banners.mapIndexed { i, rect ->
            bannerRegion(upright, rect, "Karte ${i + 1}/${banners.size}", trace)
        }
        ScanReport(
            mode = "Fächer · ${regions.size} Banner",
            regionCount = regions.size,
            regions = regions,
            stages = trace.orEmpty(),
        )
    }

    private suspend fun bannerRegion(
        bitmap: Bitmap,
        rect: Rect,
        cardLabel: String,
        trace: MutableList<ScanStage>?,
    ): ScanRegion {
        // Crop the red blob, then tighten vertically to the white title line (drops the card's white
        // top edge above and any illustration the box leaked in below the title); binarize white-on-red.
        val crop = ScanImageOps.cropRect(bitmap, rect)
        trace?.add(ScanStage("$cardLabel · Crop (Banner-Blob)", crop, "${crop.width}×${crop.height}px"))
        trace?.add(
            ScanStage(
                "$cardLabel · Zeilen-Profil (Rot/Weiß)", ScanImageOps.titleProfilePlot(crop),
                "rot = Rot-Anteil, blau = Weiß-Anteil · gestrichelt = Gates · orange = Ränder, grün = Textzeile",
            ),
        )
        val ribbon = ScanImageOps.tightenToTitleText(crop)
        trace?.add(
            ScanStage(
                "$cardLabel · Titelzeile (Weißtext)", ribbon,
                if (ribbon.height >= crop.height) "⚠ unverändert – keine Titelschrift erkannt (Schwelle anpassen)"
                else "auf Titelzeile zugeschnitten · ${ribbon.width}×${ribbon.height}px",
            ),
        )
        val sides = ScanImageOps.tightenToTitleSides(ribbon)
        trace?.add(
            ScanStage(
                "$cardLabel · Seiten beschnitten", sides,
                if (sides.width >= ribbon.width) "⚠ unverändert – keine volle Rot-Spalte (Seiten-Cut anpassen)"
                else "links/rechts auf Rot-Band beschnitten · ${sides.width}×${sides.height}px",
            ),
        )
        val processed = ScanImageOps.binarizeWhite(sides, ScanImageOps.TITLE_TARGET_HEIGHT)
        trace?.add(ScanStage("$cardLabel · Binarisiert → Tesseract", processed, "${processed.width}×${processed.height}px"))
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
