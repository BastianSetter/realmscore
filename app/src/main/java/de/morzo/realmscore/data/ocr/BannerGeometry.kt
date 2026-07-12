package de.morzo.realmscore.data.ocr

import kotlin.math.atan2

/**
 * Pure geometry for the Phase-29 banner normalization: pixel classifiers and the scan steps
 * (probe-line top edge, angle from two points, gold-ring edge, vertical red run). Everything here
 * works on a **packed ARGB `IntArray`** (row-major, `y * width + x`) instead of an Android `Bitmap`,
 * so the geometry is unit-testable on the JVM without Robolectric. [BannerNormalizer] owns the
 * Android side (crop/rotate/scale) and the tunable thresholds; it feeds those thresholds in here.
 *
 * Channel extraction is done with bit ops (not `android.graphics.Color`) to keep this file
 * Android-free; the red rule mirrors [ScanImageOps.isSaturatedRed] exactly.
 */
object BannerGeometry {

    fun red(argb: Int): Int = (argb ushr 16) and 0xFF
    fun green(argb: Int): Int = (argb ushr 8) and 0xFF
    fun blue(argb: Int): Int = argb and 0xFF

    /** Packs 8-bit channels into an opaque ARGB int (test helper / template rendering). */
    fun argb(r: Int, g: Int, b: Int): Int =
        (0xFF shl 24) or ((r and 0xFF) shl 16) or ((g and 0xFF) shl 8) or (b and 0xFF)

    /** Default red-dominance cutoff for [isSaturatedRed] when no tuned value is supplied. */
    const val DEFAULT_RED_MIN_RATIO = 2.5f

    /**
     * Red-dominance metric `2r / (g + b)`: high for the saturated red ribbon (~11), ~2 for the gold
     * ring, ~1 for white/grey. Brightness-independent (scales out) so shadowed/tilted red still reads.
     */
    fun redDominance(argb: Int): Float =
        2f * red(argb) / (green(argb) + blue(argb)).coerceAtLeast(1)

    /**
     * Saturated red of the ribbon: bright enough (r ≥ 60) and [redDominance] ≥ [minRatio]. The single
     * `2r/(g+b)` ratio replaces the former two independent `(r−g)/r`, `(r−b)/r` gates.
     */
    fun isSaturatedRed(argb: Int, minRatio: Float = DEFAULT_RED_MIN_RATIO): Boolean {
        if (red(argb) < 60) return false
        return redDominance(argb) >= minRatio
    }

    /**
     * The golden/amber ring of the number bubble, judged by the single **blue-to-green ratio**
     * `b / g`: gold is red+green high with low blue → small `b/g` (~0.2), while the red ribbon and
     * white both sit near `b/g ≈ 1`. Gold when bright enough (r ≥ [minRed]) and `b/g ≤`
     * [maxBlueGreenRatio]. Replaces the former two independent green/blue-vs-red gates.
     */
    fun isGoldenYellow(argb: Int, minRed: Int, maxBlueGreenRatio: Float): Boolean {
        if (red(argb) < minRed) return false
        return blue(argb).toFloat() / green(argb).coerceAtLeast(1) <= maxBlueGreenRatio
    }

    /**
     * Scans column [x] top→bottom for the first stable red run of at least [minRun] pixels and
     * returns the y of the run's **start** — the white→red transition onto the banner's top border.
     * Returns -1 if no such run exists (probe line missed the banner). Short runs are ignored as
     * JPEG/compression noise.
     */
    fun firstRedRunTop(
        pixels: IntArray,
        width: Int,
        height: Int,
        x: Int,
        minRun: Int,
        redMinRatio: Float = DEFAULT_RED_MIN_RATIO,
    ): Int {
        if (x < 0 || x >= width) return -1
        var run = 0
        for (y in 0 until height) {
            if (isSaturatedRed(pixels[y * width + x], redMinRatio)) {
                run++
                if (run >= minRun) return y - run + 1
            } else {
                run = 0
            }
        }
        return -1
    }

