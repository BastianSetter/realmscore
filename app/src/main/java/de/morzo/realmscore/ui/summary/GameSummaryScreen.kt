package de.morzo.realmscore.ui.summary

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.morzo.realmscore.R
import de.morzo.realmscore.di.AppContainer
import de.morzo.realmscore.domain.model.Profile
import de.morzo.realmscore.ui.sandbox.components.MoveToSandboxIcon

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameSummaryScreen(
    container: AppContainer,
    gameId: String,
    onCloseGameDone: () -> Unit,
    onNewGame: (continueSession: Boolean) -> Unit,
    onShowStats: () -> Unit,
    onBackToGame: () -> Unit,
    onMoveToSandbox: (gameId: String, roundId: String, profileId: String) -> Unit,
    onBack: () -> Unit,
) {
    val vm: GameSummaryViewModel = viewModel(
        factory = GameSummaryViewModel.Factory(
            gameId = gameId,
            gameRepo = container.gameRepository,
            roundRepo = container.roundRepository,
            profileRepo = container.profileRepository,
            p2p = container.p2pSessionRepository,
        ),
    )
    val state by vm.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.game_summary_title)) },
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
            WinnerPodium(state.podium)

            Spacer(Modifier.height(24.dp))

            state.gameStats?.let { GameStatsBlock(it) }

            Spacer(Modifier.height(24.dp))

            RoundsTable(
                rows = state.rounds,
                players = state.players,
                totals = state.totalsByProfile,
                onMoveToSandbox = { roundId, profileId ->
                    onMoveToSandbox(gameId, roundId, profileId)
                },
            )

            Spacer(Modifier.height(24.dp))

            if (state.isClosed) {
                OutlinedButton(
                    onClick = onShowStats,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.game_summary_show_stats))
                }
                Spacer(Modifier.height(8.dp))
                // The game is already closed here (closing now happens on the round-summary "Spiel
                // abschließen"). The host/solo primary action starts the next game with the same
                // players + settings (and, when hosting, brings the joined phones along). A joined
                // phone can't start the next game, so "Zurück zum Hauptmenü" is its only action — the
                // host pulls it into the next game via the central OpenRound signal regardless.
                // Either way, leaving to the menu tears down the P2P session (leaveToMenu) so the user
                // returns to a fresh, connection-free state.
                if (!state.isP2pClient) {
                    Button(
                        onClick = { vm.prepareNewGame(onNewGame) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.game_summary_new_game))
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { vm.leaveToMenu(onCloseGameDone) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.game_summary_back_home))
                    }
                } else {
                    Button(
                        onClick = { vm.leaveToMenu(onCloseGameDone) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.game_summary_back_home))
                    }
                }
            } else {
                Button(
                    onClick = { vm.closeAndNavigate(onCloseGameDone) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.game_summary_close_game))
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onBackToGame,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.game_summary_back_to_game))
                }
            }
        }
    }
}

