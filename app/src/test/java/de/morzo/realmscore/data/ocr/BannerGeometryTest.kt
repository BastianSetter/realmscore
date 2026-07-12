package de.morzo.realmscore.data.ocr

import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.tan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-JVM tests for the banner geometry (Phase 29): pixel classifiers plus the scan steps on
 * synthetic banners (white field, red ribbon of known tilt, golden bubble ring). Angle to ±1°,
 * bubbleRight/bannerTop/height to ±2 px. No Android / Robolectric.
 */
class BannerGeometryTest {

    private val white = BannerGeometry.argb(240, 240, 240)
    private val red = BannerGeometry.argb(220, 20, 20)
    private val gold = BannerGeometry.argb(210, 170, 40)

    @Test
    fun `classifiers separate red gold and white`() {
        // Red 2r/(g+b)=11, gold≈2, white≈1 → gate 2.5 keeps red, drops gold+white.
        assertTrue(BannerGeometry.isSaturatedRed(red))
        assertTrue(!BannerGeometry.isSaturatedRed(white))
        assertTrue(!BannerGeometry.isSaturatedRed(gold))
        // Gold b/g≈0.24, red b/g=1, white b/g=1 → gate 0.6 keeps gold, drops red+white.
        assertTrue(BannerGeometry.isGoldenYellow(gold, 120, 0.6f))
        assertTrue(!BannerGeometry.isGoldenYellow(red, 120, 0.6f))
        assertTrue(!BannerGeometry.isGoldenYellow(white, 120, 0.6f))
    }

    /** Horizontal banner: red rows [top..bottom], a gold disc near the left, else white. */
    private fun horizontalBanner(w: Int, h: Int, top: Int, bottom: Int, cx: Int, cy: Int, radius: Int): IntArray {
        val px = IntArray(w * h) { white }
        for (y in top..bottom) for (x in 0 until w) px[y * w + x] = red
        for (y in 0 until h) for (x in 0 until w) {
            val dx = x - cx
            val dy = y - cy
            if (dx * dx + dy * dy <= radius * radius) px[y * w + x] = gold
        }
        return px
    }

    @Test
    fun `first red run finds the banner top edge`() {
        val w = 60
        val h = 80
        val px = horizontalBanner(w, h, top = 30, bottom = 60, cx = 10, cy = 45, radius = 6)
        // Probe a column clear of the gold disc.
        val y = BannerGeometry.firstRedRunTop(px, w, h, x = 40, minRun = 3)
        assertEquals(30, y)
    }

    @Test
    fun `gold right edge and vertical red run recover bubble and height`() {
        val w = 120
        val h = 90
        val top = 30
        val bottom = 69
        val cx = 30
        val cy = 50
        val radius = 12
        val px = horizontalBanner(w, h, top, bottom, cx, cy, radius)

        val bubbleRight = BannerGeometry.goldRightEdge(
            px, w, h, row = cy, startX = 80, minRun = 3,
            goldMinRed = 120, goldMaxBlueGreenRatio = 0.6f,
        )
        assertTrue("bubbleRight $bubbleRight ~ ${cx + radius}", abs(bubbleRight - (cx + radius)) <= 2)

        // A text-free column right of the ring is pure red top..bottom.
        val run = BannerGeometry.verticalRedRun(px, w, h, x = bubbleRight + 6, seedY = cy)
        assertNotNull(run)
        assertTrue(abs(run!!.first - top) <= 2)
        assertTrue(abs(run.last - bottom) <= 2)
    }

    @Test
    fun `right red end finds the ribbon right edge and bridges speckle`() {
        val w = 120
        val h = 40
        val ribbonRight = 90
        val scanRow = 8
        val px = IntArray(w * h) { white }
        for (y in 5..20) for (x in 0..ribbonRight) px[y * w + x] = red
        // A single non-red speckle on the scan row inside the ribbon must be bridged, not end the scan.
        px[scanRow * w + 50] = white

        val end = BannerGeometry.rightRedEnd(px, w, h, row = scanRow, startX = 30, maxGap = 3)
        assertEquals(ribbonRight, end)

        // Column past the ribbon has no red → -1.
        val none = BannerGeometry.rightRedEnd(px, w, h, row = 0, startX = 30, maxGap = 3)
        assertEquals(-1, none)
    }

    @Test
    fun `angle from two probe points matches a ten degree tilt`() {
        val w = 400
        val h = 150
        val m = tan(Math.toRadians(10.0)) // slope of the top edge
        val bandHeight = 40
        val px = IntArray(w * h) { white }
        for (x in 0 until w) {
            val top = (20 + m * x).roundToInt()
            for (y in top until (top + bandHeight)) {
                if (y in 0 until h) px[y * w + x] = red
            }
        }
        val x1 = (w * 0.30f).toInt()
        val x2 = (w * 0.80f).toInt()
        val y1 = BannerGeometry.firstRedRunTop(px, w, h, x1, 3)
        val y2 = BannerGeometry.firstRedRunTop(px, w, h, x2, 3)
        val angle = BannerGeometry.angleDegrees(x1, y1, x2, y2)
        assertTrue("angle $angle ~ 10°", abs(angle - 10.0) < 1.0)
    }
}
