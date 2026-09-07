package com.example.kuiklyaistock.repository

import com.example.kuiklyaistock.model.AiAnalysis
import com.example.kuiklyaistock.model.AiMarketOverview
import com.example.kuiklyaistock.model.AiStockInsight
import com.example.kuiklyaistock.model.MarketSummary
import com.example.kuiklyaistock.model.StockDetail
import com.example.kuiklyaistock.model.StockQuote
import com.example.kuiklyaistock.model.TrendPoint

// 股票数据仓库，后续可替换为真实行情服务。
interface StockRepository {
    fun getQuotes(): List<StockQuote>
    fun getMarketSummary(): MarketSummary
    fun getDetail(code: String): StockDetail?
    fun getAiAnalysis(code: String): AiAnalysis?
    fun getAiMarketOverview(): AiMarketOverview
    fun getAiInsights(): List<AiStockInsight>
}

// 第一阶段使用的确定性本地数据。
class MockStockRepository : StockRepository {
    private val details: List<StockDetail> = listOf(
        stock(
            name = "腾讯控股",
            code = "00700",
            price = 382.40,
            change = 6.80,
            changePercent = 1.81,
            high = 386.20,
            low = 374.60,
            volume = 18_320_000,
            turnover = 6_982_000_000.0,
            trend = listOf(375.2, 377.8, 376.4, 380.1, 379.6, 383.0, 382.4),
        ),
        stock(
            name = "贵州茅台",
            code = "600519",
            price = 1_682.00,
            change = -12.00,
            changePercent = -0.71,
            high = 1_704.50,
            low = 1_668.80,
            volume = 2_140_000,
            turnover = 3_610_000_000.0,
            trend = listOf(1702.0, 1696.4, 1688.2, 1691.5, 1680.0, 1686.5, 1682.0),
        ),
        stock(
            name = "宁德时代",
            code = "300750",
            price = 214.76,
            change = 4.26,
            changePercent = 2.02,
            high = 216.80,
            low = 208.30,
            volume = 12_870_000,
            turnover = 2_745_000_000.0,
            trend = listOf(208.3, 209.7, 211.4, 210.2, 213.5, 215.8, 214.76),
        ),
        stock(
            name = "中国平安",
            code = "601318",
            price = 48.63,
            change = -0.37,
            changePercent = -0.76,
            high = 49.20,
            low = 48.15,
            volume = 9_540_000,
            turnover = 464_000_000.0,
            trend = listOf(49.12, 48.96, 48.74, 48.82, 48.44, 48.76, 48.63),
        ),
        stock(
            name = "比亚迪",
            code = "002594",
            price = 246.18,
            change = 3.18,
            changePercent = 1.31,
            high = 248.60,
            low = 241.90,
            volume = 8_260_000,
            turnover = 2_028_000_000.0,
            trend = listOf(242.1, 243.6, 245.0, 244.2, 246.8, 247.3, 246.18),
        ),
    )

    private val analyses: Map<String, AiAnalysis> = mapOf(
        "00700" to AiAnalysis(
            trendJudgement = "短线偏强，价格站稳日内均价上方。",
            operationTip = "关注 380 港元附近支撑，分批观察，不追高。",
            riskReminder = "海外科技板块波动可能放大日内回撤。",
            signalInterpretation = "成交量较前一时段放大，买方信号保持。",
            marketSummary = "腾讯控股日内震荡上行，市场情绪偏积极。",
        ),
        "600519" to AiAnalysis(
            trendJudgement = "短线震荡偏弱，尚未收复早盘高点。",
            operationTip = "等待价格重新站上 1,700 元，再评估加仓。",
            riskReminder = "消费板块需求预期变化会带来估值压力。",
            signalInterpretation = "高位缩量回落，暂未出现明确反转信号。",
            marketSummary = "贵州茅台小幅回调，防守情绪占优。",
        ),
        "300750" to AiAnalysis(
            trendJudgement = "趋势偏强，回踩后保持上行结构。",
            operationTip = "可沿 212 元附近支撑观察量价配合。",
            riskReminder = "行业竞争加剧，业绩兑现节奏仍需验证。",
            signalInterpretation = "短周期均线金叉，动能指标逐步改善。",
            marketSummary = "宁德时代放量反弹，新能源方向活跃。",
        ),
        "601318" to AiAnalysis(
            trendJudgement = "窄幅震荡，方向选择尚不明确。",
            operationTip = "以 48 元至 49.5 元区间高抛低吸为主。",
            riskReminder = "金融权重走弱可能拖累整体表现。",
            signalInterpretation = "波动率下降，等待成交量重新放大。",
            marketSummary = "中国平安横盘整理，资金观望明显。",
        ),
        "002594" to AiAnalysis(
            trendJudgement = "震荡向上，短线多头略占优势。",
            operationTip = "关注 242 元支撑，突破 248 元可看高一线。",
            riskReminder = "汽车价格竞争和原材料成本仍是主要风险。",
            signalInterpretation = "量价同步回升，反弹信号相对积极。",
            marketSummary = "比亚迪温和反弹，市场关注度回升。",
        ),
    )

    override fun getQuotes(): List<StockQuote> = details.map { it.quote }

    override fun getMarketSummary(): MarketSummary {
        val quotes = getQuotes()
        return MarketSummary(
            totalCount = quotes.size,
            risingCount = quotes.count { it.isRising },
            fallingCount = quotes.count { it.isFalling },
            flatCount = quotes.count { !it.isRising && !it.isFalling },
            updatedAt = quotes.firstOrNull()?.updatedAt.orEmpty(),
        )
    }

    override fun getDetail(code: String): StockDetail? {
        val normalized = code.trim()
        return details.firstOrNull { it.quote.code == normalized || it.quote.symbol == normalized }
    }

    override fun getAiAnalysis(code: String): AiAnalysis? = analyses[code.trim()]

    override fun getAiMarketOverview(): AiMarketOverview = AiMarketOverview(
        title = "震荡中结构性机会占优",
        sentiment = "谨慎乐观",
        summary = "样本股票中科技与新能源方向表现较强，权重消费和金融仍处于整理阶段。",
        riskTip = "关注高位波动、板块轮动加快和成交量回落风险。",
        updatedAt = "2026-09-07 15:00",
    )

    override fun getAiInsights(): List<AiStockInsight> = details.mapNotNull { detail ->
        analyses[detail.quote.code]?.let { analysis ->
            AiStockInsight(detail.quote, analysis)
        }
    }

    private fun stock(
        name: String,
        code: String,
        price: Double,
        change: Double,
        changePercent: Double,
        high: Double,
        low: Double,
        volume: Long,
        turnover: Double,
        trend: List<Double>,
    ): StockDetail {
        val quote = StockQuote(name, code, price, change, changePercent, "2026-09-07 15:00")
        return StockDetail(
            quote = quote,
            high = high,
            low = low,
            volume = volume,
            turnover = turnover,
            trend = trend.mapIndexed { index, value -> TrendPoint("${index + 9}:30", value) },
        )
    }
}
