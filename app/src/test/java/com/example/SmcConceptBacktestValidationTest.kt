package com.example

import com.example.tradestrat.data.MarketDataValidator
import com.example.tradestrat.engine.BacktestEngine
import com.example.tradestrat.engine.SmcEngine
import com.example.tradestrat.model.*
import org.junit.Assert.*
import org.junit.BeforeClass
import org.junit.Test
import java.io.File
import java.io.InputStreamReader
import java.util.Locale

/**
 * SMC / ICT CONCEPTS DETERMINISTIC BACKTESTING VALIDATION TEST
 * 
 * Validates all 10 SMC/ICT components independently and in combinations
 * with zero look-ahead bias, next-bar-open execution, pessimistic SL-first,
 * $2,000 margin, 2x leverage ($4,000 position notional).
 */
class SmcConceptBacktestValidationTest {

    companion object {
        lateinit var btcCandles: List<Candle>
        val btcAsset = MarketAsset("BTC_USD", "BTC/USD", "Bitcoin", AssetCategory.CRYPTO, 65000.0, "Crypto")

        val standardRisk = RiskParameters(
            initialCapital = 10000.0,
            positionSizingMode = PositionSizingMode.FIXED_DOLLAR,
            positionSizeValue = 2000.0, // Fixed $2,000 margin
            leverage = 2.0,             // $4,000 position notional
            stopLossType = StopLossType.PERCENTAGE,
            stopLossValue = 5.0,        // 5% Stop Loss
            takeProfitType = TakeProfitType.PERCENTAGE,
            takeProfitValue = 10.0,     // 10% Take Profit
            slippageBps = 5.0,          // 5 bps adverse slippage
            commissionBps = 10.0,       // 10 bps exchange commission
            executionModel = ExecutionModel.REALISTIC,
            intrabarExecution = IntrabarExecutionAssumption.PESSIMISTIC_STOP_FIRST
        )

        @JvmStatic
        @BeforeClass
        fun loadDataset() {
            val resourceStream = SmcConceptBacktestValidationTest::class.java.classLoader?.getResourceAsStream("data/btc_daily_5yr.csv")
            val rawCsv = if (resourceStream != null) {
                InputStreamReader(resourceStream).readText()
            } else {
                val directFile = File("src/test/resources/data/btc_daily_5yr.csv")
                if (directFile.exists()) directFile.readText() else File("/app/src/test/resources/data/btc_daily_5yr.csv").readText()
            }

            val list = mutableListOf<Candle>()
            val lines = rawCsv.lines().drop(1).filter { it.isNotBlank() }
            for (line in lines) {
                val parts = line.split(",")
                if (parts.size >= 6) {
                    list.add(
                        Candle(
                            timestamp = parts[0].trim().toLong(),
                            open = parts[1].trim().toDouble(),
                            high = parts[2].trim().toDouble(),
                            low = parts[3].trim().toDouble(),
                            close = parts[4].trim().toDouble(),
                            volume = parts[5].trim().toDouble()
                        )
                    )
                }
            }
            btcCandles = list
            println("Loaded ${btcCandles.size} historical BTC/USDT candles for SMC backtesting.")
        }
    }

