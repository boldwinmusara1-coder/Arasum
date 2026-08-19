package com.example.tradestrat.engine

import com.example.tradestrat.model.*
import java.util.UUID
import kotlin.math.*

object BacktestEngine {

    fun runBacktest(
        candles: List<Candle>,
        asset: MarketAsset,
        regime: MarketRegime,
        timeframe: Timeframe,
        strategy: StrategyDefinition,
        risk: RiskParameters,
        dataSourceInfo: DataSourceInfo? = null
    ): BacktestResult {
        if (candles.size < 10) {
            return emptyResult(asset, regime, timeframe, strategy, risk, candles, dataSourceInfo)
        }

        // 1. Calculate all indicators according to strategy config (strictly causal)
        val indicators = precalculateIndicators(candles, strategy)

        // 2. State tracking
        var cash = risk.initialCapital
        var peakEquity = risk.initialCapital
        var maxDrawdownPct = 0.0
        var maxDrawdownDurationBars = 0
        var currentDrawdownDuration = 0

        val trades = mutableListOf<Trade>()
        val equityCurve = ArrayList<EquityPoint>(candles.size)
        val signalMarkers = mutableListOf<SignalMarker>()

        var activePosition: ActivePosition? = null
        val slippageRate = (risk.slippageBps / 10000.0)
        val feeRate = (risk.commissionBps / 10000.0)

        val benchmarkInitialPrice = candles.first().close

        for (i in candles.indices) {
            val candle = candles[i]
            val prevCandle = if (i > 0) candles[i - 1] else candle

            // --- Step A: Check Active Position Exit / Stops ---
            if (activePosition != null) {
                val pos = activePosition!!
                var exitTrade = false
                var exitPrice = 0.0
                var exitReason = ExitReason.SIGNAL_REVERSAL

                // Check Circuit Breaker
                val currentUnrealizedEquity = calculateEquity(cash, pos, candle.close)
                val currentDd = if (peakEquity > 0) ((peakEquity - currentUnrealizedEquity) / peakEquity) * 100.0 else 0.0
                if (currentDd >= risk.maxDrawdownCircuitBreakerPct) {
                    exitTrade = true
                    exitPrice = candle.close * (if (pos.direction == TradeDirection.LONG) (1.0 - slippageRate) else (1.0 + slippageRate))
                    exitReason = ExitReason.CIRCUIT_BREAKER
                }

                // Intra-candle Stop Loss & Take Profit checks with configurable Intrabar Execution
                if (!exitTrade) {
                    if (pos.direction == TradeDirection.LONG) {
                        val slHit = pos.stopLossPrice != null && candle.low <= pos.stopLossPrice!!
                        val tpHit = pos.takeProfitPrice != null && candle.high >= pos.takeProfitPrice!!

                        if (slHit && tpHit) {
                            // Intrabar Collision: Both SL and TP within the same candle
                            val slFirst = when (risk.intrabarExecution) {
                                IntrabarExecutionAssumption.PESSIMISTIC_STOP_FIRST -> true
                                IntrabarExecutionAssumption.BAR_DIRECTION -> {
                                    // If price open was closer to low than to high, assume low was touched first
                                    abs(candle.open - candle.low) <= abs(candle.open - candle.high)
                                }
                                IntrabarExecutionAssumption.OPTIMISTIC_TP_FIRST -> false
                            }

                            if (slFirst) {
                                exitTrade = true
                                exitPrice = pos.stopLossPrice!! * (1.0 - slippageRate)
                                exitReason = if (pos.isTrailingStop) ExitReason.TRAILING_STOP else ExitReason.STOP_LOSS
                            } else {
                                exitTrade = true
                                exitPrice = pos.takeProfitPrice!! * (1.0 - slippageRate)
                                exitReason = ExitReason.TAKE_PROFIT
                            }
                        } else if (slHit) {
                            exitTrade = true
                            exitPrice = pos.stopLossPrice!! * (1.0 - slippageRate)
                            exitReason = if (pos.isTrailingStop) ExitReason.TRAILING_STOP else ExitReason.STOP_LOSS
                        } else if (tpHit) {
                            exitTrade = true
                            exitPrice = pos.takeProfitPrice!! * (1.0 - slippageRate)
                            exitReason = ExitReason.TAKE_PROFIT
                        } else if (pos.isTrailingStop) {
                            if (candle.high > pos.trailingPeakPrice) {
                                pos.trailingPeakPrice = candle.high
                                if (risk.stopLossType == StopLossType.TRAILING_PERCENTAGE) {
                                    pos.stopLossPrice = pos.trailingPeakPrice * (1.0 - (risk.stopLossValue / 100.0))
                                } else if (risk.stopLossType == StopLossType.TRAILING_ATR) {
                                    val atrVal = indicators.atr.getOrNull(i) ?: (candle.close * 0.02)
                                    pos.stopLossPrice = pos.trailingPeakPrice - (risk.stopLossValue * atrVal)
                                }
                            }
                        }
                    } else { // SHORT position
                        val slHit = pos.stopLossPrice != null && candle.high >= pos.stopLossPrice!!
                        val tpHit = pos.takeProfitPrice != null && candle.low <= pos.takeProfitPrice!!

                        if (slHit && tpHit) {
                            // Intrabar Collision: Both SL and TP within the same candle
                            val slFirst = when (risk.intrabarExecution) {
                                IntrabarExecutionAssumption.PESSIMISTIC_STOP_FIRST -> true
                                IntrabarExecutionAssumption.BAR_DIRECTION -> {
                                    abs(candle.open - candle.high) <= abs(candle.open - candle.low)
                                }
                                IntrabarExecutionAssumption.OPTIMISTIC_TP_FIRST -> false
                            }

                            if (slFirst) {
                                exitTrade = true
                                exitPrice = pos.stopLossPrice!! * (1.0 + slippageRate)
                                exitReason = if (pos.isTrailingStop) ExitReason.TRAILING_STOP else ExitReason.STOP_LOSS
                            } else {
                                exitTrade = true
                                exitPrice = pos.takeProfitPrice!! * (1.0 + slippageRate)
                                exitReason = ExitReason.TAKE_PROFIT
                            }
                        } else if (slHit) {
                            exitTrade = true
                            exitPrice = pos.stopLossPrice!! * (1.0 + slippageRate)
                            exitReason = if (pos.isTrailingStop) ExitReason.TRAILING_STOP else ExitReason.STOP_LOSS
                        } else if (tpHit) {
                            exitTrade = true
                            exitPrice = pos.takeProfitPrice!! * (1.0 + slippageRate)
                            exitReason = ExitReason.TAKE_PROFIT
                        } else if (pos.isTrailingStop) {
                            if (candle.low < pos.trailingPeakPrice) {
                                pos.trailingPeakPrice = candle.low
                                if (risk.stopLossType == StopLossType.TRAILING_PERCENTAGE) {
                                    pos.stopLossPrice = pos.trailingPeakPrice * (1.0 + (risk.stopLossValue / 100.0))
                                } else if (risk.stopLossType == StopLossType.TRAILING_ATR) {
                                    val atrVal = indicators.atr.getOrNull(i) ?: (candle.close * 0.02)
                                    pos.stopLossPrice = pos.trailingPeakPrice + (risk.stopLossValue * atrVal)
                                }
                            }
                        }
                    }
                }

                // Update runup and drawdown of open position
                val unrealizedPct = if (pos.direction == TradeDirection.LONG) {
                    ((candle.close - pos.entryPrice) / pos.entryPrice) * 100.0
                } else {
                    ((pos.entryPrice - candle.close) / pos.entryPrice) * 100.0
                }
                pos.maxRunUpPct = max(pos.maxRunUpPct, unrealizedPct)
                pos.maxDrawdownPct = min(pos.maxDrawdownPct, unrealizedPct)

                if (exitTrade) {
                    val closedTrade = closePosition(
                        pos, exitPrice, candle.timestamp, i, exitReason, feeRate, risk.initialCapital
                    )
                    cash += closedTrade.positionValue + closedTrade.pnlDollars
                    trades.add(closedTrade)
                    signalMarkers.add(
                        SignalMarker(
                            barIndex = i,
                            timestamp = candle.timestamp,
                            price = exitPrice,
                            direction = pos.direction,
                            isEntry = false,
                            exitReason = exitReason
                        )
                    )
                    activePosition = null
                }
            }

            // --- Step B: Strategy Signal Evaluation ---
            val (longSignal, shortSignal) = evaluateSignal(strategy, i, candles, indicators)

            // Process Exit/Reversal on Signal
            if (activePosition != null) {
                val pos = activePosition!!
                if ((pos.direction == TradeDirection.LONG && shortSignal) ||
                    (pos.direction == TradeDirection.SHORT && longSignal)
                ) {
                    val exitPrice = if (pos.direction == TradeDirection.LONG) {
                        candle.close * (1.0 - slippageRate)
                    } else {
                        candle.close * (1.0 + slippageRate)
                    }
                    val closedTrade = closePosition(
                        pos, exitPrice, candle.timestamp, i, ExitReason.SIGNAL_REVERSAL, feeRate, risk.initialCapital
                    )
                    cash += closedTrade.positionValue + closedTrade.pnlDollars
                    trades.add(closedTrade)
                    signalMarkers.add(
                        SignalMarker(
                            barIndex = i,
                            timestamp = candle.timestamp,
                            price = exitPrice,
                            direction = pos.direction,
                            isEntry = false,
                            exitReason = ExitReason.SIGNAL_REVERSAL
                        )
                    )
                    activePosition = null
                }
            }

            // Process Entry on Signal (if no active position)
            if (activePosition == null && (longSignal || (shortSignal && risk.allowShorting))) {
                val direction = if (longSignal) TradeDirection.LONG else TradeDirection.SHORT
                val entryPrice = if (direction == TradeDirection.LONG) {
                    candle.close * (1.0 + slippageRate)
                } else {
                    candle.close * (1.0 - slippageRate)
                }

                val currentEquity = cash
                val currentAtr = indicators.atr.getOrNull(i) ?: (candle.close * 0.02)

                // Calculate Position Sizing
                val posSizeDollars = calculatePositionSize(currentEquity, risk, entryPrice, currentAtr, trades)
                val leveragedValue = posSizeDollars * risk.leverage

                if (posSizeDollars > 50.0 && cash >= (posSizeDollars * 0.95)) {
                    val quantity = leveragedValue / entryPrice
                    val entryFee = leveragedValue * feeRate
                    cash -= posSizeDollars + entryFee

                    // Calculate Initial Stops & Targets
                    val (slPrice, tpPrice, isTrailing) = calculateStopsAndTargets(
                        direction, entryPrice, currentAtr, risk
                    )

                    activePosition = ActivePosition(
                        direction = direction,
                        entryBarIndex = i,
                        entryTimestamp = candle.timestamp,
                        entryPrice = entryPrice,
                        quantity = quantity,
                        positionMarginValue = posSizeDollars,
                        stopLossPrice = slPrice,
                        takeProfitPrice = tpPrice,
                        isTrailingStop = isTrailing,
                        trailingPeakPrice = entryPrice,
                        entryFeePaid = entryFee,
                        initialRiskDollars = if (slPrice != null) abs(entryPrice - slPrice) * quantity else posSizeDollars * 0.03
                    )

                    signalMarkers.add(
                        SignalMarker(
                            barIndex = i,
                            timestamp = candle.timestamp,
                            price = entryPrice,
                            direction = direction,
                            isEntry = true
                        )
                    )
                }
            }

            // --- Step C: Update Mark-To-Market Equity Curve ---
            val barEquity = calculateEquity(cash, activePosition, candle.close)
            if (barEquity > peakEquity) {
                peakEquity = barEquity
                currentDrawdownDuration = 0
            } else {
                currentDrawdownDuration++
                maxDrawdownDurationBars = max(maxDrawdownDurationBars, currentDrawdownDuration)
            }

            val currentDdPct = if (peakEquity > 0) ((peakEquity - barEquity) / peakEquity) * 100.0 else 0.0
            maxDrawdownPct = max(maxDrawdownPct, currentDdPct)

            val benchmarkEquity = (candle.close / benchmarkInitialPrice) * risk.initialCapital

            equityCurve.add(
                EquityPoint(
                    barIndex = i,
                    timestamp = candle.timestamp,
                    equity = barEquity,
                    cash = cash,
                    drawdownPct = currentDdPct,
                    benchmarkEquity = benchmarkEquity,
                    price = candle.close
                )
            )
        }

        // Close any remaining active position at final bar
        if (activePosition != null) {
            val lastCandle = candles.last()
            val pos = activePosition!!
            val exitPrice = lastCandle.close * (if (pos.direction == TradeDirection.LONG) (1.0 - slippageRate) else (1.0 + slippageRate))
            val closedTrade = closePosition(
                pos, exitPrice, lastCandle.timestamp, candles.size - 1, ExitReason.END_OF_DATA, feeRate, risk.initialCapital
            )
            cash += closedTrade.positionValue + closedTrade.pnlDollars
            trades.add(closedTrade)
            activePosition = null
        }

        // 3. Compute Quantitative Metrics
        val metrics = computeMetrics(risk.initialCapital, equityCurve, trades, candles, maxDrawdownPct, maxDrawdownDurationBars)

        val dsInfo = dataSourceInfo ?: DataSourceInfo(
            provider = "Real Market Data API",
            symbol = asset.symbol,
            market = asset.category.label,
            timeframe = timeframe.label,
            startDate = if (candles.isNotEmpty()) candles.first().formattedDate(timeframe.minutes) else "",
            endDate = if (candles.isNotEmpty()) candles.last().formattedDate(timeframe.minutes) else "",
            startTimestamp = candles.firstOrNull()?.timestamp ?: 0L,
            endTimestamp = candles.lastOrNull()?.timestamp ?: 0L,
            candleCount = candles.size,
            isRealHistorical = true,
            intrabarExecutionRule = risk.intrabarExecution.label
        )

        return BacktestResult(
            id = UUID.randomUUID().toString(),
            asset = asset,
            regime = regime,
            timeframe = timeframe,
            strategy = strategy,
            riskParams = risk,
            candles = candles,
            indicators = indicators,
            trades = trades,
            equityCurve = equityCurve,
            signalMarkers = signalMarkers,
            metrics = metrics,
            dataSource = dsInfo
        )
    }

