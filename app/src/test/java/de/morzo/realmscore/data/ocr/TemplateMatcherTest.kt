package de.morzo.realmscore.data.ocr

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-JVM tests for the NCC matcher (Phase 29): identity, ±2 px tolerance, brightness/contrast
 * invariance, garbage rejection, and disambiguation between two similar templates.
 */
class TemplateMatcherTest {

    private val w = 20
    private val h = 12

    /** A deterministic, non-flat pattern (needed for NCC to be defined). */
    private fun pattern(seed: Int): FloatArray {
        val r = Random(seed)
        return FloatArray(w * h) { i ->
            val x = i % w
            val y = i / w
            // Smooth gradient + a little structure so ±px shifts stay well-correlated.
            (0.3f * x / w + 0.3f * y / h + 0.2f * ((x / 3 + y / 2) % 2) + 0.1f * r.nextFloat())
        }
    }

    private fun gray(pixels: FloatArray) = GrayImage(pixels, w, h)

    @Test
    fun `identity scores near one`() {
        val p = pattern(1)
        val matcher = TemplateMatcher(listOf(BannerTemplate("a", gray(p))))
        val score = matcher.scoredCandidates(gray(p.copyOf())).first().second
        assertEquals(1.0f, score, 1e-3f)
    }

    @Test
    fun `two px shift is recovered by the search window`() {
        val p = pattern(2)
        // Shift the query right by 2 / down by 1 (edge-clamped); the ±2 window should realign it.
        val shifted = FloatArray(w * h)
        for (y in 0 until h) for (x in 0 until w) {
            val sx = (x - 2).coerceIn(0, w - 1)
            val sy = (y - 1).coerceIn(0, h - 1)
            shifted[y * w + x] = p[sy * w + sx]
        }
        val matcher = TemplateMatcher(listOf(BannerTemplate("a", gray(p))))
        val score = matcher.scoredCandidates(gray(shifted)).first().second
        assertTrue("shifted score $score should be high", score > 0.9f)
    }

    @Test
    fun `ncc is invariant to global brightness and contrast`() {
        val p = pattern(3)
        val transformed = FloatArray(p.size) { 0.4f * p[it] + 0.25f } // a*x + b, a>0
        val matcher = TemplateMatcher(listOf(BannerTemplate("a", gray(p))))
        val score = matcher.scoredCandidates(gray(transformed)).first().second
        assertEquals(1.0f, score, 1e-3f)
    }

    @Test
    fun `random noise stays below the match floor`() {
        val p = pattern(4)
        val r = Random(99)
        val noise = FloatArray(p.size) { r.nextFloat() }
        val matcher = TemplateMatcher(listOf(BannerTemplate("a", gray(p))))
        val score = matcher.scoredCandidates(gray(noise)).first().second
        assertTrue("noise score $score should be below the floor", score < BannerNormalizer.MIN_TEMPLATE_SCORE)
    }

    @Test
    fun `correct template wins between two similar ones`() {
        val a = pattern(5)
        // b is a with one quadrant overwritten — similar but distinguishable.
        val b = a.copyOf()
        for (y in 0 until h / 2) for (x in 0 until w / 2) b[y * w + x] = 1f - b[y * w + x]
        val matcher = TemplateMatcher(
            listOf(BannerTemplate("a", gray(a)), BannerTemplate("b", gray(b))),
        )
        val ranked = matcher.scoredCandidates(gray(a.copyOf()))
        assertEquals("a", ranked.first().first)
        assertTrue(ranked.first().second > ranked[1].second)
    }
}
