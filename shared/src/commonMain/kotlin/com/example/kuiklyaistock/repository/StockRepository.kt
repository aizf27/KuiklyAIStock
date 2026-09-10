package com.example.kuiklyaistock.repository

import com.example.kuiklyaistock.model.AiAnalysis
import com.example.kuiklyaistock.model.AiMarketOverview
import com.example.kuiklyaistock.model.AiStockInsight
import com.example.kuiklyaistock.model.MarketSummary
import com.example.kuiklyaistock.model.MarketIndexQuote
import com.example.kuiklyaistock.model.StockDetail
import com.example.kuiklyaistock.model.StockQuote
import com.example.kuiklyaistock.model.TrendPoint
import com.tencent.kuikly.core.coroutines.delay
import com.tencent.kuikly.core.coroutines.CoroutineScope

sealed class StockLoadResult<out T> {
    data class Success<T>(val data: T) : StockLoadResult<T>()
    data object Empty : StockLoadResult<Nothing>()
    data class Failure(val message: String) : StockLoadResult<Nothing>()
}

data class StockHomeData(
    val quotes: List<StockQuote>,
    val marketSummary: MarketSummary,
)

data class StockDetailData(
    val detail: StockDetail,
    val analysis: AiAnalysis?,
)

data class StockAiData(
    val overview: AiMarketOverview,
    val insights: List<AiStockInsight>,
)

enum class StockRequestType {
    HOME,
    DETAIL,
    AI,
}

// 股票数据仓库，后续可替换为真实行情服务。
interface StockRepository {
    suspend fun loadHome(scope: CoroutineScope): StockLoadResult<StockHomeData>
    suspend fun loadDetail(scope: CoroutineScope, code: String): StockLoadResult<StockDetailData>
    suspend fun loadAi(scope: CoroutineScope): StockLoadResult<StockAiData>
}

