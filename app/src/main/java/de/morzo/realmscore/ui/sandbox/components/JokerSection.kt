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
import de.morzo.realmscore.domain.scoring.ResolvedCard
import de.morzo.realmscore.domain.scoring.joker.JokerResolver
import de.morzo.realmscore.ui.util.currentLocale

private val MIRAGE_SUITS = setOf(Suit.ARMY, Suit.LAND, Suit.WEATHER, Suit.FLOOD, Suit.FLAME)
private val SHAPESHIFTER_SUITS = setOf(Suit.ARTIFACT, Suit.LEADER, Suit.WIZARD, Suit.WEAPON, Suit.BEAST)
private val BOOK_OF_CHANGES_SUITS = Suit.entries.filter { it != Suit.WILD }

private val ISLAND_SUITS = setOf(Suit.FLOOD, Suit.FLAME)
private val FOUNTAIN_SUITS = setOf(Suit.WEAPON, Suit.FLOOD, Suit.FLAME, Suit.LAND, Suit.WEATHER)

/** A pickable target: [key] is the physical hand card chosen, [label] the text shown to the user. */
private data class TargetOption(val key: String, val label: String)

/**
 * One titled row per card that needs a player choice (Phase 23): the four substitution jokers plus
 * Island and Fountain of Life, which are now ordinary [JokerType]s rather than ad-hoc PlayerChoices.
 *
 * Island/Fountain candidate targets are taken from the *resolved* hand (effective suit/strength),
 * so a Doppelganger that has become a Flood/Flame/eligible card shows up as a valid pick — fixing
 * the case where the optimiser chose such a target but the row still read "nicht gesetzt".
 */
@Composable
fun JokerSection(
    jokers: List<CardDefinition>,
    assignments: Map<String, JokerAssignment>,
    allCards: List<CardDefinition>,
    handCards: List<CardDefinition>,
    onAssignmentChange: (String, JokerAssignment?) -> Unit,
    modifier: Modifier = Modifier,
    onOptimal: (() -> Unit)? = null,
    optimalRunning: Boolean = false,
) {
    if (jokers.isEmpty()) return

    // Pure-domain resolver over the full game card list; cheap to build and used only to derive
    // effective suits/strengths for the Island/Fountain candidate lists.
    val resolver = remember(allCards) {
        JokerResolver { key -> allCards.firstOrNull { it.key == key } }
    }
    val resolved = remember(handCards, assignments) { resolver.resolve(handCards, assignments) }

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
                    resolved = resolved,
                    onChange = { onAssignmentChange(joker.key, it) },
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
    resolved: List<ResolvedCard>,
    onChange: (JokerAssignment?) -> Unit,
) {
    val locale = currentLocale()
    val options: List<TargetOption> = remember(joker.jokerType, allCards, handCards, resolved, locale) {
        val handKeys = handCards.map { it.key }.toSet()
        when (joker.jokerType) {
            JokerType.DOPPELGANGER ->
                handCards.filter { it.key != joker.key && !it.isJoker }
                    .map { TargetOption(it.key, it.displayName(locale)) }
            JokerType.MIRAGE ->
                allCards.filter { it.suit in MIRAGE_SUITS && !it.isJoker && it.key !in handKeys }
                    .map { TargetOption(it.key, it.displayName(locale)) }
            JokerType.SHAPESHIFTER ->
                allCards.filter { it.suit in SHAPESHIFTER_SUITS && !it.isJoker && it.key !in handKeys }
                    .map { TargetOption(it.key, it.displayName(locale)) }
            JokerType.BOOK_OF_CHANGES ->
                handCards.filter { it.key != joker.key }
                    .map { TargetOption(it.key, it.displayName(locale)) }
            // Island/Fountain: candidates come from the RESOLVED hand, labelled by effective name,
            // so a Doppelganger that became an eligible card is offered (and shown) correctly.
            JokerType.ISLAND ->
                resolved.filter { it.originalKey != joker.key && it.effectiveSuit in ISLAND_SUITS }
                    .map { TargetOption(it.originalKey, it.effectiveName) }
            JokerType.FOUNTAIN_OF_LIFE ->
                resolved.filter {
                    it.originalKey != joker.key &&
                        it.effectiveSuit in FOUNTAIN_SUITS &&
                        it.effectiveStrength > 0
                }.map { TargetOption(it.originalKey, it.effectiveName) }
            null -> emptyList()
        }
    }

    val currentLabel = assignment?.targetCardKey
        ?.let { key -> options.firstOrNull { it.key == key }?.label }
        ?: stringResource(R.string.sandbox_joker_unset)

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(joker.displayName(locale), style = MaterialTheme.typography.bodyLarge)
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TargetPicker(
                label = stringResource(R.string.sandbox_joker_target),
                current = currentLabel,
                options = options,
                onSelected = { option ->
                    if (option == null) {
                        onChange(null)
                    } else {
                        val newSuit = if (joker.jokerType == JokerType.BOOK_OF_CHANGES) {
                            assignment?.targetSuit ?: BOOK_OF_CHANGES_SUITS.first()
                        } else null
                        onChange(JokerAssignment(joker.key, option.key, newSuit))
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
    options: List<TargetOption>,
    onSelected: (TargetOption?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
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
        options.forEach { option ->
            DropdownMenuItem(
                text = { Text(option.label) },
                onClick = {
                    onSelected(option); expanded = false
                },
            )
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
