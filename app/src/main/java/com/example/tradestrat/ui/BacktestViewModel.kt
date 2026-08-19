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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

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

    private val _optimizationResults = MutableStateFlow<List<OptimizationResult>>(emptyList())
    val optimizationResults = _optimizationResults.asStateFlow()

    private val _isOptimizing = MutableStateFlow(false)
    val isOptimizing = _isOptimizing.asStateFlow()

    private val _regimeComparison = MutableStateFlow<List<RegimeComparisonResult>>(emptyList())
    val regimeComparison = _regimeComparison.asStateFlow()

    private val _isComparingRegimes = MutableStateFlow(false)
    val isComparingRegimes = _isComparingRegimes.asStateFlow()

    private val _healthScorecard = MutableStateFlow<StrategyHealthScorecard?>(null)
    val healthScorecard = _healthScorecard.asStateFlow()

    // Database Flows
    val savedStrategies: StateFlow<List<StrategyDefinition>> = repository.savedStrategies
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val savedBacktests: StateFlow<List<SavedBacktestEntity>> = repository.savedBacktests
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        // Run initial real backtest immediately on startup
        runBacktest()
    }

    fun setAsset(asset: MarketAsset) {
        _selectedAsset.value = asset
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
        val updated = current.copy(
            strategyType = StrategyType.SMC_ICT_CONCEPTS,
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

    fun runBacktest() {
        viewModelScope.launch(Dispatchers.Default) {
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

                val result = BacktestEngine.runBacktest(
                    candles = validatedCandles,
                    asset = asset,
                    regime = regime,
                    timeframe = tf,
                    strategy = strat,
                    risk = risk,
                    dataSourceInfo = dsInfo
                )

                _currentResult.value = result
                _healthScorecard.value = StrategyAdvisor.generateHealthReport(result)
            } else {
                val error = fetchResult.exceptionOrNull()
                val errMessage = error?.message ?: "Failed to fetch real market data."
                _dataFetchError.value = errMessage
                // Strict mandate: Do not run backtest or fabricate synthetic data
                _currentResult.value = null
                _healthScorecard.value = null
            }

            _isBacktesting.value = false
        }
    }

    fun runOptimization() {
        viewModelScope.launch(Dispatchers.Default) {
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
        viewModelScope.launch {
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
        viewModelScope.launch {
            _currentResult.value?.let { res ->
                repository.saveBacktestResult(res)
            }
        }
    }

    fun deleteSavedStrategy(id: String) {
        viewModelScope.launch {
            repository.deleteStrategy(id)
        }
    }

    fun deleteSavedBacktest(id: String) {
        viewModelScope.launch {
            repository.deleteBacktest(id)
        }
    }
}