// 第一阶段使用的确定性本地数据。
class MockStockRepository(
    private val delayMillis: Int = 240,
    private val forcedFailures: Set<StockRequestType> = emptySet(),
    private val forcedEmptyResults: Set<StockRequestType> = emptySet(),
) : StockRepository {
    private val details: List<StockDetail> = listOf(
        stock(
            name = "中科曙光",
            code = "603019",
            price = 74.28,
            change = 5.80,
            changePercent = 8.46,
            open = 69.10,
            previousClose = 68.48,
            high = 75.20,
            low = 68.80,
            volume = 28_600_000,
            turnover = 2_124_000_000.0,
            trend = listOf(68.6, 69.8, 70.4, 72.1, 73.6, 74.9, 74.28),
            dailyTrend = listOf(64.8, 66.2, 67.0, 68.4, 70.1, 72.6, 74.28),
        ),
        stock(
            name = "工业富联",
            code = "601138",
            price = 26.91,
            change = 1.70,
            changePercent = 6.72,
            open = 25.40,
            previousClose = 25.21,
            high = 27.20,
            low = 25.18,
            volume = 46_200_000,
            turnover = 1_243_000_000.0,
            trend = listOf(25.3, 25.6, 26.0, 26.3, 26.7, 27.0, 26.91),
            dailyTrend = listOf(23.8, 24.2, 24.6, 24.9, 25.3, 26.1, 26.91),
        ),
        stock(
            name = "宁德时代",
            code = "300750",
            price = 211.37,
            change = 10.42,
            changePercent = 5.19,
            open = 202.60,
            previousClose = 200.95,
            high = 213.40,
            low = 201.80,
            volume = 18_870_000,
            turnover = 3_984_000_000.0,
            trend = listOf(201.8, 204.2, 205.6, 207.8, 209.5, 212.0, 211.37),
            dailyTrend = listOf(193.8, 196.1, 198.4, 201.2, 204.8, 208.3, 211.37),
        ),
        stock(
            name = "比亚迪",
            code = "002594",
            price = 102.66,
            change = -2.43,
            changePercent = -2.31,
            open = 104.20,
            previousClose = 105.09,
            high = 104.80,
            low = 101.90,
            volume = 22_260_000,
            turnover = 2_287_000_000.0,
            trend = listOf(104.8, 104.2, 103.9, 103.4, 102.8, 102.4, 102.66),
            dailyTrend = listOf(108.2, 107.4, 106.3, 105.8, 104.9, 103.7, 102.66),
        ),
        stock(
            name = "东方财富",
            code = "300059",
            price = 24.80,
            change = 0.93,
            changePercent = 3.88,
            open = 24.02,
            previousClose = 23.87,
            high = 25.16,
            low = 23.90,
            volume = 35_540_000,
            turnover = 1_672_000_000.0,
            trend = listOf(23.9, 24.1, 24.2, 24.5, 24.7, 24.9, 24.8),
            dailyTrend = listOf(22.7, 23.0, 23.4, 23.6, 24.0, 24.4, 24.8),
        ),
    )

    private val analyses: Map<String, AiAnalysis> = mapOf(
        "00700" to AiAnalysis(
            trendJudgement = "短线偏强，价格站稳日内均价上方。",
            focusPoint = "关注 380 港元附近支撑与成交量是否延续。",
            riskReminder = "海外科技板块波动可能放大日内回撤。",
            signalInterpretation = "成交量较前一时段放大，买方信号保持。",
            factSummary = "腾讯控股日内震荡上行，样本市场情绪偏积极。",
            applicablePeriod = "短线 1-5 日",
            evidenceSummary = "价格位于日内高位附近，成交量较前段放大。",
            updatedAt = "2026-09-10 15:00",
            isDemo = true,
        ),
        "600519" to AiAnalysis(
            trendJudgement = "短线震荡偏弱，尚未收复早盘高点。",
            focusPoint = "关注价格能否重回 1,700 元以及消费板块强弱。",
            riskReminder = "消费板块需求预期变化会带来估值压力。",
            signalInterpretation = "高位缩量回落，暂未出现明确反转信号。",
            factSummary = "贵州茅台小幅回调，防守情绪占优。",
            applicablePeriod = "短线 1-5 日",
            evidenceSummary = "价格低于今开，日内反弹未突破早盘高点。",
            updatedAt = "2026-09-10 15:00",
            isDemo = true,
        ),
        "300750" to AiAnalysis(
            trendJudgement = "趋势偏强，回踩后保持上行结构。",
            focusPoint = "关注 212 元附近支撑与量价配合是否持续。",
            riskReminder = "行业竞争加剧，业绩兑现节奏仍需验证。",
            signalInterpretation = "短周期均线金叉，动能指标逐步改善。",
            factSummary = "宁德时代放量反弹，新能源方向活跃。",
            applicablePeriod = "短线 1-5 日",
            evidenceSummary = "价格高于今开，日内量价同步回升。",
            updatedAt = "2026-09-10 15:00",
            isDemo = true,
        ),
        "601318" to AiAnalysis(
            trendJudgement = "窄幅震荡，方向选择尚不明确。",
            focusPoint = "关注 48 元至 49.5 元区间是否出现有效突破。",
            riskReminder = "金融权重走弱可能拖累整体表现。",
            signalInterpretation = "波动率下降，等待成交量重新放大。",
            factSummary = "中国平安横盘整理，资金观望明显。",
            applicablePeriod = "短线 1-5 日",
            evidenceSummary = "价格靠近日内低位，成交量未出现明显放大。",
            updatedAt = "2026-09-10 15:00",
            isDemo = true,
        ),
        "002594" to AiAnalysis(
            trendJudgement = "震荡向上，短线多头略占优势。",
            focusPoint = "关注 242 元支撑与 248 元附近的成交量变化。",
            riskReminder = "汽车价格竞争和原材料成本仍是主要风险。",
            signalInterpretation = "量价同步回升，反弹信号相对积极。",
            factSummary = "比亚迪温和反弹，市场关注度回升。",
            applicablePeriod = "短线 1-5 日",
            evidenceSummary = "价格高于昨收，午后保持温和上行。",
            updatedAt = "2026-09-10 15:00",
            isDemo = true,
        ),
    )

    override suspend fun loadHome(scope: CoroutineScope): StockLoadResult<StockHomeData> {
        scope.waitForMockResponse()
        forcedResult<StockHomeData>(StockRequestType.HOME)?.let { return it }
        val quotes = details.map { it.quote }
        if (quotes.isEmpty()) return StockLoadResult.Empty
        return StockLoadResult.Success(StockHomeData(quotes, createMarketSummary(quotes.filter { it.code.length == 6 })))
    }

    private fun createMarketSummary(quotes: List<StockQuote>): MarketSummary {
        return MarketSummary(
            totalCount = 5_436,
            risingCount = 1_794,
            fallingCount = 3_642,
            flatCount = 0,
            sessionStatus = "已收盘",
            sampleTurnoverAmount = 1_873_100_000_000.0,
            sampleNetInflowAmount = -10_665_000_000.0,
            indices = listOf(
                MarketIndexQuote("上证指数", "000001", 3_951.51, 10.96, 0.28, "2026-09-10 15:00"),
                MarketIndexQuote("深证成指", "399001", 13_723.32, 20.11, 0.15, "2026-09-10 15:00"),
                MarketIndexQuote("创业板指", "399006", 3_354.97, -4.75, -0.14, "2026-09-10 15:00"),
            ),
            updatedAt = "2026-09-10 15:00",
        )
    }

    override suspend fun loadDetail(scope: CoroutineScope, code: String): StockLoadResult<StockDetailData> {
        scope.waitForMockResponse()
        forcedResult<StockDetailData>(StockRequestType.DETAIL)?.let { return it }
        val normalized = code.trim()
        val detail = details.firstOrNull { it.quote.code == normalized }
            ?: return StockLoadResult.Empty
        return StockLoadResult.Success(StockDetailData(detail, analyses[normalized] ?: createDefaultAnalysis(detail)))
    }

    override suspend fun loadAi(scope: CoroutineScope): StockLoadResult<StockAiData> {
        scope.waitForMockResponse()
        forcedResult<StockAiData>(StockRequestType.AI)?.let { return it }
        val insights = details.map { detail ->
            AiStockInsight(detail.quote, analyses[detail.quote.code] ?: createDefaultAnalysis(detail))
        }
        if (insights.isEmpty()) return StockLoadResult.Empty
        return StockLoadResult.Success(StockAiData(createAiMarketOverview(), insights))
    }

    private fun createAiMarketOverview(): AiMarketOverview = AiMarketOverview(
        title = "震荡中结构性机会占优",
        sentiment = "谨慎乐观",
        summary = "样本股票中科技与新能源方向表现较强，权重消费和金融仍处于整理阶段。",
        riskTip = "关注高位波动、板块轮动加快和成交量回落风险。",
        updatedAt = "2026-09-10 15:00",
    )

    private fun createDefaultAnalysis(detail: StockDetail): AiAnalysis = AiAnalysis(
        trendJudgement = "样本走势随市场波动，当前仅用于界面演示。",
        focusPoint = "关注成交量变化、关键价位和所属板块强弱。",
        riskReminder = "Mock 数据不构成投资建议，真实交易需结合最新公告与行情。",
        signalInterpretation = "当前信号来自本地样本数据，不代表真实量化结论。",
        factSummary = "${detail.quote.name}当前样本涨跌幅为${detail.quote.changePercent}%。",
        applicablePeriod = "界面演示",
        evidenceSummary = "价格、成交量和走势图均为确定性 Mock 数据。",
        updatedAt = "2026-09-10 15:00",
        isDemo = true,
    )

    private suspend fun CoroutineScope.waitForMockResponse() {
        if (delayMillis > 0) delay(delayMillis)
    }

    private fun <T> forcedResult(type: StockRequestType): StockLoadResult<T>? {
        return when {
            type in forcedFailures -> StockLoadResult.Failure("模拟数据加载失败")
            type in forcedEmptyResults -> StockLoadResult.Empty
            else -> null
        }
    }

    private fun stock(
        name: String,
        code: String,
        price: Double,
        change: Double,
        changePercent: Double,
        open: Double,
        previousClose: Double,
        high: Double,
        low: Double,
        volume: Long,
        turnover: Double,
        trend: List<Double>,
        dailyTrend: List<Double>,
    ): StockDetail {
        val quote = StockQuote(name, code, price, change, changePercent, "2026-09-10 15:00")
        return StockDetail(
            quote = quote,
            open = open,
            previousClose = previousClose,
            high = high,
            low = low,
            volume = volume,
            turnover = turnover,
            intradayTrend = trend.mapIndexed { index, value -> TrendPoint("${index + 9}:30", value) },
            dailyTrend = dailyTrend.mapIndexed { index, value -> TrendPoint("${index + 1}日", value) },
        )
    }
}
