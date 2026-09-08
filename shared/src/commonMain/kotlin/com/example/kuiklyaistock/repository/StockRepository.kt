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
            name = "腾讯控股",
            code = "00700",
            price = 382.40,
            change = 6.80,
            changePercent = 1.81,
            open = 375.60,
            previousClose = 375.60,
            high = 386.20,
            low = 374.60,
            volume = 18_320_000,
            turnover = 6_982_000_000.0,
            trend = listOf(375.2, 377.8, 376.4, 380.1, 379.6, 383.0, 382.4),
            dailyTrend = listOf(362.0, 365.5, 368.2, 371.6, 374.9, 378.1, 382.4),
        ),
        stock(
            name = "贵州茅台",
            code = "600519",
            price = 1_682.00,
            change = -12.00,
            changePercent = -0.71,
            open = 1_698.50,
            previousClose = 1_694.00,
            high = 1_704.50,
            low = 1_668.80,
            volume = 2_140_000,
            turnover = 3_610_000_000.0,
            trend = listOf(1702.0, 1696.4, 1688.2, 1691.5, 1680.0, 1686.5, 1682.0),
            dailyTrend = listOf(1724.0, 1712.0, 1706.5, 1698.0, 1689.0, 1686.5, 1682.0),
        ),
        stock(
            name = "宁德时代",
            code = "300750",
            price = 214.76,
            change = 4.26,
            changePercent = 2.02,
            open = 210.50,
            previousClose = 210.50,
            high = 216.80,
            low = 208.30,
            volume = 12_870_000,
            turnover = 2_745_000_000.0,
            trend = listOf(208.3, 209.7, 211.4, 210.2, 213.5, 215.8, 214.76),
            dailyTrend = listOf(202.6, 205.0, 207.8, 209.2, 211.5, 213.0, 214.76),
        ),
        stock(
            name = "中国平安",
            code = "601318",
            price = 48.63,
            change = -0.37,
            changePercent = -0.76,
            open = 49.05,
            previousClose = 49.00,
            high = 49.20,
            low = 48.15,
            volume = 9_540_000,
            turnover = 464_000_000.0,
            trend = listOf(49.12, 48.96, 48.74, 48.82, 48.44, 48.76, 48.63),
            dailyTrend = listOf(50.20, 49.80, 49.36, 49.12, 48.88, 48.74, 48.63),
        ),
        stock(
            name = "比亚迪",
            code = "002594",
            price = 246.18,
            change = 3.18,
            changePercent = 1.31,
            open = 243.10,
            previousClose = 243.00,
            high = 248.60,
            low = 241.90,
            volume = 8_260_000,
            turnover = 2_028_000_000.0,
            trend = listOf(242.1, 243.6, 245.0, 244.2, 246.8, 247.3, 246.18),
            dailyTrend = listOf(238.0, 240.6, 241.2, 242.8, 244.0, 245.4, 246.18),
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
            updatedAt = "2026-09-07 15:00",
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
            updatedAt = "2026-09-07 15:00",
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
            updatedAt = "2026-09-07 15:00",
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
            updatedAt = "2026-09-07 15:00",
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
            updatedAt = "2026-09-07 15:00",
            isDemo = true,
        ),
    )

    override suspend fun loadHome(scope: CoroutineScope): StockLoadResult<StockHomeData> {
        scope.waitForMockResponse()
        forcedResult<StockHomeData>(StockRequestType.HOME)?.let { return it }
        val quotes = details.map { it.quote }
        if (quotes.isEmpty()) return StockLoadResult.Empty
        return StockLoadResult.Success(StockHomeData(quotes, createMarketSummary(quotes)))
    }

    private fun createMarketSummary(quotes: List<StockQuote>): MarketSummary {
        return MarketSummary(
            totalCount = quotes.size,
            risingCount = quotes.count { it.isRising },
            fallingCount = quotes.count { it.isFalling },
            flatCount = quotes.count { !it.isRising && !it.isFalling },
            sessionStatus = "已收盘",
            turnover = details.sumOf { it.turnover },
            indices = listOf(
                MarketIndexQuote("上证指数", "000001", 3_280.12, 13.66, 0.42, "2026-09-07 15:00"),
                MarketIndexQuote("深证成指", "399001", 10_456.20, -18.85, -0.18, "2026-09-07 15:00"),
            ),
            updatedAt = quotes.firstOrNull()?.updatedAt.orEmpty(),
        )
    }

    override suspend fun loadDetail(scope: CoroutineScope, code: String): StockLoadResult<StockDetailData> {
        scope.waitForMockResponse()
        forcedResult<StockDetailData>(StockRequestType.DETAIL)?.let { return it }
        val normalized = code.trim()
        val detail = details.firstOrNull { it.quote.code == normalized || it.quote.symbol == normalized }
            ?: return StockLoadResult.Empty
        return StockLoadResult.Success(StockDetailData(detail, analyses[normalized]))
    }

    override suspend fun loadAi(scope: CoroutineScope): StockLoadResult<StockAiData> {
        scope.waitForMockResponse()
        forcedResult<StockAiData>(StockRequestType.AI)?.let { return it }
        val insights = details.mapNotNull { detail ->
            analyses[detail.quote.code]?.let { analysis -> AiStockInsight(detail.quote, analysis) }
        }
        if (insights.isEmpty()) return StockLoadResult.Empty
        return StockLoadResult.Success(StockAiData(createAiMarketOverview(), insights))
    }

    private fun createAiMarketOverview(): AiMarketOverview = AiMarketOverview(
        title = "震荡中结构性机会占优",
        sentiment = "谨慎乐观",
        summary = "样本股票中科技与新能源方向表现较强，权重消费和金融仍处于整理阶段。",
        riskTip = "关注高位波动、板块轮动加快和成交量回落风险。",
        updatedAt = "2026-09-07 15:00",
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
        val quote = StockQuote(name, code, price, change, changePercent, "2026-09-07 15:00")
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
