package com.example.tradestrat.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tradestrat.model.*
import com.example.ui.theme.*
import kotlin.math.*

@Composable
fun CandlestickChart(
    candles: List<Candle>,
    indicators: CalculatedIndicators,
    signalMarkers: List<SignalMarker>,
    modifier: Modifier = Modifier,
    asset: MarketAsset? = null,
    timeframe: Timeframe? = null,
    showIndicators: Boolean = true,
    showSignals: Boolean = true
) {
    if (candles.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(280.dp)
                .background(TradeSurfaceDark, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text("No candlestick data available", color = TextMuted)
        }
        return
    }

    var visibleBars by remember { mutableStateOf(60) }
    var scrollOffset by remember { mutableStateOf(0) }
    var selectedBarIndex by remember { mutableStateOf<Int?>(null) }
    val textMeasurer = rememberTextMeasurer()

    // Clamp visible bars & scroll
    val maxBars = candles.size
    val clampedVisibleBars = visibleBars.coerceIn(20, maxBars)
    val maxScroll = max(0, maxBars - clampedVisibleBars)
    val clampedScroll = scrollOffset.coerceIn(0, maxScroll)

    // Slice candles to visible window (defaults to newest candles at the right)
    val startIdx = max(0, maxBars - clampedVisibleBars - clampedScroll)
    val endIdx = min(maxBars, startIdx + clampedVisibleBars)
    val visibleCandles = candles.subList(startIdx, endIdx)

    val visibleFastMa = indicators.fastMa.subList(startIdx, endIdx)
    val visibleSlowMa = indicators.slowMa.subList(startIdx, endIdx)
    val visibleBbUpper = indicators.bbUpper.subList(startIdx, endIdx)
    val visibleBbLower = indicators.bbLower.subList(startIdx, endIdx)
    val visibleBbMid = indicators.bbMiddle.subList(startIdx, endIdx)
    val visibleSupertrend = indicators.supertrend.subList(startIdx, endIdx)

    val visibleSignals = signalMarkers.filter { it.barIndex in startIdx until endIdx }

    val activeSelectedCandle = selectedBarIndex?.let { selIdx ->
        if (selIdx in startIdx until endIdx) candles[selIdx] else null
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("candlestick_chart_card"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = BentoCardBg),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(BentoBorder))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Header Controls & Legend
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(
                        imageVector = Icons.Default.ShowChart,
                        contentDescription = "Chart",
                        tint = BullGreen,
                        modifier = Modifier.size(20.dp)
                    )
                    if (asset != null) {
                        Surface(
                            shape = CircleShape,
                            color = BentoLilacContainer
                        ) {
                            Text(
                                text = asset.category.name,
                                style = MaterialTheme.typography.labelSmall,
                                color = BentoLilacText,
                                fontSize = 9.sp,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = "${asset.symbol} • ${timeframe?.label ?: "D1"}",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    } else {
                        Text(
                            text = "Price Action & Execution Signals",
                            style = MaterialTheme.typography.labelLarge,
                            color = TextPrimary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    // Zoom In
                    IconButton(
                        onClick = { visibleBars = (visibleBars - 15).coerceAtLeast(20) },
                        modifier = Modifier.size(32.dp).testTag("zoom_in_button")
                    ) {
                        Icon(Icons.Default.Remove, contentDescription = "Zoom In", tint = TextSecondary, modifier = Modifier.size(16.dp))
                    }
                    // Zoom Out
                    IconButton(
                        onClick = { visibleBars = (visibleBars + 15).coerceAtMost(maxBars) },
                        modifier = Modifier.size(32.dp).testTag("zoom_out_button")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Zoom Out", tint = TextSecondary, modifier = Modifier.size(16.dp))
                    }
                }
            }

            // Indicator Legend pills
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LegendPill("Fast MA", FastMaLine)
                LegendPill("Slow MA", SlowMaLine)
                LegendPill("Bollinger", BollingerUpperLine)
                LegendPill("Buy ▲", BullGreen)
                LegendPill("Sell ▼", BearRed)
            }

            // Selected Bar Tooltip Info Bar
            if (activeSelectedCandle != null) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = TradeSurfaceElevated
                ) {
                    Row(
                        modifier = Modifier
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = activeSelectedCandle.formattedDate(),
                            style = TextStyle(fontSize = 11.sp, color = CyanAccent, fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "O: ${activeSelectedCandle.open}  H: ${activeSelectedCandle.high}  L: ${activeSelectedCandle.low}  C: ${activeSelectedCandle.close}",
                            style = TextStyle(fontSize = 11.sp, color = TextPrimary)
                        )
                        val changeColor = if (activeSelectedCandle.isBullish) BullGreen else BearRed
                        Text(
                            text = String.format("%+.2f%%", activeSelectedCandle.changePct),
                            style = TextStyle(fontSize = 11.sp, color = changeColor, fontWeight = FontWeight.Bold)
                        )
                    }
                }
            }

            // Main Canvas
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp)
                    .background(TradeDarkBg, RoundedCornerShape(8.dp))
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTapGestures { offset ->
                                val barWidth = size.width / visibleCandles.size
                                val touchedRelativeIdx = (offset.x / barWidth).toInt().coerceIn(0, visibleCandles.size - 1)
                                selectedBarIndex = startIdx + touchedRelativeIdx
                            }
                        }
                        .pointerInput(Unit) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                val barWidth = size.width / visibleCandles.size
                                val barsDragged = (dragAmount.x / barWidth).toInt()
                                if (barsDragged != 0) {
                                    scrollOffset = (scrollOffset + barsDragged).coerceIn(0, maxScroll)
                                }
                                val touchedRelativeIdx = (change.position.x / barWidth).toInt().coerceIn(0, visibleCandles.size - 1)
                                selectedBarIndex = startIdx + touchedRelativeIdx
                            }
                        }
                ) {
                    if (visibleCandles.isEmpty()) return@Canvas

                    val chartWidth = size.width
                    val totalHeight = size.height
                    val priceAreaHeight = totalHeight * 0.78f
                    val volumeAreaHeight = totalHeight * 0.20f
                    val volumeAreaTop = totalHeight * 0.80f

                    // Calculate Price range (including visible indicators)
                    var minPrice = visibleCandles.minOf { it.low }
                    var maxPrice = visibleCandles.maxOf { it.high }

                    if (showIndicators) {
                        visibleFastMa.filterNotNull().forEach {
                            minPrice = min(minPrice, it)
                            maxPrice = max(maxPrice, it)
                        }
                        visibleSlowMa.filterNotNull().forEach {
                            minPrice = min(minPrice, it)
                            maxPrice = max(maxPrice, it)
                        }
                        visibleBbLower.filterNotNull().forEach {
                            minPrice = min(minPrice, it)
                        }
                        visibleBbUpper.filterNotNull().forEach {
                            maxPrice = max(maxPrice, it)
                        }
                    }

                    // Add 5% padding to price range
                    val priceSpan = max(0.0001, maxPrice - minPrice)
                    val paddedMinPrice = minPrice - priceSpan * 0.05
                    val paddedMaxPrice = maxPrice + priceSpan * 0.05
                    val effectivePriceSpan = paddedMaxPrice - paddedMinPrice

                    val maxVolume = max(1.0, visibleCandles.maxOf { it.volume })

                    // Draw Horizontal Grid lines & Price Labels
                    val gridLinesCount = 4
                    for (g in 0..gridLinesCount) {
                        val y = priceAreaHeight * (g.toFloat() / gridLinesCount)
                        val priceAtGrid = paddedMaxPrice - (effectivePriceSpan * (g.toDouble() / gridLinesCount))

                        drawLine(
                            color = ChartGridLine,
                            start = Offset(0f, y),
                            end = Offset(chartWidth, y),
                            strokeWidth = 1f
                        )

                        drawText(
                            textMeasurer = textMeasurer,
                            text = String.format("%.2f", priceAtGrid),
                            topLeft = Offset(chartWidth - 55.dp.toPx(), y - 12.sp.toPx()),
                            style = TextStyle(fontSize = 9.sp, color = TextMuted)
                        )
                    }

                    val barCount = visibleCandles.size
                    val candleSpacing = chartWidth / barCount
                    val candleBodyWidth = max(2f, candleSpacing * 0.65f)

                    fun priceToY(price: Double): Float {
                        val normalized = (paddedMaxPrice - price) / effectivePriceSpan
                        return (normalized * priceAreaHeight).toFloat().coerceIn(0f, priceAreaHeight)
                    }

                    // Draw Bollinger Bands Shaded Area & Lines
                    if (showIndicators && visibleBbUpper.size == barCount && visibleBbLower.size == barCount) {
                        val upperPath = Path()
                        val lowerPath = Path()
                        var upperStarted = false
                        var lowerStarted = false

                        for (i in 0 until barCount) {
                            val up = visibleBbUpper[i]
                            val low = visibleBbLower[i]
                            val x = (i * candleSpacing) + (candleSpacing / 2f)

                            if (up != null) {
                                val yUp = priceToY(up)
                                if (!upperStarted) {
                                    upperPath.moveTo(x, yUp)
                                    upperStarted = true
                                } else {
                                    upperPath.lineTo(x, yUp)
                                }
                            }

                            if (low != null) {
                                val yLow = priceToY(low)
                                if (!lowerStarted) {
                                    lowerPath.moveTo(x, yLow)
                                    lowerStarted = true
                                } else {
                                    lowerPath.lineTo(x, yLow)
                                }
                            }
                        }

                        if (upperStarted) {
                            drawPath(upperPath, BollingerUpperLine, style = Stroke(width = 1.5f))
                        }
                        if (lowerStarted) {
                            drawPath(lowerPath, BollingerLowerLine, style = Stroke(width = 1.5f))
                        }
                    }

                    // Draw Fast MA Line
                    if (showIndicators && visibleFastMa.any { it != null }) {
                        val fastPath = Path()
                        var started = false
                        for (i in 0 until barCount) {
                            val v = visibleFastMa[i] ?: continue
                            val x = (i * candleSpacing) + (candleSpacing / 2f)
                            val y = priceToY(v)
                            if (!started) {
                                fastPath.moveTo(x, y)
                                started = true
                            } else {
                                fastPath.lineTo(x, y)
                            }
                        }
                        if (started) {
                            drawPath(fastPath, FastMaLine, style = Stroke(width = 2.0f, cap = StrokeCap.Round))
                        }
                    }

                    // Draw Slow MA Line
                    if (showIndicators && visibleSlowMa.any { it != null }) {
                        val slowPath = Path()
                        var started = false
                        for (i in 0 until barCount) {
                            val v = visibleSlowMa[i] ?: continue
                            val x = (i * candleSpacing) + (candleSpacing / 2f)
                            val y = priceToY(v)
                            if (!started) {
                                slowPath.moveTo(x, y)
                                started = true
                            } else {
                                slowPath.lineTo(x, y)
                            }
                        }
                        if (started) {
                            drawPath(slowPath, SlowMaLine, style = Stroke(width = 2.0f, cap = StrokeCap.Round))
                        }
                    }

                    // Draw Candlesticks & Volume Bars
                    for (i in 0 until barCount) {
                        val c = visibleCandles[i]
                        val centerX = (i * candleSpacing) + (candleSpacing / 2f)
                        val openY = priceToY(c.open)
                        val closeY = priceToY(c.close)
                        val highY = priceToY(c.high)
                        val lowY = priceToY(c.low)

                        val isBull = c.isBullish
                        val candleColor = if (isBull) BullGreen else BearRed

                        // Draw Wick
                        drawLine(
                            color = candleColor,
                            start = Offset(centerX, highY),
                            end = Offset(centerX, lowY),
                            strokeWidth = max(1.2f, candleBodyWidth * 0.15f)
                        )

                        // Draw Body
                        val bodyTop = min(openY, closeY)
                        val bodyBottom = max(openY, closeY)
                        val bodyHeight = max(2f, bodyBottom - bodyTop)

                        drawRoundRect(
                            color = candleColor,
                            topLeft = Offset(centerX - (candleBodyWidth / 2f), bodyTop),
                            size = Size(candleBodyWidth, bodyHeight),
                            cornerRadius = CornerRadius(1.5f, 1.5f)
                        )

                        // Draw Volume Histogram Bar
                        val volHeight = ((c.volume / maxVolume) * volumeAreaHeight).toFloat()
                        val volTop = totalHeight - volHeight
                        val volColor = if (isBull) BullGreen.copy(alpha = 0.35f) else BearRed.copy(alpha = 0.35f)

                        drawRect(
                            color = volColor,
                            topLeft = Offset(centerX - (candleBodyWidth / 2f), volTop),
                            size = Size(candleBodyWidth, volHeight)
                        )
                    }

                    // Draw Execution Signal Markers
                    if (showSignals) {
                        for (sig in visibleSignals) {
                            val relIdx = sig.barIndex - startIdx
                            if (relIdx !in 0 until barCount) continue
                            val sigCandle = visibleCandles[relIdx]
                            val centerX = (relIdx * candleSpacing) + (candleSpacing / 2f)

                            if (sig.isEntry) {
                                if (sig.direction == TradeDirection.LONG) {
                                    // BUY Marker (Up Arrow below candle Low)
                                    val y = priceToY(sigCandle.low) + 16.dp.toPx()
                                    drawSignalArrow(centerX, y, isUp = true, color = BullGreen)
                                } else {
                                    // SHORT Marker (Down Arrow above candle High)
                                    val y = priceToY(sigCandle.high) - 16.dp.toPx()
                                    drawSignalArrow(centerX, y, isUp = false, color = BearRed)
                                }
                            } else {
                                // Exit Marker
                                val y = if (sig.direction == TradeDirection.LONG) {
                                    priceToY(sigCandle.high) - 14.dp.toPx()
                                } else {
                                    priceToY(sigCandle.low) + 14.dp.toPx()
                                }
                                drawCircle(
                                    color = if (sig.exitReason == ExitReason.TAKE_PROFIT) BullGreen else BearRed,
                                    radius = 4.5.dp.toPx(),
                                    center = Offset(centerX, y)
                                )
                            }
                        }
                    }

                    // Highlight selected bar crosshair
                    selectedBarIndex?.let { sel ->
                        val relIdx = sel - startIdx
                        if (relIdx in 0 until barCount) {
                            val centerX = (relIdx * candleSpacing) + (candleSpacing / 2f)
                            drawLine(
                                color = CyanAccent.copy(alpha = 0.7f),
                                start = Offset(centerX, 0f),
                                end = Offset(centerX, totalHeight),
                                strokeWidth = 1.5f,
                                pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f, 10f))
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun DrawScope.drawSignalArrow(x: Float, y: Float, isUp: Boolean, color: Color) {
    val size = 7.dp.toPx()
    val path = Path()
    if (isUp) {
        // Points upwards (BUY)
        path.moveTo(x, y - size)
        path.lineTo(x - size, y + size)
        path.lineTo(x + size, y + size)
        path.close()
    } else {
        // Points downwards (SELL/SHORT)
        path.moveTo(x, y + size)
        path.lineTo(x - size, y - size)
        path.lineTo(x + size, y - size)
        path.close()
    }
    drawPath(path, color)
}

@Composable
private fun LegendPill(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(color, CircleShape)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(text = label, style = TextStyle(fontSize = 10.sp, color = TextSecondary))
    }
}