    private data class ActivePosition(
        val direction: TradeDirection,
        val entryBarIndex: Int,
        val entryTimestamp: Long,
        val entryPrice: Double,
        val quantity: Double,
        val positionMarginValue: Double,
        var stopLossPrice: Double?,
        var takeProfitPrice: Double?,
        val isTrailingStop: Boolean,
        var trailingPeakPrice: Double,
        val entryFeePaid: Double,
        val initialRiskDollars: Double,
        var maxRunUpPct: Double = 0.0,
        var maxDrawdownPct: Double = 0.0
    )

    private fun calculateEquity(cash: Double, pos: ActivePosition?, currentPrice: Double): Double {
        if (pos == null) return cash
        val pnl = if (pos.direction == TradeDirection.LONG) {
            (currentPrice - pos.entryPrice) * pos.quantity
        } else {
            (pos.entryPrice - currentPrice) * pos.quantity
        }
        return cash + pos.positionMarginValue + pnl
    }

    private fun closePosition(
        pos: ActivePosition,
        exitPrice: Double,
        exitTimestamp: Long,
        exitBarIndex: Int,
        reason: ExitReason,
        feeRate: Double,
        initialCapital: Double
    ): Trade {
        val grossPnl = if (pos.direction == TradeDirection.LONG) {
            (exitPrice - pos.entryPrice) * pos.quantity
        } else {
            (pos.entryPrice - exitPrice) * pos.quantity
        }
        val exitFee = (exitPrice * pos.quantity) * feeRate
        val totalFees = pos.entryFeePaid + exitFee
        val netPnlDollars = grossPnl - totalFees
        val netPnlPercent = if (pos.positionMarginValue > 0) (netPnlDollars / pos.positionMarginValue) * 100.0 else 0.0
        val rMultiple = if (pos.initialRiskDollars > 0) netPnlDollars / pos.initialRiskDollars else 0.0

        return Trade(
            id = UUID.randomUUID().toString().take(8),
            barIndex = pos.entryBarIndex,
            exitBarIndex = exitBarIndex,
            entryTimestamp = pos.entryTimestamp,
            exitTimestamp = exitTimestamp,
            direction = pos.direction,
            entryPrice = pos.entryPrice,
            exitPrice = exitPrice,
            quantity = pos.quantity,
            positionValue = pos.positionMarginValue,
            pnlDollars = netPnlDollars,
            pnlPercent = netPnlPercent,
            exitReason = reason,
            feesPaid = totalFees,
            rMultiple = rMultiple,
            holdingBars = max(1, exitBarIndex - pos.entryBarIndex),
            maxRunUpPct = pos.maxRunUpPct,
            maxDrawdownPct = pos.maxDrawdownPct
        )
    }

