package de.morzo.realmscore.ui.handentry

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import de.morzo.realmscore.data.cards.CardLookup
import de.morzo.realmscore.domain.model.CardDefinition
import de.morzo.realmscore.domain.model.Suit
import de.morzo.realmscore.domain.repository.HandCardEntry
import de.morzo.realmscore.domain.repository.HandCardRepository
import de.morzo.realmscore.domain.repository.ProfileRepository
import de.morzo.realmscore.domain.scoring.JokerAssignment
import de.morzo.realmscore.domain.scoring.PlayerChoices
import de.morzo.realmscore.domain.scoring.ScoringEngine
import de.morzo.realmscore.domain.scoring.ScoringInput
import de.morzo.realmscore.domain.scoring.solver.OptimalSolver
import de.morzo.realmscore.ui.sandbox.CardSlot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

const val PLAYER_HAND_SLOT_COUNT = 7

private const val NECROMANCER_KEY = "necromancer"

data class PlayerHandEntryUiState(
    val isLoading: Boolean = true,
    val playerName: String = "",
    val slots: List<CardSlot> = List(PLAYER_HAND_SLOT_COUNT) { CardSlot.Empty },
    val jokerAssignments: Map<String, JokerAssignment> = emptyMap(),
    val playerChoices: PlayerChoices = PlayerChoices(),
    val cardsUsedByOthers: Set<String> = emptySet(),
    val isOptimalRunning: Boolean = false,
    val isSaving: Boolean = false,
    /**
     * Discard mode (Mittelfeld): the entry just records card identities, so joker resolution,
     * the Necromancer field and per-card scoring are suppressed. Used by RoundCaptureViewModel.
     */
    val isDiscard: Boolean = false,
    /** Cards required to mark the entry complete (7 for a hand, 10/12 for the Mittelfeld). */
    val requiredSlotCount: Int = PLAYER_HAND_SLOT_COUNT,
) {
    val filledCards: List<CardDefinition>
        get() = slots.mapNotNull { (it as? CardSlot.Filled)?.card }

    val cardsCount: Int get() = filledCards.size

    val jokersInHand: List<CardDefinition>
        get() = filledCards.filter { it.isJoker }

    val necromancerInHand: Boolean
        get() = filledCards.any { it.key == NECROMANCER_KEY }

    val allJokersResolved: Boolean
        get() = jokersInHand.all { joker ->
            val assignment = jokerAssignments[joker.key] ?: return@all false
            assignment.targetCardKey != null
        }

    val canSubmit: Boolean
        get() = cardsCount == requiredSlotCount && (isDiscard || allJokersResolved) && !isSaving
}

