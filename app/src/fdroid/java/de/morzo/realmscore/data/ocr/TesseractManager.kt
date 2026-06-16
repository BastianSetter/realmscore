package de.morzo.realmscore.data.ocr

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.googlecode.tesseract.android.TessBaseAPI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Owns the single [TessBaseAPI] instance (Phase 26, `fdroid` flavour). Trained data ships in
 * `assets/tessdata/` and is copied to `filesDir/tessdata/` on first use; Tesseract is then
 * initialised with both German and English so either physical card edition is readable.
 *
 * [TessBaseAPI] is not thread-safe and holds native state between `setImage`/`getUTF8Text`, so every
 * recognition runs serialized through [mutex] on [Dispatchers.IO].
 */
class TesseractManager(private val context: Context) {

    private val mutex = Mutex()
    @Volatile private var api: TessBaseAPI? = null
    @Volatile private var initFailed = false

    suspend fun warmUp() {
        runCatching { getOrInit() }
    }

    private suspend fun getOrInit(): TessBaseAPI? = mutex.withLock {
        api?.let { return it }
        if (initFailed) return null
        withContext(Dispatchers.IO) {
            runCatching {
                copyTrainedDataIfNeeded()
                TessBaseAPI().apply {
                    if (!init(context.filesDir.absolutePath, LANGUAGES)) {
                        recycle()
                        throw IllegalStateException("Tesseract init failed for '$LANGUAGES'")
                    }
                    pageSegMode = TessBaseAPI.PageSegMode.PSM_SINGLE_LINE
                    // NB: no tessedit_char_whitelist — it degrades / empties LSTM output. Stray digits
                    // and symbols are instead dropped when normalizing for the fuzzy match.
                }.also { api = it }
            }.getOrElse { e ->
                Log.e(TAG, "Tesseract initialisation failed", e)
                initFailed = true
                null
            }
        }
    }

    suspend fun recognizeLine(bitmap: Bitmap): RecognizedLine? {
        val engine = getOrInit() ?: return null
        return mutex.withLock {
            withContext(Dispatchers.IO) {
                runCatching {
                    engine.setImage(bitmap)
                    val text = engine.getUTF8Text()?.trim().orEmpty()
                    val confidence = engine.meanConfidence()
                    engine.clear()
                    RecognizedLine(text, confidence)
                }.getOrNull()
            }
        }
    }

    private fun copyTrainedDataIfNeeded() {
        val tessDir = File(context.filesDir, "tessdata").apply { mkdirs() }
        for (file in TRAINED_DATA_FILES) {
            val target = File(tessDir, file)
            if (target.exists() && target.length() > 0L) continue
            context.assets.open("tessdata/$file").use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
        }
    }

    data class RecognizedLine(val text: String, val confidence: Int)

    private companion object {
        const val TAG = "TesseractManager"
        const val LANGUAGES = "deu+eng"
        val TRAINED_DATA_FILES = listOf("deu.traineddata", "eng.traineddata")
    }
}
