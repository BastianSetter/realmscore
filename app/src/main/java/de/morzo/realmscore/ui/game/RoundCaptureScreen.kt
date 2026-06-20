package de.morzo.realmscore.ui.game

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.morzo.realmscore.R
import de.morzo.realmscore.di.AppContainer
import de.morzo.realmscore.ui.scan.CameraScanScreen
import de.morzo.realmscore.ui.util.sortedByLocalizedSuitAndName
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoundCaptureScreen(
    container: AppContainer,
    roundId: String,
    onAllPlayersCaptured: () -> Unit,
    onBack: () -> Unit,
) {
    val vm: RoundCaptureViewModel = viewModel(
        factory = RoundCaptureViewModel.Factory(
            cardLookup = container.cardLookup,
            handCardRepo = container.handCardRepository,
            profileRepo = container.profileRepository,
            gameRepo = container.gameRepository,
            roundRepo = container.roundRepository,
            settingsRepo = container.settingsRepository,
            engine = container.scoringEngine,
            optimalSolver = container.optimalSolver,
            p2p = container.p2pSessionRepository,
            deviceUuidProvider = container.deviceUuidProvider,
            roundId = roundId,
        ),
    )
    val state by vm.uiState.collectAsStateWithLifecycle()

    // Per-entry "dropped to manual" flags so the auto-opening camera doesn't keep reappearing once
    // the user chose manual entry (or was denied the camera) for that hand. Resets with this round.
    val manuallyDismissed = remember { mutableStateMapOf<String, Boolean>() }
    val currentId = state.currentProfileId

    // Phase 26: when the setting is on, an empty, not-yet-filled entry shows the camera scan instead
    // of the manual KartenPick — but kept *inside* the scaffold so the player dropdown stays usable
    // and the user can pick which hand / the Mittelfeld to scan. Filling the entry (via scan or by
    // going manual) flips back to the normal flow.
    val showCameraScan = !state.isLoading &&
        state.cameraScanEnabled &&
        currentId != null &&
        state.current.cardsCount == 0 &&
        manuallyDismissed[currentId] != true

    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val skippedTemplate = stringResource(R.string.scan_skipped_conflicts)
    val revealBlockedMsg = stringResource(R.string.reveal_blocked_duplicates)

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    if (state.isLoading) {
                        Text(stringResource(R.string.round_entry_title_loading))
                    } else {
                        PlayerDropdown(
                            players = state.orderedPlayers,
                            currentProfileId = state.currentProfileId,
                            onSelectPlayer = vm::switchToPlayer,
                            onForceUnlock = vm::takeOverUnit,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.game_back),
                        )
                    }
                },
                actions = {
                    if (state.allCaptured) {
                        TextButton(
                            onClick = {
                                if (state.hasDuplicates) {
                                    scope.launch { snackbarHostState.showSnackbar(revealBlockedMsg) }
                                } else {
                                    onAllPlayersCaptured()
                                }
                            },
                        ) {
                            Text(stringResource(R.string.round_capture_reveal))
                        }
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

        // P2P (Stage B): this device finished its share — show the remaining units and let the user
        // take over a stuck one. The host computes the reveal once every unit is done (B4).
        if (state.waitingForOthers) {
            WaitingForOthersContent(
                modifier = Modifier.padding(padding),
                players = state.orderedPlayers,
                onTakeOver = vm::takeOverUnit,
            )
            return@Scaffold
        }

        if (showCameraScan && currentId != null) {
            CameraScanScreen(
                modifier = Modifier.padding(padding),
                scanner = container.cardScanner,
                maxCards = state.current.requiredSlotCount,
                entryKey = currentId,
                // Cards already placed in other hands / the Mittelfeld can't appear here too.
                excludedKeys = state.current.cardsUsedByOthers,
                onScanComplete = { cards, skipped ->
                    // OCR returns cards in detection order (looks random); show them sorted by
                    // localized suit, then card name, so the resulting tokens read consistently.
                    vm.setCardsFromScan(cards.sortedByLocalizedSuitAndName(context))
                    if (skipped > 0) {
                        scope.launch {
                            snackbarHostState.showSnackbar(String.format(skippedTemplate, skipped))
                        }
                    }
                },
                onManual = { manuallyDismissed[currentId] = true },
            )
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (state.hasDuplicates) {
                DuplicateWarning(names = state.duplicateCardNames)
            }
            de.morzo.realmscore.ui.handentry.PlayerHandCaptureContent(
                modifier = Modifier.weight(1f),
                state = state.current,
                allCards = vm.allCards,
                necromancerCandidates = vm::necromancerCandidates,
                onSetCardInSlot = vm::setCardInSlot,
                onClearSlot = vm::clearSlot,
                onSetJokerAssignment = vm::setJokerAssignment,
                onApplyOptimal = vm::applyOptimal,
                onSetNecromancerPick = vm::setNecromancerPick,
                onClearNecromancerPick = vm::clearNecromancerPick,
                onSubmit = { vm.submitCurrentAndAdvance(onAllPlayersCaptured) },
                submitLabel = if (state.current.isDiscard) {
                    stringResource(R.string.discard_capture_submit)
                } else {
                    stringResource(R.string.round_capture_submit)
                },
                autoOpenKey = state.currentProfileId,
                searchEnabled = state.pickerSearchEnabled,
            )
        }
    }
}

