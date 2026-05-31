package de.morzo.realmscore.ui.tabs.history

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.morzo.realmscore.R
import de.morzo.realmscore.di.AppContainer
import de.morzo.realmscore.ui.util.formatRelativeDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    container: AppContainer,
    onOpenGame: (gameId: String, isClosed: Boolean) -> Unit,
) {
    val context = LocalContext.current
    val vm: HistoryViewModel = viewModel(
        factory = HistoryViewModel.Factory(
            appContext = context.applicationContext,
            gameRepo = container.gameRepository,
            roundRepo = container.roundRepository,
            profileRepo = container.profileRepository,
        ),
    )
    val state by vm.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.history_title)) })
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            FilterBar(
                filters = state.filters,
                availablePlayers = state.availablePlayers,
                onSearchChange = vm::setSearchQuery,
                onToggleStatus = vm::toggleStatus,
                onTogglePlayer = vm::togglePlayerFilter,
                onClearPlayers = vm::clearPlayerFilter,
            )
            HorizontalDivider()

            when {
                state.isLoading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                state.items.isEmpty() -> EmptyHistoryState(state.filters)
                else -> {
                    LazyColumn(
                        contentPadding = PaddingValues(vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        items(state.items, key = { it.gameId }) { item ->
                            HistoryListItem(
                                item = item,
                                onClick = {
                                    onOpenGame(item.gameId, item.status != HistoryStatus.OPEN)
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterBar(
    filters: HistoryFilters,
    availablePlayers: List<ParticipantBadge>,
    onSearchChange: (String) -> Unit,
    onToggleStatus: (HistoryStatus) -> Unit,
    onTogglePlayer: (String) -> Unit,
    onClearPlayers: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        OutlinedTextField(
            value = filters.searchQuery,
            onValueChange = onSearchChange,
            label = { Text(stringResource(R.string.history_search)) },
            singleLine = true,
            trailingIcon = {
                if (filters.searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchChange("") }) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = stringResource(R.string.history_search_clear),
                        )
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        StatusFilterChips(filters.statuses, onToggleStatus)
        Spacer(Modifier.height(8.dp))
        PlayerFilterRow(
            availablePlayers = availablePlayers,
            selected = filters.playerProfileIds,
            onToggle = onTogglePlayer,
            onClear = onClearPlayers,
        )
    }
}

@Composable
private fun StatusFilterChips(
    selected: Set<HistoryStatus>,
    onToggle: (HistoryStatus) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        HistoryStatus.values().forEach { status ->
            val labelRes = when (status) {
                HistoryStatus.OPEN -> R.string.history_status_open
                HistoryStatus.COMPLETED -> R.string.history_status_completed
                HistoryStatus.ABANDONED -> R.string.history_status_abandoned
            }
            FilterChip(
                selected = status in selected,
                onClick = { onToggle(status) },
                label = { Text(stringResource(labelRes)) },
            )
        }
    }
}

@Composable
private fun PlayerFilterRow(
    availablePlayers: List<ParticipantBadge>,
    selected: Set<String>,
    onToggle: (String) -> Unit,
    onClear: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        AssistChip(
            onClick = { expanded = true },
            label = {
                val text = when {
                    selected.isEmpty() -> stringResource(R.string.history_player_filter)
                    else -> stringResource(R.string.history_player_filter_count, selected.size)
                }
                Text(text)
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Filled.FilterList,
                    contentDescription = null,
                    modifier = Modifier.size(AssistChipDefaults.IconSize),
                )
            },
            enabled = availablePlayers.isNotEmpty(),
        )
        if (selected.isNotEmpty()) {
            Spacer(Modifier.width(8.dp))
            AssistChip(
                onClick = onClear,
                label = { Text(stringResource(R.string.history_player_filter_clear)) },
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            availablePlayers.forEach { player ->
                val isSelected = player.profileId in selected
                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            PlayerDot(colorArgb = player.colorArgb, size = 16.dp)
                            Spacer(Modifier.width(8.dp))
                            Text(player.name)
                            if (isSelected) {
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = stringResource(R.string.history_player_filter_selected_mark),
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            }
                        }
                    },
                    onClick = { onToggle(player.profileId) },
                )
            }
        }
    }
}

@Composable
private fun HistoryListItem(
    item: HistoryItem,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            StatusBadge(item.status)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.displayName ?: item.fallbackName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = formatRelativeDate(item.startedAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                AvatarRow(item.participants, maxVisible = 5)
                item.winner?.let { winner ->
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(
                            R.string.history_winner_label,
                            winner.name,
                            winner.score,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                item.currentTopStand?.let { top ->
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(
                            R.string.history_top_stand_label,
                            top.name,
                            top.score,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusBadge(status: HistoryStatus) {
    val color = when (status) {
        HistoryStatus.OPEN -> MaterialTheme.colorScheme.tertiary
        HistoryStatus.COMPLETED -> MaterialTheme.colorScheme.primary
        HistoryStatus.ABANDONED -> MaterialTheme.colorScheme.outline
    }
    val label = stringResource(
        when (status) {
            HistoryStatus.OPEN -> R.string.history_status_open
            HistoryStatus.COMPLETED -> R.string.history_status_completed
            HistoryStatus.ABANDONED -> R.string.history_status_abandoned
        },
    )
    Box(
        modifier = Modifier
            .background(color = color, shape = RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text(
            text = label,
            color = Color.White,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
        )
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
                text = stringResource(R.string.history_avatar_overflow, overflow),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PlayerDot(
    colorArgb: Int,
    size: androidx.compose.ui.unit.Dp,
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
private fun EmptyHistoryState(filters: HistoryFilters) {
    val hasFilters = filters.searchQuery.isNotBlank() ||
        filters.playerProfileIds.isNotEmpty() ||
        filters.statuses.size != HistoryStatus.values().size
    val msg = stringResource(
        if (hasFilters) R.string.history_empty_filtered else R.string.history_empty,
    )
    Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(
            text = msg,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
