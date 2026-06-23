package de.morzo.realmscore.data.ocr

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect

/**
 * Finds card-shaped regions in a photo without any OpenCV/ML dependency (Phase 26, F-Droid-safe).
 *
 * Heuristic, foreground-segmentation approach:
 *  1. downscale for speed,
 *  2. estimate the table/background luminance from the frame border,
 *  3. mark pixels that differ enough from the background as "card",
 *  4. group them into connected components and keep the card-shaped ones (size + aspect ratio),
 *  5. map the boxes back to full-resolution coordinates.
 *
 * This is intentionally lenient (favouring recall): a spurious box just yields a card the user
 * corrects in stage 2, and a missed card is filled manually. The thresholds below are the obvious
 * place to tune against real photos — see the Phase 26 plan's "what I need from you" (sample photos).
 */
object CardRectangleDetector {

    private const val DETECT_WIDTH = 720
    private const val FG_DELTA = 38              // luminance distance from background to count as card
    private const val MIN_AREA_FRACTION = 0.015  // a card occupies at least this share of the frame
    private const val MAX_AREA_FRACTION = 0.7    // …and at most this (guards against the whole table)
    private const val MIN_ASPECT = 0.35          // w/h band — lenient, orientation varies
    private const val MAX_ASPECT = 1.7

    fun detect(bitmap: Bitmap, maxCards: Int): List<Rect> {
        if (maxCards <= 0) return emptyList()
        val scale = DETECT_WIDTH.toFloat() / bitmap.width
        val workW = DETECT_WIDTH.coerceAtMost(bitmap.width)
        val workH = (bitmap.height * (workW.toFloat() / bitmap.width)).toInt().coerceAtLeast(1)
        val small = Bitmap.createScaledBitmap(bitmap, workW, workH, true)

        val mask = foregroundMask(small, workW, workH)
        val boxes = ConnectedComponents.boxes(mask, workW, workH)
            .filter { qualifies(it, workW, workH) }
            .sortedByDescending { it.width().toLong() * it.height() }
            .take(maxCards)
            // Reading order: roughly top rows first, then left-to-right within a row band.
            .sortedWith(compareBy({ it.centerY() / (workH / 3).coerceAtLeast(1) }, { it.left }))

        val inv = bitmap.width.toFloat() / workW
        return boxes.map { it.scaledTo(inv, bitmap.width, bitmap.height) }
    }

    private fun foregroundMask(bmp: Bitmap, w: Int, h: Int): BooleanArray {
        val pixels = IntArray(w * h)
        bmp.getPixels(pixels, 0, w, 0, 0, w, h)
        val lum = IntArray(pixels.size) { luminance(pixels[it]) }

        // Background = average luminance along the four borders (the table around the cards).
        var sum = 0L
        var count = 0
        for (x in 0 until w) {
            sum += lum[x]; sum += lum[(h - 1) * w + x]; count += 2
        }
        for (y in 0 until h) {
            sum += lum[y * w]; sum += lum[y * w + (w - 1)]; count += 2
        }
        val bg = (sum / count).toInt()

        return BooleanArray(pixels.size) { kotlin.math.abs(lum[it] - bg) > FG_DELTA }
    }

    private fun qualifies(box: Rect, w: Int, h: Int): Boolean {
        val area = box.width().toLong() * box.height()
        val frame = w.toLong() * h
        if (area < frame * MIN_AREA_FRACTION) return false
        if (area > frame * MAX_AREA_FRACTION) return false
        if (box.width() < 12 || box.height() < 12) return false
        val aspect = box.width().toFloat() / box.height()
        return aspect in MIN_ASPECT..MAX_ASPECT
    }

    private fun luminance(p: Int): Int =
        (0.299 * Color.red(p) + 0.587 * Color.green(p) + 0.114 * Color.blue(p)).toInt().coerceIn(0, 255)

    private fun Rect.scaledTo(factor: Float, maxW: Int, maxH: Int): Rect {
        val l = (left * factor).toInt().coerceIn(0, maxW - 1)
        val t = (top * factor).toInt().coerceIn(0, maxH - 1)
        val r = (right * factor).toInt().coerceIn(l + 1, maxW)
        val b = (bottom * factor).toInt().coerceIn(t + 1, maxH)
        return Rect(l, t, r, b)
    }
}
