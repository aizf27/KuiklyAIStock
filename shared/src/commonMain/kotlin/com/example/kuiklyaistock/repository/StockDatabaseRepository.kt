package com.example.kuiklyaistock.repository

import com.example.kuiklyaistock.model.AiAnalysis
import com.example.kuiklyaistock.model.MarketIndexQuote
import com.example.kuiklyaistock.model.StockQuote
import com.example.kuiklyaistock.model.TrendPoint
import com.tencent.kuiklybase.chart.model.OhlcPoint

/**
 * 股票数据库仓储接口，隔离 SQLDelight 实现细节
 */
interface StockDatabaseRepository {
    // 查询单只股票的最新报价快照
    suspend fun getQuote(code: String): StockQuote?

    // 查询所有股票的最新报价快照
    suspend fun getAllQuotes(): List<StockQuote>

    // 批量插入或更新股票报价快照
    suspend fun insertQuotes(quotes: List<StockQuote>)

    // 查询三大指数的缓存报价
    suspend fun getMarketIndices(): List<MarketIndexQuote>

    // 插入或更新三大指数缓存报价
    suspend fun insertMarketIndices(indices: List<MarketIndexQuote>)

    // 查询指定股票的分时走势数据
    suspend fun getIntradayTrend(code: String): List<TrendPoint>

    // 插入分时走势数据（会先删除该股票的旧分时数据）
    suspend fun insertIntradayTrend(code: String, data: List<TrendPoint>)

    // 查询指定股票的K线数据，period为"day"/"week"/"month"
    suspend fun getKLines(code: String, period: String): List<OhlcPoint>

    // 插入K线数据（会先删除该股票该周期的旧K线数据）
    suspend fun insertKLines(code: String, period: String, data: List<OhlcPoint>)

    // 查询指定股票的AI分析缓存
    suspend fun getAiAnalysis(code: String): AiAnalysisCacheEntry?

    // 插入或更新AI分析缓存，关联行情时间以判断是否过期
    suspend fun insertAiAnalysis(code: String, analysis: AiAnalysis, quoteTime: String)
}

/**
 * AI分析缓存条目，包含分析结果和时效性信息
 * @param analysis AI分析结果
 * @param quoteTime 分析所依据的行情时间
 * @param analyzedAt 分析生成的时间戳（毫秒）
 */
data class AiAnalysisCacheEntry(
    val analysis: AiAnalysis,
    val quoteTime: String,
    val analyzedAt: Long,
)
