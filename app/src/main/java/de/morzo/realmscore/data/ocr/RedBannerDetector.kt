package de.morzo.realmscore.data.ocr

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect

/**
 * Finds the red name banners on Fantasy Realms cards (Phase 26). Every card's title sits on a
 * saturated red ribbon near its top, so red blobs are strong, position-independent anchors that both
 * locate cards and crop the name directly. Only **wide-and-short** red blobs qualify, so red artwork
 * (a dress, armour, a flame) is rejected while the banner ribbon passes.
 */
/** Debug trace of the single-card red-banner detection (see [RedBannerDetector.detectBestTraced]). */
data class BannerTrace(
    /** Final full-resolution, scaled-and-padded banner rect — identical to [RedBannerDetector.detectBest]. */
    val banner: Rect?,
    val mask: Bitmap,
    val overlay: Bitmap,
    val candidateCount: Int,
)

/** Debug trace of the fanned (single-column) detection (see [RedBannerDetector.detectFannedTraced]). */
data class FanTrace(
    /** Chosen banners, top→bottom (reading order) — identical to [RedBannerDetector.detectFanned]. */
    val banners: List<Rect>,
    val mask: Bitmap,
    val overlay: Bitmap,
)

object RedBannerDetector {

    private const val DETECT_WIDTH = 720
    private const val MIN_AREA_FRACTION = 0.004  // ignore tiny red specks
    private const val MIN_ASPECT = 1.4           // banners are wider than tall; lenient enough for tilt,
                                                 // still rejects tall vertical suit bands (aspect < 1)
    private const val PAD_FRACTION = 0.1         // small vertical grow; the recognizer tightens to text
    private const val UPPER_BIAS = 0.5           // single-card: penalise distance-from-top so the title
                                                 // ribbon beats wider red artwork lower on the card

    /**
     * Single-column **fan** variant (Phase 26.2): the cards are laid in one overlapping stack so each
     * shows its own white top edge + red title ribbon (cards run white→red top to bottom). Every card
     * therefore contributes one red banner blob; this returns the largest [maxCards] of them ordered
     * **top→bottom** (reading order), each scaled+padded to full resolution. A pure top sort (not the
     * single-card "best" pick) is what keeps the regions aligned with the physical card order.
     */
    fun detectFanned(bitmap: Bitmap, maxCards: Int): List<Rect> {
        if (maxCards <= 0) return emptyList()
        val a = analyzeRed(bitmap)
        val inv = bitmap.width.toFloat() / a.workW
        return a.fanBanners(maxCards).map { it.scaledAndPadded(inv, bitmap.width, bitmap.height) }
    }

    /**
     * Same detection as [detectFanned], plus the red mask and an overlay with every qualifying
     * candidate (yellow) and the chosen banners (green, numbered top→bottom) for the scan-debug screen.
     * The [banners][FanTrace.banners] it returns are identical to [detectFanned].
     */
    fun detectFannedTraced(bitmap: Bitmap, maxCards: Int): FanTrace {
        val a = analyzeRed(bitmap)
        val picked = a.fanBanners(maxCards)
        val inv = bitmap.width.toFloat() / a.workW
        val banners = picked.map { it.scaledAndPadded(inv, bitmap.width, bitmap.height) }
        return FanTrace(banners, maskBitmap(a), fanOverlay(a, picked))
    }

    /** The largest [maxCards] qualifying banners, then ordered top→bottom (detect-resolution coords). */
    private fun RedAnalysis.fanBanners(maxCards: Int): List<Rect> =
        qualifying
            .sortedByDescending { it.width().toLong() * it.height() }
            .take(maxCards)
            .sortedBy { it.top }

    /**
     * Single-card variant (Phase 26.1): assumes **one upright card filling the frame** and returns its
     * one red title banner — or null if none stands out. Picks by a width-and-position score (not raw
     * area), so the wide title ribbon near the top wins over a chunkier red illustration further down.
     */
    fun detectBest(bitmap: Bitmap): Rect? {
        val a = analyzeRed(bitmap)
        val chosen = a.chosen ?: return null
        return chosen.scaledAndPadded(bitmap.width.toFloat() / a.workW, bitmap.width, bitmap.height)
    }

