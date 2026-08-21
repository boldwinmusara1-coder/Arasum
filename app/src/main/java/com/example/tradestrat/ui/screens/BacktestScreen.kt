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
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tradestrat.data.MarketDataProvider
import com.example.tradestrat.model.*
import com.example.tradestrat.ui.BacktestViewModel
import com.example.ui.theme.LocalAppTheme
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BacktestScreen(
    viewModel: BacktestViewModel,
    modifier: Modifier = Modifier,
    onBacktestComplete: () -> Unit = {}
) {
    val theme = LocalAppTheme.current
    val selectedAsset by viewModel.selectedAsset.collectAsState()
    val selectedTimeframe by viewModel.selectedTimeframe.collectAsState()
    val selectedStrategy by viewModel.selectedStrategy.collectAsState()
    val riskParameters by viewModel.riskParameters.collectAsState()
    val isBacktesting by viewModel.isBacktesting.collectAsState()
    val currentResult by viewModel.currentResult.collectAsState()

    var selectedCategory by remember { mutableStateOf<AssetCategory?>(null) }
    var showAdvancedSettings by remember { mutableStateOf(false) }

    // Date range preset state
    var selectedDateRangePreset by remember { mutableStateOf("1Y") }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(theme.background)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 96.dp)
    ) {
        // Section Header
        item {
            Column(modifier = Modifier.padding(top = 4.dp)) {
                Text(
                    text = "Backtest Studio",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = theme.textPrimary,
                    fontSize = 24.sp
                )
                Text(
                    text = "Configure parameters and run high-fidelity simulations",
                    style = MaterialTheme.typography.bodySmall,
                    color = theme.textSecondary,
                    fontSize = 12.sp
                )
            }
        }

        // STEP 1: MARKET SELECTION
        item {
            Card(
                modifier = Modifier.fillMaxWidth().testTag("config_market_card"),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = theme.surface),
                border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(theme.border))
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(
                                modifier = Modifier.size(24.dp).background(theme.brandPrimaryContainer, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("1", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = theme.brandPrimaryText)
                            }
                            Text(
                                text = "Market & Instrument",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = theme.textPrimary
                            )
                        }

                        Surface(shape = RoundedCornerShape(8.dp), color = theme.brandPrimaryContainer) {
                            Text(
                                text = selectedAsset.symbol,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = theme.brandPrimaryText,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    // Category filter chips
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        item {
                            FilterChip(
                                selected = selectedCategory == null,
                                onClick = { selectedCategory = null },
                                label = { Text("All", fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = theme.brandPrimary,
                                    selectedLabelColor = Color.White
                                )
                            )
                        }
                        items(AssetCategory.values()) { cat ->
                            FilterChip(
                                selected = selectedCategory == cat,
                                onClick = { selectedCategory = cat },
                                label = { Text(cat.name, fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = theme.brandPrimary,
                                    selectedLabelColor = Color.White
                                )
                            )
                        }
                    }

                    // Asset Horizontal Picker
                    val filteredAssets = MarketDataProvider.ASSETS.filter {
                        selectedCategory == null || it.category == selectedCategory
                    }

                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(filteredAssets) { asset ->
                            val isSelected = selectedAsset.id == asset.id
                            Surface(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { viewModel.setAsset(asset) }
                                    .testTag("asset_item_${asset.symbol.replace('/', '_')}"),
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) theme.brandPrimaryContainer else theme.surfaceElevated,
                                border = CardDefaults.outlinedCardBorder().copy(
                                    brush = androidx.compose.ui.graphics.SolidColor(if (isSelected) theme.brandPrimary else theme.border)
                                )
                            ) {
                                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                                    Text(
                                        text = asset.symbol,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = if (isSelected) theme.brandPrimaryText else theme.textPrimary
                                    )
                                    Text(
                                        text = asset.name,
                                        fontSize = 10.sp,
                                        color = theme.textSecondary,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // STEP 2: TIMEFRAME SELECTION (Includes 5m, 15m, 30m, 1h, 4h, 1D, 1W)
        item {
            Card(
                modifier = Modifier.fillMaxWidth().testTag("config_timeframe_card"),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = theme.surface),
                border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(theme.border))
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(
                                modifier = Modifier.size(24.dp).background(theme.brandPrimaryContainer, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("2", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = theme.brandPrimaryText)
                            }
                            Text(
                                text = "Timeframe",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = theme.textPrimary
                            )
                        }

                        Text(
                            text = selectedTimeframe.label,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = theme.brandPrimary
                        )
                    }

                    // Prominent Timeframe Pills (All standard plus 5m, 30m)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(Timeframe.M5, Timeframe.M15, Timeframe.M30, Timeframe.H1, Timeframe.H4, Timeframe.D1).forEach { tf ->
                            val isSelected = selectedTimeframe == tf
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { viewModel.setTimeframe(tf) }
                                    .testTag("timeframe_btn_${tf.name.lowercase()}"),
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) theme.brandPrimary else theme.surfaceElevated,
                                border = CardDefaults.outlinedCardBorder().copy(
                                    brush = androidx.compose.ui.graphics.SolidColor(if (isSelected) theme.brandPrimary else theme.border)
                                )
                            ) {
                                Box(
                                    modifier = Modifier.padding(vertical = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = tf.label,
                                        fontSize = 12.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) Color.White else theme.textPrimary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // STEP 3: STRATEGY SELECTION
        item {
            Card(
                modifier = Modifier.fillMaxWidth().testTag("config_strategy_card"),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = theme.surface),
                border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(theme.border))
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(
                                modifier = Modifier.size(24.dp).background(theme.brandPrimaryContainer, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("3", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = theme.brandPrimaryText)
                            }
                            Text(
                                text = "Strategy Engine",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = theme.textPrimary
                            )
                        }

                        Surface(shape = RoundedCornerShape(8.dp), color = theme.brandPrimaryContainer) {
                            Text(
                                text = selectedStrategy.strategyType.name,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = theme.brandPrimaryText,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                            )
                        }
                    }

                    // Strategy Presets List
                    StrategyDefinition.PRESETS.forEach { strategy ->
                        val isSelected = selectedStrategy.id == strategy.id
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { viewModel.setStrategy(strategy) }
                                .testTag("strategy_option_${strategy.id}"),
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) theme.brandPrimaryContainer.copy(alpha = 0.6f) else theme.surfaceElevated,
                            border = CardDefaults.outlinedCardBorder().copy(
                                brush = androidx.compose.ui.graphics.SolidColor(if (isSelected) theme.brandPrimary else theme.border)
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = strategy.name,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = if (isSelected) theme.brandPrimaryText else theme.textPrimary
                                    )
                                    Text(
                                        text = strategy.description,
                                        fontSize = 11.sp,
                                        color = theme.textSecondary,
                                        maxLines = 2
                                    )
                                }

                                RadioButton(
                                    selected = isSelected,
                                    onClick = { viewModel.setStrategy(strategy) },
                                    colors = RadioButtonDefaults.colors(selectedColor = theme.brandPrimary)
                                )
                            }
                        }
                    }
                }
            }
        }

        // STEP 4: DATE RANGE PRESETS
        item {
            Card(
                modifier = Modifier.fillMaxWidth().testTag("config_date_range_card"),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = theme.surface),
                border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(theme.border))
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(
                            modifier = Modifier.size(24.dp).background(theme.brandPrimaryContainer, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("4", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = theme.brandPrimaryText)
                        }
                        Text(
                            text = "Historical Date Window",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = theme.textPrimary
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("30D", "90D", "180D", "1Y", "2Y", "5Y").forEach { preset ->
                            val isSelected = selectedDateRangePreset == preset
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { selectedDateRangePreset = preset }
                                    .testTag("date_preset_${preset.lowercase()}"),
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) theme.brandPrimary else theme.surfaceElevated,
                                border = CardDefaults.outlinedCardBorder().copy(
                                    brush = androidx.compose.ui.graphics.SolidColor(if (isSelected) theme.brandPrimary else theme.border)
                                )
                            ) {
                                Box(
                                    modifier = Modifier.padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = preset,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) Color.White else theme.textPrimary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // STEP 5: CAPITAL & RISK PARAMETERS
        item {
            Card(
                modifier = Modifier.fillMaxWidth().testTag("config_risk_card"),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = theme.surface),
                border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(theme.border))
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(
                            modifier = Modifier.size(24.dp).background(theme.brandPrimaryContainer, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("5", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = theme.brandPrimaryText)
                        }
                        Text(
                            text = "Capital, Position Sizing & Exits",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = theme.textPrimary
                        )
                    }

                    // Initial Capital & Sizing
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = "${riskParameters.initialCapital.toInt()}",
                            onValueChange = { str ->
                                val v = str.toDoubleOrNull() ?: riskParameters.initialCapital
                                viewModel.updateRiskParameters(riskParameters.copy(initialCapital = v))
                            },
                            label = { Text("Initial Capital ($)", fontSize = 11.sp) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f).testTag("input_initial_capital"),
                            shape = RoundedCornerShape(12.dp)
                        )

                        OutlinedTextField(
                            value = "${riskParameters.positionSizeValue.toInt()}",
                            onValueChange = { str ->
                                val v = str.toDoubleOrNull() ?: riskParameters.positionSizeValue
                                viewModel.updateRiskParameters(riskParameters.copy(positionSizeValue = v))
                            },
                            label = { Text("Size / Trade ($)", fontSize = 11.sp) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f).testTag("input_position_size"),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }

                    // Leverage & Shorting
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Account Leverage: ${riskParameters.leverage.toInt()}x", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = theme.textPrimary)
                            Text("Margin Multiplier", fontSize = 10.sp, color = theme.textSecondary)
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(1.0, 2.0, 5.0, 10.0).forEach { lev ->
                                val isSelected = riskParameters.leverage == lev
                                Surface(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { viewModel.updateRiskParameters(riskParameters.copy(leverage = lev)) },
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isSelected) theme.brandPrimary else theme.surfaceElevated
                                ) {
                                    Text(
                                        text = "${lev.toInt()}x",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) Color.White else theme.textPrimary,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Stop Loss & Take Profit %
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = "${riskParameters.stopLossValue}",
                            onValueChange = { str ->
                                val v = str.toDoubleOrNull() ?: riskParameters.stopLossValue
                                viewModel.updateRiskParameters(riskParameters.copy(stopLossValue = v))
                            },
                            label = { Text("Stop Loss (%)", fontSize = 11.sp) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f).testTag("input_stop_loss"),
                            shape = RoundedCornerShape(12.dp)
                        )

                        OutlinedTextField(
                            value = "${riskParameters.takeProfitValue}",
                            onValueChange = { str ->
                                val v = str.toDoubleOrNull() ?: riskParameters.takeProfitValue
                                viewModel.updateRiskParameters(riskParameters.copy(takeProfitValue = v))
                            },
                            label = { Text("Take Profit (%)", fontSize = 11.sp) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f).testTag("input_take_profit"),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }

                    // Realistic Execution Settings (Slippage & Commissions)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = "${riskParameters.slippageBps.toInt()}",
                            onValueChange = { str ->
                                val v = str.toDoubleOrNull() ?: riskParameters.slippageBps
                                viewModel.updateRiskParameters(riskParameters.copy(slippageBps = v))
                            },
                            label = { Text("Slippage (bps)", fontSize = 11.sp) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f).testTag("input_slippage_bps"),
                            shape = RoundedCornerShape(12.dp)
                        )

                        OutlinedTextField(
                            value = "${riskParameters.commissionBps.toInt()}",
                            onValueChange = { str ->
                                val v = str.toDoubleOrNull() ?: riskParameters.commissionBps
                                viewModel.updateRiskParameters(riskParameters.copy(commissionBps = v))
                            },
                            label = { Text("Commission (bps)", fontSize = 11.sp) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f).testTag("input_commission_bps"),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                }
            }
        }

        // STEP 6: ADVANCED SETTINGS ACCORDION
        item {
            Card(
                modifier = Modifier.fillMaxWidth().testTag("config_advanced_accordion"),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = theme.surface),
                border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(theme.border))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showAdvancedSettings = !showAdvancedSettings },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Default.Settings, contentDescription = null, tint = theme.textSecondary, modifier = Modifier.size(20.dp))
                            Text(
                                text = "Advanced Execution & Safeguards",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = theme.textPrimary
                            )
                        }

                        Icon(
                            imageVector = if (showAdvancedSettings) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = "Toggle",
                            tint = theme.textSecondary
                        )
                    }

                    AnimatedVisibility(visible = showAdvancedSettings) {
                        Column(
                            modifier = Modifier.padding(top = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "Execution Model: Next-Bar Open with Slippage & Spread Simulation",
                                fontSize = 12.sp,
                                color = theme.textSecondary
                            )
                            Text(
                                text = "Intrabar Collision: Pessimistic Stop-Loss First Priority",
                                fontSize = 12.sp,
                                color = theme.textSecondary
                            )
                            Text(
                                text = "Mark-to-Market Accounting: Bar-by-bar portfolio equity reconciliation",
                                fontSize = 12.sp,
                                color = theme.textSecondary
                            )
                        }
                    }
                }
            }
        }

        // MAIN RUN CTA BUTTON
        item {
            Button(
                onClick = {
                    viewModel.runBacktest()
                    onBacktestComplete()
                },
                enabled = !isBacktesting,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("backtest_run_button"),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = theme.brandPrimary,
                    contentColor = Color.White
                )
            ) {
                if (isBacktesting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Executing Quantitative Simulation...", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                } else {
                    Icon(Icons.Default.PlayArrow, contentDescription = "Run", modifier = Modifier.size(22.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Execute Backtest (${selectedAsset.symbol} • ${selectedTimeframe.label})", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }
        }
    }

    // Backtest Progress In-Flight Dialog / Modal
    if (isBacktesting) {
        AlertDialog(
            onDismissRequest = {},
            title = {
                Text(
                    text = "Running Strategy Simulation",
                    fontWeight = FontWeight.Bold,
                    color = theme.textPrimary
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Asset: ${selectedAsset.symbol} (${selectedTimeframe.label})\nStrategy: ${selectedStrategy.name}",
                        fontSize = 13.sp,
                        color = theme.textSecondary
                    )

                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                        color = theme.brandPrimary,
                        trackColor = theme.surfaceElevated
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Processing historical bars...",
                            fontSize = 11.sp,
                            color = theme.textMuted
                        )
                        Text(
                            text = "Simulating",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = theme.brandPrimary
                        )
                    }
                }
            },
            confirmButton = {},
            containerColor = theme.surface,
            shape = RoundedCornerShape(20.dp)
        )
    }
}
