package de.morzo.realmscore.ui.sandbox.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.morzo.realmscore.R
import de.morzo.realmscore.domain.model.CardDefinition

/**
 * Joker-like selection field for the Necromancer (Phase 17.1). Only shown when a Necromancer is
 * in the hand. The pulled (non-Wizard) card is scored as an extra 8th card by the engine.
 */
@Composable
fun NecromancerSection(
    pickedCard: CardDefinition?,
    onPickCard: () -> Unit,
    onClearPick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        tonalElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.necromancer_section_title),
                style = MaterialTheme.typography.titleMedium,
            )
            OutlinedCard(onClick = onPickCard, modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(start = 12.dp, top = 4.dp, bottom = 4.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (pickedCard != null) {
                        Text(
                            text = pickedCard.nameDe,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        IconButton(onClick = onClearPick) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = stringResource(R.string.necromancer_clear),
                            )
                        }
                    } else {
                        Text(
                            text = stringResource(R.string.necromancer_pick_hint),
                            modifier = Modifier
                                .weight(1f)
                                .padding(vertical = 12.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        IconButton(onClick = onPickCard) {
                            Icon(imageVector = Icons.Default.Add, contentDescription = null)
                        }
                    }
                }
            }
            Text(
                text = stringResource(R.string.necromancer_suit_hint),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
