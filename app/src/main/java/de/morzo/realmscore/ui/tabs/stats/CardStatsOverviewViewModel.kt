package de.morzo.realmscore.ui.tabs.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import de.morzo.realmscore.domain.repository.StatsRepository
import de.morzo.realmscore.domain.stats.CardStatsRow
import de.morzo.realmscore.domain.stats.CardStatsSort
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CardStatsOverviewUiState(
    val isLoading: Boolean = true,
    val rows: List<CardStatsRow> = emptyList(),
    val sortBy: CardStatsSort = CardStatsSort.POPULARITY,
    val totalRounds: Int = 0,
    val scannedRounds: Int = 0,
)

class CardStatsOverviewViewModel(
    private val statsRepository: StatsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CardStatsOverviewUiState())
    val uiState: StateFlow<CardStatsOverviewUiState> = _uiState.asStateFlow()

    init {
        load(CardStatsSort.POPULARITY)
    }

    fun changeSort(sortBy: CardStatsSort) {
        if (sortBy == _uiState.value.sortBy && _uiState.value.rows.isNotEmpty()) return
        load(sortBy)
    }

    private fun load(sortBy: CardStatsSort) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, sortBy = sortBy) }
            val rows = statsRepository.getCardStatsOverview(sortBy)
            val global = statsRepository.getGlobalStats()
            // scannedRounds is approximated via getCardStats on first known card if available;
            // but the repository's overview rows do not carry it, so we re-derive here.
            // For now we surface totalRounds via the GlobalStats.
            _uiState.update {
                it.copy(
                    isLoading = false,
                    rows = rows,
                    sortBy = sortBy,
                    totalRounds = global.totalRoundsPlayed,
                    scannedRounds = 0,
                )
            }
        }
    }

    class Factory(
        private val statsRepository: StatsRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return CardStatsOverviewViewModel(statsRepository) as T
        }
    }
}
