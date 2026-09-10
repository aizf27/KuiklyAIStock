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
    val sessionStatus: String,
    val sampleTurnoverAmount: Double,
    val sampleNetInflowAmount: Double,
    val indices: List<MarketIndexQuote>,
    val updatedAt: String,
)

// 首页市场概览使用的指数报价。
data class MarketIndexQuote(
    val name: String,
    val code: String,
    val price: Double,
    val change: Double,
    val changePercent: Double,
    val updatedAt: String,
)

// 详情页使用的完整股票行情。
data class StockDetail(
    val quote: StockQuote,
    val open: Double,
    val previousClose: Double,
    val high: Double,
    val low: Double,
    val volume: Long,
    val turnover: Double,
    val intradayTrend: List<TrendPoint>,
    val dailyTrend: List<TrendPoint>,
)

// 简化的走势数据点，供跨端走势组件渲染。
data class TrendPoint(
    val label: String,
    val price: Double,
)

// AI 分析区域的四类信息。
data class AiAnalysis(
    val trendJudgement: String,
    val focusPoint: String,
    val riskReminder: String,
    val signalInterpretation: String,
    val factSummary: String,
    val applicablePeriod: String,
    val evidenceSummary: String,
    val updatedAt: String,
    val isDemo: Boolean,
)

// AI 解读首页的市场级结论。
data class AiMarketOverview(
    val title: String,
    val sentiment: String,
    val summary: String,
    val riskTip: String,
    val updatedAt: String,
)

// AI 解读首页的重点股票卡片数据。
data class AiStockInsight(
    val quote: StockQuote,
    val analysis: AiAnalysis,
)
