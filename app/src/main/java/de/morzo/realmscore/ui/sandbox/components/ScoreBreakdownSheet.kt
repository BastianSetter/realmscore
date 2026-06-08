package de.morzo.realmscore.ui.sandbox.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import de.morzo.realmscore.domain.scoring.CardScoreResult
import de.morzo.realmscore.domain.scoring.EffectApplication
import de.morzo.realmscore.domain.scoring.ScoringResult

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScoreBreakdownSheet(
    result: ScoringResult,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.sandbox_breakdown_title, result.totalScore),
                style = MaterialTheme.typography.titleLarge,
            )
            val sortedCards = remember(result) {
                result.perCard.sortedByDescending { it.contributedScore }
            }
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(sortedCards, key = { it.cardKey }) { cardResult ->
                    CardBreakdownItem(cardResult)
                }
            }
        }
    }
}

@Composable
private fun CardBreakdownItem(result: CardScoreResult) {
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
                        text = result.effectiveName,
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
                            text = stringResource(R.string.sandbox_breakdown_blanked),
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
                    EffectRow(effect)
                }
            }
        }
    }
}

@Composable
private fun EffectRow(effect: EffectApplication) {
    val context = LocalContext.current
    val resId = remember(effect.descriptionKey) {
        context.resources.getIdentifier(effect.descriptionKey, "string", context.packageName)
    }
    val text = remember(effect, resId) {
        if (resId == 0) {
            "${effect.descriptionKey} ${effect.descriptionArgs.joinToString(" ")}"
        } else {
            try {
                context.getString(resId, *effect.descriptionArgs.toTypedArray())
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

private fun formatDelta(value: Int): String = if (value > 0) "+$value" else value.toString()
