package de.morzo.realmscore.ui.sandbox.multihand

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.morzo.realmscore.R
import de.morzo.realmscore.data.cards.CardLookup
import de.morzo.realmscore.di.AppContainer
import de.morzo.realmscore.domain.model.CardDefinition
import de.morzo.realmscore.domain.scoring.CardScoreResult
import de.morzo.realmscore.ui.components.CardPicker
import de.morzo.realmscore.ui.components.suitColor
import de.morzo.realmscore.ui.components.suitOnColor
import de.morzo.realmscore.ui.sandbox.CardSlot
import de.morzo.realmscore.ui.sandbox.HandSnapshot
import de.morzo.realmscore.ui.sandbox.SandboxLaunchData
import de.morzo.realmscore.ui.sandbox.SandboxUiState
import de.morzo.realmscore.ui.sandbox.SandboxViewModel
import de.morzo.realmscore.ui.sandbox.components.BreakdownEffectRow
import de.morzo.realmscore.ui.sandbox.components.JokerSection
import de.morzo.realmscore.ui.sandbox.components.NecromancerSection
import de.morzo.realmscore.ui.sandbox.components.formatDelta
import de.morzo.realmscore.ui.util.displayName

/**
 * Multi-Hand compare view (Phase 22): two independent Sandbox hands side by side. The left hand is
 * pre-filled from [initialLeft]; the right starts empty. A score-comparison header sits on top. No
 * ring visualization here — that stays exclusive to the single-hand Sandbox.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MultiHandScreen(
    container: AppContainer,
    initialLeft: HandSnapshot,
    onBack: () -> Unit,
) {
    val vmLeft: SandboxViewModel = viewModel(
        key = "multihand-left",
        factory = SandboxViewModel.Factory(
            launchData = SandboxLaunchData.Prefilled(initialLeft),
            cardLookup = container.cardLookup,
            engine = container.scoringEngine,
            solver = container.optimalSolver,
        ),
    )
    val vmRight: SandboxViewModel = viewModel(
        key = "multihand-right",
        factory = SandboxViewModel.Factory(
            launchData = SandboxLaunchData.Empty,
            cardLookup = container.cardLookup,
            engine = container.scoringEngine,
            solver = container.optimalSolver,
        ),
    )

    val stateLeft by vmLeft.uiState.collectAsStateWithLifecycle()
    val stateRight by vmRight.uiState.collectAsStateWithLifecycle()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.multihand_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.multihand_back_to_single),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            MultiHandScoreHeader(scoreLeft = stateLeft.score, scoreRight = stateRight.score)
            MultiHandActions(
                onCopyAToB = { vmRight.applyHandSnapshot(vmLeft.currentSnapshot()) },
                onSwap = {
                    val left = vmLeft.currentSnapshot()
                    val right = vmRight.currentSnapshot()
                    vmLeft.applyHandSnapshot(right)
                    vmRight.applyHandSnapshot(left)
                },
            )
            HorizontalDivider()
            Row(Modifier.weight(1f).fillMaxWidth()) {
                SandboxHandColumn(
                    modifier = Modifier.weight(1f),
                    label = stringResource(R.string.multihand_left),
                    vm = vmLeft,
                    state = stateLeft,
                )
                VerticalDivider()
                SandboxHandColumn(
                    modifier = Modifier.weight(1f),
                    label = stringResource(R.string.multihand_right),
                    vm = vmRight,
                    state = stateRight,
                )
            }
        }
    }
}

@Composable
private fun MultiHandActions(
    onCopyAToB: () -> Unit,
    onSwap: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        TextButton(onClick = onCopyAToB) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.size(4.dp))
            Text(stringResource(R.string.multihand_copy_a_to_b))
        }
        TextButton(onClick = onSwap) {
            Icon(
                imageVector = Icons.Default.SwapHoriz,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.size(4.dp))
            Text(stringResource(R.string.multihand_swap))
        }
    }
}

@Composable
private fun MultiHandScoreHeader(scoreLeft: Int, scoreRight: Int) {
    val leftWins = scoreLeft > scoreRight
    val rightWins = scoreRight > scoreLeft
    Row(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ScoreDisplay(score = scoreLeft, isWinner = leftWins, modifier = Modifier.weight(1f))
        Text(
            text = stringResource(R.string.multihand_vs),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ScoreDisplay(score = scoreRight, isWinner = rightWins, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun ScoreDisplay(score: Int, isWinner: Boolean, modifier: Modifier = Modifier) {
    Text(
        text = score.toString(),
        modifier = modifier,
        textAlign = TextAlign.Center,
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = if (isWinner) FontWeight.Bold else FontWeight.Normal,
        color = if (isWinner) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
    )
}

@Composable
private fun SandboxHandColumn(
    label: String,
    vm: SandboxViewModel,
    state: SandboxUiState,
    modifier: Modifier = Modifier,
) {
    var pickerForSlot by remember { mutableStateOf<Int?>(null) }
    var necromancerPickerOpen by remember { mutableStateOf(false) }

    val placedKeys = remember(state.slots) { state.filledCards.map { it.key }.toSet() }
    val perCard = remember(state.scoringResult) {
        state.scoringResult?.perCard?.associateBy { it.cardKey } ?: emptyMap()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        state.slots.forEachIndexed { index, slot ->
            MultiHandSlot(
                slot = slot,
                score = (slot as? CardSlot.Filled)?.let { perCard[it.card.key] },
                onEdit = { pickerForSlot = index },
            )
        }

        JokerSection(
            jokers = state.jokerCardsInHand,
            assignments = state.jokerAssignments,
            allCards = vm.allCards,
            handCards = state.filledCards,
            onAssignmentChange = vm::setJokerAssignment,
        )

        if (state.necromancerInHand) {
            NecromancerSection(
                pickedCard = state.playerChoices.necromancerPickKey
                    ?.let { key -> vm.allCards.firstOrNull { it.key == key } },
                onPickCard = { necromancerPickerOpen = true },
                onClearPick = vm::clearNecromancerPick,
            )
        }
    }

    pickerForSlot?.let { slotIdx ->
        val currentSlot = state.slots[slotIdx]
        val isFilled = currentSlot is CardSlot.Filled
        val excluded = if (isFilled) placedKeys - (currentSlot as CardSlot.Filled).card.key else placedKeys
        CardPicker(
            allCards = vm.allCards,
            excludedKeys = excluded,
            onCardChosen = { card ->
                vm.setCardInSlot(slotIdx, card)
                pickerForSlot = null
            },
            onDismiss = { pickerForSlot = null },
            showClearButton = isFilled,
            onClear = if (isFilled) {
                {
                    vm.clearSlot(slotIdx)
                    pickerForSlot = null
                }
            } else null,
        )
    }

    if (necromancerPickerOpen) {
        // Discard pile is never scanned in the compare view, so offer the full eligible set.
        val candidates = remember(placedKeys) {
            vm.allCards.filter { it.suit in CardLookup.NECROMANCER_SUITS && it.key !in placedKeys }
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

@Composable
private fun MultiHandSlot(
    slot: CardSlot,
    score: CardScoreResult?,
    onEdit: () -> Unit,
) {
    when (slot) {
        CardSlot.Empty -> EmptyMultiHandSlot(onEdit)
        is CardSlot.Filled -> FilledMultiHandSlot(slot.card, score, onEdit)
    }
}

@Composable
private fun EmptyMultiHandSlot(onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        tonalElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = stringResource(R.string.sandbox_empty_slot),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun FilledMultiHandSlot(
    card: CardDefinition,
    score: CardScoreResult?,
    onEdit: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = suitColor(card.suit),
    ) {
        Column(Modifier.fillMaxWidth().padding(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(
                    modifier = Modifier.weight(1f).clickable { expanded = !expanded },
                ) {
                    Text(
                        text = card.displayName(),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = suitOnColor(card.suit),
                    )
                    if (score != null) {
                        Text(
                            text = formatDelta(score.contributedScore),
                            style = MaterialTheme.typography.labelLarge,
                            color = suitOnColor(card.suit),
                        )
                    }
                }
                IconButton(onClick = onEdit) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = stringResource(R.string.sandbox_remove_card),
                        tint = suitOnColor(card.suit),
                    )
                }
            }
            if (expanded && score != null && score.effects.isNotEmpty()) {
                Box(Modifier.background(MaterialTheme.colorScheme.surface, RoundedCornerShape(6.dp))) {
                    Column(Modifier.fillMaxWidth().padding(8.dp)) {
                        score.effects.forEach { effect -> BreakdownEffectRow(effect) }
                    }
                }
            }
        }
    }
}
