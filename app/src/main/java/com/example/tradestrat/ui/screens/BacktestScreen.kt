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
import com.example.tradestrat.ui.components.DateRangePreset
import com.example.ui.theme.LocalAppTheme
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BacktestScreen(
    viewModel: BacktestViewModel,
    modifier: Modifier = Modifier,
    onNavigateToSmcIct: () -> Unit = {},
    onBacktestComplete: () -> Unit = {}
) {
    val theme = LocalAppTheme.current
    val selectedAsset by viewModel.selectedAsset.collectAsState()
    val selectedTimeframe by viewModel.selectedTimeframe.collectAsState()
    val selectedStrategy by viewModel.selectedStrategy.collectAsState()
    val riskParameters by viewModel.riskParameters.collectAsState()
    val isBacktesting by viewModel.isBacktesting.collectAsState()
    val progress by viewModel.backtestProgress.collectAsState()
    val selectedDatePreset by viewModel.selectedDatePreset.collectAsState()
    val favoriteSymbols by viewModel.favoriteSymbols.collectAsState()
    val recentSymbols by viewModel.recentSymbols.collectAsState()
    val dataFetchError by viewModel.dataFetchError.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<AssetCategory?>(null) }
    var showOnlyFavorites by remember { mutableStateOf(false) }
    var showAdvancedSettings by remember { mutableStateOf(false) }

    // Risk inputs
    var initialCapitalInput by remember(riskParameters) {
        mutableStateOf(riskParameters.initialCapital.toInt().toString())
    }
    var riskPercentInput by remember(riskParameters) {
        mutableStateOf(riskParameters.riskPerTradePercent.toString())
    }
    var stopLossAtrInput by remember(riskParameters) {
        mutableStateOf(riskParameters.stopLossAtrMultiplier.toString())
    }
    var takeProfitRInput by remember(riskParameters) {
        mutableStateOf(riskParameters.takeProfitRMultiple.toString())
    }
    var slippageBpsInput by remember(riskParameters) {
        mutableStateOf((riskParameters.slippagePercent * 10000.0).toInt().toString())
    }
    var commissionBpsInput by remember(riskParameters) {
        mutableStateOf((riskParameters.commissionPercent * 10000.0).toInt().toString())
    }

    // Filtered assets
    val filteredAssets = remember(searchQuery, selectedCategory, showOnlyFavorites, favoriteSymbols) {
        MarketDataProvider.ASSETS.filter { asset ->
            val matchesSearch = searchQuery.isBlank() || asset.symbol.contains(searchQuery, ignoreCase = true) || asset.name.contains(searchQuery, ignoreCase = true)
            val matchesCategory = selectedCategory == null || asset.category == selectedCategory
            val matchesFav = !showOnlyFavorites || favoriteSymbols.contains(asset.symbol)
            matchesSearch && matchesCategory && matchesFav
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(theme.background)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 96.dp)
    ) {
        // Header
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
                    text = "Primary configuration & institutional risk modeling",
                    style = MaterialTheme.typography.bodySmall,
                    color = theme.textSecondary,
                    fontSize = 12.sp
                )
            }
        }

        // Live Backtest Progress / Cancellation Banner
        if (isBacktesting) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = theme.surfaceElevated),
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(theme.brandPrimary))
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = theme.brandPrimary)
                                Text("Executing Backtest Simulation...", fontWeight = FontWeight.Bold, color = theme.textPrimary, fontSize = 13.sp)
                            }

                            Button(
                                onClick = { viewModel.cancelBacktest() },
                                colors = ButtonDefaults.buttonColors(containerColor = theme.accentRed.copy(alpha = 0.15f)),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Cancel", color = theme.accentRed, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            }
                        }

                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = theme.brandPrimary)

                        Text(
                            text = "${progress.strategyName} • ${progress.symbol} • ${progress.timeframe} • ${progress.currentDateStr}",
                            fontSize = 11.sp,
                            color = theme.textSecondary
                        )
                    }
                }
            }
        }

        // 1. MARKET SELECTOR
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("config_market_card"),
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
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .background(theme.brandPrimary.copy(alpha = 0.18f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("1", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = theme.brandPrimary)
                            }
                            Text(
                                text = "Market & Asset",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = theme.textPrimary
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = theme.brandPrimary.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = "${selectedAsset.symbol} (${selectedAsset.category.label})",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = theme.brandPrimary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    // Search & Favorites Bar
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Search symbol (BTC, EUR, AAPL)...", fontSize = 12.sp, color = theme.textMuted) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", tint = theme.textMuted, modifier = Modifier.size(18.dp)) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = theme.surfaceElevated,
                                unfocusedContainerColor = theme.surfaceElevated,
                                focusedBorderColor = theme.brandPrimary,
                                unfocusedBorderColor = theme.borderSubtle
                            ),
                            singleLine = true
                        )

                        IconButton(
                            onClick = { showOnlyFavorites = !showOnlyFavorites },
                            modifier = Modifier
                                .size(44.dp)
                                .background(if (showOnlyFavorites) theme.brandPrimary.copy(alpha = 0.2f) else theme.surfaceElevated, RoundedCornerShape(10.dp))
                        ) {
                            Icon(
                                imageVector = if (showOnlyFavorites) Icons.Default.Star else Icons.Default.StarBorder,
                                contentDescription = "Favorites",
                                tint = if (showOnlyFavorites) Color(0xFFF59E0B) else theme.textMuted
                            )
                        }
                    }

                    // Category Chips
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        item {
                            FilterChip(
                                selected = selectedCategory == null,
                                onClick = { selectedCategory = null },
                                label = { Text("All", fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = theme.brandPrimary.copy(alpha = 0.2f), selectedLabelColor = theme.brandPrimary)
                            )
                        }
                        items(AssetCategory.values()) { cat ->
                            FilterChip(
                                selected = selectedCategory == cat,
                                onClick = { selectedCategory = cat },
                                label = { Text(cat.label, fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = theme.brandPrimary.copy(alpha = 0.2f), selectedLabelColor = theme.brandPrimary)
                            )
                        }
                    }

                    // Asset Grid / Horizontal Carousel
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(filteredAssets) { asset ->
                            val isSelected = asset.symbol == selectedAsset.symbol
                            val isFav = favoriteSymbols.contains(asset.symbol)

                            Surface(
                                modifier = Modifier
                                    .clickable { viewModel.setAsset(asset) }
                                    .width(130.dp),
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) theme.brandPrimary.copy(alpha = 0.15f) else theme.surfaceElevated,
                                border = androidx.compose.foundation.BorderStroke(1.dp, if (isSelected) theme.brandPrimary else theme.borderSubtle)
                            ) {
                                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(text = asset.symbol, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = theme.textPrimary)
                                        IconButton(
                                            onClick = { viewModel.toggleFavoriteSymbol(asset.symbol) },
                                            modifier = Modifier.size(20.dp)
                                        ) {
                                            Icon(
                                                imageVector = if (isFav) Icons.Default.Star else Icons.Default.StarBorder,
                                                contentDescription = "Fav",
                                                tint = if (isFav) Color(0xFFF59E0B) else theme.textMuted,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }
                                    Text(text = asset.name, fontSize = 10.sp, color = theme.textMuted, maxLines = 1)
                                }
                            }
                        }
                    }
                }
            }
        }

        // 2. TIMEFRAME SELECTOR (1-Tap Horizontal UX)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = theme.surface),
                border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(theme.borderSubtle))
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .background(theme.brandPrimary.copy(alpha = 0.18f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("2", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = theme.brandPrimary)
                        }
                        Text(
                            text = "Timeframe",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = theme.textPrimary
                        )
                    }

                    // Fast 1-Tap Horizontal Timeframe Selector
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(Timeframe.values()) { tf ->
                            val isSelected = tf == selectedTimeframe
                            Surface(
                                modifier = Modifier
                                    .clickable { viewModel.setTimeframe(tf) }
                                    .weight(1f, fill = false),
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) theme.brandPrimary else theme.surfaceElevated,
                                border = androidx.compose.foundation.BorderStroke(1.dp, if (isSelected) theme.brandPrimary else theme.borderSubtle)
                            ) {
                                Text(
                                    text = tf.label,
                                    color = if (isSelected) Color.White else theme.textPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // 3. STRATEGY SELECTOR
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
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .background(theme.brandPrimary.copy(alpha = 0.18f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("3", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = theme.brandPrimary)
                            }
                            Text(
                                text = "Strategy Algorithm",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = theme.textPrimary
                            )
                        }

                        if (selectedStrategy.strategyType in listOf(StrategyType.SMC_CONCEPTS, StrategyType.ICT_CONCEPTS, StrategyType.SMC_ICT_CONCEPTS)) {
                            TextButton(onClick = onNavigateToSmcIct) {
                                Text("Configure SMC/ICT", fontSize = 11.sp, color = theme.brandPrimary, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    // Strategy Presets with clear descriptions
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        StrategyDefinition.PRESETS.forEach { strat ->
                            val isSelected = strat.strategyType == selectedStrategy.strategyType
                            val badgeColor = when (strat.strategyType) {
                                StrategyType.SMC_CONCEPTS -> Color(0xFFA855F7)
                                StrategyType.ICT_CONCEPTS -> Color(0xFFF59E0B)
                                StrategyType.SMC_ICT_CONCEPTS -> Color(0xFF10B981)
                                StrategyType.TRENDLINE_BREAK, StrategyType.TRENDLINE_BOUNCE -> Color(0xFF38BDF8)
                                else -> theme.brandPrimary
                            }

                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.setStrategy(strat) },
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) badgeColor.copy(alpha = 0.14f) else theme.surfaceElevated,
                                border = androidx.compose.foundation.BorderStroke(1.dp, if (isSelected) badgeColor else theme.borderSubtle)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Text(
                                                text = strat.name,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp,
                                                color = theme.textPrimary
                                            )
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = badgeColor.copy(alpha = 0.2f)
                                            ) {
                                                Text(
                                                    text = strat.strategyType.name,
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = badgeColor,
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = strat.description,
                                            fontSize = 11.sp,
                                            color = theme.textSecondary,
                                            lineHeight = 14.sp
                                        )
                                    }

                                    RadioButton(
                                        selected = isSelected,
                                        onClick = { viewModel.setStrategy(strat) },
                                        colors = RadioButtonDefaults.colors(selectedColor = badgeColor)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 4. DATE RANGE PRESETS
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = theme.surface),
                border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(theme.borderSubtle))
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .background(theme.brandPrimary.copy(alpha = 0.18f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("4", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = theme.brandPrimary)
                        }
                        Text(
                            text = "Historical Date Range",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = theme.textPrimary
                        )
                    }

                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(DateRangePreset.values()) { preset ->
                            val isSelected = preset == selectedDatePreset
                            FilterChip(
                                selected = isSelected,
                                onClick = { viewModel.setDatePreset(preset) },
                                label = { Text(preset.label, fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = theme.brandPrimary.copy(alpha = 0.2f),
                                    selectedLabelColor = theme.brandPrimary
                                )
                            )
                        }
                    }
                }
            }
        }

        // 5. RISK PARAMETERS & ADVANCED OPTIONS
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
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .background(theme.brandPrimary.copy(alpha = 0.18f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("5", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = theme.brandPrimary)
                            }
                            Text(
                                text = "Risk & Position Sizing",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = theme.textPrimary
                            )
                        }

                        TextButton(onClick = { showAdvancedSettings = !showAdvancedSettings }) {
                            Text(
                                text = if (showAdvancedSettings) "Hide Advanced" else "Advanced Options",
                                fontSize = 11.sp,
                                color = theme.brandPrimary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Primary Risk Inputs
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = initialCapitalInput,
                            onValueChange = {
                                initialCapitalInput = it
                                it.toDoubleOrNull()?.let { cap -> viewModel.updateRiskParameters(riskParameters.copy(initialCapital = cap)) }
                            },
                            label = { Text("Initial Capital ($)", fontSize = 10.sp) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        )

                        OutlinedTextField(
                            value = riskPercentInput,
                            onValueChange = {
                                riskPercentInput = it
                                it.toDoubleOrNull()?.let { r -> viewModel.updateRiskParameters(riskParameters.copy(positionSizeValue = r)) }
                            },
                            label = { Text("Risk Per Trade (%)", fontSize = 10.sp) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        )
                    }

                    // Expandable Advanced Options
                    AnimatedVisibility(visible = showAdvancedSettings) {
                        Column(
                            modifier = Modifier.padding(top = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            HorizontalDivider(color = theme.borderSubtle, thickness = 1.dp)

                            Text(
                                text = "EXECUTION & SLIPPAGE ASSUMPTIONS",
                                style = MaterialTheme.typography.labelSmall,
                                color = theme.brandPrimary,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )

                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                OutlinedTextField(
                                    value = slippageBpsInput,
                                    onValueChange = {
                                        slippageBpsInput = it
                                        it.toDoubleOrNull()?.let { bps -> viewModel.updateRiskParameters(riskParameters.copy(slippageBps = bps)) }
                                    },
                                    label = { Text("Slippage (bps)", fontSize = 10.sp) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(10.dp)
                                )

                                OutlinedTextField(
                                    value = commissionBpsInput,
                                    onValueChange = {
                                        commissionBpsInput = it
                                        it.toDoubleOrNull()?.let { bps -> viewModel.updateRiskParameters(riskParameters.copy(commissionBps = bps)) }
                                    },
                                    label = { Text("Commission (bps)", fontSize = 10.sp) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(10.dp)
                                )
                            }

                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                OutlinedTextField(
                                    value = stopLossAtrInput,
                                    onValueChange = {
                                        stopLossAtrInput = it
                                        it.toDoubleOrNull()?.let { sl -> viewModel.updateRiskParameters(riskParameters.copy(stopLossValue = sl)) }
                                    },
                                    label = { Text("SL ATR Multiplier", fontSize = 10.sp) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(10.dp)
                                )

                                OutlinedTextField(
                                    value = takeProfitRInput,
                                    onValueChange = {
                                        takeProfitRInput = it
                                        it.toDoubleOrNull()?.let { tp -> viewModel.updateRiskParameters(riskParameters.copy(takeProfitValue = tp)) }
                                    },
                                    label = { Text("Take Profit R-Multiple", fontSize = 10.sp) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(10.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // ERROR DISPLAY
        if (dataFetchError != null) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = theme.accentRed.copy(alpha = 0.12f)),
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(theme.accentRed.copy(alpha = 0.4f)))
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.ErrorOutline, contentDescription = "Error", tint = theme.accentRed, modifier = Modifier.size(20.dp))
                            Text("Market Data Fetch Error", fontWeight = FontWeight.Bold, color = theme.accentRed, fontSize = 13.sp)
                        }
                        Text(text = dataFetchError ?: "", color = theme.textPrimary, fontSize = 11.sp, lineHeight = 16.sp)
                    }
                }
            }
        }

        // ACTIVE BACKTEST PROGRESS & CANCEL
        if (isBacktesting) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = theme.surfaceElevated),
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(theme.brandPrimary.copy(alpha = 0.3f)))
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = theme.brandPrimary
                                )
                                Text(
                                    text = progress.currentDateStr.ifEmpty { "Executing quantitative engine..." },
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = theme.textPrimary
                                )
                            }
                            OutlinedButton(
                                onClick = { viewModel.cancelBacktest() },
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.height(30.dp)
                            ) {
                                Text("Cancel", fontSize = 11.sp, color = theme.accentRed, fontWeight = FontWeight.Bold)
                            }
                        }
                        LinearProgressIndicator(
                            progress = { progress.progressPct },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = theme.brandPrimary,
                            trackColor = theme.borderSubtle
                        )
                    }
                }
            }
        }

        // RUN BACKTEST BUTTON
        item {
            Button(
                onClick = {
                    if (!isBacktesting) {
                        viewModel.runBacktest()
                        onBacktestComplete()
                    }
                },
                enabled = !isBacktesting,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("run_backtest_button"),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = theme.brandPrimary,
                    disabledContainerColor = theme.surfaceElevated
                )
            ) {
                if (isBacktesting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = theme.brandPrimaryText
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "SIMULATING...",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = theme.textSecondary
                    )
                } else {
                    Icon(imageVector = Icons.Default.PlayArrow, contentDescription = "Run", tint = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "RUN BACKTEST SIMULATION",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = Color.White
                    )
                }
            }
        }
    }
}
