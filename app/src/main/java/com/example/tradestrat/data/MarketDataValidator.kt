package com.example.tradestrat.data

import com.example.tradestrat.model.AssetCategory
import com.example.tradestrat.model.Candle
import com.example.tradestrat.model.Timeframe

data class DataValidationReport(
    val isValid: Boolean,
    val totalCandles: Int,
    val firstCandleTimestamp: Long,
    val lastCandleTimestamp: Long,
    val duplicatesRemovedCount: Int,
    val unexpectedGapsCount: Int = 0,
    val expectedGapsCount: Int = 0,
    val lastClosedCandleTimestamp: Long = 0L,
    val violations: List<String>
)

object MarketDataValidator {

    /**
     * Validates and cleanses raw market candle data.
     * Ensures strict chronological sorting, removes duplicate timestamps,
     * filters out incomplete/future candles, and verifies that all OHLC relationships hold true.
     * Classifies gaps into EXPECTED GAP (weekends/market closures for non-24/7 markets) and UNEXPECTED DATA GAP.
     * For 24/7 markets (e.g. Crypto), weekend gaps are correctly identified as UNEXPECTED DATA GAP.
     */
    fun validateAndClean(
        rawCandles: List<Candle>,
        timeframe: Timeframe,
        assetCategory: AssetCategory? = null,
        expectedStartTimeMs: Long? = null,
        expectedEndTimeMs: Long? = null,
        currentTimeMs: Long = System.currentTimeMillis()
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
                    unexpectedGapsCount = 0,
                    expectedGapsCount = 0,
                    lastClosedCandleTimestamp = 0L,
                    violations = listOf("Candle dataset is empty. No historical data received from API.")
                )
            )
        }

        // 1. Filter out incomplete / future candles (close time must be <= currentTimeMs)
        val timeframeDurationMs = timeframe.minutes * 60 * 1000L
        val closedCandles = rawCandles.filter { c ->
            val candleCloseTime = c.timestamp + timeframeDurationMs
            candleCloseTime <= currentTimeMs + 60000L // 1-minute clock skew tolerance
        }

        val incompleteFiltered = rawCandles.size - closedCandles.size
        if (incompleteFiltered > 0) {
            violations.add("Filtered out $incompleteFiltered incomplete / unclosed current bar(s).")
        }

        // 2. Remove duplicate timestamps while keeping the latest entry
        val initialCount = closedCandles.size
        val deduplicated = closedCandles.distinctBy { it.timestamp }
        val duplicatesRemoved = initialCount - deduplicated.size
        if (duplicatesRemoved > 0) {
            violations.add("Detected and purged $duplicatesRemoved duplicate candle timestamp(s).")
        }

        // 3. Sort chronologically
        val sorted = deduplicated.sortedBy { it.timestamp }

        // 4. Validate OHLC price integrity and positive volume
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

        // 5. Gap Classification: EXPECTED GAP vs UNEXPECTED DATA GAP
        var unexpectedGaps = 0
        var expectedGaps = 0
        val is247Market = assetCategory == AssetCategory.CRYPTO

        if (cleanCandles.size >= 2) {
            for (i in 1 until cleanCandles.size) {
                val prevTs = cleanCandles[i - 1].timestamp
                val currTs = cleanCandles[i].timestamp
                val deltaMs = currTs - prevTs

                if (deltaMs > timeframeDurationMs + 5000L) {
                    if (is247Market) {
                        // 24/7 Crypto markets never close on weekends or overnight
                        unexpectedGaps++
                        val gapHours = deltaMs / (1000.0 * 3600.0)
                        violations.add("UNEXPECTED DATA GAP: Detected %.1fh gap in 24/7 Crypto market between %d and %d.".format(
                            gapHours, prevTs, currTs
                        ))
                    } else {
                        // Traditional financial markets (FX, Equities, Indices, Commodities)
                        val isWeekendClosure = deltaMs in (36 * 3600 * 1000L)..(72 * 3600 * 1000L)
                        val isOvernightClosure = (assetCategory == AssetCategory.STOCKS || assetCategory == AssetCategory.INDICES) &&
                                deltaMs in (10 * 3600 * 1000L)..(20 * 3600 * 1000L)

                        if (isWeekendClosure || isOvernightClosure) {
                            expectedGaps++
                            val gapType = if (isWeekendClosure) "Weekend closure" else "Overnight closure"
                            violations.add("EXPECTED GAP: %s (%.1fh) between %d and %d.".format(
                                gapType, deltaMs / (1000.0 * 3600.0), prevTs, currTs
                            ))
                        } else {
                            unexpectedGaps++
                            violations.add("UNEXPECTED DATA GAP: Detected %.1fh gap during regular trading hours between %d and %d.".format(
                                deltaMs / (1000.0 * 3600.0), prevTs, currTs
                            ))
                        }
                    }
                }
            }
        }

        // 6. Verify minimum historical candle depth
        if (cleanCandles.size < 10) {
            violations.add("Insufficient historical depth: got ${cleanCandles.size} clean candles, minimum 10 required for indicator calculation.")
        }

        val isValid = cleanCandles.size >= 10

        val lastClosedTimestamp = cleanCandles.lastOrNull()?.let { it.timestamp + timeframeDurationMs } ?: 0L

        val report = DataValidationReport(
            isValid = isValid,
            totalCandles = cleanCandles.size,
            firstCandleTimestamp = cleanCandles.firstOrNull()?.timestamp ?: 0L,
            lastCandleTimestamp = cleanCandles.lastOrNull()?.timestamp ?: 0L,
            duplicatesRemovedCount = duplicatesRemoved,
            unexpectedGapsCount = unexpectedGaps,
            expectedGapsCount = expectedGaps,
            lastClosedCandleTimestamp = lastClosedTimestamp,
            violations = violations
        )

        return Pair(cleanCandles, report)
    }
}
