package com.example.kuiklyaistock.model

import com.tencent.kuiklybase.chart.model.OhlcPoint
import kotlinx.serialization.Serializable

/**
 * 行情数据来源类型
 */
enum class StockDataSource {
    REMOTE,  // 从远程服务器获取的实时行情
    CACHE,   // 从本地数据库读取的缓存行情
    MOCK,    // 用于开发测试的模拟行情
}

internal fun StockDataSource.displayName(): String = when (this) {
    StockDataSource.REMOTE -> "真实行情"
    StockDataSource.CACHE -> "缓存行情"
    StockDataSource.MOCK -> "Mock 行情"
}

/**
 * 股票报价摘要，用于列表页展示
 * @param name 股票名称，如"中国平安"
 * @param code 股票代码，如"sh601318"
 * @param price 最新价
 * @param change 涨跌额
 * @param changePercent 涨跌幅百分比
 * @param updatedAt 行情更新时间
 */
data class StockQuote(
    val name: String,
    val code: String,
    val price: Double,
    val change: Double,
    val changePercent: Double,
    val updatedAt: String,
) {
    // 判断当前是否上涨
    val isRising: Boolean
        get() = change > 0

    // 判断当前是否下跌
    val isFalling: Boolean
        get() = change < 0
}

/**
 * 市场概览摘要，用于首页展示整体市场情况
 * @param totalCount 市场总股票数
 * @param risingCount 上涨股票数
 * @param fallingCount 下跌股票数
 * @param flatCount 平盘股票数
 * @param sessionStatus 交易时段状态，如"盘中"、"已收盘"
 * @param sampleTurnoverAmount 样本股票总成交额
 * @param sampleNetInflowAmount 样本股票净流入金额
 * @param indices 三大指数报价列表
 * @param updatedAt 更新时间
 */
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

/**
 * 指数报价，用于首页市场概览展示大盘指数
 * @param name 指数名称，如"上证指数"
 * @param code 指数代码，如"sh000001"
 * @param price 最新点位
 * @param change 涨跌点数
 * @param changePercent 涨跌幅百分比
 * @param updatedAt 更新时间
 */
data class MarketIndexQuote(
    val name: String,
    val code: String,
    val price: Double,
    val change: Double,
    val changePercent: Double,
    val updatedAt: String,
)

/**
 * 股票详情完整行情数据，用于个股详情页展示
 * @param quote 基础报价信息
 * @param open 今日开盘价
 * @param previousClose 昨日收盘价
 * @param high 今日最高价
 * @param low 今日最低价
 * @param volume 成交量（手）
 * @param turnover 成交额（元）
 * @param turnoverRate 换手率（可选）
 * @param peRatio 市盈率（可选）
 * @param intradayTrend 分时走势数据点列表
 * @param fiveDayTrend 5日走势数据点列表
 * @param dailyKLine 日K线数据列表
 * @param weeklyKLine 周K线数据列表
 * @param monthlyKLine 月K线数据列表
 * @param dailyTrend 已废弃，使用dailyKLine替代
 */
data class StockDetail(
    val quote: StockQuote,
    val open: Double,
    val previousClose: Double,
    val high: Double,
    val low: Double,
    val volume: Long,
    val turnover: Double,
    val turnoverRate: Double? = null,
    val peRatio: Double? = null,
    val intradayTrend: List<TrendPoint>,
    val fiveDayTrend: List<TrendPoint> = emptyList(),
    val dailyKLine: List<OhlcPoint> = emptyList(),
    val weeklyKLine: List<OhlcPoint> = emptyList(),
    val monthlyKLine: List<OhlcPoint> = emptyList(),
    val dailyTrend: List<TrendPoint> = emptyList(),
)

/**
 * 走势数据点，用于跨端走势图组件渲染
 * @param label 时间标签，如"09:30"或"2024-01-01"
 * @param price 该时间点的价格
 * @param volume 该时间点的成交量（可选）
 */
data class TrendPoint(
    val label: String,
    val price: Double,
    val volume: Double = 0.0,
)

/**
 * AI分析的趋势方向判断
 */
@Serializable
enum class AiTrendType(val label: String) {
    STRONG("偏强"),      // 价格在昨收上方且有上涨动能
    SIDEWAYS("震荡"),    // 价格在昨收附近窄幅波动
    WEAK("偏弱"),        // 价格在昨收下方且承压
}

/**
 * AI分析数据来源类型
 */
@Serializable
enum class AiAnalysisSource {
    MOCK,    // 本地模拟数据
    REMOTE,  // 远程AI服务返回
    CACHE,   // 数据库缓存
}

/**
 * AI分析的风险等级评估
 */
@Serializable
enum class AiRiskLevel(val label: String) {
    LOW("低风险"),      // 震荡不大，方向相对明确
    MEDIUM("中风险"),   // 存在一定不确定性
    HIGH("高风险"),     // 波动较大或趋势不明
}

/**
 * 条件式观察计划，给出后续观察方向但不构成交易建议
 * @param focusRangeLow 重点观察价格区间下限
 * @param focusRangeHigh 重点观察价格区间上限
 * @param confirmationCondition 确认条件描述
 * @param confirmationPrice 确认价格（可选）
 * @param referenceTarget 参考目标价格（可选）
 * @param riskBoundary 风险边界描述
 */
@Serializable
data class AiObservationPlan(
    val focusRangeLow: Double,
    val focusRangeHigh: Double,
    val confirmationCondition: String,
    val confirmationPrice: Double? = null,
    val referenceTarget: Double? = null,
    val riskBoundary: String,
)

/**
 * 结构化信号及其数据依据
 * @param title 信号标题，如"价格位置"
 * @param status 信号状态，如"昨收上方"
 * @param explanation 信号解读
 * @param evidence 数据依据
 */
@Serializable
data class AiSignal(
    val title: String,
    val status: String,
    val explanation: String,
    val evidence: String,
)

/**
 * AI股票分析结果，包含趋势判断、观察计划、风险提示等
 * @param trendJudgement 综合趋势判断
 * @param focusPoint 当前关注要点
 * @param riskReminder 风险提醒文本
 * @param signalInterpretation 信号解读汇总
 * @param factSummary 事实摘要
 * @param applicablePeriod 分析适用周期
 * @param evidenceSummary 数据依据汇总
 * @param updatedAt 分析所用行情时间
 * @param isDemo 是否为演示数据
 * @param trendType 趋势类型枚举
 * @param riskLevel 风险等级枚举
 * @param primaryRisks 主要风险列表
 * @param invalidationCondition 判断失效条件
 * @param observationPlan 观察计划（可选）
 * @param signals 结构化信号列表
 * @param source 分析来源
 * @param modelName AI模型名称（可选）
 * @param generatedAt AI生成时间
 * @param requestId 请求ID（可选）
 */
@Serializable
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

/**
 * AI市场概览，用于首页展示整体市场的AI分析结论
 * @param title 标题
 * @param sentiment 市场情绪描述
 * @param summary 市场摘要
 * @param riskTip 风险提示
 * @param updatedAt 更新时间
 */
data class AiMarketOverview(
    val title: String,
    val sentiment: String,
    val summary: String,
    val riskTip: String,
    val updatedAt: String,
)

/**
 * AI重点股票洞察卡片，包含股票报价和AI分析
 * @param quote 股票报价
 * @param analysis AI分析结果
 */
data class AiStockInsight(
    val quote: StockQuote,
    val analysis: AiAnalysis,
)
