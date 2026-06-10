package de.morzo.realmscore.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import de.morzo.realmscore.R
import de.morzo.realmscore.domain.model.CardDefinition
import de.morzo.realmscore.domain.model.Suit
import de.morzo.realmscore.ui.util.displayName

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardPicker(
    allCards: List<CardDefinition>,
    onCardChosen: (CardDefinition) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    excludedKeys: Set<String> = emptySet(),
    showClearButton: Boolean = false,
    onClear: (() -> Unit)? = null,
    // When both are non-null, the title shows a "(scanned/total)" progress hint — used during the
    // continuous-fill capture of a hand/Mittelfeld so the user sees how many cards are already in.
    scannedCount: Int? = null,
    totalCount: Int? = null,
) {
    var query by rememberSaveable { mutableStateOf("") }
    // Single-select suit filter. null = "Alle" (no filter).
    // See docs/ui-decisions/cardpicker-suit-filter.md for the rationale and the
    // future option to toggle back to multi-select behaviour.
    var selectedSuit by rememberSaveable { mutableStateOf<Suit?>(null) }

    // Only offer suit filters that actually have selectable cards (e.g. the Necromancer picker
    // receives a pre-filtered Army/Wizard/Leader/Beast list, so the other suits are hidden).
    val availableSuits = remember(allCards, excludedKeys) {
        Suit.entries.filter { suit -> allCards.any { it.suit == suit && it.key !in excludedKeys } }
    }
    // A previously selected suit may no longer be offered after the card set changes → treat as "Alle".
    val effectiveSuit = selectedSuit?.takeIf { it in availableSuits }

    val filtered = remember(query, effectiveSuit, allCards, excludedKeys) {
        val normalizedQuery = query.trim().lowercase()
        allCards
            .asSequence()
            .filter { it.key !in excludedKeys }
            .filter { effectiveSuit == null || it.suit == effectiveSuit }
            .filter {
                normalizedQuery.isEmpty() ||
                    it.nameDe.lowercase().contains(normalizedQuery) ||
                    it.nameEn?.lowercase()?.contains(normalizedQuery) == true
            }
            .toList()
    }

    // Full-screen dialog (Phase 18.2): the picker used to be a ModalBottomSheet "pull-up". A
    // full-screen Dialog keeps the existing search / suit-column / card-list content intact while
    // giving it the whole screen. Back press maps to onDismiss via the Dialog.
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Scaffold(
            modifier = modifier.fillMaxSize(),
            topBar = {
                TopAppBar(
                    title = {
                        val base = stringResource(R.string.card_picker_title)
                        Text(
                            if (scannedCount != null && totalCount != null) {
                                "$base ($scannedCount/$totalCount)"
                            } else {
                                base
                            },
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = stringResource(R.string.card_picker_close),
                            )
                        }
                    },
                )
            },
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 16.dp),
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text(stringResource(R.string.card_picker_search)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (showClearButton && onClear != null) {
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = onClear,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(imageVector = Icons.Default.Delete, contentDescription = null)
                        Spacer(Modifier.size(8.dp))
                        Text(stringResource(R.string.sandbox_remove_card))
                    }
                }
                Spacer(Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SuitColumn(
                        suits = availableSuits,
                        selected = effectiveSuit,
                        onSelect = { selectedSuit = it },
                        modifier = Modifier
                            .width(128.dp)
                            .fillMaxHeight(),
                    )
                    CardColumn(
                        cards = filtered,
                        onCardClick = onCardChosen,
                        modifier = Modifier
                            .fillMaxHeight()
                            .weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun SuitColumn(
    suits: List<Suit>,
    selected: Suit?,
    onSelect: (Suit?) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        item(key = "all") {
            SuitListItem(
                label = stringResource(R.string.suit_all),
                accentColor = null,
                selected = selected == null,
                onClick = { onSelect(null) },
            )
        }
        items(suits, key = { it.name }) { suit ->
            SuitListItem(
                label = stringResource(suitLabelRes(suit)),
                accentColor = suitColor(suit),
                selected = selected == suit,
                onClick = { onSelect(suit) },
            )
        }
    }
}

@Composable
private fun SuitListItem(
    label: String,
    accentColor: androidx.compose.ui.graphics.Color?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val borderColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    val borderWidth = if (selected) 2.dp else 1.dp
    val background = if (selected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surface
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(background)
            .border(width = borderWidth, color = borderColor, shape = RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(width = 8.dp, height = 24.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(accentColor ?: MaterialTheme.colorScheme.outline),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun CardColumn(
    cards: List<CardDefinition>,
    onCardClick: (CardDefinition) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (cards.isEmpty()) {
        Box(
            modifier = modifier.fillMaxWidth(),
            contentAlignment = Alignment.TopStart,
        ) {
            Text(
                text = stringResource(R.string.card_picker_no_results),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 16.dp),
            )
        }
        return
    }
    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items(cards, key = { it.key }) { card ->
            CardPickerItem(card = card, onClick = { onCardClick(card) })
        }
    }
}

@Composable
private fun CardPickerItem(card: CardDefinition, onClick: () -> Unit) {
    val onColor = suitOnColor(card.suit)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(suitColor(card.suit))
            .clickable(onClick = onClick)
            .padding(PaddingValues(horizontal = 12.dp)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = card.displayName(),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = onColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = card.baseStrength.toString(),
            style = MaterialTheme.typography.labelLarge,
            color = onColor,
        )
    }
}
