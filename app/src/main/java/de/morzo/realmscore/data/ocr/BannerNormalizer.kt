package de.morzo.realmscore.data.ocr

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import kotlin.math.abs

/**
 * Turns a red-banner blob rect (from the shared [RedBannerDetector]) into the canonical grayscale
 * **Standardbox** used for template matching (Phase 29). A deterministic anchor construction makes the
 * crop identical across the reference templates and live camera photos, so [TemplateMatcher] can rely
 * on plain NCC with a tiny search window instead of OCR:
 *
 *  0. grow the blob left (past the non-red number bubble) + a small margin all round → *raw crop*
 *  1. two vertical probe lines find the white→red top edge of the ribbon (text-free by construction)
 *  2. de-rotate the crop so that edge is horizontal (±25° cap), then re-measure the edge → `up`
 *  3. from a point safely inside the red, walk left to the golden number-bubble ring → `gold`
 *  4. from probe line 2, walk **right** along that same bubble row (crossing the white title letters
 *     via a sustained-gap tolerance) to the ribbon's red→background edge → `red`
 *  5. with `width = red − gold`, define the Standardbox as `top=up`, `right=red`,
 *     `bottom=up + BOX_FACTOR_A·width`, `left=gold − BOX_FACTOR_B·width`; crop it, scale to
 *     [TEMPLATE_W]×[TEMPLATE_H], convert to grayscale luminance (no binarization).
 *
 * The old banner-*bottom* detection (a vertical red run grown downward) was dropped: the area below the
 * ribbon is frequently red too, so the run overshot. Anchoring the box to the ribbon's horizontal right
 * end instead avoids relying on the unreliable bottom border.
 *
 * If any step degenerates the region is *not normalizable* → [BannerNormalization.box] is null with a
 * German [failedStep] label; the caller leaves the slot empty (stage-2 correction) and shows a ⚠ stage.
 *
 * Threshold/position factors are tunable `var`s (live-edited from the Scanner-Test screen). The
 * `BOX_FACTOR_*` box proportions are tunable too during calibration, but once fixed they define the
 * template geometry — changing them invalidates every stored template. NB: the shipped factor/gold
 * values are reasonable starting points — they must be dialed in once against real reference photos on
 * the Scanner-Test screen before templates are cut.
 */
class BannerNormalizer {

    /** Result of one normalization: the Standardbox on success, plus trace data for the debug screen. */
    data class BannerNormalization(
        /** Canonical grayscale Standardbox for matching, or null when a step failed. */
        val box: GrayImage?,
        /** The Standardbox rendered as a viewable grayscale bitmap (save-as-template + debug). */
        val boxBitmap: Bitmap?,
        /** German name of the step that degenerated, or null on success. */
        val failedStep: String?,
        val angleDeg: Double,
        /** `gold`: right edge of the golden number-bubble ring. */
        val bubbleRight: Int,
        /** `red`: right end of the red ribbon (horizontal scan). */
        val ribbonRight: Int,
        /** `up`: banner top edge (post-rotation). */
        val bannerTop: Int,
        /**
         * Ordered intermediate stages for the Scanner-Test screen, each an overlay/plot with a note,
         * emitted in pipeline order with each colour-value plot right after the step it explains.
         * Debug only; empty in production. Kept up to (and including) the failing step.
         */
        val stages: List<Stage> = emptyList(),
    ) {
        val isSuccess: Boolean get() = box != null
    }

    /** One labeled intermediate image + caption of the normalization (debug screen). */
    data class Stage(val label: String, val image: Bitmap, val note: String = "")

