package de.morzo.realmscore.ui.handentry

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.morzo.realmscore.domain.model.CardDefinition
import de.morzo.realmscore.domain.scoring.JokerAssignment
import de.morzo.realmscore.ui.components.CardPicker
import de.morzo.realmscore.ui.components.CardPickerContent
import de.morzo.realmscore.ui.sandbox.CardSlot
import de.morzo.realmscore.ui.sandbox.components.HandSlotsRow
import de.morzo.realmscore.ui.sandbox.components.JokerSection
import de.morzo.realmscore.ui.sandbox.components.NecromancerRowData

/** The two stages of capturing one entry: pick the cards, then resolve the jokers (spec 25.5). */
private enum class CaptureStage { CardPick, PlayerStage }

/**
 * The reusable inner content of a player's hand capture, modelled as one construct in two stages
 * (spec 25.5):
 *
 *  - **KartenPick** — the chosen cards as an overlapping, tappable fan ([OverlappingHandStack], tap
 *    corrects a card via the full-screen [CardPicker]) above an *embedded* [CardPickerContent] that
 *    fills the next empty slot. Once all required cards are in, it auto-advances to:
 *  - **Spieler-Stage** — the hand laid out flat ([HandSlotsRow], non-overlapping), the joker
 *    resolution ([JokerSection]) with the Optimizer, and the "done" submit button. Correcting a card
 *    here reopens the familiar full-screen [CardPicker].
 *
 * The Mittelfeld (discard) entry has no joker stage, so it stays in KartenPick and submits there.
 *
 * State is passed in via [state] and mutations via the callbacks; the content holds only transient
 * stage/picker UI state. [autoOpenKey] (pass the current profileId) resets that transient state when
 * a different entry is selected, so each freshly selected entry starts in the right stage.
 * [searchEnabled] drives whether the embedded picker shows its text-search field.
 */
@Composable
fun PlayerHandCaptureContent(
    state: PlayerHandEntryUiState,
    allCards: List<CardDefinition>,
    necromancerCandidates: (Set<String>) -> List<CardDefinition>,
    onSetCardInSlot: (Int, CardDefinition) -> Unit,
    onClearSlot: (Int) -> Unit,
    onSetJokerAssignment: (String, JokerAssignment?) -> Unit,
    onApplyOptimal: () -> Unit,
    onSetNecromancerPick: (String) -> Unit,
    onClearNecromancerPick: () -> Unit,
    onSubmit: () -> Unit,
    submitLabel: String,
    modifier: Modifier = Modifier,
    autoOpenKey: Any? = Unit,
    searchEnabled: Boolean = true,
) {
    // Discard entries never resolve jokers, so they live in CardPick only; a player hand starts in
    // the player stage when it is already complete (e.g. re-entered to correct), else in CardPick.
    val initialStage = if (!state.isDiscard && state.cardsCount >= state.requiredSlotCount) {
        CaptureStage.PlayerStage
    } else {
        CaptureStage.CardPick
    }
    var stage by rememberSaveable(autoOpenKey) { mutableStateOf(initialStage) }
    // Slot whose card is being corrected via the full-screen picker (both stages); the Necromancer
    // pull keeps its own full-screen picker.
    var editSlot by remember(autoOpenKey) { mutableStateOf<Int?>(null) }
    var necromancerPickerOpen by rememberSaveable(autoOpenKey) { mutableStateOf(false) }

    // Auto-advance to the player stage as soon as a non-discard hand is complete.
    LaunchedEffect(autoOpenKey, state.cardsCount, state.isDiscard) {
        if (!state.isDiscard &&
            state.cardsCount >= state.requiredSlotCount &&
            stage == CaptureStage.CardPick
        ) {
            stage = CaptureStage.PlayerStage
        }
    }

    val placedKeys = remember(state.slots) { state.filledCards.map { it.key }.toSet() }
    val unavailableKeys = remember(placedKeys, state.cardsUsedByOthers) {
        placedKeys + state.cardsUsedByOthers
    }

    when (stage) {
        CaptureStage.CardPick -> CardPickStage(
            state = state,
            allCards = allCards,
            unavailableKeys = unavailableKeys,
            searchEnabled = searchEnabled,
            onSetCardInSlot = onSetCardInSlot,
            onSubmit = onSubmit,
            submitLabel = submitLabel,
            autoOpenKey = autoOpenKey,
            modifier = modifier,
        )
        CaptureStage.PlayerStage -> PlayerStageContent(
            state = state,
            allCards = allCards,
            onCorrectSlot = { editSlot = it },
            onSetJokerAssignment = onSetJokerAssignment,
            onApplyOptimal = onApplyOptimal,
            onOpenNecromancerPicker = { necromancerPickerOpen = true },
            onClearNecromancerPick = onClearNecromancerPick,
            onSubmit = onSubmit,
            submitLabel = submitLabel,
            modifier = modifier,
        )
    }

    // Full-screen correction picker, shared by both stages and the overlapping fan.
    editSlot?.let { idx ->
        val currentSlot = state.slots[idx]
        val filled = currentSlot as? CardSlot.Filled
        val excluded = if (filled != null) unavailableKeys - filled.card.key else unavailableKeys
        CardPicker(
            allCards = allCards,
            excludedKeys = excluded,
            highlightedKey = filled?.card?.key,
            onCardChosen = { card ->
                onSetCardInSlot(idx, card)
                editSlot = null
            },
            onDismiss = { editSlot = null },
            showClearButton = filled != null,
            onClear = if (filled != null) {
                {
                    onClearSlot(idx)
                    editSlot = null
                }
            } else null,
        )
    }

    if (necromancerPickerOpen) {
        val candidates = remember(placedKeys) { necromancerCandidates(placedKeys) }
        CardPicker(
            allCards = candidates,
            onCardChosen = { card ->
                onSetNecromancerPick(card.key)
                necromancerPickerOpen = false
            },
            onDismiss = { necromancerPickerOpen = false },
        )
    }
}

