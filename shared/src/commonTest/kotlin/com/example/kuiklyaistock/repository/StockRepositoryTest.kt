package com.example.kuiklyaistock.repository

import com.example.kuiklyaistock.model.AiAnalysis
import com.example.kuiklyaistock.model.AiRiskLevel
import com.example.kuiklyaistock.model.AiTrendType
import com.tencent.kuikly.core.coroutines.CoroutineScope
import kotlin.coroutines.Continuation
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StockRepositoryTest {
    private val repository = MockStockRepository(delayMillis = 0)

    @Test
    fun providesAtLeastThirtyUniqueAShareQuotes() {
        val data = loadHome()

        assertTrue(data.quotes.size >= 30)
        assertEquals(data.quotes.size, data.quotes.map { it.code }.distinct().size)
        assertTrue(data.quotes.all { it.name.isNotBlank() })
        assertTrue(data.quotes.all { it.code.length == 6 && it.code.all(Char::isDigit) })
    }

    @Test
    fun everyQuoteResolvesCompleteDetailAndAnalysis() {
        loadHome().quotes.forEach { quote ->
            val data = assertIs<StockLoadResult.Success<StockDetailData>>(
                runImmediate { repository.loadDetail(testScope, quote.code) }
            ).data

            assertEquals(quote, data.detail.quote)
            assertTrue(data.detail.previousClose > 0.0)
            assertTrue(data.detail.open > 0.0)
            assertTrue(data.detail.low <= minOf(data.detail.open, data.detail.quote.price))
            assertTrue(data.detail.high >= maxOf(data.detail.open, data.detail.quote.price))
            assertTrue(data.detail.volume > 0L)
            assertTrue(data.detail.turnover > 0.0)
            assertTrue(data.detail.intradayTrend.size >= 10)
            assertTrue(data.detail.dailyTrend.size >= 10)
            assertTrue(data.detail.intradayTrend.all { it.label.isNotEmpty() && it.price > 0.0 })
            assertTrue(data.detail.dailyTrend.all { it.label.isNotEmpty() && it.price > 0.0 })
            assertNotNull(data.analysis)
            assertTrue(data.analysis.factSummary.isNotEmpty())
            assertTrue(data.analysis.evidenceSummary.isNotEmpty())
            assertTrue(data.analysis.isDemo)
        }
    }

    @Test
    fun keepsQuoteChangeAndPercentConsistent() {
        loadHome().quotes.forEach { quote ->
            val detail = assertIs<StockLoadResult.Success<StockDetailData>>(
                runImmediate { repository.loadDetail(testScope, quote.code) }
            ).data.detail
            val expectedChange = detail.quote.price - detail.previousClose
            val expectedPercent = expectedChange / detail.previousClose * 100

            assertTrue(abs(detail.quote.change - expectedChange) < 0.011)
            assertTrue(abs(detail.quote.changePercent - expectedPercent) < 0.011)
        }
    }

    @Test
    fun calculatesSummaryFromMockUniverse() {
        val data = loadHome()
        val summary = data.marketSummary

        assertEquals(data.quotes.size, summary.totalCount)
        assertEquals(summary.totalCount, summary.risingCount + summary.fallingCount + summary.flatCount)
        assertEquals("已收盘", summary.sessionStatus)
        assertEquals(3, summary.indices.size)
        assertTrue(summary.indices.all { it.price > 0 && it.updatedAt.isNotEmpty() })
        assertTrue(summary.sampleTurnoverAmount > 0)
    }

    @Test
    fun searchesByTrimmedNameAndCodeSubstring() {
        val quotes = loadHome().quotes

        assertEquals(listOf("600519"), searchStockQuotes(quotes, "  茅台 ").map { it.code })
        assertTrue(searchStockQuotes(quotes, "0750").any { it.code == "300750" })
        assertEquals(quotes, searchStockQuotes(quotes, "  "))
        assertTrue(searchStockQuotes(quotes, "不存在的股票").isEmpty())
    }

    @Test
    fun providesStrongSidewaysAndWeakStructuredAnalyses() {
        val aiData = assertIs<StockLoadResult.Success<StockAiData>>(
            runImmediate { repository.loadAi(testScope) }
        ).data
        val analyses = aiData.insights.map { it.analysis }

        assertEquals(
            setOf(AiTrendType.STRONG, AiTrendType.SIDEWAYS, AiTrendType.WEAK),
            analyses.map { it.trendType }.toSet(),
        )
        assertTrue(analyses.map { it.trendJudgement }.distinct().size >= 3)
        assertTrue(analyses.all { it.signals.size == 3 })
        assertTrue(analyses.all { it.primaryRisks.isNotEmpty() && it.invalidationCondition.isNotEmpty() })
        assertTrue(analyses.any { it.riskLevel == AiRiskLevel.HIGH })
    }

    @Test
    fun keepsObservationRangesValidAndWeakTargetsEmpty() {
        val analyses = loadHome().quotes.map { quote ->
            assertIs<StockLoadResult.Success<StockDetailData>>(
                runImmediate { repository.loadDetail(testScope, quote.code) }
            ).data.analysis
        }.filterNotNull()

        analyses.mapNotNull { it.observationPlan }.forEach { plan ->
            assertTrue(plan.focusRangeLow > 0.0)
            assertTrue(plan.focusRangeHigh >= plan.focusRangeLow)
            assertTrue(plan.confirmationCondition.startsWith("若"))
        }
        assertTrue(analyses.filter { it.trendType == AiTrendType.WEAK }.all {
            it.observationPlan?.referenceTarget == null
        })
        assertTrue(analyses.any { it.observationPlan == null })
    }

    @Test
    fun keepsAnalysisTimeAndHomepageInsightConsistent() {
        val aiData = assertIs<StockLoadResult.Success<StockAiData>>(
            runImmediate { repository.loadAi(testScope) }
        ).data

        aiData.insights.forEach { insight ->
            val detailData = assertIs<StockLoadResult.Success<StockDetailData>>(
                runImmediate { repository.loadDetail(testScope, insight.quote.code) }
            ).data
            assertEquals(detailData.detail.quote.updatedAt, detailData.analysis?.updatedAt)
            assertEquals(detailData.analysis, insight.analysis)
        }
    }

    @Test
    fun keepsLegacyAnalysisConstructorCompatibleWithEmptyPlanAndSignals() {
        val analysis = AiAnalysis(
            trendJudgement = "旧判断",
            focusPoint = "旧关注点",
            riskReminder = "旧风险",
            signalInterpretation = "旧信号",
            factSummary = "旧事实",
            applicablePeriod = "旧周期",
            evidenceSummary = "旧依据",
            updatedAt = "2026-09-10 15:00",
            isDemo = true,
        )

        assertEquals(AiTrendType.SIDEWAYS, analysis.trendType)
        assertEquals(AiRiskLevel.MEDIUM, analysis.riskLevel)
        assertNull(analysis.observationPlan)
        assertTrue(analysis.signals.isEmpty())
        assertTrue(analysis.primaryRisks.isEmpty())
    }

    @Test
    fun canReturnDetailWhenAiAnalysisIsUnavailable() {
        val noAnalysisRepository = MockStockRepository(
            delayMillis = 0,
            analysisUnavailableCodes = setOf("600519"),
        )
        val data = assertIs<StockLoadResult.Success<StockDetailData>>(
            runImmediate { noAnalysisRepository.loadDetail(testScope, "600519") }
        ).data

        assertEquals("600519", data.detail.quote.code)
        assertNull(data.analysis)
    }

    @Test
    fun returnsEmptyForUnknownCode() {
        val result = runImmediate { repository.loadDetail(testScope, "UNKNOWN") }

        assertEquals(StockLoadResult.Empty, result)
    }

    @Test
    fun supportsForcedEmptyAndFailureResults() {
        val emptyRepository = MockStockRepository(
            delayMillis = 0,
            forcedEmptyResults = setOf(StockRequestType.AI),
        )
        val failingRepository = MockStockRepository(
            delayMillis = 0,
            forcedFailures = setOf(StockRequestType.HOME),
        )

        assertEquals(StockLoadResult.Empty, runImmediate { emptyRepository.loadAi(testScope) })
        assertIs<StockLoadResult.Failure>(runImmediate { failingRepository.loadHome(testScope) })
    }

    private fun loadHome(): StockHomeData = assertIs<StockLoadResult.Success<StockHomeData>>(
        runImmediate { repository.loadHome(testScope) }
    ).data

    private fun <T> runImmediate(block: suspend () -> T): T {
        var completed: Result<T>? = null
        block.startCoroutine(object : Continuation<T> {
            override val context: CoroutineContext = EmptyCoroutineContext
            override fun resumeWith(result: Result<T>) {
                completed = result
            }
        })
        return checkNotNull(completed) { "零延迟 Mock 请求不应挂起" }.getOrThrow()
    }

    private companion object {
        val testScope = object : CoroutineScope {
            override val coroutineContext: CoroutineContext = EmptyCoroutineContext
        }
    }
}
