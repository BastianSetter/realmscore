package de.morzo.realmscore.ui.game

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import de.morzo.realmscore.domain.repository.GameRepository
import de.morzo.realmscore.domain.repository.HandCardRepository
import de.morzo.realmscore.domain.repository.ProfileRepository
import de.morzo.realmscore.domain.repository.RoundRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val HAND_CARDS_PER_PLAYER = 7

enum class PlayerEntryStatus { NOT_STARTED, COMPLETED }

data class PlayerEntryRow(
    val profileId: String,
    val name: String,
    val colorArgb: Int,
    val status: PlayerEntryStatus,
)

data class RoundEntryUiState(
    val isLoading: Boolean = true,
    val roundNumber: Int = 0,
    val players: List<PlayerEntryRow> = emptyList(),
) {
    val allCompleted: Boolean
        get() = players.isNotEmpty() && players.all { it.status == PlayerEntryStatus.COMPLETED }
}

class RoundEntryViewModel(
    private val roundId: String,
    private val roundRepo: RoundRepository,
    private val gameRepo: GameRepository,
    private val profileRepo: ProfileRepository,
    private val handCardRepo: HandCardRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(RoundEntryUiState())
    val uiState: StateFlow<RoundEntryUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val round = roundRepo.getRoundById(roundId)
                ?: error("Round not found: $roundId")
            val participants = gameRepo.getParticipants(round.gameId)
                .sortedBy { it.seatOrder }
            val basePlayers = participants.mapNotNull { participant ->
                profileRepo.getById(participant.profileId)?.let { profile ->
                    PlayerEntryRow(
                        profileId = profile.id,
                        name = profile.name,
                        colorArgb = profile.colorArgb,
                        status = PlayerEntryStatus.NOT_STARTED,
                    )
                }
            }
            _uiState.update {
                it.copy(
                    isLoading = false,
                    roundNumber = round.roundNumber,
                    players = basePlayers,
                )
            }

            handCardRepo.observeHandCardCountByProfile(roundId).collect { counts ->
                _uiState.update { state ->
                    val updated = state.players.map { row ->
                        val cnt = counts[row.profileId] ?: 0
                        val status = if (cnt >= HAND_CARDS_PER_PLAYER) {
                            PlayerEntryStatus.COMPLETED
                        } else {
                            PlayerEntryStatus.NOT_STARTED
                        }
                        row.copy(status = status)
                    }
                    state.copy(players = updated)
                }
            }
        }
    }

    class Factory(
        private val roundId: String,
        private val roundRepo: RoundRepository,
        private val gameRepo: GameRepository,
        private val profileRepo: ProfileRepository,
        private val handCardRepo: HandCardRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return RoundEntryViewModel(
                roundId = roundId,
                roundRepo = roundRepo,
                gameRepo = gameRepo,
                profileRepo = profileRepo,
                handCardRepo = handCardRepo,
            ) as T
        }
    }
}
