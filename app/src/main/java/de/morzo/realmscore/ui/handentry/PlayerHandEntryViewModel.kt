package de.morzo.realmscore.ui.handentry

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import de.morzo.realmscore.data.cards.CardLookup
import de.morzo.realmscore.data.cards.CursedItemLookup
import de.morzo.realmscore.data.repository.HandScoringService
import de.morzo.realmscore.data.repository.RoundScoringContext
import de.morzo.realmscore.domain.game.HandRules
import de.morzo.realmscore.domain.model.CardDefinition
import de.morzo.realmscore.domain.model.CursedItem
import de.morzo.realmscore.domain.model.JokerType
import de.morzo.realmscore.domain.repository.HandCardEntry
import de.morzo.realmscore.domain.repository.HandCardRepository
import de.morzo.realmscore.domain.repository.ProfileRepository
import de.morzo.realmscore.domain.scoring.JokerAssignment
import de.morzo.realmscore.domain.scoring.solver.OptimalSolver
import de.morzo.realmscore.domain.scoring.toScoringChoices
import de.morzo.realmscore.ui.sandbox.CardSlot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Base-game hand size; games with the expansion's new suits use [HandRules]. */
const val PLAYER_HAND_SLOT_COUNT = HandRules.BASE_HAND

data class PlayerHandEntryUiState(
    val isLoading: Boolean = true,
    val playerName: String = "",
    val slots: List<CardSlot> = List(PLAYER_HAND_SLOT_COUNT) { CardSlot.Empty },
    val jokerAssignments: Map<String, JokerAssignment> = emptyMap(),
    val cardsUsedByOthers: Set<String> = emptySet(),
    // Whether this round's Mittelfeld has been captured. Gates the Necromancer optimiser (P2P §6 #5):
    // false → the pull can't be optimised, so it must be set manually first. Defaults true for
    // contexts without a Mittelfeld (e.g. the standalone hand entry).
    val mittelfeldScanned: Boolean = true,
    val isOptimalRunning: Boolean = false,
    val isSaving: Boolean = false,
    /**
     * Discard mode (Mittelfeld): the entry just records card identities, so joker resolution,
     * the Necromancer field and per-card scoring are suppressed. Used by RoundCaptureViewModel.
     */
    val isDiscard: Boolean = false,
    /**
     * Cards required to mark the entry complete (7 / 8 for a hand, the Mittelfeld target for the
     * discard). Up to [maxCardCount] may be entered (Phase 30: Kobold/Dschinn/Portal, discard overflow).
     */
    val requiredSlotCount: Int = PLAYER_HAND_SLOT_COUNT,
    val maxCardCount: Int = PLAYER_HAND_SLOT_COUNT,
    /** Phase 30: the game is played with the expansion's new suits (hand rules, Undead). */
    val newSuits: Boolean = false,
    /** Phase 30: the game records cursed items; [cursedItems] is the selectable list. */
    val cursedItemsEnabled: Boolean = false,
    val cursedItems: List<CursedItem> = emptyList(),
    val cursedItemKeys: Set<String> = emptySet(),
    /** Cursed items already recorded for another player this round (each exists once). */
    val cursedItemsUsedByOthers: Set<String> = emptySet(),
    val playerCount: Int = 0,
) {
    val filledCards: List<CardDefinition>
        get() = slots.mapNotNull { (it as? CardSlot.Filled)?.card }

    val cardsCount: Int get() = filledCards.size

    /** Wild substitution jokers only — these are mandatory to resolve before submit. */
    val jokersInHand: List<CardDefinition>
        get() = filledCards.filter { it.isJoker }

    /** The Necromancer in hand (base or expansion edition), if any. */
    val necromancerCard: CardDefinition?
        get() = filledCards.firstOrNull { it.jokerType == JokerType.NECROMANCER }

    /**
     * Every card needing a generic joker choice row (substitution jokers + Island/Fountain/Angel). The
     * Necromancer is a JokerType too, but renders as its own dedicated row in the joker section
     * (with a full card picker), so it is excluded here.
     */
    val jokerCardsInHand: List<CardDefinition>
        get() = filledCards.filter { it.jokerType != null && it.jokerType != JokerType.NECROMANCER }

    val necromancerInHand: Boolean
        get() = necromancerCard != null

    val allJokersResolved: Boolean
        get() = jokersInHand.all { joker ->
            val assignment = jokerAssignments[joker.key] ?: return@all false
            assignment.targetCardKey != null
        }

    /** Summed points of the selected cursed items. */
    val cursedPoints: Int
        get() = cursedItems.filter { it.key in cursedItemKeys }.sumOf { it.pointsFor(playerCount) }

    /** Whether the entered card count is legal (hand: incl. Kobold/Dschinn/Portal and the Necromancer pull). */
    val hasValidCardCount: Boolean
        get() = if (isDiscard) {
            cardsCount in requiredSlotCount..maxCardCount
        } else {
            val necromancer = necromancerCard
            HandRules.isValidHandSize(
                newSuits = newSuits,
                handKeys = filledCards.map { it.key },
                cursedItemKeys = cursedItemKeys,
                hasNecromancerPull = necromancer != null &&
                    jokerAssignments[necromancer.key]?.targetCardKey != null,
            )
        }

    val canSubmit: Boolean
        get() = hasValidCardCount && (isDiscard || allJokersResolved) && !isSaving
}

/** Builds the persisted entries of a hand draft: every target lives on its own card's entry. */
fun handEntriesOf(slots: List<CardSlot>, jokerAssignments: Map<String, JokerAssignment>): List<HandCardEntry> =
    slots.mapIndexedNotNull { idx, slot ->
        val card = (slot as? CardSlot.Filled)?.card ?: return@mapIndexedNotNull null
        val assignment = jokerAssignments[card.key]
        HandCardEntry(
            cardKey = card.key,
            position = idx,
            jokerTargetCardKey = assignment?.targetCardKey,
            jokerTargetSuit = assignment?.targetSuit?.name,
        )
    }

