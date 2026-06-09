package de.morzo.realmscore.ui.sandbox

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Science
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.morzo.realmscore.R
import de.morzo.realmscore.di.AppContainer
import de.morzo.realmscore.ui.components.CardPicker
import de.morzo.realmscore.ui.components.HandBreakdownSheet
import de.morzo.realmscore.ui.sandbox.components.ChoiceSection
import de.morzo.realmscore.ui.sandbox.components.HandSlotsRow
import de.morzo.realmscore.ui.sandbox.components.JokerSection
import de.morzo.realmscore.ui.sandbox.components.NecromancerSection
import de.morzo.realmscore.ui.sandbox.components.ScoreFooter
import de.morzo.realmscore.ui.util.formatShortDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SandboxScreen(
    container: AppContainer,
    launchData: SandboxLaunchData = SandboxLaunchData.Empty,
    viewModel: SandboxViewModel = viewModel(
        key = sandboxViewModelKey(launchData),
        factory = SandboxViewModel.Factory(
            launchData = launchData,
            cardLookup = container.cardLookup,
            engine = container.scoringEngine,
            solver = container.optimalSolver,
            handCardRepo = container.handCardRepository,
            roundRepo = container.roundRepository,
            gameRepo = container.gameRepository,
            profileRepo = container.profileRepository,
        ),
    ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var pickerForSlot by rememberSaveable { mutableStateOf<Int?>(null) }
    var necromancerPickerOpen by rememberSaveable { mutableStateOf(false) }

    val placedKeys = remember(state.slots) {
        state.filledCards.map { it.key }.toSet()
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.sandbox_title)) })
        },
        bottomBar = {
            ScoreFooter(
                score = state.score,
                onBreakdownClick = if (state.scoringResult != null) viewModel::openBreakdown else null,
            )
        },
    ) { padding ->
        if (state.isLoadingLaunchData) {
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
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            state.originBanner?.let { banner ->
                OriginBannerCard(banner = banner, onDismiss = viewModel::reset)
            }

            HandSlotsRow(
                slots = state.slots,
                onSlotTap = { idx -> pickerForSlot = idx },
            )

            JokerSection(
                jokers = state.jokersInHand,
                assignments = state.jokerAssignments,
                allCards = viewModel.allCards,
                handCards = state.filledCards,
                onAssignmentChange = viewModel::setJokerAssignment,
            )

            ChoiceSection(
                handCards = state.filledCards,
                islandTargetKey = state.playerChoices.islandTargetKey,
                fountainSourceKey = state.playerChoices.fountainSourceKey,
                onIslandChange = viewModel::setIslandTarget,
                onFountainChange = viewModel::setFountainSource,
            )

            if (state.necromancerInHand) {
                NecromancerSection(
                    pickedCard = state.playerChoices.necromancerPickKey
                        ?.let { key -> viewModel.allCards.firstOrNull { it.key == key } },
                    onPickCard = { necromancerPickerOpen = true },
                    onClearPick = viewModel::clearNecromancerPick,
                )
            }

            Spacer(Modifier.height(8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = viewModel::applyOptimal,
                    enabled = !state.optimalRunning && state.filledCards.isNotEmpty(),
                ) {
                    if (state.optimalRunning) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Text(stringResource(R.string.sandbox_optimal))
                    }
                }
                OutlinedButton(onClick = viewModel::reset) {
                    Text(stringResource(R.string.sandbox_reset))
                }
            }
        }
    }

    pickerForSlot?.let { slotIdx ->
        val currentSlot = state.slots[slotIdx]
        val isFilled = currentSlot is CardSlot.Filled
        val excluded = if (isFilled) placedKeys - (currentSlot as CardSlot.Filled).card.key else placedKeys
        CardPicker(
            allCards = viewModel.allCards,
            excludedKeys = excluded,
            onCardChosen = { card ->
                viewModel.setCardInSlot(slotIdx, card)
                pickerForSlot = null
            },
            onDismiss = { pickerForSlot = null },
            showClearButton = isFilled,
            onClear = if (isFilled) {
                {
                    viewModel.clearSlot(slotIdx)
                    pickerForSlot = null
                }
            } else null,
        )
    }

    if (necromancerPickerOpen) {
        // PHASE 20: bei gescanntem Mittelfeld auf discardKeys filtern (getNecromancerCandidates).
        val candidates = remember(placedKeys) {
            container.cardLookup.getNecromancerCandidates(handKeys = placedKeys)
        }
        CardPicker(
            allCards = candidates,
            onCardChosen = { card ->
                viewModel.setNecromancerPick(card.key)
                necromancerPickerOpen = false
            },
            onDismiss = { necromancerPickerOpen = false },
        )
    }

    val result = state.scoringResult
    if (state.breakdownOpen && result != null) {
        HandBreakdownSheet(
            cards = state.filledCards,
            result = result,
            onDismiss = viewModel::closeBreakdown,
        )
    }
}

@Composable
private fun OriginBannerCard(banner: OriginBanner, onDismiss: () -> Unit) {
    val gameLabel = banner.gameDisplayName
        ?: stringResource(R.string.sandbox_origin_no_game_name, formatShortDate(banner.gameStartedAt))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(12.dp),
            )
            .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Default.Science,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.size(8.dp))
        Text(
            text = stringResource(
                R.string.sandbox_origin_banner,
                gameLabel,
                banner.roundNumber,
                banner.playerName,
            ),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
        )
        IconButton(onClick = onDismiss) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = stringResource(R.string.sandbox_origin_dismiss),
            )
        }
    }
}

private fun sandboxViewModelKey(launchData: SandboxLaunchData): String = when (launchData) {
    SandboxLaunchData.Empty -> "sandbox-empty"
    is SandboxLaunchData.FromRound ->
        "sandbox-from-${launchData.gameId}-${launchData.roundId}-${launchData.profileId}"
}
