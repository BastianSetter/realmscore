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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.morzo.realmscore.R
import de.morzo.realmscore.domain.model.CardDefinition
import de.morzo.realmscore.domain.scoring.ScoringResult
import de.morzo.realmscore.ui.sandbox.components.CardBreakdownList

/** Ring vs. textual-list view of a hand's score breakdown (Phase 18). */
enum class BreakdownMode { RING, LIST }

/**
 * The Ring/Liste toggle, extracted (spec 25.6) so the bottom sheet (round summary) and the inline
 * Sandbox points section can share the exact same control.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BreakdownModeChips(
    mode: BreakdownMode,
    onModeChange: (BreakdownMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            selected = mode == BreakdownMode.RING,
            onClick = { onModeChange(BreakdownMode.RING) },
            label = { Text(stringResource(R.string.breakdown_mode_ring)) },
        )
        FilterChip(
            selected = mode == BreakdownMode.LIST,
            onClick = { onModeChange(BreakdownMode.LIST) },
            label = { Text(stringResource(R.string.breakdown_mode_list)) },
        )
    }
}

/**
 * The breakdown body for the selected [mode] — ring visualization or textual per-card list —
 * shared (spec 25.6) by [HandBreakdownSheet] and the inline Sandbox points section.
 */
@Composable
fun HandBreakdownBody(
    mode: BreakdownMode,
    cards: List<CardDefinition>,
    result: ScoringResult,
    cardLookup: (String) -> CardDefinition?,
    modifier: Modifier = Modifier,
    listMaxHeight: androidx.compose.ui.unit.Dp = 480.dp,
) {
    when (mode) {
        BreakdownMode.RING -> HandRingView(
            handCards = cards,
            scoringResult = result,
            cardLookup = cardLookup,
            modifier = modifier,
        )
        BreakdownMode.LIST -> CardBreakdownList(
            result = result,
            cardLookup = cardLookup,
            modifier = modifier.heightIn(max = listMaxHeight),
        )
    }
}

/**
 * Score breakdown shown as a large bottom sheet (Phase 18). Offers a toggle between the ring
 * visualization (default) and the textual per-card list. Still used by the round summary; the
 * Sandbox now embeds the same content inline (spec 25.6) instead of opening this sheet.
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
    var mode by rememberSaveable { mutableStateOf(BreakdownMode.RING) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp)) {
            Text(
                text = stringResource(R.string.sandbox_breakdown_title, result.totalScore),
                style = MaterialTheme.typography.titleLarge,
            )
            BreakdownModeChips(
                mode = mode,
                onModeChange = { mode = it },
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            )
            HandBreakdownBody(
                mode = mode,
                cards = cards,
                result = result,
                cardLookup = cardLookup,
            )
        }
    }
}
