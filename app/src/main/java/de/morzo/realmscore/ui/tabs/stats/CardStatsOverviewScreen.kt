package de.morzo.realmscore.ui.tabs.stats

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.morzo.realmscore.R
import de.morzo.realmscore.di.AppContainer
import de.morzo.realmscore.domain.stats.CardStatsRow
import de.morzo.realmscore.domain.stats.CardStatsSort
import de.morzo.realmscore.ui.components.charts.BarPart
import de.morzo.realmscore.ui.components.charts.StackedBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardStatsOverviewScreen(
    container: AppContainer,
    onBack: () -> Unit,
    onOpenCard: (cardKey: String) -> Unit,
) {
    val vm: CardStatsOverviewViewModel = viewModel(
        factory = CardStatsOverviewViewModel.Factory(container.statsRepository),
    )
    val state by vm.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.stats_cards_title)) },
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
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            SortDropdown(state.sortBy, vm::changeSort)
            DataQualityBanner(state.totalRounds, state.scannedRounds)
            HorizontalDivider()
            when {
                state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                else -> LazyColumn(
                    contentPadding = PaddingValues(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    items(state.rows, key = { it.card.key }) { row ->
                        CardStatsRowItem(row, onClick = { onOpenCard(row.card.key) })
                    }
                }
            }
        }
    }
}

@Composable
private fun SortDropdown(current: CardStatsSort, onSelect: (CardStatsSort) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val label = when (current) {
        CardStatsSort.POPULARITY -> stringResource(R.string.stats_cards_sort_popularity)
        CardStatsSort.AVG_CONTRIBUTION -> stringResource(R.string.stats_cards_sort_avg)
        CardStatsSort.NAME -> stringResource(R.string.stats_cards_sort_name)
    }
    Row(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.stats_cards_sort_label),
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.width(8.dp))
        OutlinedButton(onClick = { expanded = true }) {
            Text(label)
            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.stats_cards_sort_popularity)) },
                onClick = {
                    expanded = false
                    onSelect(CardStatsSort.POPULARITY)
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.stats_cards_sort_avg)) },
                onClick = {
                    expanded = false
                    onSelect(CardStatsSort.AVG_CONTRIBUTION)
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.stats_cards_sort_name)) },
                onClick = {
                    expanded = false
                    onSelect(CardStatsSort.NAME)
                },
            )
        }
    }
}

@Composable
internal fun DataQualityBanner(totalRounds: Int, scannedRounds: Int) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Column {
            Text(
                text = stringResource(
                    R.string.stats_card_data_warning,
                    totalRounds,
                    scannedRounds,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            if (totalRounds > 0 && scannedRounds * 4 < totalRounds) {
                Text(
                    text = stringResource(R.string.stats_card_low_data_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
    }
}

@Composable
private fun CardStatsRowItem(row: CardStatsRow, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SuitBadge(row.card.suit)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = row.card.nameDe,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(4.dp))
                StackedBar(
                    parts = listOf(
                        BarPart(
                            label = "hand",
                            value = row.inHandCount.toFloat(),
                            color = MaterialTheme.colorScheme.primary,
                        ),
                        BarPart(
                            label = "discard",
                            value = row.inDiscardCount.toFloat(),
                            color = MaterialTheme.colorScheme.tertiary,
                        ),
                    ),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.stats_card_in_hand, row.inHandCount) +
                        " · " +
                        stringResource(R.string.stats_card_avg_contribution, row.avgContribution),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SuitBadge(suit: de.morzo.realmscore.domain.model.Suit) {
    val label = stringResource(de.morzo.realmscore.ui.components.suitLabelRes(suit))
    Box(
        modifier = Modifier
            .background(
                color = de.morzo.realmscore.ui.components.suitColor(suit),
                shape = RoundedCornerShape(6.dp),
            )
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = de.morzo.realmscore.ui.components.suitOnColor(suit),
        )
    }
}
