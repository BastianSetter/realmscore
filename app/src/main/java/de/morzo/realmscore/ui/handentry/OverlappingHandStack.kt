package de.morzo.realmscore.ui.handentry

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import de.morzo.realmscore.ui.sandbox.CardSlot
import de.morzo.realmscore.ui.sandbox.components.CardSlotView

/** Fixed card width; height follows the 2:3 card aspect ratio used by [CardSlotView]. */
private val CardWidth = 96.dp
private val CardHeight = CardWidth * 3 / 2

/** At most a third of each card peeks out; the step shrinks further if many cards must fit. */
private val MaxVisibleFraction = 1f / 3f

/**
 * The chosen cards as an overlapping, tappable fan (spec 25.5, KartenPick stage): each card covers
 * roughly the back two-thirds of the previous one — only its leading third peeks out — so the hand
 * stays visible while leaving maximum room for the embedded picker below. The last card is fully
 * shown and drawn on top.
 *
 * Only the *filled* slots are rendered; [onSlotTap] reports the card's original slot index so the
 * caller can open the full-screen picker to correct exactly that slot. Renders nothing when empty.
 */
@Composable
fun OverlappingHandStack(
    slots: List<CardSlot>,
    onSlotTap: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val filled = slots.mapIndexedNotNull { index, slot ->
        (slot as? CardSlot.Filled)?.let { index to it }
    }
    if (filled.isEmpty()) return

    BoxWithConstraints(modifier = modifier.fillMaxWidth().height(CardHeight)) {
        val count = filled.size
        val maxStep = CardWidth * MaxVisibleFraction
        // Shrink the per-card step if the fan at maxStep would overflow the available width.
        val step: Dp = if (count <= 1) {
            0.dp
        } else {
            minOf(maxStep, (maxWidth - CardWidth) / (count - 1)).coerceAtLeast(0.dp)
        }

        filled.forEachIndexed { position, (slotIndex, slot) ->
            CardSlotView(
                slot = slot,
                onClick = { onSlotTap(slotIndex) },
                modifier = Modifier
                    .offset(x = step * position)
                    .width(CardWidth)
                    // Later cards draw on top, so the rightmost card stays fully tappable and the
                    // peeking left third of each earlier card remains the part that receives taps.
                    .zIndex(position.toFloat()),
            )
        }
    }
}