class PlayerHandEntryViewModel(
    private val cardLookup: CardLookup,
    private val handCardRepo: HandCardRepository,
    private val profileRepo: ProfileRepository,
    private val engine: ScoringEngine,
    private val optimalSolver: OptimalSolver,
    private val roundId: String,
    private val profileId: String,
) : ViewModel() {

    val allCards: List<CardDefinition> = cardLookup.getAll()

    private val _uiState = MutableStateFlow(PlayerHandEntryUiState())
    val uiState: StateFlow<PlayerHandEntryUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val profile = profileRepo.getById(profileId)
                ?: error("Profile not found: $profileId")
            val existing = handCardRepo.getHand(roundId, profileId)

            val slots: List<CardSlot> = MutableList<CardSlot>(PLAYER_HAND_SLOT_COUNT) { CardSlot.Empty }
                .also { mut ->
                    existing?.cards?.forEach { entry ->
                        val card = cardLookup.getByKey(entry.cardKey) ?: return@forEach
                        if (entry.position in 0 until PLAYER_HAND_SLOT_COUNT) {
                            mut[entry.position] = CardSlot.Filled(card)
                        }
                    }
                }
            // The Necromancer is not a joker; we persist its pull on the Necromancer HandCard via
            // the reused jokerTargetCardKey field. Exclude it from joker assignments on load.
            val jokerAssignments = existing?.cards
                ?.filter { it.cardKey != NECROMANCER_KEY }
                ?.mapNotNull { entry ->
                    val target = entry.jokerTargetCardKey ?: return@mapNotNull null
                    val suit = entry.jokerTargetSuit?.let { runCatching { Suit.valueOf(it) }.getOrNull() }
                    entry.cardKey to JokerAssignment(
                        jokerKey = entry.cardKey,
                        targetCardKey = target,
                        targetSuit = suit,
                    )
                }?.toMap()
                ?: emptyMap()

            val necromancerPickKey = existing?.cards
                ?.firstOrNull { it.cardKey == NECROMANCER_KEY }
                ?.jokerTargetCardKey

            _uiState.update {
                it.copy(
                    isLoading = false,
                    playerName = profile.name,
                    slots = slots,
                    jokerAssignments = jokerAssignments,
                    playerChoices = PlayerChoices(necromancerPickKey = necromancerPickKey),
                )
            }

            launch {
                handCardRepo.observeCardKeysUsedByOtherProfiles(roundId, profileId)
                    .collect { used ->
                        _uiState.update { it.copy(cardsUsedByOthers = used) }
                    }
            }
        }
    }

    fun setCardInSlot(slotIndex: Int, card: CardDefinition) {
        if (slotIndex !in 0 until PLAYER_HAND_SLOT_COUNT) return
        _uiState.update { state ->
            val newSlots = state.slots.toMutableList().also { it[slotIndex] = CardSlot.Filled(card) }
            state.copy(slots = newSlots).pruneStaleSelections()
        }
    }

    fun clearSlot(slotIndex: Int) {
        if (slotIndex !in 0 until PLAYER_HAND_SLOT_COUNT) return
        _uiState.update { state ->
            val newSlots = state.slots.toMutableList().also { it[slotIndex] = CardSlot.Empty }
            state.copy(slots = newSlots).pruneStaleSelections()
        }
    }

    fun setJokerAssignment(jokerKey: String, assignment: JokerAssignment?) {
        _uiState.update { state ->
            val newAssignments = state.jokerAssignments.toMutableMap()
            if (assignment == null) newAssignments.remove(jokerKey) else newAssignments[jokerKey] = assignment
            state.copy(jokerAssignments = newAssignments)
        }
    }

    fun setNecromancerPick(cardKey: String) {
        _uiState.update { state ->
            state.copy(playerChoices = state.playerChoices.copy(necromancerPickKey = cardKey))
        }
    }

    fun clearNecromancerPick() {
        _uiState.update { state ->
            state.copy(playerChoices = state.playerChoices.copy(necromancerPickKey = null))
        }
    }

    fun applyOptimal() {
        val current = _uiState.value
        if (current.filledCards.isEmpty()) return
        val seed = ScoringInput(
            hand = current.filledCards,
            jokerAssignments = current.jokerAssignments,
            playerChoices = current.playerChoices,
        )
        _uiState.update { it.copy(isOptimalRunning = true) }
        viewModelScope.launch {
            val best = withContext(Dispatchers.Default) { optimalSolver.findOptimal(seed) }
            _uiState.update {
                it.copy(
                    jokerAssignments = best.bestInput.jokerAssignments,
                    playerChoices = best.bestInput.playerChoices,
                    isOptimalRunning = false,
                )
            }
        }
    }

    fun submit(onSuccess: () -> Unit) {
        val current = _uiState.value
        if (!current.canSubmit) return
        _uiState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            val input = ScoringInput(
                hand = current.filledCards,
                jokerAssignments = current.jokerAssignments,
                playerChoices = current.playerChoices,
            )
            val totalScore = withContext(Dispatchers.Default) { engine.score(input).totalScore }
            val entries = current.slots.mapIndexedNotNull { idx, slot ->
                val card = (slot as? CardSlot.Filled)?.card ?: return@mapIndexedNotNull null
                val assignment = current.jokerAssignments[card.key]
                // Necromancer reuses jokerTargetCardKey to persist its pulled card (the "target").
                val targetCardKey = if (card.key == NECROMANCER_KEY) {
                    current.playerChoices.necromancerPickKey
                } else {
                    assignment?.targetCardKey
                }
                HandCardEntry(
                    cardKey = card.key,
                    position = idx,
                    jokerTargetCardKey = targetCardKey,
                    jokerTargetSuit = assignment?.targetSuit?.name,
                )
            }
            handCardRepo.saveHand(
                roundId = roundId,
                profileId = profileId,
                cards = entries,
                totalScore = totalScore,
            )
            _uiState.update { it.copy(isSaving = false) }
            onSuccess()
        }
    }

    private fun PlayerHandEntryUiState.pruneStaleSelections(): PlayerHandEntryUiState {
        val handKeys = filledCards.map { it.key }.toSet()
        val newAssignments = jokerAssignments.filterKeys { it in handKeys }
        // The Necromancer pick is a discard-pile card (never in hand): keep it as long as a
        // Necromancer is still in the hand, unlike Island/Fountain targets which must be hand cards.
        val necromancerStillInHand = NECROMANCER_KEY in handKeys
        val newChoices = playerChoices.copy(
            islandTargetKey = playerChoices.islandTargetKey?.takeIf { it in handKeys },
            fountainSourceKey = playerChoices.fountainSourceKey?.takeIf { it in handKeys },
            necromancerPickKey = playerChoices.necromancerPickKey?.takeIf { necromancerStillInHand },
        )
        return copy(jokerAssignments = newAssignments, playerChoices = newChoices)
    }

    class Factory(
        private val cardLookup: CardLookup,
        private val handCardRepo: HandCardRepository,
        private val profileRepo: ProfileRepository,
        private val engine: ScoringEngine,
        private val optimalSolver: OptimalSolver,
        private val roundId: String,
        private val profileId: String,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return PlayerHandEntryViewModel(
                cardLookup = cardLookup,
                handCardRepo = handCardRepo,
                profileRepo = profileRepo,
                engine = engine,
                optimalSolver = optimalSolver,
                roundId = roundId,
                profileId = profileId,
            ) as T
        }
    }
}
