package de.morzo.realmscore.ui.game

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import de.morzo.realmscore.data.cards.CardLookup
import de.morzo.realmscore.domain.game.CaptureOrdering
import de.morzo.realmscore.domain.model.CardDefinition
import de.morzo.realmscore.domain.model.Suit
import de.morzo.realmscore.domain.repository.GameRepository
import de.morzo.realmscore.domain.repository.HandCardEntry
import de.morzo.realmscore.domain.repository.HandCardRepository
import de.morzo.realmscore.domain.repository.ProfileRepository
import de.morzo.realmscore.domain.repository.RoundRepository
import de.morzo.realmscore.domain.scoring.JokerAssignment
import de.morzo.realmscore.domain.scoring.PlayerChoices
import de.morzo.realmscore.domain.scoring.ScoringEngine
import de.morzo.realmscore.domain.scoring.ScoringInput
import de.morzo.realmscore.domain.scoring.solver.OptimalSolver
import de.morzo.realmscore.ui.handentry.PLAYER_HAND_SLOT_COUNT
import de.morzo.realmscore.ui.handentry.PlayerHandEntryUiState
import de.morzo.realmscore.ui.sandbox.CardSlot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val NECROMANCER_KEY = "necromancer"

/** One player chip in the capture dropdown. */
data class CapturePlayer(
    val profileId: String,
    val name: String,
    val colorArgb: Int,
    val captured: Boolean,
)

data class RoundCaptureUiState(
    val isLoading: Boolean = true,
    val roundNumber: Int = 0,
    val orderedPlayers: List<CapturePlayer> = emptyList(),
    val currentProfileId: String? = null,
    /** UI shape consumed by the shared PlayerHandCaptureContent for the current player. */
    val current: PlayerHandEntryUiState = PlayerHandEntryUiState(isLoading = false),
) {
    val allCaptured: Boolean
        get() = orderedPlayers.isNotEmpty() && orderedPlayers.all { it.captured }
}

/**
 * Drives the Phase 18.1 full-screen round capture: one ViewModel orchestrates all players of a
 * round, holding an in-memory draft per player so switching between them (via the dropdown) never
 * loses entered cards. Submitting saves the current hand, records its scan position and advances to
 * the next not-yet-captured player; once everyone is captured it persists the scan order and
 * signals the caller to move on to the reveal.
 */
