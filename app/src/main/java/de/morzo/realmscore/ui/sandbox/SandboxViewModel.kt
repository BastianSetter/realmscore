package de.morzo.realmscore.ui.sandbox

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import de.morzo.realmscore.data.cards.CardLookup
import de.morzo.realmscore.domain.model.CardDefinition
import de.morzo.realmscore.domain.model.Suit
import de.morzo.realmscore.domain.model.FavoriteCard
import de.morzo.realmscore.domain.model.SandboxFavorite
import de.morzo.realmscore.domain.repository.GameRepository
import de.morzo.realmscore.domain.repository.HandCardRepository
import de.morzo.realmscore.domain.repository.ProfileRepository
import de.morzo.realmscore.domain.repository.RoundRepository
import de.morzo.realmscore.domain.repository.SandboxFavoriteRepository
import de.morzo.realmscore.domain.scoring.JokerAssignment
import de.morzo.realmscore.domain.scoring.ScoringEngine
import de.morzo.realmscore.domain.scoring.ScoringInput
import de.morzo.realmscore.domain.scoring.ScoringResult
import de.morzo.realmscore.domain.scoring.solver.OptimalSolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

const val SANDBOX_SLOT_COUNT = 7

private const val NECROMANCER_KEY = "necromancer"

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

/**
 * Serializable-by-value snapshot of a Sandbox hand (Phase 22). Carries only card keys plus joker
 * assignments (which now include the Necromancer pull), so it can pre-fill another [SandboxViewModel]
 * (Multi-Hand left column) or be turned into a [FavoriteCard] list.
 */
data class HandSnapshot(
    val slotKeys: List<String?>,
    val jokerAssignments: Map<String, JokerAssignment>,
)