class PlayerHandEntryViewModel(
    private val cardLookup: CardLookup,
    private val cursedItemLookup: CursedItemLookup,
    private val handCardRepo: HandCardRepository,
    private val profileRepo: ProfileRepository,
    private val handScoring: HandScoringService,
    private val optimalSolver: OptimalSolver,
    private val roundId: String,
    private val profileId: String,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PlayerHandEntryUiState())
    val uiState: StateFlow<PlayerHandEntryUiState> = _uiState.asStateFlow()

    private var scoringContext: RoundScoringContext? = null

    /** The game's card pool (base, or with the expansion's new suits). */
    val allCards: List<CardDefinition>
        get() = cardLookup.cardsFor(_uiState.value.newSuits)

    init {
        viewModelScope.launch {
            val profile = profileRepo.getById(profileId)
                ?: error("Profile not found: $profileId")
            val ctx = handScoring.context(roundId).also { scoringContext = it }
            val existing = handCardRepo.getHand(roundId, profileId)
            val slotCount = HandRules.slotCount(ctx.newSuits, ctx.cursedItems)

            val slots: List<CardSlot> = MutableList<CardSlot>(slotCount) { CardSlot.Empty }
                .also { mut ->
                    existing?.cards?.forEach { entry ->
                        val card = cardLookup.getByKey(entry.cardKey) ?: return@forEach
                        if (entry.position in 0 until slotCount) {
                            mut[entry.position] = CardSlot.Filled(card)
                        }
                    }
                }
            // Every chosen target — substitution jokers, Island/Fountain/Angel and the Necromancer
            // pull — is persisted on its own card entry, so they all rebuild uniformly.
            val jokerAssignments = existing?.cards?.toScoringChoices()?.jokerAssignments ?: emptyMap()

            _uiState.update {
                it.copy(
                    isLoading = false,
                    playerName = profile.name,
                    slots = slots,
                    jokerAssignments = jokerAssignments,
                    requiredSlotCount = HandRules.minHand(ctx.newSuits),
                    maxCardCount = slotCount,
                    newSuits = ctx.newSuits,
                    cursedItemsEnabled = ctx.cursedItems,
                    cursedItems = if (ctx.cursedItems) cursedItemLookup.getAll() else emptyList(),
                    cursedItemKeys = existing?.cursedItemKeys?.toSet() ?: emptySet(),
                    playerCount = ctx.playerCount,
                    mittelfeldScanned = ctx.discardScanned || !ctx.newSuits,
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
        _uiState.update { state ->
            if (slotIndex !in state.slots.indices) return@update state
            val newSlots = state.slots.toMutableList().also { it[slotIndex] = CardSlot.Filled(card) }
            state.copy(slots = newSlots).pruneStaleSelections()
        }
    }

    fun clearSlot(slotIndex: Int) {
        _uiState.update { state ->
            if (slotIndex !in state.slots.indices) return@update state
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
        val necromancer = _uiState.value.necromancerCard ?: return
        setJokerAssignment(necromancer.key, JokerAssignment(necromancer.key, cardKey))
    }

    fun clearNecromancerPick() {
        val necromancer = _uiState.value.necromancerCard ?: return
        setJokerAssignment(necromancer.key, null)
    }

    fun toggleCursedItem(key: String) {
        _uiState.update { state ->
            val keys = if (key in state.cursedItemKeys) state.cursedItemKeys - key else state.cursedItemKeys + key
            state.copy(cursedItemKeys = keys)
        }
    }

    fun applyOptimal() {
        val current = _uiState.value
        val ctx = scoringContext ?: return
        if (current.filledCards.isEmpty()) return
        val seed = handScoring.input(handEntriesOf(current.slots, current.jokerAssignments), ctx) ?: return
        _uiState.update { it.copy(isOptimalRunning = true) }
        viewModelScope.launch {
            val best = withContext(Dispatchers.Default) { optimalSolver.findOptimal(seed) }
            _uiState.update {
                it.copy(
                    jokerAssignments = best.bestInput.jokerAssignments,
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
            handScoring.saveHand(
                roundId = roundId,
                profileId = profileId,
                entries = handEntriesOf(current.slots, current.jokerAssignments),
                cursedItemKeys = current.cursedItemKeys.toList(),
                context = scoringContext,
            )
            _uiState.update { it.copy(isSaving = false) }
            onSuccess()
        }
    }

    private fun PlayerHandEntryUiState.pruneStaleSelections(): PlayerHandEntryUiState {
        val handKeys = filledCards.map { it.key }.toSet()
        // Every assignment (jokers, Island, Fountain, Angel, Necromancer) is keyed by its hand card,
        // so dropping cards no longer in the hand prunes them all uniformly.
        return copy(jokerAssignments = jokerAssignments.filterKeys { it in handKeys })
    }

    class Factory(
        private val cardLookup: CardLookup,
        private val cursedItemLookup: CursedItemLookup,
        private val handCardRepo: HandCardRepository,
        private val profileRepo: ProfileRepository,
        private val handScoring: HandScoringService,
        private val optimalSolver: OptimalSolver,
        private val roundId: String,
        private val profileId: String,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return PlayerHandEntryViewModel(
                cardLookup = cardLookup,
                cursedItemLookup = cursedItemLookup,
                handCardRepo = handCardRepo,
                profileRepo = profileRepo,
                handScoring = handScoring,
                optimalSolver = optimalSolver,
                roundId = roundId,
                profileId = profileId,
            ) as T
        }
    }
}
