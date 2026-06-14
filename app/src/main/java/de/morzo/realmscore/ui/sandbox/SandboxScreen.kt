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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.morzo.realmscore.R
import de.morzo.realmscore.di.AppContainer
import de.morzo.realmscore.domain.model.CardDefinition
import de.morzo.realmscore.domain.scoring.ScoringResult
import de.morzo.realmscore.ui.components.BreakdownMode
import de.morzo.realmscore.ui.components.BreakdownModeChips
import de.morzo.realmscore.ui.components.CardPicker
import de.morzo.realmscore.ui.components.CardPickerContent
import de.morzo.realmscore.ui.components.HandBreakdownBody
import de.morzo.realmscore.ui.handentry.OverlappingHandStack
import de.morzo.realmscore.ui.sandbox.components.HandSlotsRow
import de.morzo.realmscore.ui.sandbox.components.JokerSection
import de.morzo.realmscore.ui.sandbox.components.NecromancerRowData
import de.morzo.realmscore.ui.sandbox.multihand.MultiHandScreen
import de.morzo.realmscore.ui.util.formatShortDate

/** The single-hand Sandbox has two stages (spec 25.6): the KartenPick fill flow (only for a fresh,
 *  bottom-bar entry) and the main hand-tweaking screen. "Move to Sandbox" skips straight to Main. */
