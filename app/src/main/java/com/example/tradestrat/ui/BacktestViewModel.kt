package com.example.tradestrat.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.tradestrat.data.MarketDataRepository
import com.example.tradestrat.data.MarketDataProvider
import com.example.tradestrat.data.db.AppDatabase
import com.example.tradestrat.data.db.BacktestRepository
import com.example.tradestrat.data.db.SavedBacktestEntity
import com.example.tradestrat.engine.*
import com.example.tradestrat.model.*
import com.example.tradestrat.ui.components.DateRangePreset
import com.example.tradestrat.ui.components.ProviderSelection
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

class BacktestViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = BacktestRepository(AppDatabase.getDatabase(application))
    private val marketDataRepo = MarketDataRepository()

    // UI Configuration States
    private val _selectedAsset = MutableStateFlow(MarketDataProvider.ASSETS.first())
    val selectedAsset = _selectedAsset.asStateFlow()

    private val _selectedRegime = MutableStateFlow(MarketRegime.HISTORICAL_REALISTIC)
    val selectedRegime = _selectedRegime.asStateFlow()

    private val _selectedTimeframe = MutableStateFlow(Timeframe.D1)
    val selectedTimeframe = _selectedTimeframe.asStateFlow()

    private val _selectedStrategy = MutableStateFlow(StrategyDefinition.PRESETS.first())
    val selectedStrategy = _selectedStrategy.asStateFlow()

    private val _riskParameters = MutableStateFlow(RiskParameters())
    val riskParameters = _riskParameters.asStateFlow()

    // Real Historical Data Feed States
    private val _selectedProvider = MutableStateFlow(ProviderSelection.AUTO)
    val selectedProvider = _selectedProvider.asStateFlow()

    private val _selectedDatePreset = MutableStateFlow(DateRangePreset.DAYS_180)
    val selectedDatePreset = _selectedDatePreset.asStateFlow()

    private val _dataFetchError = MutableStateFlow<String?>(null)
    val dataFetchError = _dataFetchError.asStateFlow()

    private val _dataSourceInfo = MutableStateFlow<DataSourceInfo?>(null)
    val dataSourceInfo = _dataSourceInfo.asStateFlow()

    private val _apiKey = MutableStateFlow<String?>(null)
    val apiKey = _apiKey.asStateFlow()

    // Execution Outputs
    private val _currentResult = MutableStateFlow<BacktestResult?>(null)
    val currentResult = _currentResult.asStateFlow()

    private val _isBacktesting = MutableStateFlow(false)
    val isBacktesting = _isBacktesting.asStateFlow()

    private val _backtestProgress = MutableStateFlow(BacktestProgress())
    val backtestProgress = _backtestProgress.asStateFlow()

    private var backtestJob: Job? = null

    // Optimization & Regime Stress Test
    private val _optimizationResults = MutableStateFlow<List<OptimizationResult>>(emptyList())
    val optimizationResults = _optimizationResults.asStateFlow()

    private val _isOptimizing = MutableStateFlow(false)
    val isOptimizing = _isOptimizing.asStateFlow()

    private var optimizationJob: Job? = null

    private val _regimeComparison = MutableStateFlow<List<RegimeComparisonResult>>(emptyList())
    val regimeComparison = _regimeComparison.asStateFlow()

    private val _isComparingRegimes = MutableStateFlow(false)
    val isComparingRegimes = _isComparingRegimes.asStateFlow()

    private val _healthScorecard = MutableStateFlow<StrategyHealthScorecard?>(null)
    val healthScorecard = _healthScorecard.asStateFlow()

    // Strategy Lab (Comparison on Identical Dataset)
    private val _strategyLabItems = MutableStateFlow<List<StrategyLabItem>>(emptyList())
    val strategyLabItems = _strategyLabItems.asStateFlow()

    private val _isStrategyLabRunning = MutableStateFlow(false)
    val isStrategyLabRunning = _isStrategyLabRunning.asStateFlow()

    // Market Search & Favorites
    private val _favoriteSymbols = MutableStateFlow<Set<String>>(setOf("BTCUSDT", "EURUSD", "SPY"))
    val favoriteSymbols = _favoriteSymbols.asStateFlow()

    private val _recentSymbols = MutableStateFlow<List<String>>(listOf("BTCUSDT", "ETHUSDT", "EURUSD", "AAPL"))
    val recentSymbols = _recentSymbols.asStateFlow()

    // Trade Inspection & Detail View
    private val _selectedTradeForDetail = MutableStateFlow<Trade?>(null)
    val selectedTradeForDetail = _selectedTradeForDetail.asStateFlow()

    // Trade Journal
    private val _journalEntries = MutableStateFlow<List<JournalEntry>>(emptyList())
    val journalEntries = _journalEntries.asStateFlow()

    // Multi-Timeframe Workspace
    private val _isMtfEnabled = MutableStateFlow(false)
    val isMtfEnabled = _isMtfEnabled.asStateFlow()

    private val _mtfConfirmationTimeframe = MutableStateFlow(Timeframe.M15)
    val mtfConfirmationTimeframe = _mtfConfirmationTimeframe.asStateFlow()

    private val _mtfConfirmationCandles = MutableStateFlow<List<Candle>>(emptyList())
    val mtfConfirmationCandles = _mtfConfirmationCandles.asStateFlow()

    // Historical Replay & Manual Trading Engine
    private val _isReplayActive = MutableStateFlow(false)
    val isReplayActive = _isReplayActive.asStateFlow()

    private val _replayAllCandles = MutableStateFlow<List<Candle>>(emptyList())
    private val _replayCurrentIndex = MutableStateFlow(0)
    val replayCurrentIndex = _replayCurrentIndex.asStateFlow()

    private val _replaySpeed = MutableStateFlow(1.0f) // 0.25x, 0.5x, 1x, 2x, 5x
    val replaySpeed = _replaySpeed.asStateFlow()

    private val _isReplayPlaying = MutableStateFlow(false)
    val isReplayPlaying = _isReplayPlaying.asStateFlow()

    private val _activeManualPosition = MutableStateFlow<ManualReplayPosition?>(null)
    val activeManualPosition = _activeManualPosition.asStateFlow()

    private val _manualReplayTrades = MutableStateFlow<List<Trade>>(emptyList())
    val manualReplayTrades = _manualReplayTrades.asStateFlow()

    private var replayTimerJob: Job? = null

    // Database Flows
    val savedStrategies: StateFlow<List<StrategyDefinition>> = repository.savedStrategies
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val savedBacktests: StateFlow<List<SavedBacktestEntity>> = repository.savedBacktests
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Derived Analytics Flows
    val sessionAnalytics: StateFlow<List<SessionAnalytics>> = _currentResult.map { result ->
        if (result == null || result.trades.isEmpty()) emptyList()
        else computeSessionAnalytics(result.trades)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val maeMfeDistribution: StateFlow<MaeMfeDistribution?> = _currentResult.map { result ->
        if (result == null || result.trades.isEmpty()) null
        else computeMaeMfe(result.trades)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    init {
        // Run initial real backtest immediately on startup
        runBacktest()
    }

    fun setAsset(asset: MarketAsset) {
        _selectedAsset.value = asset
        recordRecentSymbol(asset.symbol)
        runBacktest()
    }

    fun setRegime(regime: MarketRegime) {
        _selectedRegime.value = regime
        runBacktest()
    }

    fun setTimeframe(tf: Timeframe) {
        _selectedTimeframe.value = tf
        runBacktest()
    }

    fun setStrategy(strategy: StrategyDefinition) {
        _selectedStrategy.value = strategy
        runBacktest()
    }

    fun updateRiskParameters(risk: RiskParameters) {
        _riskParameters.value = risk
        runBacktest()
    }

    fun updateSmcConfig(smcConfig: SmcConfig) {
        val current = _selectedStrategy.value
        val updatedType = when (current.strategyType) {
            StrategyType.SMC_CONCEPTS -> StrategyType.SMC_CONCEPTS
            StrategyType.ICT_CONCEPTS -> StrategyType.ICT_CONCEPTS
            StrategyType.SMC_ICT_CONCEPTS -> StrategyType.SMC_ICT_CONCEPTS
            else -> StrategyType.SMC_CONCEPTS
        }
        val updated = current.copy(
            strategyType = updatedType,
            indicatorConfig = current.indicatorConfig.copy(smcConfig = smcConfig)
        )
        _selectedStrategy.value = updated
        runBacktest()
    }

    fun setProvider(provider: ProviderSelection) {
        _selectedProvider.value = provider
        runBacktest()
    }

    fun setDatePreset(preset: DateRangePreset) {
        _selectedDatePreset.value = preset
        runBacktest()
    }

    fun setApiKey(key: String) {
        _apiKey.value = if (key.isBlank()) null else key
    }

    fun toggleFavoriteSymbol(symbol: String) {
        val current = _favoriteSymbols.value.toMutableSet()
        if (current.contains(symbol)) {
            current.remove(symbol)
        } else {
            current.add(symbol)
        }
        _favoriteSymbols.value = current
    }

    private fun recordRecentSymbol(symbol: String) {
        val current = _recentSymbols.value.toMutableList()
        current.remove(symbol)
        current.add(0, symbol)
        _recentSymbols.value = current.take(8)
    }

    fun selectTradeForDetail(trade: Trade?) {
        _selectedTradeForDetail.value = trade
    }

    // Cancellation of running backtest
    fun cancelBacktest() {
        backtestJob?.cancel()
        _isBacktesting.value = false
        _backtestProgress.value = BacktestProgress(isRunning = false)
    }

    fun runBacktest() {
        backtestJob?.cancel()
        backtestJob = viewModelScope.launch(Dispatchers.Default) {
            _isBacktesting.value = true
            _dataFetchError.value = null

            val asset = _selectedAsset.value
            val regime = _selectedRegime.value
            val tf = _selectedTimeframe.value
            val strat = _selectedStrategy.value
            val risk = _riskParameters.value
            val preset = _selectedDatePreset.value
            val prov = _selectedProvider.value

            val now = System.currentTimeMillis()
            val startMs = now - (preset.days.toLong() * 24L * 60L * 60L * 1000L)

            _backtestProgress.value = BacktestProgress(
                isRunning = true,
                progressPct = 0.1f,
                processedCandles = 0,
                totalCandles = 0,
                currentDateStr = "Fetching historical data...",
                tradesFound = 0,
                currentEquity = risk.initialCapital,
                strategyName = strat.name,
                symbol = asset.symbol,
                timeframe = tf.label
            )

            val fetchResult = marketDataRepo.getHistoricalCandles(
                asset = asset,
                timeframe = tf,
                startTimeMs = startMs,
                endTimeMs = now,
                apiKey = _apiKey.value,
                isDemoMode = false,
                provider = if (prov == ProviderSelection.AUTO) null else prov.id
            )

            if (!isActive) return@launch

            if (fetchResult.isSuccess) {
                val fetchData = fetchResult.getOrThrow()
                val validatedCandles = fetchData.candles
                _dataFetchError.value = null

                val dsInfo = DataSourceInfo(
                    provider = if (prov == ProviderSelection.AUTO) fetchData.providerName else prov.label,
                    symbol = asset.symbol,
                    market = asset.category.label,
                    timeframe = tf.label,
                    startDate = validatedCandles.firstOrNull()?.formattedDate(tf.minutes) ?: "",
                    endDate = validatedCandles.lastOrNull()?.formattedDate(tf.minutes) ?: "",
                    startTimestamp = validatedCandles.firstOrNull()?.timestamp ?: 0L,
                    endTimestamp = validatedCandles.lastOrNull()?.timestamp ?: 0L,
                    candleCount = validatedCandles.size,
                    isRealHistorical = fetchData.isRealHistorical,
                    validationStatus = if (fetchData.validationReport.isValid) "VERIFIED_VALID" else "WARNING",
                    intrabarExecutionRule = risk.intrabarExecution.label
                )
                _dataSourceInfo.value = dsInfo

                _backtestProgress.value = BacktestProgress(
                    isRunning = true,
                    progressPct = 0.6f,
                    processedCandles = validatedCandles.size,
                    totalCandles = validatedCandles.size,
                    currentDateStr = dsInfo.endDate,
                    tradesFound = 0,
                    currentEquity = risk.initialCapital,
                    strategyName = strat.name,
                    symbol = asset.symbol,
                    timeframe = tf.label
                )

                val result = BacktestEngine.runBacktest(
                    candles = validatedCandles,
                    asset = asset,
                    regime = regime,
                    timeframe = tf,
                    strategy = strat,
                    risk = risk,
                    dataSourceInfo = dsInfo
                )

                if (!isActive) return@launch

                _currentResult.value = result
                _healthScorecard.value = StrategyAdvisor.generateHealthReport(result)

                _backtestProgress.value = BacktestProgress(
                    isRunning = false,
                    progressPct = 1.0f,
                    processedCandles = validatedCandles.size,
                    totalCandles = validatedCandles.size,
                    currentDateStr = dsInfo.endDate,
                    tradesFound = result.trades.size,
                    currentEquity = result.metrics.finalEquity,
                    strategyName = strat.name,
                    symbol = asset.symbol,
                    timeframe = tf.label
                )
            } else {
                val error = fetchResult.exceptionOrNull()
                val errMessage = error?.message ?: "Failed to fetch real market data."
                _dataFetchError.value = errMessage
                _currentResult.value = null
                _healthScorecard.value = null
                _backtestProgress.value = BacktestProgress(isRunning = false)
            }

            _isBacktesting.value = false
        }
    }

    // Strategy Lab (Compare multiple strategies on identical dataset)
    fun runStrategyLabComparison(strategiesToCompare: List<StrategyDefinition>? = null) {
        val strats = strategiesToCompare ?: listOf(
            StrategyDefinition.PRESETS.firstOrNull { it.strategyType == StrategyType.TRENDLINE_BREAK } ?: StrategyDefinition.PRESETS[0],
            StrategyDefinition.PRESETS.firstOrNull { it.strategyType == StrategyType.SMC_CONCEPTS } ?: StrategyDefinition.PRESETS[1],
            StrategyDefinition.PRESETS.firstOrNull { it.strategyType == StrategyType.ICT_CONCEPTS } ?: StrategyDefinition.PRESETS[2],
            StrategyDefinition.PRESETS.firstOrNull { it.strategyType == StrategyType.SMC_ICT_CONCEPTS } ?: StrategyDefinition.PRESETS[3]
        )

        viewModelScope.launch(Dispatchers.Default) {
            _isStrategyLabRunning.value = true
            _strategyLabItems.value = strats.map { StrategyLabItem(strategy = it, isEvaluating = true) }

            val asset = _selectedAsset.value
            val regime = _selectedRegime.value
            val tf = _selectedTimeframe.value
            val risk = _riskParameters.value
            val preset = _selectedDatePreset.value
            val prov = _selectedProvider.value

            val now = System.currentTimeMillis()
            val startMs = now - (preset.days.toLong() * 24L * 60L * 60L * 1000L)

            val fetchResult = marketDataRepo.getHistoricalCandles(
                asset = asset,
                timeframe = tf,
                startTimeMs = startMs,
                endTimeMs = now,
                apiKey = _apiKey.value,
                isDemoMode = false,
                provider = if (prov == ProviderSelection.AUTO) null else prov.id
            )

            if (fetchResult.isSuccess) {
                val validatedCandles = fetchResult.getOrThrow().candles
                val dsInfo = _dataSourceInfo.value ?: DataSourceInfo(
                    provider = "Real Historical API",
                    symbol = asset.symbol,
                    market = asset.category.label,
                    timeframe = tf.label,
                    startDate = validatedCandles.firstOrNull()?.formattedDate(tf.minutes) ?: "",
                    endDate = validatedCandles.lastOrNull()?.formattedDate(tf.minutes) ?: "",
                    startTimestamp = validatedCandles.firstOrNull()?.timestamp ?: 0L,
                    endTimestamp = validatedCandles.lastOrNull()?.timestamp ?: 0L,
                    candleCount = validatedCandles.size,
                    isRealHistorical = true
                )

                val results = mutableListOf<StrategyLabItem>()
                for (strat in strats) {
                    try {
                        val res = BacktestEngine.runBacktest(
                            candles = validatedCandles,
                            asset = asset,
                            regime = regime,
                            timeframe = tf,
                            strategy = strat,
                            risk = risk,
                            dataSourceInfo = dsInfo
                        )
                        results.add(StrategyLabItem(strategy = strat, result = res, isEvaluating = false))
                    } catch (e: Exception) {
                        results.add(StrategyLabItem(strategy = strat, error = e.message, isEvaluating = false))
                    }
                }
                _strategyLabItems.value = results
            } else {
                _strategyLabItems.value = strats.map {
                    StrategyLabItem(strategy = it, error = "Failed to fetch market data", isEvaluating = false)
                }
            }

            _isStrategyLabRunning.value = false
        }
    }

    // Multi-Timeframe Workspace
    fun toggleMtfMode() {
        _isMtfEnabled.value = !_isMtfEnabled.value
        if (_isMtfEnabled.value) {
            loadMtfConfirmationCandles()
        }
    }

    fun setMtfConfirmationTimeframe(tf: Timeframe) {
        _mtfConfirmationTimeframe.value = tf
        loadMtfConfirmationCandles()
    }

    private fun loadMtfConfirmationCandles() {
        viewModelScope.launch(Dispatchers.Default) {
            val asset = _selectedAsset.value
            val tf = _mtfConfirmationTimeframe.value
            val preset = _selectedDatePreset.value
            val prov = _selectedProvider.value
            val now = System.currentTimeMillis()
            val startMs = now - (preset.days.toLong() * 24L * 60L * 60L * 1000L)

            val fetchResult = marketDataRepo.getHistoricalCandles(
                asset = asset,
                timeframe = tf,
                startTimeMs = startMs,
                endTimeMs = now,
                apiKey = _apiKey.value,
                isDemoMode = false,
                provider = if (prov == ProviderSelection.AUTO) null else prov.id
            )

            if (fetchResult.isSuccess) {
                _mtfConfirmationCandles.value = fetchResult.getOrThrow().candles
            }
        }
    }

    // Replay Engine & Manual Trading
    fun startHistoricalReplay(startBarIndex: Int = 30) {
        val result = _currentResult.value ?: return
        val allCandles = result.candles
        if (allCandles.size < 30) return

        _replayAllCandles.value = allCandles
        _replayCurrentIndex.value = startBarIndex.coerceIn(20, allCandles.size - 1)
        _isReplayActive.value = true
        _isReplayPlaying.value = false
        _activeManualPosition.value = null
    }

    fun exitReplay() {
        stopReplayTimer()
        _isReplayActive.value = false
        _isReplayPlaying.value = false
        _activeManualPosition.value = null
    }

    fun stepReplay(delta: Int) {
        val maxIdx = _replayAllCandles.value.size - 1
        if (maxIdx <= 0) return
        val nextIdx = (_replayCurrentIndex.value + delta).coerceIn(20, maxIdx)
        _replayCurrentIndex.value = nextIdx
        checkManualPositionTrigger(nextIdx)
    }

    fun resetReplayToStart() {
        stopReplayTimer()
        _replayCurrentIndex.value = 20
        _activeManualPosition.value = null
    }

    fun jumpReplayToEnd() {
        stopReplayTimer()
        _replayCurrentIndex.value = (_replayAllCandles.value.size - 1).coerceAtLeast(0)
    }

    fun toggleReplayPlay() {
        if (_isReplayPlaying.value) {
            stopReplayTimer()
        } else {
            startReplayTimer()
        }
    }

    fun setReplaySpeed(speed: Float) {
        _replaySpeed.value = speed
        if (_isReplayPlaying.value) {
            startReplayTimer()
        }
    }

    private fun startReplayTimer() {
        stopReplayTimer()
        _isReplayPlaying.value = true
        val delayMs = (1000L / _replaySpeed.value).toLong().coerceIn(100L, 4000L)

        replayTimerJob = viewModelScope.launch(Dispatchers.Default) {
            while (isActive && _isReplayPlaying.value) {
                delay(delayMs)
                val maxIdx = _replayAllCandles.value.size - 1
                if (_replayCurrentIndex.value >= maxIdx) {
                    _isReplayPlaying.value = false
                    break
                }
                val nextIdx = _replayCurrentIndex.value + 1
                _replayCurrentIndex.value = nextIdx
                checkManualPositionTrigger(nextIdx)
            }
        }
    }

    private fun stopReplayTimer() {
        _isReplayPlaying.value = false
        replayTimerJob?.cancel()
        replayTimerJob = null
    }

    fun placeManualReplayOrder(
        direction: TradeDirection,
        stopLoss: Double,
        takeProfit: Double,
        notes: String = ""
    ) {
        val candles = _replayAllCandles.value
        val curIdx = _replayCurrentIndex.value
        if (curIdx !in candles.indices) return

        val currentCandle = candles[curIdx]
        val entryPrice = currentCandle.close
        val capital = _riskParameters.value.initialCapital
        val riskPerTrade = capital * (_riskParameters.value.riskPerTradePercent / 100.0)
        val slDistance = kotlin.math.abs(entryPrice - stopLoss)
        val qty = if (slDistance > 0) riskPerTrade / slDistance else 1.0

        val pos = ManualReplayPosition(
            id = UUID.randomUUID().toString(),
            direction = direction,
            entryPrice = entryPrice,
            stopLoss = stopLoss,
            takeProfit = takeProfit,
            quantity = qty,
            entryTimestamp = currentCandle.timestamp,
            entryBarIndex = curIdx,
            notes = notes
        )
        _activeManualPosition.value = pos
    }

    fun closeManualReplayPositionManually() {
        val pos = _activeManualPosition.value ?: return
        val candles = _replayAllCandles.value
        val curIdx = _replayCurrentIndex.value
        if (curIdx !in candles.indices) return

        val candle = candles[curIdx]
        val exitPrice = candle.close
        recordManualReplayTrade(pos, exitPrice, candle.timestamp, curIdx, ExitReason.SIGNAL_REVERSAL)
        _activeManualPosition.value = null
    }

    private fun checkManualPositionTrigger(barIndex: Int) {
        val pos = _activeManualPosition.value ?: return
        val candles = _replayAllCandles.value
        if (barIndex !in candles.indices) return

        val candle = candles[barIndex]
        var exitHit: Pair<Double, ExitReason>? = null

        if (pos.direction == TradeDirection.LONG) {
            if (candle.low <= pos.stopLoss) {
                exitHit = Pair(pos.stopLoss, ExitReason.STOP_LOSS)
            } else if (candle.high >= pos.takeProfit) {
                exitHit = Pair(pos.takeProfit, ExitReason.TAKE_PROFIT)
            }
        } else {
            if (candle.high >= pos.stopLoss) {
                exitHit = Pair(pos.stopLoss, ExitReason.STOP_LOSS)
            } else if (candle.low <= pos.takeProfit) {
                exitHit = Pair(pos.takeProfit, ExitReason.TAKE_PROFIT)
            }
        }

        if (exitHit != null) {
            recordManualReplayTrade(pos, exitHit.first, candle.timestamp, barIndex, exitHit.second)
            _activeManualPosition.value = null
        }
    }

    private fun recordManualReplayTrade(
        pos: ManualReplayPosition,
        exitPrice: Double,
        exitTimestamp: Long,
        exitBarIndex: Int,
        exitReason: ExitReason
    ) {
        val slDist = kotlin.math.abs(pos.entryPrice - pos.stopLoss)
        val priceDiff = if (pos.direction == TradeDirection.LONG) exitPrice - pos.entryPrice else pos.entryPrice - exitPrice
        val pnlDollars = priceDiff * pos.quantity
        val pnlPct = if (pos.entryPrice > 0) (priceDiff / pos.entryPrice) * 100.0 else 0.0
        val rMultiple = if (slDist > 0) priceDiff / slDist else 0.0

        val trade = Trade(
            id = pos.id,
            barIndex = pos.entryBarIndex,
            exitBarIndex = exitBarIndex,
            entryTimestamp = pos.entryTimestamp,
            exitTimestamp = exitTimestamp,
            direction = pos.direction,
            entryPrice = pos.entryPrice,
            exitPrice = exitPrice,
            quantity = pos.quantity,
            positionValue = pos.entryPrice * pos.quantity,
            pnlDollars = pnlDollars,
            pnlPercent = pnlPct,
            exitReason = exitReason,
            feesPaid = 0.0,
            rMultiple = rMultiple,
            holdingBars = exitBarIndex - pos.entryBarIndex,
            maxRunUpPct = 0.0,
            maxDrawdownPct = 0.0,
            entryReason = "Manual Replay Execution"
        )

        _manualReplayTrades.value = listOf(trade) + _manualReplayTrades.value

        // Auto-add to journal
        val jEntry = JournalEntry(
            id = UUID.randomUUID().toString(),
            tradeId = trade.id,
            timestamp = System.currentTimeMillis(),
            symbol = _selectedAsset.value.symbol,
            strategyName = "Manual Replay",
            strategyType = _selectedStrategy.value.strategyType,
            direction = trade.direction,
            entryPrice = trade.entryPrice,
            exitPrice = trade.exitPrice,
            pnlDollars = trade.pnlDollars,
            pnlPercent = trade.pnlPercent,
            rMultiple = trade.rMultiple,
            thesis = pos.notes.ifBlank { "Replay manual trade entry" },
            isManualReplay = true,
            entryReason = "Manual Replay [${trade.direction.name}]"
        )
        _journalEntries.value = listOf(jEntry) + _journalEntries.value
    }

    // Journal Management
    fun addOrUpdateJournalEntry(entry: JournalEntry) {
        val current = _journalEntries.value.toMutableList()
        val idx = current.indexOfFirst { it.id == entry.id }
        if (idx >= 0) {
            current[idx] = entry
        } else {
            current.add(0, entry)
        }
        _journalEntries.value = current
    }

    fun deleteJournalEntry(id: String) {
        _journalEntries.value = _journalEntries.value.filter { it.id != id }
    }

    fun createJournalEntryFromTrade(trade: Trade, notes: String = "", tags: List<String> = emptyList()) {
        val entry = JournalEntry(
            id = UUID.randomUUID().toString(),
            tradeId = trade.id,
            timestamp = trade.exitTimestamp,
            symbol = _selectedAsset.value.symbol,
            strategyName = _selectedStrategy.value.name,
            strategyType = _selectedStrategy.value.strategyType,
            direction = trade.direction,
            entryPrice = trade.entryPrice,
            exitPrice = trade.exitPrice,
            pnlDollars = trade.pnlDollars,
            pnlPercent = trade.pnlPercent,
            rMultiple = trade.rMultiple,
            thesis = notes,
            tags = tags,
            entryReason = trade.entryReason ?: trade.exitReason.label,
            setupGrade = if (trade.isWin) JournalGrade.A else JournalGrade.B
        )
        addOrUpdateJournalEntry(entry)
    }

    // Optimizer Sweep
    fun cancelOptimization() {
        optimizationJob?.cancel()
        _isOptimizing.value = false
    }

    fun runOptimization() {
        optimizationJob?.cancel()
        optimizationJob = viewModelScope.launch(Dispatchers.Default) {
            _isOptimizing.value = true
            val asset = _selectedAsset.value
            val regime = _selectedRegime.value
            val tf = _selectedTimeframe.value
            val strat = _selectedStrategy.value
            val risk = _riskParameters.value
            val preset = _selectedDatePreset.value
            val prov = _selectedProvider.value

            val now = System.currentTimeMillis()
            val startMs = now - (preset.days.toLong() * 24L * 60L * 60L * 1000L)

            val fetchResult = marketDataRepo.getHistoricalCandles(
                asset = asset,
                timeframe = tf,
                startTimeMs = startMs,
                endTimeMs = now,
                apiKey = _apiKey.value,
                isDemoMode = false,
                provider = if (prov == ProviderSelection.AUTO) null else prov.id
            )

            if (!isActive) return@launch

            if (fetchResult.isSuccess) {
                val cleanCandles = fetchResult.getOrThrow().candles
                val optResults = StrategyOptimizer.runParameterSweep(strat, asset, regime, tf, risk, cleanCandles)
                _optimizationResults.value = optResults
            } else {
                _dataFetchError.value = fetchResult.exceptionOrNull()?.message ?: "Optimization data fetch failed."
            }

            _isOptimizing.value = false
        }
    }

    fun runRegimeStressTest() {
        viewModelScope.launch(Dispatchers.Default) {
            _isComparingRegimes.value = true
            val asset = _selectedAsset.value
            val tf = _selectedTimeframe.value
            val strat = _selectedStrategy.value
            val risk = _riskParameters.value

            val comparisons = StrategyOptimizer.evaluateAcrossRegimes(strat, asset, tf, risk)
            _regimeComparison.value = comparisons
            _isComparingRegimes.value = false
        }
    }

    fun saveCurrentStrategy(name: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val custom = _selectedStrategy.value.copy(
                id = "strat_${System.currentTimeMillis()}",
                name = name,
                isCustom = true
            )
            repository.saveStrategy(custom)
            _selectedStrategy.value = custom
        }
    }

    fun saveCurrentBacktest() {
        viewModelScope.launch(Dispatchers.IO) {
            _currentResult.value?.let { res ->
                repository.saveBacktestResult(res)
            }
        }
    }

    fun deleteSavedStrategy(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteStrategy(id)
        }
    }

    fun deleteSavedBacktest(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteBacktest(id)
        }
    }

    // Analytics Computations
    private fun computeSessionAnalytics(trades: List<Trade>): List<SessionAnalytics> {
        val timeZone = TimeZone.getTimeZone("UTC")
        val asiaTrades = mutableListOf<Trade>()
        val londonTrades = mutableListOf<Trade>()
        val nyTrades = mutableListOf<Trade>()
        val overlapTrades = mutableListOf<Trade>()

        val cal = Calendar.getInstance(timeZone)
        for (trade in trades) {
            cal.timeInMillis = trade.entryTimestamp
            val hour = cal.get(Calendar.HOUR_OF_DAY)
            when (hour) {
                in 0..7 -> asiaTrades.add(trade)
                in 8..11 -> londonTrades.add(trade)
                in 12..16 -> overlapTrades.add(trade)
                in 17..21 -> nyTrades.add(trade)
                else -> asiaTrades.add(trade)
            }
        }

        fun summarize(name: String, list: List<Trade>): SessionAnalytics {
            val count = list.size
            val wins = list.count { it.isWin }
            val winRate = if (count > 0) (wins.toDouble() / count) * 100.0 else 0.0
            val pnl = list.sumOf { it.pnlDollars }
            val avgR = if (count > 0) list.map { it.rMultiple }.average() else 0.0
            return SessionAnalytics(name, count, winRate, pnl, avgR)
        }

        return listOf(
            summarize("Asia (00:00 - 08:00 UTC)", asiaTrades),
            summarize("London (08:00 - 12:00 UTC)", londonTrades),
            summarize("London / NY Overlap (12:00 - 16:00 UTC)", overlapTrades),
            summarize("New York (16:00 - 22:00 UTC)", nyTrades)
        )
    }

    private fun computeMaeMfe(trades: List<Trade>): MaeMfeDistribution {
        val points = trades.map { trade ->
            val mae = trade.maxDrawdownPct
            val mfe = trade.maxRunUpPct
            MaeMfePoint(
                tradeId = trade.id,
                barIndex = trade.barIndex,
                direction = trade.direction,
                rMultiple = trade.rMultiple,
                maePct = mae,
                mfePct = mfe,
                isWin = trade.isWin,
                entryPrice = trade.entryPrice,
                exitPrice = trade.exitPrice
            )
        }
        val avgMae = if (points.isNotEmpty()) points.map { it.maePct }.average() else 0.0
        val avgMfe = if (points.isNotEmpty()) points.map { it.mfePct }.average() else 0.0
        val maxMae = if (points.isNotEmpty()) points.maxOf { it.maePct } else 0.0
        val maxMfe = if (points.isNotEmpty()) points.maxOf { it.mfePct } else 0.0

        return MaeMfeDistribution(
            avgMaePct = avgMae,
            avgMfePct = avgMfe,
            maxMaePct = maxMae,
            maxMfePct = maxMfe,
            points = points
        )
    }
}

