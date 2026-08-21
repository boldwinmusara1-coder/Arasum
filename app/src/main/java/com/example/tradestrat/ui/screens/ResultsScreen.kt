package com.example.tradestrat.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tradestrat.model.*
import com.example.tradestrat.ui.BacktestViewModel
import com.example.tradestrat.ui.components.*
import com.example.ui.theme.LocalAppTheme
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultsScreen(
    viewModel: BacktestViewModel,
    modifier: Modifier = Modifier,
    onNavigateToBacktest: () -> Unit = {}
) {
    val theme = LocalAppTheme.current
    val currentResult by viewModel.currentResult.collectAsState()
    val isBacktesting by viewModel.isBacktesting.collectAsState()
    val sessionAnalytics by viewModel.sessionAnalytics.collectAsState()
    val maeMfeDistribution by viewModel.maeMfeDistribution.collectAsState()
    val selectedTradeForDetail by viewModel.selectedTradeForDetail.collectAsState()

    var selectedTradeFilter by remember { mutableStateOf(0) } // 0: All, 1: Wins, 2: Losses, 3: Longs, 4: Shorts
    var showUnderwaterDrawdown by remember { mutableStateOf(false) }

    val result = currentResult

    if (result == null) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(theme.background)
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = theme.surfaceElevated,
                    modifier = Modifier.size(72.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.QueryStats,
                            contentDescription = null,
                            tint = theme.brandPrimary,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }
                Text(
                    text = "No Backtest Results Yet",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = theme.textPrimary
                )
                Text(
                    text = "Configure your parameters and run a simulation to see quantitative analytics and equity curves.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = theme.textSecondary,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Button(
                    onClick = onNavigateToBacktest,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = theme.brandPrimary)
                ) {
                    Text("Go to Backtest Setup", fontWeight = FontWeight.Bold)
                }
            }
        }
        return
    }

    val metrics = result.metrics
    val isProfitable = metrics.netProfitDollars >= 0.0

    val filteredTrades = remember(result.trades, selectedTradeFilter) {
        when (selectedTradeFilter) {
            1 -> result.trades.filter { it.isWin }
            2 -> result.trades.filter { !it.isWin }
            3 -> result.trades.filter { it.direction == TradeDirection.LONG }
            4 -> result.trades.filter { it.direction == TradeDirection.SHORT }
            else -> result.trades
        }
    }

    // Trade Detail Sheet
    if (selectedTradeForDetail != null) {
        TradeDetailSheet(
            trade = selectedTradeForDetail!!,
            strategy = result.strategy,
            asset = result.asset,
            onAddToJournal = { notes, tags ->
                viewModel.createJournalEntryFromTrade(selectedTradeForDetail!!, notes, tags)
            },
            onDismiss = { viewModel.selectTradeForDetail(null) }
        )
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(theme.background)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 96.dp)
    ) {
        // TOP: Strategy, Market, Timeframe, Date Range
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = theme.surface),
                border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(theme.borderSubtle))
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = theme.brandPrimary.copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = result.strategy.strategyType.name,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = theme.brandPrimary,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                            Text(
                                text = result.strategy.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = theme.textPrimary
                            )
                        }

                        IconButton(onClick = { viewModel.saveCurrentBacktest() }) {
                            Icon(imageVector = Icons.Default.BookmarkAdd, contentDescription = "Save", tint = theme.brandPrimary)
                        }
                    }

                    Text(
                        text = "${result.asset.symbol} • ${result.timeframe.label} • ${result.dataSource.startDate} → ${result.dataSource.endDate}",
                        fontSize = 11.sp,
                        color = theme.textSecondary
                    )
                }
            }
        }

        // PRIMARY METRICS: Net P&L & ROI
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = theme.surface),
                border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(theme.borderSubtle))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("NET P&L (USD)", fontSize = 10.sp, color = theme.textMuted, fontWeight = FontWeight.SemiBold)
                        Text(
                            text = String.format(Locale.US, "%+.2f", metrics.netProfitDollars),
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isProfitable) theme.accentGreen else theme.accentRed
                        )
                        Text(
                            text = "Initial: $${metrics.initialCapital.toInt()} → Final: $${metrics.finalEquity.toInt()}",
                            fontSize = 10.sp,
                            color = theme.textSecondary
                        )
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text("RETURN ON INVESTMENT", fontSize = 10.sp, color = theme.textMuted, fontWeight = FontWeight.SemiBold)
                        Text(
                            text = String.format(Locale.US, "%+.2f%%", metrics.netProfitPercent),
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isProfitable) theme.accentGreen else theme.accentRed
                        )
                        Text(
                            text = "Benchmark: ${String.format(Locale.US, "%+.1f%%", metrics.benchmarkReturnPercent)}",
                            fontSize = 10.sp,
                            color = theme.textSecondary
                        )
                    }
                }
            }
        }

        // SECONDARY METRICS: Win Rate, Profit Factor, Expectancy, Max Drawdown, Trades, Avg R
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricBox("Win Rate", String.format(Locale.US, "%.1f%%", metrics.winRatePercent), "${metrics.winningTrades}W / ${metrics.losingTrades}L", Modifier.weight(1f), theme)
                MetricBox("Profit Factor", String.format(Locale.US, "%.2f", metrics.profitFactor), "Payoff: ${String.format(Locale.US, "%.2f", metrics.payoffRatio)}", Modifier.weight(1f), theme)
                MetricBox("Max Drawdown", String.format(Locale.US, "%.1f%%", metrics.maxDrawdownPercent), "${metrics.maxDrawdownDurationBars} bars", Modifier.weight(1f), theme, valueColor = theme.accentRed)
            }
        }

        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricBox("Expectancy", String.format(Locale.US, "$%.2f", metrics.expectancyDollars), "${String.format(Locale.US, "%+.2f R", metrics.expectancyR)}", Modifier.weight(1f), theme)
                MetricBox("Total Trades", metrics.totalTrades.toString(), "Avg Hold: ${metrics.avgHoldingBars.toInt()}b", Modifier.weight(1f), theme)
                MetricBox("Average R", String.format(Locale.US, "%+.2f R", metrics.avgRMultiple), "Fees: $${metrics.totalFeesPaid.toInt()}", Modifier.weight(1f), theme)
            }
        }

        // ADDITIONAL INSTITUTIONAL METRICS: Sharpe, Sortino, CAGR, Calmar, Alpha
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = theme.surface),
                border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(theme.borderSubtle))
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "INSTITUTIONAL RISK-ADJUSTED METRICS",
                        style = MaterialTheme.typography.labelSmall,
                        color = theme.brandPrimary,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        InstMetric("Sharpe", String.format(Locale.US, "%.2f", metrics.sharpeRatio), theme)
                        InstMetric("Sortino", String.format(Locale.US, "%.2f", metrics.sortinoRatio), theme)
                        InstMetric("CAGR", String.format(Locale.US, "%.1f%%", metrics.cagrPercent), theme)
                        InstMetric("Calmar", String.format(Locale.US, "%.2f", metrics.calmarRatio), theme)
                        InstMetric("Alpha", String.format(Locale.US, "%+.1f%%", metrics.alphaPercent), theme)
                    }
                }
            }
        }

        // CANVAS EQUITY CURVE
        item {
            EquityCurveChart(
                equityCurve = result.equityCurve,
                initialCapital = metrics.initialCapital,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // PERFORMANCE BREAKDOWN (Long vs Short & Global Sessions)
        item {
            PerformanceBreakdownCard(
                trades = result.trades,
                metrics = metrics,
                sessionAnalytics = sessionAnalytics
            )
        }

        // EXCURSION ANALYTICS (MAE & MFE)
        maeMfeDistribution?.let { dist ->
            item {
                MaeMfeCard(distribution = dist)
            }
        }

        // TEMPORAL EDGE HEATMAP
        item {
            TradingHeatmapsCard(trades = result.trades)
        }

        // DATA MANAGEMENT SUMMARY CARD
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = theme.surface),
                border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(theme.borderSubtle))
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(imageVector = Icons.Default.Storage, contentDescription = "Data", tint = theme.brandPrimary, modifier = Modifier.size(18.dp))
                            Text(
                                text = "Data Source & Integrity",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = theme.textPrimary
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = theme.accentGreen.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = "LOCAL CACHE & REMOTE SYNC",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = theme.accentGreen,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text("PROVIDER", fontSize = 9.sp, color = theme.textMuted, fontWeight = FontWeight.SemiBold)
                            Text(result.dataSource.provider, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = theme.textPrimary)
                        }
                        Column {
                            Text("CANDLES", fontSize = 9.sp, color = theme.textMuted, fontWeight = FontWeight.SemiBold)
                            Text("${result.dataSource.candleCount} bars", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = theme.textPrimary)
                        }
                        Column {
                            Text("EXECUTION", fontSize = 9.sp, color = theme.textMuted, fontWeight = FontWeight.SemiBold)
                            Text(result.riskParams.intrabarExecution.label, fontSize = 11.sp, color = theme.textSecondary)
                        }
                    }

                    Button(
                        onClick = { viewModel.runBacktest() },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = theme.surfaceElevated)
                    ) {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = "Refresh", tint = theme.brandPrimary, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Refresh & Re-validate Dataset", fontSize = 12.sp, color = theme.brandPrimary, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // TRADE EXECUTION LOG (Click to inspect trade detail)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = theme.surface),
                border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(theme.borderSubtle))
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Trade Execution Log (${filteredTrades.size})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = theme.textPrimary
                        )
                        Text("Tap trade to inspect", fontSize = 11.sp, color = theme.textMuted)
                    }

                    // Filter row
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        val filters = listOf("All", "Wins", "Losses", "Longs", "Shorts")
                        items(filters.size) { idx ->
                            FilterChip(
                                selected = selectedTradeFilter == idx,
                                onClick = { selectedTradeFilter = idx },
                                label = { Text(filters[idx], fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = theme.brandPrimary.copy(alpha = 0.2f),
                                    selectedLabelColor = theme.brandPrimary
                                )
                            )
                        }
                    }

                    // Trades
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        filteredTrades.take(50).forEachIndexed { idx, trade ->
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.selectTradeForDetail(trade) },
                                shape = RoundedCornerShape(10.dp),
                                color = theme.surfaceElevated,
                                border = androidx.compose.foundation.BorderStroke(1.dp, theme.borderSubtle)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text("#${idx + 1}", fontSize = 11.sp, color = theme.textMuted, fontWeight = FontWeight.Bold)
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = if (trade.direction == TradeDirection.LONG) theme.accentGreen.copy(alpha = 0.15f) else theme.accentRed.copy(alpha = 0.15f)
                                        ) {
                                            Text(
                                                text = trade.direction.name,
                                                color = if (trade.direction == TradeDirection.LONG) theme.accentGreen else theme.accentRed,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 10.sp,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                            )
                                        }

                                        Column {
                                            Text(
                                                text = trade.entryReason ?: trade.exitReason.label,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = theme.textPrimary
                                            )
                                            Text(
                                                text = "Entry: $${String.format(Locale.US, "%.4f", trade.entryPrice)} → $${String.format(Locale.US, "%.4f", trade.exitPrice)}",
                                                fontSize = 10.sp,
                                                color = theme.textSecondary
                                            )
                                        }
                                    }

                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            text = String.format(Locale.US, "%+.2f%%", trade.pnlPercent),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = if (trade.isWin) theme.accentGreen else theme.accentRed
                                        )
                                        Text(
                                            text = String.format(Locale.US, "%+.2f R", trade.rMultiple),
                                            fontSize = 10.sp,
                                            color = if (trade.isWin) theme.accentGreen else theme.accentRed
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricBox(
    label: String,
    value: String,
    subValue: String,
    modifier: Modifier = Modifier,
    theme: com.example.ui.theme.AppColors,
    valueColor: Color = theme.textPrimary
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = theme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, theme.borderSubtle)
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(text = label, fontSize = 9.sp, color = theme.textMuted, fontWeight = FontWeight.SemiBold)
            Text(text = value, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = valueColor)
            Text(text = subValue, fontSize = 9.sp, color = theme.textSecondary)
        }
    }
}

@Composable
private fun InstMetric(
    label: String,
    value: String,
    theme: com.example.ui.theme.AppColors
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, fontSize = 9.sp, color = theme.textMuted, fontWeight = FontWeight.SemiBold)
        Text(text = value, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = theme.textPrimary)
    }
}