    private fun calculatePositionSize(
        equity: Double,
        risk: RiskParameters,
        entryPrice: Double,
        atr: Double,
        tradesHistory: List<Trade>
    ): Double {
        return when (risk.positionSizingMode) {
            PositionSizingMode.PERCENT_EQUITY -> {
                val pct = (risk.positionSizeValue / 100.0).coerceIn(0.05, 1.0)
                equity * pct
            }
            PositionSizingMode.FIXED_DOLLAR -> {
                min(equity * 0.95, risk.positionSizeValue)
            }
            PositionSizingMode.RISK_BASED -> {
                // Risk X% of account based on stop loss distance
                val riskDollars = equity * (risk.positionSizeValue / 100.0).coerceIn(0.005, 0.10)
                val stopDistance = if (risk.stopLossType == StopLossType.PERCENTAGE) {
                    entryPrice * (risk.stopLossValue / 100.0)
                } else if (risk.stopLossType == StopLossType.ATR_MULTIPLE) {
                    atr * risk.stopLossValue
                } else {
                    entryPrice * 0.03
                }
                if (stopDistance > 0) {
                    val size = (riskDollars / stopDistance) * entryPrice
                    min(equity * 0.95, max(50.0, size))
                } else {
                    equity * 0.25
                }
            }
            PositionSizingMode.KELLY_CRITERION -> {
                if (tradesHistory.size >= 10) {
                    val wins = tradesHistory.filter { it.isWin }
                    val winRate = wins.size.toDouble() / tradesHistory.size
                    val avgWin = if (wins.isNotEmpty()) wins.map { it.pnlDollars }.average() else 1.0
                    val losses = tradesHistory.filter { !it.isWin }
                    val avgLoss = if (losses.isNotEmpty()) abs(losses.map { it.pnlDollars }.average()) else 1.0
                    val b = if (avgLoss > 0) avgWin / avgLoss else 1.0
                    val kelly = if (b > 0) (winRate * (b + 1.0) - 1.0) / b else 0.1
                    val halfKelly = (kelly / 2.0).coerceIn(0.05, 0.50)
                    equity * halfKelly
                } else {
                    equity * 0.20
                }
            }
        }
    }

