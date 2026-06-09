package de.morzo.realmscore.ui.reveal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import de.morzo.realmscore.data.cards.CardLookup
import de.morzo.realmscore.domain.model.CardDefinition
import de.morzo.realmscore.domain.model.Suit
import de.morzo.realmscore.domain.repository.HandCardRepository
import de.morzo.realmscore.domain.scoring.JokerAssignment
import de.morzo.realmscore.domain.scoring.ScoringEngine
import de.morzo.realmscore.domain.scoring.ScoringInput
import de.morzo.realmscore.domain.scoring.ScoringResult
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
            val assignments: Map<String, JokerAssignment> = saved.cards.mapNotNull { entry ->
                val target = entry.jokerTargetCardKey ?: return@mapNotNull null
                val suit = entry.jokerTargetSuit?.let { runCatching { Suit.valueOf(it) }.getOrNull() }
                entry.cardKey to JokerAssignment(
                    jokerKey = entry.cardKey,
                    targetCardKey = target,
                    targetSuit = suit,
                )
            }.toMap()
            val result = withContext(Dispatchers.Default) {
                engine.score(ScoringInput(hand = hand, jokerAssignments = assignments))
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
