package com.example.kuiklyaistock

import com.example.kuiklyaistock.model.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse

/**
 * 股票数据模型单元测试
 * 验证核心数据结构的正确性
 */
class StockModelsTest {

    @Test
    fun `StockQuote 应该正确判断涨跌状态`() {
        // 上涨股票
        val risingStock = StockQuote(
            name = "中国平安",
            code = "sh601318",
            price = 50.5,
            change = 1.2,
            changePercent = 2.43,
            updatedAt = "2024-09-12 15:00:00"
        )
        assertTrue(risingStock.isRising, "涨跌额为正应该是上涨")
        assertFalse(risingStock.isFalling, "涨跌额为正不应该是下跌")

        // 下跌股票
        val fallingStock = StockQuote(
            name = "贵州茅台",
            code = "sh600519",
            price = 1580.0,
            change = -15.0,
            changePercent = -0.94,
            updatedAt = "2024-09-12 15:00:00"
        )
        assertFalse(fallingStock.isRising, "涨跌额为负不应该是上涨")
        assertTrue(fallingStock.isFalling, "涨跌额为负应该是下跌")

        // 平盘股票
        val flatStock = StockQuote(
            name = "招商银行",
            code = "sh600036",
            price = 35.0,
            change = 0.0,
            changePercent = 0.0,
            updatedAt = "2024-09-12 15:00:00"
        )
        assertFalse(flatStock.isRising, "涨跌额为0不应该是上涨")
        assertFalse(flatStock.isFalling, "涨跌额为0不应该是下跌")
    }

    @Test
    fun `StockDataSource 应该正确显示数据来源名称`() {
        assertEquals("真实行情", StockDataSource.REMOTE.displayName())
        assertEquals("缓存行情", StockDataSource.CACHE.displayName())
        assertEquals("Mock 行情", StockDataSource.MOCK.displayName())
    }

    @Test
    fun `MarketSummary 应该包含完整市场统计信息`() {
        val summary = MarketSummary(
            totalCount = 5000,
            risingCount = 2800,
            fallingCount = 2000,
            flatCount = 200,
            sessionStatus = "盘中",
            sampleTurnoverAmount = 5000000000.0,
            sampleNetInflowAmount = 1200000000.0,
            indices = listOf(
                MarketIndexQuote("上证指数", "sh000001", 3200.5, 12.3, 0.39, "2024-09-12 15:00:00"),
                MarketIndexQuote("深证成指", "sz399001", 10500.2, -50.1, -0.47, "2024-09-12 15:00:00"),
                MarketIndexQuote("创业板指", "sz399006", 2100.8, 5.2, 0.25, "2024-09-12 15:00:00")
            ),
            updatedAt = "2024-09-12 15:00:00"
        )

        assertEquals(5000, summary.totalCount)
        assertEquals(2800, summary.risingCount)
        assertEquals(3, summary.indices.size)
        assertEquals("上证指数", summary.indices[0].name)
    }

    @Test
    fun `AiTrendType 应该有正确的标签`() {
        assertEquals("偏强", AiTrendType.STRONG.label)
        assertEquals("震荡", AiTrendType.SIDEWAYS.label)
        assertEquals("偏弱", AiTrendType.WEAK.label)
    }

    @Test
    fun `AiRiskLevel 应该有正确的标签`() {
        assertEquals("低风险", AiRiskLevel.LOW.label)
        assertEquals("中风险", AiRiskLevel.MEDIUM.label)
        assertEquals("高风险", AiRiskLevel.HIGH.label)
    }

    @Test
    fun `StockDetail 应该包含完整行情数据`() {
        val quote = StockQuote(
            name = "中国平安",
            code = "sh601318",
            price = 50.5,
            change = 1.2,
            changePercent = 2.43,
            updatedAt = "2024-09-12 15:00:00"
        )

        val detail = StockDetail(
            quote = quote,
            open = 49.8,
            previousClose = 49.3,
            high = 50.8,
            low = 49.5,
            volume = 50000000,
            turnover = 2500000000.0,
            turnoverRate = 1.5,
            peRatio = 8.5,
            intradayTrend = listOf(
                TrendPoint("09:30", 49.8, 1000000.0),
                TrendPoint("10:00", 50.0, 1200000.0),
                TrendPoint("11:30", 50.5, 1500000.0)
            )
        )

        assertEquals("中国平安", detail.quote.name)
        assertEquals(49.8, detail.open)
        assertEquals(49.3, detail.previousClose)
        assertEquals(50.8, detail.high)
        assertEquals(49.5, detail.low)
        assertEquals(50000000L, detail.volume)
        assertEquals(1.5, detail.turnoverRate)
        assertEquals(8.5, detail.peRatio)
        assertEquals(3, detail.intradayTrend.size)
    }

    @Test
    fun `AiAnalysis 应该包含完整分析结果`() {
        val analysis = AiAnalysis(
            trendJudgement = "价格在昨收上方运行，短线偏强",
            focusPoint = "关注能否守住昨收上方",
            riskReminder = "涨幅较大，注意回调风险",
            signalInterpretation = "价格位置：昨收上方；成交量：放量",
            factSummary = "当前价 50.5 元，涨幅 2.43%",
            applicablePeriod = "当日",
            evidenceSummary = "今开 49.8，昨收 49.3，最新 50.5",
            updatedAt = "2024-09-12 15:00:00",
            isDemo = true,
            trendType = AiTrendType.STRONG,
            riskLevel = AiRiskLevel.MEDIUM,
            primaryRisks = listOf("短线涨幅较大", "成交活跃度需观察"),
            invalidationCondition = "若跌破昨收则判断失效",
            source = AiAnalysisSource.MOCK
        )

        assertEquals(AiTrendType.STRONG, analysis.trendType)
        assertEquals(AiRiskLevel.MEDIUM, analysis.riskLevel)
        assertEquals(2, analysis.primaryRisks.size)
        assertTrue(analysis.isDemo)
        assertEquals(AiAnalysisSource.MOCK, analysis.source)
    }
}
