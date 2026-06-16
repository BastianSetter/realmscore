package de.morzo.realmscore.data.ocr

import android.graphics.Bitmap
import android.graphics.Rect

/**
 * Finds the red name banners on Fantasy Realms cards (Phase 26). Every card's title sits on a
 * saturated red ribbon near its top, so red blobs are strong, position-independent anchors that both
 * locate cards and crop the name directly. Only **wide-and-short** red blobs qualify, so red artwork
 * (a dress, armour, a flame) is rejected while the banner ribbon passes.
 */
object RedBannerDetector {

    private const val DETECT_WIDTH = 720
    private const val MIN_AREA_FRACTION = 0.004  // ignore tiny red specks
    private const val MIN_ASPECT = 1.4           // banners are wider than tall; lenient enough for tilt,
                                                 // still rejects tall vertical suit bands (aspect < 1)
    private const val PAD_FRACTION = 0.1         // small vertical grow; the recognizer tightens to text

    fun detect(bitmap: Bitmap, maxCards: Int): List<Rect> {
        if (maxCards <= 0) return emptyList()
        val workW = DETECT_WIDTH.coerceAtMost(bitmap.width)
        val workH = (bitmap.height * (workW.toFloat() / bitmap.width)).toInt().coerceAtLeast(1)
        val small = Bitmap.createScaledBitmap(bitmap, workW, workH, true)

        val pixels = IntArray(workW * workH)
        small.getPixels(pixels, 0, workW, 0, 0, workW, workH)
        val mask = BooleanArray(pixels.size) { ScanImageOps.isSaturatedRed(pixels[it]) }

        val frame = workW.toLong() * workH
        val boxes = ConnectedComponents.boxes(mask, workW, workH)
            .filter { qualifies(it, frame) }
            .sortedByDescending { it.width().toLong() * it.height() }
            .take(maxCards)
            // Reading order: top rows first, then left-to-right within a row band.
            .sortedWith(
                compareBy(
                    { ((it.top + it.bottom) / 2) / (workH / 3).coerceAtLeast(1) },
                    { it.left },
                ),
            )

        val inv = bitmap.width.toFloat() / workW
        return boxes.map { it.scaledAndPadded(inv, bitmap.width, bitmap.height) }
    }

    private fun qualifies(box: Rect, frame: Long): Boolean {
        val area = box.width().toLong() * box.height()
        if (area < frame * MIN_AREA_FRACTION) return false
        if (box.width() < 24 || box.height() < 8) return false
        return box.width().toFloat() / box.height() >= MIN_ASPECT
    }

    private fun Rect.scaledAndPadded(factor: Float, maxW: Int, maxH: Int): Rect {
        val padY = height() * PAD_FRACTION * factor
        val l = (left * factor).toInt().coerceIn(0, maxW - 1)
        val t = (top * factor - padY).toInt().coerceIn(0, maxH - 1)
        val r = (right * factor).toInt().coerceIn(l + 1, maxW)
        val b = (bottom * factor + padY).toInt().coerceIn(t + 1, maxH)
        return Rect(l, t, r, b)
    }
}
