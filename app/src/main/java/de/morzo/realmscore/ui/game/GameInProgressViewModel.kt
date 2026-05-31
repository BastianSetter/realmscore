package de.morzo.realmscore.ui.game

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import de.morzo.realmscore.domain.repository.RoundRepository
import de.morzo.realmscore.domain.usecase.game.GameState
import de.morzo.realmscore.domain.usecase.game.GetGameStateUseCase
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface GameInProgressUiState {
    data object Loading : GameInProgressUiState
    data class Ready(val state: GameState) : GameInProgressUiState
}

class GameInProgressViewModel(
    private val gameId: String,
    private val getGameStateUseCase: GetGameStateUseCase,
    private val roundRepo: RoundRepository,
) : ViewModel() {

    val uiState: StateFlow<GameInProgressUiState> =
        getGameStateUseCase.observe(gameId)
            .map<GameState, GameInProgressUiState> { GameInProgressUiState.Ready(it) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = GameInProgressUiState.Loading,
            )

    fun startNextRound(onStarted: (roundId: String) -> Unit) {
        viewModelScope.launch {
            val round = roundRepo.startRound(gameId)
            onStarted(round.id)
        }
    }

    fun continueOpenRound(onContinue: (roundId: String) -> Unit) {
        viewModelScope.launch {
            val open = roundRepo.getOpenRound(gameId) ?: roundRepo.startRound(gameId)
            onContinue(open.id)
        }
    }

    class Factory(
        private val gameId: String,
        private val getGameStateUseCase: GetGameStateUseCase,
        private val roundRepo: RoundRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return GameInProgressViewModel(gameId, getGameStateUseCase, roundRepo) as T
        }
    }
}
