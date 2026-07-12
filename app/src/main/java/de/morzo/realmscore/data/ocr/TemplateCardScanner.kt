package de.morzo.realmscore.data.ocr

import android.graphics.Bitmap
import android.graphics.Color
import de.morzo.realmscore.data.cards.CardLookup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Flavor-independent template-matching scanner (Phase 29): instead of OCR, each red banner is
 * normalized to a canonical grayscale Standardbox ([BannerNormalizer]) and matched against stored
 * reference templates by NCC ([TemplateMatcher]). Shares the fan layout and the [RedBannerDetector]
 * with the Tesseract path, so it drops into the same camera workflow (`usesFanLayout = true`). Works
 * in both flavors — no OCR engine, no Google libs.
 *
 * Templates are loaded lazily (assets + filesDir overrides) on first use / [warmUp]. Cards without a
 * template are simply not matchable; a region that can't be normalized leaves its slot empty
 * (stage-2 correction) rather than forcing a wrong match.
 */
class TemplateCardScanner(
    private val templateStore: BannerTemplateStore,
    private val cardLookup: CardLookup,
) : CardScanner {

    override val usesFanLayout: Boolean = true

    private val normalizer = BannerNormalizer()

    @Volatile private var templates: List<BannerTemplate> = emptyList()
    @Volatile private var matcher: TemplateMatcher = TemplateMatcher(emptyList())

    override suspend fun warmUp() {
        withContext(Dispatchers.IO) { reloadTemplates() }
    }

    /** Number of loadable templates (for the Scanner-Test inventory `x/N`). */
    fun templateCount(): Int = templateStore.availableKeys().size

    /** Force a template reload (after the Scanner-Test screen saved/removed a filesDir override). */
    fun reload() = reloadTemplates()

    private fun reloadTemplates() {
        templates = templateStore.load()
        matcher = TemplateMatcher(templates)
    }

    private fun ensureLoaded() {
        if (matcher.templateCount == 0 && templateStore.availableKeys().isNotEmpty()) reloadTemplates()
    }

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

    private suspend fun analyze(
        source: Bitmap,
        rotationDegrees: Int,
        maxCards: Int,
        trace: MutableList<ScanStage>?,
    ): ScanReport = withContext(Dispatchers.Default) {
        ensureLoaded()
        val upright = ScanImageOps.rotate(source, rotationDegrees)

        val banners = if (trace != null) {
            val t = RedBannerDetector.detectFannedTraced(upright, maxCards)
            trace += ScanStage("Rot-Maske", t.mask, "${t.banners.size} Banner · ${t.mask.width}×${t.mask.height}px")
            trace += ScanStage(
                "Banner-Auswahl", t.overlay,
                if (t.banners.isEmpty()) "⚠ keine Banner erkannt" else "grün = gewählt (Lesereihenfolge), gelb = verworfen",
            )
            t.banners
        } else {
            RedBannerDetector.detectFanned(upright, maxCards)
        }

        if (banners.isEmpty()) {
            return@withContext ScanReport("Bitmap · kein Banner", 0, emptyList(), trace.orEmpty(), 0)
        }

        val start = System.nanoTime()
        val regions = banners.mapIndexed { i, rect ->
            val norm = normalizer.normalize(upright, rect, trace = trace != null)
            val label = "Karte ${i + 1}/${banners.size}"
            if (!norm.isSuccess) {
                appendFailureStages(trace, label, norm)
                emptyRegion()
            } else {
                val scored = matcher.scoredCandidates(norm.box!!).take(3)
                val candidates = scored.mapNotNull { (key, score) ->
                    cardLookup.getByKey(key)?.let { CandidateScore(it, score) }
                }
                appendSuccessStages(trace, label, norm, candidates)
                ScanRegion(
                    band = "Bitmap",
                    crop = norm.boxBitmap!!,
                    ocrText = "",
                    confidence = ((candidates.firstOrNull()?.score ?: 0f) * 100).toInt(),
                    candidates = candidates,
                    minScore = BannerNormalizer.MIN_TEMPLATE_SCORE,
                )
            }
        }
        val durationMs = (System.nanoTime() - start) / 1_000_000

        ScanReport(
            mode = "Bitmap · ${regions.size} Banner · ${matcher.templateCount} Vorlagen",
            regionCount = regions.size,
            regions = regions,
            stages = trace.orEmpty(),
            durationMs = durationMs,
        )
    }

    // Un-normalizable region: no candidates → the slot stays empty (stage-2 correction).
    private fun emptyRegion() = ScanRegion(
        band = "Bitmap ⚠",
        crop = PLACEHOLDER,
        ocrText = "",
        confidence = 0,
        candidates = emptyList(),
        minScore = BannerNormalizer.MIN_TEMPLATE_SCORE,
    )

    private fun appendSuccessStages(
        trace: MutableList<ScanStage>?,
        label: String,
        norm: BannerNormalizer.BannerNormalization,
        candidates: List<CandidateScore>,
    ) {
        trace ?: return
        // The ordered geometry stages (overlays + colour-value plots, each after its own step).
        norm.stages.forEach { trace += ScanStage("$label · ${it.label}", it.image, it.note) }
        norm.boxBitmap?.let {
            val best = candidates.firstOrNull()
            val note = if (best != null) "bestes Template: ${best.card.nameDe} — ${(best.score * 100).toInt()}%"
            else "kein Template über Schwelle"
            trace += ScanStage("$label · 6 · Standardbox (Graubild)", it, note)
        }
        candidates.firstOrNull()?.let { best ->
            templateBitmap(best.card.key)?.let { tpl ->
                trace += ScanStage("$label · 6 · bestes Template", tpl, "${best.card.nameDe} — ${(best.score * 100).toInt()}%")
            }
        }
    }

    private fun appendFailureStages(
        trace: MutableList<ScanStage>?,
        label: String,
        norm: BannerNormalizer.BannerNormalization,
    ) {
        trace ?: return
        norm.stages.forEach { trace += ScanStage("$label · ${it.label}", it.image, it.note) }
        trace += ScanStage(
            "$label · ⚠ nicht normalisierbar", norm.stages.lastOrNull()?.image ?: PLACEHOLDER,
            "⚠ Schritt fehlgeschlagen: ${norm.failedStep} — Slot bleibt leer",
        )
    }

    /** Renders a stored template's [GrayImage] to a viewable bitmap for the debug stage. */
    private fun templateBitmap(cardKey: String): Bitmap? {
        val gray = templates.firstOrNull { it.cardKey == cardKey }?.image ?: return null
        val out = IntArray(gray.pixels.size) {
            val v = (gray.pixels[it] * 255).toInt().coerceIn(0, 255)
            Color.rgb(v, v, v)
        }
        return Bitmap.createBitmap(gray.width, gray.height, Bitmap.Config.ARGB_8888)
            .also { it.setPixels(out, 0, gray.width, 0, 0, gray.width, gray.height) }
    }

    private companion object {
        /** 1×1 stand-in so a failed (un-normalizable) region still has a non-null crop. */
        val PLACEHOLDER: Bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
    }
}
