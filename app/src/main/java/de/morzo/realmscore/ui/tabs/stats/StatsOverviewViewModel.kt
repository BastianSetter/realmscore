package de.morzo.realmscore.ui.tabs.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import de.morzo.realmscore.domain.repository.GameRepository
import de.morzo.realmscore.domain.repository.StatsRepository
import de.morzo.realmscore.domain.stats.StatsOverview
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext

data class StatsOverviewUiState(
    val isLoading: Boolean = true,
    val overview: StatsOverview? = null,
) {
    val isEmpty: Boolean
        get() = overview != null && overview.totalClosedGames < 3
}

class StatsOverviewViewModel(
    private val statsRepository: StatsRepository,
    gameRepository: GameRepository,
) : ViewModel() {

    val uiState: StateFlow<StatsOverviewUiState> = gameRepository.observeClosedGames()
        .map { _ ->
            val overview = withContext(Dispatchers.Default) {
                statsRepository.getOverview()
            }
            StatsOverviewUiState(isLoading = false, overview = overview)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = StatsOverviewUiState(isLoading = true),
        )

    class Factory(
        private val statsRepository: StatsRepository,
        private val gameRepository: GameRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return StatsOverviewViewModel(statsRepository, gameRepository) as T
        }
    }
}
