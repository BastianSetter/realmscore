package de.morzo.realmscore.ui.handentry

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.morzo.realmscore.R
import de.morzo.realmscore.domain.model.CardDefinition
import de.morzo.realmscore.domain.scoring.JokerAssignment
import de.morzo.realmscore.ui.components.CardPicker
import de.morzo.realmscore.ui.sandbox.CardSlot
import de.morzo.realmscore.ui.sandbox.components.HandSlotsRow
import de.morzo.realmscore.ui.sandbox.components.JokerSection
import de.morzo.realmscore.ui.sandbox.components.NecromancerSection

private sealed interface PickerMode {
    data class SingleEdit(val slotIndex: Int) : PickerMode
    data object ContinuousFill : PickerMode
}

/**
 * The reusable inner content of a player's hand capture (7 slots, joker section, Necromancer
 * section, submit button + the card pickers). Extracted from the old PlayerHandEntryScreen so both
 * that screen and the Phase 18.1 full-screen [de.morzo.realmscore.ui.game.RoundCaptureScreen] share
 * one implementation. State is passed in via [state] and mutations via the callbacks – the content
 * holds only transient picker UI state.
 *
 * [autoOpenKey] resets the "auto-open the picker on first show" behaviour; pass the current
 * profileId so each freshly selected, not-yet-filled player auto-opens the continuous-fill picker.
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
) {
    var pickerMode by remember(autoOpenKey) { mutableStateOf<PickerMode?>(null) }
    var necromancerPickerOpen by rememberSaveable(autoOpenKey) { mutableStateOf(false) }
    var autoOpenedOnce by rememberSaveable(autoOpenKey) { mutableStateOf(false) }

    LaunchedEffect(autoOpenKey, state.isLoading, state.cardsCount) {
        if (!state.isLoading && !autoOpenedOnce && state.cardsCount < state.requiredSlotCount) {
            pickerMode = PickerMode.ContinuousFill
            autoOpenedOnce = true
        }
    }

    val placedKeys = remember(state.slots) { state.filledCards.map { it.key }.toSet() }
    val unavailableKeys = remember(placedKeys, state.cardsUsedByOthers) {
        placedKeys + state.cardsUsedByOthers
    }

    Column(
        modifier = modifier
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Text(
            text = if (state.isDiscard) {
                stringResource(R.string.discard_capture_count, state.cardsCount, state.requiredSlotCount)
            } else {
                stringResource(R.string.player_hand_count, state.cardsCount)
            },
            style = MaterialTheme.typography.titleMedium,
        )

        Spacer(Modifier.height(16.dp))

        HandSlotsRow(
            slots = state.slots,
            onSlotTap = { idx ->
                pickerMode = if (state.slots[idx] is CardSlot.Filled) {
                    PickerMode.SingleEdit(idx)
                } else {
                    PickerMode.ContinuousFill
                }
            },
        )

        if (!state.isDiscard && state.jokersInHand.isNotEmpty()) {
            Spacer(Modifier.height(24.dp))
            JokerSection(
                jokers = state.jokersInHand,
                assignments = state.jokerAssignments,
                allCards = allCards,
                handCards = state.filledCards,
                onAssignmentChange = onSetJokerAssignment,
                onOptimal = onApplyOptimal,
                optimalRunning = state.isOptimalRunning,
            )
        }

        if (!state.isDiscard && state.necromancerInHand) {
            Spacer(Modifier.height(24.dp))
            NecromancerSection(
                pickedCard = state.playerChoices.necromancerPickKey
                    ?.let { key -> allCards.firstOrNull { it.key == key } },
                onPickCard = { necromancerPickerOpen = true },
                onClearPick = onClearNecromancerPick,
            )
        }

        Spacer(Modifier.height(24.dp))

        Button(
            onClick = onSubmit,
            enabled = state.canSubmit,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (state.isSaving) {
                CircularProgressIndicator(strokeWidth = 2.dp)
            } else {
                Text(submitLabel)
            }
        }
    }

    when (val mode = pickerMode) {
        is PickerMode.SingleEdit -> {
            val currentSlot = state.slots[mode.slotIndex]
            val isFilled = currentSlot is CardSlot.Filled
            val excluded = if (isFilled) {
                unavailableKeys - (currentSlot as CardSlot.Filled).card.key
            } else {
                unavailableKeys
            }
            CardPicker(
                allCards = allCards,
                excludedKeys = excluded,
                onCardChosen = { card ->
                    onSetCardInSlot(mode.slotIndex, card)
                    pickerMode = null
                },
                onDismiss = { pickerMode = null },
                showClearButton = isFilled,
                onClear = if (isFilled) {
                    {
                        onClearSlot(mode.slotIndex)
                        pickerMode = null
                    }
                } else null,
            )
        }
        PickerMode.ContinuousFill -> {
            CardPicker(
                allCards = allCards,
                excludedKeys = unavailableKeys,
                onCardChosen = { card ->
                    val nextEmpty = state.slots.indexOfFirst { it is CardSlot.Empty }
                    if (nextEmpty >= 0) {
                        onSetCardInSlot(nextEmpty, card)
                        // After filling this slot, close once no empty slot remains.
                        if (state.slots.count { it is CardSlot.Empty } <= 1) {
                            pickerMode = null
                        }
                    }
                },
                onDismiss = { pickerMode = null },
                showClearButton = false,
                onClear = null,
            )
        }
        null -> Unit
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
