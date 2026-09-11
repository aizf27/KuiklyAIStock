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

// AI 分析的趋势方向。
enum class AiTrendType(val label: String) {
    STRONG("偏强"),
    SIDEWAYS("震荡"),
    WEAK("偏弱"),
}

// AI 分析来源。
enum class AiAnalysisSource {
    MOCK,
    REMOTE,
    CACHE,
}

// AI 分析的风险等级。
enum class AiRiskLevel(val label: String) {
    LOW("低风险"),
    MEDIUM("中风险"),
    HIGH("高风险"),
}

// 条件式观察计划，不直接绑定交易操作。
data class AiObservationPlan(
    val focusRangeLow: Double,
    val focusRangeHigh: Double,
    val confirmationCondition: String,
    val confirmationPrice: Double? = null,
    val referenceTarget: Double? = null,
    val riskBoundary: String,
)

// 单条结构化信号及其数据依据。
data class AiSignal(
    val title: String,
    val status: String,
    val explanation: String,
    val evidence: String,
)

// AI 分析保留旧字段，并通过默认值兼容现有调用方。
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
    val trendType: AiTrendType = AiTrendType.SIDEWAYS,
    val riskLevel: AiRiskLevel = AiRiskLevel.MEDIUM,
    val primaryRisks: List<String> = emptyList(),
    val invalidationCondition: String = "",
    val observationPlan: AiObservationPlan? = null,
    val signals: List<AiSignal> = emptyList(),
    val source: AiAnalysisSource = AiAnalysisSource.MOCK,
    val modelName: String? = null,
    val generatedAt: String = "",
    val requestId: String? = null,
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