/**
 * P2P pre-reveal waiting screen (Stage B): this device captured all the units it could grab; the round
 * isn't done yet. Lists the units still outstanding with who is on them, and offers "Übernehmen" to
 * force-release a unit stuck on an idle device so this device can take it over.
 */
@Composable
private fun WaitingForOthersContent(
    modifier: Modifier = Modifier,
    players: List<CapturePlayer>,
    onTakeOver: (String) -> Unit,
) {
    val doneCount = players.count { it.isDone }
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp))
            Text(
                text = stringResource(R.string.p2p_waiting_title),
                style = MaterialTheme.typography.titleMedium,
            )
        }
        Text(
            text = stringResource(R.string.p2p_waiting_progress, doneCount, players.size),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        players.filterNot { it.isDone }.forEach { player ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ColorDot(colorArgb = player.colorArgb)
                Column(Modifier.weight(1f)) {
                    Text(player.name)
                    Text(
                        text = player.lockedByName?.let { stringResource(R.string.p2p_locked_by, it) }
                            ?: stringResource(R.string.p2p_unit_open),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (player.lockedByName != null) {
                    OutlinedButton(onClick = { onTakeOver(player.profileId) }) {
                        Text(stringResource(R.string.p2p_take_over))
                    }
                }
            }
        }
    }
}

@Composable
private fun DuplicateWarning(names: List<String>) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        shape = RoundedCornerShape(8.dp),
    ) {
        Text(
            text = stringResource(R.string.capture_duplicate_warning, names.joinToString(", ")),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.padding(12.dp),
        )
    }
}

@Composable
private fun PlayerDropdown(
    players: List<CapturePlayer>,
    currentProfileId: String?,
    onSelectPlayer: (String) -> Unit,
    onForceUnlock: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val current = players.firstOrNull { it.profileId == currentProfileId }

    Box {
        Row(
            modifier = Modifier.clickable { expanded = true },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ColorDot(colorArgb = current?.colorArgb ?: 0)
            Text(
                text = current?.let { "${it.name} (${it.cardsCount}/${it.requiredCount})" }
                    ?: stringResource(R.string.round_capture_switch_player),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Icon(
                imageVector = Icons.Default.ArrowDropDown,
                contentDescription = stringResource(R.string.round_capture_switch_player),
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            players.forEach { player ->
                DropdownMenuItem(
                    text = {
                        Column(Modifier.widthIn(max = 280.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                ColorDot(colorArgb = player.colorArgb)
                                Text(
                                    text = "${player.name} (${player.cardsCount}/${player.requiredCount})",
                                    fontWeight = if (player.profileId == currentProfileId) {
                                        FontWeight.SemiBold
                                    } else {
                                        FontWeight.Normal
                                    },
                                )
                                Spacer(Modifier.width(4.dp))
                                if (player.captured || player.isDone) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = stringResource(
                                            R.string.round_entry_status_completed,
                                        ),
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                            // P2P: another device is capturing this unit — show by whom + "Entsperren".
                            // The name takes the remaining width and wraps; the button stays one line.
                            if (player.lockedByName != null && !player.isDone) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Text(
                                        text = stringResource(R.string.p2p_locked_by, player.lockedByName),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.weight(1f),
                                    )
                                    TextButton(
                                        onClick = {
                                            onForceUnlock(player.profileId)
                                            expanded = false
                                        },
                                        contentPadding = PaddingValues(horizontal = 8.dp),
                                    ) {
                                        Text(
                                            text = stringResource(R.string.p2p_force_unlock),
                                            maxLines = 1,
                                            softWrap = false,
                                        )
                                    }
                                }
                            }
                        }
                    },
                    onClick = {
                        onSelectPlayer(player.profileId)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun ColorDot(colorArgb: Int) {
    Box(
        modifier = Modifier
            .size(24.dp)
            .background(color = Color(colorArgb), shape = CircleShape),
    )
}
