package de.morzo.realmscore.ui.sandbox.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.morzo.realmscore.ui.sandbox.CardSlot
import kotlin.math.roundToInt

/** Ideal card width; the actual column count is derived from how many of these fit the screen. */
private val IdealCardWidth = 80.dp
private val CardSpacing = 8.dp

/**
 * Lays the card slots out in multiple rows (Solitaire-style) instead of a single horizontally
 * scrolling row. The column count is (available width / ideal card width) rounded, and the cards in
 * each full row are stretched to fill the width so every card has the same width regardless of the
 * total count (7 hand cards, 10/12 Mittelfeld cards, …). The last, partial row stays left-aligned.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HandSlotsRow(
    slots: List<CardSlot>,
    onSlotTap: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        // How many ideal-width cards fit (fractional → rounded), at least one.
        val columns = (maxWidth / IdealCardWidth).roundToInt().coerceAtLeast(1)
        // Stretch so `columns` cards (plus the gaps between them) exactly fill the width.
        val cardWidth = (maxWidth - CardSpacing * (columns - 1)) / columns

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(CardSpacing),
            verticalArrangement = Arrangement.spacedBy(CardSpacing),
            maxItemsInEachRow = columns,
        ) {
            slots.forEachIndexed { index, slot ->
                CardSlotView(
                    slot = slot,
                    onClick = { onSlotTap(index) },
                    modifier = Modifier.width(cardWidth),
                )
            }
        }
    }
}
