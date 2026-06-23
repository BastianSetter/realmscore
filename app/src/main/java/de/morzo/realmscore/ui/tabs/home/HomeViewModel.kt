package de.morzo.realmscore.ui.tabs.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import de.morzo.realmscore.domain.model.Game
import de.morzo.realmscore.domain.p2p.P2PSessionRepository
import de.morzo.realmscore.domain.p2p.model.HandshakePayload
import de.morzo.realmscore.domain.p2p.model.SessionState
import de.morzo.realmscore.domain.repository.GameRepository
import de.morzo.realmscore.domain.repository.ProfileRepository
import de.morzo.realmscore.domain.repository.RoundRepository
import de.morzo.realmscore.domain.stats.random.PickRandomStatUseCase
import de.morzo.realmscore.domain.stats.random.RandomStatResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
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

/**
 * The P2P state of the Home "session" card (Phase 28). Host and client states are mutually exclusive,
 * so one card slot covers all three: host shows its QR for re-scans, a dropped client rejoins silently,
 * otherwise it's the plain join entry point.
 */
sealed interface P2pCardState {
    /** No session: the normal "Session beitreten" scanner entry. */
    data object Join : P2pCardState

    /** A client with a persisted last host (§6 #2): "Session erneut beitreten" → silent reconnect. */
    data object Rejoin : P2pCardState

    /** This device is hosting: offer to re-display the QR + code so a re-scanning client can rejoin. */
    data class ShowQr(val payload: HandshakePayload, val sessionCode: String) : P2pCardState
}

data class HomeUiState(
    val ownerName: String? = null,
    val openGames: List<OpenGameCard> = emptyList(),
    val p2pCard: P2pCardState = P2pCardState.Join,
    val randomStat: RandomStatResult = RandomStatResult.NotEnoughData,
    val isLoading: Boolean = true,
)

class HomeViewModel(
    private val profileRepo: ProfileRepository,
    private val gameRepo: GameRepository,
    private val roundRepo: RoundRepository,
    private val pickRandomStatUseCase: PickRandomStatUseCase,
    private val p2pSessionRepo: P2PSessionRepository,
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
        viewModelScope.launch {
            combine(
                p2pSessionRepo.sessionState,
                p2pSessionRepo.rejoinInfo,
            ) { session, rejoin ->
                when {
                    session is SessionState.Hosting -> P2pCardState.ShowQr(session.payload, session.sessionCode)
                    rejoin != null -> P2pCardState.Rejoin
                    else -> P2pCardState.Join
                }
            }.collect { card -> _uiState.update { it.copy(p2pCard = card) } }
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
        private val p2pSessionRepo: P2PSessionRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return HomeViewModel(
                profileRepo = profileRepo,
                gameRepo = gameRepo,
                roundRepo = roundRepo,
                pickRandomStatUseCase = pickRandomStatUseCase,
                p2pSessionRepo = p2pSessionRepo,
            ) as T
        }
    }
}
