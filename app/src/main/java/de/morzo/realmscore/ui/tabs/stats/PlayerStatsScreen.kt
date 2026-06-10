package de.morzo.realmscore.ui.tabs.stats

import androidx.compose.foundation.background
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.morzo.realmscore.R
import de.morzo.realmscore.ui.util.displayName
import de.morzo.realmscore.di.AppContainer
import de.morzo.realmscore.domain.stats.OpponentStat
import de.morzo.realmscore.domain.stats.PlayerStats
import de.morzo.realmscore.domain.stats.RecentGameEntry
import de.morzo.realmscore.ui.components.charts.BarChart
import de.morzo.realmscore.ui.components.charts.LineChart
import de.morzo.realmscore.ui.sandbox.components.MoveToSandboxIcon
import de.morzo.realmscore.ui.util.formatRelativeDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerStatsScreen(
    container: AppContainer,
    profileId: String,
    onBack: () -> Unit,
    onOpenOpponent: (profileId: String) -> Unit,
    onMoveToSandbox: (gameId: String, roundId: String, profileId: String) -> Unit,
) {
    val vm: PlayerStatsViewModel = viewModel(
        factory = PlayerStatsViewModel.Factory(profileId, container.statsRepository),
    )
    val state by vm.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    val name = state.stats?.profile?.name.orEmpty()
                    Text(if (name.isEmpty()) "" else stringResource(R.string.stats_player_title, name))
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.stats_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            when {
                state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                state.stats == null -> Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) { Text(stringResource(R.string.stats_empty_title)) }
                else -> PlayerStatsBody(
                    stats = state.stats!!,
                    onOpenOpponent = onOpenOpponent,
                    onMoveToSandbox = { gameId, roundId ->
                        onMoveToSandbox(gameId, roundId, profileId)
                    },
                )
            }
        }
    }
}

@Composable
private fun PlayerStatsBody(
    stats: PlayerStats,
    onOpenOpponent: (String) -> Unit,
    onMoveToSandbox: (gameId: String, roundId: String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        PlayerHeader(stats)
        KeyMetricsGrid(stats)
        TrendSection(stats)
        HistogramSection(stats)
        FavoritesSection(stats)
        OpponentsSection(stats.opponents, onOpenOpponent)
        RecentGamesSection(stats.recentGames, onMoveToSandbox)
    }
}

@Composable
private fun PlayerHeader(stats: PlayerStats) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(48.dp)
                .background(color = Color(stats.profile.colorArgb), shape = CircleShape),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = stats.profile.name,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun KeyMetricsGrid(stats: PlayerStats) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricTile(
                label = stringResource(R.string.stats_player_games_played),
                value = stats.gamesPlayed.toString(),
                modifier = Modifier.weight(1f),
            )
            MetricTile(
                label = stringResource(R.string.stats_player_win_rate),
                value = stringResource(
                    R.string.stats_player_winrate_value,
                    (stats.winRate * 100).toInt(),
                ),
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricTile(
                label = stringResource(R.string.stats_player_avg_score),
                value = "%.1f".format(stats.avgScorePerHand),
                modifier = Modifier.weight(1f),
            )
            MetricTile(
                label = stringResource(R.string.stats_player_best_score),
                value = stats.bestSingleHandScore.toString(),
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun MetricTile(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
        Column(Modifier.padding(12.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun TrendSection(stats: PlayerStats) {
    Column {
        Text(
            text = stringResource(R.string.stats_player_section_trend),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(8.dp))
        if (stats.scoreTrend.size < 2) {
            Text(
                text = stringResource(R.string.stats_player_no_trend),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            LineChart(points = stats.scoreTrend.map { it.score.toFloat() })
        }
    }
}

@Composable
private fun HistogramSection(stats: PlayerStats) {
    Column {
        Text(
            text = stringResource(R.string.stats_player_section_histogram),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(8.dp))
        if (stats.handValueBuckets.isEmpty()) {
            Text(
                text = stringResource(R.string.stats_player_no_histogram),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            BarChart(data = stats.handValueBuckets.map { it.count })
        }
    }
}

@Composable
private fun FavoritesSection(stats: PlayerStats) {
    if (stats.favoriteCards.isEmpty()) return
    Column {
        Text(
            text = stringResource(R.string.stats_player_section_favorites),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(8.dp))
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                stats.favoriteCards.forEachIndexed { i, item ->
                    if (i > 0) HorizontalDivider(thickness = 0.5.dp)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = item.card.displayName(),
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            text = stringResource(R.string.stats_player_favorite_count, item.count),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OpponentsSection(
    opponents: List<OpponentStat>,
    onOpenOpponent: (String) -> Unit,
) {
    if (opponents.isEmpty()) return
    Column {
        Text(
            text = stringResource(R.string.stats_player_section_opponents),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(8.dp))
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                opponents.forEachIndexed { i, opp ->
                    if (i > 0) HorizontalDivider(thickness = 0.5.dp)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier
                                .size(24.dp)
                                .background(
                                    color = Color(opp.profile.colorArgb),
                                    shape = CircleShape,
                                ),
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = opp.profile.name,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = stringResource(
                                    R.string.stats_player_opponent_record,
                                    opp.winsForViewer,
                                    opp.winsForOpponent,
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = stringResource(
                                    R.string.stats_player_opponent_games,
                                    opp.gamesTogether,
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        TextButton(onClick = { onOpenOpponent(opp.profile.id) }) {
                            Text(text = ">", style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RecentGamesSection(
    recentGames: List<RecentGameEntry>,
    onMoveToSandbox: (gameId: String, roundId: String) -> Unit,
) {
    if (recentGames.isEmpty()) return
    Column {
        Text(
            text = stringResource(R.string.stats_player_section_recent),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(8.dp))
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                recentGames.forEachIndexed { i, game ->
                    if (i > 0) HorizontalDivider(thickness = 0.5.dp)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(vertical = 6.dp),
                        ) {
                            Text(
                                text = game.gameDisplayName ?: formatRelativeDate(game.startedAt),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                            )
                            val subtitle = if (game.viewerWon) {
                                stringResource(R.string.stats_player_recent_won)
                            } else {
                                game.winnerName?.let {
                                    stringResource(R.string.stats_player_recent_won_by, it)
                                } ?: formatRelativeDate(game.startedAt)
                            }
                            Text(
                                text = "$subtitle · ${game.viewerScore} P.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        val targetRoundId = game.sandboxRoundId
                        if (targetRoundId != null) {
                            MoveToSandboxIcon(
                                onClick = { onMoveToSandbox(game.gameId, targetRoundId) },
                            )
                        }
                    }
                }
            }
        }
    }
}
