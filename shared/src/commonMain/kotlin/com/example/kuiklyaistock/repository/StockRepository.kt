package com.example.kuiklyaistock.repository

import com.example.kuiklyaistock.model.AiAnalysis
import com.example.kuiklyaistock.model.AiMarketOverview
import com.example.kuiklyaistock.model.AiStockInsight
import com.example.kuiklyaistock.model.MarketIndexQuote
import com.example.kuiklyaistock.model.MarketSummary
import com.example.kuiklyaistock.model.StockDataSource
import com.example.kuiklyaistock.model.StockDetail
import com.example.kuiklyaistock.model.StockQuote
import com.example.kuiklyaistock.model.TrendPoint
import com.tencent.kuikly.core.coroutines.CoroutineScope
import com.tencent.kuikly.core.coroutines.delay
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round

sealed class StockLoadResult<out T> {
    data class Success<T>(val data: T) : StockLoadResult<T>()
    data object Empty : StockLoadResult<Nothing>()
    data class Failure(val message: String) : StockLoadResult<Nothing>()
}

data class StockHomeData(
    val quotes: List<StockQuote>,
    val marketSummary: MarketSummary,
    val dataSource: StockDataSource = StockDataSource.MOCK,
    val quoteTime: String = "",
    val requestCompletedAt: Long = 0L,
    val isExpired: Boolean = false,
    val missingCodes: List<String> = emptyList(),
)

