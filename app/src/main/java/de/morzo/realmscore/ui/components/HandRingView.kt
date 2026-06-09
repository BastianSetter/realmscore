package de.morzo.realmscore.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.morzo.realmscore.domain.model.CardDefinition
import de.morzo.realmscore.domain.scoring.RingConnection
import de.morzo.realmscore.domain.scoring.RingLayoutOptimizer
import de.morzo.realmscore.domain.scoring.ScoringResult
import de.morzo.realmscore.domain.scoring.buildRingConnections
import de.morzo.realmscore.R
import de.morzo.realmscore.ui.theme.SuitColors
import de.morzo.realmscore.ui.sandbox.components.BreakdownEffectRow
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

private val BonusColor = Color(0xFF2E7D32)
private val PenaltyColor = Color(0xFFC62828)

/**
 * Ring visualization of a 7-card hand (Phase 18). Cards are placed around a circle with the
 * highest-scoring card at the top; directed lines show how cards influence each other (green =
 * bonus, red = penalty, thickness ∝ |effect|). Tapping a card reveals its detailed breakdown below
 * the ring.
 */
@Composable
fun HandRingView(
    cards: List<CardDefinition>,
    scoringResult: ScoringResult,
    modifier: Modifier = Modifier,
) {
    if (cards.isEmpty()) return

    val connections = remember(scoringResult, cards) { buildRingConnections(cards, scoringResult) }
    val scores = remember(scoringResult, cards) {
        val byKey = scoringResult.perCard.associateBy { it.cardKey }
        cards.map { byKey[it.key]?.contributedScore ?: 0 }
    }
    val layout = remember(cards, connections, scores) {
        RingLayoutOptimizer.optimize(cards, scores, connections)
    }
    val maxWeight = remember(connections) {
        connections.maxOfOrNull { abs(it.weight) }?.coerceAtLeast(1) ?: 1
    }
    val resultByKey = remember(scoringResult) { scoringResult.perCard.associateBy { it.cardKey } }

    var tappedCardIdx by remember(cards) { mutableStateOf<Int?>(null) }

    // Line-reveal animation on first render.
    var animateIn by remember(cards) { mutableStateOf(false) }
    LaunchedEffect(cards) { animateIn = true }
    val progress by animateFloatAsState(
        targetValue = if (animateIn) 1f else 0f,
        animationSpec = tween(durationMillis = 300),
        label = "ringLineProgress",
    )

    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val onSurface = MaterialTheme.colorScheme.onSurface
    val highlight = MaterialTheme.colorScheme.primary
    val darkTheme = androidx.compose.foundation.isSystemInDarkTheme()

    Column(modifier = modifier.fillMaxWidth()) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(320.dp),
        ) {
            val widthPx = with(density) { maxWidth.toPx() }
            val heightPx = with(density) { maxHeight.toPx() }
            val center = Offset(widthPx / 2f, heightPx / 2f)
            val ringRadius = minOf(widthPx, heightPx) * 0.35f
            val cardRadius = minOf(widthPx, heightPx) * 0.13f

            // Pixel center of each card index for both drawing and tap hit-testing.
            val nodeCenters = remember(layout, widthPx, heightPx) {
                val arr = arrayOfNulls<Offset>(cards.size)
                layout.forEachIndexed { pos, cardIdx ->
                    arr[cardIdx] = positionCenter(pos, layout.size, center, ringRadius)
                }
                arr.requireNoNulls()
            }

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(320.dp)
                    .pointerInput(nodeCenters) {
                        detectTapGestures { tap ->
                            val hit = nodeCenters.indexOfFirst { c ->
                                hypot((tap.x - c.x).toDouble(), (tap.y - c.y).toDouble()) <=
                                    cardRadius * 1.5f
                            }
                            tappedCardIdx = when {
                                hit < 0 -> null
                                hit == tappedCardIdx -> null
                                else -> hit
                            }
                        }
                    },
            ) {
                // 1) Connection lines (drawn beneath the nodes). When a card is selected, only its
                // incident lines stay solid + labelled; the rest fade into the background.
                val selectedIdx = tappedCardIdx
                connections.forEach { conn ->
                    val incident = selectedIdx == null ||
                        conn.fromCardIdx == selectedIdx || conn.toCardIdx == selectedIdx
                    drawConnection(
                        conn = conn,
                        from = nodeCenters[conn.fromCardIdx],
                        to = nodeCenters[conn.toCardIdx],
                        maxWeight = maxWeight,
                        cardRadius = cardRadius,
                        progress = progress,
                        dimmed = !incident,
                        label = if (selectedIdx != null && incident) {
                            if (conn.weight > 0) "+${conn.weight}" else conn.weight.toString()
                        } else null,
                        textMeasurer = textMeasurer,
                        labelColor = onSurface,
                    )
                }

                // 2) Card nodes on top.
                cards.forEachIndexed { idx, card ->
                    val res = resultByKey[card.key]
                    drawCardNode(
                        textMeasurer = textMeasurer,
                        card = card,
                        contributedScore = res?.contributedScore ?: 0,
                        baseStrength = card.baseStrength,
                        isBlanked = res?.isBlanked == true,
                        isSelected = tappedCardIdx == idx,
                        center = nodeCenters[idx],
                        radius = cardRadius,
                        textColor = onSurface,
                        highlightColor = highlight,
                        darkTheme = darkTheme,
                    )
                }
            }
        }

        val selected = tappedCardIdx
        if (selected != null) {
            val card = cards[selected]
            val res = resultByKey[card.key]
            CardDetailCard(
                card = card,
                contributedScore = res?.contributedScore ?: 0,
                isBlanked = res?.isBlanked == true,
                effects = res?.effects.orEmpty(),
            )
        } else {
            Text(
                text = stringResource(R.string.ring_tap_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun CardDetailCard(
    card: CardDefinition,
    contributedScore: Int,
    isBlanked: Boolean,
    effects: List<de.morzo.realmscore.domain.scoring.EffectApplication>,
) {
    androidx.compose.material3.Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(
                text = card.nameDe,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = androidx.compose.ui.res.stringResource(
                    de.morzo.realmscore.R.string.ring_base_strength,
                    card.baseStrength,
                ),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (isBlanked) {
                Text(
                    text = androidx.compose.ui.res.stringResource(
                        de.morzo.realmscore.R.string.sandbox_breakdown_blanked,
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            effects.forEach { effect -> BreakdownEffectRow(effect) }
        }
    }
}

/** Computes the on-screen center of a ring position. */
private fun positionCenter(pos: Int, count: Int, center: Offset, ringRadius: Float): Offset {
    val angle = (pos.toFloat() / count.toFloat()) * 2f * PI.toFloat() - PI.toFloat() / 2f
    return Offset(center.x + ringRadius * cos(angle), center.y + ringRadius * sin(angle))
}

private fun DrawScope.drawConnection(
    conn: RingConnection,
    from: Offset,
    to: Offset,
    maxWeight: Int,
    cardRadius: Float,
    progress: Float,
    dimmed: Boolean,
    label: String?,
    textMeasurer: TextMeasurer,
    labelColor: Color,
) {
    if (conn.weight == 0) return
    val dir = to - from
    val len = hypot(dir.x.toDouble(), dir.y.toDouble()).toFloat()
    if (len < 0.001f) return
    val unit = Offset(dir.x / len, dir.y / len)

    val baseAlpha = if (dimmed) 0.12f else 1f
    val strokeWidth = (abs(conn.weight).toFloat() / maxWeight * 6f + 2f).dp.toPx()
    val color = when {
        conn.isBlanking -> Color.Gray
        conn.weight > 0 -> BonusColor
        else -> PenaltyColor
    }.copy(alpha = baseAlpha)

    val headGap = cardRadius + 8.dp.toPx()
    val start = from + unit * cardRadius
    val end = to - unit * headGap
    // Animate the line growing from its origin.
    val animatedEnd = start + (end - start) * progress

    drawLine(
        color = color,
        start = start,
        end = animatedEnd,
        strokeWidth = strokeWidth,
        cap = androidx.compose.ui.graphics.StrokeCap.Round,
    )
    if (progress > 0.99f) {
        drawArrowHead(color, tip = end, dir = unit, strokeWidth = strokeWidth)
    }

    // Exact per-line contribution near the line origin (spec: "Beitrag am Linienansatz").
    if (label != null && progress > 0.99f) {
        val labelPos = start + unit * (cardRadius * 0.6f + 6.dp.toPx())
        val labelStyle = TextStyle(
            color = labelColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
        )
        val layout = textMeasurer.measure(text = label, style = labelStyle)
        val topLeft = Offset(
            labelPos.x - layout.size.width / 2f,
            labelPos.y - layout.size.height / 2f,
        )
        // Readable chip behind the number.
        val pad = 3.dp.toPx()
        drawRoundRect(
            color = Color.White.copy(alpha = 0.85f),
            topLeft = Offset(topLeft.x - pad, topLeft.y - pad),
            size = androidx.compose.ui.geometry.Size(
                layout.size.width + pad * 2,
                layout.size.height + pad * 2,
            ),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx()),
        )
        drawText(textLayoutResult = layout, topLeft = topLeft)
    }
}

private fun DrawScope.drawArrowHead(color: Color, tip: Offset, dir: Offset, strokeWidth: Float) {
    val size = strokeWidth * 2.2f + 6.dp.toPx()
    // Perpendicular vector.
    val perp = Offset(-dir.y, dir.x)
    val base = tip - dir * size
    val left = base + perp * (size * 0.5f)
    val right = base - perp * (size * 0.5f)
    val path = Path().apply {
        moveTo(tip.x, tip.y)
        lineTo(left.x, left.y)
        lineTo(right.x, right.y)
        close()
    }
    drawPath(path, color = color)
}

private fun DrawScope.drawCardNode(
    textMeasurer: TextMeasurer,
    card: CardDefinition,
    contributedScore: Int,
    baseStrength: Int,
    isBlanked: Boolean,
    isSelected: Boolean,
    center: Offset,
    radius: Float,
    textColor: Color,
    highlightColor: Color,
    darkTheme: Boolean,
) {
    val alpha = if (isBlanked) 0.35f else 1f
    val bgColor = if (isBlanked) Color.Gray else SuitColors.forSuit(card.suit, darkTheme)

    drawCircle(color = bgColor.copy(alpha = alpha), radius = radius, center = center)
    if (isSelected) {
        drawCircle(
            color = highlightColor,
            radius = radius,
            center = center,
            style = Stroke(width = 3.dp.toPx()),
        )
    } else {
        drawCircle(
            color = Color.White.copy(alpha = alpha * 0.4f),
            radius = radius,
            center = center,
            style = Stroke(width = 2f),
        )
    }

    val shortName = card.nameDe.let { if (it.length > 10) it.take(9) + "…" else it }
    val nameStyle = TextStyle(
        color = textColor.copy(alpha = alpha),
        fontSize = 10.sp,
        fontWeight = FontWeight.SemiBold,
        textAlign = TextAlign.Center,
    )
    val scoreStyle = TextStyle(
        color = textColor.copy(alpha = alpha),
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
    )
    val maxTextWidth = (radius * 1.8f).toInt().coerceAtLeast(1)
    val nameLayout = textMeasurer.measure(
        text = shortName,
        style = nameStyle,
        maxLines = 2,
        constraints = androidx.compose.ui.unit.Constraints(maxWidth = maxTextWidth),
    )
    val scoreText = if (contributedScore > 0) "+$contributedScore" else contributedScore.toString()
    val scoreLayout = textMeasurer.measure(text = scoreText, style = scoreStyle)

    val totalHeight = nameLayout.size.height + scoreLayout.size.height + 2f
    val nameTop = center.y - totalHeight / 2f
    drawText(
        textLayoutResult = nameLayout,
        topLeft = Offset(center.x - nameLayout.size.width / 2f, nameTop),
    )
    drawText(
        textLayoutResult = scoreLayout,
        topLeft = Offset(
            center.x - scoreLayout.size.width / 2f,
            nameTop + nameLayout.size.height + 2f,
        ),
    )
}
