package com.example.tradestrat.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tradestrat.model.ExitReason
import com.example.tradestrat.model.Trade
import com.example.tradestrat.model.TradeDirection
import com.example.ui.theme.*

enum class TradeFilter(val label: String) {
    ALL("All Trades"),
    WINS("Profitable"),
    LOSSES("Losses"),
    LONGS("Longs"),
    SHORTS("Shorts")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TradeLogList(
    trades: List<Trade>,
    modifier: Modifier = Modifier
) {
    var selectedFilter by remember { mutableStateOf(TradeFilter.ALL) }

    val filteredTrades = remember(trades, selectedFilter) {
        when (selectedFilter) {
            TradeFilter.ALL -> trades
            TradeFilter.WINS -> trades.filter { it.isWin }
            TradeFilter.LOSSES -> trades.filter { !it.isWin }
            TradeFilter.LONGS -> trades.filter { it.direction == TradeDirection.LONG }
            TradeFilter.SHORTS -> trades.filter { it.direction == TradeDirection.SHORT }
        }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("trade_log_card"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = BentoCardBg),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(BentoBorder))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.FilterList,
                        contentDescription = "Trades",
                        tint = CyanAccent,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Trade Execution Log (${trades.size})",
                        style = MaterialTheme.typography.labelLarge,
                        color = TextPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Filter Chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                TradeFilter.values().forEach { filter ->
                    FilterChip(
                        selected = selectedFilter == filter,
                        onClick = { selectedFilter = filter },
                        label = { Text(filter.label, fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = CyanAccent.copy(alpha = 0.2f),
                            selectedLabelColor = CyanAccent,
                            containerColor = TradeSurfaceElevated,
                            labelColor = TextSecondary
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = selectedFilter == filter,
                            borderColor = TradeBorder,
                            selectedBorderColor = CyanAccent
                        ),
                        modifier = Modifier.height(30.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (filteredTrades.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No trades match the selected filter", color = TextMuted, style = MaterialTheme.typography.bodyMedium)
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Render latest 15 trades or all
                    filteredTrades.take(20).forEach { trade ->
                        TradeItemRow(trade)
                    }

                    if (filteredTrades.size > 20) {
                        Text(
                            text = "+ ${filteredTrades.size - 20} more trades in full history",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMuted,
                            modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 4.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TradeItemRow(trade: Trade) {
    val isLong = trade.direction == TradeDirection.LONG
    val dirColor = if (isLong) BullGreen else BearRed
    val pColor = if (trade.isWin) BullGreen else BearRed
    val reasonColor = Color(trade.exitReason.badgeColor)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("trade_item_${trade.id}"),
        shape = RoundedCornerShape(10.dp),
        color = TradeSurfaceElevated
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            // Top Row: Direction, ID, PnL
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    // Direction badge
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = dirColor.copy(alpha = 0.2f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (isLong) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                                contentDescription = trade.direction.name,
                                tint = dirColor,
                                modifier = Modifier.size(10.dp)
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = trade.direction.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = dirColor,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp
                            )
                        }
                    }

                    Text(
                        text = "#${trade.id}",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted
                    )

                    // Exit Reason badge
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = reasonColor.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = trade.exitReason.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = reasonColor,
                            fontSize = 9.sp,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                // PnL badge
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "${if (trade.isWin) "+" else ""}$${String.format("%,.2f", trade.pnlDollars)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = pColor,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${if (trade.isWin) "+" else ""}${String.format("%.2f%%", trade.pnlPercent)} (${String.format("%+.2fR", trade.rMultiple)})",
                        style = MaterialTheme.typography.labelSmall,
                        color = pColor,
                        fontSize = 10.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Details Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Entry: $${String.format("%,.2f", trade.entryPrice)} • ${trade.formattedEntryDate()}",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                    fontSize = 10.sp
                )
                Text(
                    text = "Exit: $${String.format("%,.2f", trade.exitPrice)} • ${trade.holdingBars} bars",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                    fontSize = 10.sp
                )
            }
        }
    }
}
