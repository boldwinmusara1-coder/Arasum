package com.example.tradestrat.data

import com.example.tradestrat.model.Candle
import com.example.tradestrat.model.Timeframe

data class DataValidationReport(
    val isValid: Boolean,
    val totalCandles: Int,
    val firstCandleTimestamp: Long,
    val lastCandleTimestamp: Long,
    val duplicatesRemovedCount: Int,
    val violations: List<String>
)

object MarketDataValidator {

    /**
     * Validates and cleanses raw market candle data.
     * Ensures strict chronological sorting, removes duplicate timestamps,
     * and verifies that all OHLC relationships (High >= Open/Close/Low, Low <= Open/Close/High, Volume >= 0) hold true.
     */
    fun validateAndClean(
        rawCandles: List<Candle>,
        timeframe: Timeframe,
        expectedStartTimeMs: Long? = null,
        expectedEndTimeMs: Long? = null
    ): Pair<List<Candle>, DataValidationReport> {
        val violations = mutableListOf<String>()

        if (rawCandles.isEmpty()) {
            return Pair(
                emptyList(),
                DataValidationReport(
                    isValid = false,
                    totalCandles = 0,
                    firstCandleTimestamp = 0L,
                    lastCandleTimestamp = 0L,
                    duplicatesRemovedCount = 0,
                    violations = listOf("Candle dataset is empty. No historical data received from API.")
                )
            )
        }

        // 1. Remove duplicate timestamps while keeping the latest entry
        val initialCount = rawCandles.size
        val deduplicated = rawCandles.distinctBy { it.timestamp }
        val duplicatesRemoved = initialCount - deduplicated.size
        if (duplicatesRemoved > 0) {
            violations.add("Detected and purged $duplicatesRemoved duplicate candle timestamp(s).")
        }

        // 2. Sort chronologically
        val sorted = deduplicated.sortedBy { it.timestamp }

        // 3. Validate OHLC price integrity and positive volume
        val cleanCandles = mutableListOf<Candle>()
        var ohlcViolationCount = 0

        for (i in sorted.indices) {
            val c = sorted[i]

            // Check non-negative / non-zero prices
            if (c.open <= 0 || c.high <= 0 || c.low <= 0 || c.close <= 0) {
                ohlcViolationCount++
                continue
            }

            // High must be >= max(open, close, low)
            // Low must be <= min(open, close, high)
            val isValidHigh = c.high >= c.open && c.high >= c.close && c.high >= c.low
            val isValidLow = c.low <= c.open && c.low <= c.close && c.low <= c.high
            val isValidVolume = c.volume >= 0.0

            if (!isValidHigh || !isValidLow || !isValidVolume) {
                ohlcViolationCount++
                continue
            }

            cleanCandles.add(c)
        }

        if (ohlcViolationCount > 0) {
            violations.add("Filtered out $ohlcViolationCount invalid candle(s) violating OHLC bounds (High >= Open/Close/Low, Low <= Open/Close/High).")
        }

        // 4. Verify chronological progression
        var chronologicalError = false
        for (i in 1 until cleanCandles.size) {
            if (cleanCandles[i].timestamp <= cleanCandles[i - 1].timestamp) {
                chronologicalError = true
                break
            }
        }
        if (chronologicalError) {
            violations.add("Chronological sequence anomaly detected in cleansed candles.")
        }

        // 5. Verify minimum candle depth
        if (cleanCandles.size < 10) {
            violations.add("Insufficient historical depth: got ${cleanCandles.size} clean candles, minimum 10 required for indicator calculation.")
        }

        val isValid = cleanCandles.size >= 10 && !chronologicalError

        val report = DataValidationReport(
            isValid = isValid,
            totalCandles = cleanCandles.size,
            firstCandleTimestamp = cleanCandles.firstOrNull()?.timestamp ?: 0L,
            lastCandleTimestamp = cleanCandles.lastOrNull()?.timestamp ?: 0L,
            duplicatesRemovedCount = duplicatesRemoved,
            violations = violations
        )

        return Pair(cleanCandles, report)
    }
}
