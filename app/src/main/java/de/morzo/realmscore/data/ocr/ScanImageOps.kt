package de.morzo.realmscore.data.ocr

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect

/**
 * Pure image operations shared by both flavours' scanners (Phase 26): rotation, cropping, the
 * red-banner title tightening, white-text binarization and Otsu thresholding. No OCR engine here.
 */
object ScanImageOps {

    const val NAME_REGION_FRACTION = 0.22  // top/bottom share of a card height for the fallback band
    const val TARGET_NAME_HEIGHT = 64       // upscale small crops to ~this many px tall for OCR
    const val TITLE_TARGET_HEIGHT = 110     // banners tighten to the title line, so upscale larger

    // --- Debug-tunable knobs (set live from the Scanner-Test screen; defaults used in production). ---
    /** White-text cut as a fraction of the crop's bright level (higher = thinner text, lower = fatter). */
    var whiteTextBrightFraction = 0.65f
    /** Padding kept *above* the title band, as a fraction of the band height (negative = crop inward). */
    var titlePadTopFraction = -0.15f
    /** Padding kept *below* the title band, as a fraction of the band height (negative = crop inward). */
    var titlePadBottomFraction = 0.20f
    /** A *border* row (plain red ribbon framing the title) needs more red than this. */
    var titleBorderRed = 0.70f
    /** A *border* row also needs less white than this — together with [titleBorderRed] = pure red ribbon. */
    var titleBorderWhite = 0.08f
    /** A *text* row needs more white than this — confirms the lettering between the two red borders. */
    var titleTextWhite = 0.20f
    /** A *side-trim* column counts as solid ribbon above this red fraction — sets the left/right cut. */
    var titleSideRed = 0.60f

    fun rotate(src: Bitmap, degrees: Int): Bitmap {
        val normalized = ((degrees % 360) + 360) % 360
        if (normalized == 0) return src
        val matrix = Matrix().apply { postRotate(normalized.toFloat()) }
        return Bitmap.createBitmap(src, 0, 0, src.width, src.height, matrix, true)
    }

    fun cropRect(bitmap: Bitmap, rect: Rect): Bitmap {
        val left = rect.left.coerceIn(0, bitmap.width - 1)
        val top = rect.top.coerceIn(0, bitmap.height - 1)
        val width = rect.width().coerceIn(1, bitmap.width - left)
        val height = rect.height().coerceIn(1, bitmap.height - top)
        return Bitmap.createBitmap(bitmap, left, top, width, height)
    }

    /** Crops the top (or bottom) name band of a card box, clamped to the bitmap. */
    fun cropNameBand(bitmap: Bitmap, rect: Rect, fromBottom: Boolean): Bitmap {
        val bandHeight = (rect.height() * NAME_REGION_FRACTION).toInt().coerceAtLeast(1)
        val rawTop = if (fromBottom) rect.bottom - bandHeight else rect.top
        val top = rawTop.coerceIn(0, bitmap.height - 1)
        val left = rect.left.coerceIn(0, bitmap.width - 1)
        val width = rect.width().coerceIn(1, bitmap.width - left)
        val height = bandHeight.coerceIn(1, bitmap.height - top)
        return Bitmap.createBitmap(bitmap, left, top, width, height)
    }

    /** Bilinear upscale to [targetHeight] (no-op if already tall enough). */
    fun upscale(src: Bitmap, targetHeight: Int): Bitmap {
        if (src.height >= targetHeight) return src
        val scale = targetHeight.toFloat() / src.height
        return Bitmap.createScaledBitmap(src, (src.width * scale).toInt().coerceAtLeast(1), targetHeight, true)
    }

    /**
     * Within a banner crop, isolates the **white‑on‑red title line** by finding the two plain‑red ribbon
     * borders that frame the lettering — robust on Fantasy Realms cards where the padded crop leaks a
     * white card edge / pale background **above** the ribbon and warm illustration **below** the title.
     * Top‑down scan of the per‑row red/white fractions:
     *  1. **Top border** = first row that is plain red ribbon: red > [titleBorderRed] *and* white <
     *     [titleBorderWhite]. The white card edge above (white, little red) is skipped.
     *  2. **Text row** = first row after that with white > [titleTextWhite] — confirms the lettering has
     *     begun (guards against a second border row sitting directly next to the first).
     *  3. **Bottom border** = the next plain‑red row after the text (or, failing that, the end of the
     *     text run). Below the title the ribbon is plain red again, so this lands right under the text.
     *  4. Crop [topBorder..bottomBorder] ± padding ([titlePadTopFraction] / [titlePadBottomFraction];
     *     negative values bite inward into the border).
     * White uses the per‑pixel minimum channel with the same percentile cut as [binarizeWhite] (so the
     * [whiteTextBrightFraction] knob tunes detection and binarization alike); red uses [isSaturatedRed].
     */
    fun tightenToTitleText(src: Bitmap): Bitmap {
        if (src.height < 8) return src
        val band = analyzeTitleRows(src)
        if (band.topBorder < 0 || band.bottomBorder < 0) return src

        val titleH = band.bottomBorder - band.topBorder + 1
        val padTop = (titleH * titlePadTopFraction).toInt()       // negative = crop inward from the top
        val padBottom = (titleH * titlePadBottomFraction).toInt() // negative = crop inward from the bottom
        val top = (band.topBorder - padTop).coerceIn(0, src.height - 1)
        val bottom = (band.bottomBorder + padBottom).coerceIn(top, src.height - 1)
        val bandH = (bottom - top + 1).coerceAtLeast(1)
        return if (bandH >= src.height) src else Bitmap.createBitmap(src, 0, top, src.width, bandH)
    }

