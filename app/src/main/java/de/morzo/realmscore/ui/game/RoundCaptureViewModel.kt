package de.morzo.realmscore.ui.game

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import de.morzo.realmscore.data.cards.CardLookup
import de.morzo.realmscore.domain.game.CaptureOrdering
import de.morzo.realmscore.domain.model.CardDefinition
import de.morzo.realmscore.domain.repository.GameRepository
import de.morzo.realmscore.domain.repository.HandCardEntry
import de.morzo.realmscore.domain.repository.HandCardRepository
import de.morzo.realmscore.domain.repository.ProfileRepository
import de.morzo.realmscore.domain.repository.RoundRepository
import de.morzo.realmscore.domain.repository.SettingsRepository
import de.morzo.realmscore.domain.scoring.JokerAssignment
import de.morzo.realmscore.domain.scoring.PlayerChoices
import de.morzo.realmscore.domain.scoring.ScoringEngine
import de.morzo.realmscore.domain.scoring.ScoringInput
import de.morzo.realmscore.domain.scoring.solver.OptimalSolver
import de.morzo.realmscore.domain.scoring.toScoringChoices
import de.morzo.realmscore.ui.handentry.PLAYER_HAND_SLOT_COUNT
import de.morzo.realmscore.ui.handentry.PlayerHandEntryUiState
import de.morzo.realmscore.ui.sandbox.CardSlot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val NECROMANCER_KEY = "necromancer"
private const val ISLAND_KEY = "island"
private const val FOUNTAIN_KEY = "fountain_of_life"

/** Sentinel id for the synthetic Mittelfeld (discard) entry in the capture rotation. */
private const val DISCARD_ID = "__discard__"

/** Neutral blue-grey dot for the Mittelfeld entry in the dropdown. */
private const val DISCARD_COLOR = 0xFF607D8B.toInt()

/** Mittelfeld card counts: 10 for a two-player game, 12 with more players. */
private const val DISCARD_SLOTS_TWO_PLAYERS = 10
private const val DISCARD_SLOTS_MULTI_PLAYER = 12

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
    /** UI shape consumed by the shared PlayerHandCaptureContent for the current entry. */
    val current: PlayerHandEntryUiState = PlayerHandEntryUiState(isLoading = false),
) {
    val allCaptured: Boolean
        get() = orderedPlayers.isNotEmpty() && orderedPlayers.all { it.captured }
}

/**
 * Drives the full-screen round capture (Phase 18.1): one ViewModel orchestrates every entry of a
 * round, holding an in-memory draft per entry so switching between them (via the dropdown) never
 * loses entered cards. Submitting saves the current entry and auto-advances to the next not-yet
 * captured one; once everything is captured it persists the scan order and signals the caller to
 * move on to the reveal.
 *
 * Phase 20: when the "Mittelfeld erfassen" setting is on, the central discard pile is added as an
 * extra entry (10 cards for two players, 12 otherwise) that is captured exactly like a player and
 * is mandatory before the reveal. It records card identities only — no jokers, Necromancer or
 * scoring — and its saved cards then scope the Necromancer pick for the player hands.
 */
