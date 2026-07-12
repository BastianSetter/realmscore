package de.morzo.realmscore.data.ocr

import android.graphics.Bitmap

/**
 * The single switch point (Phase 29): each call reads `bitmapMatchingEnabled` and routes to the
 * flavor-independent [template] scanner (bitmap NCC) when on, or the flavor's OCR [ocr] scanner when
 * off (the unchanged default). Both paths use the fan layout, so `usesFanLayout` is stable and the
 * camera screen never needs to know which engine ran. Both engines are warmed up.
 */
class DelegatingCardScanner(
    private val ocr: CardScanner,
    private val template: CardScanner,
    private val bitmapMatchingEnabled: suspend () -> Boolean,
) : CardScanner {

    // Both paths fan; the camera guide/hint is identical, so this can be a stable constant.
    override val usesFanLayout: Boolean = true

    override suspend fun warmUp() {
        ocr.warmUp()
        template.warmUp()
    }

    private suspend fun active(): CardScanner = if (bitmapMatchingEnabled()) template else ocr

    override suspend fun recognizeMultiple(
        source: Bitmap,
        rotationDegrees: Int,
        maxCards: Int,
        excludedKeys: Set<String>,
    ): ScanResult = active().recognizeMultiple(source, rotationDegrees, maxCards, excludedKeys)

    override suspend fun recognizeDetailed(
        source: Bitmap,
        rotationDegrees: Int,
        maxCards: Int,
    ): ScanReport = active().recognizeDetailed(source, rotationDegrees, maxCards)
}
