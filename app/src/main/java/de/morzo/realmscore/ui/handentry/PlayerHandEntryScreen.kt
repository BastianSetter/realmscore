package de.morzo.realmscore.ui.handentry

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.morzo.realmscore.R
import de.morzo.realmscore.di.AppContainer
import de.morzo.realmscore.ui.components.CardPicker
import de.morzo.realmscore.ui.sandbox.CardSlot
import de.morzo.realmscore.ui.sandbox.components.HandSlotsRow
import de.morzo.realmscore.ui.sandbox.components.JokerSection
import de.morzo.realmscore.ui.sandbox.components.NecromancerSection

private sealed interface PickerMode {
    data class SingleEdit(val slotIndex: Int) : PickerMode
    data object ContinuousFill : PickerMode
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerHandEntryScreen(
    container: AppContainer,
    roundId: String,
    profileId: String,
    onSubmitDone: () -> Unit,
    onBack: () -> Unit,
) {
    val vm: PlayerHandEntryViewModel = viewModel(
        factory = PlayerHandEntryViewModel.Factory(
            cardLookup = container.cardLookup,
            handCardRepo = container.handCardRepository,
            profileRepo = container.profileRepository,
            engine = container.scoringEngine,
            optimalSolver = container.optimalSolver,
            roundId = roundId,
            profileId = profileId,
        ),
    )
    val state by vm.uiState.collectAsStateWithLifecycle()
    var pickerMode by remember { mutableStateOf<PickerMode?>(null) }
    var necromancerPickerOpen by rememberSaveable { mutableStateOf(false) }
    var autoOpenedOnce by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(state.isLoading, state.cardsCount) {
        if (!state.isLoading && !autoOpenedOnce && state.cardsCount < PLAYER_HAND_SLOT_COUNT) {
            pickerMode = PickerMode.ContinuousFill
            autoOpenedOnce = true
        }
    }

    val placedKeys = remember(state.slots) { state.filledCards.map { it.key }.toSet() }
    val unavailableKeys = remember(placedKeys, state.cardsUsedByOthers) {
        placedKeys + state.cardsUsedByOthers
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (state.isLoading) stringResource(R.string.round_entry_title_loading)
                        else stringResource(R.string.player_hand_title, state.playerName),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.game_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        if (state.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                text = stringResource(R.string.player_hand_count, state.cardsCount),
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

            if (state.jokersInHand.isNotEmpty()) {
                Spacer(Modifier.height(24.dp))
                JokerSection(
                    jokers = state.jokersInHand,
                    assignments = state.jokerAssignments,
                    allCards = vm.allCards,
                    handCards = state.filledCards,
                    onAssignmentChange = vm::setJokerAssignment,
                    onOptimal = vm::applyOptimal,
                    optimalRunning = state.isOptimalRunning,
                )
            }

            if (state.necromancerInHand) {
                Spacer(Modifier.height(24.dp))
                NecromancerSection(
                    pickedCard = state.playerChoices.necromancerPickKey
                        ?.let { key -> vm.allCards.firstOrNull { it.key == key } },
                    onPickCard = { necromancerPickerOpen = true },
                    onClearPick = vm::clearNecromancerPick,
                )
            }

            Spacer(Modifier.height(24.dp))

            Button(
                onClick = { vm.submit(onSubmitDone) },
                enabled = state.canSubmit,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (state.isSaving) {
                    CircularProgressIndicator(strokeWidth = 2.dp)
                } else {
                    Text(stringResource(R.string.player_hand_submit))
                }
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
                allCards = vm.allCards,
                excludedKeys = excluded,
                onCardChosen = { card ->
                    vm.setCardInSlot(mode.slotIndex, card)
                    pickerMode = null
                },
                onDismiss = { pickerMode = null },
                showClearButton = isFilled,
                onClear = if (isFilled) {
                    {
                        vm.clearSlot(mode.slotIndex)
                        pickerMode = null
                    }
                } else null,
            )
        }
        PickerMode.ContinuousFill -> {
            CardPicker(
                allCards = vm.allCards,
                excludedKeys = unavailableKeys,
                onCardChosen = { card ->
                    val nextEmpty = state.slots.indexOfFirst { it is CardSlot.Empty }
                    if (nextEmpty >= 0) {
                        vm.setCardInSlot(nextEmpty, card)
                        if (vm.uiState.value.cardsCount >= PLAYER_HAND_SLOT_COUNT) {
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
        // PHASE 20: bei gescanntem Mittelfeld auf discardKeys filtern (getNecromancerCandidates).
        val candidates = remember(placedKeys) {
            container.cardLookup.getNecromancerCandidates(handKeys = placedKeys)
        }
        CardPicker(
            allCards = candidates,
            onCardChosen = { card ->
                vm.setNecromancerPick(card.key)
                necromancerPickerOpen = false
            },
            onDismiss = { necromancerPickerOpen = false },
        )
    }
}
