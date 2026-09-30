package de.morzo.realmscore.ui.handentry

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.morzo.realmscore.R
import de.morzo.realmscore.domain.model.CursedItem
import de.morzo.realmscore.ui.util.currentLocale

/**
 * Phase 30 (expansion part 1): the cursed items a player used (flipped) this round. Only their fixed
 * points matter, so this is a plain multi-select; an item recorded for another player is disabled,
 * since each exists once.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CursedItemsSection(
    items: List<CursedItem>,
    selectedKeys: Set<String>,
    usedByOthers: Set<String>,
    playerCount: Int,
    onToggle: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val locale = currentLocale()
    val total = items.filter { it.key in selectedKeys }.sumOf { it.pointsFor(playerCount) }
    Surface(modifier = modifier.fillMaxWidth(), tonalElevation = 1.dp) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = stringResource(R.string.cursed_items_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(R.string.cursed_items_hint),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(top = 8.dp),
            ) {
                items.sortedBy { it.displayName(locale) }.forEach { item ->
                    val selected = item.key in selectedKeys
                    FilterChip(
                        selected = selected,
                        onClick = { onToggle(item.key) },
                        enabled = selected || item.key !in usedByOthers,
                        label = { Text("${item.displayName(locale)} (${signed(item.pointsFor(playerCount))})") },
                    )
                }
            }
            Text(
                text = stringResource(
                    R.string.cursed_items_total,
                    if (selectedKeys.isEmpty()) stringResource(R.string.cursed_items_none) else signed(total),
                ),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

private fun signed(value: Int): String = if (value > 0) "+$value" else value.toString()
