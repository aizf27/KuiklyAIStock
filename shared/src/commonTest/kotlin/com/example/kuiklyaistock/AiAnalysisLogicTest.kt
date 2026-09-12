package com.example.kuiklyaistock

import com.example.kuiklyaistock.model.*
import com.example.kuiklyaistock.repository.createMockAiAnalysis
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertNotNull

/**
 * AI 分析业务逻辑测试
 * 验证 Mock 数据生成的正确性
 */
class AiAnalysisLogicTest {

    @Test
    fun `Mock AI 分析应该根据涨幅判断趋势`() {
        // 测试上涨股票（涨幅 >= 2%）
        val risingDetail = createStockDetail(
            price = 51.0,
            previousClose = 50.0,
            changePercent = 2.0
        )
        val risingAnalysis = createMockAiAnalysis(risingDetail)
        assertEquals(AiTrendType.STRONG, risingAnalysis.trendType, "涨幅 >= 2% 应该判断为偏强")

        // 测试下跌股票（涨幅 < 0）
        val fallingDetail = createStockDetail(
            price = 48.5,
            previousClose = 50.0,
            changePercent = -3.0
        )
        val fallingAnalysis = createMockAiAnalysis(fallingDetail)
        assertEquals(AiTrendType.WEAK, fallingAnalysis.trendType, "涨幅 < 0 应该判断为偏弱")

        // 测试震荡股票（0 <= 涨幅 < 2%）
        val sidewaysDetail = createStockDetail(
            price = 50.5,
            previousClose = 50.0,
            changePercent = 1.0
        )
        val sidewaysAnalysis = createMockAiAnalysis(sidewaysDetail)
        assertEquals(AiTrendType.SIDEWAYS, sidewaysAnalysis.trendType, "0 <= 涨幅 < 2% 应该判断为震荡")
    }

    @Test
    fun `Mock AI 分析应该根据趋势判断风险等级`() {
        // 偏弱 + 跌幅 <= -2% = 高风险
        val highRiskDetail = createStockDetail(
            price = 47.0,
            previousClose = 50.0,
            changePercent = -6.0
        )
        val highRiskAnalysis = createMockAiAnalysis(highRiskDetail)
        assertEquals(AiRiskLevel.HIGH, highRiskAnalysis.riskLevel, "大跌应该判断为高风险")

        // 震荡 + 涨幅 < 1% = 低风险
        val lowRiskDetail = createStockDetail(
            price = 50.3,
            previousClose = 50.0,
            changePercent = 0.6
        )
        val lowRiskAnalysis = createMockAiAnalysis(lowRiskDetail)
        assertEquals(AiRiskLevel.LOW, lowRiskAnalysis.riskLevel, "窄幅震荡应该判断为低风险")

        // 其他情况 = 中风险
        val mediumRiskDetail = createStockDetail(
            price = 51.0,
            previousClose = 50.0,
            changePercent = 2.0
        )
        val mediumRiskAnalysis = createMockAiAnalysis(mediumRiskDetail)
        assertEquals(AiRiskLevel.MEDIUM, mediumRiskAnalysis.riskLevel, "其他情况应该判断为中风险")
    }

    @Test
    fun `Mock AI 分析应该包含完整的分析字段`() {
        val detail = createStockDetail(
            price = 51.0,
            previousClose = 50.0,
            changePercent = 2.0
        )
        val analysis = createMockAiAnalysis(detail)

        // 验证必填字段不为空
        assertTrue(analysis.trendJudgement.isNotEmpty(), "综合判断不应为空")
        assertTrue(analysis.focusPoint.isNotEmpty(), "关注要点不应为空")
        assertTrue(analysis.riskReminder.isNotEmpty(), "风险提示不应为空")
        assertTrue(analysis.signalInterpretation.isNotEmpty(), "信号解读不应为空")
        assertTrue(analysis.factSummary.isNotEmpty(), "事实摘要不应为空")
        assertTrue(analysis.applicablePeriod.isNotEmpty(), "适用周期不应为空")
        assertTrue(analysis.evidenceSummary.isNotEmpty(), "数据依据不应为空")
        assertTrue(analysis.invalidationCondition.isNotEmpty(), "失效条件不应为空")

        // 验证列表字段
        assertTrue(analysis.primaryRisks.isNotEmpty(), "主要风险列表不应为空")
        assertTrue(analysis.signals.isNotEmpty(), "信号列表不应为空")

        // 验证 Demo 标记
        assertTrue(analysis.isDemo, "Mock 数据应该标记为 Demo")
        assertEquals(AiAnalysisSource.MOCK, analysis.source, "来源应该是 MOCK")
    }

    @Test
    fun `Mock AI 分析的观察计划应该合理`() {
        val detail = createStockDetail(
            price = 51.0,
            previousClose = 50.0,
            changePercent = 2.0,
            high = 51.5,
            low = 49.8
        )
        val analysis = createMockAiAnalysis(detail)

        assertNotNull(analysis.observationPlan, "应该生成观察计划")
        val plan = analysis.observationPlan!!

        // 验证价格区间合理性
        assertTrue(plan.focusRangeLow > 0, "观察区间下限应该大于0")
        assertTrue(plan.focusRangeHigh > plan.focusRangeLow, "观察区间上限应该大于下限")
        assertTrue(plan.confirmationCondition.isNotEmpty(), "确认条件不应为空")
        assertTrue(plan.riskBoundary.isNotEmpty(), "风险边界不应为空")
    }

    @Test
    fun `Mock AI 分析的信号应该包含关键字段`() {
        val detail = createStockDetail(
            price = 51.0,
            previousClose = 50.0,
            changePercent = 2.0
        )
        val analysis = createMockAiAnalysis(detail)

        assertTrue(analysis.signals.isNotEmpty(), "应该生成信号列表")

        analysis.signals.forEach { signal ->
            assertTrue(signal.title.isNotEmpty(), "信号标题不应为空")
            assertTrue(signal.status.isNotEmpty(), "信号状态不应为空")
            assertTrue(signal.explanation.isNotEmpty(), "信号解读不应为空")
            assertTrue(signal.evidence.isNotEmpty(), "数据依据不应为空")
        }
    }

    // 辅助方法：创建测试用的 StockDetail
    private fun createStockDetail(
        price: Double,
        previousClose: Double,
        changePercent: Double,
        high: Double = price * 1.02,
        low: Double = price * 0.98
    ): StockDetail {
        val change = price - previousClose
        val quote = StockQuote(
            name = "测试股票",
            code = "sh600000",
            price = price,
            change = change,
            changePercent = changePercent,
            updatedAt = "2024-09-12 15:00:00"
        )

        return StockDetail(
            quote = quote,
            open = previousClose,
            previousClose = previousClose,
            high = high,
            low = low,
            volume = 10000000,
            turnover = 500000000.0,
            intradayTrend = emptyList()
        )
    }
}
