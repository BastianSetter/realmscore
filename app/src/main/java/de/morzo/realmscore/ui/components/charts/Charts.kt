package de.morzo.realmscore.ui.components.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

data class BarPart(
    val label: String,
    val value: Float,
    val color: Color,
)

/**
 * Horizontal stacked bar. Each part takes up a proportion of the row based on [BarPart.value].
 * Zero-value parts are skipped. Heights/labels are not rendered — only the bar itself.
 */
@Composable
fun StackedBar(
    parts: List<BarPart>,
    modifier: Modifier = Modifier,
    height: androidx.compose.ui.unit.Dp = 8.dp,
) {
    val total = parts.sumOf { it.value.toDouble() }.toFloat()
    if (total <= 0f) {
        Box(
            modifier
                .fillMaxWidth()
                .height(height)
                .background(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(50),
                ),
        )
        return
    }
    Row(
        modifier
            .fillMaxWidth()
            .height(height)
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(50),
            ),
    ) {
        parts.forEach { p ->
            if (p.value <= 0f) return@forEach
            Box(
                Modifier
                    .weight(p.value / total)
                    .fillMaxWidth()
                    .background(color = p.color),
            )
        }
    }
}

/**
 * Vertical bar chart. Renders a row of bars whose heights are proportional to [data].
 * If [labels] is provided and not empty, prints the labels under the chart in a small row.
 */
@Composable
fun BarChart(
    data: List<Int>,
    modifier: Modifier = Modifier,
    barColor: Color = MaterialTheme.colorScheme.primary,
    minBarHeightFraction: Float = 0.04f,
) {
    val max = data.maxOrNull() ?: 0
    Canvas(modifier = modifier.fillMaxWidth().height(96.dp)) {
        if (data.isEmpty() || max <= 0) return@Canvas
        val barCount = data.size
        val totalWidth = size.width
        val gap = totalWidth * 0.02f
        val barWidth = (totalWidth - gap * (barCount + 1)) / barCount
        val baseline = size.height
        data.forEachIndexed { i, value ->
            val fraction = value.toFloat() / max
            val rectHeight = if (value > 0) {
                (fraction * size.height).coerceAtLeast(size.height * minBarHeightFraction)
            } else 0f
            val x = gap + i * (barWidth + gap)
            val y = baseline - rectHeight
            drawRect(
                color = barColor,
                topLeft = Offset(x, y),
                size = androidx.compose.ui.geometry.Size(barWidth, rectHeight),
            )
        }
    }
}

@Composable
fun BarChartWithLabels(
    data: List<Int>,
    labels: List<String>,
    modifier: Modifier = Modifier,
    barColor: Color = MaterialTheme.colorScheme.primary,
) {
    androidx.compose.foundation.layout.Column(modifier = modifier.fillMaxWidth()) {
        BarChart(data = data, barColor = barColor)
        if (labels.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
            ) {
                labels.forEach { label ->
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/**
 * Simple smoothed line chart for [points]. Y-axis is auto-scaled to the data range.
 * Renders nothing when fewer than 2 points are given.
 */
@Composable
fun LineChart(
    points: List<Float>,
    modifier: Modifier = Modifier,
    lineColor: Color = MaterialTheme.colorScheme.primary,
    fillColor: Color = MaterialTheme.colorScheme.primaryContainer,
) {
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(120.dp),
    ) {
        if (points.size < 2) return@Canvas
        val minY = points.min()
        val maxY = points.max()
        val span = (maxY - minY).coerceAtLeast(1f)
        val stepX = size.width / (points.size - 1)

        fun yFor(value: Float): Float {
            val fraction = (value - minY) / span
            return size.height - fraction * size.height * 0.9f - size.height * 0.05f
        }

        val path = Path().apply {
            moveTo(0f, yFor(points[0]))
            for (i in 1 until points.size) {
                lineTo(i * stepX, yFor(points[i]))
            }
        }
        val fill = Path().apply {
            addPath(path)
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }
        drawPath(path = fill, color = fillColor.copy(alpha = 0.4f))
        drawPath(
            path = path,
            color = lineColor,
            style = Stroke(width = 4f, cap = StrokeCap.Round),
        )
    }
}
