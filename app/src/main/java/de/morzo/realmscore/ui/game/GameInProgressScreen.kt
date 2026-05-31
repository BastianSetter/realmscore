package de.morzo.realmscore.ui.game

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.morzo.realmscore.R
import de.morzo.realmscore.di.AppContainer
import de.morzo.realmscore.domain.model.GameMode
import de.morzo.realmscore.domain.usecase.game.GameState
import de.morzo.realmscore.ui.sandbox.components.MoveToSandboxIcon

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameInProgressScreen(
    container: AppContainer,
    gameId: String,
    onStartRound: (roundId: String) -> Unit,
    onMoveToSandbox: (gameId: String, roundId: String, profileId: String) -> Unit,
    onBack: () -> Unit,
) {
    val vm: GameInProgressViewModel = viewModel(
        factory = GameInProgressViewModel.Factory(
            gameId = gameId,
            getGameStateUseCase = container.getGameStateUseCase,
            roundRepo = container.roundRepository,
        ),
    )
    val uiState by vm.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.game_in_progress_title)) },
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
        when (val s = uiState) {
            GameInProgressUiState.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }
            }
            is GameInProgressUiState.Ready -> {
                GameContent(
                    state = s.state,
                    onStartRound = { vm.startNextRound(onStartRound) },
                    onContinueRound = { vm.continueOpenRound(onStartRound) },
                    onMoveToSandbox = { roundId, profileId ->
                        onMoveToSandbox(gameId, roundId, profileId)
                    },
                    modifier = Modifier.padding(padding),
                )
            }
        }
    }
}

@Composable
private fun GameContent(
    state: GameState,
    onStartRound: () -> Unit,
    onContinueRound: () -> Unit,
    onMoveToSandbox: (roundId: String, profileId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        GameHeader(state)
        Spacer(Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(Modifier.height(16.dp))
        ScoreTable(state = state, onMoveToSandbox = onMoveToSandbox)
        Spacer(Modifier.height(24.dp))
        if (state.hasOpenRound) {
            Button(
                onClick = onContinueRound,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.game_continue_round))
            }
        } else {
            Button(
                onClick = onStartRound,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.game_start_round))
            }
        }
    }
}

@Composable
private fun GameHeader(state: GameState) {
    Column {
        val modeText = when (state.game.mode) {
            GameMode.FIXED_ROUNDS -> stringResource(
                R.string.game_header_mode_fixed_rounds,
                state.game.targetRounds ?: 0,
            )
            GameMode.POINT_LIMIT -> stringResource(
                R.string.game_header_mode_point_limit,
                state.game.targetPoints ?: 0,
            )
        }
        Text(
            text = modeText,
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(8.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            state.participants.forEach { p ->
                ProfileAvatar(
                    name = p.profile.name,
                    colorArgb = p.profile.colorArgb,
                )
            }
        }
    }
}

@Composable
private fun ProfileAvatar(name: String, colorArgb: Int, size: Int = 32) {
    Box(
        modifier = Modifier
            .size(size.dp)
            .background(color = Color(colorArgb), shape = CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = name.take(1).uppercase(),
            color = Color.White,
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

@Composable
private fun ScoreTable(
    state: GameState,
    onMoveToSandbox: (roundId: String, profileId: String) -> Unit,
) {
    if (state.rounds.isEmpty()) {
        Text(
            text = stringResource(R.string.game_no_rounds_yet),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }

    val showProgress = state.game.mode == GameMode.POINT_LIMIT
    val sortedPlayers = state.participants.sortedByDescending {
        state.totalScoresByProfile[it.profile.id] ?: 0
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(8.dp),
            )
            .padding(8.dp)
            .horizontalScroll(rememberScrollState()),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            HeaderCell(
                text = stringResource(R.string.game_table_player),
                widthDp = PLAYER_COL_WIDTH,
                align = TextAlign.Start,
            )
            state.rounds.forEach { round ->
                HeaderCell(text = "R${round.roundNumber}", widthDp = NUM_COL_WIDTH)
            }
            HeaderCell(
                text = stringResource(R.string.game_total),
                widthDp = TOTAL_COL_WIDTH,
            )
            if (showProgress) {
                HeaderCell(
                    text = stringResource(R.string.game_progress),
                    widthDp = PROGRESS_COL_WIDTH,
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        sortedPlayers.forEach { p ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                PlayerCell(p.profile.name, p.profile.colorArgb)
                state.rounds.forEach { round ->
                    val score = state.resultsByRoundAndProfile[round.id to p.profile.id]
                    val canMoveToSandbox = score != null && round.completedAt != null
                    Row(
                        modifier = Modifier.width(NUM_COL_WIDTH.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = score?.toString() ?: "—",
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.End,
                            modifier = Modifier.weight(1f).padding(horizontal = 2.dp, vertical = 4.dp),
                        )
                        if (canMoveToSandbox) {
                            MoveToSandboxIcon(
                                onClick = { onMoveToSandbox(round.id, p.profile.id) },
                                compact = true,
                            )
                        }
                    }
                }
                BodyCell(
                    text = (state.totalScoresByProfile[p.profile.id] ?: 0).toString(),
                    widthDp = TOTAL_COL_WIDTH,
                    bold = true,
                )
                if (showProgress) {
                    val total = state.totalScoresByProfile[p.profile.id] ?: 0
                    val target = state.game.targetPoints ?: 0
                    BodyCell(
                        text = stringResource(R.string.game_progress_to_limit, total, target),
                        widthDp = PROGRESS_COL_WIDTH,
                    )
                }
            }
        }
    }
}

@Composable
private fun PlayerCell(name: String, colorArgb: Int) {
    Row(
        modifier = Modifier.width(PLAYER_COL_WIDTH.dp).padding(end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ProfileAvatar(name = name, colorArgb = colorArgb, size = 20)
        Spacer(Modifier.width(6.dp))
        Text(
            text = name,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
        )
    }
}

@Composable
private fun HeaderCell(text: String, widthDp: Int, align: TextAlign = TextAlign.End) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        textAlign = align,
        modifier = Modifier.width(widthDp.dp).padding(horizontal = 4.dp),
    )
}

@Composable
private fun BodyCell(text: String, widthDp: Int, bold: Boolean = false) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal,
        textAlign = TextAlign.End,
        modifier = Modifier.width(widthDp.dp).padding(horizontal = 4.dp, vertical = 4.dp),
    )
}

private const val PLAYER_COL_WIDTH = 120
private const val NUM_COL_WIDTH = 72
private const val TOTAL_COL_WIDTH = 64
private const val PROGRESS_COL_WIDTH = 88
