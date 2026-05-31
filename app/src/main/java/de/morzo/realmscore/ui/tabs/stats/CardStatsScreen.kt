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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import de.morzo.realmscore.domain.stats.CardStats
import de.morzo.realmscore.ui.components.charts.BarChart
import de.morzo.realmscore.ui.components.suitColor
import de.morzo.realmscore.ui.components.suitLabelRes
import de.morzo.realmscore.ui.sandbox.components.MoveToSandboxIcon

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardStatsScreen(
    container: AppContainer,
    cardKey: String,
    onBack: () -> Unit,
    onMoveToSandbox: (gameId: String, roundId: String, profileId: String) -> Unit,
) {
    val vm: CardStatsViewModel = viewModel(
        factory = CardStatsViewModel.Factory(cardKey, container.statsRepository),
    )
    val state by vm.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.stats?.card?.nameDe ?: "") },
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
                else -> CardStatsBody(stats = state.stats!!, onMoveToSandbox = onMoveToSandbox)
            }
        }
    }
}

@Composable
private fun CardStatsBody(
    stats: CardStats,
    onMoveToSandbox: (gameId: String, roundId: String, profileId: String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        DataQualityBanner(stats.totalRoundsConsidered, stats.scannedRoundsCount)
        Column(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            CardHeader(stats)
            BasicsCard(stats)
            HighestContribution(stats = stats, onMoveToSandbox = onMoveToSandbox)
            PlayersSection(stats)
            PartnersSection(stats)
            ContributionHistogram(stats)
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun CardHeader(stats: CardStats) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .background(
                            color = suitColor(stats.card.suit),
                            shape = RoundedCornerShape(6.dp),
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = stringResource(suitLabelRes(stats.card.suit)),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "${stats.card.baseStrength}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = stats.card.nameDe,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stats.card.ruleTextDe,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun BasicsCard(stats: CardStats) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = stringResource(R.string.stats_card_section_basics),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.stats_card_in_hand, stats.inHandCount))
                Text(stringResource(R.string.stats_card_in_discard, stats.inDiscardCount))
                Text(
                    stringResource(
                        R.string.stats_card_hand_share,
                        (stats.handShare * 100).toInt(),
                    ),
                )
                Text(stringResource(R.string.stats_card_avg_contribution, stats.avgContribution))
            }
        }
    }
}

@Composable
private fun HighestContribution(
    stats: CardStats,
    onMoveToSandbox: (gameId: String, roundId: String, profileId: String) -> Unit,
) {
    val context = stats.highestContext ?: return
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(
                        R.string.stats_card_highest_single,
                        stats.highestSingleContribution,
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = stringResource(
                        R.string.stats_card_highest_context,
                        context.roundNumber,
                        context.profileName,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            MoveToSandboxIcon(
                onClick = {
                    onMoveToSandbox(context.gameId, context.roundId, context.profileId)
                },
            )
        }
    }
}

@Composable
private fun PlayersSection(stats: CardStats) {
    if (stats.playersWhoPlayedIt.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = stringResource(R.string.stats_card_section_players),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                stats.playersWhoPlayedIt.forEachIndexed { i, p ->
                    if (i > 0) HorizontalDivider(thickness = 0.5.dp)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier
                                .size(20.dp)
                                .background(
                                    color = Color(p.profile.colorArgb),
                                    shape = CircleShape,
                                ),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = p.profile.name,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            text = stringResource(R.string.stats_card_player_count, p.count),
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
private fun PartnersSection(stats: CardStats) {
    if (stats.frequentPartners.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = stringResource(R.string.stats_card_section_partners),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                stats.frequentPartners.forEachIndexed { i, item ->
                    if (i > 0) HorizontalDivider(thickness = 0.5.dp)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = item.card.nameDe,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            text = stringResource(R.string.stats_card_partner_count, item.count),
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
private fun ContributionHistogram(stats: CardStats) {
    if (stats.contributionBuckets.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = stringResource(R.string.stats_card_section_histogram),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        BarChart(data = stats.contributionBuckets.map { it.count })
    }
}
