package de.morzo.realmscore.ui.sandbox

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import de.morzo.realmscore.data.cards.CardLookup
import de.morzo.realmscore.domain.model.CardDefinition
import de.morzo.realmscore.domain.model.Suit
import de.morzo.realmscore.domain.repository.GameRepository
import de.morzo.realmscore.domain.repository.HandCardRepository
import de.morzo.realmscore.domain.repository.ProfileRepository
import de.morzo.realmscore.domain.repository.RoundRepository
import de.morzo.realmscore.domain.scoring.JokerAssignment
import de.morzo.realmscore.domain.scoring.PlayerChoices
import de.morzo.realmscore.domain.scoring.ScoringEngine
import de.morzo.realmscore.domain.scoring.ScoringInput
import de.morzo.realmscore.domain.scoring.ScoringResult
import de.morzo.realmscore.domain.scoring.solver.OptimalSolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

const val SANDBOX_SLOT_COUNT = 7

sealed class CardSlot {
    data object Empty : CardSlot()
    data class Filled(val card: CardDefinition) : CardSlot()
}

data class OriginBanner(
    val gameId: String,
    val roundNumber: Int,
    val playerName: String,
    val gameDisplayName: String?,
    val gameStartedAt: Long,
)

data class SandboxUiState(
    val slots: List<CardSlot> = List(SANDBOX_SLOT_COUNT) { CardSlot.Empty },
    val jokerAssignments: Map<String, JokerAssignment> = emptyMap(),
    val playerChoices: PlayerChoices = PlayerChoices(),
    val scoringResult: ScoringResult? = null,
    val optimalRunning: Boolean = false,
    val breakdownOpen: Boolean = false,
    val originBanner: OriginBanner? = null,
    val discardCards: List<CardDefinition> = emptyList(),
    val discardScanned: Boolean = false,
    val isLoadingLaunchData: Boolean = false,
) {
    val score: Int get() = scoringResult?.totalScore ?: 0

    val filledCards: List<CardDefinition>
        get() = slots.mapNotNull { (it as? CardSlot.Filled)?.card }

    val jokersInHand: List<CardDefinition>
        get() = filledCards.filter { it.isJoker }

    val islandInHand: Boolean
        get() = filledCards.any { it.key == "island" }

    val fountainInHand: Boolean
        get() = filledCards.any { it.key == "fountain_of_life" }

    val necromancerInHand: Boolean
        get() = filledCards.any { it.key == "necromancer" }
}

