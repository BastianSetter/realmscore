package de.morzo.realmscore.data.ocr

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Rect

/**
 * Pure image operations shared by both flavours' scanners (Phase 26): rotation, cropping, the
 * red-banner title tightening, white-text binarization and Otsu thresholding. No OCR engine here.
 */
object ScanImageOps {

    const val NAME_REGION_FRACTION = 0.22  // top/bottom share of a card height for the fallback band
    const val TARGET_NAME_HEIGHT = 64       // upscale small crops to ~this many px tall for OCR
    const val TITLE_TARGET_HEIGHT = 110     // banners tighten to the title line, so upscale larger

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
     * Crops a banner down to its brightest horizontal band — the white title text — so the value
     * circle's row gap and any illustration the padded box leaked in fall away, and the title fills
     * the frame. Picks the *densest* contiguous band of bright rows (so a thin bright card border
     * above the title loses to the much denser title band). Brightness uses the per-pixel minimum
     * channel, so the red banner (low min) does not register as text.
     */
    fun tightenToTitleBand(src: Bitmap): Bitmap {
        val w = src.width
        val h = src.height
        if (h < 8) return src
        val pixels = IntArray(w * h)
        src.getPixels(pixels, 0, w, 0, 0, w, h)
        val value = IntArray(pixels.size)
        val histogram = IntArray(256)
        for (i in pixels.indices) {
            val m = minChannel(pixels[i])  // white text → high min; red/dark banner → low min
            value[i] = m
            histogram[m]++
        }
        val threshold = otsuThreshold(histogram, pixels.size)
        val rowMin = (w * 0.04).toInt().coerceAtLeast(1)
        val maxGap = (h * 0.05).toInt().coerceAtLeast(2)

        var bestStart = -1
        var bestEnd = -1
        var bestSum = -1L
        var curStart = -1
        var curEnd = -1
        var curSum = 0L
        var gap = 0
        for (y in 0 until h) {
            var bright = 0
            val base = y * w
            for (x in 0 until w) if (value[base + x] >= threshold) bright++
            if (bright >= rowMin) {
                if (curStart == -1) curStart = y
                curEnd = y
                curSum += bright
                gap = 0
            } else if (curStart != -1) {
                gap++
                if (gap > maxGap) {
                    if (curSum > bestSum) { bestSum = curSum; bestStart = curStart; bestEnd = curEnd }
                    curStart = -1; curEnd = -1; curSum = 0; gap = 0
                }
            }
        }
        if (curStart != -1 && curSum > bestSum) { bestStart = curStart; bestEnd = curEnd }
        if (bestStart == -1 || bestEnd < bestStart) return src

        val pad = ((bestEnd - bestStart + 1) * 0.3).toInt()
        val top = (bestStart - pad).coerceAtLeast(0)
        val bottom = (bestEnd + pad).coerceAtMost(h - 1)
        val bandH = (bottom - top + 1).coerceAtLeast(1)
        return if (bandH >= h) src else Bitmap.createBitmap(src, 0, top, w, bandH)
    }

    /**
     * Binarizes white title text to black-on-white, keyed on the per-pixel **minimum channel** so the
     * polarity is fixed (white text = high min; red/dark banner = low min). Unlike a luminance Otsu
     * with majority-based inversion, this never flips between frames. Upscales small crops first.
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
        val threshold = otsuThreshold(histogram, pixels.size)
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

    /** Saturated red of the title banner ribbon — high red, clearly above green and blue (excludes
     *  purple suit bands and brown table wood). Shared by the blob detector and the ML Kit title filter. */
    fun isBannerRed(p: Int): Boolean {
        val r = Color.red(p)
        val g = Color.green(p)
        val b = Color.blue(p)
        return r >= 110 && r - g >= 55 && r - b >= 45
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
            // banners (unlike the absolute isBannerRed used for blob detection), while still excluding
            // white/cream (r≈g) and blue/purple (b ≥ r).
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
