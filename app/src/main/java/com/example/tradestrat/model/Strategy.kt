package com.example.tradestrat.model

enum class StrategyType(val title: String, val subtitle: String, val badge: String) {
    MA_CROSSOVER("Moving Average Crossover", "Fast MA cross Slow MA (Golden/Death Cross)", "Trend Following"),
    RSI_MEAN_REVERSION("RSI Mean Reversion", "Oversold bounce entry and overbought exit", "Momentum / Reversal"),
    MACD_MOMENTUM("MACD Signal & Zero Cross", "MACD line crosses Signal line with histogram confirmation", "Trend / Momentum"),
    BOLLINGER_BREAKOUT("Bollinger Band Breakout", "Volatility squeeze & upper/lower band breakout", "Volatility"),
    BOLLINGER_REVERSION("Bollinger Mean Reversion", "Buy lower band touch, take profit at middle/upper band", "Mean Reversion"),
    SUPERTREND_RUN("Supertrend Trend Follower", "ATR-based dynamic trailing trend filter", "Trend"),
    TURTLE_BREAKOUT("Donchian / Turtle Breakout", "20-bar channel high/low breakout system", "Breakout"),
    OPENING_RANGE_BREAKOUT("Opening Range Breakout (ORB)", "Trades breakout of initial session price range with volume surge", "Price Action / Breakout"),
    TRENDLINE_BREAK("Trendline Break", "Enters when price breaks through swing pivot support/resistance trendlines", "Price Action / Breakout"),
    TRENDLINE_BOUNCE("Trendline Bounce", "Buys pullbacks bouncing off support trendlines with rejection candles", "Support & Resistance"),
    MULTI_CONFLUENCE("Multi-Indicator Confluence", "Trend filter + RSI pullback + ATR stop confirmation", "Advanced Confluence"),
    SMC_ICT_CONCEPTS("SMC / ICT Concepts", "Institutional smart money concepts: BOS, CHOCH, FVG, Order Blocks, Liquidity Sweeps", "Institutional Price Action")
}