private enum class SandboxStage { CardPick, Main }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SandboxScreen(
    container: AppContainer,
    launchData: SandboxLaunchData = SandboxLaunchData.Empty,
    onOpenFavorites: () -> Unit = {},
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
            favoriteRepo = container.sandboxFavoriteRepository,
        ),
    ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var pickerForSlot by rememberSaveable { mutableStateOf<Int?>(null) }
    var necromancerPickerOpen by rememberSaveable { mutableStateOf(false) }
    var renameOpen by rememberSaveable { mutableStateOf(false) }
    var compareSnapshot by remember { mutableStateOf<HandSnapshot?>(null) }

    compareSnapshot?.let { snapshot ->
        MultiHandScreen(
            container = container,
            initialLeft = snapshot,
            onBack = { compareSnapshot = null },
        )
        return
    }

    // A fresh (bottom-bar) entry starts in the KartenPick fill flow; every other launch path arrives
    // with cards already known and goes straight to the main screen.
    var stage by rememberSaveable {
        mutableStateOf(
            if (launchData is SandboxLaunchData.Empty) SandboxStage.CardPick else SandboxStage.Main,
        )
    }
    // Auto-advance to the main screen once the hand is full (mirrors the capture flow, spec 25.5).
    LaunchedEffect(state.filledCards.size, stage) {
        if (stage == SandboxStage.CardPick && state.filledCards.size == SANDBOX_SLOT_COUNT) {
            stage = SandboxStage.Main
        }
    }

    val searchEnabled by container.settingsRepository.pickerSearchEnabled
        .collectAsStateWithLifecycle(initialValue = true)

    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    LaunchedEffect(viewModel) {
        viewModel.favoriteSaved.collect { number ->
            snackbarHostState.showSnackbar(
                context.getString(R.string.sandbox_saved_as_favorite, number),
            )
        }
    }

    val placedKeys = remember(state.slots) {
        state.filledCards.map { it.key }.toSet()
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            if (stage == SandboxStage.CardPick) {
                CardPickTopBar(onOpenFavorites = onOpenFavorites)
            } else {
                MainTopBar(
                    handLabel = handLabel(state),
                    isFavorite = state.isFavorite,
                    starEnabled = state.canSaveFavorite || state.isFavorite,
                    onRename = { renameOpen = true },
                    onToggleFavorite = viewModel::toggleFavorite,
                    onOpenFavorites = onOpenFavorites,
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (stage == SandboxStage.Main) {
                SandboxActionBar(
                    onReset = viewModel::reset,
                    onCompare = { compareSnapshot = viewModel.currentSnapshot() },
                    compareEnabled = state.filledCards.isNotEmpty(),
                )
            }
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

        when (stage) {
            SandboxStage.CardPick -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .imePadding()
                    .padding(16.dp),
            ) {
                OverlappingHandStack(
                    slots = state.slots,
                    onSlotTap = { idx -> pickerForSlot = idx },
                )
                Spacer(Modifier.height(16.dp))
                CardPickerContent(
                    allCards = viewModel.allCards,
                    excludedKeys = placedKeys,
                    showSearch = searchEnabled,
                    onCardChosen = { card ->
                        val nextEmpty = state.slots.indexOfFirst { it is CardSlot.Empty }
                        if (nextEmpty >= 0) viewModel.setCardInSlot(nextEmpty, card)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                )
            }

            SandboxStage.Main -> Column(
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
                    jokers = state.jokerCardsInHand,
                    assignments = state.jokerAssignments,
                    allCards = viewModel.allCards,
                    handCards = state.filledCards,
                    onAssignmentChange = viewModel::setJokerAssignment,
                    onOptimal = viewModel::applyOptimal,
                    optimalRunning = state.optimalRunning,
                    necromancer = if (state.necromancerInHand) {
                        NecromancerRowData(
                            card = viewModel.allCards.first { it.key == "necromancer" },
                            pickedCard = state.jokerAssignments["necromancer"]?.targetCardKey
                                ?.let { key -> viewModel.allCards.firstOrNull { it.key == key } },
                            onPick = { necromancerPickerOpen = true },
                            onClear = viewModel::clearNecromancerPick,
                        )
                    } else null,
                )

                val result = state.scoringResult
                if (result != null) {
                    SandboxScoreSection(
                        score = state.score,
                        cards = state.filledCards,
                        result = result,
                        cardLookup = container.cardLookup::getByKey,
                    )
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
            highlightedKey = (currentSlot as? CardSlot.Filled)?.card?.key,
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
        // When this sandbox was opened from a round with a scanned Mittelfeld, restrict the
        // Necromancer candidates to the captured discard cards (Phase 20).
        val candidates = remember(placedKeys, state.discardScanned, state.discardCards) {
            container.cardLookup.getNecromancerCandidates(
                handKeys = placedKeys,
                discardScanned = state.discardScanned,
                discardKeys = state.discardCards.map { it.key }.toSet(),
            )
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

    if (renameOpen) {
        RenameHandDialog(
            initialName = state.handName.orEmpty(),
            onConfirm = { name ->
                viewModel.renameHand(name)
                renameOpen = false
            },
            onDismiss = { renameOpen = false },
        )
    }
}

/** The header name shown in the main stage: the free-text name, else the favorite number, else a
 *  generic default for an unsaved hand (spec 25.6). */
@Composable
private fun handLabel(state: SandboxUiState): String =
    state.handName
        ?: state.favoriteNumber?.let { stringResource(R.string.sandbox_favorite_number, it) }
        ?: stringResource(R.string.sandbox_hand_default_name)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CardPickTopBar(onOpenFavorites: () -> Unit) {
    TopAppBar(
        title = { Text(stringResource(R.string.sandbox_title)) },
        actions = {
            IconButton(onClick = onOpenFavorites) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.List,
                    contentDescription = stringResource(R.string.sandbox_favorites_title),
                )
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainTopBar(
    handLabel: String,
    isFavorite: Boolean,
    starEnabled: Boolean,
    onRename: () -> Unit,
    onToggleFavorite: () -> Unit,
    onOpenFavorites: () -> Unit,
) {
    TopAppBar(
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(handLabel)
                IconButton(onClick = onRename) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = stringResource(R.string.sandbox_rename_hand),
                    )
                }
            }
        },
        actions = {
            IconButton(onClick = onToggleFavorite, enabled = starEnabled) {
                Icon(
                    imageVector = if (isFavorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                    contentDescription = stringResource(
                        if (isFavorite) R.string.sandbox_unmark_favorite else R.string.sandbox_mark_favorite,
                    ),
                )
            }
            IconButton(onClick = onOpenFavorites) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.List,
                    contentDescription = stringResource(R.string.sandbox_favorites_title),
                )
            }
        },
    )
}

/** Inline points section (spec 25.6): the former bottom score banner moves up here as the header,
 *  with the Ring/Liste toggle on the right and the chosen view rendered below it. */
@Composable
private fun SandboxScoreSection(
    score: Int,
    cards: List<CardDefinition>,
    result: ScoringResult,
    cardLookup: (String) -> CardDefinition?,
    modifier: Modifier = Modifier,
) {
    var mode by rememberSaveable { mutableStateOf(BreakdownMode.RING) }
    Surface(
        modifier = modifier.fillMaxWidth(),
        tonalElevation = 1.dp,
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.sandbox_score, score),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                )
                BreakdownModeChips(mode = mode, onModeChange = { mode = it })
            }
            Spacer(Modifier.height(8.dp))
            HandBreakdownBody(
                mode = mode,
                cards = cards,
                result = result,
                cardLookup = cardLookup,
            )
        }
    }
}

/** The bottom fixed bar of the main stage: reset on the left, compare on the right (spec 25.6). */
@Composable
private fun SandboxActionBar(
    onReset: () -> Unit,
    onCompare: () -> Unit,
    compareEnabled: Boolean,
) {
    Surface(tonalElevation = 4.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(onClick = onReset, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.sandbox_reset_hand))
            }
            Button(
                onClick = onCompare,
                enabled = compareEnabled,
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(R.string.sandbox_compare_hand))
            }
        }
    }
}

@Composable
private fun RenameHandDialog(
    initialName: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by rememberSaveable { mutableStateOf(initialName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.sandbox_rename_hand)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                label = { Text(stringResource(R.string.sandbox_hand_name_label)) },
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
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
    is SandboxLaunchData.FromFavorite -> "sandbox-favorite-${launchData.favoriteId}"
    is SandboxLaunchData.Prefilled -> "sandbox-prefilled"
}