    /** Angle (degrees) of the line through the two top-edge probe points; 0 = horizontal. */
    fun angleDegrees(x1: Int, y1: Int, x2: Int, y2: Int): Double =
        Math.toDegrees(atan2((y2 - y1).toDouble(), (x2 - x1).toDouble()))

    /**
     * From [startX] in row [row], walks **left** until a stable gold run (≥ [minRun]) of the number
     * bubble's ring appears, and returns the x of that run's **right** edge = `bubbleRight`. Returns
     * -1 if no gold ring is found left of [startX] (bubble occluded / gold threshold off).
     */
    fun goldRightEdge(
        pixels: IntArray,
        width: Int,
        height: Int,
        row: Int,
        startX: Int,
        minRun: Int,
        goldMinRed: Int,
        goldMaxBlueGreenRatio: Float,
    ): Int {
        if (row < 0 || row >= height) return -1
        val from = startX.coerceIn(0, width - 1)
        var run = 0
        var x = from
        while (x >= 0) {
            val gold = isGoldenYellow(pixels[row * width + x], goldMinRed, goldMaxBlueGreenRatio)
            if (gold) {
                run++
                if (run >= minRun) return x + run - 1   // rightmost pixel of the run (we came from the right)
            } else {
                run = 0
            }
            x--
        }
        return -1
    }

    /**
     * The contiguous vertical red run in column [x] that contains [seedY] (which is expected to sit
     * inside the banner). Grows up and down from the seed while pixels stay saturated-red. Because the
     * column is chosen text-free (between ring and first letter), white letters never break the run.
     * Returns `bannerTop..bannerBottom`, or null if [seedY] itself isn't red.
     */
    fun verticalRedRun(
        pixels: IntArray,
        width: Int,
        height: Int,
        x: Int,
        seedY: Int,
        topRatio: Float = DEFAULT_RED_MIN_RATIO,
        bottomRatio: Float = DEFAULT_RED_MIN_RATIO,
    ): IntRange? {
        if (x < 0 || x >= width || seedY < 0 || seedY >= height) return null
        // Seed sits mid-banner (deeply red): the lenient of the two gates lets it pass.
        val seedRatio = minOf(topRatio, bottomRatio)
        if (!isSaturatedRed(pixels[seedY * width + x], seedRatio)) return null
        // Grow up to the banner's top edge (top gate) and down to its bottom edge (bottom gate).
        var top = seedY
        while (top - 1 >= 0 && isSaturatedRed(pixels[(top - 1) * width + x], topRatio)) top--
        var bottom = seedY
        while (bottom + 1 < height && isSaturatedRed(pixels[(bottom + 1) * width + x], bottomRatio)) bottom++
        return top..bottom
    }

    /**
     * From [startX] in row [row], walks **right** along the red ribbon and returns the x of its right
     * end — the last saturated-red pixel before a non-red gap wider than [maxGap] px. The scan row is
     * chosen to sit in the ribbon's top red strip *above* the white title letters, so the run is solid
     * red; the small [maxGap] only bridges JPEG speckle, not letters. Returns -1 if no red is seen
     * at/after [startX] (probe column missed the ribbon).
     */
    fun rightRedEnd(
        pixels: IntArray,
        width: Int,
        height: Int,
        row: Int,
        startX: Int,
        maxGap: Int,
        redMinRatio: Float = DEFAULT_RED_MIN_RATIO,
    ): Int {
        if (row < 0 || row >= height) return -1
        val from = startX.coerceIn(0, width - 1)
        var lastRed = -1
        var gap = 0
        for (x in from until width) {
            if (isSaturatedRed(pixels[row * width + x], redMinRatio)) {
                lastRed = x
                gap = 0
            } else if (lastRed >= 0) {
                gap++
                if (gap > maxGap) break
            }
        }
        return lastRed
    }
}
