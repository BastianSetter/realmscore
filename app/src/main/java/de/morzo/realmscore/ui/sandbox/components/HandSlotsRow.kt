package de.morzo.realmscore.ui.sandbox.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.morzo.realmscore.ui.sandbox.CardSlot

@Composable
fun HandSlotsRow(
    slots: List<CardSlot>,
    onSlotTap: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        slots.forEachIndexed { index, slot ->
            CardSlotView(
                slot = slot,
                onClick = { onSlotTap(index) },
            )
        }
    }
}
