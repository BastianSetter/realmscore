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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
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
import de.morzo.realmscore.domain.model.displayName
import de.morzo.realmscore.ui.theme.SuitColors
import de.morzo.realmscore.ui.sandbox.components.CardBreakdownDetail
import de.morzo.realmscore.ui.sandbox.components.formatDelta
import de.morzo.realmscore.ui.util.currentLocale
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

private val BonusColor = Color(0xFF2E7D32)
private val PenaltyColor = Color(0xFFC62828)
private val BlankColor = Color(0xFF111111)

/** Height of the ring canvas. Taller than the node-circle diameter so the ring can grow (Phase 25.3). */
private val RingBoxHeight = 360.dp

/** Fixed node radius — kept constant so changing [RingBoxHeight] resizes the ring, not the cards. */
private val NodeRadius = 42.dp

/**
 * Outer (neighbour) edges run centre-to-centre and bow outward. This is how far the curve's control
 * point sits past the edge midpoint, in multiples of the node radius — the knob for outward curve.
 */
private const val NEIGHBOUR_OUTWARD_CURVE = 2f

/**
 * Centre-crossing (far) edges are pulled toward the ring centre by this fraction of the distance
 * from the edge midpoint to the centre (0 = straight, 1 = right through the centre).
 */
private const val FAR_CENTER_CURVE = 0.65f

/**
 * Centre-crossing (far) edges don't all meet at a node's centre: their endpoints fan out along the
 * node's tangent line (orthogonal to the ring radius) at equidistant slots. This is the gap between
 * adjacent slots, as a fraction of the node radius.
 */
private const val FAR_ATTACH_SPACING = 0.5f

/**
 * Upper bound on a far-edge fan's half-width, as a fraction of the node radius. With enough cards
 * the reserved slots (one per far partner) would otherwise reach past the rim, leaving line ends
 * floating off the node; when the fixed [FAR_ATTACH_SPACING] would overflow this, the slots are
 * compressed to fit so every endpoint stays inside the node (and thus hidden under it).
 */
private const val FAR_FAN_MAX_HALF = 0.8f

/**
 * Ring visualization of a 7-card hand (Phase 18, redesigned in Phase 25.3). Cards are placed
 * around a circle with the highest-scoring card on top; curved lines show how cards influence each
 * other. Direction is encoded by a light→dark gradient (light = outgoing, dark = incoming) instead
 * of arrowheads, neighbour edges bow outward and distant edges curve toward the centre, and a
 * blanking relationship is drawn as a black line. Tapping a card switches the ring into a detail
 * mode that isolates that card's contribution.
 */
