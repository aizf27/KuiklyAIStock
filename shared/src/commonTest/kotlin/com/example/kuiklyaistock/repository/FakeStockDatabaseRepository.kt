package com.example.kuiklyaistock.repository

import com.example.kuiklyaistock.model.AiAnalysis
import com.example.kuiklyaistock.model.MarketIndexQuote
import com.example.kuiklyaistock.model.StockQuote
import com.example.kuiklyaistock.model.TrendPoint
import com.tencent.kuiklybase.chart.model.OhlcPoint

internal class FakeStockDatabaseRepository : StockDatabaseRepository {
    private val quotes = linkedMapOf<String, StockQuote>()
    private val indices = linkedMapOf<String, MarketIndexQuote>()
    private val intraday = mutableMapOf<String, List<TrendPoint>>()
    private val klines = mutableMapOf<Pair<String, String>, List<OhlcPoint>>()
    private val analyses = mutableMapOf<String, AiAnalysisCacheEntry>()

    override suspend fun getQuote(code: String): StockQuote? = quotes[code]

    override suspend fun getAllQuotes(): List<StockQuote> = quotes.values.toList()

    override suspend fun insertQuotes(quotes: List<StockQuote>) {
        quotes.forEach { quote -> this.quotes[quote.code] = quote }
    }

    override suspend fun getMarketIndices(): List<MarketIndexQuote> = indices.values.toList()

    override suspend fun insertMarketIndices(indices: List<MarketIndexQuote>) {
        indices.forEach { index -> this.indices[index.code] = index }
    }

    override suspend fun getIntradayTrend(code: String): List<TrendPoint> = intraday[code].orEmpty()

    override suspend fun insertIntradayTrend(code: String, data: List<TrendPoint>) {
        intraday[code] = data
    }

    override suspend fun getKLines(code: String, period: String): List<OhlcPoint> = klines[code to period].orEmpty()

    override suspend fun insertKLines(code: String, period: String, data: List<OhlcPoint>) {
        klines[code to period] = data
    }

    override suspend fun getAiAnalysis(code: String): AiAnalysisCacheEntry? = analyses[code]

    override suspend fun insertAiAnalysis(code: String, analysis: AiAnalysis, quoteTime: String) {
        analyses[code] = AiAnalysisCacheEntry(analysis, quoteTime, 0L)
    }
}
