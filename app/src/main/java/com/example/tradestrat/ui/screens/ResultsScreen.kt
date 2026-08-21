package com.example.tradestrat.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import com.example.tradestrat.ui.components.EquityCurveChart
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

    var selectedTradeFilter by remember { mutableStateOf(0) } // 0: All, 1: Wins, 2: Losses
    var showSaveSnackbar by remember { mutableStateOf(false) }

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
            else -> result.trades
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(theme.background)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 96.dp)
    ) {
        // Header
        item {
            Column(modifier = Modifier.padding(top = 4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Backtest Analytics",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = theme.textPrimary,
                            fontSize = 24.sp
                        )
                        Text(
                            text = "${result.asset.symbol} • ${result.timeframe.label} • ${result.strategy.name}",
                            style = MaterialTheme.typography.bodySmall,
                            color = theme.textSecondary,
                            fontSize = 12.sp
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilledTonalIconButton(
                            onClick = {
                                viewModel.saveCurrentBacktest()
                                showSaveSnackbar = true
                            },
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = theme.surfaceElevated,
                                contentColor = theme.brandPrimary
                            ),
                            modifier = Modifier.testTag("save_backtest_result_btn")
                        ) {
                            Icon(Icons.Default.BookmarkBorder, contentDescription = "Save to History")
                        }
                    }
                }
            }
        }

        // Hero Performance Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("results_hero_card"),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = theme.surface),
                border = CardDefaults.outlinedCardBorder().copy(
                    brush = androidx.compose.ui.graphics.SolidColor(
                        if (isProfitable) theme.tradeGreen.copy(alpha = 0.5f) else theme.tradeRed.copy(alpha = 0.5f)
                    )
                )
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "NET PERFORMANCE",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = theme.textMuted,
                            letterSpacing = 1.sp
                        )

                        Surface(
                            shape = CircleShape,
                            color = if (isProfitable) theme.tradeGreenContainer else theme.tradeRedContainer
                        ) {
                            Text(
                                text = String.format(Locale.US, "%+.2f%% ROI", metrics.netProfitPercent),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isProfitable) theme.tradeGreenText else theme.tradeRedText,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Column {
                            Text(
                                text = String.format(Locale.US, "%s$%,.2f", if (metrics.netProfitDollars >= 0) "+" else "-", kotlin.math.abs(metrics.netProfitDollars)),
                                fontSize = 32.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isProfitable) theme.tradeGreen else theme.tradeRed
                            )
                            Text(
                                text = "Final Equity: $${String.format(Locale.US, "%,.2f", metrics.finalEquity)} (from $${String.format(Locale.US, "%,.0f", metrics.initialCapital)})",
                                fontSize = 12.sp,
                                color = theme.textSecondary
                            )
                        }
                    }

                    Divider(color = theme.borderSubtle)

                    // 4 Quick Stats
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        QuickStatItem("Win Rate", String.format(Locale.US, "%.1f%%", metrics.winRatePercent), theme.textPrimary, theme)
                        QuickStatItem("Profit Factor", if (metrics.profitFactor.isInfinite()) "∞" else String.format(Locale.US, "%.2f", metrics.profitFactor), if (metrics.profitFactor >= 1.4) theme.tradeGreen else theme.textPrimary, theme)
                        QuickStatItem("Max DD", String.format(Locale.US, "%.1f%%", metrics.maxDrawdownPercent), theme.tradeRed, theme)
                        QuickStatItem("Sharpe", String.format(Locale.US, "%.2f", metrics.sharpeRatio), if (metrics.sharpeRatio >= 1.0) theme.brandPrimary else theme.textPrimary, theme)
                    }
                }
            }
        }

        // Equity Curve Chart Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = theme.surface),
                border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(theme.border))
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Equity Growth & Benchmark Trajectory",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = theme.textPrimary
                    )

                    EquityCurveChart(
                        equityCurve = result.equityCurve,
                        initialCapital = metrics.initialCapital
                    )
                }
            }
        }

        // Comprehensive Metrics Breakdown
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = theme.surface),
                border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(theme.border))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Risk & Return Analytics",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = theme.textPrimary
                    )

                    MetricRow("Benchmark Buy & Hold ROI", String.format(Locale.US, "%+.2f%%", metrics.benchmarkReturnPercent), theme.textPrimary, theme)
                    MetricRow("Strategy Alpha", String.format(Locale.US, "%+.2f%%", metrics.alphaPercent), if (metrics.alphaPercent >= 0) theme.tradeGreen else theme.tradeRed, theme)
                    MetricRow("CAGR (Annualized Return)", String.format(Locale.US, "%+.2f%%", metrics.cagrPercent), theme.textPrimary, theme)
                    Divider(color = theme.borderSubtle)
                    MetricRow("Sortino Ratio", String.format(Locale.US, "%.2f", metrics.sortinoRatio), theme.textPrimary, theme)
                    MetricRow("Calmar Ratio", String.format(Locale.US, "%.2f", metrics.calmarRatio), theme.textPrimary, theme)
                    MetricRow("Payoff Ratio (Avg Win / Avg Loss)", String.format(Locale.US, "%.2f", metrics.payoffRatio), theme.textPrimary, theme)
                    Divider(color = theme.borderSubtle)
                    MetricRow("Total Executed Trades", "${metrics.totalTrades}", theme.textPrimary, theme)
                    MetricRow("Winning Trades", "${metrics.winningTrades} (${String.format(Locale.US, "%.1f%%", metrics.winRatePercent)})", theme.tradeGreen, theme)
                    MetricRow("Losing Trades", "${metrics.losingTrades}", theme.tradeRed, theme)
                    MetricRow("Avg Win / Trade", String.format(Locale.US, "%+.2f%%", metrics.avgWinningTradePercent), theme.tradeGreen, theme)
                    MetricRow("Avg Loss / Trade", String.format(Locale.US, "%.2f%%", metrics.avgLosingTradePercent), theme.tradeRed, theme)
                }
            }
        }

        // Trade Log Section Header & Filter Tabs
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Trade Execution Journal (${filteredTrades.size})",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = theme.textPrimary
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("All (${result.trades.size})", "Wins (${metrics.winningTrades})", "Losses (${metrics.losingTrades})").forEachIndexed { index, label ->
                        val isSelected = selectedTradeFilter == index
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { selectedTradeFilter = index }
                                .testTag("trade_filter_$index"),
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) theme.brandPrimary else theme.surfaceElevated
                        ) {
                            Box(modifier = Modifier.padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                                Text(
                                    text = label,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) Color.White else theme.textSecondary
                                )
                            }
                        }
                    }
                }
            }
        }

        // Individual Trades List
        if (filteredTrades.isEmpty()) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = theme.surfaceElevated
                ) {
                    Box(modifier = Modifier.padding(24.dp), contentAlignment = Alignment.Center) {
                        Text("No trades matching this filter", color = theme.textMuted, fontSize = 13.sp)
                    }
                }
            }
        } else {
            items(filteredTrades) { trade ->
                TradeItemCard(trade = trade, theme = theme)
            }
        }
    }
}