@Composable
fun HandRingView(
    handCards: List<CardDefinition>,
    scoringResult: ScoringResult,
    cardLookup: (String) -> CardDefinition?,
    modifier: Modifier = Modifier,
) {
    if (handCards.isEmpty()) return

    // When the Necromancer pulled a card, append it as an 8th node so its interactions with the
    // hand are visualized; without a pick the ring stays at the seven hand cards. Everything below
    // (connections, layout, nodes, tap hit-testing) then operates on this augmented list.
    val cards = remember(handCards, scoringResult) {
        val pick = scoringResult.perCard.firstOrNull { it.isNecromancerPick }
            ?.let { cardLookup(it.cardKey) }
        if (pick != null) handCards + pick else handCards
    }

    val connections = remember(scoringResult, cards) { buildRingConnections(cards, scoringResult) }
    val scores = remember(scoringResult, cards) {
        val byKey = scoringResult.perCard.associateBy { it.cardKey }
        cards.map { byKey[it.key]?.contributedScore ?: 0 }
    }
    val layout = remember(cards, connections, scores) {
        RingLayoutOptimizer.optimize(cards, scores, connections)
    }
    // pos[cardIdx] = ring position of that card; used for ring-distance (neighbour) classification.
    val posByCardIdx = remember(layout) {
        val arr = IntArray(layout.size)
        layout.forEachIndexed { pos, cardIdx -> arr[cardIdx] = pos }
        arr
    }
    val maxWeight = remember(connections) {
        connections.maxOfOrNull { abs(it.weight) }?.coerceAtLeast(1) ?: 1
    }
    val resultByKey = remember(scoringResult) { scoringResult.perCard.associateBy { it.cardKey } }

    var tappedCardIdx by remember(cards) { mutableStateOf<Int?>(null) }

    val selectedIdx = tappedCardIdx

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
    val locale = currentLocale()
    val onSurface = MaterialTheme.colorScheme.onSurface
    val highlight = MaterialTheme.colorScheme.primary
    val surface = MaterialTheme.colorScheme.surface
    val darkTheme = androidx.compose.foundation.isSystemInDarkTheme()

    Column(modifier = modifier.fillMaxWidth()) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(RingBoxHeight),
        ) {
            val widthPx = with(density) { maxWidth.toPx() }
            val heightPx = with(density) { maxHeight.toPx() }
            val center = Offset(widthPx / 2f, heightPx / 2f)
            // Node size is fixed (independent of the box) so enlarging the ring leaves the cards the
            // same size; the ring radius then expands to fill the available space, reserving room for
            // one node plus a small rim margin.
            val cardRadius = with(density) { NodeRadius.toPx() }
            val ringRadius = minOf(widthPx, heightPx) / 2f - cardRadius - with(density) { 8.dp.toPx() }

            // Pixel center of each card index for both drawing and tap hit-testing.
            val nodeCenters = remember(layout, widthPx, heightPx) {
                val arr = arrayOfNulls<Offset>(cards.size)
                layout.forEachIndexed { pos, cardIdx ->
                    arr[cardIdx] = positionCenter(pos, layout.size, center, ringRadius)
                }
                arr.requireNoNulls()
            }

            // Curve geometry (start/control/end) per connection, shared by the lines and the chips.
            val edgeGeoms = remember(connections, nodeCenters, cardRadius) {
                computeEdgeGeometries(
                    connections = connections,
                    nodeCenters = nodeCenters,
                    posByCardIdx = posByCardIdx,
                    layoutSize = layout.size,
                    ringCenter = center,
                    cardRadius = cardRadius,
                )
            }

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(RingBoxHeight)
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
                // 1) Connection lines, always drawn beneath the nodes regardless of selection.
                edgeGeoms.forEach { geom ->
                    drawConnection(
                        start = geom.start,
                        end = geom.end,
                        control = geom.control,
                        weight = geom.conn.weight,
                        isBlanking = geom.conn.isBlanking,
                        maxWeight = maxWeight,
                        progress = progress,
                    )
                }

                // 2) Card nodes on top.
                cards.forEachIndexed { idx, card ->
                    val res = resultByKey[card.key]
                    val contributed = res?.contributedScore ?: 0
                    // In detail mode only the selected card is highlighted; all others dim but keep
                    // showing their own total score.
                    val dimmed = selectedIdx != null && idx != selectedIdx
                    val centerValue = if (selectedIdx == idx) {
                        card.baseStrength.toString()
                    } else {
                        formatDelta(contributed)
                    }
                    drawCardNode(
                        textMeasurer = textMeasurer,
                        card = card,
                        locale = locale,
                        centerValue = centerValue,
                        isBlanked = res?.isBlanked == true,
                        isSelected = idx == selectedIdx,
                        dimmed = dimmed,
                        center = nodeCenters[idx],
                        radius = cardRadius,
                        textColor = onSurface,
                        highlightColor = highlight,
                        backgroundColor = surface,
                        darkTheme = darkTheme,
                        bookOfChangesSuit = res?.bookOfChangesSuit,
                    )
                }

                // 3) Detail-mode effect chips, drawn ON TOP of the nodes so they aren't covered.
                // Every line incident to the selected card is labelled with its weight (the effect
                // on the edge's owner), placed just inside the owner card's rim: effects on the
                // selected card land on the selected node, effects it exerts on others land on those
                // other nodes (spec 25.3 §5).
                if (selectedIdx != null && progress > 0.99f) {
                    edgeGeoms.forEach { geom ->
                        val conn = geom.conn
                        if (conn.isBlanking) return@forEach
                        if (conn.fromCardIdx != selectedIdx && conn.toCardIdx != selectedIdx) {
                            return@forEach
                        }
                        // Seat the chip exactly where this line crosses the owner (to) node's rim.
                        val anchor = curveRimPoint(
                            start = geom.start,
                            control = geom.control,
                            end = geom.end,
                            nodeCenter = nodeCenters[conn.toCardIdx],
                            radius = cardRadius,
                        )
                        drawDetailChip(
                            anchor = anchor,
                            label = abs(conn.weight).toString(),
                            positive = conn.weight > 0,
                            textMeasurer = textMeasurer,
                        )
                    }
                }
            }
        }

        val selected = tappedCardIdx
        val selectedRes = selected?.let { resultByKey[cards[it].key] }
        if (selectedRes != null) {
            CardBreakdownDetail(
                result = scoringResult,
                cardResult = selectedRes,
                cardLookup = cardLookup,
                modifier = Modifier.padding(top = 8.dp),
                initiallyExpanded = true,
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

/** Computes the on-screen center of a ring position. */
private fun positionCenter(pos: Int, count: Int, center: Offset, ringRadius: Float): Offset {
    val angle = (pos.toFloat() / count.toFloat()) * 2f * PI.toFloat() - PI.toFloat() / 2f
    return Offset(center.x + ringRadius * cos(angle), center.y + ringRadius * sin(angle))
}

/** Smallest hop count between two ring positions (0 .. n/2). */
private fun arcDist(a: Int, b: Int, n: Int): Int {
    val d = abs(a - b)
    return min(d, n - d)
}

/** Unit vector, or zero if [v] is degenerate. */
private fun normalize(v: Offset): Offset {
    val len = hypot(v.x.toDouble(), v.y.toDouble()).toFloat()
    return if (len < 0.001f) Offset.Zero else Offset(v.x / len, v.y / len)
}

/** Unit vector pointing from the ring [center] toward [point] (radially outward). */
private fun unitOutward(point: Offset, center: Offset): Offset = normalize(point - center)

/** Resolved on-screen geometry of one connection's quadratic curve. */
private class EdgeGeometry(
    val conn: RingConnection,
    val start: Offset,
    val end: Offset,
    val control: Offset,
)

/**
 * Resolves every connection to a quadratic curve (start, control, end):
 *  - Neighbour edges run centre→centre and bow outward ([NEIGHBOUR_OUTWARD_CURVE]).
 *  - Far edges fan their endpoints along each node's tangent line at equidistant slots
 *    ([FAR_ATTACH_SPACING]) and curve toward the ring centre ([FAR_CENTER_CURVE]). Slots are
 *    ordered by where the other node lies along the tangent so the fan doesn't self-cross.
 */
private fun computeEdgeGeometries(
    connections: List<RingConnection>,
    nodeCenters: Array<Offset>,
    posByCardIdx: IntArray,
    layoutSize: Int,
    ringCenter: Offset,
    cardRadius: Float,
): List<EdgeGeometry> {
    fun isNeighbour(conn: RingConnection) =
        arcDist(posByCardIdx[conn.fromCardIdx], posByCardIdx[conn.toCardIdx], layoutSize) == 1

    // Tangent at a node = unit vector orthogonal to its ring radius.
    fun tangent(i: Int): Offset {
        val r = unitOutward(nodeCenters[i], ringCenter)
        return Offset(-r.y, r.x)
    }

    // Reserve a tangent-line slot on every node for EACH of its far partners (any node ≥ 2 hops
    // away), ordered along the tangent — independent of which connections actually exist. An edge
    // then always attaches at the slot reserved for its specific partner, so its position is stable
    // regardless of how many other edges are present; slots without a connection are simply never
    // drawn. Every node thus gets the same fan width (n − 3 far partners).
    val n = nodeCenters.size
    val spacing = cardRadius * FAR_ATTACH_SPACING
    val maxHalf = cardRadius * FAR_FAN_MAX_HALF
    val slotByNodePartner = Array(n) { HashMap<Int, Offset>() }
    for (i in 0 until n) {
        val t = tangent(i)
        val partners = (0 until n).filter { j ->
            j != i && arcDist(posByCardIdx[i], posByCardIdx[j], layoutSize) >= 2
        }.sortedBy { j ->
            val d = nodeCenters[j] - nodeCenters[i]
            d.x * t.x + d.y * t.y
        }
        // Keep the whole fan inside the node: compress the spacing only if it would overflow.
        val count = partners.size
        val rawHalf = spacing * (count - 1) / 2f
        val effSpacing = if (count > 1 && rawHalf > maxHalf) (2f * maxHalf) / (count - 1) else spacing
        partners.forEachIndexed { rank, j ->
            val offset = (rank - (count - 1) / 2f) * effSpacing
            slotByNodePartner[i][j] = nodeCenters[i] + t * offset
        }
    }

    return connections.map { conn ->
        val fromC = nodeCenters[conn.fromCardIdx]
        val toC = nodeCenters[conn.toCardIdx]
        if (isNeighbour(conn)) {
            val mid = (fromC + toC) / 2f
            val control = mid + unitOutward(mid, ringCenter) * (cardRadius * NEIGHBOUR_OUTWARD_CURVE)
            EdgeGeometry(conn, fromC, toC, control)
        } else {
            val start = slotByNodePartner[conn.fromCardIdx][conn.toCardIdx] ?: fromC
            val end = slotByNodePartner[conn.toCardIdx][conn.fromCardIdx] ?: toC
            val mid = (start + end) / 2f
            val control = mid + (ringCenter - mid) * FAR_CENTER_CURVE
            EdgeGeometry(conn, start, end, control)
        }
    }
}

/**
 * Point where the quadratic [start]→[control]→[end] crosses the circle of [radius] around
 * [nodeCenter], searched over the half of the curve nearest [end] (the owner node). Used to seat a
 * detail chip exactly on the rim where its line meets the node.
 */
private fun curveRimPoint(
    start: Offset,
    control: Offset,
    end: Offset,
    nodeCenter: Offset,
    radius: Float,
): Offset {
    var best = end
    var bestErr = Float.MAX_VALUE
    var t = 0.5f
    while (t <= 1.0001f) {
        val u = 1f - t
        val p = start * (u * u) + control * (2f * u * t) + end * (t * t)
        val d = hypot((p.x - nodeCenter.x).toDouble(), (p.y - nodeCenter.y).toDouble()).toFloat()
        val err = abs(d - radius)
        if (err < bestErr) {
            bestErr = err
            best = p
        }
        t += 0.02f
    }
    return best
}

private fun DrawScope.drawConnection(
    start: Offset,
    end: Offset,
    control: Offset,
    weight: Int,
    isBlanking: Boolean,
    maxWeight: Int,
    progress: Float,
) {
    if (!isBlanking && weight == 0) return
    val span = end - start
    if (hypot(span.x.toDouble(), span.y.toDouble()).toFloat() < 0.001f) return

    val fullPath = Path().apply {
        moveTo(start.x, start.y)
        quadraticTo(control.x, control.y, end.x, end.y)
    }

    val (lightColor, darkColor) = when {
        isBlanking -> Color(0xFF9E9E9E) to BlankColor
        weight > 0 -> lerp(BonusColor, Color.White, 0.55f) to BonusColor
        else -> lerp(PenaltyColor, Color.White, 0.55f) to PenaltyColor
    }
    val strokeWidth = if (isBlanking) {
        3.dp.toPx()
    } else {
        (abs(weight).toFloat() / maxWeight * 6f + 2f).dp.toPx()
    }
    // Light = outgoing (source), dark = incoming (target).
    val brush = Brush.linearGradient(
        colors = listOf(lightColor, darkColor),
        start = start,
        end = end,
    )

    // Grow the line along the curve for the reveal animation.
    val drawn = if (progress >= 0.999f) {
        fullPath
    } else {
        Path().also { dest ->
            val measure = PathMeasure().apply { setPath(fullPath, false) }
            measure.getSegment(0f, measure.length * progress.coerceIn(0f, 1f), dest, true)
        }
    }
    drawPath(
        path = drawn,
        brush = brush,
        style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
    )
}

/**
 * Draws a detail-mode effect chip as a coloured circle centred at [anchor]: green for a bonus, red
 * for a penalty (the colour carries the sign, so the number is unsigned), white number on top.
 */
private fun DrawScope.drawDetailChip(
    anchor: Offset,
    label: String,
    positive: Boolean,
    textMeasurer: TextMeasurer,
) {
    val labelStyle = TextStyle(
        color = Color.White,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
    )
    val layout = textMeasurer.measure(text = label, style = labelStyle)
    val radius = max(layout.size.width, layout.size.height) / 2f + 4.dp.toPx()
    drawCircle(
        color = if (positive) BonusColor else PenaltyColor,
        radius = radius,
        center = anchor,
    )
    // Thin light ring so the chip reads clearly even on a same-hued node.
    drawCircle(
        color = Color.White.copy(alpha = 0.85f),
        radius = radius,
        center = anchor,
        style = Stroke(width = 1.5.dp.toPx()),
    )
    drawText(
        textLayoutResult = layout,
        topLeft = Offset(
            anchor.x - layout.size.width / 2f,
            anchor.y - layout.size.height / 2f,
        ),
    )
}

private fun DrawScope.drawCardNode(
    textMeasurer: TextMeasurer,
    card: CardDefinition,
    locale: Locale,
    centerValue: String,
    isBlanked: Boolean,
    isSelected: Boolean,
    dimmed: Boolean,
    center: Offset,
    radius: Float,
    textColor: Color,
    highlightColor: Color,
    backgroundColor: Color,
    darkTheme: Boolean,
    bookOfChangesSuit: de.morzo.realmscore.domain.model.Suit?,
) {
    val alpha = when {
        isBlanked -> 0.35f
        dimmed -> 0.4f
        else -> 1f
    }
    val bgColor = if (isBlanked) Color.Gray else SuitColors.forSuit(card.suit, darkTheme)

    // Opaque backing so dimmed/blanked nodes (drawn translucent) still hide the connection lines
    // ending beneath them.
    drawCircle(color = backgroundColor, radius = radius, center = center)
    // A card re-coloured by the Book of Changes is split down the middle: the left half keeps its
    // printed suit colour, the right half shows the new suit. Blanked nodes stay flat grey.
    val changedColor = bookOfChangesSuit?.takeIf { !isBlanked }?.let { SuitColors.forSuit(it, darkTheme) }
    if (changedColor != null) {
        val topLeft = Offset(center.x - radius, center.y - radius)
        val arcSize = androidx.compose.ui.geometry.Size(radius * 2f, radius * 2f)
        drawArc(
            color = bgColor.copy(alpha = alpha),
            startAngle = 90f,
            sweepAngle = 180f,
            useCenter = true,
            topLeft = topLeft,
            size = arcSize,
        )
        drawArc(
            color = changedColor.copy(alpha = alpha),
            startAngle = -90f,
            sweepAngle = 180f,
            useCenter = true,
            topLeft = topLeft,
            size = arcSize,
        )
    } else {
        drawCircle(color = bgColor.copy(alpha = alpha), radius = radius, center = center)
    }
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

    val shortName = card.displayName(locale).let { if (it.length > 10) it.take(9) + "…" else it }
    val nameStyle = TextStyle(
        color = textColor.copy(alpha = alpha),
        fontSize = 10.sp,
        fontWeight = FontWeight.SemiBold,
        textAlign = TextAlign.Center,
    )
    // Spec 25.3 §4: prominent total points in the card centre.
    val scoreStyle = TextStyle(
        color = textColor.copy(alpha = alpha),
        fontSize = 18.sp,
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
    val scoreLayout = textMeasurer.measure(text = centerValue, style = scoreStyle)

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