    private fun calculateStopsAndTargets(
        direction: TradeDirection,
        entryPrice: Double,
        atr: Double,
        risk: RiskParameters
    ): Triple<Double?, Double?, Boolean> {
        var slPrice: Double? = null
        var isTrailing = false

        when (risk.stopLossType) {
            StopLossType.NONE -> slPrice = null
            StopLossType.PERCENTAGE -> {
                val offset = entryPrice * (risk.stopLossValue / 100.0)
                slPrice = if (direction == TradeDirection.LONG) entryPrice - offset else entryPrice + offset
            }
            StopLossType.ATR_MULTIPLE -> {
                val offset = atr * risk.stopLossValue
                slPrice = if (direction == TradeDirection.LONG) entryPrice - offset else entryPrice + offset
            }
            StopLossType.TRAILING_PERCENTAGE -> {
                val offset = entryPrice * (risk.stopLossValue / 100.0)
                slPrice = if (direction == TradeDirection.LONG) entryPrice - offset else entryPrice + offset
                isTrailing = true
            }
            StopLossType.TRAILING_ATR -> {
                val offset = atr * risk.stopLossValue
                slPrice = if (direction == TradeDirection.LONG) entryPrice - offset else entryPrice + offset
                isTrailing = true
            }
        }

        var tpPrice: Double? = null
        when (risk.takeProfitType) {
            TakeProfitType.NONE -> tpPrice = null
            TakeProfitType.PERCENTAGE -> {
                val offset = entryPrice * (risk.takeProfitValue / 100.0)
                tpPrice = if (direction == TradeDirection.LONG) entryPrice + offset else entryPrice - offset
            }
            TakeProfitType.RISK_REWARD_RATIO -> {
                if (slPrice != null) {
                    val slDistance = abs(entryPrice - slPrice)
                    val tpDistance = slDistance * risk.takeProfitValue
                    tpPrice = if (direction == TradeDirection.LONG) entryPrice + tpDistance else entryPrice - tpDistance
                } else {
                    val offset = entryPrice * 0.05
                    tpPrice = if (direction == TradeDirection.LONG) entryPrice + offset else entryPrice - offset
                }
            }
            TakeProfitType.ATR_MULTIPLE -> {
                val offset = atr * risk.takeProfitValue
                tpPrice = if (direction == TradeDirection.LONG) entryPrice + offset else entryPrice - offset
            }
        }

        return Triple(slPrice, tpPrice, isTrailing)
    }