data class StockDetailData(
    val detail: StockDetail,
    val analysis: AiAnalysis?,
    val dataSource: StockDataSource = StockDataSource.MOCK,
    val quoteTime: String = detail.quote.updatedAt,
    val requestCompletedAt: Long = 0L,
    val isExpired: Boolean = false,
    val missingCodes: List<String> = emptyList(),
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

// 股票数据仓库，真实行情和 Mock 实现共用此接口。
interface StockRepository {
    suspend fun loadHome(scope: CoroutineScope): StockLoadResult<StockHomeData>
    suspend fun refreshHome(scope: CoroutineScope): StockLoadResult<StockHomeData>
    suspend fun loadDetail(scope: CoroutineScope, code: String): StockLoadResult<StockDetailData>
    suspend fun loadAi(scope: CoroutineScope): StockLoadResult<StockAiData>
}

internal fun searchStockQuotes(quotes: List<StockQuote>, keyword: String): List<StockQuote> {
    val query = keyword.trim().lowercase()
    return quotes
        .filter { it.code.length == 6 && it.code.all(Char::isDigit) }
        .filter { query.isEmpty() || it.name.lowercase().contains(query) || it.code.contains(query) }
}

// 使用确定性本地行情，保证搜索、详情、K 线和交易链路都可离线演示。
class MockStockRepository(
    private val delayMillis: Int = 240,
    private val forcedFailures: Set<StockRequestType> = emptySet(),
    private val forcedEmptyResults: Set<StockRequestType> = emptySet(),
    private val analysisUnavailableCodes: Set<String> = emptySet(),
) : StockRepository {
    private val details: List<StockDetail> = MOCK_STOCK_SEEDS.mapIndexed(::createStockDetail)

    override suspend fun loadHome(scope: CoroutineScope): StockLoadResult<StockHomeData> {
        scope.waitForMockResponse()
        forcedResult<StockHomeData>(StockRequestType.HOME)?.let { return it }
        val quotes = details.map { it.quote }
        if (quotes.isEmpty()) return StockLoadResult.Empty
        return StockLoadResult.Success(StockHomeData(quotes, createMarketSummary(details)))
    }

    override suspend fun refreshHome(scope: CoroutineScope): StockLoadResult<StockHomeData> = loadHome(scope)

    override suspend fun loadDetail(scope: CoroutineScope, code: String): StockLoadResult<StockDetailData> {
        scope.waitForMockResponse()
        forcedResult<StockDetailData>(StockRequestType.DETAIL)?.let { return it }
        val normalized = code.trim()
        val detail = details.firstOrNull { it.quote.code == normalized }
            ?: return StockLoadResult.Empty
        return StockLoadResult.Success(
            StockDetailData(detail, if (normalized in analysisUnavailableCodes) null else createMockAiAnalysis(detail))
        )
    }

    override suspend fun loadAi(scope: CoroutineScope): StockLoadResult<StockAiData> {
        scope.waitForMockResponse()
        forcedResult<StockAiData>(StockRequestType.AI)?.let { return it }
        val insights = details.take(8).map { detail ->
            AiStockInsight(detail.quote, createMockAiAnalysis(detail))
        }
        if (insights.isEmpty()) return StockLoadResult.Empty
        return StockLoadResult.Success(StockAiData(createAiMarketOverview(), insights))
    }

    private fun createMarketSummary(details: List<StockDetail>): MarketSummary {
        val quotes = details.map { it.quote }
        val risingCount = quotes.count { it.change > 0.0 }
        val fallingCount = quotes.count { it.change < 0.0 }
        return MarketSummary(
            totalCount = quotes.size,
            risingCount = risingCount,
            fallingCount = fallingCount,
            flatCount = quotes.size - risingCount - fallingCount,
            sessionStatus = "已收盘",
            sampleTurnoverAmount = details.sumOf { it.turnover },
            sampleNetInflowAmount = details.sumOf { it.turnover * it.quote.changePercent / 100.0 * 0.08 },
            indices = listOf(
                MarketIndexQuote("上证指数", "000001", 3_951.51, 10.96, 0.28, MOCK_UPDATED_AT),
                MarketIndexQuote("深证成指", "399001", 13_723.32, 20.11, 0.15, MOCK_UPDATED_AT),
                MarketIndexQuote("创业板指", "399006", 3_354.97, -4.75, -0.14, MOCK_UPDATED_AT),
            ),
            updatedAt = MOCK_UPDATED_AT,
        )
    }

    private fun createAiMarketOverview(): AiMarketOverview = AiMarketOverview(
        title = "震荡中结构性机会占优",
        sentiment = "谨慎乐观",
        summary = "Mock 样本覆盖科技、消费、金融、新能源、医药和通信，板块表现分化。",
        riskTip = "关注高位波动、板块轮动加快和成交量回落风险。",
        updatedAt = MOCK_UPDATED_AT,
    )

    private suspend fun CoroutineScope.waitForMockResponse() {
        if (delayMillis > 0) delay(delayMillis)
    }

    private fun <T> forcedResult(type: StockRequestType): StockLoadResult<T>? = when {
        type in forcedFailures -> StockLoadResult.Failure("模拟数据加载失败")
        type in forcedEmptyResults -> StockLoadResult.Empty
        else -> null
    }

    private fun createStockDetail(index: Int, seed: MockStockSeed): StockDetail {
        val previousClose = round2(seed.price / (1.0 + seed.changePercent / 100.0))
        val change = round2(seed.price - previousClose)
        val changePercent = round2(change / previousClose * 100.0)
        val openOffset = ((index * 7) % 9 - 4) * 0.18
        val open = round2(previousClose * (1.0 + openOffset / 100.0))
        val amplitude = max(seed.price * (0.012 + index % 4 * 0.003), 0.08)
        val high = round2(max(max(open, seed.price), previousClose) + amplitude)
        val low = round2(max(0.01, min(min(open, seed.price), previousClose) - amplitude * 0.82))
        val volume = 5_000_000L + (index * 3_760_000L) % 58_000_000L
        val turnover = round(volume * seed.price * 100.0) / 100.0
        val quote = StockQuote(
            name = seed.name,
            code = seed.code,
            price = seed.price,
            change = change,
            changePercent = changePercent,
            updatedAt = MOCK_UPDATED_AT,
        )
        return StockDetail(
            quote = quote,
            open = open,
            previousClose = previousClose,
            high = high,
            low = low,
            volume = volume,
            turnover = turnover,
            intradayTrend = createTrend(previousClose, seed.price, 13, amplitude * 0.42, index)
                .mapIndexed { pointIndex, value -> TrendPoint(INTRADAY_LABELS[pointIndex], value) },
            dailyTrend = createTrend(previousClose * (1.0 - seed.changePercent / 180.0), seed.price, 20, amplitude, index + 11)
                .mapIndexed { pointIndex, value -> TrendPoint("${pointIndex + 1}日", value) },
        )
    }

    private fun createTrend(
        start: Double,
        end: Double,
        count: Int,
        amplitude: Double,
        seed: Int,
    ): List<Double> = (0 until count).map { index ->
        if (index == count - 1) {
            end
        } else {
            val progress = index.toDouble() / (count - 1)
            val wave = (((index * 7 + seed * 3) % 9) - 4) / 4.0
            round2(max(0.01, start + (end - start) * progress + wave * amplitude))
        }
    }

    private fun round2(value: Double): Double = round(value * 100.0) / 100.0

    private data class MockStockSeed(
        val name: String,
        val code: String,
        val price: Double,
        val changePercent: Double,
        val industry: String,
    )

    private companion object {
        const val MOCK_UPDATED_AT = "2026-09-10 15:00"
        val INTRADAY_LABELS = listOf(
            "9:30", "9:50", "10:10", "10:30", "10:50", "11:10", "11:30",
            "13:20", "13:40", "14:00", "14:20", "14:40", "15:00",
        )
        val MOCK_STOCK_SEEDS = listOf(
            MockStockSeed("中科曙光", "603019", 74.28, 8.46, "科技"),
            MockStockSeed("工业富联", "601138", 26.91, 6.72, "科技"),
            MockStockSeed("宁德时代", "300750", 211.37, 5.19, "新能源"),
            MockStockSeed("比亚迪", "002594", 102.66, -2.31, "新能源"),
            MockStockSeed("东方财富", "300059", 24.80, 3.88, "金融"),
            MockStockSeed("贵州茅台", "600519", 1_568.20, -0.86, "消费"),
            MockStockSeed("五粮液", "000858", 132.45, 1.24, "消费"),
            MockStockSeed("伊利股份", "600887", 28.63, -0.42, "消费"),
            MockStockSeed("美的集团", "000333", 76.18, 1.65, "消费"),
            MockStockSeed("格力电器", "000651", 45.72, -1.13, "消费"),
            MockStockSeed("海天味业", "603288", 42.36, 0.57, "消费"),
            MockStockSeed("中国平安", "601318", 58.44, -0.68, "金融"),
            MockStockSeed("招商银行", "600036", 43.91, 1.08, "金融"),
            MockStockSeed("工商银行", "601398", 7.36, 0.41, "金融"),
            MockStockSeed("中信证券", "600030", 29.64, -1.76, "金融"),
            MockStockSeed("立讯精密", "002475", 48.52, 2.83, "科技"),
            MockStockSeed("海康威视", "002415", 32.78, -0.94, "科技"),
            MockStockSeed("兆易创新", "603986", 128.60, 4.16, "科技"),
            MockStockSeed("韦尔股份", "603501", 116.35, 2.07, "科技"),
            MockStockSeed("科大讯飞", "002230", 54.21, -1.38, "科技"),
            MockStockSeed("隆基绿能", "601012", 18.72, 2.24, "新能源"),
            MockStockSeed("阳光电源", "300274", 96.48, 3.51, "新能源"),
            MockStockSeed("赣锋锂业", "002460", 39.66, -2.46, "新能源"),
            MockStockSeed("亿纬锂能", "300014", 51.27, 1.92, "新能源"),
            MockStockSeed("天齐锂业", "002466", 38.84, -1.55, "新能源"),
            MockStockSeed("迈瑞医疗", "300760", 248.90, 1.37, "医药"),
            MockStockSeed("恒瑞医药", "600276", 55.62, -0.73, "医药"),
            MockStockSeed("药明康德", "603259", 72.15, 2.61, "医药"),
            MockStockSeed("爱尔眼科", "300015", 13.84, -1.07, "医药"),
            MockStockSeed("中国移动", "600941", 114.36, 0.62, "通信"),
            MockStockSeed("中兴通讯", "000063", 41.28, 3.06, "通信"),
            MockStockSeed("中国联通", "600050", 5.89, -0.34, "通信"),
            MockStockSeed("三一重工", "600031", 19.76, 1.44, "工业"),
            MockStockSeed("万华化学", "600309", 78.53, -1.26, "材料"),
            MockStockSeed("紫金矿业", "601899", 19.32, 2.18, "材料"),
            MockStockSeed("长江电力", "600900", 29.18, 0.28, "公用事业"),
        )
    }
}
