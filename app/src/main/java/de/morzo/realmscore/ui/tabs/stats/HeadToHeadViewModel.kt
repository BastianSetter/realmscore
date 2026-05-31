package de.morzo.realmscore.ui.tabs.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import de.morzo.realmscore.domain.repository.StatsRepository
import de.morzo.realmscore.domain.stats.HeadToHeadStats
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HeadToHeadUiState(
    val isLoading: Boolean = true,
    val stats: HeadToHeadStats? = null,
)

class HeadToHeadViewModel(
    private val profileIdA: String,
    private val profileIdB: String,
    private val statsRepository: StatsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HeadToHeadUiState())
    val uiState: StateFlow<HeadToHeadUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val stats = statsRepository.getHeadToHeadStats(profileIdA, profileIdB)
            _uiState.update { it.copy(isLoading = false, stats = stats) }
        }
    }

    class Factory(
        private val profileIdA: String,
        private val profileIdB: String,
        private val statsRepository: StatsRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return HeadToHeadViewModel(profileIdA, profileIdB, statsRepository) as T
        }
    }
}
