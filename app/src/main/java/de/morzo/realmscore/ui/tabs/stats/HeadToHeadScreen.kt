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
import de.morzo.realmscore.di.AppContainer
import de.morzo.realmscore.domain.model.displayName
import de.morzo.realmscore.domain.stats.HeadToHeadStats
import de.morzo.realmscore.domain.stats.SharedGameEntry
import de.morzo.realmscore.ui.util.currentLocale
import de.morzo.realmscore.ui.util.formatRelativeDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HeadToHeadScreen(
    container: AppContainer,
    profileIdA: String,
    profileIdB: String,
    onBack: () -> Unit,
) {
    val vm: HeadToHeadViewModel = viewModel(
        factory = HeadToHeadViewModel.Factory(profileIdA, profileIdB, container.statsRepository),
    )
    val state by vm.uiState.collectAsStateWithLifecycle()
    val locale = currentLocale()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.stats_h2h_title)) },
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
                state.stats == null -> Text(stringResource(R.string.stats_empty_title))
                else -> HeadToHeadBody(
                    state.stats!!,
                    cardNameFor = { key ->
                        container.cardLookup.getByKey(key)?.displayName(locale) ?: key
                    },
                )
            }
        }
    }
}

@Composable
private fun HeadToHeadBody(
    stats: HeadToHeadStats,
    cardNameFor: (String) -> String,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        H2HHeader(stats)
        Text(
            text = stringResource(R.string.stats_h2h_games_together, stats.gamesTogether),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        if (stats.gamesTogether == 0) {
            Text(
                text = stringResource(R.string.stats_h2h_no_games),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Column
        }
        WinsAndAverages(stats)
        SharedGamesSection(stats.sharedGameHistory, stats.playerA.id, stats.playerB.id)
        CardComparisonSection(stats, cardNameFor)
    }
}

@Composable
private fun H2HHeader(stats: HeadToHeadStats) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AvatarBlock(name = stats.playerA.name, colorArgb = stats.playerA.colorArgb, modifier = Modifier.weight(1f))
        Text(text = "vs.", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        AvatarBlock(name = stats.playerB.name, colorArgb = stats.playerB.colorArgb, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun AvatarBlock(name: String, colorArgb: Int, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(56.dp)
                .background(color = Color(colorArgb), shape = CircleShape),
        )
        Spacer(Modifier.height(8.dp))
        Text(text = name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun WinsAndAverages(stats: HeadToHeadStats) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(horizontalAlignment = Alignment.Start) {
                    Text(stats.playerA.name, style = MaterialTheme.typography.labelMedium)
                    Text(
                        stringResource(R.string.stats_h2h_wins_a, stats.winsA),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        stringResource(R.string.stats_h2h_avg_a, stats.avgScoreA),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(stats.playerB.name, style = MaterialTheme.typography.labelMedium)
                    Text(
                        stringResource(R.string.stats_h2h_wins_b, stats.winsB),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        stringResource(R.string.stats_h2h_avg_b, stats.avgScoreB),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun SharedGamesSection(games: List<SharedGameEntry>, idA: String, idB: String) {
    if (games.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = stringResource(R.string.stats_h2h_section_games),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                games.forEachIndexed { i, game ->
                    if (i > 0) HorizontalDivider(thickness = 0.5.dp)
                    Column(Modifier.padding(vertical = 6.dp)) {
                        Text(
                            text = game.gameDisplayName ?: formatRelativeDate(game.startedAt),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                        val winnerMarker = when (game.winnerProfileId) {
                            idA -> "A"
                            idB -> "B"
                            else -> "—"
                        }
                        Text(
                            text = "${game.scoreA} vs. ${game.scoreB}  (Sieger: $winnerMarker)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CardComparisonSection(
    stats: HeadToHeadStats,
    cardNameFor: (String) -> String,
) {
    val combinedKeys = (stats.cardCountsA.keys + stats.cardCountsB.keys).toList()
    if (combinedKeys.isEmpty()) return
    val rows = combinedKeys.map { key ->
        val a = stats.cardCountsA[key] ?: 0
        val b = stats.cardCountsB[key] ?: 0
        Triple(key, a, b)
    }
        .sortedByDescending { kotlin.math.abs(it.second - it.third) }
        .take(8)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = stringResource(R.string.stats_h2h_section_cards),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = stringResource(
                R.string.stats_h2h_card_diff_label,
                stats.playerA.name,
                stats.playerB.name,
            ),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                rows.forEachIndexed { i, (key, a, b) ->
                    if (i > 0) HorizontalDivider(thickness = 0.5.dp)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(text = "$a", modifier = Modifier.width(32.dp))
                        Text(
                            text = cardNameFor(key),
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(text = "$b", modifier = Modifier.width(32.dp))
                    }
                }
            }
        }
    }
}
