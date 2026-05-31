package de.morzo.realmscore.ui.reveal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import de.morzo.realmscore.data.cards.CardLookup
import de.morzo.realmscore.domain.model.Suit
import de.morzo.realmscore.domain.repository.GameRepository
import de.morzo.realmscore.domain.repository.HandCardRepository
import de.morzo.realmscore.domain.repository.ProfileRepository
import de.morzo.realmscore.domain.repository.RoundRepository
import de.morzo.realmscore.domain.scoring.JokerAssignment
import de.morzo.realmscore.domain.scoring.ScoringEngine
import de.morzo.realmscore.domain.scoring.ScoringInput
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class PlayerReveal(
    val profileId: String,
    val name: String,
    val colorArgb: Int,
    val finalScore: Int,
    val topCards: List<String>,
)

data class RevealUiState(
    val isLoading: Boolean = true,
    val players: List<PlayerReveal> = emptyList(),
    val currentRevealIndex: Int = 0,
) {
    val isDone: Boolean
        get() = players.isNotEmpty() && currentRevealIndex >= players.size
}

class RevealViewModel(
    private val roundId: String,
    private val roundRepo: RoundRepository,
    private val gameRepo: GameRepository,
    private val profileRepo: ProfileRepository,
    private val handCardRepo: HandCardRepository,
    private val cardLookup: CardLookup,
    private val engine: ScoringEngine,
) : ViewModel() {

    private val _uiState = MutableStateFlow(RevealUiState())
    val uiState: StateFlow<RevealUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val round = roundRepo.getRoundById(roundId)
                ?: error("Round not found: $roundId")
            val participants = gameRepo.getParticipants(round.gameId)
                .sortedBy { it.seatOrder }

            val players = participants.mapNotNull { participant ->
                val profile = profileRepo.getById(participant.profileId) ?: return@mapNotNull null
                val saved = handCardRepo.getHand(roundId, participant.profileId)
                    ?: return@mapNotNull null
                val topCards = withContext(Dispatchers.Default) {
                    computeTopCards(saved.cards)
                }
                PlayerReveal(
                    profileId = profile.id,
                    name = profile.name,
                    colorArgb = profile.colorArgb,
                    finalScore = saved.totalScore,
                    topCards = topCards,
                )
            }.sortedBy { it.finalScore }

            _uiState.update {
                it.copy(
                    isLoading = false,
                    players = players,
                    currentRevealIndex = 0,
                )
            }

            roundRepo.markRoundCompleted(roundId)
        }
    }

    fun revealNext() {
        _uiState.update { state ->
            if (state.players.isEmpty()) return@update state
            val next = (state.currentRevealIndex + 1).coerceAtMost(state.players.size)
            state.copy(currentRevealIndex = next)
        }
    }

    private fun computeTopCards(
        cards: List<de.morzo.realmscore.domain.repository.HandCardEntry>,
    ): List<String> {
        val hand = cards.mapNotNull { cardLookup.getByKey(it.cardKey) }
        if (hand.size != cards.size) return emptyList()
        val assignments: Map<String, JokerAssignment> = cards.mapNotNull { entry ->
            val target = entry.jokerTargetCardKey ?: return@mapNotNull null
            val suit = entry.jokerTargetSuit?.let { runCatching { Suit.valueOf(it) }.getOrNull() }
            entry.cardKey to JokerAssignment(
                jokerKey = entry.cardKey,
                targetCardKey = target,
                targetSuit = suit,
            )
        }.toMap()
        val result = engine.score(ScoringInput(hand = hand, jokerAssignments = assignments))
        return result.perCard
            .filter { !it.isBlanked }
            .sortedByDescending { it.contributedScore }
            .take(3)
            .map { it.effectiveName }
    }

    class Factory(
        private val roundId: String,
        private val roundRepo: RoundRepository,
        private val gameRepo: GameRepository,
        private val profileRepo: ProfileRepository,
        private val handCardRepo: HandCardRepository,
        private val cardLookup: CardLookup,
        private val engine: ScoringEngine,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return RevealViewModel(
                roundId = roundId,
                roundRepo = roundRepo,
                gameRepo = gameRepo,
                profileRepo = profileRepo,
                handCardRepo = handCardRepo,
                cardLookup = cardLookup,
                engine = engine,
            ) as T
        }
    }
}
