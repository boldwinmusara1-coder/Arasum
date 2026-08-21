package com.example.tradestrat.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tradestrat.data.MarketDataProvider
import com.example.tradestrat.model.*
import com.example.tradestrat.ui.BacktestViewModel
import com.example.tradestrat.ui.components.EquityCurveChart
import com.example.ui.theme.LocalAppTheme
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: BacktestViewModel,
    modifier: Modifier = Modifier,
    onNavigateToBacktest: () -> Unit = {},
    onNavigateToStrategies: () -> Unit = {},
    onNavigateToSmcIct: () -> Unit = {},
    onNavigateToResults: () -> Unit = {}
) {
    val theme = LocalAppTheme.current
    val selectedAsset by viewModel.selectedAsset.collectAsState()
    val selectedTimeframe by viewModel.selectedTimeframe.collectAsState()
    val selectedStrategy by viewModel.selectedStrategy.collectAsState()
    val currentResult by viewModel.currentResult.collectAsState()
    val isBacktesting by viewModel.isBacktesting.collectAsState()
    val savedBacktests by viewModel.savedBacktests.collectAsState()
    val dataFetchError by viewModel.dataFetchError.collectAsState()

    val result = currentResult
    val isProfitable = (result?.metrics?.netProfitDollars ?: 0.0) >= 0.0

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(theme.background)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 32.dp)
    ) {
        // App Header & Branding
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Arasum Trading",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = theme.textPrimary,
                        fontSize = 24.sp
                    )
                    Text(
                        text = "Quantitative Backtesting & Strategy Studio",
                        style = MaterialTheme.typography.bodySmall,
                        color = theme.textSecondary,
                        fontSize = 12.sp
                    )
                }

                // Status Indicator
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = if (isBacktesting) theme.brandPrimaryContainer else theme.surfaceElevated,
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(theme.border))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(
                                    if (isBacktesting) theme.brandPrimary else theme.tradeGreen,
                                    CircleShape
                                )
                        )
                        Text(
                            text = if (isBacktesting) "Testing..." else "Ready",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isBacktesting) theme.brandPrimaryText else theme.textPrimary
                        )
                    }
                }
            }
        }

        // Error message banner if any
        if (dataFetchError != null) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth().testTag("dashboard_error_card"),
                    shape = RoundedCornerShape(14.dp),
                    color = theme.tradeRedContainer.copy(alpha = 0.8f),
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(theme.tradeRed))
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Default.CloudOff, contentDescription = "Error", tint = theme.tradeRedText)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Market Data Notice",
                                fontWeight = FontWeight.Bold,
                                color = theme.tradeRedText,
                                fontSize = 13.sp
                            )
                            Text(
                                text = "We couldn't load the latest market data. Check your connection and try again.",
                                color = theme.tradeRedText,
                                fontSize = 12.sp
                            )
                        }
                        TextButton(
                            onClick = { viewModel.runBacktest() },
                            colors = ButtonDefaults.textButtonColors(contentColor = theme.tradeRedText)
                        ) {
                            Text("Retry", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Active Backtest Hero Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("dashboard_hero_card"),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = theme.surface),
                border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(theme.border))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Strategy & Asset Badge Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = theme.brandPrimaryContainer
                            ) {
                                Text(
                                    text = selectedAsset.symbol,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = theme.brandPrimaryText,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = theme.surfaceElevated
                            ) {
                                Text(
                                    text = selectedTimeframe.label,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = theme.textSecondary,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Surface(
                            shape = CircleShape,
                            color = if (isProfitable) theme.tradeGreenContainer else theme.tradeRedContainer
                        ) {
                            Text(
                                text = if (result != null) String.format(Locale.US, "%+.2f%% ROI", result.metrics.netProfitPercent) else "NO DATA",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isProfitable) theme.tradeGreenText else theme.tradeRedText,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }

                    // Main Net P&L Display
                    Column {
                        Text(
                            text = "Net Profit & Loss",
                            style = MaterialTheme.typography.labelMedium,
                            color = theme.textSecondary
                        )
                        Text(
                            text = if (result != null) String.format(Locale.US, "%s$%,.2f", if (result.metrics.netProfitDollars >= 0) "+" else "-", kotlin.math.abs(result.metrics.netProfitDollars)) else "$0.00",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isProfitable) theme.tradeGreen else theme.tradeRed,
                            fontSize = 30.sp
                        )
                        Text(
                            text = "Strategy: ${selectedStrategy.name}",
                            style = MaterialTheme.typography.bodySmall,
                            color = theme.textMuted,
                            fontSize = 12.sp
                        )
                    }

                    Divider(color = theme.borderSubtle, thickness = 1.dp)

                    // Key Summary Metrics Grid (Win Rate, Profit Factor, Max Drawdown, Total Trades)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        DashboardMetricItem(
                            label = "Win Rate",
                            value = if (result != null) String.format(Locale.US, "%.1f%%", result.metrics.winRatePercent) else "--",
                            color = theme.textPrimary,
                            theme = theme
                        )
                        DashboardMetricItem(
                            label = "Profit Factor",
                            value = if (result != null) {
                                if (result.metrics.profitFactor.isInfinite()) "∞" else String.format(Locale.US, "%.2f", result.metrics.profitFactor)
                            } else "--",
                            color = if ((result?.metrics?.profitFactor ?: 0.0) >= 1.5) theme.tradeGreen else theme.textPrimary,
                            theme = theme
                        )
                        DashboardMetricItem(
                            label = "Max Drawdown",
                            value = if (result != null) String.format(Locale.US, "%.1f%%", result.metrics.maxDrawdownPercent) else "--",
                            color = theme.tradeRed,
                            theme = theme
                        )
                        DashboardMetricItem(
                            label = "Trades",
                            value = if (result != null) "${result.trades.size}" else "0",
                            color = theme.textPrimary,
                            theme = theme
                        )
                    }

                    // Action Buttons Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = onNavigateToBacktest,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("dashboard_run_new_backtest_button"),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = theme.brandPrimary,
                                contentColor = Color.White
                            )
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = "Run", modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Run New Backtest", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }

                        OutlinedButton(
                            onClick = onNavigateToResults,
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("dashboard_view_results_button"),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = theme.textPrimary),
                            border = ButtonDefaults.outlinedButtonBorder.copy(brush = androidx.compose.ui.graphics.SolidColor(theme.border))
                        ) {
                            Icon(Icons.Default.Analytics, contentDescription = "Results", modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Details", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        }
                    }
                }
            }
        }

        // Quick Launch Hub
        item {
            Text(
                text = "Quick Launch Hub",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = theme.textPrimary,
                fontSize = 16.sp
            )
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                QuickHubCard(
                    title = "Strategy Lab",
                    subtitle = "Presets & Optimizer",
                    icon = Icons.Default.Tune,
                    iconTint = theme.brandPrimary,
                    containerColor = theme.surface,
                    modifier = Modifier.weight(1f),
                    theme = theme,
                    onClick = onNavigateToStrategies
                )
                QuickHubCard(
                    title = "SMC / ICT Suite",
                    subtitle = "Order Blocks & FVGs",
                    icon = Icons.Default.Psychology,
                    iconTint = theme.tradePurple,
                    containerColor = theme.surface,
                    modifier = Modifier.weight(1f),
                    theme = theme,
                    onClick = onNavigateToSmcIct
                )
            }
        }

        // Quick Market Switcher
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Quick Instrument Switch",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = theme.textPrimary,
                        fontSize = 15.sp
                    )
                    Text(
                        text = "Tap to load",
                        fontSize = 11.sp,
                        color = theme.textMuted
                    )
                }

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 2.dp)
                ) {
                    items(MarketDataProvider.ASSETS.take(6)) { asset ->
                        val isSelected = selectedAsset.id == asset.id
                        Surface(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { viewModel.setAsset(asset) }
                                .testTag("dashboard_asset_${asset.symbol.replace('/', '_')}"),
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) theme.brandPrimaryContainer else theme.surface,
                            border = CardDefaults.outlinedCardBorder().copy(
                                brush = androidx.compose.ui.graphics.SolidColor(if (isSelected) theme.brandPrimary else theme.border)
                            )
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                                Text(
                                    text = asset.symbol,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) theme.brandPrimaryText else theme.textPrimary
                                )
                                Text(
                                    text = asset.category.name,
                                    fontSize = 10.sp,
                                    color = theme.textSecondary
                                )
                            }
                        }
                    }
                }
            }
        }

        // Quick Timeframe Switcher (Including 5m, 15m, 30m, 1h, 4h, 1D)
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Timeframe Selection",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = theme.textPrimary,
                    fontSize = 15.sp
                )

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(listOf(Timeframe.M5, Timeframe.M15, Timeframe.M30, Timeframe.H1, Timeframe.H4, Timeframe.D1)) { tf ->
                        val isSelected = selectedTimeframe == tf
                        Surface(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { viewModel.setTimeframe(tf) }
                                .testTag("dashboard_tf_${tf.name.lowercase()}"),
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) theme.brandPrimary else theme.surface,
                            border = CardDefaults.outlinedCardBorder().copy(
                                brush = androidx.compose.ui.graphics.SolidColor(if (isSelected) theme.brandPrimary else theme.border)
                            )
                        ) {
                            Text(
                                text = tf.label,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) Color.White else theme.textPrimary,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
            }
        }

        // Portfolio Equity Preview (if result exists)
        if (result != null && result.equityCurve.isNotEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = theme.surface),
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(theme.border))
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Default.ShowChart, contentDescription = null, tint = theme.brandPrimary, modifier = Modifier.size(18.dp))
                                Text(
                                    text = "Equity Trajectory",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = theme.textPrimary
                                )
                            }
                            TextButton(onClick = onNavigateToResults) {
                                Text("Full Analytics", fontSize = 12.sp, color = theme.brandPrimary)
                            }
                        }

                        EquityCurveChart(
                            equityCurve = result.equityCurve,
                            modifier = Modifier.height(200.dp),
                            initialCapital = viewModel.riskParameters.value.initialCapital
                        )
                    }
                }
            }
        }

        // Recent Saved Backtests Section
        if (savedBacktests.isNotEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Recent Saved Backtests",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = theme.textPrimary,
                        fontSize = 15.sp
                    )

                    savedBacktests.take(3).forEach { saved ->
                        val winRateVal = saved.winRatePercent
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            color = theme.surface,
                            border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(theme.border))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "${saved.assetSymbol} • ${saved.strategyName}",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = theme.textPrimary
                                    )
                                    Text(
                                        text = "${saved.timeframe} • ${saved.totalTrades} Trades",
                                        fontSize = 11.sp,
                                        color = theme.textSecondary
                                    )
                                }

                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = String.format(Locale.US, "%s%.2f%%", if (saved.netProfitPercent >= 0) "+" else "-", kotlin.math.abs(saved.netProfitPercent)),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = if (saved.netProfitPercent >= 0) theme.tradeGreen else theme.tradeRed
                                    )
                                    Text(
                                        text = String.format(Locale.US, "%.1f%% Win", winRateVal),
                                        fontSize = 11.sp,
                                        color = theme.textSecondary
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

@Composable
private fun DashboardMetricItem(
    label: String,
    value: String,
    color: Color,
    theme: com.example.ui.theme.AppThemeColors
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            fontSize = 11.sp,
            color = theme.textSecondary
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}

@Composable
private fun QuickHubCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    iconTint: Color,
    containerColor: Color,
    modifier: Modifier = Modifier,
    theme: com.example.ui.theme.AppThemeColors,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        color = containerColor,
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(theme.border))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(iconTint.copy(alpha = 0.15f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = icon, contentDescription = title, tint = iconTint, modifier = Modifier.size(20.dp))
            }
            Column {
                Text(text = title, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = theme.textPrimary)
                Text(text = subtitle, fontSize = 11.sp, color = theme.textSecondary)
            }
        }
    }
}
