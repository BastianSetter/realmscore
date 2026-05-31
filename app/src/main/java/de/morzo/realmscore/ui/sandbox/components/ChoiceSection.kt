package de.morzo.realmscore.ui.sandbox.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.morzo.realmscore.R
import de.morzo.realmscore.domain.model.CardDefinition
import de.morzo.realmscore.domain.model.Suit

@Composable
fun ChoiceSection(
    handCards: List<CardDefinition>,
    islandTargetKey: String?,
    fountainSourceKey: String?,
    onIslandChange: (String?) -> Unit,
    onFountainChange: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val islandPresent = handCards.any { it.key == "island" }
    val fountainPresent = handCards.any { it.key == "fountain_of_life" }
    if (!islandPresent && !fountainPresent) return

    Surface(
        modifier = modifier.fillMaxWidth(),
        tonalElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.sandbox_choice_section_title),
                style = MaterialTheme.typography.titleMedium,
            )
            if (islandPresent) {
                val candidates = handCards.filter {
                    it.key != "island" && it.suit in setOf(Suit.FLOOD, Suit.FLAME)
                }
                ChoiceRow(
                    label = stringResource(R.string.sandbox_choice_island),
                    currentKey = islandTargetKey,
                    candidates = candidates,
                    onSelected = onIslandChange,
                )
            }
            if (fountainPresent) {
                val eligible = setOf(Suit.WEAPON, Suit.FLOOD, Suit.FLAME, Suit.LAND, Suit.WEATHER)
                val candidates = handCards.filter {
                    it.key != "fountain_of_life" && it.suit in eligible
                }
                ChoiceRow(
                    label = stringResource(R.string.sandbox_choice_fountain),
                    currentKey = fountainSourceKey,
                    candidates = candidates,
                    onSelected = onFountainChange,
                )
            }
        }
    }
}

@Composable
private fun ChoiceRow(
    label: String,
    currentKey: String?,
    candidates: List<CardDefinition>,
    onSelected: (String?) -> Unit,
) {
    val currentName = candidates.firstOrNull { it.key == currentKey }?.nameDe
        ?: stringResource(R.string.sandbox_joker_unset)
    var expanded by remember { mutableStateOf(false) }
    AssistChip(
        onClick = { expanded = true },
        label = { Text("$label: $currentName") },
    )
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.sandbox_joker_unset)) },
            onClick = { onSelected(null); expanded = false },
        )
        candidates.forEach { card ->
            DropdownMenuItem(
                text = { Text(card.nameDe) },
                onClick = { onSelected(card.key); expanded = false },
            )
        }
    }
}
