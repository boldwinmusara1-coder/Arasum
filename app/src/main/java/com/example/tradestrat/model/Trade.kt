package com.example.tradestrat.model

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class TradeDirection(val label: String) {
    LONG("LONG"),
    SHORT("SHORT")
}

enum class ExitReason(val label: String, val badgeColor: Long) {
    TAKE_PROFIT("Take Profit", 0xFF10B981),
    STOP_LOSS("Stop Loss", 0xFFEF4444),
    TRAILING_STOP("Trailing Stop", 0xFFF59E0B),
    SIGNAL_REVERSAL("Signal Reversal", 0xFF38BDF8),
    CIRCUIT_BREAKER("Circuit Breaker", 0xFFA855F7),
    END_OF_DATA("End of Test", 0xFF94A3B8)
}

data class Trade(
    val id: String,
    val barIndex: Int,
    val exitBarIndex: Int,
    val entryTimestamp: Long,
    val exitTimestamp: Long,
    val direction: TradeDirection,
    val entryPrice: Double,
    val exitPrice: Double,
    val quantity: Double,
    val positionValue: Double,
    val pnlDollars: Double,
    val pnlPercent: Double,
    val exitReason: ExitReason,
    val feesPaid: Double,
    val rMultiple: Double,
    val holdingBars: Int,
    val maxRunUpPct: Double,
    val maxDrawdownPct: Double,
    val entryReason: String? = null
) {
    val isWin: Boolean get() = pnlDollars > 0

    fun formattedEntryDate(): String {
        return SimpleDateFormat("MMM dd, HH:mm", Locale.US).format(Date(entryTimestamp))
    }

    fun formattedExitDate(): String {
        return SimpleDateFormat("MMM dd, HH:mm", Locale.US).format(Date(exitTimestamp))
    }
}