@Composable
private fun CardPickStage(
    state: PlayerHandEntryUiState,
    allCards: List<CardDefinition>,
    unavailableKeys: Set<String>,
    searchEnabled: Boolean,
    onSetCardInSlot: (Int, CardDefinition) -> Unit,
    onSubmit: () -> Unit,
    submitLabel: String,
    autoOpenKey: Any?,
    modifier: Modifier = Modifier,
) {
    // The slot the embedded picker fills next. Default: the first empty slot. Tapping an already
    // chosen card in the fan overrides it, so the user re-picks that card inline through the same
    // picker section (no full-screen picker here); the override resets once a card is placed.
    var pickTarget by rememberSaveable(autoOpenKey) { mutableStateOf<Int?>(null) }
    val firstEmpty = state.slots.indexOfFirst { it is CardSlot.Empty }
    val targetSlot = pickTarget ?: firstEmpty

    // When re-picking a filled slot, let its current card be selectable again (and highlight it in
    // the list), matching the full-screen correction picker used in the player stage.
    val targetCard = (state.slots.getOrNull(targetSlot) as? CardSlot.Filled)?.card
    val pickerExcluded = if (targetCard != null) unavailableKeys - targetCard.key else unavailableKeys

    // No outer verticalScroll here: the embedded picker's card list is a LazyColumn that takes the
    // remaining height via weight(1f); imePadding keeps it usable when the keyboard is up.
    Column(
        modifier = modifier
            .fillMaxSize()
            .imePadding()
            .padding(16.dp),
    ) {
        OverlappingHandStack(
            slots = state.slots,
            currentSlot = targetSlot,
            onSlotTap = { pickTarget = it },
        )
        Spacer(Modifier.height(16.dp))
        CardPickerContent(
            allCards = allCards,
            excludedKeys = pickerExcluded,
            highlightedKey = targetCard?.key,
            showSearch = searchEnabled,
            onCardChosen = { card ->
                if (targetSlot >= 0) onSetCardInSlot(targetSlot, card)
                pickTarget = null
            },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        )
        // The Mittelfeld has no player stage, so its submit lives here once all cards are scanned.
        if (state.isDiscard) {
            Spacer(Modifier.height(12.dp))
            SubmitButton(
                label = submitLabel,
                enabled = state.canSubmit,
                saving = state.isSaving,
                onClick = onSubmit,
            )
        }
    }
}

@Composable
private fun PlayerStageContent(
    state: PlayerHandEntryUiState,
    allCards: List<CardDefinition>,
    onCorrectSlot: (Int) -> Unit,
    onSetJokerAssignment: (String, JokerAssignment?) -> Unit,
    onApplyOptimal: () -> Unit,
    onOpenNecromancerPicker: () -> Unit,
    onClearNecromancerPick: () -> Unit,
    onSubmit: () -> Unit,
    submitLabel: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        // Flat, non-overlapping hand: tapping a card corrects it via the full-screen picker.
        HandSlotsRow(
            slots = state.slots,
            onSlotTap = onCorrectSlot,
        )

        // The Necromancer, substitution jokers and the Island / Fountain choices share one section +
        // the "Optimal" button (Phase 23, spec 25.4): all are JokerType targets, brute-forced alike
        // by the solver. The Necromancer leads the section as the first card to resolve.
        if (state.jokerCardsInHand.isNotEmpty() || state.necromancerInHand) {
            Spacer(Modifier.height(24.dp))
            JokerSection(
                jokers = state.jokerCardsInHand,
                assignments = state.jokerAssignments,
                allCards = allCards,
                handCards = state.filledCards,
                onAssignmentChange = onSetJokerAssignment,
                onOptimal = onApplyOptimal,
                optimalRunning = state.isOptimalRunning,
                necromancer = if (state.necromancerInHand) {
                    NecromancerRowData(
                        card = allCards.first { it.key == "necromancer" },
                        pickedCard = state.jokerAssignments["necromancer"]?.targetCardKey
                            ?.let { key -> allCards.firstOrNull { it.key == key } },
                        onPick = onOpenNecromancerPicker,
                        onClear = onClearNecromancerPick,
                    )
                } else null,
            )
        }

        Spacer(Modifier.height(24.dp))
        SubmitButton(
            label = submitLabel,
            enabled = state.canSubmit,
            saving = state.isSaving,
            onClick = onSubmit,
        )
    }
}

@Composable
private fun SubmitButton(
    label: String,
    enabled: Boolean,
    saving: Boolean,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (saving) {
            CircularProgressIndicator(strokeWidth = 2.dp)
        } else {
            Text(label)
        }
    }
}
