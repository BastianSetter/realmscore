package de.morzo.realmscore.data.ocr

import kotlin.math.sqrt

/**
 * A grayscale image as a flat luminance [pixels] array (row-major, `y * width + x`, values 0..1) plus
 * its [width]/[height]. Keeps the matcher free of Android types so it is unit-testable on the JVM.
 */
data class GrayImage(val pixels: FloatArray, val width: Int, val height: Int) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is GrayImage) return false
        return width == other.width && height == other.height && pixels.contentEquals(other.pixels)
    }

    override fun hashCode(): Int = (width * 31 + height) * 31 + pixels.contentHashCode()
}

/** One reference banner: the canonical grayscale Standardbox tied to a [cardKey]. */
data class BannerTemplate(val cardKey: String, val image: GrayImage)

/**
 * Matches a normalized banner Standardbox against every reference template by **normalized
 * cross-correlation (NCC)** with a small ±[searchRadius] px search window (Phase 29). Because the
 * deterministic anchor construction ([BannerNormalizer]) puts every crop in the same frame, no
 * sliding window or feature extraction is needed — only a mini window to absorb the residual anchor
 * error. NCC is invariant to global brightness/contrast (`a·x + b`), which is exactly what replaces
 * the OCR pipeline's brittle binarization thresholds.
 *
 * Pure Kotlin, no Android types. Templates are centered and unit-normalized once at construction, so
 * each comparison is a dot product; the ±2 px window is applied by shifting the *query* box with edge
 * clamping (25 full-frame vectors), keeping every vector the same length and comparable.
 */
class TemplateMatcher(
    templates: List<BannerTemplate>,
    private val searchRadius: Int = SEARCH_RADIUS,
) {

    private class Normalized(val cardKey: String, val vec: FloatArray)

    private val width: Int = templates.firstOrNull()?.image?.width ?: 0
    private val height: Int = templates.firstOrNull()?.image?.height ?: 0

    // Center + unit-normalize each template once. A flat (zero-variance) template can't be correlated
    // and is dropped — it would only ever score NaN.
    private val norms: List<Normalized> = templates.mapNotNull { t ->
        if (t.image.width != width || t.image.height != height) return@mapNotNull null
        centerNormalize(t.image.pixels)?.let { Normalized(t.cardKey, it) }
    }

    val templateCount: Int get() = norms.size

    /**
     * NCC score per template for [box], highest first. Score is the max NCC over the ±[searchRadius]
     * window, clamped to 0..1. Returns empty if the box size doesn't match the templates or the box
     * is flat.
     */
    fun scoredCandidates(box: GrayImage): List<Pair<String, Float>> {
        if (norms.isEmpty() || box.width != width || box.height != height) return emptyList()
        val shifts = shiftedNormals(box) // 25 centered/unit vectors, one per (dx, dy)
        if (shifts.isEmpty()) return emptyList()
        return norms
            .map { n ->
                var best = -1f
                for (s in shifts) {
                    val d = dot(n.vec, s)
                    if (d > best) best = d
                }
                n.cardKey to best.coerceIn(0f, 1f)
            }
            .sortedByDescending { it.second }
    }

    /** Builds the centered/unit-normalized query vector for every (dx, dy) in the search window. */
    private fun shiftedNormals(box: GrayImage): List<FloatArray> {
        val out = ArrayList<FloatArray>((2 * searchRadius + 1) * (2 * searchRadius + 1))
        val src = box.pixels
        for (dy in -searchRadius..searchRadius) {
            for (dx in -searchRadius..searchRadius) {
                val shifted = FloatArray(width * height)
                for (y in 0 until height) {
                    val sy = (y + dy).coerceIn(0, height - 1)
                    val srcRow = sy * width
                    val dstRow = y * width
                    for (x in 0 until width) {
                        val sx = (x + dx).coerceIn(0, width - 1)
                        shifted[dstRow + x] = src[srcRow + sx]
                    }
                }
                centerNormalize(shifted)?.let { out += it }
            }
        }
        return out
    }

    private companion object {
        const val SEARCH_RADIUS = 2

        /** Subtract the mean and divide by the L2 norm; null if the image is flat (norm 0). */
        fun centerNormalize(pixels: FloatArray): FloatArray? {
            var sum = 0.0
            for (p in pixels) sum += p
            val mean = (sum / pixels.size).toFloat()
            var sqSum = 0.0
            for (p in pixels) {
                val d = p - mean
                sqSum += d.toDouble() * d
            }
            val norm = sqrt(sqSum).toFloat()
            if (norm <= 1e-6f) return null
            val out = FloatArray(pixels.size)
            for (i in pixels.indices) out[i] = (pixels[i] - mean) / norm
            return out
        }

        fun dot(a: FloatArray, b: FloatArray): Float {
            var s = 0f
            for (i in a.indices) s += a[i] * b[i]
            return s
        }
    }
}
