package de.morzo.realmscore.ui.tabs.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import de.morzo.realmscore.domain.repository.StatsRepository
import de.morzo.realmscore.domain.stats.PlayerStats
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PlayerStatsUiState(
    val isLoading: Boolean = true,
    val stats: PlayerStats? = null,
)

class PlayerStatsViewModel(
    private val profileId: String,
    private val statsRepository: StatsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PlayerStatsUiState())
    val uiState: StateFlow<PlayerStatsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val stats = statsRepository.getPlayerStats(profileId)
            _uiState.update { it.copy(isLoading = false, stats = stats) }
        }
    }

    class Factory(
        private val profileId: String,
        private val statsRepository: StatsRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return PlayerStatsViewModel(profileId, statsRepository) as T
        }
    }
}
