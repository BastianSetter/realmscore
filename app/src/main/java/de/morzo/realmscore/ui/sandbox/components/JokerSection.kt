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
import androidx.compose.material3.HorizontalDivider
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
import java.util.Locale

private val BOOK_OF_CHANGES_SUITS = Suit.entries.filter { it != Suit.WILD }

private val ISLAND_SUITS = setOf(Suit.FLOOD, Suit.FLAME)
private val FOUNTAIN_SUITS = setOf(Suit.WEAPON, Suit.FLOOD, Suit.FLAME, Suit.LAND, Suit.WEATHER)

/** A pickable target: [key] is the physical hand card chosen, [label] the text shown to the user. */
private data class TargetOption(val key: String, val label: String)

/** The action verb shown in front of each joker's target dropdown (e.g. "Spiegelt", "Holt"). */
@Composable
private fun jokerActionLabel(jokerType: JokerType?): String = stringResource(
    when (jokerType) {
        JokerType.DOPPELGANGER -> R.string.joker_action_doppelganger
        JokerType.MIRAGE -> R.string.joker_action_mirage
        JokerType.SHAPESHIFTER -> R.string.joker_action_shapeshifter
        JokerType.BOOK_OF_CHANGES -> R.string.joker_action_book
        JokerType.ISLAND -> R.string.joker_action_island
        JokerType.FOUNTAIN_OF_LIFE -> R.string.joker_action_fountain
        JokerType.NECROMANCER -> R.string.joker_action_necromancer
        null -> R.string.joker_action_mirage
    }
)

/**
 * Drives the Necromancer row at the top of the joker section. The pull comes from the discard pile,
 * so it uses a full card picker ([onPick]) rather than the inline dropdown the other jokers use.
 */
data class NecromancerRowData(
    val card: CardDefinition,
    val pickedCard: CardDefinition?,
    val onPick: () -> Unit,
    val onClear: () -> Unit,
)