    /**
     * Same detection as [detectBest], plus the intermediate visualizations for the scan-debug screen:
     * the saturated-red mask and the downscaled photo with every qualifying banner candidate (yellow)
     * and the chosen one (green) drawn on it. The [banner] it returns is identical to [detectBest].
     */
    fun detectBestTraced(bitmap: Bitmap): BannerTrace {
        val a = analyzeRed(bitmap)
        val banner = a.chosen?.scaledAndPadded(bitmap.width.toFloat() / a.workW, bitmap.width, bitmap.height)
        return BannerTrace(banner, maskBitmap(a), overlayBitmap(a), a.qualifying.size)
    }

    /** Shared core: downscale, build the red mask, find qualifying banner blobs and pick the best. */
    private fun analyzeRed(bitmap: Bitmap): RedAnalysis {
        val workW = DETECT_WIDTH.coerceAtMost(bitmap.width)
        val workH = (bitmap.height * (workW.toFloat() / bitmap.width)).toInt().coerceAtLeast(1)
        val small = Bitmap.createScaledBitmap(bitmap, workW, workH, true)

        val pixels = IntArray(workW * workH)
        small.getPixels(pixels, 0, workW, 0, 0, workW, workH)
        val mask = BooleanArray(pixels.size) { ScanImageOps.isSaturatedRed(pixels[it]) }

        val frame = workW.toLong() * workH
        val qualifying = ConnectedComponents.boxes(mask, workW, workH).filter { qualifies(it, frame) }
        val chosen = qualifying.maxByOrNull { it.width() - UPPER_BIAS * ((it.top + it.bottom) / 2) }
        return RedAnalysis(small, mask, workW, workH, qualifying, chosen)
    }

    /** Red mask rendered as red-on-dark at detect resolution. */
    private fun maskBitmap(a: RedAnalysis): Bitmap {
        val out = IntArray(a.mask.size) { if (a.mask[it]) Color.rgb(255, 40, 40) else Color.rgb(24, 24, 24) }
        return Bitmap.createBitmap(a.workW, a.workH, Bitmap.Config.ARGB_8888)
            .also { it.setPixels(out, 0, a.workW, 0, 0, a.workW, a.workH) }
    }

    /** The downscaled photo with candidate boxes (yellow) and the chosen banner (green) drawn on it. */
    private fun overlayBitmap(a: RedAnalysis): Bitmap {
        val out = a.small.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(out)
        val candidate = Paint().apply { color = Color.YELLOW; style = Paint.Style.STROKE; strokeWidth = 2f }
        val chosenPaint = Paint().apply { color = Color.GREEN; style = Paint.Style.STROKE; strokeWidth = 4f }
        a.qualifying.forEach { canvas.drawRect(it, candidate) }
        a.chosen?.let { canvas.drawRect(it, chosenPaint) }
        return out
    }

    /** Fan overlay: every qualifying blob (yellow) + the chosen banners (green, numbered top→bottom). */
    private fun fanOverlay(a: RedAnalysis, picked: List<Rect>): Bitmap {
        val out = a.small.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(out)
        val candidate = Paint().apply { color = Color.YELLOW; style = Paint.Style.STROKE; strokeWidth = 2f }
        val chosenPaint = Paint().apply { color = Color.GREEN; style = Paint.Style.STROKE; strokeWidth = 4f }
        val label = Paint().apply { color = Color.GREEN; textSize = 22f; isAntiAlias = true }
        a.qualifying.forEach { canvas.drawRect(it, candidate) }
        picked.forEachIndexed { i, r ->
            canvas.drawRect(r, chosenPaint)
            canvas.drawText("${i + 1}", r.left.toFloat() + 2f, r.top.toFloat() + 20f, label)
        }
        return out
    }

    private class RedAnalysis(
        val small: Bitmap,
        val mask: BooleanArray,
        val workW: Int,
        val workH: Int,
        /** Banner blobs that passed [qualifies], in detect-resolution coords. */
        val qualifying: List<Rect>,
        /** The picked banner (detect-resolution coords), or null if none qualified. */
        val chosen: Rect?,
    )

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
