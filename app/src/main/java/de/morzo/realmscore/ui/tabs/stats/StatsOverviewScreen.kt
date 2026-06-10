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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.morzo.realmscore.R
import de.morzo.realmscore.ui.util.displayName
import de.morzo.realmscore.di.AppContainer
import de.morzo.realmscore.domain.stats.CardWithCount
import de.morzo.realmscore.domain.stats.GlobalStats
import de.morzo.realmscore.domain.stats.PlayerRankingEntry

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsOverviewScreen(
    container: AppContainer,
    onOpenPlayer: (profileId: String) -> Unit,
    onOpenCard: (cardKey: String) -> Unit,
    onOpenCardOverview: () -> Unit,
) {
    val vm: StatsOverviewViewModel = viewModel(
        factory = StatsOverviewViewModel.Factory(
            statsRepository = container.statsRepository,
            gameRepository = container.gameRepository,
        ),
    )
    val state by vm.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.stats_overview_title)) })
        },
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            when {
                state.isLoading && state.overview == null -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                state.overview == null || state.isEmpty -> EmptyStatsBody(
                    gamesNeeded = (STATS_MIN_GAMES - (state.overview?.totalClosedGames ?: 0))
                        .coerceAtLeast(1),
                )
                else -> OverviewBody(
                    overview = state.overview!!,
                    onOpenPlayer = onOpenPlayer,
                    onOpenCard = onOpenCard,
                    onOpenCardOverview = onOpenCardOverview,
                )
            }
        }
    }
}

@Composable
private fun OverviewBody(
    overview: de.morzo.realmscore.domain.stats.StatsOverview,
    onOpenPlayer: (String) -> Unit,
    onOpenCard: (String) -> Unit,
    onOpenCardOverview: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        GlobalStatsCard(overview.global)
        PlayerRankingCard(overview.playerRanking, onOpenPlayer)
        CardHitsCard(overview.cardHits, onOpenCard)
        TextButton(
            onClick = onOpenCardOverview,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.stats_overview_all_cards))
        }
    }
}

@Composable
private fun GlobalStatsCard(global: GlobalStats) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(R.string.stats_overview_global),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            StatRow(stringResource(R.string.stats_global_games), global.totalGamesPlayed.toString())
            StatRow(stringResource(R.string.stats_global_rounds), global.totalRoundsPlayed.toString())
            StatRow(stringResource(R.string.stats_global_players), global.uniquePlayers.toString())
        }
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun PlayerRankingCard(
    ranking: List<PlayerRankingEntry>,
    onOpenPlayer: (String) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.stats_overview_player_ranking),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(8.dp))
            if (ranking.isEmpty()) {
                Text(
                    stringResource(R.string.stats_player_ranking_no_games),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                return@Column
            }
            ranking.forEachIndexed { index, entry ->
                if (index > 0) HorizontalDivider(thickness = 0.5.dp)
                PlayerRankingRow(
                    rank = index + 1,
                    entry = entry,
                    onClick = { onOpenPlayer(entry.profile.id) },
                )
            }
        }
    }
}

@Composable
private fun PlayerRankingRow(
    rank: Int,
    entry: PlayerRankingEntry,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "$rank.",
            modifier = Modifier.width(28.dp),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        PlayerDot(colorArgb = entry.profile.colorArgb)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.profile.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(
                    R.string.stats_player_ranking_winrate,
                    (entry.winRate * 100).toInt(),
                    entry.gamesPlayed,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = onClick) {
            Text(text = ">", style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
internal fun PlayerDot(colorArgb: Int, size: androidx.compose.ui.unit.Dp = 24.dp) {
    Box(
        modifier = Modifier
            .size(size)
            .background(color = Color(colorArgb), shape = CircleShape),
    )
}

@Composable
private fun CardHitsCard(
    hits: de.morzo.realmscore.domain.stats.CardHits,
    onOpenCard: (String) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.stats_overview_card_hits),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(8.dp))
            CardHitRow(
                titleRes = R.string.stats_card_hits_most_played,
                cardName = hits.mostPlayed?.card?.displayName(),
                secondary = hits.mostPlayed?.count?.let {
                    stringResource(R.string.stats_card_hits_count, it)
                },
                onClick = { hits.mostPlayed?.card?.key?.let(onOpenCard) },
            )
            HorizontalDivider(thickness = 0.5.dp)
            CardHitRow(
                titleRes = R.string.stats_card_hits_rarest,
                cardName = hits.rarestPlayed?.card?.displayName(),
                secondary = hits.rarestPlayed?.count?.let {
                    stringResource(R.string.stats_card_hits_count, it)
                },
                onClick = { hits.rarestPlayed?.card?.key?.let(onOpenCard) },
            )
            HorizontalDivider(thickness = 0.5.dp)
            CardHitRow(
                titleRes = R.string.stats_card_hits_most_valuable,
                cardName = hits.mostValuable?.first?.displayName(),
                secondary = hits.mostValuable?.let { (_, avg, _) ->
                    stringResource(R.string.stats_card_hits_avg, avg)
                },
                onClick = { hits.mostValuable?.first?.key?.let(onOpenCard) },
            )
        }
    }
}

@Composable
private fun CardHitRow(
    titleRes: Int,
    cardName: String?,
    secondary: String?,
    onClick: () -> Unit,
) {
    val name = cardName ?: stringResource(R.string.stats_card_no_contributions)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(titleRes),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
            )
            if (secondary != null) {
                Text(
                    text = secondary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (cardName != null) {
            TextButton(onClick = onClick) {
                Text(text = ">", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
private fun EmptyStatsBody(gamesNeeded: Int) {
    Box(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(R.string.stats_empty_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = pluralStringResource(R.plurals.stats_empty_body, gamesNeeded, gamesNeeded),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