class SandboxViewModel(
    private val launchData: SandboxLaunchData,
    private val cardLookup: CardLookup,
    private val engine: ScoringEngine,
    private val optimalSolver: OptimalSolver,
    private val handCardRepo: HandCardRepository?,
    private val roundRepo: RoundRepository?,
    private val gameRepo: GameRepository?,
    private val profileRepo: ProfileRepository?,
) : ViewModel() {

    val allCards: List<CardDefinition> = cardLookup.getAll()

    private val _uiState = MutableStateFlow(SandboxUiState())
    val uiState: StateFlow<SandboxUiState> = _uiState.asStateFlow()

    init {
        when (val data = launchData) {
            is SandboxLaunchData.Empty -> Unit
            is SandboxLaunchData.FromRound -> {
                _uiState.update { it.copy(isLoadingLaunchData = true) }
                viewModelScope.launch { loadFromRound(data) }
            }
        }
    }

    private suspend fun loadFromRound(data: SandboxLaunchData.FromRound) {
        val handRepo = handCardRepo ?: return clearLoading()
        val rRepo = roundRepo ?: return clearLoading()
        val gRepo = gameRepo ?: return clearLoading()
        val pRepo = profileRepo ?: return clearLoading()

        val saved = handRepo.getHand(data.roundId, data.profileId) ?: return clearLoading()
        val round = rRepo.getRoundById(data.roundId) ?: return clearLoading()
        val game = gRepo.getById(data.gameId) ?: return clearLoading()
        val profile = pRepo.getById(data.profileId) ?: return clearLoading()

        val discardKeys = rRepo.getDiscardCards(data.roundId)
        val discardCards = discardKeys.mapNotNull { cardLookup.getByKey(it) }

        val orderedEntries = saved.cards.sortedBy { it.position }
        val cards = orderedEntries.mapNotNull { cardLookup.getByKey(it.cardKey) }
        val slots = MutableList<CardSlot>(SANDBOX_SLOT_COUNT) { CardSlot.Empty }
        cards.forEachIndexed { idx, card ->
            if (idx < SANDBOX_SLOT_COUNT) slots[idx] = CardSlot.Filled(card)
        }

        // The Necromancer is not a joker; it persists its pulled card on its own HandCard via the
        // reused jokerTargetCardKey field. Exclude it here and restore it as the necromancer pick.
        val jokerAssignments: Map<String, JokerAssignment> = orderedEntries
            .filter { it.jokerTargetCardKey != null && it.cardKey != "necromancer" }
            .associate { entry ->
                entry.cardKey to JokerAssignment(
                    jokerKey = entry.cardKey,
                    targetCardKey = entry.jokerTargetCardKey,
                    targetSuit = entry.jokerTargetSuit
                        ?.let { runCatching { Suit.valueOf(it) }.getOrNull() },
                )
            }

        val necromancerPickKey = orderedEntries
            .firstOrNull { it.cardKey == "necromancer" }
            ?.jokerTargetCardKey

        val banner = OriginBanner(
            gameId = data.gameId,
            roundNumber = round.roundNumber,
            playerName = profile.name,
            gameDisplayName = game.displayName,
            gameStartedAt = game.startedAt,
        )

        _uiState.update { state ->
            state.copy(
                slots = slots,
                jokerAssignments = jokerAssignments,
                playerChoices = PlayerChoices(necromancerPickKey = necromancerPickKey),
                discardCards = discardCards,
                discardScanned = round.discardScanned,
                originBanner = banner,
                isLoadingLaunchData = false,
            ).recomputeScore()
        }
    }

    private fun clearLoading() {
        _uiState.update { it.copy(isLoadingLaunchData = false) }
    }

    fun setCardInSlot(slotIndex: Int, card: CardDefinition) {
        if (slotIndex !in 0 until SANDBOX_SLOT_COUNT) return
        _uiState.update { state ->
            val newSlots = state.slots.toMutableList().also { it[slotIndex] = CardSlot.Filled(card) }
            state.copy(slots = newSlots).pruneStaleSelections().recomputeScore()
        }
    }

    fun clearSlot(slotIndex: Int) {
        if (slotIndex !in 0 until SANDBOX_SLOT_COUNT) return
        _uiState.update { state ->
            val newSlots = state.slots.toMutableList().also { it[slotIndex] = CardSlot.Empty }
            state.copy(slots = newSlots).pruneStaleSelections().recomputeScore()
        }
    }

    fun setJokerAssignment(jokerKey: String, assignment: JokerAssignment?) {
        _uiState.update { state ->
            val newAssignments = state.jokerAssignments.toMutableMap()
            if (assignment == null) newAssignments.remove(jokerKey) else newAssignments[jokerKey] = assignment
            state.copy(jokerAssignments = newAssignments).recomputeScore()
        }
    }

    fun setIslandTarget(key: String?) {
        _uiState.update { state ->
            state.copy(playerChoices = state.playerChoices.copy(islandTargetKey = key)).recomputeScore()
        }
    }

    fun setFountainSource(key: String?) {
        _uiState.update { state ->
            state.copy(playerChoices = state.playerChoices.copy(fountainSourceKey = key)).recomputeScore()
        }
    }

    fun setNecromancerPick(cardKey: String) {
        _uiState.update { state ->
            state.copy(playerChoices = state.playerChoices.copy(necromancerPickKey = cardKey)).recomputeScore()
        }
    }

    fun clearNecromancerPick() {
        _uiState.update { state ->
            state.copy(playerChoices = state.playerChoices.copy(necromancerPickKey = null)).recomputeScore()
        }
    }

    fun applyOptimal() {
        val current = _uiState.value
        val seed = current.toScoringInput()
        _uiState.update { it.copy(optimalRunning = true) }
        viewModelScope.launch {
            val best = withContext(Dispatchers.Default) { optimalSolver.findOptimal(seed) }
            _uiState.update {
                it.copy(
                    jokerAssignments = best.bestInput.jokerAssignments,
                    playerChoices = best.bestInput.playerChoices,
                    scoringResult = best.bestResult,
                    optimalRunning = false,
                )
            }
        }
    }

    fun openBreakdown() {
        _uiState.update { it.copy(breakdownOpen = true) }
    }

    fun closeBreakdown() {
        _uiState.update { it.copy(breakdownOpen = false) }
    }

    fun reset() {
        _uiState.update { SandboxUiState() }
    }

    private fun SandboxUiState.toScoringInput(): ScoringInput = ScoringInput(
        hand = filledCards,
        jokerAssignments = jokerAssignments,
        playerChoices = playerChoices,
        discardPile = discardCards,
        discardScanned = discardScanned,
    )

    private fun SandboxUiState.recomputeScore(): SandboxUiState {
        val input = toScoringInput()
        val result = if (input.hand.isEmpty()) null else engine.score(input)
        return copy(scoringResult = result)
    }

    /**
     * Drops joker/choice references that no longer match any card in the slots.
     * Called whenever the hand composition changes.
     */
    private fun SandboxUiState.pruneStaleSelections(): SandboxUiState {
        val handKeys = filledCards.map { it.key }.toSet()
        val newJokerAssignments = jokerAssignments.filterKeys { it in handKeys }
        // The Necromancer pick is a discard-pile card (never in hand), so it is kept as long as a
        // Necromancer remains in the hand — not gated on handKeys like Island/Fountain targets.
        val necromancerStillInHand = "necromancer" in handKeys
        val newChoices = playerChoices.copy(
            islandTargetKey = playerChoices.islandTargetKey?.takeIf { it in handKeys },
            fountainSourceKey = playerChoices.fountainSourceKey?.takeIf { it in handKeys },
            necromancerPickKey = playerChoices.necromancerPickKey?.takeIf { necromancerStillInHand },
        )
        return copy(jokerAssignments = newJokerAssignments, playerChoices = newChoices)
    }

    class Factory(
        private val launchData: SandboxLaunchData,
        private val cardLookup: CardLookup,
        private val engine: ScoringEngine,
        private val solver: OptimalSolver,
        private val handCardRepo: HandCardRepository? = null,
        private val roundRepo: RoundRepository? = null,
        private val gameRepo: GameRepository? = null,
        private val profileRepo: ProfileRepository? = null,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
            return SandboxViewModel(
                launchData = launchData,
                cardLookup = cardLookup,
                engine = engine,
                optimalSolver = solver,
                handCardRepo = handCardRepo,
                roundRepo = roundRepo,
                gameRepo = gameRepo,
                profileRepo = profileRepo,
            ) as T
        }
    }
}
