package com.example.kuiklyaistock.repository

import com.example.kuiklyaistock.model.AiAnalysis
import com.example.kuiklyaistock.model.StockQuote
import com.example.kuiklyaistock.model.TrendPoint
import com.tencent.kuiklybase.chart.model.OhlcPoint

// 数据库仓储接口，隔离 SQLDelight 实现细节
interface StockDatabaseRepository {
    // 查询单只股票快照
    suspend fun getQuote(code: String): StockQuote?

    // 查询所有股票快照
    suspend fun getAllQuotes(): List<StockQuote>

    // 插入或更新股票快照（批量）
    suspend fun insertQuotes(quotes: List<StockQuote>)

    // 查询分时数据
    suspend fun getIntradayTrend(code: String): List<TrendPoint>

    // 插入分时数据（会先删除旧数据）
    suspend fun insertIntradayTrend(code: String, data: List<TrendPoint>)

    // 查询 K 线数据
    suspend fun getKLines(code: String, period: String): List<OhlcPoint>

    // 插入 K 线数据（会先删除旧数据）
    suspend fun insertKLines(code: String, period: String, data: List<OhlcPoint>)

    // 查询 AI 分析缓存
    suspend fun getAiAnalysis(code: String): AiAnalysisCacheEntry?

    // 插入或更新 AI 分析缓存
    suspend fun insertAiAnalysis(code: String, analysis: AiAnalysis, quoteTime: String)
}

// AI 分析缓存条目
data class AiAnalysisCacheEntry(
    val analysis: AiAnalysis,
    val quoteTime: String,
    val analyzedAt: Long,
)
