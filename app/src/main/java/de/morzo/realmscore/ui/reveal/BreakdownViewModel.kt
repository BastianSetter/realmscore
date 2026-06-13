package de.morzo.realmscore.ui.reveal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import de.morzo.realmscore.data.cards.CardLookup
import de.morzo.realmscore.domain.model.CardDefinition
import de.morzo.realmscore.domain.repository.HandCardRepository
import de.morzo.realmscore.domain.scoring.ScoringEngine
import de.morzo.realmscore.domain.scoring.ScoringInput
import de.morzo.realmscore.domain.scoring.ScoringResult
import de.morzo.realmscore.domain.scoring.toScoringChoices
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BreakdownViewModel(
    private val handCardRepo: HandCardRepository,
    private val engine: ScoringEngine,
    private val cardLookup: CardLookup,
    private val roundId: String,
    private val profileId: String,
) : ViewModel() {

    private val _scoringResult = MutableStateFlow<ScoringResult?>(null)
    val scoringResult: StateFlow<ScoringResult?> = _scoringResult.asStateFlow()

    private val _handCards = MutableStateFlow<List<CardDefinition>>(emptyList())
    val handCards: StateFlow<List<CardDefinition>> = _handCards.asStateFlow()

    init {
        viewModelScope.launch {
            val saved = handCardRepo.getHand(roundId, profileId) ?: return@launch
            val hand = saved.cards.mapNotNull { cardLookup.getByKey(it.cardKey) }
            if (hand.size != saved.cards.size) return@launch
            _handCards.value = hand
            // Reconstruct all joker assignments (Necromancer pull, Island, Fountain included) so the
            // breakdown matches the Sandbox for the same hand.
            val reconstructed = saved.cards.toScoringChoices()
            val result = withContext(Dispatchers.Default) {
                engine.score(
                    ScoringInput(
                        hand = hand,
                        jokerAssignments = reconstructed.jokerAssignments,
                    ),
                )
            }
            _scoringResult.value = result
        }
    }

    class Factory(
        private val handCardRepo: HandCardRepository,
        private val engine: ScoringEngine,
        private val cardLookup: CardLookup,
        private val roundId: String,
        private val profileId: String,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return BreakdownViewModel(
                handCardRepo = handCardRepo,
                engine = engine,
                cardLookup = cardLookup,
                roundId = roundId,
                profileId = profileId,
            ) as T
        }
    }
}
