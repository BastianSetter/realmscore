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

/** The picked slot lifts this fraction of its own height so it stands out from its neighbours. */
private const val CurrentSlotRaiseFraction = 0.15f

/** Card height : width ratio (120 : 80); mirrors the 80:120 aspect ratio used by [CardSlotView]. */
private const val CardHeightRatio = 120f / 80f

/**
 * The hand as an overlapping, tappable fan (spec 25.5, KartenPick stage): the filled slots plus the
 * slot currently being filled ([currentSlot]) are rendered — the remaining empty slots are left
 * out, but every card keeps the position it would have if all slots were drawn, so the fan doesn't
 * reflow as cards come in. They are spread evenly across the full width from left to right so
 * adjacent cards overlap. Cards use the same width as the flat player-stage [HandSlotsRow], adjusted
 * to the screen, so the two stages look consistent.
 *
 * The [currentSlot] (the slot the embedded picker fills next) is lifted up by ~15% of its height and
 * drawn on top so it reads as the active card. [onSlotTap] reports the tapped card's slot index so
 * the caller can re-target the picker at exactly that slot.
 */
@Composable
fun OverlappingHandStack(
    slots: List<CardSlot>,
    currentSlot: Int,
    onSlotTap: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (slots.isEmpty()) return

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        // Same card sizing as the player stage: derive the width from how many ideal-width cards fit.
        val columns = (maxWidth / IdealCardWidth).roundToInt().coerceAtLeast(1)
        val cardWidth = (maxWidth - CardSpacing * (columns - 1)) / columns
        val raise = cardWidth * CardHeightRatio * CurrentSlotRaiseFraction

        val count = slots.size
        // Spread all cards across the full width; with more cards than fit, they overlap.
        val step: Dp = if (count <= 1) 0.dp else (maxWidth - cardWidth) / (count - 1)

        // The next slot that will be filled. It stays rendered (and tappable) even while a
        // different, already-filled slot is being corrected — otherwise re-targeting it would be
        // impossible because it had vanished.
        val nextEmptyIndex = slots.indexOfFirst { it is CardSlot.Empty }

        slots.forEachIndexed { index, slot ->
            // Skip the still-empty slots except the one currently being filled and the next empty
            // slot to fill; their positions are still reserved (step * index) so the rendered cards
            // don't shift as the hand fills up. Only the current slot is raised.
            if (slot is CardSlot.Empty && index != currentSlot && index != nextEmptyIndex) {
                return@forEachIndexed
            }
            CardSlotView(
                slot = slot,
                onClick = { onSlotTap(index) },
                bordered = slot is CardSlot.Filled,
                modifier = Modifier
                    .offset(
                        x = step * index,
                        y = if (index == currentSlot) -raise else 0.dp,
                    )
                    .width(cardWidth)
                    // Later cards draw on top; the raised current slot floats above all so it is
                    // never covered by the cards that follow it.
                    .zIndex(if (index == currentSlot) count.toFloat() else index.toFloat()),
            )
        }
    }
}