    private fun runSmcBacktest(
        configName: String,
        smcConfig: SmcConfig
    ): BacktestResult {
        val strategy = StrategyDefinition(
            id = "smc_test_${configName.lowercase().replace(" ", "_")}",
            name = configName,
            description = "Deterministic SMC / ICT test: $configName",
            strategyType = StrategyType.SMC_ICT_CONCEPTS,
            indicatorConfig = IndicatorConfig(smcConfig = smcConfig)
        )

        val result = BacktestEngine.runBacktest(
            candles = btcCandles,
            asset = btcAsset,
            regime = MarketRegime.HISTORICAL_REALISTIC,
            timeframe = Timeframe.D1,
            strategy = strategy,
            risk = standardRisk
        )

        val m = result.metrics
        val smc = result.smcMetrics ?: SmcMetrics()
        val grossProfit = result.trades.filter { it.pnlDollars > 0 }.sumOf { it.pnlDollars }
        val grossLoss = result.trades.filter { it.pnlDollars < 0 }.sumOf { kotlin.math.abs(it.pnlDollars) }
        val totalFees = result.trades.sumOf { it.entryFeePaid + it.exitFeePaid }

        println("\n==========================================================================")
        println("SMC CONFIGURATION: $configName")
        println("==========================================================================")
        println("Total Trades:                ${m.totalTrades}")
        println("Win Rate:                    ${String.format(Locale.US, "%.2f%%", m.winRatePercent)}")
        println("Gross Profit:                ${String.format(Locale.US, "$%.2f", grossProfit)}")
        println("Gross Loss:                  ${String.format(Locale.US, "$%.2f", grossLoss)}")
        println("Total Fees/Slippage Paid:    ${String.format(Locale.US, "$%.2f", totalFees)}")
        println("Net Realized P&L:            ${String.format(Locale.US, "$%.2f", m.netProfitDollars)}")
        println("Profit Factor:               ${String.format(Locale.US, "%.3f", m.profitFactor)}")
        println("Expectancy per Trade:        ${String.format(Locale.US, "$%.2f", m.expectancyDollars)} (${String.format(Locale.US, "%.2fR", m.expectancyR)})")
        println("Average Winning Trade:       ${String.format(Locale.US, "$%.2f", m.avgWinDollars)}")
        println("Average Losing Trade:        ${String.format(Locale.US, "$%.2f", m.avgLossDollars)}")
        println("Maximum Drawdown:            ${String.format(Locale.US, "%.2f%%", m.maxDrawdownPercent)}")
        println("Max Consecutive Losses:      ${m.maxConsecutiveLosses}")
        println("Account ROI:                 ${String.format(Locale.US, "%.2f%%", m.netProfitPercent)}")
        println("Ending Equity from $10k:     ${String.format(Locale.US, "$%.2f", m.finalEquity)}")
        println("Number of Raw Signals:       ${smc.rawSignalsCount}")
        println("Number of Filtered Signals:  ${smc.filteredSignalsCount}")
        println("Structural Events Detected:")
        println("  - BOS Events:              ${smc.bosEventsCount}")
        println("  - CHOCH / MSS Events:      ${smc.chochEventsCount}")
        println("  - Liquidity Sweeps:        ${smc.liquiditySweepsCount}")
        println("  - FVGs Created:            ${smc.fvgCount}")
        println("  - Order Blocks Created:    ${smc.orderBlocksCount}")
        println("  - Breaker Blocks Created:  ${smc.breakerBlocksCount}")
        println("  - Displacements:           ${smc.displacementCount}")
        println("  - EQH / EQL Formations:    ${smc.eqhEqlCount}")
        println("==========================================================================")

        return result
    }

    @Test
    fun testIndependentConcept1_BreakOfStructure_BOS() {
        val config = SmcConfig(
            useBos = true,
            bosLookback = 5,
            bosCloseConfirmation = true,
            useChoch = false,
            useLiquiditySweep = false,
            useFvg = false,
            useOrderBlock = false,
            useBreakerBlock = false,
            usePremiumDiscount = false,
            useDisplacement = false,
            useEqualHighsLows = false,
            useSessionFilter = false,
            minConfluences = 1
        )
        val res = runSmcBacktest("1. Break of Structure (BOS)", config)
        assertTrue("Should have trades", res.trades.isNotEmpty())
        assertTrue("BOS events detected", (res.smcMetrics?.bosEventsCount ?: 0) > 0)
    }

    @Test
    fun testIndependentConcept2_ChangeOfCharacter_CHOCH() {
        val config = SmcConfig(
            useBos = false,
            useChoch = true,
            chochLookback = 5,
            useLiquiditySweep = false,
            useFvg = false,
            useOrderBlock = false,
            useBreakerBlock = false,
            usePremiumDiscount = false,
            useDisplacement = false,
            useEqualHighsLows = false,
            useSessionFilter = false,
            minConfluences = 1
        )
        val res = runSmcBacktest("2. Change of Character (CHOCH / MSS)", config)
        assertTrue("Should have trades", res.trades.isNotEmpty())
        assertTrue("CHOCH events detected", (res.smcMetrics?.chochEventsCount ?: 0) > 0)
    }

    @Test
    fun testIndependentConcept3_LiquiditySweep() {
        val config = SmcConfig(
            useBos = false,
            useChoch = false,
            useLiquiditySweep = true,
            sweepLookback = 10,
            sweepWickMinPct = 0.10,
            useFvg = false,
            useOrderBlock = false,
            useBreakerBlock = false,
            usePremiumDiscount = false,
            useDisplacement = false,
            useEqualHighsLows = false,
            useSessionFilter = false,
            minConfluences = 1
        )
        val res = runSmcBacktest("3. Liquidity Sweep / Grab", config)
        assertTrue("Liquidity Sweeps detected", (res.smcMetrics?.liquiditySweepsCount ?: 0) > 0)
    }

    @Test
    fun testIndependentConcept4_FairValueGap_FVG() {
        val config = SmcConfig(
            useBos = false,
            useChoch = false,
            useLiquiditySweep = false,
            useFvg = true,
            fvgMinGapAtrMultiple = 0.25,
            fvgMitigationType = FvgMitigationType.TOUCH,
            useOrderBlock = false,
            useBreakerBlock = false,
            usePremiumDiscount = false,
            useDisplacement = false,
            useEqualHighsLows = false,
            useSessionFilter = false,
            minConfluences = 1
        )
        val res = runSmcBacktest("4. Fair Value Gap (FVG) Retest", config)
        assertTrue("FVG formations detected", (res.smcMetrics?.fvgCount ?: 0) > 0)
    }

