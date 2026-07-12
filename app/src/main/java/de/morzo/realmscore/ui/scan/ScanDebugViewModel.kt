package de.morzo.realmscore.ui.scan

import android.content.Context
import android.graphics.Bitmap
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import de.morzo.realmscore.data.cards.CardLookup
import de.morzo.realmscore.data.ocr.BannerTemplateStore
import de.morzo.realmscore.data.ocr.CardScanner
import de.morzo.realmscore.data.ocr.ScanReport
import de.morzo.realmscore.data.ocr.TemplateCardScanner
import de.morzo.realmscore.domain.model.CardDefinition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Which pipeline the scan-debug screen currently runs: OCR (flavor) or bitmap templates (Phase 29). */
enum class ScanMode { OCR, BITMAP }

/** Template inventory for the bitmap path: how many of the card set have a stored template. */
data class TemplateInventory(
    val available: Int = 0,
    val total: Int = 0,
    /** Cards still missing a template (for the miss list). */
    val missing: List<CardDefinition> = emptyList(),
)

data class ScanDebugUiState(
    val mode: ScanMode = ScanMode.OCR,
    val isProcessing: Boolean = false,
    val report: ScanReport? = null,
    val sourcePreview: Bitmap? = null,
    val inventory: TemplateInventory = TemplateInventory(),
)

/** One-shot results of a template save/export, surfaced to the UI as a snackbar or share-sheet. */
sealed interface ScanDebugEvent {
    data class Message(val text: String) : ScanDebugEvent
    data class ShareZip(val uri: android.net.Uri) : ScanDebugEvent
}

/**
 * Backs the developer scan-debug screen (Phase 26 + 29): runs the detailed recognizer on a picked
 * image via either the OCR or the bitmap-template scanner (A/B), and drives template creation
 * (save-as-template per region, inventory, export) for the bitmap path.
 */
class ScanDebugViewModel(
    private val ocrScanner: CardScanner,
    private val templateScanner: TemplateCardScanner,
    private val templateStore: BannerTemplateStore,
    private val cardLookup: CardLookup,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ScanDebugUiState())
    val uiState: StateFlow<ScanDebugUiState> = _uiState.asStateFlow()

    private val eventChannel = Channel<ScanDebugEvent>(Channel.BUFFERED)
    val events = eventChannel.receiveAsFlow()

    // Remember the last input so a mode switch / tuning-slider change re-runs on the same image.
    private var last: Triple<Bitmap, Int, Int>? = null

    init {
        refreshInventory()
    }

    val allCards: List<CardDefinition> get() = cardLookup.getAll()
    fun searchCards(query: String): List<CardDefinition> = cardLookup.search(query)

    private fun scannerFor(mode: ScanMode): CardScanner =
        if (mode == ScanMode.BITMAP) templateScanner else ocrScanner

    fun analyze(bitmap: Bitmap, rotationDegrees: Int, maxCards: Int) {
        last = Triple(bitmap, rotationDegrees, maxCards)
        _uiState.update { it.copy(isProcessing = true, report = null, sourcePreview = bitmap) }
        viewModelScope.launch {
            val report = scannerFor(_uiState.value.mode).recognizeDetailed(bitmap, rotationDegrees, maxCards)
            _uiState.update { it.copy(isProcessing = false, report = report) }
        }
    }

    /** Switch the A/B pipeline and re-run the last image so both paths can be compared directly. */
    fun setMode(mode: ScanMode) {
        if (_uiState.value.mode == mode) return
        _uiState.update { it.copy(mode = mode) }
        reanalyze()
    }

    /** Re-run on the last image (after a tuning knob changed). No-op if none yet. */
    fun reanalyze() {
        last?.let { analyze(it.first, it.second, it.third) }
    }

    /** Re-run the last image with a new card count (changes the 1- vs 2-column fan layout). */
    fun reanalyze(maxCards: Int) {
        last?.let { analyze(it.first, it.second, maxCards) }
    }

    /**
     * Batch-save several regions' Standardboxes as filesDir templates in one go. Reloads the scanner
     * and refreshes the inventory **once** at the end and deliberately does **not** re-analyze the
     * current photo — cutting the template set is faster when each save doesn't trigger a full re-scan;
     * the user just moves on to the next photo.
     */
    fun saveTemplates(entries: List<Pair<String, Bitmap>>) {
        if (entries.isEmpty()) return
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                entries.forEach { (cardKey, box) -> templateStore.save(cardKey, box) }
                templateScanner.reload()
            }
            refreshInventory()
            eventChannel.send(ScanDebugEvent.Message("${entries.size} Vorlage(n) gespeichert"))
        }
    }

    /** Zip the filesDir template overrides and emit a share-sheet event, or a "nothing to export" note. */
    fun exportTemplates(context: Context) {
        val appContext = context.applicationContext
        viewModelScope.launch {
            val uri = withContext(Dispatchers.IO) {
                templateStore.exportOverridesZip()?.let { file ->
                    FileProvider.getUriForFile(appContext, "${appContext.packageName}.fileprovider", file)
                }
            }
            eventChannel.send(
                if (uri != null) ScanDebugEvent.ShareZip(uri)
                else ScanDebugEvent.Message("Keine exportierbaren Vorlagen (nichts gespeichert)"),
            )
        }
    }

    private fun refreshInventory() {
        viewModelScope.launch {
            val inventory = withContext(Dispatchers.IO) {
                val available = templateStore.availableKeys()
                val all = cardLookup.getAll()
                TemplateInventory(
                    available = available.size,
                    total = all.size,
                    missing = all.filter { it.key !in available },
                )
            }
            _uiState.update { it.copy(inventory = inventory) }
        }
    }

    class Factory(
        private val ocrScanner: CardScanner,
        private val templateScanner: TemplateCardScanner,
        private val templateStore: BannerTemplateStore,
        private val cardLookup: CardLookup,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            ScanDebugViewModel(ocrScanner, templateScanner, templateStore, cardLookup) as T
    }
}
