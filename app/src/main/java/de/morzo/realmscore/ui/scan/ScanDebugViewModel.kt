package de.morzo.realmscore.ui.scan

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import de.morzo.realmscore.data.ocr.CardScanner
import de.morzo.realmscore.data.ocr.ScanReport
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ScanDebugUiState(
    val isProcessing: Boolean = false,
    val report: ScanReport? = null,
    val sourcePreview: Bitmap? = null,
)

/** Backs the developer scan-debug screen (Phase 26): runs the detailed recognizer on a picked image. */
class ScanDebugViewModel(private val scanner: CardScanner) : ViewModel() {

    private val _uiState = MutableStateFlow(ScanDebugUiState())
    val uiState: StateFlow<ScanDebugUiState> = _uiState.asStateFlow()

    // Remember the last input so a tuning-slider change can re-run the pipeline on the same image.
    private var last: Triple<Bitmap, Int, Int>? = null

    fun analyze(bitmap: Bitmap, rotationDegrees: Int, maxCards: Int) {
        last = Triple(bitmap, rotationDegrees, maxCards)
        _uiState.update { it.copy(isProcessing = true, report = null, sourcePreview = bitmap) }
        viewModelScope.launch {
            val report = scanner.recognizeDetailed(bitmap, rotationDegrees, maxCards)
            _uiState.update { it.copy(isProcessing = false, report = report) }
        }
    }

    /** Re-run on the last image (after a tuning knob in [ScanImageOps] changed). No-op if none yet. */
    fun reanalyze() {
        last?.let { analyze(it.first, it.second, it.third) }
    }

    class Factory(private val scanner: CardScanner) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            ScanDebugViewModel(scanner) as T
    }
}
