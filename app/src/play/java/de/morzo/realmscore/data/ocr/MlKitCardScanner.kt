package de.morzo.realmscore.data.ocr

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import de.morzo.realmscore.domain.model.CardDefinition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/**
 * Play OCR engine (Phase 26): reads all text with **ML Kit** on-device recognition (bundled model —
 * offline), which handles tilted/overlapping cards far better than our blob detector. It then keeps
 * only the lines that sit **on a red banner** — card *titles* are white-on-red, whereas rule text is
 * dark-on-white — so the bonus/penalty text (which names *other* cards, e.g. "…mit Regensturm…") can
 * never produce a false match. Matching is the shared [CardNameMatcher].
 */
class MlKitCardScanner(private val matcher: CardNameMatcher) : CardScanner {

    override val usesFanLayout: Boolean = false

    private val recognizer: TextRecognizer =
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    override suspend fun warmUp() {
        // The bundled model loads lazily on first inference; nothing to pre-warm cheaply.
    }

    override suspend fun recognizeMultiple(
        source: Bitmap,
        rotationDegrees: Int,
        maxCards: Int,
        excludedKeys: Set<String>,
    ): ScanResult =
        analyze(source, rotationDegrees, maxCards).regions.distinctBestCards(excludedKeys)

    override suspend fun recognizeDetailed(
        source: Bitmap,
        rotationDegrees: Int,
        maxCards: Int,
    ): ScanReport = analyze(source, rotationDegrees, maxCards)

    private suspend fun analyze(
        source: Bitmap,
        rotationDegrees: Int,
        maxCards: Int,
    ): ScanReport = withContext(Dispatchers.Default) {
        val upright = ScanImageOps.rotate(source, rotationDegrees)
        val lines = processOrNull(upright)
            ?.textBlocks?.flatMap { it.lines }
            ?.filter { it.boundingBox != null }
            .orEmpty()

        // Title lines (white text on a red banner) vs everything else (dark rule text on white).
        val titleLines = lines.filter { ScanImageOps.redFraction(upright, it.boundingBox!!) >= RED_FRACTION }
        val onBanner = titleLines.isNotEmpty()
        val source0 = if (onBanner) titleLines else lines
        // A floor rejects red-but-not-a-title text (the Artefakt suit label scores ~0.4) and rule
        // lines, while real titles match ~0.9–1.0.
        val floor = if (onBanner) BANNER_FLOOR else WHOLE_IMAGE_FLOOR

        val bestPerCard = LinkedHashMap<String, ScanRegion>()
        for (line in source0) {
            val candidates = matcher.scoredCandidates(line.text).take(3)
            val best = candidates.firstOrNull() ?: continue
            if (best.score < floor) continue
            val existing = bestPerCard[best.card.key]
            if (existing == null || best.score > existing.topScore) {
                val crop = ScanImageOps.cropRect(upright, line.boundingBox!!)
                bestPerCard[best.card.key] =
                    ScanRegion(if (onBanner) "Banner (ML Kit)" else "Zeile (ML Kit)", crop, line.text, 0, candidates)
            }
        }
        val regions = bestPerCard.values.sortedByDescending { it.topScore }.take(maxCards)
        ScanReport(
            mode = if (onBanner) "Banner-Text (ML Kit)" else "Ganzes Bild (ML Kit)",
            regionCount = regions.size,
            regions = regions,
        )
    }

    private suspend fun processOrNull(bitmap: Bitmap): Text? =
        suspendCancellableCoroutine { cont ->
            val image = InputImage.fromBitmap(bitmap, 0)
            recognizer.process(image)
                .addOnSuccessListener { cont.resume(it) }
                .addOnFailureListener { cont.resume(null) }
        }

    private companion object {
        const val RED_FRACTION = 0.04f      // any red behind a line ⇒ it sits on a banner (rule text is ~0%)
        const val BANNER_FLOOR = 0.65f      // reject non-title red text (Artefakt suit label ~0.4, rule
                                            // lines with an embedded name ~0.6); real titles match ~0.9–1.0
        const val WHOLE_IMAGE_FLOOR = 0.65f // floor when no banner lines found (fallback only)
    }
}
