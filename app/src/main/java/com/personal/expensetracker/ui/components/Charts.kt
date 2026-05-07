package com.personal.expensetracker.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.personal.expensetracker.util.FormatUtils

data class ChartSegment(
    val label: String,
    val value: Int,      // cents
    val color: Color,
    val percentage: Float // 0..1
)

@Composable
fun DonutChart(
    segments: List<ChartSegment>,
    centerText: String,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(180.dp)) {
            val strokeWidth = 32.dp.toPx()
            val radius = (size.minDimension - strokeWidth) / 2
            val topLeft = Offset(
                (size.width - 2 * radius) / 2 - strokeWidth / 2,
                (size.height - 2 * radius) / 2 - strokeWidth / 2
            )
            val arcSize = Size(2 * radius + strokeWidth, 2 * radius + strokeWidth)

            if (segments.isEmpty()) {
                // Empty state — draw a grey ring
                drawArc(
                    color = Color.Gray.copy(alpha = 0.3f),
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
                )
            } else {
                var startAngle = -90f
                segments.forEach { segment ->
                    val sweep = 360f * segment.percentage
                    drawArc(
                        color = segment.color,
                        startAngle = startAngle,
                        sweepAngle = sweep - 2f, // gap between segments
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )
                    startAngle += sweep
                }
            }
        }

        // Center text
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "Total",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            Text(
                text = centerText,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
fun ChartLegend(
    segments: List<ChartSegment>,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        segments.take(6).forEach { segment ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Canvas(modifier = Modifier.size(10.dp)) {
                    drawCircle(color = segment.color)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = segment.label,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = FormatUtils.formatAmountShort(segment.value),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

/**
 * Simple line chart for monthly spend trend. Pure Compose — avoids tying
 * the dashboard to Vico's still-shifting alpha API.
 */
@Composable
fun MonthlyTrendChart(
    points: List<Int>,
    modifier: Modifier = Modifier
) {
    if (points.isEmpty()) {
        Box(modifier = modifier.height(120.dp), contentAlignment = Alignment.Center) {
            Text(
                "Not enough data yet",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
        }
        return
    }

    val max = (points.max().coerceAtLeast(1)).toFloat()
    val color = MaterialTheme.colorScheme.primary

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(120.dp)
            .padding(8.dp)
    ) {
        if (points.size == 1) {
            drawCircle(color = color, radius = 6f, center = Offset(size.width / 2, size.height / 2))
            return@Canvas
        }
        val stepX = size.width / (points.size - 1).toFloat()
        var prev: Offset? = null
        points.forEachIndexed { idx, value ->
            val x = idx * stepX
            val y = size.height - (value / max) * size.height
            val p = Offset(x, y)
            prev?.let { from ->
                drawLine(color = color, start = from, end = p, strokeWidth = 4f, cap = StrokeCap.Round)
            }
            drawCircle(color = color, radius = 4f, center = p)
            prev = p
        }
    }
}
