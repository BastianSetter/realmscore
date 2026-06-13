package de.morzo.realmscore.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import de.morzo.realmscore.domain.scoring.ScoringResult
import de.morzo.realmscore.ui.sandbox.components.CardBreakdownList

private enum class BreakdownMode { RING, LIST }

/**
 * Score breakdown shown as a large bottom sheet (Phase 18). Offers a toggle between the ring
 * visualization (default) and the textual per-card list. Replaces the old list-only
 * `ScoreBreakdownSheet` for both the Sandbox and the round summary.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HandBreakdownSheet(
    cards: List<CardDefinition>,
    result: ScoringResult,
    cardLookup: (String) -> CardDefinition?,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var mode by remember { mutableStateOf(BreakdownMode.RING) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp)) {
            Text(
                text = stringResource(R.string.sandbox_breakdown_title, result.totalScore),
                style = MaterialTheme.typography.titleLarge,
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = mode == BreakdownMode.RING,
                    onClick = { mode = BreakdownMode.RING },
                    label = { Text(stringResource(R.string.breakdown_mode_ring)) },
                )
                FilterChip(
                    selected = mode == BreakdownMode.LIST,
                    onClick = { mode = BreakdownMode.LIST },
                    label = { Text(stringResource(R.string.breakdown_mode_list)) },
                )
            }
            when (mode) {
                BreakdownMode.RING -> HandRingView(
                    handCards = cards,
                    scoringResult = result,
                    cardLookup = cardLookup,
                )
                BreakdownMode.LIST -> CardBreakdownList(
                    result = result,
                    cardLookup = cardLookup,
                    modifier = Modifier.heightIn(max = 480.dp),
                )
            }
        }
    }
}