/** Localized name of a resolved card's effective identity, falling back to the raw key. */
private fun ResolvedCard.effectiveLabel(allCards: List<CardDefinition>, locale: Locale): String =
    allCards.firstOrNull { it.key == effectiveCardKey }?.displayName(locale) ?: effectiveCardKey

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
    necromancer: NecromancerRowData? = null,
    // In narrow columns (multi-hand compare) each joker row stacks its pieces vertically instead of
    // on one line, which would otherwise wrap into an unreadable mess (spec 25.6).
    compact: Boolean = false,
) {
    if (jokers.isEmpty() && necromancer == null) return

    // Pure-domain resolver over the full game card list; cheap to build and used only to derive
    // effective suits/strengths for the Island/Fountain/Book candidate lists (which now include the
    // Necromancer's pulled 8th card).
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
            // Build the rows actually present in the hand (Necromancer leads, as it resolves first),
            // then draw a thin divider above every row but the first. Because only present jokers
            // produce a row, absent ones never leave a doubled or dangling divider (spec follow-up).
            val rows = buildList<@Composable () -> Unit> {
                if (necromancer != null) add { NecromancerRow(necromancer, compact = compact) }
                jokers.forEach { joker ->
                    add {
                        JokerRow(
                            joker = joker,
                            assignment = assignments[joker.key],
                            allCards = allCards,
                            handCards = handCards,
                            resolved = resolved,
                            onChange = { onAssignmentChange(joker.key, it) },
                            compact = compact,
                        )
                    }
                }
            }
            rows.forEachIndexed { index, row ->
                if (index > 0) HorizontalDivider()
                row()
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
    compact: Boolean = false,
) {
    val locale = currentLocale()
    val options: List<TargetOption> = remember(joker.jokerType, allCards, handCards, resolved, locale) {
        val handKeys = handCards.map { it.key }.toSet()
        when (joker.jokerType) {
            JokerType.DOPPELGANGER ->
                handCards.filter { it.key != joker.key && !it.isJoker }
                    .map { TargetOption(it.key, it.displayName(locale)) }
            JokerType.MIRAGE ->
                allCards.filter { it.suit in JokerType.MIRAGE_SUITS && !it.isJoker && it.key !in handKeys }
                    .map { TargetOption(it.key, it.displayName(locale)) }
            JokerType.SHAPESHIFTER ->
                allCards.filter { it.suit in JokerType.SHAPESHIFTER_SUITS && !it.isJoker && it.key !in handKeys }
                    .map { TargetOption(it.key, it.displayName(locale)) }
            // Book of Changes targets the RESOLVED hand so it can also re-suit the Necromancer's
            // pulled 8th card (and is labelled by the effective identity).
            JokerType.BOOK_OF_CHANGES ->
                resolved.filter { it.originalKey != joker.key }
                    .map { TargetOption(it.originalKey, it.effectiveLabel(allCards, locale)) }
            // Island/Fountain: candidates come from the RESOLVED hand, labelled by the effective
            // card's localized name (a Doppelganger that became an eligible card is offered, and
            // shown, correctly). The label resolves the effectiveCardKey against the full card set
            // so it follows the active language (spec 25.7, Ursache B).
            JokerType.ISLAND ->
                resolved.filter { it.originalKey != joker.key && it.effectiveSuit in ISLAND_SUITS }
                    .map { TargetOption(it.originalKey, it.effectiveLabel(allCards, locale)) }
            JokerType.FOUNTAIN_OF_LIFE ->
                resolved.filter {
                    it.originalKey != joker.key &&
                        it.effectiveSuit in FOUNTAIN_SUITS &&
                        it.effectiveStrength > 0
                }.map { TargetOption(it.originalKey, it.effectiveLabel(allCards, locale)) }
            // Necromancer keeps its own dedicated section and is filtered out before reaching here.
            JokerType.NECROMANCER -> emptyList()
            null -> emptyList()
        }
    }

    val currentLabel = assignment?.targetCardKey
        ?.let { key -> options.firstOrNull { it.key == key }?.label }
        ?: stringResource(R.string.sandbox_joker_unset)

    val isBook = joker.jokerType == JokerType.BOOK_OF_CHANGES
    val onTargetSelected: (TargetOption?) -> Unit = { option ->
        if (option == null) {
            onChange(null)
        } else {
            val newSuit = if (isBook) assignment?.targetSuit ?: BOOK_OF_CHANGES_SUITS.first() else null
            onChange(JokerAssignment(joker.key, option.key, newSuit))
        }
    }

    if (compact) {
        // Narrow column (multi-hand): every piece on its own line, so nothing wraps mid-word.
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(joker.displayName(locale), style = MaterialTheme.typography.bodyLarge)
            Text(jokerActionLabel(joker.jokerType), style = MaterialTheme.typography.bodyMedium)
            TargetPicker(current = currentLabel, options = options, onSelected = onTargetSelected)
            if (isBook && assignment?.targetCardKey != null) {
                Text(
                    text = stringResource(R.string.joker_book_to),
                    style = MaterialTheme.typography.bodyMedium,
                )
                SuitPicker(
                    current = assignment.targetSuit ?: BOOK_OF_CHANGES_SUITS.first(),
                    onSelected = { suit ->
                        onChange(JokerAssignment(joker.key, assignment.targetCardKey, suit))
                    },
                )
            }
        }
    } else if (isBook) {
        // Two fields ("ändert <Karte> zu <Farbe>") need more room, so keep the name on its own line.
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(joker.displayName(locale), style = MaterialTheme.typography.bodyLarge)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(jokerActionLabel(joker.jokerType), style = MaterialTheme.typography.bodyMedium)
                TargetPicker(current = currentLabel, options = options, onSelected = onTargetSelected)
                if (assignment?.targetCardKey != null) {
                    Text(
                        text = stringResource(R.string.joker_book_to),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    SuitPicker(
                        current = assignment.targetSuit ?: BOOK_OF_CHANGES_SUITS.first(),
                        onSelected = { suit ->
                            onChange(JokerAssignment(joker.key, assignment.targetCardKey, suit))
                        },
                    )
                }
            }
        }
    } else {
        // One-liner: "<Joker> <verb> <Karte>".
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(joker.displayName(locale), style = MaterialTheme.typography.bodyLarge)
            Text(jokerActionLabel(joker.jokerType), style = MaterialTheme.typography.bodyMedium)
            TargetPicker(current = currentLabel, options = options, onSelected = onTargetSelected)
        }
    }
}

@Composable
private fun NecromancerRow(data: NecromancerRowData, compact: Boolean = false) {
    val locale = currentLocale()
    // The pick chip itself is the way to change the pull (tap → card picker); there is no separate
    // clear button (spec follow-up): re-tapping the chip lets the user choose a different card.
    val picked = data.pickedCard?.displayName(locale)
        ?: stringResource(R.string.sandbox_joker_unset)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (compact) {
            // Narrow column (multi-hand): stack name, verb, and the pick chip.
            Text(data.card.displayName(locale), style = MaterialTheme.typography.bodyLarge)
            Text(
                text = jokerActionLabel(data.card.jokerType),
                style = MaterialTheme.typography.bodyMedium,
            )
            AssistChip(onClick = data.onPick, label = { Text(picked) })
        } else {
            // One-liner: "<Totenbeschwörer> <verb> <Karte>".
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(data.card.displayName(locale), style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = jokerActionLabel(data.card.jokerType),
                    style = MaterialTheme.typography.bodyMedium,
                )
                AssistChip(onClick = data.onPick, label = { Text(picked) })
            }
        }
        Text(
            text = stringResource(R.string.necromancer_suit_hint),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TargetPicker(
    current: String,
    options: List<TargetOption>,
    onSelected: (TargetOption?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    AssistChip(
        onClick = { expanded = true },
        label = { Text(current) },
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
    current: Suit,
    onSelected: (Suit) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    AssistChip(
        onClick = { expanded = true },
        label = { Text(current.name) },
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
