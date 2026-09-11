package com.example.kuiklyaistock.repository

import com.example.kuiklyaistock.db.StockDatabase
import com.example.kuiklyaistock.model.AiAnalysis
import com.example.kuiklyaistock.model.AiAnalysisSource
import com.example.kuiklyaistock.model.StockQuote
import com.example.kuiklyaistock.model.TrendPoint
import com.tencent.kuiklybase.chart.model.OhlcPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString

// SQLDelight 实现的数据库仓储
internal class SqlDelightStockDatabaseRepository(
    private val database: StockDatabase,
    private val json: Json = Json { ignoreUnknownKeys = true },
) : StockDatabaseRepository {

    private val stockQuoteQueries = database.stockQuoteQueries
    private val intradayTrendQueries = database.intradayTrendQueries
    private val kLineDataQueries = database.kLineDataQueries
    private val aiAnalysisCacheQueries = database.aiAnalysisCacheQueries

    override suspend fun getQuote(code: String): StockQuote? = withContext(Dispatchers.Default) {
        stockQuoteQueries.getQuoteByCode(code).executeAsOneOrNull()?.toStockQuote()
    }

    override suspend fun getAllQuotes(): List<StockQuote> = withContext(Dispatchers.Default) {
        stockQuoteQueries.getAllQuotes().executeAsList().map { it.toStockQuote() }
    }

    override suspend fun insertQuotes(quotes: List<StockQuote>) = withContext(Dispatchers.Default) {
        database.transaction {
            quotes.forEach { quote ->
                stockQuoteQueries.insertOrReplaceQuote(
                    code = quote.code,
                    name = quote.name,
                    price = quote.price,
                    change = quote.change,
                    changePercent = quote.changePercent,
                    open_ = 0.0,
                    previousClose = 0.0,
                    high = 0.0,
                    low = 0.0,
                    volume = 0,
                    turnover = 0.0,
                    turnoverRate = null,
                    peRatio = null,
                    updatedAt = quote.updatedAt,
                    cachedAt = System.currentTimeMillis()
                )
            }
        }
    }

    override suspend fun getIntradayTrend(code: String): List<TrendPoint> = withContext(Dispatchers.Default) {
        intradayTrendQueries.getIntradayTrend(code).executeAsList().map {
            TrendPoint(
                label = it.time,
                price = it.price,
                volume = it.volume
            )
        }
    }

    override suspend fun insertIntradayTrend(code: String, data: List<TrendPoint>) = withContext(Dispatchers.Default) {
        database.transaction {
            intradayTrendQueries.deleteByCode(code)
            data.forEach { point ->
                intradayTrendQueries.insertIntradayPoint(
                    code = code,
                    time = point.label,
                    price = point.price,
                    volume = point.volume
                )
            }
        }
    }

    override suspend fun getKLines(code: String, period: String): List<OhlcPoint> = withContext(Dispatchers.Default) {
        kLineDataQueries.getKLines(code, period).executeAsList().mapIndexed { index, it ->
            OhlcPoint(
                label = it.date,
                x = index.toFloat(),
                open = it.open_.toFloat(),
                high = it.high.toFloat(),
                low = it.low.toFloat(),
                close = it.close.toFloat(),
                volume = it.volume.toFloat()
            )
        }
    }

    override suspend fun insertKLines(code: String, period: String, data: List<OhlcPoint>) = withContext(Dispatchers.Default) {
        database.transaction {
            kLineDataQueries.deleteByCodeAndPeriod(code, period)
            data.forEach { kline ->
                kLineDataQueries.insertOrReplaceKLine(
                    code = code,
                    period = period,
                    date = kline.label,
                    open_ = kline.open.toDouble(),
                    close = kline.close.toDouble(),
                    high = kline.high.toDouble(),
                    low = kline.low.toDouble(),
                    volume = kline.volume?.toDouble() ?: 0.0
                )
            }
        }
    }

    override suspend fun getAiAnalysis(code: String): AiAnalysisCacheEntry? = withContext(Dispatchers.Default) {
        aiAnalysisCacheQueries.getAiAnalysis(code).executeAsOneOrNull()?.let { record ->
            try {
                val analysis = json.decodeFromString<AiAnalysis>(record.content)
                AiAnalysisCacheEntry(
                    analysis = analysis,
                    quoteTime = record.quoteTime,
                    analyzedAt = record.analyzedAt
                )
            } catch (e: Exception) {
                null
            }
        }
    }

    override suspend fun insertAiAnalysis(code: String, analysis: AiAnalysis, quoteTime: String) = withContext(Dispatchers.Default) {
        val content = json.encodeToString(analysis)
        aiAnalysisCacheQueries.insertOrReplaceAiAnalysis(
            code = code,
            content = content,
            quoteTime = quoteTime,
            analyzedAt = System.currentTimeMillis()
        )
    }

    // 转换函数：数据库记录 → StockQuote
    private fun com.example.kuiklyaistock.db.StockQuote.toStockQuote() = StockQuote(
        name = name,
        code = code,
        price = price,
        change = change,
        changePercent = changePercent,
        updatedAt = updatedAt,
    )
}