    /**
     * Normalizes the [blob] region of [source] to a Standardbox. When [trace] is true the returned
     * result also carries the annotated intermediate bitmaps for the Scanner-Test screen.
     */
    fun normalize(source: Bitmap, blob: Rect, trace: Boolean): BannerNormalization {
        val blobH = blob.height().coerceAtLeast(1)
        // Ordered debug stages: each colour-value plot lands right after the step it explains.
        val stages = mutableListOf<Stage>()
        fun fail(step: String) = BannerNormalization(
            null, null, step, 0.0, -1, -1, -1, stages.toList(),
        )

        // Step 0 — raw crop: grow left past the (non-red) number bubble + a small margin. The **top**
        // margin is kept separately tiny so red *above* the banner isn't pulled into the crop and then
        // mistaken for the top edge by the probe lines.
        val margin = (blobH * RAW_MARGIN).toInt()
        val topMargin = (blobH * RAW_MARGIN_TOP).toInt()
        val rawRect = Rect(
            (blob.left - (blobH * BUBBLE_PAD).toInt()).coerceAtLeast(0),
            (blob.top - topMargin).coerceAtLeast(0),
            (blob.right + margin).coerceAtMost(source.width),
            (blob.bottom + margin).coerceAtMost(source.height),
        )
        if (rawRect.width() < 8 || rawRect.height() < 8) return fail("Roh-Crop")
        val rawCrop = Bitmap.createBitmap(source, rawRect.left, rawRect.top, rawRect.width(), rawRect.height())

        // Step 1 — top edge via two probe lines (right of the bubble, left of the ribbon tail).
        val rawPixels = rawCrop.pixels()
        val x1 = (rawCrop.width * PROBE_X1).toInt().coerceIn(0, rawCrop.width - 1)
        val x2 = (rawCrop.width * PROBE_X2).toInt().coerceIn(0, rawCrop.width - 1)
        val y1 = BannerGeometry.firstRedRunTop(rawPixels, rawCrop.width, rawCrop.height, x1, EDGE_MIN_RUN, redMinRatioTop)
        val y2 = BannerGeometry.firstRedRunTop(rawPixels, rawCrop.width, rawCrop.height, x2, EDGE_MIN_RUN, redMinRatioTop)
        if (trace) {
            stages += Stage(
                "1 · Roh-Crop + Probelinien", rawProbeOverlay(rawCrop, x1, x2, y1, y2),
                "gelb = Probelinien, grün = Oberkanten-Treffer",
            )
            stages += Stage(
                "1 · Probelinie 1 · Rot-Gate oben", redColumnPlot(rawPixels, rawCrop.width, rawCrop.height, x1, listOfNotNull(edge(y1, EDGE_HIT)), redMinRatioTop),
                RED_TOP_PLOT_NOTE,
            )
            stages += Stage(
                "1 · Probelinie 2 · Rot-Gate oben", redColumnPlot(rawPixels, rawCrop.width, rawCrop.height, x2, listOfNotNull(edge(y2, EDGE_HIT)), redMinRatioTop),
                RED_TOP_PLOT_NOTE,
            )
        }
        if (y1 < 0 || y2 < 0) return fail("Oberkante (Probelinie)")

        // Step 2 — rotation: level the top edge, then re-measure it once on the rotated crop.
        val angle = BannerGeometry.angleDegrees(x1, y1, x2, y2)
        val leveled: Bitmap
        if (abs(angle) > ROTATE_EPS) {
            val capped = angle.coerceIn(-MAX_ROTATE, MAX_ROTATE)
            leveled = rotate(rawCrop, -capped)
        } else {
            leveled = rawCrop
        }
        val pixels = if (leveled === rawCrop) rawPixels else leveled.pixels()
        val ax1 = (leveled.width * PROBE_X1).toInt().coerceIn(0, leveled.width - 1)
        val topEdge = BannerGeometry.firstRedRunTop(pixels, leveled.width, leveled.height, ax1, EDGE_MIN_RUN, redMinRatioTop)
        if (trace && leveled !== rawCrop) {
            stages += Stage("2 · Entdreht (${"%.1f".format(angle)}°)", leveled.copy(Bitmap.Config.ARGB_8888, false), "Oberkante nivelliert")
        }
        if (topEdge < 0) return BannerNormalization(null, null, "Oberkante nach Drehung", angle, -1, -1, -1, stages.toList())
        val up = topEdge

        // Step 3 — `gold`: golden number-bubble ring, searching the bubble row leftward for gold.
        val seedRow = (up + DESCEND * blobH).toInt().coerceIn(0, leveled.height - 1)
        val gold = BannerGeometry.goldRightEdge(
            pixels, leveled.width, leveled.height, seedRow, ax1, EDGE_MIN_RUN,
            goldMinRed, goldMaxBlueGreenRatio,
        )
        if (trace) {
            stages += Stage(
                "3 · Gold-Suche (Bubble-Zeile)", goldSearchOverlay(leveled, pixels, seedRow, ax1, gold),
                "gescannte Zeile bei y=$seedRow, von x=$ax1 nach links · unten die Pixelzeile vergrößert · " +
                    if (gold >= 0) "rot = gefundenes gold=$gold" else "kein Gold gefunden",
            )
            stages += Stage(
                "3 · Gold-Gate (Grün/Blau)", goldRowPlot(pixels, leveled.width, seedRow, gold),
                GOLD_PLOT_NOTE,
            )
        }
        if (gold < 0) return BannerNormalization(null, null, "Gold-Ring", angle, -1, -1, up, stages.toList())

        // Step 4 — `red`: ribbon right end. Scan the same bubble row (red strip, above the letters)
        // rightward until the red ends. The start sits far right (its own knob, decoupled from the
        // rotation probe) so mid-ribbon glare gaps can't stop the scan short of the true end.
        val rStart = (leveled.width * RIGHT_SCAN_START).toInt().coerceIn(0, leveled.width - 1)
        val red = BannerGeometry.rightRedEnd(
            pixels, leveled.width, leveled.height, seedRow, rStart, RIGHT_GAP_PX, redMinRatioRight,
        )
        if (trace) {
            stages += Stage(
                "4 · Bandende rechts (Bubble-Zeile)", rightScanOverlay(leveled, pixels, seedRow, rStart, red),
                "gescannte Zeile bei y=$seedRow, von x=$rStart nach rechts · unten die Pixelzeile vergrößert · " +
                    if (red >= 0) "rot = gefundenes red=$red" else "kein Rot gefunden",
            )
            stages += Stage(
                "4 · Rot-Gate rechts (Zeile)", redRowPlot(pixels, leveled.width, seedRow, rStart, red, redMinRatioRight),
                RED_RIGHT_PLOT_NOTE,
            )
        }
        if (red <= gold) return BannerNormalization(null, null, "Bandende (rechts)", angle, gold, red, up, stages.toList())
        val width = (red - gold).coerceAtLeast(1)

        // Step 5 — Standardbox from (gold, red, up): top=up, right=red, bottom=up+A·width, left=gold−B·width.
        val boxRect = Rect(
            (gold - BOX_FACTOR_B * width).toInt().coerceIn(0, leveled.width - 2),
            up.coerceIn(0, leveled.height - 2),
            (red + 1).coerceIn(1, leveled.width),
            (up + BOX_FACTOR_A * width).toInt().coerceIn(1, leveled.height),
        )
        if (boxRect.width() < 4 || boxRect.height() < 4) {
            return BannerNormalization(null, null, "Standardbox (leer)", angle, gold, red, up, stages.toList())
        }
        if (trace) {
            stages += Stage(
                "5 · Anker + Standardbox", boxOverlay(leveled, gold, red, up, boxRect),
                "gold=$gold (cyan), red=$red (grün), up=$up (orange), Box (magenta)",
            )
        }
        val boxCrop = Bitmap.createBitmap(leveled, boxRect.left, boxRect.top, boxRect.width(), boxRect.height())
        val (gray, grayBitmap) = toCanonicalGray(boxCrop)
        return BannerNormalization(gray, grayBitmap, null, angle, gold, red, up, stages.toList())
    }

