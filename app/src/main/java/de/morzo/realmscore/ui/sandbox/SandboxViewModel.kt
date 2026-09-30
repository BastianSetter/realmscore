package de.morzo.realmscore.ui.sandbox

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import de.morzo.realmscore.data.cards.CardLookup
import de.morzo.realmscore.domain.game.HandRules
import de.morzo.realmscore.domain.model.JokerType
import de.morzo.realmscore.domain.scoring.joker.JokerTargets
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

/** Base-game sandbox hand size; with the expansion's new suits see [HandRules.slotCount]. */
const val SANDBOX_SLOT_COUNT = HandRules.BASE_HAND

/** Default player count of a fresh sandbox (only the Dschinn reads it). */
private const val SANDBOX_DEFAULT_PLAYERS = 4

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
    /** Phase 30: sandbox plays with the expansion's new suits (own switch, independent of Settings). */
    val newSuits: Boolean = false,
    /** Phase 30: player count assumed for the Dschinn (+10 per opponent). */
    val playerCount: Int = SANDBOX_DEFAULT_PLAYERS,
) {
    val score: Int get() = scoringResult?.totalScore ?: 0

    val filledCards: List<CardDefinition>
        get() = slots.mapNotNull { (it as? CardSlot.Filled)?.card }

    /** A favorite may only be saved from a full hand (Phase 22; 8+ cards with the new suits). */
    val canSaveFavorite: Boolean
        get() = filledCards.size >= HandRules.minHand(newSuits)

    /** Whether the hand holds its required cards (drives the auto-advance out of KartenPick). */
    val isHandComplete: Boolean
        get() = filledCards.size >= HandRules.minHand(newSuits)

    /** Whether the current hand is currently persisted as a favorite (drives the star toggle). */
    val isFavorite: Boolean get() = favoriteId != null

    /**
     * Every card needing a generic joker choice row (substitution jokers + Island/Fountain). The
     * Necromancer is a JokerType too but renders as its own dedicated row in the joker section
     * (with a full card picker), so it is excluded here.
     */
    val jokerCardsInHand: List<CardDefinition>
        get() = filledCards.filter { it.jokerType != null && it.jokerType != JokerType.NECROMANCER }

    /** The Necromancer in hand (base or expansion edition), if any. */
    val necromancerCard: CardDefinition?
        get() = filledCards.firstOrNull { it.jokerType == JokerType.NECROMANCER }

    val necromancerInHand: Boolean
        get() = necromancerCard != null
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

    /** The sandbox's card pool (base, or with the expansion's new suits). */
    val allCards: List<CardDefinition>
        get() = cardLookup.cardsFor(_uiState.value.newSuits)

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
        val slotCount = slotCountFor(game.newSuitsEnabled)
        val slots = MutableList<CardSlot>(slotCount) { CardSlot.Empty }
        cards.forEachIndexed { idx, card ->
            if (idx < slotCount) slots[idx] = CardSlot.Filled(card)
        }
        val playerCount = gRepo.getParticipants(data.gameId).size

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
                newSuits = game.newSuitsEnabled,
                playerCount = playerCount,
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
        val slotCount = slotCountFor(handCards.any { JokerTargets.isExpansion(it.cardKey) })
        val slotKeys = MutableList<String?>(slotCount) { null }
        val assignments = mutableMapOf<String, JokerAssignment>()
        handCards.forEach { fav ->
            if (fav.position in 0 until slotCount) slotKeys[fav.position] = fav.cardKey
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
        // A hand holding expansion cards switches the sandbox to the new suits (Phase 30).
        val snapshotNewSuits = newSuits || snapshot.slotKeys.any { it != null && JokerTargets.isExpansion(it) }
        val slotCount = slotCountFor(snapshotNewSuits)
        val slots = snapshot.slotKeys.take(slotCount).map { key ->
            val card = key?.let { cardLookup.getByKey(it) }
            if (card != null) CardSlot.Filled(card) else CardSlot.Empty
        }
        val padded = (slots + List(slotCount) { CardSlot.Empty }).take(slotCount)
        return copy(
            newSuits = snapshotNewSuits,
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
        _uiState.update { state ->
            if (slotIndex !in state.slots.indices) return@update state
            val newSlots = state.slots.toMutableList().also { it[slotIndex] = CardSlot.Filled(card) }
            state.copy(slots = newSlots).unlinkFavorite().pruneStaleSelections().recomputeScore()
        }
    }

    fun clearSlot(slotIndex: Int) {
        _uiState.update { state ->
            if (slotIndex !in state.slots.indices) return@update state
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

    fun setNecromancerPick(cardKey: String) {
        val necromancer = _uiState.value.necromancerCard ?: return
        setJokerAssignment(necromancer.key, JokerAssignment(necromancer.key, cardKey))
    }

    fun clearNecromancerPick() {
        val necromancer = _uiState.value.necromancerCard ?: return
        setJokerAssignment(necromancer.key, null)
    }

    /**
     * Phase 30: switch the sandbox between the base game and the expansion's new suits. Cards that
     * are not part of the new pool (expansion cards when switching off, replaced base cards when
     * switching on) are removed and the slot count follows the hand size.
     */
    fun setNewSuits(enabled: Boolean) {
        _uiState.update { state ->
            if (state.newSuits == enabled) return@update state
            val poolKeys = cardLookup.cardsFor(enabled).map { it.key }.toSet()
            val kept = state.slots.map { slot ->
                val card = (slot as? CardSlot.Filled)?.card
                if (card != null && card.key in poolKeys) slot else CardSlot.Empty
            }
            val slotCount = slotCountFor(enabled)
            val filled = kept.filterIsInstance<CardSlot.Filled>()
            val resized = (filled + List(slotCount) { CardSlot.Empty }).take(slotCount)
            state.copy(newSuits = enabled, slots = resized)
                .unlinkFavorite().pruneStaleSelections().recomputeScore()
        }
    }

    /** Phase 30: player count assumed for the Dschinn. */
    fun setPlayerCount(count: Int) {
        _uiState.update { it.copy(playerCount = count.coerceIn(2, 6)).recomputeScore() }
    }

    private fun slotCountFor(newSuits: Boolean): Int = HandRules.slotCount(newSuits, cursedItems = false)

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
        _uiState.update { SandboxUiState(newSuits = it.newSuits, playerCount = it.playerCount, slots = List(slotCountFor(it.newSuits)) { CardSlot.Empty }) }
    }

    private fun SandboxUiState.toScoringInput(): ScoringInput = ScoringInput(
        hand = filledCards,
        jokerAssignments = jokerAssignments,
        discardPile = discardCards,
        discardScanned = discardScanned,
        newSuits = newSuits,
        playerCount = playerCount,
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
