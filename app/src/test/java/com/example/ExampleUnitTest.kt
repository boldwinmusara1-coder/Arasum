package com.example

import com.example.tradestrat.data.MarketDataValidator
import com.example.tradestrat.engine.BacktestEngine
import com.example.tradestrat.model.*
import org.junit.Assert.*
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun testMarketDataValidator_validCandles() {
        val baseTime = 1700000000000L
        val candles = listOf(
            Candle(baseTime, 100.0, 105.0, 95.0, 102.0, 1000.0),
            Candle(baseTime + 86400000L, 102.0, 110.0, 101.0, 108.0, 1200.0),
            Candle(baseTime + 172800000L, 108.0, 112.0, 104.0, 106.0, 800.0)
        )

        val (clean, report) = MarketDataValidator.validateAndClean(candles, Timeframe.D1)
        assertTrue(report.isValid)
        assertEquals(3, clean.size)
        assertEquals(0, report.removedBadOhlcCount)
        assertEquals(0, report.removedDuplicateCount)
    }

    @Test
    fun testMarketDataValidator_filtersInvalidOhlcAndDuplicates() {
        val baseTime = 1700000000000L
        val dirtyCandles = listOf(
            Candle(baseTime, 100.0, 105.0, 95.0, 102.0, 1000.0),
            // Duplicate timestamp
            Candle(baseTime, 100.0, 105.0, 95.0, 102.0, 1000.0),
            // Invalid: High lower than Open/Close
            Candle(baseTime + 86400000L, 102.0, 90.0, 80.0, 108.0, 1200.0),
            // Valid
            Candle(baseTime + 172800000L, 108.0, 112.0, 104.0, 106.0, 800.0)
        )

        val (clean, report) = MarketDataValidator.validateAndClean(dirtyCandles, Timeframe.D1)
        assertEquals(2, clean.size)
        assertEquals(1, report.removedBadOhlcCount)
        assertEquals(1, report.removedDuplicateCount)
    }

    @Test
    fun testBacktestEngine_executesWithRealCandles() {
        val baseTime = 1700000000000L
        val asset = MarketAsset("BTC_USD", "BTC/USD", "Bitcoin", AssetCategory.CRYPTO, 60000.0, 0.001)
        val candles = mutableListOf<Candle>()
        var price = 50000.0
        for (i in 0 until 50) {
            val open = price
            val high = open * 1.02
            val low = open * 0.98
            val close = open * 1.01
            candles.add(Candle(baseTime + (i * 86400000L), open, high, low, close, 5000.0))
            price = close
        }

        val strategy = StrategyDefinition.PRESETS.first()
        val risk = RiskParameters(intrabarExecution = IntrabarExecutionAssumption.PESSIMISTIC_STOP_FIRST)
        val dsInfo = DataSourceInfo(
            provider = "Binance Real API",
            symbol = "BTC/USD",
            market = "Crypto",
            timeframe = "1D",
            startDate = "2024-01-01",
            endDate = "2024-02-20",
            startTimestamp = baseTime,
            endTimestamp = baseTime + (49 * 86400000L),
            candleCount = 50,
            isRealHistorical = true,
            validationStatus = "VERIFIED_VALID",
            intrabarExecutionRule = risk.intrabarExecution.label
        )

        val result = BacktestEngine.runBacktest(
            candles = candles,
            asset = asset,
            regime = MarketRegime.HISTORICAL_REALISTIC,
            timeframe = Timeframe.D1,
            strategy = strategy,
            risk = risk,
            dataSourceInfo = dsInfo
        )

        assertNotNull(result)
        assertEquals(50, result.candles.size)
        assertEquals("Binance Real API", result.dataSourceInfo.provider)
        assertTrue(result.dataSourceInfo.isRealHistorical)
    }
}