    /**
     * After the vertical [tightenToTitleText], trims the **left and right** edges of the title band to
     * the first fully‑red column from each side (column red fraction > [titleSideRed]). On Fantasy Realms
     * cards this drops the number disc / coloured suit edge on the left and the ribbon tail on the right
     * (none of which are solid red), leaving just the red ribbon span that carries the white title — so
     * OCR isn't fed the number or decoration. Falls back to the input if no solid‑red column is found.
     */
    fun tightenToTitleSides(src: Bitmap): Bitmap {
        if (src.width < 8) return src
        val w = src.width
        val h = src.height
        val pixels = IntArray(w * h)
        src.getPixels(pixels, 0, w, 0, 0, w, h)
        val gate = h * titleSideRed
        val redCol = IntArray(w)
        for (x in 0 until w) {
            var red = 0
            for (y in 0 until h) if (isSaturatedRed(pixels[y * w + x])) red++
            redCol[x] = red
        }
        val left = (0 until w).firstOrNull { redCol[it] > gate } ?: return src
        val right = (w - 1 downTo 0).firstOrNull { redCol[it] > gate } ?: return src
        if (right - left < 4) return src   // degenerate span — keep the full band
        return Bitmap.createBitmap(src, left, 0, right - left + 1, h)
    }

    /** Per-row white/red profile of a banner crop and the title edges derived from it (see below). */
    class TitleBand(
        /** Per-row white (text) pixel fraction, 0..1, top to bottom. */
        val whiteFrac: FloatArray,
        /** Per-row red (banner) pixel fraction, 0..1, top to bottom. */
        val redFrac: FloatArray,
        /** First "red border" row (red-heavy, white-empty) — the title band's top edge, or -1. */
        val topBorder: Int,
        /** First text row (white-heavy) after [topBorder], confirming a title is present, or -1. */
        val textRow: Int,
        /** Border row after [textRow] (or the text run's end) — the title band's bottom edge, or -1. */
        val bottomBorder: Int,
    )

    /**
     * Top-down scan of a banner crop (shared by [tightenToTitleText] and [titleProfilePlot]): per row
     * the white (text) and red (banner) fractions, then the title edges via the two red borders that
     * frame the lettering — top border (red > [titleBorderRed], white < [titleBorderWhite]) → a text row
     * (white > [titleTextWhite]) → bottom border (the next such red row, or the text run's end).
     */
    private fun analyzeTitleRows(src: Bitmap): TitleBand {
        val w = src.width
        val h = src.height
        val pixels = IntArray(w * h)
        src.getPixels(pixels, 0, w, 0, 0, w, h)
        val minCh = IntArray(pixels.size)
        val histogram = IntArray(256)
        for (i in pixels.indices) {
            val m = minChannel(pixels[i])  // white text → high min; red/dark banner → low min
            minCh[i] = m
            histogram[m]++
        }
        val threshold = (brightLevel(histogram, pixels.size) * whiteTextBrightFraction).toInt().coerceIn(110, 235)

        val whiteFrac = FloatArray(h)
        val redFrac = FloatArray(h)
        for (y in 0 until h) {
            var white = 0
            var red = 0
            val base = y * w
            for (x in 0 until w) {
                if (minCh[base + x] >= threshold) white++
                if (isSaturatedRed(pixels[base + x])) red++
            }
            whiteFrac[y] = white.toFloat() / w
            redFrac[y] = red.toFloat() / w
        }

        // A "border" row is the plain red ribbon framing the title: lots of red, (almost) no white text.
        // Walk top→bottom: top border → a text row (white) confirms we're inside the title → the next
        // border is the bottom edge (or, if the lower border is cut off, the end of the text run).
        fun isBorder(y: Int) = redFrac[y] > titleBorderRed && whiteFrac[y] < titleBorderWhite
        val topBorder = (0 until h).firstOrNull { isBorder(it) } ?: -1
        val textRow = if (topBorder < 0) -1
        else (topBorder + 1 until h).firstOrNull { whiteFrac[it] > titleTextWhite } ?: -1
        val bottomBorder = if (textRow < 0) -1
        else (textRow + 1 until h).firstOrNull { isBorder(it) }
            ?: (textRow until h).lastOrNull { whiteFrac[it] > titleTextWhite } ?: -1

        return TitleBand(whiteFrac, redFrac, topBorder, textRow, bottomBorder)
    }