    private fun precalculateIndicators(candles: List<Candle>, strategy: StrategyDefinition): CalculatedIndicators {
        val cfg = strategy.indicatorConfig

        val fastMa = when {
            cfg.orbParams.useEmaTrendFilter -> IndicatorCalculators.calculateEMA(candles, cfg.orbParams.emaTrendPeriod)
            cfg.maParams.useEma -> IndicatorCalculators.calculateEMA(candles, cfg.maParams.fastPeriod)
            else -> IndicatorCalculators.calculateSMA(candles, cfg.maParams.fastPeriod)
        }

        val slowMa = when {
            cfg.trendlineParams.useMaTrendFilter -> IndicatorCalculators.calculateEMA(candles, cfg.trendlineParams.maTrendPeriod)
            cfg.maParams.useEma -> IndicatorCalculators.calculateEMA(candles, cfg.maParams.slowPeriod)
            else -> IndicatorCalculators.calculateSMA(candles, cfg.maParams.slowPeriod)
        }

        val rsiPeriod = when {
            cfg.orbParams.useRsiFilter -> cfg.orbParams.rsiPeriod
            cfg.trendlineParams.useRsiFilter -> cfg.trendlineParams.rsiPeriod
            else -> cfg.rsiParams.period
        }
        val rsi = IndicatorCalculators.calculateRSI(candles, rsiPeriod)

        val macd = IndicatorCalculators.calculateMACD(
            candles,
            cfg.macdParams.fastPeriod,
            cfg.macdParams.slowPeriod,
            cfg.macdParams.signalPeriod
        )

        val bb = IndicatorCalculators.calculateBollingerBands(
            candles,
            cfg.bollingerParams.period,
            cfg.bollingerParams.stdDevMultiplier
        )

        val atr = IndicatorCalculators.calculateATR(candles, cfg.supertrendParams.atrPeriod)
        val supertrend = IndicatorCalculators.calculateSupertrend(
            candles,
            cfg.supertrendParams.atrPeriod,
            cfg.supertrendParams.multiplier
        )

        val donchian = IndicatorCalculators.calculateDonchian(candles, cfg.donchianParams.period)

        return CalculatedIndicators(
            fastMa = fastMa,
            slowMa = slowMa,
            rsi = rsi,
            macdLine = macd.macdLine,
            macdSignal = macd.signalLine,
            macdHist = macd.histogram,
            bbUpper = bb.upper,
            bbMiddle = bb.middle,
            bbLower = bb.lower,
            atr = atr,
            supertrend = supertrend,
            donchianUpper = donchian.upper,
            donchianLower = donchian.lower
        )
    }