data class StrategyDefinition(
    val id: String = "ma_cross_default",
    val name: String = "EMA Trend Crossover (9/21)",
    val description: String = "Enters long when Fast EMA 9 crosses above Slow EMA 21; enters short when Fast crosses below Slow.",
    val strategyType: StrategyType = StrategyType.MA_CROSSOVER,
    val indicatorConfig: IndicatorConfig = IndicatorConfig(),
    val isCustom: Boolean = false
) {
    companion object {
        val PRESETS = listOf(
            StrategyDefinition(
                id = "preset_orb_breakout",
                name = "Opening Range Breakout (ORB 15)",
                description = "Captures directional momentum expansion when price breaks above the 15-bar opening range high with volume.",
                strategyType = StrategyType.OPENING_RANGE_BREAKOUT,
                indicatorConfig = IndicatorConfig(
                    orbParams = OrbParams(rangeBars = 15, volumeMultiplier = 1.2)
                )
            ),
            StrategyDefinition(
                id = "preset_trendline_break",
                name = "Pivot Trendline Break",
                description = "Identifies key swing highs/lows and enters on decisive structural breakout through resistance/support trendlines.",
                strategyType = StrategyType.TRENDLINE_BREAK,
                indicatorConfig = IndicatorConfig(
                    trendlineParams = TrendlineParams(pivotLookback = 10, confirmationThresholdPct = 0.3)
                )
            ),
            StrategyDefinition(
                id = "preset_trendline_bounce",
                name = "Support Trendline Bounce",
                description = "Enters on high-probability pullback retests of established support trendlines with bullish candlestick rejection.",
                strategyType = StrategyType.TRENDLINE_BOUNCE,
                indicatorConfig = IndicatorConfig(
                    trendlineParams = TrendlineParams(pivotLookback = 10, confirmationThresholdPct = 0.4)
                )
            ),
            StrategyDefinition(
                id = "preset_ema_cross",
                name = "EMA Golden Cross (9/21)",
                description = "Classic exponential moving average crossover capturing medium-term trends with responsive 9/21 periods.",
                strategyType = StrategyType.MA_CROSSOVER,
                indicatorConfig = IndicatorConfig(
                    maParams = MovingAverageParams(fastPeriod = 9, slowPeriod = 21, useEma = true)
                )
            ),
            StrategyDefinition(
                id = "preset_rsi_mean_rev",
                name = "RSI Swing Reversal (30/70)",
                description = "Buys when RSI drops below oversold threshold (30) and recovers; exits/shorts when RSI exceeds overbought threshold (70).",
                strategyType = StrategyType.RSI_MEAN_REVERSION,
                indicatorConfig = IndicatorConfig(
                    rsiParams = RsiParams(period = 14, oversoldThreshold = 30.0, overboughtThreshold = 70.0)
                )
            ),
            StrategyDefinition(
                id = "preset_macd_trend",
                name = "MACD Momentum (12/26/9)",
                description = "Standard MACD indicator crossing signal line with momentum histogram expansion.",
                strategyType = StrategyType.MACD_MOMENTUM,
                indicatorConfig = IndicatorConfig(
                    macdParams = MacdParams(fastPeriod = 12, slowPeriod = 26, signalPeriod = 9)
                )
            ),
            StrategyDefinition(
                id = "preset_bb_breakout",
                name = "Bollinger Volatility Breakout",
                description = "Rides explosive trend expansion when candles close outside the 2.0σ upper band after a squeeze.",
                strategyType = StrategyType.BOLLINGER_BREAKOUT,
                indicatorConfig = IndicatorConfig(
                    bollingerParams = BollingerParams(period = 20, stdDevMultiplier = 2.0)
                )
            ),
            StrategyDefinition(
                id = "preset_supertrend",
                name = "Supertrend Trend Rider",
                description = "ATR-based dynamic trailing envelope riding sustained directional runs with built-in volatility tracking.",
                strategyType = StrategyType.SUPERTREND_RUN,
                indicatorConfig = IndicatorConfig(
                    supertrendParams = SupertrendParams(atrPeriod = 10, multiplier = 3.0)
                )
            ),
            StrategyDefinition(
                id = "preset_turtle",
                name = "Turtle Donchian Breakout",
                description = "Legendary Richard Dennis Turtle system entering on 20-period price highs/lows.",
                strategyType = StrategyType.TURTLE_BREAKOUT,
                indicatorConfig = IndicatorConfig(
                    donchianParams = DonchianParams(period = 20)
                )
            ),
            StrategyDefinition(
                id = "preset_confluence",
                name = "Triple Indicator Confluence",
                description = "Enters long only when Price > 50 EMA AND RSI pulls back to 40-50 zone AND MACD histogram turns positive.",
                strategyType = StrategyType.MULTI_CONFLUENCE,
                indicatorConfig = IndicatorConfig(
                    maParams = MovingAverageParams(fastPeriod = 20, slowPeriod = 50, useEma = true),
                    rsiParams = RsiParams(period = 14, oversoldThreshold = 45.0, overboughtThreshold = 65.0),
                    macdParams = MacdParams(fastPeriod = 12, slowPeriod = 26, signalPeriod = 9)
                )
            ),
            StrategyDefinition(
                id = "preset_smc_structure_shift",
                name = "SMC: Market Structure Shift (BOS + CHOCH)",
                description = "Trades trend changes on Break of Structure (BOS) and Change of Character (CHOCH / MSS) with confirmed pivot breaks.",
                strategyType = StrategyType.SMC_ICT_CONCEPTS,
                indicatorConfig = IndicatorConfig(
                    smcConfig = SmcConfig(
                        useBos = true,
                        useChoch = true,
                        useLiquiditySweep = false,
                        useFvg = false,
                        useOrderBlock = false,
                        minConfluences = 1
                    )
                )
            ),
            StrategyDefinition(
                id = "preset_smc_liquidity_sweep",
                name = "SMC: Liquidity Sweep & Reversal",
                description = "Identifies false breakouts beyond key swing highs/lows (stop hunts) where price wicks out liquidity and closes back in range.",
                strategyType = StrategyType.SMC_ICT_CONCEPTS,
                indicatorConfig = IndicatorConfig(
                    smcConfig = SmcConfig(
                        useBos = false,
                        useChoch = false,
                        useLiquiditySweep = true,
                        useFvg = false,
                        useOrderBlock = false,
                        sweepLookback = 10,
                        sweepWickMinPct = 0.15,
                        minConfluences = 1
                    )
                )
            ),
            StrategyDefinition(
                id = "preset_smc_fvg_retest",
                name = "SMC: Fair Value Gap (FVG) Retest",
                description = "Captures institutional imbalance retests when price returns to fill 3-candle Fair Value Gaps in the direction of order flow.",
                strategyType = StrategyType.SMC_ICT_CONCEPTS,
                indicatorConfig = IndicatorConfig(
                    smcConfig = SmcConfig(
                        useBos = true,
                        useChoch = false,
                        useLiquiditySweep = false,
                        useFvg = true,
                        useOrderBlock = false,
                        fvgMinGapAtrMultiple = 0.3,
                        fvgMitigationType = FvgMitigationType.TOUCH,
                        minConfluences = 1
                    )
                )
            ),
            StrategyDefinition(
                id = "preset_smc_order_block",
                name = "SMC: Order Block Retest",
                description = "Trades pullback entries into institutional Order Blocks created prior to structural displacement moves.",
                strategyType = StrategyType.SMC_ICT_CONCEPTS,
                indicatorConfig = IndicatorConfig(
                    smcConfig = SmcConfig(
                        useBos = true,
                        useChoch = false,
                        useLiquiditySweep = false,
                        useFvg = false,
                        useOrderBlock = true,
                        obLookback = 15,
                        obMitigationRequired = true,
                        minConfluences = 1
                    )
                )
            ),
            StrategyDefinition(
                id = "preset_smc_ict_full_confluence",
                name = "SMC: Full ICT Multi-Confluence",
                description = "High-conviction setup requiring Session filter + BOS/CHOCH structure alignment + Order Block / FVG mitigation in Discount/Premium.",
                strategyType = StrategyType.SMC_ICT_CONCEPTS,
                indicatorConfig = IndicatorConfig(
                    smcConfig = SmcConfig(
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
                )
            )
        )
    }
}