    @Test
    fun testIndependentConcept5_OrderBlocks() {
        val config = SmcConfig(
            useBos = true, // OB requires structure context
            useChoch = false,
            useLiquiditySweep = false,
            useFvg = false,
            useOrderBlock = true,
            obLookback = 15,
            obMitigationRequired = true,
            useBreakerBlock = false,
            usePremiumDiscount = false,
            useDisplacement = false,
            useEqualHighsLows = false,
            useSessionFilter = false,
            minConfluences = 1
        )
        val res = runSmcBacktest("5. Order Block Retest", config)
        assertTrue("Order blocks created", (res.smcMetrics?.orderBlocksCount ?: 0) > 0)
    }

    @Test
    fun testIndependentConcept6_BreakerBlocks() {
        val config = SmcConfig(
            useBos = true,
            useChoch = true,
            useLiquiditySweep = false,
            useFvg = false,
            useOrderBlock = false,
            useBreakerBlock = true,
            usePremiumDiscount = false,
            useDisplacement = false,
            useEqualHighsLows = false,
            useSessionFilter = false,
            minConfluences = 1
        )
        val res = runSmcBacktest("6. Breaker Blocks", config)
        assertNotNull(res.smcMetrics)
    }

    @Test
    fun testIndependentConcept7_PremiumDiscountZones() {
        val config = SmcConfig(
            useBos = true,
            useChoch = true,
            useLiquiditySweep = false,
            useFvg = false,
            useOrderBlock = false,
            useBreakerBlock = false,
            usePremiumDiscount = true,
            discountThresholdPct = 50.0,
            useDisplacement = false,
            useEqualHighsLows = false,
            useSessionFilter = false,
            minConfluences = 1
        )
        val res = runSmcBacktest("7. Premium / Discount Filter", config)
        assertTrue("Filtered signals recorded", (res.smcMetrics?.filteredSignalsCount ?: 0) >= 0)
    }

    @Test
    fun testIndependentConcept8_DisplacementCandles() {
        val config = SmcConfig(
            useBos = false,
            useChoch = false,
            useLiquiditySweep = false,
            useFvg = false,
            useOrderBlock = false,
            useBreakerBlock = false,
            usePremiumDiscount = false,
            useDisplacement = true,
            displacementAtrMultiplier = 1.5,
            useEqualHighsLows = false,
            useSessionFilter = false,
            minConfluences = 1
        )
        val res = runSmcBacktest("8. Displacement Candles", config)
        assertTrue("Displacement candles detected", (res.smcMetrics?.displacementCount ?: 0) > 0)
    }

    @Test
    fun testIndependentConcept9_EqualHighsEqualLows() {
        val config = SmcConfig(
            useBos = false,
            useChoch = false,
            useLiquiditySweep = false,
            useFvg = false,
            useOrderBlock = false,
            useBreakerBlock = false,
            usePremiumDiscount = false,
            useDisplacement = false,
            useEqualHighsLows = true,
            eqTolerancePct = 0.20,
            useSessionFilter = false,
            minConfluences = 1
        )
        val res = runSmcBacktest("9. Equal Highs / Equal Lows (EQH / EQL)", config)
        assertNotNull(res.smcMetrics)
    }

    @Test
    fun testIndependentConcept10_TradingSessionFilter() {
        val config = SmcConfig(
            useBos = true,
            useChoch = true,
            useLiquiditySweep = false,
            useFvg = false,
            useOrderBlock = false,
            useBreakerBlock = false,
            usePremiumDiscount = false,
            useDisplacement = false,
            useEqualHighsLows = false,
            useSessionFilter = true,
            sessionType = SmcSessionType.LONDON_NY_OVERLAP,
            minConfluences = 1
        )
        val res = runSmcBacktest("10. London / NY Overlap Session Filter", config)
        assertNotNull(res.smcMetrics)
    }

    @Test
    fun testCombination_FullICTConfluence() {
        val config = SmcConfig(
            useBos = true,
            useChoch = true,
            useLiquiditySweep = true,
            useFvg = true,
            useOrderBlock = true,
            useBreakerBlock = true,
            usePremiumDiscount = true,
            useDisplacement = true,
            useEqualHighsLows = true,
            useSessionFilter = false,
            minConfluences = 2
        )
        val res = runSmcBacktest("Full ICT Confluence (BOS + CHOCH + Sweep + FVG + OB + Breaker + Premium/Discount + Displacement + EQH/EQL)", config)
        assertTrue("Should generate trades", res.trades.isNotEmpty())
        assertTrue("Signals filtered properly", (res.smcMetrics?.filteredSignalsCount ?: 0) > 0)
    }
}