    private fun evaluateSignal(
        strategy: StrategyDefinition,
        i: Int,
        candles: List<Candle>,
        ind: CalculatedIndicators
    ): Pair<Boolean, Boolean> {
        if (i < 2) return Pair(false, false)

        val current = candles[i]
        val prev = candles[i - 1]

        when (strategy.strategyType) {
            StrategyType.MA_CROSSOVER -> {
                val fastCurr = ind.fastMa.getOrNull(i) ?: return Pair(false, false)
                val fastPrev = ind.fastMa.getOrNull(i - 1) ?: return Pair(false, false)
                val slowCurr = ind.slowMa.getOrNull(i) ?: return Pair(false, false)
                val slowPrev = ind.slowMa.getOrNull(i - 1) ?: return Pair(false, false)

                val longSignal = fastPrev <= slowPrev && fastCurr > slowCurr
                val shortSignal = fastPrev >= slowPrev && fastCurr < slowCurr
                return Pair(longSignal, shortSignal)
            }

            StrategyType.RSI_MEAN_REVERSION -> {
                val rsiCurr = ind.rsi.getOrNull(i) ?: return Pair(false, false)
                val rsiPrev = ind.rsi.getOrNull(i - 1) ?: return Pair(false, false)
                val oversold = strategy.indicatorConfig.rsiParams.oversoldThreshold
                val overbought = strategy.indicatorConfig.rsiParams.overboughtThreshold

                val longSignal = rsiPrev <= oversold && rsiCurr > oversold
                val shortSignal = rsiPrev >= overbought && rsiCurr < overbought
                return Pair(longSignal, shortSignal)
            }

            StrategyType.MACD_MOMENTUM -> {
                val macdCurr = ind.macdLine.getOrNull(i) ?: return Pair(false, false)
                val macdPrev = ind.macdLine.getOrNull(i - 1) ?: return Pair(false, false)
                val sigCurr = ind.macdSignal.getOrNull(i) ?: return Pair(false, false)
                val sigPrev = ind.macdSignal.getOrNull(i - 1) ?: return Pair(false, false)

                val longSignal = macdPrev <= sigPrev && macdCurr > sigCurr
                val shortSignal = macdPrev >= sigPrev && macdCurr < sigCurr
                return Pair(longSignal, shortSignal)
            }

            StrategyType.BOLLINGER_REVERSION -> {
                val bbLowerCurr = ind.bbLower.getOrNull(i) ?: return Pair(false, false)
                val bbUpperCurr = ind.bbUpper.getOrNull(i) ?: return Pair(false, false)
                val bbMidCurr = ind.bbMiddle.getOrNull(i) ?: return Pair(false, false)

                val longSignal = prev.low < bbLowerCurr && current.close > bbLowerCurr
                val shortSignal = prev.high > bbUpperCurr && current.close < bbUpperCurr
                return Pair(longSignal, shortSignal)
            }

            StrategyType.BOLLINGER_BREAKOUT -> {
                val bbUpperCurr = ind.bbUpper.getOrNull(i) ?: return Pair(false, false)
                val bbUpperPrev = ind.bbUpper.getOrNull(i - 1) ?: return Pair(false, false)
                val bbLowerCurr = ind.bbLower.getOrNull(i) ?: return Pair(false, false)
                val bbLowerPrev = ind.bbLower.getOrNull(i - 1) ?: return Pair(false, false)

                val longSignal = prev.close <= bbUpperPrev && current.close > bbUpperCurr
                val shortSignal = prev.close >= bbLowerPrev && current.close < bbLowerCurr
                return Pair(longSignal, shortSignal)
            }

            StrategyType.SUPERTREND_RUN -> {
                val stCurr = ind.supertrend.getOrNull(i) ?: return Pair(false, false)
                val stPrev = ind.supertrend.getOrNull(i - 1) ?: return Pair(false, false)

                val longSignal = prev.close <= stPrev && current.close > stCurr
                val shortSignal = prev.close >= stPrev && current.close < stCurr
                return Pair(longSignal, shortSignal)
            }

            StrategyType.TURTLE_BREAKOUT -> {
                val upperPrev = ind.donchianUpper.getOrNull(i - 1) ?: return Pair(false, false)
                val lowerPrev = ind.donchianLower.getOrNull(i - 1) ?: return Pair(false, false)

                val longSignal = current.high > upperPrev
                val shortSignal = current.low < lowerPrev
                return Pair(longSignal, shortSignal)
            }

            StrategyType.OPENING_RANGE_BREAKOUT -> {
                val rangeBars = strategy.indicatorConfig.orbParams.rangeBars.coerceIn(5, 30)
                val sessionLength = 60
                val sessionIdx = i % sessionLength
                if (sessionIdx < rangeBars || i < rangeBars) return Pair(false, false)

                val sessionStart = i - sessionIdx
                val openingCandles = candles.subList(sessionStart, sessionStart + rangeBars)
                val orbHigh = openingCandles.maxOf { it.high }
                val orbLow = openingCandles.minOf { it.low }

                val recentVolAvg = candles.subList((i - 5).coerceAtLeast(0), i).map { it.volume }.average().coerceAtLeast(1.0)
                val volThreshold = recentVolAvg * strategy.indicatorConfig.orbParams.volumeMultiplier

                var longSignal = prev.close <= orbHigh && current.close > orbHigh && current.volume >= volThreshold
                var shortSignal = prev.close >= orbLow && current.close < orbLow && current.volume >= volThreshold

                // EMA Filter
                if (strategy.indicatorConfig.orbParams.useEmaTrendFilter) {
                    val ema = ind.fastMa.getOrNull(i) ?: current.close
                    longSignal = longSignal && current.close > ema
                    shortSignal = shortSignal && current.close < ema
                }

                // RSI Filter
                if (strategy.indicatorConfig.orbParams.useRsiFilter) {
                    val rsi = ind.rsi.getOrNull(i) ?: 50.0
                    val thresh = strategy.indicatorConfig.orbParams.rsiThreshold
                    longSignal = longSignal && rsi >= thresh
                    shortSignal = shortSignal && rsi <= (100.0 - thresh)
                }

                return Pair(longSignal, shortSignal)
            }

            StrategyType.TRENDLINE_BREAK -> {
                val lookback = strategy.indicatorConfig.trendlineParams.pivotLookback.coerceIn(5, 25)
                if (i < lookback * 3) return Pair(false, false)

                // Detect swing pivot highs and lows
                val p1High = candles.subList(i - lookback * 2, i - lookback).maxByOrNull { it.high } ?: return Pair(false, false)
                val p2High = candles.subList(i - lookback, i).maxByOrNull { it.high } ?: return Pair(false, false)
                val idx1High = candles.indexOf(p1High)
                val idx2High = candles.indexOf(p2High)

                val p1Low = candles.subList(i - lookback * 2, i - lookback).minByOrNull { it.low } ?: return Pair(false, false)
                val p2Low = candles.subList(i - lookback, i).minByOrNull { it.low } ?: return Pair(false, false)
                val idx1Low = candles.indexOf(p1Low)
                val idx2Low = candles.indexOf(p2Low)

                var longSignal = false
                if (idx2High > idx1High) {
                    val slope = (p2High.high - p1High.high) / (idx2High - idx1High)
                    val tlPrice = p2High.high + slope * (i - idx2High)
                    val confirm = 1.0 + (strategy.indicatorConfig.trendlineParams.confirmationThresholdPct / 100.0)
                    longSignal = prev.close <= tlPrice && current.close >= tlPrice * confirm
                }

                var shortSignal = false
                if (idx2Low > idx1Low) {
                    val slope = (p2Low.low - p1Low.low) / (idx2Low - idx1Low)
                    val tlPrice = p2Low.low + slope * (i - idx2Low)
                    val confirm = 1.0 - (strategy.indicatorConfig.trendlineParams.confirmationThresholdPct / 100.0)
                    shortSignal = prev.close >= tlPrice && current.close <= tlPrice * confirm
                }

                return Pair(longSignal, shortSignal)
            }

            StrategyType.TRENDLINE_BOUNCE -> {
                val lookback = strategy.indicatorConfig.trendlineParams.pivotLookback.coerceIn(5, 25)
                if (i < lookback * 3) return Pair(false, false)

                val p1Low = candles.subList(i - lookback * 2, i - lookback).minByOrNull { it.low } ?: return Pair(false, false)
                val p2Low = candles.subList(i - lookback, i).minByOrNull { it.low } ?: return Pair(false, false)
                val idx1Low = candles.indexOf(p1Low)
                val idx2Low = candles.indexOf(p2Low)

                val p1High = candles.subList(i - lookback * 2, i - lookback).maxByOrNull { it.high } ?: return Pair(false, false)
                val p2High = candles.subList(i - lookback, i).maxByOrNull { it.high } ?: return Pair(false, false)
                val idx1High = candles.indexOf(p1High)
                val idx2High = candles.indexOf(p2High)

                var longSignal = false
                if (idx2Low > idx1Low) {
                    val slope = (p2Low.low - p1Low.low) / (idx2Low - idx1Low)
                    val tlPrice = p2Low.low + slope * (i - idx2Low)
                    // Bounce off ascending support: touched line and closed green above line
                    val touchedLine = current.low <= tlPrice * 1.003
                    val closedAbove = current.close > tlPrice && current.close > current.open
                    val bullishHammer = (current.close - current.low) > (current.high - current.close)
                    var valid = touchedLine && closedAbove && bullishHammer

                    // MA Trend Filter
                    if (valid && strategy.indicatorConfig.trendlineParams.useMaTrendFilter) {
                        val ma = ind.slowMa.getOrNull(i) ?: current.close
                        valid = current.close >= ma * 0.98
                    }

                    // RSI Oversold Filter
                    if (valid && strategy.indicatorConfig.trendlineParams.useRsiFilter) {
                        val rsi = ind.rsi.getOrNull(i) ?: 50.0
                        valid = rsi <= strategy.indicatorConfig.trendlineParams.rsiOversoldThreshold + 15.0
                    }

                    longSignal = valid
                }

                var shortSignal = false
                if (idx2High > idx1High) {
                    val slope = (p2High.high - p1High.high) / (idx2High - idx1High)
                    val tlPrice = p2High.high + slope * (i - idx2High)
                    // Bounce off descending resistance: touched line and closed red below line
                    val touchedLine = current.high >= tlPrice * 0.997
                    val closedBelow = current.close < tlPrice && current.close < current.open
                    val bearishRejection = (current.high - current.close) > (current.close - current.low)
                    var valid = touchedLine && closedBelow && bearishRejection

                    // MA Trend Filter
                    if (valid && strategy.indicatorConfig.trendlineParams.useMaTrendFilter) {
                        val ma = ind.slowMa.getOrNull(i) ?: current.close
                        valid = current.close <= ma * 1.02
                    }

                    // RSI Overbought Filter
                    if (valid && strategy.indicatorConfig.trendlineParams.useRsiFilter) {
                        val rsi = ind.rsi.getOrNull(i) ?: 50.0
                        valid = rsi >= strategy.indicatorConfig.trendlineParams.rsiOverboughtThreshold - 15.0
                    }

                    shortSignal = valid
                }

                return Pair(longSignal, shortSignal)
            }

            StrategyType.MULTI_CONFLUENCE -> {
                val slowMa = ind.slowMa.getOrNull(i) ?: return Pair(false, false)
                val rsiCurr = ind.rsi.getOrNull(i) ?: return Pair(false, false)
                val histCurr = ind.macdHist.getOrNull(i) ?: return Pair(false, false)
                val histPrev = ind.macdHist.getOrNull(i - 1) ?: return Pair(false, false)

                val trendBullish = current.close > slowMa
                val rsiBullish = rsiCurr in 40.0..60.0
                val macdTurningUp = histCurr > histPrev && histCurr > 0

                val longSignal = trendBullish && rsiBullish && macdTurningUp

                val trendBearish = current.close < slowMa
                val rsiBearish = rsiCurr in 50.0..70.0
                val macdTurningDown = histCurr < histPrev && histCurr < 0

                val shortSignal = trendBearish && rsiBearish && macdTurningDown
                return Pair(longSignal, shortSignal)
            }
        }
    }

