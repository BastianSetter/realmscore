package de.morzo.realmscore.ui.scan

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import de.morzo.realmscore.data.ocr.CardScanner
import de.morzo.realmscore.domain.model.CardDefinition
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CameraScanUiState(
    val isRecognizing: Boolean = false,
    /** Non-null once a photo has been recognised; consumed by the screen to hand cards back. */
    val result: List<CardDefinition>? = null,
    /** Recognised cards dropped because already used in another entry (reported alongside [result]). */
    val skippedConflicts: Int = 0,
    /** A photo produced no usable cards; the user can retake or switch to manual. */
    val recognitionFailed: Boolean = false,
)

/**
 * Holds the recognition state for the camera scan (Phase 26). Camera binding/capture lives in the
 * composable; this VM only runs OCR off the main thread and surfaces the outcome.
 */
class CameraScanViewModel(
    private val scanner: CardScanner,
    private val maxCards: Int,
    private val excludedKeys: Set<String>,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CameraScanUiState())
    val uiState: StateFlow<CameraScanUiState> = _uiState.asStateFlow()

    fun recognize(bitmap: Bitmap, rotationDegrees: Int) {
        if (_uiState.value.isRecognizing) return
        _uiState.update { it.copy(isRecognizing = true, recognitionFailed = false) }
        viewModelScope.launch {
            val result = scanner.recognizeMultiple(bitmap, rotationDegrees, maxCards, excludedKeys)
            _uiState.update {
                if (result.cards.isEmpty()) {
                    it.copy(isRecognizing = false, recognitionFailed = true)
                } else {
                    it.copy(
                        isRecognizing = false,
                        result = result.cards,
                        skippedConflicts = result.skippedConflicts,
                    )
                }
            }
        }
    }

    fun dismissFailure() {
        _uiState.update { it.copy(recognitionFailed = false) }
    }

    class Factory(
        private val scanner: CardScanner,
        private val maxCards: Int,
        private val excludedKeys: Set<String>,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            CameraScanViewModel(scanner, maxCards, excludedKeys) as T
    }
}
