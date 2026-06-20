package de.morzo.realmscore.ui.reveal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import de.morzo.realmscore.data.cards.CardLookup
import de.morzo.realmscore.domain.model.CardDefinition
import de.morzo.realmscore.domain.model.Game
import de.morzo.realmscore.domain.model.GameMode
import de.morzo.realmscore.domain.p2p.P2PSessionRepository
import de.morzo.realmscore.domain.p2p.model.SessionState
import de.morzo.realmscore.domain.repository.GameRepository
import de.morzo.realmscore.domain.repository.HandCardRepository
import de.morzo.realmscore.domain.repository.ProfileRepository
import de.morzo.realmscore.domain.repository.RoundRepository
import de.morzo.realmscore.domain.scoring.ScoringEngine
import de.morzo.realmscore.domain.scoring.ScoringInput
import de.morzo.realmscore.domain.scoring.toScoringChoices
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class PlayerSummary(
    val profileId: String,
    val name: String,
    val colorArgb: Int,
    val totalScore: Int,
    val positiveContribution: Int,
    val negativeContribution: Int,
    val blankedCount: Int,
)

data class RoundSummaryUiState(
    val isLoading: Boolean = true,
    val gameId: String = "",
    val roundNumber: Int = 0,
    val players: List<PlayerSummary> = emptyList(),
    val winnerId: String? = null,
    val discardScanned: Boolean = false,
    val discardCards: List<CardDefinition> = emptyList(),
    val canStartNextRound: Boolean = false,
    val canEditRound: Boolean = false,
    val isLastRound: Boolean = false,
    /** P2P (Stage B): a client only follows the host — it never advances or closes the game itself. */
    val isP2pClient: Boolean = false,
)