    private fun computeMetrics(
        initialCapital: Double,
        equityCurve: List<EquityPoint>,
        trades: List<Trade>,
        candles: List<Candle>,
        maxDrawdownPct: Double,
        maxDrawdownDurationBars: Int
    ): BacktestMetrics {
        val finalEquity = equityCurve.lastOrNull()?.equity ?: initialCapital
        val netProfitDollars = finalEquity - initialCapital
        val netProfitPercent = (netProfitDollars / initialCapital) * 100.0

        val initialBenchmark = candles.firstOrNull()?.close ?: 1.0
        val finalBenchmark = candles.lastOrNull()?.close ?: 1.0
        val benchmarkReturnPercent = if (initialBenchmark > 0) ((finalBenchmark - initialBenchmark) / initialBenchmark) * 100.0 else 0.0
        val alphaPercent = netProfitPercent - benchmarkReturnPercent

        val totalTrades = trades.size
        val winningTradesList = trades.filter { it.isWin }
        val losingTradesList = trades.filter { !it.isWin }
        val winningTrades = winningTradesList.size
        val losingTrades = losingTradesList.size
        val winRatePercent = if (totalTrades > 0) (winningTrades.toDouble() / totalTrades) * 100.0 else 0.0

        val grossProfits = winningTradesList.sumOf { it.pnlDollars }
        val grossLosses = abs(losingTradesList.sumOf { it.pnlDollars })
        val profitFactor = if (grossLosses > 0.0) grossProfits / grossLosses else if (grossProfits > 0) 99.99 else 0.0

        val avgWinDollars = if (winningTrades > 0) grossProfits / winningTrades else 0.0
        val avgLossDollars = if (losingTrades > 0) grossLosses / losingTrades else 1.0
        val payoffRatio = if (avgLossDollars > 0) avgWinDollars / avgLossDollars else 0.0

        val avgTradePercent = if (totalTrades > 0) trades.map { it.pnlPercent }.average() else 0.0
        val avgWinningTradePercent = if (winningTrades > 0) winningTradesList.map { it.pnlPercent }.average() else 0.0
        val avgLosingTradePercent = if (losingTrades > 0) losingTradesList.map { it.pnlPercent }.average() else 0.0

        val largestWinningTradeDollars = winningTradesList.maxOfOrNull { it.pnlDollars } ?: 0.0
        val largestLosingTradeDollars = losingTradesList.minOfOrNull { it.pnlDollars } ?: 0.0

        var maxConsWins = 0
        var currentConsWins = 0
        var maxConsLosses = 0
        var currentConsLosses = 0

        for (t in trades) {
            if (t.isWin) {
                currentConsWins++
                currentConsLosses = 0
                maxConsWins = max(maxConsWins, currentConsWins)
            } else {
                currentConsLosses++
                currentConsWins = 0
                maxConsLosses = max(maxConsLosses, currentConsLosses)
            }
        }

        val totalFeesPaid = trades.sumOf { it.feesPaid }
        val avgHoldingBars = if (totalTrades > 0) trades.map { it.holdingBars }.average() else 0.0

        // Expectancy = (WinRate * AvgWin) - (LossRate * AvgLoss)
        val winProb = if (totalTrades > 0) winningTrades.toDouble() / totalTrades else 0.0
        val lossProb = 1.0 - winProb
        val expectancyDollars = (winProb * avgWinDollars) - (lossProb * avgLossDollars)
        val expectancyR = if (totalTrades > 0) trades.map { it.rMultiple }.average() else 0.0

        // Daily / Bar returns for Sharpe & Sortino
        val barReturns = mutableListOf<Double>()
        for (k in 1 until equityCurve.size) {
            val prevEq = equityCurve[k - 1].equity
            val currEq = equityCurve[k].equity
            if (prevEq > 0) {
                barReturns.add((currEq - prevEq) / prevEq)
            }
        }

        val meanBarReturn = if (barReturns.isNotEmpty()) barReturns.average() else 0.0
        val variance = if (barReturns.size > 1) {
            barReturns.sumOf { (it - meanBarReturn) * (it - meanBarReturn) } / (barReturns.size - 1)
        } else 0.0
        val stdDev = sqrt(variance)

        val downsideVariance = if (barReturns.size > 1) {
            barReturns.filter { it < 0.0 }.sumOf { it * it } / barReturns.size
        } else 0.0
        val downsideStdDev = sqrt(downsideVariance)

        val barsPerYear = 252.0 // standard trading days per year
        val annualizedReturn = meanBarReturn * barsPerYear
        val annualizedStdDev = stdDev * sqrt(barsPerYear)
        val annualizedDownsideStdDev = downsideStdDev * sqrt(barsPerYear)

        val riskFreeRate = 0.04 // 4% risk-free rate
        val sharpeRatio = if (annualizedStdDev > 0) (annualizedReturn - riskFreeRate) / annualizedStdDev else 0.0
        val sortinoRatio = if (annualizedDownsideStdDev > 0) (annualizedReturn - riskFreeRate) / annualizedDownsideStdDev else 0.0

        val yearsElapsed = max(0.1, candles.size.toDouble() / barsPerYear)
        val cagrPercent = (Math.pow(max(0.01, finalEquity / initialCapital), 1.0 / yearsElapsed) - 1.0) * 100.0
        val calmarRatio = if (maxDrawdownPct > 0) (cagrPercent / maxDrawdownPct) else 0.0

        return BacktestMetrics(
            initialCapital = initialCapital,
            finalEquity = finalEquity,
            netProfitDollars = netProfitDollars,
            netProfitPercent = netProfitPercent,
            benchmarkReturnPercent = benchmarkReturnPercent,
            alphaPercent = alphaPercent,
            cagrPercent = cagrPercent,
            maxDrawdownPercent = maxDrawdownPct,
            maxDrawdownDurationBars = maxDrawdownDurationBars,
            sharpeRatio = sharpeRatio,
            sortinoRatio = sortinoRatio,
            calmarRatio = calmarRatio,
            totalTrades = totalTrades,
            winningTrades = winningTrades,
            losingTrades = losingTrades,
            winRatePercent = winRatePercent,
            profitFactor = profitFactor,
            payoffRatio = payoffRatio,
            avgTradePercent = avgTradePercent,
            avgWinningTradePercent = avgWinningTradePercent,
            avgLosingTradePercent = avgLosingTradePercent,
            avgWinDollars = avgWinDollars,
            avgLossDollars = avgLossDollars,
            avgRMultiple = expectancyR,
            largestWinningTradeDollars = largestWinningTradeDollars,
            largestLosingTradeDollars = largestLosingTradeDollars,
            maxConsecutiveWins = maxConsWins,
            maxConsecutiveLosses = maxConsLosses,
            totalFeesPaid = totalFeesPaid,
            avgHoldingBars = avgHoldingBars,
            expectancyDollars = expectancyDollars,
            expectancyR = expectancyR
        )
    }