    /**
     * Debug chart of a banner crop's per-row profile (rows top→bottom on the y axis, fraction 0..1 on
     * the x axis): red curve = red fraction, blue curve = white fraction, dashed verticals = the three
     * gates ([titleBorderRed] red, [titleBorderWhite] white-min, [titleTextWhite] white-max), horizontal
     * lines = the detected borders (orange) and the confirming text row (green). Aligns row-for-row with
     * the crop image shown above it.
     */
    fun titleProfilePlot(src: Bitmap, plotWidth: Int = 360): Bitmap {
        val band = analyzeTitleRows(src)
        val h = band.whiteFrac.size.coerceAtLeast(1)
        val out = Bitmap.createBitmap(plotWidth, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(Color.rgb(248, 248, 248))

        // Gate thresholds (dashed verticals): red border, white-min (border), white-max (text).
        val dash = Paint().apply { style = Paint.Style.STROKE; strokeWidth = 1.5f; pathEffect = DashPathEffect(floatArrayOf(6f, 6f), 0f) }
        dash.color = Color.argb(150, 200, 0, 0)
        canvas.drawLine(titleBorderRed * plotWidth, 0f, titleBorderRed * plotWidth, h.toFloat(), dash)
        dash.color = Color.argb(110, 30, 90, 220)
        canvas.drawLine(titleBorderWhite * plotWidth, 0f, titleBorderWhite * plotWidth, h.toFloat(), dash)
        dash.color = Color.argb(190, 30, 90, 220)
        canvas.drawLine(titleTextWhite * plotWidth, 0f, titleTextWhite * plotWidth, h.toFloat(), dash)

        // Profiles (red = red fraction, blue = white fraction).
        val redPaint = Paint().apply { color = Color.rgb(220, 30, 30); strokeWidth = 2f; isAntiAlias = true }
        val whitePaint = Paint().apply { color = Color.rgb(30, 90, 220); strokeWidth = 2f; isAntiAlias = true }
        for (y in 1 until h) {
            canvas.drawLine(band.redFrac[y - 1] * plotWidth, (y - 1).toFloat(), band.redFrac[y] * plotWidth, y.toFloat(), redPaint)
            canvas.drawLine(band.whiteFrac[y - 1] * plotWidth, (y - 1).toFloat(), band.whiteFrac[y] * plotWidth, y.toFloat(), whitePaint)
        }

        // Detected edges (horizontal lines): the two red borders (orange), the confirming text row (green).
        val edge = Paint().apply { strokeWidth = 2f }
        edge.color = Color.rgb(255, 140, 0)
        if (band.topBorder >= 0) canvas.drawLine(0f, band.topBorder.toFloat(), plotWidth.toFloat(), band.topBorder.toFloat(), edge)
        if (band.bottomBorder >= 0) canvas.drawLine(0f, band.bottomBorder.toFloat(), plotWidth.toFloat(), band.bottomBorder.toFloat(), edge)
        if (band.textRow >= 0) {
            edge.color = Color.rgb(0, 160, 0)
            canvas.drawLine(0f, band.textRow.toFloat(), plotWidth.toFloat(), band.textRow.toFloat(), edge)
        }
        return out
    }

    /** 98th-percentile of a min-channel histogram — a glare-robust "white" level (ignores hot specks). */
    private fun brightLevel(histogram: IntArray, total: Int): Int {
        var cumulative = 0
        val cutoff = (total * 0.98).toInt()
        for (v in 0..255) {
            cumulative += histogram[v]
            if (cumulative >= cutoff) return v
        }
        return 255
    }

    /**
     * Binarizes **white title text** to black-on-white, keyed on the per-pixel **minimum channel**
     * (white text = high in every channel → high min; red/dark banner → low min). The threshold is a
     * fraction of the crop's *bright* level (98th percentile of min-channel), **not** Otsu: once the
     * crop is tightened to the red ribbon it is mostly red with only a little white text, and Otsu then
     * lumps the red in with the text and blacks the whole band out. A brightness-relative high cut
     * isolates just the white text and still tracks lighting (polarity is fixed, so no frame flicker).
     */
    fun binarizeWhite(src: Bitmap, targetHeight: Int): Bitmap {
        val scaled = upscale(src, targetHeight)
        val w = scaled.width
        val h = scaled.height
        val pixels = IntArray(w * h)
        scaled.getPixels(pixels, 0, w, 0, 0, w, h)
        val minCh = IntArray(pixels.size)
        val histogram = IntArray(256)
        for (i in pixels.indices) {
            val m = minChannel(pixels[i])
            minCh[i] = m
            histogram[m]++
        }
        val threshold = (brightLevel(histogram, pixels.size) * whiteTextBrightFraction).toInt().coerceIn(110, 235)
        val out = IntArray(pixels.size) { if (minCh[it] >= threshold) Color.BLACK else Color.WHITE }
        return Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also {
            it.setPixels(out, 0, w, 0, 0, w, h)
        }
    }

    /** Upscale (if small), grayscale, Otsu-binarize to black text on white (majority-based polarity). */
    fun preprocessForOcr(src: Bitmap, targetHeight: Int = TARGET_NAME_HEIGHT): Bitmap {
        val scaled = upscale(src, targetHeight)
        val w = scaled.width
        val h = scaled.height
        val pixels = IntArray(w * h)
        scaled.getPixels(pixels, 0, w, 0, 0, w, h)
        val gray = IntArray(pixels.size)
        val histogram = IntArray(256)
        for (i in pixels.indices) {
            val p = pixels[i]
            val lum = (0.299 * Color.red(p) + 0.587 * Color.green(p) + 0.114 * Color.blue(p))
                .toInt().coerceIn(0, 255)
            gray[i] = lum
            histogram[lum]++
        }
        val threshold = otsuThreshold(histogram, pixels.size)
        var darkCount = 0
        for (g in gray) if (g < threshold) darkCount++
        val invert = darkCount > pixels.size / 2

        val out = IntArray(pixels.size)
        for (i in gray.indices) {
            val isDark = gray[i] < threshold
            val isText = if (invert) !isDark else isDark
            out[i] = if (isText) Color.BLACK else Color.WHITE
        }
        return Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also {
            it.setPixels(out, 0, w, 0, 0, w, h)
        }
    }

    fun minChannel(p: Int): Int = minOf(Color.red(p), Color.green(p), Color.blue(p))

    /**
     * Saturated red of the title banner, judged **proportionally** so it is brightness-independent:
     * dark / shadowed / tilted banners still count (the difference scales with the red level), while
     * the less-saturated brown of a wooden table does not. Used for the whole-photo blob detection in
     * the Tesseract flavour. (The ML Kit flavour uses [redFraction]'s simpler test, applied only to
     * text-line boxes that are already on a card.)
     */
    fun isSaturatedRed(p: Int): Boolean {
        val r = Color.red(p)
        val g = Color.green(p)
        val b = Color.blue(p)
        if (r < 60) return false
        return (r - g) >= 0.4f * r && (r - b) >= 0.3f * r
    }

    /** Fraction of [box] in [bitmap] that is banner-red — used to tell a white-on-red *title* line from
     *  dark-on-white *rule* text (Phase 26, ML Kit flavour). */
    fun redFraction(bitmap: Bitmap, box: Rect): Float {
        val left = box.left.coerceIn(0, bitmap.width - 1)
        val top = box.top.coerceIn(0, bitmap.height - 1)
        val right = box.right.coerceIn(left + 1, bitmap.width)
        val bottom = box.bottom.coerceIn(top + 1, bitmap.height)
        val w = right - left
        val h = bottom - top
        if (w < 2 || h < 2) return 0f
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, left, top, w, h)
        var red = 0
        for (p in pixels) {
            val r = Color.red(p)
            val g = Color.green(p)
            val b = Color.blue(p)
            // Relative red: red clearly dominant, at *any* brightness — catches dark/shadowed/tilted
            // banners, while still excluding white/cream (r≈g) and blue/purple (b ≥ r). Simpler than
            // isSaturatedRed because it only ever sees text-line boxes already on a card (no table wood).
            if (r - g >= 24 && r - b >= 16) red++
        }
        return red.toFloat() / pixels.size
    }

    /** Otsu's method: the value threshold that maximises between-class variance. */
    fun otsuThreshold(histogram: IntArray, total: Int): Int {
        var sumAll = 0.0
        for (t in 0..255) sumAll += t.toDouble() * histogram[t]
        var sumBackground = 0.0
        var weightBackground = 0
        var maxVariance = 0.0
        var threshold = 127
        for (t in 0..255) {
            weightBackground += histogram[t]
            if (weightBackground == 0) continue
            val weightForeground = total - weightBackground
            if (weightForeground == 0) break
            sumBackground += t.toDouble() * histogram[t]
            val meanBackground = sumBackground / weightBackground
            val meanForeground = (sumAll - sumBackground) / weightForeground
            val between = weightBackground.toDouble() * weightForeground *
                (meanBackground - meanForeground) * (meanBackground - meanForeground)
            if (between > maxVariance) {
                maxVariance = between
                threshold = t
            }
        }
        return threshold
    }
}