class RoundSummaryViewModel(
    private val roundId: String,
    private val roundRepo: RoundRepository,
    private val gameRepo: GameRepository,
    private val profileRepo: ProfileRepository,
    private val handCardRepo: HandCardRepository,
    private val cardLookup: CardLookup,
    private val engine: ScoringEngine,
    private val p2p: P2PSessionRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(RoundSummaryUiState())
    val uiState: StateFlow<RoundSummaryUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val round = roundRepo.getRoundById(roundId)
                ?: error("Round not found: $roundId")
            val game = gameRepo.getById(round.gameId)
                ?: error("Game not found: ${round.gameId}")
            val participants = gameRepo.getParticipants(round.gameId)
                .sortedBy { it.seatOrder }

            val rawSummaries = participants.mapNotNull { participant ->
                val profile = profileRepo.getById(participant.profileId) ?: return@mapNotNull null
                val saved = handCardRepo.getHand(roundId, participant.profileId)
                    ?: return@mapNotNull null
                val breakdown = withContext(Dispatchers.Default) {
                    rescore(saved.cards)
                }
                PlayerSummary(
                    profileId = profile.id,
                    name = profile.name,
                    colorArgb = profile.colorArgb,
                    totalScore = saved.totalScore,
                    positiveContribution = breakdown.positive,
                    negativeContribution = breakdown.negative,
                    blankedCount = breakdown.blankedCount,
                )
            }.sortedByDescending { it.totalScore }

            val winnerId = rawSummaries.firstOrNull()?.profileId

            roundRepo.markRoundCompleted(roundId)

            // Discard pile (Mittelfeld) is editable after the reveal, so observe it reactively.
            launch {
                combine(
                    roundRepo.observeRoundById(roundId),
                    roundRepo.observeDiscardCards(roundId),
                ) { r, keys ->
                    (r?.discardScanned ?: false) to keys.mapNotNull { cardLookup.getByKey(it) }
                }.collect { (scanned, cards) ->
                    _uiState.update { it.copy(discardScanned = scanned, discardCards = cards) }
                }
            }

            launch {
                combine(
                    roundRepo.observeRoundsForGame(game.id),
                    roundRepo.observeResultsForGame(game.id),
                ) { rounds, results ->
                    val maxRoundNumber = rounds.maxOfOrNull { it.roundNumber } ?: round.roundNumber
                    val isLast = round.roundNumber >= maxRoundNumber
                    val totalsByProfile: Map<String, Int> = participants
                        .map { it.profileId }
                        .associateWith { pid ->
                            results.filter { it.profileId == pid }.sumOf { it.totalScore }
                        }
                    val canNext = canStartNextRound(game, totalsByProfile, rounds.size)
                    Triple(isLast, canNext, totalsByProfile.size)
                }.collect { (isLast, canNext, _) ->
                    val isP2pClient = p2p.sessionState.value is SessionState.Connected
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            gameId = game.id,
                            roundNumber = round.roundNumber,
                            players = rawSummaries,
                            winnerId = winnerId,
                            canStartNextRound = canNext && isLast,
                            canEditRound = isLast,
                            isLastRound = isLast,
                            isP2pClient = isP2pClient,
                        )
                    }
                }
            }
        }
    }

    fun startNextRound(onStarted: (newRoundId: String) -> Unit) {
        viewModelScope.launch {
            val gameId = _uiState.value.gameId
            if (gameId.isEmpty()) return@launch
            // Host of a live session: mint + distribute the round so every device follows (clients get
            // the OpenRound signal). Otherwise (solo) just start the next round locally.
            if (p2p.sessionState.value is SessionState.Hosting) {
                p2p.startSharedSession(gameId)
                    .onSuccess { roundId -> onStarted(roundId) }
                    .onFailure { onStarted(roundRepo.startRound(gameId).id) }
            } else {
                onStarted(roundRepo.startRound(gameId).id)
            }
        }
    }

    private data class Breakdown(val positive: Int, val negative: Int, val blankedCount: Int)

    private fun rescore(
        cards: List<de.morzo.realmscore.domain.repository.HandCardEntry>,
    ): Breakdown {
        val hand = cards.mapNotNull { cardLookup.getByKey(it.cardKey) }
        if (hand.size != cards.size) return Breakdown(0, 0, 0)
        // Single reconstruction path: every target (jokers, Island, Fountain, Necromancer pull) →
        // jokerAssignments, matching how the hand was scored at capture time.
        val choices = cards.toScoringChoices()
        val result = engine.score(
            ScoringInput(
                hand = hand,
                jokerAssignments = choices.jokerAssignments,
            )
        )
        val positive = result.perCard.filter { !it.isBlanked && it.contributedScore > 0 }
            .sumOf { it.contributedScore }
        val negative = -result.perCard.filter { !it.isBlanked && it.contributedScore < 0 }
            .sumOf { it.contributedScore }
        return Breakdown(
            positive = positive,
            negative = negative,
            blankedCount = result.blankedKeys.size,
        )
    }

    private fun canStartNextRound(
        game: Game,
        totalsByProfile: Map<String, Int>,
        completedRoundCount: Int,
    ): Boolean {
        return when (game.mode) {
            GameMode.FIXED_ROUNDS -> {
                val target = game.targetRounds ?: return false
                completedRoundCount < target
            }
            GameMode.POINT_LIMIT -> {
                val target = game.targetPoints ?: return true
                totalsByProfile.values.none { it >= target }
            }
        }
    }

    class Factory(
        private val roundId: String,
        private val roundRepo: RoundRepository,
        private val gameRepo: GameRepository,
        private val profileRepo: ProfileRepository,
        private val handCardRepo: HandCardRepository,
        private val cardLookup: CardLookup,
        private val engine: ScoringEngine,
        private val p2p: P2PSessionRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return RoundSummaryViewModel(
                roundId = roundId,
                roundRepo = roundRepo,
                gameRepo = gameRepo,
                profileRepo = profileRepo,
                handCardRepo = handCardRepo,
                cardLookup = cardLookup,
                engine = engine,
                p2p = p2p,
            ) as T
        }
    }
}
