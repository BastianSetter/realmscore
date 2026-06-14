package de.morzo.realmscore.ui.handentry

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import de.morzo.realmscore.ui.sandbox.CardSlot
import de.morzo.realmscore.ui.sandbox.components.CardSlotView
import kotlin.math.roundToInt

/** Ideal card width; matches the flat player-stage [HandSlotsRow] so both stages size cards alike. */
private val IdealCardWidth = 80.dp
private val CardSpacing = 8.dp

/**
 * The hand as an overlapping, tappable fan (spec 25.5, KartenPick stage): the filled slots plus the
 * single slot currently being filled (the first empty one, highlighted with a halo and drawn on
 * top) are rendered — the remaining empty slots are left out, but every card keeps the position it
 * would have if all slots were drawn, so the fan doesn't reflow as cards come in. They are spread
 * evenly across the full width from left to right so adjacent cards overlap. Cards use the same
 * width as the flat player-stage [HandSlotsRow], adjusted to the screen, so the two stages look
 * consistent.
 *
 * [onSlotTap] reports the card's slot index so the caller can open the full-screen picker to correct
 * exactly that slot. Renders nothing when there are no slots.
 */
@Composable
fun OverlappingHandStack(
    slots: List<CardSlot>,
    onSlotTap: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (slots.isEmpty()) return
    val currentSlot = slots.indexOfFirst { it is CardSlot.Empty }

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        // Same card sizing as the player stage: derive the width from how many ideal-width cards fit.
        val columns = (maxWidth / IdealCardWidth).roundToInt().coerceAtLeast(1)
        val cardWidth = (maxWidth - CardSpacing * (columns - 1)) / columns

        val count = slots.size
        // Spread all cards across the full width; with more cards than fit, they overlap.
        val step: Dp = if (count <= 1) 0.dp else (maxWidth - cardWidth) / (count - 1)

        slots.forEachIndexed { index, slot ->
            // Skip the still-empty slots except the one currently being filled; their positions are
            // still reserved (step * index) so the rendered cards don't shift as the hand fills up.
            if (slot is CardSlot.Empty && index != currentSlot) return@forEachIndexed
            CardSlotView(
                slot = slot,
                onClick = { onSlotTap(index) },
                highlighted = index == currentSlot,
                modifier = Modifier
                    .offset(x = step * index)
                    .width(cardWidth)
                    // Later cards draw on top; the highlighted current slot floats above all so its
                    // halo is never covered by the cards that follow it.
                    .zIndex(if (index == currentSlot) count.toFloat() else index.toFloat()),
            )
        }
    }
}
