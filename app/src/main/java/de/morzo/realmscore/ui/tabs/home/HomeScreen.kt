package de.morzo.realmscore.ui.tabs.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import de.morzo.realmscore.R
import de.morzo.realmscore.domain.stats.random.RandomStatResult
import de.morzo.realmscore.domain.stats.random.StatDestination
import de.morzo.realmscore.domain.stats.random.StatVisualization
import de.morzo.realmscore.ui.nav.Routes
import de.morzo.realmscore.ui.util.formatRelativeDate
import de.morzo.realmscore.ui.util.formatShortDate

@Composable
fun HomeScreen(
    navController: NavHostController,
    viewModel: HomeViewModel,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.onResume()
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars),
        contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Text(
                text = stringResource(R.string.home_greeting, state.ownerName.orEmpty()),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        item {
            Button(
                onClick = { navController.navigate(Routes.NEW_GAME) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            ) {
                Text(stringResource(R.string.home_new_game))
            }
        }
        item {
            Card(
                onClick = { navController.navigate(Routes.sandboxRouteEmpty()) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.home_sandbox_title),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.home_sandbox_body),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    TextButton(
                        onClick = { navController.navigate(Routes.sandboxFavoritesRoute()) },
                        contentPadding = PaddingValues(0.dp),
                    ) {
                        Text(stringResource(R.string.home_sandbox_view_favorites))
                    }
                }
            }
        }

        if (state.openGames.isNotEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.home_games_to_continue),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            item {
                OpenGamesRow(
                    games = state.openGames,
                    onOpen = { gameId ->
                        navController.navigate(Routes.gameRoute(gameId))
                    },
                )
            }
        }

        item {
            Text(
                text = stringResource(R.string.home_did_you_know),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        item {
            RandomStatBlock(
                result = state.randomStat,
                onNavigate = { destination -> navigateForStat(navController, destination) },
            )
        }
    }
}

private fun navigateForStat(
    navController: NavHostController,
    destination: StatDestination?,
) {
    when (destination) {
        is StatDestination.Player -> navController.navigate(Routes.playerStatsRoute(destination.profileId))
        is StatDestination.Card -> navController.navigate(Routes.cardStatsRoute(destination.cardKey))
        is StatDestination.HeadToHead ->
            navController.navigate(Routes.headToHeadRoute(destination.profileIdA, destination.profileIdB))
        StatDestination.Overview -> navController.navigate(Routes.TAB_STATS)
        null -> Unit
    }
}

@Composable
private fun OpenGamesRow(
    games: List<OpenGameCard>,
    onOpen: (String) -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(games, key = { it.gameId }) { game ->
            OpenGameCardItem(game = game, onClick = { onOpen(game.gameId) })
        }
    }
}

@Composable
private fun OpenGameCardItem(game: OpenGameCard, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.width(260.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = game.displayName
                    ?: stringResource(
                        R.string.home_open_game_fallback_name,
                        formatShortDate(game.startedAt),
                    ),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = formatRelativeDate(game.updatedAt),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            AvatarRow(participants = game.participants, maxVisible = 5)
            if (game.topStand.profileId != null && game.topStand.name != null) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(
                        R.string.home_open_game_top_stand,
                        game.topStand.name,
                        game.topStand.score,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun AvatarRow(participants: List<ParticipantBadge>, maxVisible: Int) {
    if (participants.isEmpty()) return
    val visible = participants.take(maxVisible)
    val overflow = participants.size - visible.size
    Row(verticalAlignment = Alignment.CenterVertically) {
        visible.forEach { p ->
            PlayerDot(colorArgb = p.colorArgb, size = 22.dp, initial = p.name.take(1).uppercase())
            Spacer(Modifier.width(4.dp))
        }
        if (overflow > 0) {
            Text(
                text = stringResource(R.string.home_avatar_overflow, overflow),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PlayerDot(
    colorArgb: Int,
    size: Dp,
    initial: String? = null,
) {
    Box(
        modifier = Modifier
            .size(size)
            .background(color = Color(colorArgb), shape = CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (initial != null) {
            Text(
                text = initial,
                color = Color.White,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun RandomStatBlock(
    result: RandomStatResult,
    onNavigate: (StatDestination?) -> Unit,
) {
    when (result) {
        RandomStatResult.NotEnoughData -> {
            Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.home_stats_not_enough_title),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.home_stats_not_enough_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        is RandomStatResult.Found -> {
            Card(
                onClick = { onNavigate(result.stat.tapDestination) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        text = result.stat.title,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Spacer(Modifier.height(8.dp))
                    StatVisualizationView(result.stat.visualization)
                }
            }
        }
    }
}

@Composable
private fun StatVisualizationView(viz: StatVisualization) {
    when (viz) {
        is StatVisualization.BigNumber -> {
            Text(
                text = viz.value,
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        is StatVisualization.BarChart -> {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.Bottom,
                modifier = Modifier.height(48.dp).fillMaxWidth(),
            ) {
                val max = (viz.values.maxOrNull() ?: 1f).coerceAtLeast(1f)
                viz.values.forEach { v ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height((48f * (v / max)).dp)
                            .background(
                                color = MaterialTheme.colorScheme.primary,
                                shape = MaterialTheme.shapes.extraSmall,
                            ),
                    )
                }
            }
        }
        is StatVisualization.LineChart -> {
            // MVP: render as small text values; full chart is out of scope here.
            Text(
                text = viz.points.joinToString(separator = " · ") { it.toInt().toString() },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