@Composable
private fun WinnerPodium(entries: List<PodiumEntry>) {
    if (entries.isEmpty()) return

    val ordered = entries.sortedWith(
        compareBy({ displayOrderForRank(it.rank) }, { it.name })
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(240.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        ordered.forEach { entry ->
            PodiumStep(entry = entry, height = heightForRank(entry.rank))
        }
    }
}

private fun displayOrderForRank(rank: Int): Int = when (rank) {
    2 -> 0
    1 -> 1
    3 -> 2
    else -> 3 + rank
}

private fun heightForRank(rank: Int): Dp = when (rank) {
    1 -> 120.dp
    2 -> 90.dp
    3 -> 70.dp
    else -> 50.dp
}

@Composable
private fun PodiumStep(entry: PodiumEntry, height: Dp) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(96.dp),
    ) {
        if (entry.rank == 1) {
            Icon(
                imageVector = Icons.Filled.EmojiEvents,
                contentDescription = null,
                tint = Color(0xFFFFB300),
                modifier = Modifier.size(28.dp),
            )
            Spacer(Modifier.height(4.dp))
        } else {
            Spacer(Modifier.height(32.dp))
        }
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(color = Color(entry.colorArgb), shape = CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = entry.name.take(2).uppercase(),
                color = Color.White,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = entry.name,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
        Text(
            text = stringResource(R.string.podium_score, entry.totalScore),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .height(height)
                .fillMaxWidth()
                .background(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "${entry.rank}.",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun GameStatsBlock(stats: GameStats) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(12.dp),
            )
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = stringResource(R.string.game_stats_section_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(4.dp))
        Text(stringResource(R.string.game_stats_rounds, stats.roundCount))
        if (stats.highestSingleHandPlayer.isNotEmpty()) {
            Text(
                stringResource(
                    R.string.game_stats_highest_hand,
                    stats.highestSingleHandPlayer,
                    stats.highestSingleHandScore,
                    stats.highestSingleHandRound,
                )
            )
        }
        if (stats.closestRoundNumber > 0) {
            Text(
                stringResource(
                    R.string.game_stats_closest_round,
                    stats.closestRoundNumber,
                    stats.closestRoundDifference,
                )
            )
        }
    }
}

@Composable
private fun RoundsTable(
    rows: List<RoundsTableRow>,
    players: List<Profile>,
    totals: Map<String, Int>,
    onMoveToSandbox: (roundId: String, profileId: String) -> Unit,
) {
    if (rows.isEmpty() || players.isEmpty()) return

    val cellWidth = 84.dp
    val roundLabelWidth = 64.dp

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(12.dp),
            )
            .padding(8.dp),
    ) {
        Text(
            text = stringResource(R.string.game_summary_rounds_table_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
        )

        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
        ) {
            Column {
                TableHeaderCell(
                    width = roundLabelWidth,
                    text = stringResource(R.string.game_summary_round_label),
                )
                rows.forEach { row ->
                    TableCell(
                        width = roundLabelWidth,
                        text = row.roundNumber.toString(),
                        bold = true,
                    )
                }
                HorizontalDivider()
                TableCell(
                    width = roundLabelWidth,
                    text = stringResource(R.string.game_summary_total_label),
                    bold = true,
                )
            }
            players.forEach { player ->
                Column {
                    Box(
                        modifier = Modifier
                            .width(cellWidth)
                            .height(48.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .background(
                                    color = Color(player.colorArgb),
                                    shape = CircleShape,
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = player.name.take(1).uppercase(),
                                color = Color.White,
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    }
                    rows.forEach { row ->
                        val score = row.scoresByProfile[player.id]
                        val isWinner = row.winnerProfileId == player.id
                        TableScoreCell(
                            width = cellWidth,
                            score = score,
                            isWinner = isWinner,
                            onMoveToSandbox = if (score != null) {
                                { onMoveToSandbox(row.roundId, player.id) }
                            } else null,
                        )
                    }
                    HorizontalDivider()
                    TableCell(
                        width = cellWidth,
                        text = (totals[player.id] ?: 0).toString(),
                        bold = true,
                    )
                }
            }
        }
    }
}

@Composable
private fun TableHeaderCell(width: Dp, text: String) {
    Box(
        modifier = Modifier
            .width(width)
            .height(48.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun TableCell(width: Dp, text: String, bold: Boolean = false) {
    Box(
        modifier = Modifier
            .width(width)
            .height(40.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun TableScoreCell(
    width: Dp,
    score: Int?,
    isWinner: Boolean,
    onMoveToSandbox: (() -> Unit)?,
) {
    Box(
        modifier = Modifier
            .width(width)
            .height(40.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = score?.toString() ?: "–",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isWinner) FontWeight.SemiBold else FontWeight.Normal,
            )
            if (isWinner) {
                Spacer(Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Filled.EmojiEvents,
                    contentDescription = stringResource(R.string.round_summary_winner_badge),
                    tint = Color(0xFFFFB300),
                    modifier = Modifier.size(14.dp),
                )
            }
            if (onMoveToSandbox != null) {
                MoveToSandboxIcon(onClick = onMoveToSandbox, compact = true)
            }
        }
    }
}
