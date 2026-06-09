package de.morzo.realmscore.ui.game

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.morzo.realmscore.R
import de.morzo.realmscore.di.AppContainer

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
            engine = container.scoringEngine,
            optimalSolver = container.optimalSolver,
            roundId = roundId,
        ),
    )
    val state by vm.uiState.collectAsStateWithLifecycle()

    Scaffold(
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
                        TextButton(onClick = onAllPlayersCaptured) {
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

        de.morzo.realmscore.ui.handentry.PlayerHandCaptureContent(
            modifier = Modifier.padding(padding),
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
            submitLabel = stringResource(R.string.round_capture_submit),
            autoOpenKey = state.currentProfileId,
        )
    }
}

@Composable
private fun PlayerDropdown(
    players: List<CapturePlayer>,
    currentProfileId: String?,
    onSelectPlayer: (String) -> Unit,
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
                text = current?.name ?: stringResource(R.string.round_capture_switch_player),
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
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            ColorDot(colorArgb = player.colorArgb)
                            Text(
                                text = player.name,
                                fontWeight = if (player.profileId == currentProfileId) {
                                    FontWeight.SemiBold
                                } else {
                                    FontWeight.Normal
                                },
                            )
                            Spacer(Modifier.width(4.dp))
                            if (player.captured) {
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
