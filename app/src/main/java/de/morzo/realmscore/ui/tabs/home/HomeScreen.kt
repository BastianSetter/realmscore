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
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.morzo.realmscore.R
import de.morzo.realmscore.domain.stats.random.RandomStatResult
import de.morzo.realmscore.domain.stats.random.StatDestination
import de.morzo.realmscore.domain.stats.random.StatVisualization

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onNewGame: () -> Unit,
    onOpenGame: (String) -> Unit,
    onOpenSandbox: () -> Unit,
    onOpenFavorites: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenStat: (StatDestination?) -> Unit,
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
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.home_greeting, state.ownerName.orEmpty()),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onOpenSettings) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = stringResource(R.string.tab_settings),
                    )
                }
            }
        }
        item {
            GameEntryPoints(
                openGames = state.openGames,
                onNewGame = onNewGame,
                onOpenGame = onOpenGame,
            )
        }
        item {
            Card(
                onClick = onOpenSandbox,
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
                        onClick = onOpenFavorites,
                        contentPadding = PaddingValues(0.dp),
                    ) {
                        Text(stringResource(R.string.home_sandbox_view_favorites))
                    }
                }
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
                onNavigate = onOpenStat,
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