    private fun emptyResult(
        asset: MarketAsset,
        regime: MarketRegime,
        timeframe: Timeframe,
        strategy: StrategyDefinition,
        risk: RiskParameters,
        candles: List<Candle>,
        dataSourceInfo: DataSourceInfo? = null
    ): BacktestResult {
        val dsInfo = dataSourceInfo ?: DataSourceInfo(
            provider = "No Data",
            symbol = asset.symbol,
            market = asset.category.label,
            timeframe = timeframe.label,
            startDate = "",
            endDate = "",
            startTimestamp = 0L,
            endTimestamp = 0L,
            candleCount = candles.size,
            isRealHistorical = true,
            validationStatus = "EMPTY_OR_INSUFFICIENT_DATA"
        )
        return BacktestResult(
            id = UUID.randomUUID().toString(),
            asset = asset,
            regime = regime,
            timeframe = timeframe,
            strategy = strategy,
            riskParams = risk,
            candles = candles,
            indicators = CalculatedIndicators(),
            trades = emptyList(),
            equityCurve = emptyList(),
            signalMarkers = emptyList(),
            metrics = BacktestMetrics(
                initialCapital = risk.initialCapital,
                finalEquity = risk.initialCapital,
                netProfitDollars = 0.0,
                netProfitPercent = 0.0,
                benchmarkReturnPercent = 0.0,
                alphaPercent = 0.0,
                cagrPercent = 0.0,
                maxDrawdownPercent = 0.0,
                maxDrawdownDurationBars = 0,
                sharpeRatio = 0.0,
                sortinoRatio = 0.0,
                calmarRatio = 0.0,
                totalTrades = 0,
                winningTrades = 0,
                losingTrades = 0,
                winRatePercent = 0.0,
                profitFactor = 0.0,
                payoffRatio = 0.0,
                avgTradePercent = 0.0,
                avgWinningTradePercent = 0.0,
                avgLosingTradePercent = 0.0,
                avgWinDollars = 0.0,
                avgLossDollars = 0.0,
                avgRMultiple = 0.0,
                largestWinningTradeDollars = 0.0,
                largestLosingTradeDollars = 0.0,
                maxConsecutiveWins = 0,
                maxConsecutiveLosses = 0,
                totalFeesPaid = 0.0,
                avgHoldingBars = 0.0,
                expectancyDollars = 0.0,
                expectancyR = 0.0
            ),
            dataSource = dsInfo
        )
    }
}