class RoundCaptureViewModel(
    private val cardLookup: CardLookup,
    private val handCardRepo: HandCardRepository,
    private val profileRepo: ProfileRepository,
    private val gameRepo: GameRepository,
    private val roundRepo: RoundRepository,
    private val engine: ScoringEngine,
    private val optimalSolver: OptimalSolver,
    private val roundId: String,
) : ViewModel() {

    val allCards: List<CardDefinition> = cardLookup.getAll()

    private data class Draft(
        val slots: List<CardSlot> = List(PLAYER_HAND_SLOT_COUNT) { CardSlot.Empty },
        val jokerAssignments: Map<String, JokerAssignment> = emptyMap(),
        val playerChoices: PlayerChoices = PlayerChoices(),
    )

    private var gameId: String = ""
    private val drafts = mutableMapOf<String, Draft>()
    private val nameById = mutableMapOf<String, String>()
    private val colorById = mutableMapOf<String, Int>()
    private var orderedIds: List<String> = emptyList()
    private val captured = mutableSetOf<String>()
    // Scan order: 0-based position assigned the first time a player is submitted.
    private val scanOrder = LinkedHashMap<String, Int>()

    private var isSaving = false
    private var isOptimalRunning = false

    private val _uiState = MutableStateFlow(RoundCaptureUiState())
    val uiState: StateFlow<RoundCaptureUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val round = roundRepo.getRoundById(roundId) ?: error("Round not found: $roundId")
            gameId = round.gameId
            val participants = gameRepo.getParticipants(round.gameId)

            // Default order: previous round's scan order (lastScanOrder asc), brand-new players
            // (null) last by seatOrder.
            val ordered = CaptureOrdering.order(participants)

            ordered.forEach { participant ->
                val profile = profileRepo.getById(participant.profileId) ?: return@forEach
                nameById[profile.id] = profile.name
                colorById[profile.id] = profile.colorArgb
                drafts[profile.id] = loadDraft(profile.id)
            }
            orderedIds = ordered.map { it.profileId }.filter { drafts.containsKey(it) }
            // Already fully recorded players count as captured (e.g. when re-entering to correct).
            orderedIds.forEach { id ->
                if (drafts.getValue(id).slots.count { it is CardSlot.Filled } == PLAYER_HAND_SLOT_COUNT) {
                    captured += id
                }
            }
            val first = orderedIds.firstOrNull { it !in captured } ?: orderedIds.firstOrNull()

            _uiState.update {
                it.copy(
                    isLoading = false,
                    roundNumber = round.roundNumber,
                    currentProfileId = first,
                )
            }
            rebuild()
        }
    }

    private suspend fun loadDraft(profileId: String): Draft {
        val existing = handCardRepo.getHand(roundId, profileId) ?: return Draft()
        val slots = MutableList<CardSlot>(PLAYER_HAND_SLOT_COUNT) { CardSlot.Empty }
        existing.cards.forEach { entry ->
            val card = cardLookup.getByKey(entry.cardKey) ?: return@forEach
            if (entry.position in 0 until PLAYER_HAND_SLOT_COUNT) {
                slots[entry.position] = CardSlot.Filled(card)
            }
        }
        val jokerAssignments = existing.cards
            .filter { it.cardKey != NECROMANCER_KEY }
            .mapNotNull { entry ->
                val target = entry.jokerTargetCardKey ?: return@mapNotNull null
                val suit = entry.jokerTargetSuit?.let { runCatching { Suit.valueOf(it) }.getOrNull() }
                entry.cardKey to JokerAssignment(
                    jokerKey = entry.cardKey,
                    targetCardKey = target,
                    targetSuit = suit,
                )
            }.toMap()
        val necromancerPickKey = existing.cards
            .firstOrNull { it.cardKey == NECROMANCER_KEY }
            ?.jokerTargetCardKey
        return Draft(
            slots = slots,
            jokerAssignments = jokerAssignments,
            playerChoices = PlayerChoices(necromancerPickKey = necromancerPickKey),
        )
    }

    /** Recomputes [RoundCaptureUiState.orderedPlayers] and [RoundCaptureUiState.current]. */
    private fun rebuild() {
        val currentId = _uiState.value.currentProfileId
        val players = orderedIds.map { id ->
            CapturePlayer(
                profileId = id,
                name = nameById[id] ?: "",
                colorArgb = colorById[id] ?: 0,
                captured = id in captured,
            )
        }
        _uiState.update { it.copy(orderedPlayers = players, current = buildCurrent(currentId)) }
    }

    private fun buildCurrent(profileId: String?): PlayerHandEntryUiState {
        if (profileId == null) return PlayerHandEntryUiState(isLoading = false)
        val draft = drafts[profileId] ?: Draft()
        val usedByOthers = drafts.asSequence()
            .filter { it.key != profileId }
            .flatMap { (_, d) -> d.slots.asSequence().mapNotNull { (it as? CardSlot.Filled)?.card?.key } }
            .toSet()
        return PlayerHandEntryUiState(
            isLoading = false,
            playerName = nameById[profileId] ?: "",
            slots = draft.slots,
            jokerAssignments = draft.jokerAssignments,
            playerChoices = draft.playerChoices,
            cardsUsedByOthers = usedByOthers,
            isOptimalRunning = isOptimalRunning,
            isSaving = isSaving,
        )
    }

    private fun updateCurrentDraft(transform: (Draft) -> Draft) {
        val id = _uiState.value.currentProfileId ?: return
        val current = drafts[id] ?: Draft()
        drafts[id] = transform(current).pruned()
        rebuild()
    }

    fun switchToPlayer(profileId: String) {
        if (profileId !in drafts) return
        _uiState.update { it.copy(currentProfileId = profileId) }
        rebuild()
    }

    fun setCardInSlot(slotIndex: Int, card: CardDefinition) {
        if (slotIndex !in 0 until PLAYER_HAND_SLOT_COUNT) return
        updateCurrentDraft { draft ->
            draft.copy(slots = draft.slots.toMutableList().also { it[slotIndex] = CardSlot.Filled(card) })
        }
    }

    fun clearSlot(slotIndex: Int) {
        if (slotIndex !in 0 until PLAYER_HAND_SLOT_COUNT) return
        updateCurrentDraft { draft ->
            draft.copy(slots = draft.slots.toMutableList().also { it[slotIndex] = CardSlot.Empty })
        }
    }

    fun setJokerAssignment(jokerKey: String, assignment: JokerAssignment?) {
        updateCurrentDraft { draft ->
            val newAssignments = draft.jokerAssignments.toMutableMap()
            if (assignment == null) newAssignments.remove(jokerKey) else newAssignments[jokerKey] = assignment
            draft.copy(jokerAssignments = newAssignments)
        }
    }

    fun setNecromancerPick(cardKey: String) {
        updateCurrentDraft { draft ->
            draft.copy(playerChoices = draft.playerChoices.copy(necromancerPickKey = cardKey))
        }
    }

    fun clearNecromancerPick() {
        updateCurrentDraft { draft ->
            draft.copy(playerChoices = draft.playerChoices.copy(necromancerPickKey = null))
        }
    }

    fun applyOptimal() {
        val id = _uiState.value.currentProfileId ?: return
        val draft = drafts[id] ?: return
        val filled = draft.slots.mapNotNull { (it as? CardSlot.Filled)?.card }
        if (filled.isEmpty()) return
        val seed = ScoringInput(
            hand = filled,
            jokerAssignments = draft.jokerAssignments,
            playerChoices = draft.playerChoices,
        )
        isOptimalRunning = true
        rebuild()
        viewModelScope.launch {
            val best = withContext(Dispatchers.Default) { optimalSolver.findOptimal(seed) }
            drafts[id] = (drafts[id] ?: draft).copy(
                jokerAssignments = best.bestInput.jokerAssignments,
                playerChoices = best.bestInput.playerChoices,
            )
            isOptimalRunning = false
            rebuild()
        }
    }

    /**
     * Saves the current player's hand, records its scan position and advances to the next
     * not-yet-captured player. If everyone is captured, persists the scan order and calls [onAllDone].
     */
    fun submitCurrentAndAdvance(onAllDone: () -> Unit) {
        val id = _uiState.value.currentProfileId ?: return
        val draft = drafts[id] ?: return
        if (!draft.canSubmit() || isSaving) return
        isSaving = true
        rebuild()
        viewModelScope.launch {
            val filled = draft.slots.mapNotNull { (it as? CardSlot.Filled)?.card }
            val input = ScoringInput(
                hand = filled,
                jokerAssignments = draft.jokerAssignments,
                playerChoices = draft.playerChoices,
            )
            val totalScore = withContext(Dispatchers.Default) { engine.score(input).totalScore }
            val entries = draft.slots.mapIndexedNotNull { idx, slot ->
                val card = (slot as? CardSlot.Filled)?.card ?: return@mapIndexedNotNull null
                val assignment = draft.jokerAssignments[card.key]
                val targetCardKey = if (card.key == NECROMANCER_KEY) {
                    draft.playerChoices.necromancerPickKey
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
                profileId = id,
                cards = entries,
                totalScore = totalScore,
            )
            captured += id
            if (id !in scanOrder) scanOrder[id] = scanOrder.size

            isSaving = false
            val next = orderedIds.firstOrNull { it !in captured }
            if (next == null) {
                gameRepo.updateScanOrder(gameId, scanOrder.toMap())
                rebuild()
                onAllDone()
            } else {
                _uiState.update { it.copy(currentProfileId = next) }
                rebuild()
            }
        }
    }

    private fun Draft.canSubmit(): Boolean {
        val filled = slots.mapNotNull { (it as? CardSlot.Filled)?.card }
        if (filled.size != PLAYER_HAND_SLOT_COUNT) return false
        val jokers = filled.filter { it.isJoker }
        val allJokersResolved = jokers.all { joker ->
            jokerAssignments[joker.key]?.targetCardKey != null
        }
        return allJokersResolved
    }

    private fun Draft.pruned(): Draft {
        val handKeys = slots.mapNotNull { (it as? CardSlot.Filled)?.card?.key }.toSet()
        val newAssignments = jokerAssignments.filterKeys { it in handKeys }
        val necromancerStillInHand = NECROMANCER_KEY in handKeys
        val newChoices = playerChoices.copy(
            islandTargetKey = playerChoices.islandTargetKey?.takeIf { it in handKeys },
            fountainSourceKey = playerChoices.fountainSourceKey?.takeIf { it in handKeys },
            necromancerPickKey = playerChoices.necromancerPickKey?.takeIf { necromancerStillInHand },
        )
        return copy(jokerAssignments = newAssignments, playerChoices = newChoices)
    }

    fun necromancerCandidates(handKeys: Set<String>): List<CardDefinition> =
        cardLookup.getNecromancerCandidates(handKeys = handKeys)

    class Factory(
        private val cardLookup: CardLookup,
        private val handCardRepo: HandCardRepository,
        private val profileRepo: ProfileRepository,
        private val gameRepo: GameRepository,
        private val roundRepo: RoundRepository,
        private val engine: ScoringEngine,
        private val optimalSolver: OptimalSolver,
        private val roundId: String,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return RoundCaptureViewModel(
                cardLookup = cardLookup,
                handCardRepo = handCardRepo,
                profileRepo = profileRepo,
                gameRepo = gameRepo,
                roundRepo = roundRepo,
                engine = engine,
                optimalSolver = optimalSolver,
                roundId = roundId,
            ) as T
        }
    }
}
