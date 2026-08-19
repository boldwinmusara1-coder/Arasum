package com.example.tradestrat.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tradestrat.model.DataSourceInfo
import com.example.tradestrat.model.MarketAsset
import com.example.tradestrat.model.Timeframe
import com.example.ui.theme.*

enum class DateRangePreset(val label: String, val days: Int) {
    DAYS_30("30D", 30),
    DAYS_90("90D", 90),
    DAYS_180("180D", 180),
    YEAR_1("1Y", 365),
    YEARS_2("2Y", 730),
    MAX("MAX", 1200)
}

enum class ProviderSelection(val id: String, val label: String, val description: String) {
    AUTO("auto", "Auto Provider", "Picks the optimal live source for asset category"),
    BINANCE("binance", "Binance API", "High-frequency spot/crypto candles"),
    COINBASE("coinbase", "Coinbase API", "Institutional US crypto data"),
    YAHOO("yahoo", "Yahoo Finance", "Equities, Forex, Indices & Commodities"),
    TWELVEDATA("twelvedata", "TwelveData", "Global multi-asset institutional feed")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MarketDataConfigCard(
    asset: MarketAsset,
    timeframe: Timeframe,
    dataSourceInfo: DataSourceInfo?,
    isLoading: Boolean,
    errorMessage: String?,
    selectedProvider: ProviderSelection,
    selectedPreset: DateRangePreset,
    onProviderSelected: (ProviderSelection) -> Unit,
    onPresetSelected: (DateRangePreset) -> Unit,
    onRefresh: () -> Unit,
    onApiKeyConfigured: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showProviderDialog by remember { mutableStateOf(false) }
    var showApiKeyDialog by remember { mutableStateOf(false) }
    var apiKeyInput by remember { mutableStateOf("") }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = TvSurface,
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(TvBorder))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header Row: Data Pipeline Status + Provider Selector
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudDownload,
                        contentDescription = "Real Data Feed",
                        tint = if (errorMessage != null) TvRed else TvGreen,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "HISTORICAL MARKET DATA",
                        style = MaterialTheme.typography.labelSmall,
                        color = TvTextSecondary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        letterSpacing = 0.5.sp
                    )
                }

                // Provider Badge Button
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = TvSurfaceElevated,
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(TvBorder)),
                    modifier = Modifier
                        .clickable { showProviderDialog = true }
                        .testTag("provider_selector_btn")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = selectedProvider.label,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TvBlue
                        )
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = "Select Provider",
                            tint = TvBlue,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }

            // Date Range Presets Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                DateRangePreset.values().forEach { preset ->
                    val isSelected = selectedPreset == preset
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .height(28.dp)
                            .clickable { onPresetSelected(preset) }
                            .testTag("preset_range_${preset.name}"),
                        shape = RoundedCornerShape(4.dp),
                        color = if (isSelected) TvBlue.copy(alpha = 0.2f) else TvSurfaceElevated,
                        border = if (isSelected) CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(TvBlue)) else null
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = preset.label,
                                fontSize = 10.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) TvBlue else TvTextSecondary
                            )
                        }
                    }
                }
            }

            // Real Data Integrity Badge / Info Bar
            if (dataSourceInfo != null && errorMessage == null) {
                val isReal = dataSourceInfo.isRealHistorical
                val statusColor = if (isReal) TvGreen else TvAmber
                val statusText = if (isReal) "Verified Real Feed • ${dataSourceInfo.provider}" else "Simulated Demo • ${dataSourceInfo.provider}"
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = statusColor.copy(alpha = 0.08f),
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(statusColor.copy(alpha = 0.3f))),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = if (isReal) Icons.Default.CheckCircle else Icons.Default.Info,
                                contentDescription = if (isReal) "Verified Real Data" else "Demo Data",
                                tint = statusColor,
                                modifier = Modifier.size(14.dp)
                            )
                            Column {
                                Text(
                                    text = statusText,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = statusColor,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp
                                )
                                if (dataSourceInfo.startDate.isNotEmpty() && dataSourceInfo.endDate.isNotEmpty()) {
                                    Text(
                                        text = "${dataSourceInfo.startDate} → ${dataSourceInfo.endDate} (${dataSourceInfo.candleCount} bars)",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TvTextSecondary,
                                        fontSize = 9.sp
                                    )
                                }
                            }
                        }

                        IconButton(
                            onClick = onRefresh,
                            modifier = Modifier
                                .size(24.dp)
                                .testTag("refresh_real_data_btn")
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(14.dp),
                                    color = TvGreen,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Refresh Data",
                                    tint = TvTextSecondary,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Error Banner if Real API Request Fails (Strict: No fallback to synthetic data)
            if (errorMessage != null) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = TvRed.copy(alpha = 0.12f),
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(TvRed.copy(alpha = 0.5f))),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ErrorOutline,
                                contentDescription = "API Error",
                                tint = TvRed,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Market Data API Notice",
                                style = MaterialTheme.typography.labelMedium,
                                color = TvRed,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Text(
                            text = errorMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = TvTextPrimary,
                            fontSize = 11.sp
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = onRefresh,
                                colors = ButtonDefaults.buttonColors(containerColor = TvRed),
                                shape = RoundedCornerShape(4.dp),
                                modifier = Modifier.height(28.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                            ) {
                                Text("Retry API", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }

                            OutlinedButton(
                                onClick = { showApiKeyDialog = true },
                                shape = RoundedCornerShape(4.dp),
                                modifier = Modifier.height(28.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                            ) {
                                Text("Set API Key", fontSize = 10.sp, color = TvTextSecondary)
                            }
                        }
                    }
                }
            }
        }
    }

    // Provider Choice Dialog
    if (showProviderDialog) {
        AlertDialog(
            onDismissRequest = { showProviderDialog = false },
            title = {
                Text(
                    text = "Select Market Data Provider",
                    style = MaterialTheme.typography.titleMedium,
                    color = TvTextPrimary,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    ProviderSelection.values().forEach { prov ->
                        val isSelected = selectedProvider == prov
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onProviderSelected(prov)
                                    showProviderDialog = false
                                }
                                .testTag("provider_option_${prov.name}"),
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) TvSurfaceElevated else TvSurface
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = prov.label,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) TvBlue else TvTextPrimary
                                    )
                                    Text(
                                        text = prov.description,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TvTextSecondary,
                                        fontSize = 10.sp
                                    )
                                }
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Selected",
                                        tint = TvBlue,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showProviderDialog = false }) {
                    Text("Close", color = TvTextSecondary)
                }
            },
            shape = RoundedCornerShape(16.dp),
            containerColor = TvSurface
        )
    }

    // API Key Dialog
    if (showApiKeyDialog) {
        AlertDialog(
            onDismissRequest = { showApiKeyDialog = false },
            title = {
                Text("Configure Market Data API Key", style = MaterialTheme.typography.titleMedium, color = TvTextPrimary, fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Enter your TwelveData or custom Market API key. The key will be stored locally for requests.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TvTextSecondary
                    )
                    OutlinedTextField(
                        value = apiKeyInput,
                        onValueChange = { apiKeyInput = it },
                        label = { Text("API Key") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = TvBlue,
                            unfocusedBorderColor = TvBorder,
                            focusedTextColor = TvTextPrimary,
                            unfocusedTextColor = TvTextPrimary
                        ),
                        modifier = Modifier.fillMaxWidth().testTag("api_key_text_field")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onApiKeyConfigured(apiKeyInput.trim())
                        showApiKeyDialog = false
                        onRefresh()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = TvBlue)
                ) {
                    Text("Save & Connect")
                }
            },
            dismissButton = {
                TextButton(onClick = { showApiKeyDialog = false }) {
                    Text("Cancel", color = TvTextSecondary)
                }
            },
            shape = RoundedCornerShape(16.dp),
            containerColor = TvSurface
        )
    }
}
