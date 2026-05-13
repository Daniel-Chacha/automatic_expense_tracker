package com.personal.financetracker.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class ChartSeries(
    val label: String,
    val color: Color,
    /** Null = no data for that X position (renders as gap, not zero). */
    val values: List<Int?>
)

data class MultiSeriesData(
    val xLabels: List<String>,
    val series: List<ChartSeries>
) {
    val isEmpty: Boolean
        get() = series.isEmpty() || series.all { s -> s.values.all { it == null } }
}

enum class MultiChartType { BAR, LINE }

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MultiSeriesChart(
    data: MultiSeriesData,
    type: MultiChartType,
    modifier: Modifier = Modifier,
    chartHeight: Dp = 200.dp
) {
    if (data.isEmpty) {
        Box(
            modifier = modifier.height(chartHeight),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "No data for this selection",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
        }
        return
    }

    val maxValue = remember(data) {
        data.series.flatMap { it.values }.filterNotNull().maxOrNull()?.toFloat()?.coerceAtLeast(1f) ?: 1f
    }

    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val axisColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)

    // Width grows with x-tick count so we can horizontally scroll if 12 months
    // squeeze the bars below readable width.
    val minWidthPerX = if (data.xLabels.size >= 12) 56.dp else 64.dp
    val totalContentWidth = minWidthPerX * data.xLabels.size + 56.dp
    val scrollState = rememberScrollState()

    Column(modifier = modifier) {
        Row(modifier = Modifier.horizontalScroll(scrollState)) {
            Column(modifier = Modifier.width(totalContentWidth)) {
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(chartHeight)
                ) {
                    drawChart(
                        data = data,
                        type = type,
                        maxValue = maxValue,
                        gridColor = gridColor,
                        axisColor = axisColor
                    )
                }
                Spacer(Modifier.height(4.dp))
                Row(modifier = Modifier.fillMaxWidth().padding(start = 40.dp, end = 8.dp)) {
                    data.xLabels.forEach { label ->
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        SeriesLegend(series = data.series)
    }
}

private fun DrawScope.drawChart(
    data: MultiSeriesData,
    type: MultiChartType,
    maxValue: Float,
    gridColor: Color,
    axisColor: Color
) {
    val leftPad = 40f
    val rightPad = 8f
    val topPad = 8f
    val bottomPad = 8f
    val plotLeft = leftPad
    val plotTop = topPad
    val plotWidth = size.width - leftPad - rightPad
    val plotHeight = size.height - topPad - bottomPad

    val xCount = data.xLabels.size.coerceAtLeast(1)
    val xStep = plotWidth / xCount

    // Horizontal gridlines (5 of them)
    for (i in 0..4) {
        val y = plotTop + plotHeight * (1f - i / 4f)
        drawLine(
            color = gridColor.copy(alpha = if (i == 0) 0.6f else 0.25f),
            start = Offset(plotLeft, y),
            end = Offset(plotLeft + plotWidth, y),
            strokeWidth = 1f
        )
    }

    // Left axis line
    drawLine(
        color = axisColor,
        start = Offset(plotLeft, plotTop),
        end = Offset(plotLeft, plotTop + plotHeight),
        strokeWidth = 1.5f
    )

    when (type) {
        MultiChartType.BAR -> drawGroupedBars(data, maxValue, plotLeft, plotTop, plotHeight, xStep)
        MultiChartType.LINE -> drawOverlaidLines(data, maxValue, plotLeft, plotTop, plotHeight, xStep)
    }
}

private fun DrawScope.drawGroupedBars(
    data: MultiSeriesData,
    maxValue: Float,
    plotLeft: Float,
    plotTop: Float,
    plotHeight: Float,
    xStep: Float
) {
    val seriesCount = data.series.size.coerceAtLeast(1)
    val groupPaddingRatio = 0.18f
    val groupInner = xStep * (1 - groupPaddingRatio)
    val barGapRatio = 0.18f
    val perSeriesWidth = groupInner / seriesCount
    val barWidth = perSeriesWidth * (1 - barGapRatio)

    for (xIdx in data.xLabels.indices) {
        val groupCenterX = plotLeft + xStep * xIdx + xStep / 2f
        val groupStart = groupCenterX - groupInner / 2f

        for (sIdx in data.series.indices) {
            val v = data.series[sIdx].values.getOrNull(xIdx) ?: continue
            val frac = (v.toFloat() / maxValue).coerceIn(0f, 1f)
            val barHeight = plotHeight * frac
            val barX = groupStart + perSeriesWidth * sIdx + (perSeriesWidth - barWidth) / 2f
            val barTop = plotTop + plotHeight - barHeight
            drawRoundRect(
                color = data.series[sIdx].color,
                topLeft = Offset(barX, barTop),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(4f, 4f)
            )
        }
    }
}

private fun DrawScope.drawOverlaidLines(
    data: MultiSeriesData,
    maxValue: Float,
    plotLeft: Float,
    plotTop: Float,
    plotHeight: Float,
    xStep: Float
) {
    for (series in data.series) {
        val path = Path()
        var started = false
        for (xIdx in data.xLabels.indices) {
            val v = series.values.getOrNull(xIdx)
            val cx = plotLeft + xStep * xIdx + xStep / 2f
            if (v == null) {
                started = false
                continue
            }
            val frac = (v.toFloat() / maxValue).coerceIn(0f, 1f)
            val cy = plotTop + plotHeight - plotHeight * frac
            if (!started) {
                path.moveTo(cx, cy)
                started = true
            } else {
                path.lineTo(cx, cy)
            }
            drawCircle(color = series.color, radius = 4f, center = Offset(cx, cy))
        }
        drawPath(
            path = path,
            color = series.color,
            style = Stroke(width = 3f, cap = StrokeCap.Round)
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SeriesLegend(series: List<ChartSeries>, modifier: Modifier = Modifier) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        series.forEach { s ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(s.color)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = s.label,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}
