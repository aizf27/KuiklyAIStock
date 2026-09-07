package com.example.kuiklyaistock.model

// 列表页使用的股票报价摘要。
data class StockQuote(
    val name: String,
    val code: String,
    val price: Double,
    val change: Double,
    val changePercent: Double,
    val updatedAt: String,
) {
    val symbol: String
        get() = code

    val isRising: Boolean
        get() = change > 0

    val isFalling: Boolean
        get() = change < 0
}

// 首页市场概览摘要。
data class MarketSummary(
    val totalCount: Int,
    val risingCount: Int,
    val fallingCount: Int,
    val flatCount: Int,
    val updatedAt: String,
)

// 详情页使用的完整股票行情。
data class StockDetail(
    val quote: StockQuote,
    val high: Double,
    val low: Double,
    val volume: Long,
    val turnover: Double,
    val trend: List<TrendPoint>,
)

// 简化的走势数据点，供跨端走势组件渲染。
data class TrendPoint(
    val time: String,
    val price: Double,
)

// AI 分析区域的四类信息。
data class AiAnalysis(
    val trendJudgement: String,
    val operationTip: String,
    val riskReminder: String,
    val signalInterpretation: String,
    val marketSummary: String,
)
