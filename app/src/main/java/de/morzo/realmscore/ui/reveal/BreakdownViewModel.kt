package de.morzo.realmscore.ui.reveal

import de.morzo.realmscore.data.repository.HandScoringService
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
    private val handScoring: HandScoringService,
    private val roundId: String,
    private val profileId: String,
) : ViewModel() {

    private val _scoringResult = MutableStateFlow<ScoringResult?>(null)
    val scoringResult: StateFlow<ScoringResult?> = _scoringResult.asStateFlow()

    private val _handCards = MutableStateFlow<List<CardDefinition>>(emptyList())
    val handCards: StateFlow<List<CardDefinition>> = _handCards.asStateFlow()

    /** Phase 30: cursed items the player used this round and their summed points. */
    private val _cursedItemKeys = MutableStateFlow<List<String>>(emptyList())
    val cursedItemKeys: StateFlow<List<String>> = _cursedItemKeys.asStateFlow()
    private val _cursedPoints = MutableStateFlow(0)
    val cursedPoints: StateFlow<Int> = _cursedPoints.asStateFlow()

    init {
        viewModelScope.launch {
            val saved = handCardRepo.getHand(roundId, profileId) ?: return@launch
            // Canonical input (targets, Mittelfeld, player count) so the breakdown matches the stored
            // score of the same hand.
            val input = handScoring.input(saved.cards, handScoring.context(roundId)) ?: return@launch
            _handCards.value = input.hand
            _cursedItemKeys.value = saved.cursedItemKeys
            _cursedPoints.value = saved.cursedPoints
            val result = withContext(Dispatchers.Default) { engine.score(input) }
            _scoringResult.value = result
        }
    }

    class Factory(
        private val handCardRepo: HandCardRepository,
        private val engine: ScoringEngine,
        private val cardLookup: CardLookup,
        private val handScoring: HandScoringService,
        private val roundId: String,
        private val profileId: String,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return BreakdownViewModel(
                handCardRepo = handCardRepo,
                engine = engine,
                cardLookup = cardLookup,
                handScoring = handScoring,
                roundId = roundId,
                profileId = profileId,
            ) as T
        }
    }
}
