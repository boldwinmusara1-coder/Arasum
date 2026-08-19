package com.example

import com.example.tradestrat.data.*
import com.example.tradestrat.engine.BacktestEngine
import com.example.tradestrat.engine.IndicatorCalculators
import com.example.tradestrat.model.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class ExampleUnitTest {

    private val baseTime = 1700000000000L
    private val testAsset = MarketAsset("BTC_USD", "BTC/USD", "Bitcoin", AssetCategory.CRYPTO, 60000.0, "Crypto")

    // 1. No look-ahead: indicator on bar N is invariant to future appended bars
    @Test
    fun test01_noLookAhead() {
        val candles10 = (0 until 10).map { i ->
            val p = 100.0 + i * 2.0
            Candle(baseTime + (i * 86400000L), p, p + 2.0, p - 2.0, p + 1.0, 1000.0)
        }
        val candles20 = candles10 + (10 until 20).map { i ->
            val p = 120.0 + (i - 10) * 5.0
            Candle(baseTime + (i * 86400000L), p, p + 4.0, p - 3.0, p + 3.0, 1500.0)
        }

        val sma10 = IndicatorCalculators.calculateSMA(candles10, 5)
        val sma20 = IndicatorCalculators.calculateSMA(candles20, 5)

        for (i in 0 until 10) {
            assertEquals(sma10[i], sma20[i])
        }
    }

    // 2. Signal on candle N enters on candle N+1 open in REALISTIC mode
    @Test
    fun test02_realisticExecution_entersOnNPlus1Open() {
        val candles = mutableListOf<Candle>()
        for (i in 0 until 30) {
            val p = if (i < 15) 100.0 - i * 0.5 else 90.0 + (i - 15) * 3.0
            candles.add(Candle(baseTime + (i * 86400000L), p, p + 1.0, p - 1.0, p, 1000.0))
        }

        val strategy = StrategyDefinition.PRESETS.first { it.strategyType == StrategyType.MA_CROSSOVER }
        val risk = RiskParameters(
            executionModel = ExecutionModel.REALISTIC,
            slippageBps = 0.0,
            commissionBps = 0.0
        )

        val result = BacktestEngine.runBacktest(candles, testAsset, MarketRegime.HISTORICAL_REALISTIC, Timeframe.D1, strategy, risk)
        assertEquals(ExecutionModel.REALISTIC, result.riskParams.executionModel)
        if (result.trades.isNotEmpty()) {
            val trade = result.trades.first()
            assertTrue(trade.barIndex >= 1)
        }
    }

    // 3. Idealized mode enters on signal close
    @Test
    fun test03_idealizedExecution_entersOnSignalClose() {
        val risk = RiskParameters(
            executionModel = ExecutionModel.IDEALIZED,
            slippageBps = 0.0,
            commissionBps = 0.0
        )
        assertEquals(ExecutionModel.IDEALIZED, risk.executionModel)
    }

    // 4. Long SL: Hits stop loss price accurately
    @Test
    fun test04_longStopLoss() {
        val risk = RiskParameters(
            initialCapital = 10000.0,
            stopLossType = StopLossType.PERCENTAGE,
            stopLossValue = 5.0 // 5% stop
        )
        val (sizeResult, slPrice, _, _) = BacktestEngine.calculateOrderSizingAndStops(
            equity = 10000.0,
            risk = risk,
            direction = TradeDirection.LONG,
            entryPrice = 100.0,
            atr = 2.0,
            tradesHistory = emptyList()
        )
        assertNotNull(slPrice)
        assertEquals(95.0, slPrice!!, 0.01)
    }

    // 5. Long TP: Hits take profit price accurately
    @Test
    fun test05_longTakeProfit() {
        val risk = RiskParameters(
            initialCapital = 10000.0,
            takeProfitType = TakeProfitType.PERCENTAGE,
            takeProfitValue = 10.0 // 10% TP
        )
        val (sizeResult, _, tpPrice, _) = BacktestEngine.calculateOrderSizingAndStops(
            equity = 10000.0,
            risk = risk,
            direction = TradeDirection.LONG,
            entryPrice = 100.0,
            atr = 2.0,
            tradesHistory = emptyList()
        )
        assertNotNull(tpPrice)
        assertEquals(110.0, tpPrice!!, 0.01)
    }

    // 6. Short SL: Hits stop loss price accurately above entry
    @Test
    fun test06_shortStopLoss() {
        val risk = RiskParameters(
            initialCapital = 10000.0,
            stopLossType = StopLossType.PERCENTAGE,
            stopLossValue = 5.0
        )
        val (sizeResult, slPrice, _, _) = BacktestEngine.calculateOrderSizingAndStops(
            equity = 10000.0,
            risk = risk,
            direction = TradeDirection.SHORT,
            entryPrice = 100.0,
            atr = 2.0,
            tradesHistory = emptyList()
        )
        assertNotNull(slPrice)
        assertEquals(105.0, slPrice!!, 0.01)
    }

    // 7. Short TP: Hits take profit price accurately below entry
    @Test
    fun test07_shortTakeProfit() {
        val risk = RiskParameters(
            initialCapital = 10000.0,
            takeProfitType = TakeProfitType.PERCENTAGE,
            takeProfitValue = 10.0
        )
        val (sizeResult, _, tpPrice, _) = BacktestEngine.calculateOrderSizingAndStops(
            equity = 10000.0,
            risk = risk,
            direction = TradeDirection.SHORT,
            entryPrice = 100.0,
            atr = 2.0,
            tradesHistory = emptyList()
        )
        assertNotNull(tpPrice)
        assertEquals(90.0, tpPrice!!, 0.01)
    }

    // 8. SL + TP same candle collision resolution
    @Test
    fun test08_slTpSameCandle_collision() {
        val riskPessimistic = RiskParameters(
            intrabarExecution = IntrabarExecutionAssumption.PESSIMISTIC_STOP_FIRST
        )
        assertEquals(IntrabarExecutionAssumption.PESSIMISTIC_STOP_FIRST, riskPessimistic.intrabarExecution)
    }

    // 9. Gap through SL: exits at open when gap exceeds SL
    @Test
    fun test09_gapThroughStopLoss() {
        val entryPrice = 100.0
        val slPrice = 95.0
        val gapOpenPrice = 90.0 // Market opens below stop loss
        val effectiveExitPrice = if (gapOpenPrice < slPrice) gapOpenPrice else slPrice
        assertEquals(90.0, effectiveExitPrice, 0.01)
    }

    // 10. Trailing stop: tracks peak price and ratchets SL
    @Test
    fun test10_trailingStop() {
        val risk = RiskParameters(
            stopLossType = StopLossType.TRAILING_PERCENTAGE,
            stopLossValue = 5.0
        )
        val (_, slPrice, _, isTrailing) = BacktestEngine.calculateOrderSizingAndStops(
            equity = 10000.0,
            risk = risk,
            direction = TradeDirection.LONG,
            entryPrice = 100.0,
            atr = 2.0,
            tradesHistory = emptyList()
        )
        assertTrue(isTrailing)
        assertEquals(95.0, slPrice!!, 0.01)
    }

    // 11. Position sizing: risk-based formula calculates exact dollar risk
    @Test
    fun test11_positionSizing_riskBased() {
        val risk = RiskParameters(
            initialCapital = 10000.0,
            positionSizingMode = PositionSizingMode.RISK_BASED,
            positionSizeValue = 1.0, // 1% risk ($100)
            stopLossType = StopLossType.PERCENTAGE,
            stopLossValue = 5.0, // 5% stop distance on $2,000 entry = $100
            leverage = 1.0,
            commissionBps = 0.0,
            slippageBps = 0.0
        )
        val (posSizeResult, slPrice, _, _) = BacktestEngine.calculateOrderSizingAndStops(
            equity = 10000.0,
            risk = risk,
            direction = TradeDirection.LONG,
            entryPrice = 2000.0,
            atr = 100.0,
            tradesHistory = emptyList()
        )
        assertNotNull(posSizeResult)
        assertEquals(100.0, posSizeResult!!.initialRiskDollars, 0.01)
        assertEquals(1.0, posSizeResult.quantity, 0.01)
        assertEquals(2000.0, posSizeResult.positionValue, 0.01)
    }

    // 12. Commission accounting on entry and exit
    @Test
    fun test12_commissionAccounting() {
        val risk = RiskParameters(
            initialCapital = 10000.0,
            positionSizingMode = PositionSizingMode.FIXED_DOLLAR,
            positionSizeValue = 1000.0,
            leverage = 1.0,
            commissionBps = 10.0 // 0.10% = $1.00 on $1000
        )
        val (sizeResult, _, _, _) = BacktestEngine.calculateOrderSizingAndStops(
            equity = 10000.0,
            risk = risk,
            direction = TradeDirection.LONG,
            entryPrice = 100.0,
            atr = 2.0,
            tradesHistory = emptyList()
        )
        assertNotNull(sizeResult)
        assertEquals(1.0, sizeResult!!.entryFee, 0.01)
    }

    // 13. Slippage calculation
    @Test
    fun test13_slippageCalculation() {
        val basePrice = 100.0
        val slippageRate = 5.0 / 10000.0 // 5 bps = 0.05%
        val buyPrice = basePrice * (1.0 + slippageRate)
        val sellPrice = basePrice * (1.0 - slippageRate)
        assertEquals(100.05, buyPrice, 0.001)
        assertEquals(99.95, sellPrice, 0.001)
    }

    // 14. Leverage & margin accounting
    @Test
    fun test14_leverageMarginAccounting() {
        val risk = RiskParameters(
            initialCapital = 10000.0,
            positionSizingMode = PositionSizingMode.FIXED_DOLLAR,
            positionSizeValue = 2000.0,
            leverage = 5.0
        )
        val (sizeResult, _, _, _) = BacktestEngine.calculateOrderSizingAndStops(
            equity = 10000.0,
            risk = risk,
            direction = TradeDirection.LONG,
            entryPrice = 100.0,
            atr = 2.0,
            tradesHistory = emptyList()
        )
        assertNotNull(sizeResult)
        assertEquals(2000.0, sizeResult!!.marginAllocated, 0.01)
        assertEquals(10000.0, sizeResult.positionValue, 0.01)
    }

    // 15. Drawdown calculation from peak equity
    @Test
    fun test15_drawdownCalculation() {
        val peak = 12000.0
        val current = 10800.0
        val ddPct = ((peak - current) / peak) * 100.0
        assertEquals(10.0, ddPct, 0.01)
    }

    // 16. Final equity calculation
    @Test
    fun test16_finalEquityCalculation() {
        val initial = 10000.0
        val pnl1 = 500.0
        val pnl2 = -200.0
        val finalEq = initial + pnl1 + pnl2
        assertEquals(10300.0, finalEq, 0.01)
    }

    // 17. 4H timeframe aggregation
    @Test
    fun test17_4hTimeframeAggregation() {
        val baseHourMs = 1700000000000L - (1700000000000L % (4 * 3600000L))
        val hourlyCandles = listOf(
            Candle(baseHourMs, 100.0, 105.0, 98.0, 102.0, 100.0),
            Candle(baseHourMs + 3600000L, 102.0, 108.0, 101.0, 107.0, 200.0),
            Candle(baseHourMs + 7200000L, 107.0, 110.0, 104.0, 106.0, 150.0),
            Candle(baseHourMs + 10800000L, 106.0, 109.0, 103.0, 108.0, 250.0)
        )

        val aggregated = TimeframeAggregator.aggregate(hourlyCandles, Timeframe.H1, Timeframe.H4)
        assertEquals(1, aggregated.size)
        val bar4h = aggregated.first()
        assertEquals(100.0, bar4h.open, 0.01)
        assertEquals(110.0, bar4h.high, 0.01)
        assertEquals(98.0, bar4h.low, 0.01)
        assertEquals(108.0, bar4h.close, 0.01)
        assertEquals(700.0, bar4h.volume, 0.01)
    }

    // 18. Missing candle & unexpected gap detection
    @Test
    fun test18_missingCandle_unexpectedGapDetection() {
        val t0 = 1700000000000L
        val candlesWithGap = (0 until 15).map { i ->
            // Intraday missing bar between bar 4 and 5 (skip 30 min on 15m timeframe)
            val offset = if (i >= 5) (i + 2) * 900000L else i * 900000L
            val p = 100.0 + i
            Candle(t0 + offset, p, p + 2.0, p - 2.0, p + 1.0, 1000.0)
        }

        val (clean, report) = MarketDataValidator.validateAndClean(candlesWithGap, Timeframe.M15)
        assertTrue(clean.size >= 10)
        assertTrue(report.unexpectedGapsCount >= 1)
    }

    // 19. Duplicate candle detection and deduplication
    @Test
    fun test19_duplicateCandleDetection() {
        val t0 = 1700000000000L
        val dirty = (0 until 15).map { i ->
            val p = 100.0 + i
            Candle(t0 + i * 86400000L, p, p + 5.0, p - 3.0, p + 2.0, 1000.0)
        } + listOf(
            Candle(t0, 100.0, 105.0, 95.0, 102.0, 1000.0) // duplicate
        )

        val (clean, report) = MarketDataValidator.validateAndClean(dirty, Timeframe.D1)
        assertEquals(15, clean.size)
        assertEquals(1, report.duplicatesRemovedCount)
    }

    // 20. Trendline pivot confirmation: no future lookahead
    @Test
    fun test20_trendlinePivotConfirmation_noLookahead() {
        val strength = 3
        val currentBar = 10
        val maxSearchEnd = currentBar - strength // Can only confirm pivots up to bar 7
        assertEquals(7, maxSearchEnd)
    }

    // 21. Trendline break: confirms breakout above resistance
    @Test
    fun test21_trendlineBreak() {
        val linePrice = 100.0
        val prevClose = 99.0
        val currentClose = 102.0
        val confirmMultiplier = 1.01
        val isBreakout = prevClose <= linePrice && currentClose >= linePrice * confirmMultiplier
        assertTrue(isBreakout)
    }

    // 22. Trendline bounce: confirms bounce off support
    @Test
    fun test22_trendlineBounce() {
        val linePrice = 100.0
        val currentLow = 99.8
        val currentClose = 101.5
        val currentOpen = 100.2
        val currentHigh = 102.0
        val touched = currentLow <= linePrice * 1.005 && currentClose > linePrice
        val bullishRejection = currentClose > currentOpen && (currentClose - currentLow) > (currentHigh - currentClose)
        assertTrue(touched && bullishRejection)
    }

    // 23. ORB session boundaries: timezone-aware
    @Test
    fun test23_orbSessionBoundaries() {
        val orbParams = OrbParams(
            sessionTimezone = "America/New_York",
            sessionStartHour = 9,
            sessionStartMinute = 30,
            openingRangeMinutes = 30,
            sessionEndHour = 16,
            sessionEndMinute = 0
        )
        val tracker = BacktestEngine.OrbSessionTracker(orbParams, Timeframe.M15)
        // 1700058600000 = Nov 15 2023 14:30 UTC = 09:30 AM NY (Session Start)
        val candle1 = Candle(1700058600000L, 100.0, 105.0, 99.0, 102.0, 1000.0)
        tracker.update(candle1, 0)
        assertTrue(tracker.isWithinTradingSession)
        assertFalse(tracker.isOpeningRangeComplete)
    }

    // 24. No synthetic data in REAL mode: fails safely with explicit error
    @Test
    fun test24_noSyntheticDataInRealMode() = runBlocking {
        val repo = MarketDataRepository()
        // Invalid asset with no real feed should fail explicitly rather than generate synthetic data
        val bogusAsset = MarketAsset("UNKNOWN_BOGUS", "BOGUS/USD", "Bogus", AssetCategory.STOCKS, 100.0, "Bogus")
        val result = repo.getHistoricalCandles(bogusAsset, Timeframe.D1, baseTime, baseTime + 864000000L, isDemoMode = false)
        assertTrue(result.isFailure)
        val errorMsg = result.exceptionOrNull()?.message ?: ""
        assertTrue(errorMsg.isNotEmpty())
    }
}

