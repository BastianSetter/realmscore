package de.morzo.realmscore.ui.sandbox.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.morzo.realmscore.R
import de.morzo.realmscore.domain.model.CardDefinition
import de.morzo.realmscore.ui.components.suitColor
import de.morzo.realmscore.ui.components.suitOnColor
import de.morzo.realmscore.ui.sandbox.CardSlot

private val SlotShape = RoundedCornerShape(12.dp)

@Composable
fun CardSlotView(
    slot: CardSlot,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(width = 80.dp, height = 120.dp)
            .clip(SlotShape)
            .clickable(onClick = onClick),
    ) {
        when (slot) {
            CardSlot.Empty -> EmptySlotContent()
            is CardSlot.Filled -> FilledSlotContent(slot.card)
        }
    }
}

@Composable
private fun EmptySlotContent() {
    val outline = MaterialTheme.colorScheme.outline
    Box(
        modifier = Modifier
            .fillMaxSize()
            .drawBehind {
                drawRoundRect(
                    color = outline,
                    size = Size(size.width, size.height),
                    cornerRadius = CornerRadius(12.dp.toPx(), 12.dp.toPx()),
                    style = Stroke(
                        width = 2.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f),
                    ),
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Default.Add,
            contentDescription = stringResource(R.string.sandbox_empty_slot),
            tint = MaterialTheme.colorScheme.outline,
        )
    }
}

@Composable
private fun FilledSlotContent(card: CardDefinition) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(suitColor(card.suit))
            .padding(PaddingValues(8.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = card.nameDe,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = suitOnColor(card.suit),
            textAlign = TextAlign.Center,
        )
    }
}