    private fun edge(y: Int, color: Int): Pair<Int, Int>? = if (y >= 0) y to color else null

    /**
     * Gold-search visualization (step 3): the de-rotated crop with the scanned bubble row drawn as a
     * horizontal line, a tick at the search start [startX] and (if found) at [bubbleRight]; below the
     * crop the *actual* scanned pixel row is magnified into a strip so the searched colours are
     * visible, with the same start/result ticks.
     */
    private fun goldSearchOverlay(leveled: Bitmap, pixels: IntArray, row: Int, startX: Int, bubbleRight: Int): Bitmap {
        val w = leveled.width
        val stripH = 20
        val out = Bitmap.createBitmap(w, leveled.height + stripH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(Color.rgb(24, 24, 24))
        canvas.drawBitmap(leveled, 0f, 0f, null)

        // Magnified strip of the exact scanned row.
        val bar = Paint()
        for (x in 0 until w) {
            bar.color = pixels[row * w + x]
            canvas.drawRect(x.toFloat(), leveled.height.toFloat(), x + 1f, (leveled.height + stripH).toFloat(), bar)
        }
        // The scanned row on the crop.
        val line = Paint().apply { color = Color.YELLOW; strokeWidth = 2f }
        canvas.drawLine(0f, row.toFloat(), w.toFloat(), row.toFloat(), line)
        // Start tick (white) and found bubbleRight (red), on both crop and strip.
        val startPaint = Paint().apply { color = Color.WHITE; strokeWidth = 2f }
        canvas.drawLine(startX.toFloat(), 0f, startX.toFloat(), out.height.toFloat(), startPaint)
        if (bubbleRight in 0 until w) {
            val found = Paint().apply { color = Color.rgb(230, 30, 30); strokeWidth = 3f }
            canvas.drawLine(bubbleRight.toFloat(), 0f, bubbleRight.toFloat(), out.height.toFloat(), found)
        }
        return out
    }

    private fun Bitmap.pixels(): IntArray {
        val p = IntArray(width * height)
        getPixels(p, 0, width, 0, 0, width, height)
        return p
    }

    private fun rotate(src: Bitmap, degrees: Double): Bitmap {
        val matrix = Matrix().apply { postRotate(degrees.toFloat(), src.width / 2f, src.height / 2f) }
        return Bitmap.createBitmap(src, 0, 0, src.width, src.height, matrix, true)
    }

    /** Scales to canonical size and returns both the matcher's [GrayImage] and a viewable gray bitmap. */
    private fun toCanonicalGray(bitmap: Bitmap): Pair<GrayImage, Bitmap> {
        val scaled = Bitmap.createScaledBitmap(bitmap, TEMPLATE_W, TEMPLATE_H, true)
        return grayOf(scaled)
    }

    private fun rawProbeOverlay(rawCrop: Bitmap, x1: Int, x2: Int, y1: Int, y2: Int): Bitmap {
        val out = rawCrop.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(out)
        val probe = Paint().apply { color = Color.YELLOW; strokeWidth = 2f }
        val hit = Paint().apply { color = Color.GREEN; style = Paint.Style.FILL }
        canvas.drawLine(x1.toFloat(), 0f, x1.toFloat(), out.height.toFloat(), probe)
        canvas.drawLine(x2.toFloat(), 0f, x2.toFloat(), out.height.toFloat(), probe)
        if (y1 >= 0) canvas.drawCircle(x1.toFloat(), y1.toFloat(), 4f, hit)
        if (y2 >= 0) canvas.drawCircle(x2.toFloat(), y2.toFloat(), 4f, hit)
        return out
    }

    private fun boxOverlay(leveled: Bitmap, gold: Int, red: Int, up: Int, box: Rect): Bitmap {
        val out = leveled.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(out)
        val goldPaint = Paint().apply { color = Color.CYAN; strokeWidth = 2f }
        val redPaint = Paint().apply { color = Color.GREEN; strokeWidth = 2f }
        val upPaint = Paint().apply { color = Color.rgb(255, 140, 0); strokeWidth = 2f }
        val boxPaint = Paint().apply { color = Color.MAGENTA; style = Paint.Style.STROKE; strokeWidth = 3f }
        canvas.drawLine(gold.toFloat(), 0f, gold.toFloat(), out.height.toFloat(), goldPaint)
        canvas.drawLine(red.toFloat(), 0f, red.toFloat(), out.height.toFloat(), redPaint)
        canvas.drawLine(0f, up.toFloat(), out.width.toFloat(), up.toFloat(), upPaint)
        canvas.drawRect(box, boxPaint)
        return out
    }

    /**
     * Rightward red-scan visualization (step 4): mirrors [goldSearchOverlay] — the de-rotated crop with
     * the scanned bubble row as a horizontal line, a tick at the search start [startX] and (if found) at
     * [red], plus the magnified scanned pixel row below.
     */
    private fun rightScanOverlay(leveled: Bitmap, pixels: IntArray, row: Int, startX: Int, red: Int): Bitmap {
        val w = leveled.width
        val stripH = 20
        val out = Bitmap.createBitmap(w, leveled.height + stripH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(Color.rgb(24, 24, 24))
        canvas.drawBitmap(leveled, 0f, 0f, null)

        val bar = Paint()
        for (x in 0 until w) {
            bar.color = pixels[row * w + x]
            canvas.drawRect(x.toFloat(), leveled.height.toFloat(), x + 1f, (leveled.height + stripH).toFloat(), bar)
        }
        val line = Paint().apply { color = Color.YELLOW; strokeWidth = 2f }
        canvas.drawLine(0f, row.toFloat(), w.toFloat(), row.toFloat(), line)
        val startPaint = Paint().apply { color = Color.WHITE; strokeWidth = 2f }
        canvas.drawLine(startX.toFloat(), 0f, startX.toFloat(), out.height.toFloat(), startPaint)
        if (red in 0 until w) {
            val found = Paint().apply { color = Color.rgb(230, 30, 30); strokeWidth = 3f }
            canvas.drawLine(red.toFloat(), 0f, red.toFloat(), out.height.toFloat(), found)
        }
        return out
    }

    /**
     * Vertical scan plot (rows on the y axis, 0..1 on the x axis), analogous to the OCR
     * [ScanImageOps.titleProfilePlot]: the single red-dominance metric `2r/(g+b)` that
     * [BannerGeometry.isSaturatedRed] gates on — scaled by [RED_PLOT_MAX] to fit the axis — with a
     * dashed cutoff at [redMinRatio], a left bar marking the rows accepted as red, and horizontal
     * [edges] (row → colour) for the detected top edge / banner run. Column [col] is the scan line.
     */
    private fun redColumnPlot(pixels: IntArray, w: Int, h: Int, col: Int, edges: List<Pair<Int, Int>>, ratio: Float): Bitmap {
        val plotW = PLOT_W
        val out = Bitmap.createBitmap(plotW, h.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(Color.rgb(248, 248, 248))

        val dash = Paint().apply { style = Paint.Style.STROKE; strokeWidth = 1.5f; pathEffect = android.graphics.DashPathEffect(floatArrayOf(6f, 6f), 0f) }
        dash.color = Color.argb(160, 200, 0, 0)
        val cutX = (ratio / RED_PLOT_MAX).coerceIn(0f, 1f) * plotW
        canvas.drawLine(cutX, 0f, cutX, h.toFloat(), dash)   // 2r/(g+b) gate

        val curve = Paint().apply { color = Color.rgb(210, 30, 30); strokeWidth = 2f; isAntiAlias = true }
        val redBar = Paint().apply { color = Color.rgb(210, 30, 30) }
        var prev = 0f
        for (y in 0 until h) {
            val p = pixels[y * w + col]
            val v = (BannerGeometry.redDominance(p) / RED_PLOT_MAX).coerceIn(0f, 1f)
            if (y > 0) canvas.drawLine(prev * plotW, (y - 1).toFloat(), v * plotW, y.toFloat(), curve)
            prev = v
            if (BannerGeometry.isSaturatedRed(p, ratio)) canvas.drawRect(0f, y.toFloat(), 6f, y + 1f, redBar)
        }
        val edgePaint = Paint().apply { strokeWidth = 2f }
        edges.forEach { (y, color) ->
            edgePaint.color = color
            canvas.drawLine(0f, y.toFloat(), plotW.toFloat(), y.toFloat(), edgePaint)
        }
        return out
    }

    /**
     * Horizontal scan plot (columns on the x axis, 0..1 on the y axis) for the gold-ring gate along
     * one row: the single blue-to-green ratio `b/g` (curve) that [BannerGeometry.isGoldenYellow] gates
     * on, with a dashed horizontal cutoff at [goldMaxBlueGreenRatio], a bottom bar marking gold columns
     * (`b/g` below the cutoff and r ≥ [goldMinRed]), and a vertical line at [bubbleRight] (−1 = none).
     */
    private fun goldRowPlot(pixels: IntArray, w: Int, row: Int, bubbleRight: Int): Bitmap {
        val plotH = PLOT_H
        val out = Bitmap.createBitmap(w.coerceAtLeast(1), plotH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(Color.rgb(248, 248, 248))
        fun yOf(v: Float) = plotH - v.coerceIn(0f, 1f) * plotH

        val dash = Paint().apply { style = Paint.Style.STROKE; strokeWidth = 1.5f; pathEffect = android.graphics.DashPathEffect(floatArrayOf(6f, 6f), 0f) }
        dash.color = Color.argb(170, 180, 130, 0)
        canvas.drawLine(0f, yOf(goldMaxBlueGreenRatio), w.toFloat(), yOf(goldMaxBlueGreenRatio), dash)  // b/g cutoff

        val ratioPaint = Paint().apply { color = Color.rgb(180, 130, 0); strokeWidth = 2f; isAntiAlias = true }
        val goldBar = Paint().apply { color = Color.rgb(210, 170, 40) }
        var prev = 0f
        for (x in 0 until w) {
            val p = pixels[row * w + x]
            val v = (BannerGeometry.blue(p).toFloat() / BannerGeometry.green(p).coerceAtLeast(1)).coerceIn(0f, 1f)
            if (x > 0) canvas.drawLine((x - 1).toFloat(), yOf(prev), x.toFloat(), yOf(v), ratioPaint)
            prev = v
            if (BannerGeometry.isGoldenYellow(p, goldMinRed, goldMaxBlueGreenRatio)) {
                canvas.drawRect(x.toFloat(), plotH - 6f, x + 1f, plotH.toFloat(), goldBar)
            }
        }
        if (bubbleRight in 0 until w) {
            val marker = Paint().apply { color = Color.rgb(210, 30, 30); strokeWidth = 2f }
            canvas.drawLine(bubbleRight.toFloat(), 0f, bubbleRight.toFloat(), plotH.toFloat(), marker)
        }
        return out
    }

    /**
     * Horizontal scan plot (columns on the x axis, 0..1 on the y axis) for the right ribbon-end gate
     * along one row: the red-dominance metric `2r/(g+b)` (curve, scaled by [RED_PLOT_MAX]) that
     * [BannerGeometry.isSaturatedRed] gates on, with a dashed horizontal cutoff at [ratio], a bottom
     * bar marking red columns, a vertical line at the scan start [startX] (white) and at the found
     * [red] end (−1 = none).
     */
    private fun redRowPlot(pixels: IntArray, w: Int, row: Int, startX: Int, red: Int, ratio: Float): Bitmap {
        val plotH = PLOT_H
        val out = Bitmap.createBitmap(w.coerceAtLeast(1), plotH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(Color.rgb(248, 248, 248))
        fun yOf(v: Float) = plotH - v.coerceIn(0f, 1f) * plotH

        val dash = Paint().apply { style = Paint.Style.STROKE; strokeWidth = 1.5f; pathEffect = android.graphics.DashPathEffect(floatArrayOf(6f, 6f), 0f) }
        dash.color = Color.argb(160, 200, 0, 0)
        canvas.drawLine(0f, yOf(ratio / RED_PLOT_MAX), w.toFloat(), yOf(ratio / RED_PLOT_MAX), dash)  // 2r/(g+b) cutoff

        val curve = Paint().apply { color = Color.rgb(210, 30, 30); strokeWidth = 2f; isAntiAlias = true }
        val redBar = Paint().apply { color = Color.rgb(210, 30, 30) }
        var prev = 0f
        for (x in 0 until w) {
            val p = pixels[row * w + x]
            val v = (BannerGeometry.redDominance(p) / RED_PLOT_MAX).coerceIn(0f, 1f)
            if (x > 0) canvas.drawLine((x - 1).toFloat(), yOf(prev), x.toFloat(), yOf(v), curve)
            prev = v
            if (BannerGeometry.isSaturatedRed(p, ratio)) canvas.drawRect(x.toFloat(), plotH - 6f, x + 1f, plotH.toFloat(), redBar)
        }
        val startPaint = Paint().apply { color = Color.rgb(90, 90, 90); strokeWidth = 2f }
        if (startX in 0 until w) canvas.drawLine(startX.toFloat(), 0f, startX.toFloat(), plotH.toFloat(), startPaint)
        if (red in 0 until w) {
            val marker = Paint().apply { color = Color.rgb(30, 160, 30); strokeWidth = 2f }
            canvas.drawLine(red.toFloat(), 0f, red.toFloat(), plotH.toFloat(), marker)
        }
        return out
    }

    /**
     * Tunable factors/thresholds — live-edited from the Scanner-Test screen, defaults used in
     * production. Kept as `var`s in a companion so both the scanner and the debug sliders share them.
     */
    companion object {
        private const val PLOT_W = 320   // vertical-scan plot width (x = ratio 0..1)
        private const val PLOT_H = 180   // horizontal-scan plot height (y = ratio 0..1)
        private const val RED_PLOT_MAX = 6f  // display scale for 2r/(g+b) on the 0..1 red plot
        // ARGB literals (not Color.rgb) so the companion init stays Android-free for unit tests.
        private val EDGE_HIT = 0xFF00A000.toInt()           // detected top-edge marker (green)
        private const val RED_TOP_PLOT_NOTE =
            "Kurve = 2r/(g+b) (÷$RED_PLOT_MAX skaliert) · gestrichelt = Rot-Gate oben · " +
                "linker Balken = als Rot erkannt · grüne Linie = erkannte Oberkante"
        private const val RED_RIGHT_PLOT_NOTE =
            "Kurve = 2r/(g+b) (÷$RED_PLOT_MAX skaliert) · gestrichelt = Rot-Gate rechts · " +
                "unterer Balken = als Rot erkannt · grau = Startlinie, grün = Bandende red"
        private const val GOLD_PLOT_NOTE =
            "Kurve = b/g · gestrichelt = Gold-Gate (b/g ≤ x) · unterer Balken = als Gold erkannt · " +
                "rote Linie = gold"

        /** Canonical Standardbox size (grayscale). Templates are stored/scaled to exactly this. */
        const val TEMPLATE_W = 320
        const val TEMPLATE_H = 64

        // --- Fixed scan constants. ---
        const val EDGE_MIN_RUN = 3          // px; a stable red/gold run (rejects JPEG speckle)
        const val RIGHT_GAP_PX = 3          // px; non-red gap tolerated by the right ribbon-end scan (speckle only)
        const val ROTATE_EPS = 0.5          // deg; below this, skip de-rotation
        const val MAX_ROTATE = 25.0         // deg; rotation cap

        // --- Tunable knobs (Scanner-Test sliders). ---
        /** Raw-crop margin (right + bottom), fraction of blob height. */
        var RAW_MARGIN = 0.08f
        /** Raw-crop **top** margin, fraction of blob height — kept small so stray red above the banner isn't caught as the top edge. */
        var RAW_MARGIN_TOP = 0.04f
        /** Left probe line, fraction of raw-crop width (right of the bubble). */
        var PROBE_X1 = 0.45f
        /** Right probe line, fraction of raw-crop width (top-edge / rotation measurement). */
        var PROBE_X2 = 0.70f
        /** Start of the right ribbon-end scan, fraction of raw-crop width — far right so mid-ribbon glare gaps are skipped. */
        var RIGHT_SCAN_START = 0.85f
        /** Left grow of the raw crop, in blob heights (past the non-red number bubble). */
        var BUBBLE_PAD = 1.3f
        /** Gold-search / right-scan line: how far below the top edge to scan, in blob heights (red strip above the letters). */
        var DESCEND = 0.11f
        /** Standardbox bottom: `up + BOX_FACTOR_A·width` (width = red − gold). Template geometry once fixed. */
        var BOX_FACTOR_A = 0.21f
        /** Standardbox left: `gold − BOX_FACTOR_B·width`. Template geometry once fixed. */
        var BOX_FACTOR_B = 0.27f
        /** Red gate for the **top** edge (probe lines + banner-top): min red-dominance `2r/(g+b)`. */
        var redMinRatioTop = 1.37f
        /** Red gate for the **right** ribbon-end scan: min red-dominance `2r/(g+b)`. */
        var redMinRatioRight = 1.41f
        /** Gold-ring classifier: minimum red channel. */
        var goldMinRed = 132
        /** Gold-ring classifier: blue-to-green ratio `b/g` must be ≤ this (gold = low blue). */
        var goldMaxBlueGreenRatio = 0.85f

        /** Sanity floor for accepting a template match — pure garbage rejection, not an ambiguity gate. */
        var MIN_TEMPLATE_SCORE = 0.5f

        /** Scales [bitmap] to canonical size and returns its [GrayImage] + a viewable gray bitmap. */
        fun grayOf(bitmap: Bitmap): Pair<GrayImage, Bitmap> {
            val scaled = if (bitmap.width == TEMPLATE_W && bitmap.height == TEMPLATE_H) bitmap
            else Bitmap.createScaledBitmap(bitmap, TEMPLATE_W, TEMPLATE_H, true)
            val px = IntArray(TEMPLATE_W * TEMPLATE_H)
            scaled.getPixels(px, 0, TEMPLATE_W, 0, 0, TEMPLATE_W, TEMPLATE_H)
            val f = FloatArray(px.size)
            val gray = IntArray(px.size)
            for (i in px.indices) {
                val p = px[i]
                val lum = (0.299 * Color.red(p) + 0.587 * Color.green(p) + 0.114 * Color.blue(p))
                    .toInt().coerceIn(0, 255)
                f[i] = lum / 255f
                gray[i] = Color.rgb(lum, lum, lum)
            }
            val bmp = Bitmap.createBitmap(TEMPLATE_W, TEMPLATE_H, Bitmap.Config.ARGB_8888)
            bmp.setPixels(gray, 0, TEMPLATE_W, 0, 0, TEMPLATE_W, TEMPLATE_H)
            return GrayImage(f, TEMPLATE_W, TEMPLATE_H) to bmp
        }
    }
}
