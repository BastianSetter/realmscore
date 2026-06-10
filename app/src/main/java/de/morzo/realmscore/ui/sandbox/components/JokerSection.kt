package de.morzo.realmscore.ui.sandbox.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.morzo.realmscore.R
import de.morzo.realmscore.domain.model.CardDefinition
import de.morzo.realmscore.domain.model.JokerType
import de.morzo.realmscore.domain.model.Suit
import de.morzo.realmscore.domain.model.displayName
import de.morzo.realmscore.domain.scoring.JokerAssignment
import de.morzo.realmscore.ui.util.currentLocale

private val MIRAGE_SUITS = setOf(Suit.ARMY, Suit.LAND, Suit.WEATHER, Suit.FLOOD, Suit.FLAME)
private val SHAPESHIFTER_SUITS = setOf(Suit.ARTIFACT, Suit.LEADER, Suit.WIZARD, Suit.WEAPON, Suit.BEAST)
private val BOOK_OF_CHANGES_SUITS = Suit.entries.filter { it != Suit.WILD }

private const val ISLAND_KEY = "island"
private const val FOUNTAIN_KEY = "fountain_of_life"
private val ISLAND_SUITS = setOf(Suit.FLOOD, Suit.FLAME)
private val FOUNTAIN_SUITS = setOf(Suit.WEAPON, Suit.FLOOD, Suit.FLAME, Suit.LAND, Suit.WEATHER)

/**
 * Shows the joker assignments together with the Island / Fountain-of-Life picks (Phase 18.2): these
 * two are [de.morzo.realmscore.domain.scoring.PlayerChoices], not real jokers, but the [OptimalSolver]
 * brute-forces them just like jokers, so they live in the same section and share the "Optimal" button.
 */
@Composable
fun JokerSection(
    jokers: List<CardDefinition>,
    assignments: Map<String, JokerAssignment>,
    allCards: List<CardDefinition>,
    handCards: List<CardDefinition>,
    onAssignmentChange: (String, JokerAssignment?) -> Unit,
    modifier: Modifier = Modifier,
    islandTargetKey: String? = null,
    fountainSourceKey: String? = null,
    onIslandChange: (String?) -> Unit = {},
    onFountainChange: (String?) -> Unit = {},
    onOptimal: (() -> Unit)? = null,
    optimalRunning: Boolean = false,
) {
    val islandPresent = handCards.any { it.key == ISLAND_KEY }
    val fountainPresent = handCards.any { it.key == FOUNTAIN_KEY }
    if (jokers.isEmpty() && !islandPresent && !fountainPresent) return
    Surface(
        modifier = modifier.fillMaxWidth(),
        tonalElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.sandbox_joker_section_title),
                style = MaterialTheme.typography.titleMedium,
            )
            jokers.forEach { joker ->
                JokerRow(
                    joker = joker,
                    assignment = assignments[joker.key],
                    allCards = allCards,
                    handCards = handCards,
                    onChange = { onAssignmentChange(joker.key, it) },
                )
            }
            if (islandPresent) {
                ChoiceRow(
                    label = stringResource(R.string.sandbox_choice_island),
                    currentKey = islandTargetKey,
                    candidates = handCards.filter { it.key != ISLAND_KEY && it.suit in ISLAND_SUITS },
                    onSelected = onIslandChange,
                )
            }
            if (fountainPresent) {
                ChoiceRow(
                    label = stringResource(R.string.sandbox_choice_fountain),
                    currentKey = fountainSourceKey,
                    candidates = handCards.filter { it.key != FOUNTAIN_KEY && it.suit in FOUNTAIN_SUITS },
                    onSelected = onFountainChange,
                )
            }
            if (onOptimal != null) {
                Button(
                    onClick = onOptimal,
                    enabled = !optimalRunning,
                ) {
                    if (optimalRunning) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Text(stringResource(R.string.player_hand_optimal))
                    }
                }
            }
        }
    }
}

@Composable
private fun JokerRow(
    joker: CardDefinition,
    assignment: JokerAssignment?,
    allCards: List<CardDefinition>,
    handCards: List<CardDefinition>,
    onChange: (JokerAssignment?) -> Unit,
) {
    val targets = remember(joker.jokerType, allCards, handCards) {
        val handKeys = handCards.map { it.key }.toSet()
        when (joker.jokerType) {
            JokerType.DOPPELGANGER -> handCards.filter { it.key != joker.key && !it.isJoker }
            JokerType.MIRAGE -> allCards.filter { it.suit in MIRAGE_SUITS && !it.isJoker && it.key !in handKeys }
            JokerType.SHAPESHIFTER -> allCards.filter { it.suit in SHAPESHIFTER_SUITS && !it.isJoker && it.key !in handKeys }
            JokerType.BOOK_OF_CHANGES -> handCards.filter { it.key != joker.key }
            null -> emptyList()
        }
    }
    val locale = currentLocale()
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(joker.displayName(locale), style = MaterialTheme.typography.bodyLarge)
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TargetPicker(
                label = stringResource(R.string.sandbox_joker_target),
                current = assignment?.targetCardKey?.let { key ->
                    allCards.firstOrNull { it.key == key }?.displayName(locale)
                }
                    ?: stringResource(R.string.sandbox_joker_unset),
                options = targets,
                onSelected = { target ->
                    if (target == null) {
                        onChange(null)
                    } else {
                        val newSuit = if (joker.jokerType == JokerType.BOOK_OF_CHANGES) {
                            assignment?.targetSuit ?: BOOK_OF_CHANGES_SUITS.first()
                        } else null
                        onChange(JokerAssignment(joker.key, target.key, newSuit))
                    }
                },
            )
            if (joker.jokerType == JokerType.BOOK_OF_CHANGES && assignment?.targetCardKey != null) {
                SuitPicker(
                    label = stringResource(R.string.sandbox_book_new_suit),
                    current = assignment.targetSuit ?: BOOK_OF_CHANGES_SUITS.first(),
                    onSelected = { suit ->
                        onChange(JokerAssignment(joker.key, assignment.targetCardKey, suit))
                    },
                )
            }
        }
    }
}

@Composable
private fun TargetPicker(
    label: String,
    current: String,
    options: List<CardDefinition>,
    onSelected: (CardDefinition?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val locale = currentLocale()
    AssistChip(
        onClick = { expanded = true },
        label = { Text("$label: $current") },
    )
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.sandbox_joker_unset)) },
            onClick = {
                onSelected(null); expanded = false
            },
        )
        options.forEach { card ->
            DropdownMenuItem(
                text = { Text(card.displayName(locale)) },
                onClick = {
                    onSelected(card); expanded = false
                },
            )
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
    val locale = currentLocale()
    val currentName = candidates.firstOrNull { it.key == currentKey }?.displayName(locale)
        ?: stringResource(R.string.sandbox_joker_unset)
    var expanded by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
                    text = { Text(card.displayName(locale)) },
                    onClick = { onSelected(card.key); expanded = false },
                )
            }
        }
    }
}

@Composable
private fun SuitPicker(
    label: String,
    current: Suit,
    onSelected: (Suit) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    AssistChip(
        onClick = { expanded = true },
        label = { Text("$label: ${current.name}") },
    )
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        BOOK_OF_CHANGES_SUITS.forEach { suit ->
            DropdownMenuItem(
                text = { Text(suit.name) },
                onClick = { onSelected(suit); expanded = false },
            )
        }
    }
}
