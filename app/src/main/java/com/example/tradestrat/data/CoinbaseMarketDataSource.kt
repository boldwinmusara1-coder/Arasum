package com.example.tradestrat.data

import com.example.tradestrat.model.AssetCategory
import com.example.tradestrat.model.Candle
import com.example.tradestrat.model.MarketAsset
import com.example.tradestrat.model.Timeframe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class CoinbaseMarketDataSource(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()
) : MarketDataSource {

    override val providerName: String = "Coinbase Exchange Public API"
    override val supportsApiKey: Boolean = false

    override fun supportsAsset(asset: MarketAsset): Boolean {
        return asset.category == AssetCategory.CRYPTO
    }

    private fun mapGranularity(tf: Timeframe): Int {
        return when (tf) {
            Timeframe.M15 -> 900
            Timeframe.H1 -> 3600
            Timeframe.H4 -> 21600 // Coinbase 6h
            Timeframe.D1 -> 86400
        }
    }

    private fun mapProductId(asset: MarketAsset): String {
        return when (asset.symbol) {
            "BTC/USD" -> "BTC-USD"
            "ETH/USD" -> "ETH-USD"
            "SOL/USD" -> "SOL-USD"
            else -> asset.symbol.replace("/", "-")
        }
    }

    override suspend fun fetchHistoricalCandles(
        asset: MarketAsset,
        timeframe: Timeframe,
        startTimeMs: Long,
        endTimeMs: Long,
        apiKey: String?
    ): Result<List<Candle>> = withContext(Dispatchers.IO) {
        val productId = mapProductId(asset)
        val granularity = mapGranularity(timeframe)
        val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }

        val startIso = isoFormat.format(Date(startTimeMs))
        val endIso = isoFormat.format(Date(endTimeMs))
        val url = "https://api.exchange.coinbase.com/products/$productId/candles?granularity=$granularity&start=$startIso&end=$endIso"

        val request = Request.Builder()
            .url(url)
            .addHeader("User-Agent", "TradeStrat-Backtester/1.0")
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val code = response.code
                    val errorBody = response.body?.string() ?: ""
                    return@withContext Result.failure(
                        IOException("Coinbase API HTTP $code: $errorBody")
                    )
                }

                val body = response.body?.string() ?: throw IOException("Empty response from Coinbase API")
                val jsonArray = JSONArray(body)
                val candles = mutableListOf<Candle>()

                for (i in 0 until jsonArray.length()) {
                    val item = jsonArray.getJSONArray(i)
                    val epochSeconds = item.getLong(0)
                    val low = item.getDouble(1)
                    val high = item.getDouble(2)
                    val open = item.getDouble(3)
                    val close = item.getDouble(4)
                    val volume = item.getDouble(5)

                    candles.add(
                        Candle(
                            timestamp = epochSeconds * 1000L,
                            open = open,
                            high = high,
                            low = low,
                            close = close,
                            volume = volume
                        )
                    )
                }

                Result.success(candles)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