class RoundCaptureViewModel(
    private val cardLookup: CardLookup,
    private val handCardRepo: HandCardRepository,
    private val profileRepo: ProfileRepository,
    private val gameRepo: GameRepository,
    private val roundRepo: RoundRepository,
    private val settingsRepo: SettingsRepository,
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
    // Scan order: 0-based position assigned the first time a player is submitted (excludes Mittelfeld).
    private val scanOrder = LinkedHashMap<String, Int>()

    private var isSaving = false
    private var isOptimalRunning = false

    private var discardEnabled = false
    private var discardSlotCount = DISCARD_SLOTS_MULTI_PLAYER

    // Saved discard state, kept in sync via the observer below; powers the Necromancer filtering.
    private var discardScanned = false
    private var discardCards: List<CardDefinition> = emptyList()

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
            val playerIds = ordered.map { it.profileId }.filter { drafts.containsKey(it) }

            discardEnabled = settingsRepo.discardCaptureEnabled.first()
            discardSlotCount =
                if (playerIds.size <= 2) DISCARD_SLOTS_TWO_PLAYERS else DISCARD_SLOTS_MULTI_PLAYER

            orderedIds = if (discardEnabled) {
                nameById[DISCARD_ID] = "Mittelfeld"
                colorById[DISCARD_ID] = DISCARD_COLOR
                drafts[DISCARD_ID] = loadDiscardDraft()
                // Mittelfeld first so it is known before the player hands (Necromancer filtering).
                listOf(DISCARD_ID) + playerIds
            } else {
                playerIds
            }

            // Already fully recorded entries count as captured (e.g. when re-entering to correct).
            orderedIds.forEach { id ->
                val filled = drafts.getValue(id).slots.count { it is CardSlot.Filled }
                if (filled == requiredCountFor(id)) captured += id
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

            launch {
                combine(
                    roundRepo.observeRoundById(roundId),
                    roundRepo.observeDiscardCards(roundId),
                ) { r, keys -> (r?.discardScanned ?: false) to keys }
                    .collect { (scanned, keys) ->
                        discardScanned = scanned
                        discardCards = keys.mapNotNull { cardLookup.getByKey(it) }
                        rebuild()
                    }
            }
        }
    }

    private fun requiredCountFor(id: String): Int =
        if (id == DISCARD_ID) discardSlotCount else PLAYER_HAND_SLOT_COUNT

    private suspend fun loadDraft(profileId: String): Draft {
        val existing = handCardRepo.getHand(roundId, profileId) ?: return Draft()
        val slots = MutableList<CardSlot>(PLAYER_HAND_SLOT_COUNT) { CardSlot.Empty }
        existing.cards.forEach { entry ->
            val card = cardLookup.getByKey(entry.cardKey) ?: return@forEach
            if (entry.position in 0 until PLAYER_HAND_SLOT_COUNT) {
                slots[entry.position] = CardSlot.Filled(card)
            }
        }
        // Necromancer pick and Island/Fountain choices are persisted on their own card entry's
        // jokerTargetCardKey column; the shared mapper sorts them back into playerChoices.
        val reconstructed = existing.cards.toScoringChoices()
        return Draft(
            slots = slots,
            jokerAssignments = reconstructed.jokerAssignments,
            playerChoices = reconstructed.playerChoices,
        )
    }

    private suspend fun loadDiscardDraft(): Draft {
        val keys = roundRepo.getDiscardCards(roundId)
        val slots = MutableList<CardSlot>(discardSlotCount) { CardSlot.Empty }
        keys.forEachIndexed { index, key ->
            if (index < discardSlotCount) {
                cardLookup.getByKey(key)?.let { slots[index] = CardSlot.Filled(it) }
            }
        }
        return Draft(slots = slots)
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
        val isDiscard = profileId == DISCARD_ID
        // A physical card can be in only one place, so exclude everything already placed elsewhere
        // (other hands and the Mittelfeld).
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
            isDiscard = isDiscard,
            requiredSlotCount = requiredCountFor(profileId),
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
        updateCurrentDraft { draft ->
            if (slotIndex !in draft.slots.indices) draft
            else draft.copy(slots = draft.slots.toMutableList().also { it[slotIndex] = CardSlot.Filled(card) })
        }
    }

    fun clearSlot(slotIndex: Int) {
        updateCurrentDraft { draft ->
            if (slotIndex !in draft.slots.indices) draft
            else draft.copy(slots = draft.slots.toMutableList().also { it[slotIndex] = CardSlot.Empty })
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

    fun setIslandTarget(cardKey: String?) {
        updateCurrentDraft { draft ->
            draft.copy(playerChoices = draft.playerChoices.copy(islandTargetKey = cardKey))
        }
    }

    fun setFountainSource(cardKey: String?) {
        updateCurrentDraft { draft ->
            draft.copy(playerChoices = draft.playerChoices.copy(fountainSourceKey = cardKey))
        }
    }

    fun applyOptimal() {
        val id = _uiState.value.currentProfileId ?: return
        if (id == DISCARD_ID) return
        val draft = drafts[id] ?: return
        val filled = draft.slots.mapNotNull { (it as? CardSlot.Filled)?.card }
        if (filled.isEmpty()) return
        val seed = ScoringInput(
            hand = filled,
            jokerAssignments = draft.jokerAssignments,
            playerChoices = draft.playerChoices,
            discardPile = discardCards,
            discardScanned = discardScanned,
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
     * Saves the current entry (a player's hand or the Mittelfeld), records the scan position for
     * real players and advances to the next not-yet-captured entry. If everything is captured,
     * persists the scan order and calls [onAllDone].
     */
    fun submitCurrentAndAdvance(onAllDone: () -> Unit) {
        val id = _uiState.value.currentProfileId ?: return
        val draft = drafts[id] ?: return
        if (!canSubmit(id) || isSaving) return
        isSaving = true
        rebuild()
        viewModelScope.launch {
            if (id == DISCARD_ID) {
                val keys = draft.slots.mapNotNull { (it as? CardSlot.Filled)?.card?.key }
                roundRepo.saveDiscardCards(roundId, keys)
            } else {
                saveHand(id, draft)
                if (id !in scanOrder) scanOrder[id] = scanOrder.size
            }
            captured += id

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

    private suspend fun saveHand(profileId: String, draft: Draft) {
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
            // Necromancer pick + Island/Fountain choices reuse the jokerTargetCardKey column on
            // their own card entry so the reconstruction path (BreakdownViewModel/RevealViewModel
            // via toScoringChoices) can rebuild playerChoices without a schema change.
            val targetCardKey = when (card.key) {
                NECROMANCER_KEY -> draft.playerChoices.necromancerPickKey
                ISLAND_KEY -> draft.playerChoices.islandTargetKey
                FOUNTAIN_KEY -> draft.playerChoices.fountainSourceKey
                else -> assignment?.targetCardKey
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
    }

    private fun canSubmit(id: String): Boolean {
        val draft = drafts[id] ?: return false
        val filled = draft.slots.mapNotNull { (it as? CardSlot.Filled)?.card }
        if (filled.size != requiredCountFor(id)) return false
        if (id == DISCARD_ID) return true
        val jokers = filled.filter { it.isJoker }
        return jokers.all { joker -> draft.jokerAssignments[joker.key]?.targetCardKey != null }
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
        cardLookup.getNecromancerCandidates(
            handKeys = handKeys,
            discardScanned = discardScanned,
            discardKeys = discardCards.map { it.key }.toSet(),
        )

    class Factory(
        private val cardLookup: CardLookup,
        private val handCardRepo: HandCardRepository,
        private val profileRepo: ProfileRepository,
        private val gameRepo: GameRepository,
        private val roundRepo: RoundRepository,
        private val settingsRepo: SettingsRepository,
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
                settingsRepo = settingsRepo,
                engine = engine,
                optimalSolver = optimalSolver,
                roundId = roundId,
            ) as T
        }
    }
}
