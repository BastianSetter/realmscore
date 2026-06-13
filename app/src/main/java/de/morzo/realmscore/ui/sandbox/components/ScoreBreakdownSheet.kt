package de.morzo.realmscore.ui.sandbox.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.morzo.realmscore.R
import de.morzo.realmscore.domain.model.CardDefinition
import de.morzo.realmscore.domain.model.displayName
import de.morzo.realmscore.domain.scoring.CardScoreResult
import de.morzo.realmscore.domain.scoring.EffectApplication
import de.morzo.realmscore.domain.scoring.ScoringResult
import de.morzo.realmscore.ui.util.currentLocale

/**
 * List view of the per-card score breakdown. Used inside [HandBreakdownSheet]'s "Liste" tab
 * (Phase 18) and reusable wherever the textual breakdown is needed.
 *
 * [cardLookup] resolves a card key to its definition so names are shown in the active locale
 * (spec 25.7, Ursache B) — the engine only carries language-neutral keys. It must cover the full
 * game card set, not just the hand, because the Necromancer pick and joker substitution targets
 * reference cards outside the seven-card hand.
 */
@Composable
fun CardBreakdownList(
    result: ScoringResult,
    cardLookup: (String) -> CardDefinition?,
    modifier: Modifier = Modifier,
) {
    val locale = currentLocale()
    val sortedCards = remember(result) {
        result.perCard.sortedByDescending { it.contributedScore }
    }
    val nameByKey = remember(result, locale, cardLookup) {
        result.perCard.associate { card ->
            card.cardKey to (cardLookup(card.effectiveCardKey)?.displayName(locale) ?: card.effectiveCardKey)
        }
    }
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(sortedCards, key = { it.cardKey }) { cardResult ->
            val blockedBy = result.blankedBy[cardResult.cardKey]
                .orEmpty()
                .mapNotNull { nameByKey[it] }
            CardBreakdownItem(
                result = cardResult,
                displayName = nameByKey[cardResult.cardKey] ?: cardResult.effectiveCardKey,
                blockedBy = blockedBy,
                cardLookup = cardLookup,
            )
        }
    }
}

@Composable
private fun CardBreakdownItem(
    result: CardScoreResult,
    displayName: String,
    blockedBy: List<String>,
    cardLookup: (String) -> CardDefinition?,
) {
    var expanded by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = { expanded = !expanded },
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (result.isNecromancerPick) {
                        Text(
                            text = stringResource(R.string.necromancer_breakdown_tag),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    if (result.isBlanked) {
                        Text(
                            text = if (blockedBy.isNotEmpty()) {
                                stringResource(R.string.ring_blanked_by, blockedBy.joinToString(", "))
                            } else {
                                stringResource(R.string.sandbox_breakdown_blanked)
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                Text(
                    text = formatDelta(result.contributedScore),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            if (expanded && result.effects.isNotEmpty()) {
                result.effects.forEach { effect ->
                    BreakdownEffectRow(effect, cardLookup)
                }
            }
        }
    }
}

/**
 * Renders one [EffectApplication] line, resolving its `descriptionKey` to a localized string. A
 * non-null [EffectApplication.nameCardKey] is resolved to a localized card name via [cardLookup]
 * and prepended as the leading format argument (`%1$s`), so the engine never has to carry a
 * finished German name (spec 25.7, Ursache B).
 */
@Composable
fun BreakdownEffectRow(
    effect: EffectApplication,
    cardLookup: (String) -> CardDefinition?,
) {
    val context = LocalContext.current
    val locale = currentLocale()
    val resId = remember(effect.descriptionKey) {
        context.resources.getIdentifier(effect.descriptionKey, "string", context.packageName)
    }
    val args = remember(effect, locale, cardLookup) {
        val nameArg = effect.nameCardKey?.let { cardLookup(it)?.displayName(locale) ?: it }
        (listOfNotNull(nameArg) + effect.descriptionArgs).toTypedArray()
    }
    val text = remember(effect, resId, args) {
        if (resId == 0) {
            "${effect.descriptionKey} ${args.joinToString(" ")}"
        } else {
            try {
                context.getString(resId, *args)
            } catch (_: Exception) {
                context.getString(resId)
            }
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text, style = MaterialTheme.typography.bodyMedium)
        Text(formatDelta(effect.pointsDelta), style = MaterialTheme.typography.bodyMedium)
    }
}

fun formatDelta(value: Int): String = if (value > 0) "+$value" else value.toString()