data class SandboxUiState(
    val slots: List<CardSlot> = List(SANDBOX_SLOT_COUNT) { CardSlot.Empty },
    val jokerAssignments: Map<String, JokerAssignment> = emptyMap(),
    val scoringResult: ScoringResult? = null,
    val optimalRunning: Boolean = false,
    val originBanner: OriginBanner? = null,
    val discardCards: List<CardDefinition> = emptyList(),
    val discardScanned: Boolean = false,
    val isLoadingLaunchData: Boolean = false,
    // Favorite/hand naming (spec 25.6). [favoriteId] non-null ⇒ this exact hand is persisted as a
    // favorite (filled star); any hand edit clears the link. [handName] is the free-text name shown
    // in the header, kept even while unsaved so it sticks when the hand is later starred.
    val favoriteId: String? = null,
    val favoriteNumber: Int? = null,
    val handName: String? = null,
) {
    val score: Int get() = scoringResult?.totalScore ?: 0

    val filledCards: List<CardDefinition>
        get() = slots.mapNotNull { (it as? CardSlot.Filled)?.card }

    /** A favorite may only be saved from a full, 7-card hand (Phase 22). */
    val canSaveFavorite: Boolean
        get() = filledCards.size == SANDBOX_SLOT_COUNT

    /** Whether the current hand is currently persisted as a favorite (drives the star toggle). */
    val isFavorite: Boolean get() = favoriteId != null

    /**
     * Every card needing a generic joker choice row (substitution jokers + Island/Fountain). The
     * Necromancer is a JokerType too but renders as its own dedicated row in the joker section
     * (with a full card picker), so it is excluded here.
     */
    val jokerCardsInHand: List<CardDefinition>
        get() = filledCards.filter { it.jokerType != null && it.key != NECROMANCER_KEY }

    val necromancerInHand: Boolean
        get() = filledCards.any { it.key == NECROMANCER_KEY }
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
    private val favoriteRepo: SandboxFavoriteRepository?,
) : ViewModel() {

    val allCards: List<CardDefinition> = cardLookup.getAll()

    private val _uiState = MutableStateFlow(SandboxUiState())
    val uiState: StateFlow<SandboxUiState> = _uiState.asStateFlow()

    /** Emits the favorite number each time the star toggle persists a hand, for the snackbar. */
    private val _favoriteSaved = MutableSharedFlow<Int>(extraBufferCapacity = 1)
    val favoriteSaved: SharedFlow<Int> = _favoriteSaved.asSharedFlow()

    init {
        when (val data = launchData) {
            is SandboxLaunchData.Empty -> Unit
            is SandboxLaunchData.FromRound -> {
                _uiState.update { it.copy(isLoadingLaunchData = true) }
                viewModelScope.launch { loadFromRound(data) }
            }
            is SandboxLaunchData.FromFavorite -> {
                _uiState.update { it.copy(isLoadingLaunchData = true) }
                viewModelScope.launch { loadFromFavorite(data.favoriteId) }
            }
            is SandboxLaunchData.Prefilled -> {
                _uiState.update { it.applySnapshot(data.snapshot) }
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

        // Every chosen target — substitution jokers, Island/Fountain and the Necromancer pull — is
        // persisted on its own HandCard's jokerTargetCardKey column, so they all rebuild uniformly
        // into joker assignments keyed by their card.
        val jokerAssignments: Map<String, JokerAssignment> = orderedEntries
            .filter { it.jokerTargetCardKey != null }
            .associate { entry ->
                entry.cardKey to JokerAssignment(
                    jokerKey = entry.cardKey,
                    targetCardKey = entry.jokerTargetCardKey,
                    targetSuit = entry.jokerTargetSuit
                        ?.let { runCatching { Suit.valueOf(it) }.getOrNull() },
                )
            }

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

    private suspend fun loadFromFavorite(favoriteId: String) {
        val repo = favoriteRepo ?: return clearLoading()
        val favorite = repo.getById(favoriteId) ?: return clearLoading()
        _uiState.update {
            it.applySnapshot(favorite.toSnapshot()).copy(
                isLoadingLaunchData = false,
                favoriteId = favorite.id,
                favoriteNumber = favorite.number,
                handName = favorite.name,
            )
        }
    }

    /** Snapshot of the current hand, for the Multi-Hand left column or favorite serialization. */
    fun currentSnapshot(): HandSnapshot = _uiState.value.toSnapshot()

    /** Replaces the current hand with [snapshot] (Multi-Hand copy/swap). */
    fun applyHandSnapshot(snapshot: HandSnapshot) {
        _uiState.update { it.applySnapshot(snapshot) }
    }

    /**
     * Star toggle (spec 25.6): if the current hand is already a favorite it is removed; otherwise the
     * full hand is persisted (with the current [SandboxUiState.handName]) and the link kept so the
     * star stays filled. Emits the favorite number on save for the confirmation snackbar.
     */
    fun toggleFavorite() {
        val repo = favoriteRepo ?: return
        val state = _uiState.value
        val existingId = state.favoriteId
        if (existingId != null) {
            _uiState.update { it.copy(favoriteId = null, favoriteNumber = null) }
            viewModelScope.launch { repo.delete(existingId) }
            return
        }
        if (!state.canSaveFavorite) return
        val cards = state.toFavoriteCards()
        val name = state.handName
        viewModelScope.launch {
            val favorite = repo.save(cards, name)
            _uiState.update { it.copy(favoriteId = favorite.id, favoriteNumber = favorite.number) }
            _favoriteSaved.emit(favorite.number)
        }
    }

    /** Renames the current hand; persists to the linked favorite when one exists (spec 25.6). */
    fun renameHand(name: String?) {
        val clean = name?.takeIf { it.isNotBlank() }
        _uiState.update { it.copy(handName = clean) }
        val id = _uiState.value.favoriteId ?: return
        val repo = favoriteRepo ?: return
        viewModelScope.launch { repo.updateName(id, clean) }
    }

    fun loadFavorite(favorite: SandboxFavorite) {
        _uiState.update {
            it.applySnapshot(favorite.toSnapshot()).copy(
                favoriteId = favorite.id,
                favoriteNumber = favorite.number,
                handName = favorite.name,
            )
        }
    }

    private fun SandboxUiState.toSnapshot(): HandSnapshot = HandSnapshot(
        slotKeys = slots.map { (it as? CardSlot.Filled)?.card?.key },
        jokerAssignments = jokerAssignments,
    )

    private fun SandboxUiState.toFavoriteCards(): List<FavoriteCard> =
        slots.mapIndexedNotNull { index, slot ->
            val card = (slot as? CardSlot.Filled)?.card ?: return@mapIndexedNotNull null
            val assignment = jokerAssignments[card.key]
            FavoriteCard(
                position = index,
                cardKey = card.key,
                jokerTargetCardKey = assignment?.targetCardKey,
                jokerTargetSuit = assignment?.targetSuit?.name,
            )
        }

    private fun SandboxFavorite.toSnapshot(): HandSnapshot {
        val slotKeys = MutableList<String?>(SANDBOX_SLOT_COUNT) { null }
        val assignments = mutableMapOf<String, JokerAssignment>()
        handCards.forEach { fav ->
            if (fav.position in 0 until SANDBOX_SLOT_COUNT) slotKeys[fav.position] = fav.cardKey
            if (fav.jokerTargetCardKey != null || fav.jokerTargetSuit != null) {
                assignments[fav.cardKey] = JokerAssignment(
                    jokerKey = fav.cardKey,
                    targetCardKey = fav.jokerTargetCardKey,
                    targetSuit = fav.jokerTargetSuit?.let { runCatching { Suit.valueOf(it) }.getOrNull() },
                )
            }
        }
        return HandSnapshot(slotKeys, assignments)
    }

    private fun SandboxUiState.applySnapshot(snapshot: HandSnapshot): SandboxUiState {
        val slots = snapshot.slotKeys.take(SANDBOX_SLOT_COUNT).map { key ->
            val card = key?.let { cardLookup.getByKey(it) }
            if (card != null) CardSlot.Filled(card) else CardSlot.Empty
        }
        val padded = (slots + List(SANDBOX_SLOT_COUNT) { CardSlot.Empty }).take(SANDBOX_SLOT_COUNT)
        return copy(
            slots = padded,
            jokerAssignments = snapshot.jokerAssignments,
            originBanner = null,
        ).unlinkFavorite().pruneStaleSelections().recomputeScore()
    }

    /**
     * Drops the favorite link (spec 25.6): once the hand is edited it no longer matches the persisted
     * snapshot, so the star empties. The free-text [SandboxUiState.handName] is kept.
     */
    private fun SandboxUiState.unlinkFavorite(): SandboxUiState =
        if (favoriteId == null) this else copy(favoriteId = null, favoriteNumber = null)

    fun setCardInSlot(slotIndex: Int, card: CardDefinition) {
        if (slotIndex !in 0 until SANDBOX_SLOT_COUNT) return
        _uiState.update { state ->
            val newSlots = state.slots.toMutableList().also { it[slotIndex] = CardSlot.Filled(card) }
            state.copy(slots = newSlots).unlinkFavorite().pruneStaleSelections().recomputeScore()
        }
    }

    fun clearSlot(slotIndex: Int) {
        if (slotIndex !in 0 until SANDBOX_SLOT_COUNT) return
        _uiState.update { state ->
            val newSlots = state.slots.toMutableList().also { it[slotIndex] = CardSlot.Empty }
            state.copy(slots = newSlots).unlinkFavorite().pruneStaleSelections().recomputeScore()
        }
    }

    fun setJokerAssignment(jokerKey: String, assignment: JokerAssignment?) {
        _uiState.update { state ->
            val newAssignments = state.jokerAssignments.toMutableMap()
            if (assignment == null) newAssignments.remove(jokerKey) else newAssignments[jokerKey] = assignment
            state.copy(jokerAssignments = newAssignments).unlinkFavorite().recomputeScore()
        }
    }

    fun setNecromancerPick(cardKey: String) =
        setJokerAssignment(NECROMANCER_KEY, JokerAssignment(NECROMANCER_KEY, cardKey))

    fun clearNecromancerPick() = setJokerAssignment(NECROMANCER_KEY, null)

    fun applyOptimal() {
        val current = _uiState.value
        val seed = current.toScoringInput()
        _uiState.update { it.copy(optimalRunning = true) }
        viewModelScope.launch {
            val best = withContext(Dispatchers.Default) { optimalSolver.findOptimal(seed) }
            _uiState.update {
                it.copy(
                    jokerAssignments = best.bestInput.jokerAssignments,
                    scoringResult = best.bestResult,
                    optimalRunning = false,
                ).unlinkFavorite()
            }
        }
    }

    fun reset() {
        _uiState.update { SandboxUiState() }
    }

    private fun SandboxUiState.toScoringInput(): ScoringInput = ScoringInput(
        hand = filledCards,
        jokerAssignments = jokerAssignments,
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
        // Every assignment (jokers, Island, Fountain, Necromancer) is keyed by its hand card and
        // prunes away once that card leaves the hand.
        return copy(jokerAssignments = jokerAssignments.filterKeys { it in handKeys })
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
        private val favoriteRepo: SandboxFavoriteRepository? = null,
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
                favoriteRepo = favoriteRepo,
            ) as T
        }
    }
}
