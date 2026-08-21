package com.example

import com.example.tradestrat.data.HistoricalDataGenerator
import com.example.tradestrat.engine.StrategyComparisonEngine
import com.example.tradestrat.model.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class StrategyComparisonEngineTest {

    private lateinit var sampleCandles: List<Candle>
    private val asset = MarketAsset.BTC_USDT
    private val regime = MarketRegime.BULL_TRENDING
    private val tf = Timeframe.M15
    private val risk = RiskParameters(
        initialCapital = 10000.0,
        positionSizingMode = PositionSizingMode.FIXED_FRACTIONAL,
        positionSizeValue = 25.0,
        stopLossPercent = 3.0,
        takeProfitPercent = 6.0,
        leverage = 1.0,
        commissionBps = 5.0,
        slippageBps = 2.0
    )

    @Before
    fun setUp() {
        sampleCandles = HistoricalDataGenerator.generateCandles(
            asset = asset,
            regime = regime,
            timeframe = tf,
            days = 60,
            seed = 42L
        )
    }

    @Test
    fun testEmptyStrategySelection() {
        val result = StrategyComparisonEngine.runComparison(
            strategies = emptyList(),
            candles = sampleCandles,
            asset = asset,
            regime = regime,
            timeframe = tf,
            risk = risk
        )

        assertFalse("Empty strategies must fail validation", result.validation.isValid)
        assertTrue("Must contain empty strategy error", result.validation.validationErrors.any { it.contains("No strategies selected") })
        assertTrue("Result items must be empty", result.items.isEmpty())
        assertTrue("Monthly matrix must be empty", result.monthlyMatrix.isEmpty())
    }

    @Test
    fun testSingleStrategyComparison() {
        val strat = StrategyDefinition.PRESETS.first { it.id == "preset_orb_standard" }
        val result = StrategyComparisonEngine.runComparison(
            strategies = listOf(strat),
            candles = sampleCandles,
            asset = asset,
            regime = regime,
            timeframe = tf,
            risk = risk
        )

        assertTrue("Single strategy comparison should be valid", result.validation.isValid)
        assertEquals(1, result.items.size)
        assertEquals(strat.id, result.items[0].strategy.id)

        // Verify equity normalization starts at $10,000
        val normCurve = result.items[0].normalizedEquityCurve
        assertTrue(normCurve.isNotEmpty())
        assertEquals(10000.0, normCurve.first().normalizedEquity, 0.01)

        // Verify single strategy ranking
        val rankings = StrategyRankingCalculator.calculateRankings(result.items)
        assertEquals(1, rankings.size)
        assertEquals(1, rankings[0].rank)
        assertEquals(100.0, rankings[0].compositeScore, 0.01)
    }

    @Test
    fun testMultipleStrategyComparison() {
        val selectedStrats = listOf(
            StrategyDefinition.PRESETS.first { it.id == "preset_smc_ict" },
            StrategyDefinition.PRESETS.first { it.id == "preset_orb_standard" },
            StrategyDefinition.PRESETS.first { it.id == "preset_orb_defensive" },
            StrategyDefinition.PRESETS.first { it.id == "preset_ema_crossover" }
        )

        val result = StrategyComparisonEngine.runComparison(
            strategies = selectedStrats,
            candles = sampleCandles,
            asset = asset,
            regime = regime,
            timeframe = tf,
            risk = risk
        )

        assertTrue("Multi-strategy comparison should be valid", result.validation.isValid)
        assertEquals(4, result.items.size)

        // Verify strategy isolation
        val itemIds = result.items.map { it.strategy.id }
        assertEquals(selectedStrats.map { it.id }, itemIds)

        // Verify all strategies share identical candle counts and risk params
        result.items.forEach { item ->
            assertEquals(sampleCandles.size, item.result.candles.size)
            assertEquals(risk.initialCapital, item.result.riskParams.initialCapital, 0.001)
            assertEquals(risk.commissionBps, item.result.riskParams.commissionBps, 0.001)
            assertEquals(risk.slippageBps, item.result.riskParams.slippageBps, 0.001)
            assertEquals(10000.0, item.normalizedEquityCurve.first().normalizedEquity, 0.01)
        }

        // Verify monthly matrix aggregation
        assertNotNull(result.monthlyMatrix)
        if (result.monthlyMatrix.isNotEmpty()) {
            result.monthlyMatrix.forEach { row ->
                assertEquals(4, row.strategyPnl.size)
                assertEquals(4, row.strategyRoi.size)
            }
        }

        // Verify Rankings
        val rankings = StrategyRankingCalculator.calculateRankings(result.items)
        assertEquals(4, rankings.size)
        assertEquals(1, rankings[0].rank)
        assertEquals(4, rankings[3].rank)
        assertTrue(rankings[0].compositeScore >= rankings[1].compositeScore)
        assertTrue(rankings[1].compositeScore >= rankings[2].compositeScore)
    }

    @Test
    fun testIdenticalConfigurationEnforcementAndInvalidComparisonDetection() {
        val strat1 = StrategyDefinition.PRESETS.first { it.id == "preset_orb_standard" }
        val strat2 = StrategyDefinition.PRESETS.first { it.id == "preset_smc_ict" }

        // Manually run backtests with mismatched parameters to test validator
        val res1 = com.example.tradestrat.engine.BacktestEngine.runBacktest(
            candles = sampleCandles,
            asset = MarketAsset.BTC_USDT,
            regime = regime,
            timeframe = Timeframe.M15,
            strategy = strat1,
            risk = risk.copy(initialCapital = 10000.0)
        )

        val res2MismatchedCapital = com.example.tradestrat.engine.BacktestEngine.runBacktest(
            candles = sampleCandles,
            asset = MarketAsset.BTC_USDT,
            regime = regime,
            timeframe = Timeframe.M15,
            strategy = strat2,
            risk = risk.copy(initialCapital = 50000.0) // Mismatch
        )

        val validation = StrategyComparisonEngine.validateFairComparison(listOf(res1, res2MismatchedCapital))
        assertFalse("Mismatched capital must fail fair comparison validation", validation.isValid)
        assertTrue(validation.validationErrors.any { it.contains("initial capital mismatch") })

        val res3MismatchedAsset = com.example.tradestrat.engine.BacktestEngine.runBacktest(
            candles = sampleCandles,
            asset = MarketAsset.EUR_USD, // Mismatch
            regime = regime,
            timeframe = Timeframe.M15,
            strategy = strat2,
            risk = risk
        )

        val validationAsset = StrategyComparisonEngine.validateFairComparison(listOf(res1, res3MismatchedAsset))
        assertFalse("Mismatched asset must fail fair comparison validation", validationAsset.isValid)
        assertTrue(validationAsset.validationErrors.any { it.contains("evaluated asset") })
    }

    @Test
    fun testTradeDistributionAndPercentilesCalculation() {
        val strat = StrategyDefinition.PRESETS.first { it.id == "preset_smc_ict" }
        val result = StrategyComparisonEngine.runComparison(
            strategies = listOf(strat),
            candles = sampleCandles,
            asset = asset,
            regime = regime,
            timeframe = tf,
            risk = risk
        )

        val item = result.items.first()
        val dist = item.distribution

        assertNotNull(dist)
        if (item.result.trades.isNotEmpty()) {
            assertTrue(dist.largestWinnerDollars >= 0.0)
            assertTrue(dist.largestLoserDollars <= 0.0)
            assertTrue(dist.p75TradeDollars >= dist.p25TradeDollars)
            assertEquals(dist.p75TradeDollars - dist.p25TradeDollars, dist.interquartileRangeDollars, 0.001)
        }
    }
}
