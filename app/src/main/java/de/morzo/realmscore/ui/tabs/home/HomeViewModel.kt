package de.morzo.realmscore.ui.tabs.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import de.morzo.realmscore.domain.model.Game
import de.morzo.realmscore.domain.repository.GameRepository
import de.morzo.realmscore.domain.repository.ProfileRepository
import de.morzo.realmscore.domain.repository.RoundRepository
import de.morzo.realmscore.domain.stats.random.PickRandomStatUseCase
import de.morzo.realmscore.domain.stats.random.RandomStatResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ParticipantBadge(
    val profileId: String,
    val name: String,
    val colorArgb: Int,
)

data class TopStandInfo(
    val profileId: String?,
    val name: String?,
    val score: Int,
)

data class OpenGameCard(
    val gameId: String,
    val displayName: String?,
    val participants: List<ParticipantBadge>,
    val topStand: TopStandInfo,
    val updatedAt: Long,
    val startedAt: Long,
)

data class HomeUiState(
    val ownerName: String? = null,
    val openGames: List<OpenGameCard> = emptyList(),
    val randomStat: RandomStatResult = RandomStatResult.NotEnoughData,
    val isLoading: Boolean = true,
)

class HomeViewModel(
    private val profileRepo: ProfileRepository,
    private val gameRepo: GameRepository,
    private val roundRepo: RoundRepository,
    private val pickRandomStatUseCase: PickRandomStatUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val owner = profileRepo.getLocalOwner()
            _uiState.update { it.copy(ownerName = owner?.name) }
        }
        viewModelScope.launch {
            gameRepo.observeOpenGames().collect { games ->
                val cards = buildOpenGameCards(games)
                _uiState.update { it.copy(openGames = cards, isLoading = false) }
            }
        }
    }

    fun onResume() {
        viewModelScope.launch {
            val newStat = pickRandomStatUseCase.execute()
            _uiState.update { it.copy(randomStat = newStat) }
        }
    }

    private suspend fun buildOpenGameCards(games: List<Game>): List<OpenGameCard> {
        if (games.isEmpty()) return emptyList()
        val gameIds = games.map { it.id }
        val participantsByGame = gameRepo.getParticipantsForGames(gameIds).groupBy { it.gameId }
        val totalsByGame = roundRepo.getScoreTotalsForGames(gameIds)
        val profileIds = participantsByGame.values.flatten().map { it.profileId }.toSet()
        val profilesById = profileIds
            .mapNotNull { profileRepo.getById(it) }
            .associateBy { it.id }
        return games
            .sortedByDescending { it.updatedAt }
            .map { game ->
                val participants = participantsByGame[game.id].orEmpty()
                    .sortedBy { it.seatOrder }
                    .mapNotNull { p ->
                        profilesById[p.profileId]?.let { profile ->
                            ParticipantBadge(
                                profileId = profile.id,
                                name = profile.name,
                                colorArgb = profile.colorArgb,
                            )
                        }
                    }
                val totals = totalsByGame[game.id].orEmpty()
                val top = totals.entries.maxByOrNull { it.value }
                val topStand = TopStandInfo(
                    profileId = top?.key,
                    name = top?.key?.let { profilesById[it]?.name },
                    score = top?.value ?: 0,
                )
                OpenGameCard(
                    gameId = game.id,
                    displayName = game.displayName,
                    participants = participants,
                    topStand = topStand,
                    updatedAt = game.updatedAt,
                    startedAt = game.startedAt,
                )
            }
    }

    class Factory(
        private val profileRepo: ProfileRepository,
        private val gameRepo: GameRepository,
        private val roundRepo: RoundRepository,
        private val pickRandomStatUseCase: PickRandomStatUseCase,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return HomeViewModel(
                profileRepo = profileRepo,
                gameRepo = gameRepo,
                roundRepo = roundRepo,
                pickRandomStatUseCase = pickRandomStatUseCase,
            ) as T
        }
    }
}