@Composable
private fun QuickStatItem(
    label: String,
    value: String,
    valueColor: Color,
    theme: com.example.ui.theme.AppThemeColors
) {
    Column {
        Text(text = label, fontSize = 11.sp, color = theme.textMuted)
        Text(text = value, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = valueColor)
    }
}

@Composable
private fun MetricRow(
    label: String,
    value: String,
    valueColor: Color,
    theme: com.example.ui.theme.AppThemeColors
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, fontSize = 13.sp, color = theme.textSecondary)
        Text(text = value, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = valueColor)
    }
}

@Composable
private fun TradeItemCard(
    trade: Trade,
    theme: com.example.ui.theme.AppThemeColors
) {
    val isWin = trade.isWin
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = theme.surface,
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(theme.border))
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (trade.direction == TradeDirection.LONG) theme.tradeGreenContainer else theme.tradeRedContainer
                    ) {
                        Text(
                            text = trade.direction.name,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (trade.direction == TradeDirection.LONG) theme.tradeGreenText else theme.tradeRedText,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    Text(
                        text = "#${trade.id.takeLast(4)}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = theme.textPrimary
                    )
                }

                Text(
                    text = String.format(Locale.US, "%s$%,.2f (%+.2f%%)", if (trade.pnlDollars >= 0) "+" else "-", kotlin.math.abs(trade.pnlDollars), trade.pnlPercent),
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = if (isWin) theme.tradeGreen else theme.tradeRed
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Entry: $${String.format(Locale.US, "%,.2f", trade.entryPrice)} → Exit: $${String.format(Locale.US, "%,.2f", trade.exitPrice)}",
                    fontSize = 11.sp,
                    color = theme.textSecondary
                )
                Text(
                    text = trade.exitReason.name.replace("_", " "),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = theme.textMuted
                )
            }
        }
    }
}
