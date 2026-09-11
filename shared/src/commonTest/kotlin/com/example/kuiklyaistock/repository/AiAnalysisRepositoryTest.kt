package com.example.kuiklyaistock.repository

import com.example.kuiklyaistock.model.AiAnalysis
import com.example.kuiklyaistock.model.AiAnalysisSource
import com.tencent.kuikly.core.coroutines.CoroutineScope
import kotlin.coroutines.Continuation
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AiAnalysisRepositoryTest {
    private val detail by lazy {
        assertIs<StockLoadResult.Success<StockDetailData>>(
            runImmediate { MockStockRepository(delayMillis = 0).loadDetail(testScope, "600519") }
        ).data.detail
    }

    @BeforeTest
    fun clearCache() {
        AiAnalysisMemoryCache.clearForTest()
    }

    @Test
    fun parsesStructuredJsonAndDerivesLegacyFields() {
        val result = load(validJson())
        val analysis = assertIs<AiAnalysisLoadResult.Success>(result).analysis

        assertEquals(AiAnalysisSource.REMOTE, analysis.source)
        assertEquals("观察成交量与高点确认", analysis.focusPoint)
        assertEquals("波动放大", analysis.riskReminder)
        assertTrue(analysis.signalInterpretation.contains("价格维持强势"))
        assertEquals(detail.quote.updatedAt, analysis.updatedAt)
        assertEquals("req-1", analysis.requestId)
    }

    @Test
    fun keepsOldAiAnalysisConstructorCompatible() {
        val legacy = AiAnalysis("判断", "关注", "风险", "信号", "事实", "短线", "依据", "时间", true)

        assertEquals(AiAnalysisSource.MOCK, legacy.source)
        assertNull(legacy.modelName)
        assertEquals("", legacy.generatedAt)
        assertNull(legacy.requestId)
    }

    @Test
    fun rejectsMissingUnknownEmptyOversizedAndNonJsonContent() {
        val invalidContents = listOf(
            validJson().replace("\"trendType\":\"STRONG\",", ""),
            validJson().replace("\"STRONG\"", "\"UP\""),
            validJson().replace("短线偏强，关注高点确认", ""),
            validJson().replace("短线偏强，关注高点确认", "过".repeat(161)),
            "not-json",
            "",
        )

        invalidContents.forEach { content ->
            val result = load(content)
            assertEquals(AiAnalysisErrorType.FORMAT, assertIs<AiAnalysisLoadResult.Failure>(result).type)
            AiAnalysisMemoryCache.clearForTest()
        }
    }

    @Test
    fun rejectsInvalidPriceRangeAndTooManySignalsOrRisks() {
        val invalidRange = validJson().replace("\"focusRangeLow\":1680.0", "\"focusRangeLow\":1800.0")
        val fourSignals = validJson().replace(
            "{\"title\":\"价格位置\",\"status\":\"积极\",\"explanation\":\"价格维持强势\",\"evidence\":\"当前价高于昨收\"}",
            List(4) { "{\"title\":\"信号$it\",\"status\":\"中性\",\"explanation\":\"说明$it\",\"evidence\":\"依据$it\"}" }.joinToString(",")
        )
        val threeRisks = validJson().replace("[\"波动放大\",\"演示行情有限\"]", "[\"风险1\",\"风险2\",\"风险3\"]")

        listOf(invalidRange, fourSignals, threeRisks).forEach {
            assertEquals(AiAnalysisErrorType.FORMAT, assertIs<AiAnalysisLoadResult.Failure>(load(it)).type)
            AiAnalysisMemoryCache.clearForTest()
        }
    }

    @Test
    fun acceptsNoObservationPlanTargetOrSignals() {
        val content = validJson()
            .replace(observationPlanJson(), "null")
            .replace("[{\"title\":\"价格位置\",\"status\":\"积极\",\"explanation\":\"价格维持强势\",\"evidence\":\"当前价高于昨收\"}]", "[]")
        val analysis = assertIs<AiAnalysisLoadResult.Success>(load(content)).analysis

        assertNull(analysis.observationPlan)
        assertTrue(analysis.signals.isEmpty())
        assertEquals("暂无明确观察计划", analysis.focusPoint)
    }

    @Test
    fun mapsHttpAndNetworkFailures() {
        val cases = listOf(
            Triple(0, "CONFIG", AiAnalysisErrorType.CONFIG),
            Triple(400, "", AiAnalysisErrorType.INVALID_REQUEST),
            Triple(401, "", AiAnalysisErrorType.AUTH),
            Triple(402, "", AiAnalysisErrorType.BALANCE),
            Triple(422, "", AiAnalysisErrorType.INVALID_REQUEST),
            Triple(429, "", AiAnalysisErrorType.RATE_LIMIT),
            Triple(500, "", AiAnalysisErrorType.SERVER),
            Triple(503, "", AiAnalysisErrorType.SERVER),
            Triple(0, "NETWORK", AiAnalysisErrorType.NETWORK),
            Triple(200, "FORMAT", AiAnalysisErrorType.FORMAT),
        )

        cases.forEach { (status, errorType, expected) ->
            val transport = FakeTransport(AiTransportResult.Failure(status, errorType, ""))
            val result = runImmediate { RemoteAiAnalysisRepository(transport, FakeStockDatabaseRepository()).loadAnalysis(detail) }
            assertEquals(expected, assertIs<AiAnalysisLoadResult.Failure>(result).type)
            AiAnalysisMemoryCache.clearForTest()
        }
    }


    @Test
    fun omitsEmptyTrendSamplesFromRealSnapshotPrompt() {
        val transport = FakeTransport(success(validJson()))
        val realSnapshot = detail.copy(intradayTrend = emptyList(), dailyTrend = emptyList())

        runImmediate { RemoteAiAnalysisRepository(transport, FakeStockDatabaseRepository()).loadAnalysis(realSnapshot) }

        val prompt = transport.lastRequest?.userPrompt.orEmpty()
        assertTrue(prompt.contains("行情快照"))
        assertTrue(prompt.contains("未提供真实分时或日 K 数据"))
        assertTrue(!prompt.contains("分时样本="))
        assertTrue(!prompt.contains("日K样本="))
    }

    @Test
    fun returnsUnsupportedWithoutStartingRequest() {
        val transport = FakeTransport(success(validJson()), supported = false)

        assertIs<AiAnalysisLoadResult.Unsupported>(runImmediate { RemoteAiAnalysisRepository(transport, FakeStockDatabaseRepository()).loadAnalysis(detail) })
        assertEquals(0, transport.callCount)
    }

    @Test
    fun usesMemoryCacheAndMarksCacheSource() {
        val transport = FakeTransport(success(validJson()))
        val repository = RemoteAiAnalysisRepository(transport, FakeStockDatabaseRepository())

        val first = assertIs<AiAnalysisLoadResult.Success>(runImmediate { repository.loadAnalysis(detail) }).analysis
        val second = assertIs<AiAnalysisLoadResult.Success>(runImmediate { repository.loadAnalysis(detail) }).analysis

        assertEquals(AiAnalysisSource.REMOTE, first.source)
        assertEquals(AiAnalysisSource.CACHE, second.source)
        assertEquals(1, transport.callCount)
    }

    @Test
    fun isolatesCacheByQuoteTimeModelAndSchemaVersion() {
        val transport = FakeTransport(success(validJson()))
        runImmediate { RemoteAiAnalysisRepository(transport, FakeStockDatabaseRepository()).loadAnalysis(detail) }
        runImmediate { RemoteAiAnalysisRepository(transport, FakeStockDatabaseRepository()).loadAnalysis(detail.copy(quote = detail.quote.copy(updatedAt = "另一时间"))) }
        runImmediate { RemoteAiAnalysisRepository(transport, FakeStockDatabaseRepository(), modelName = "other-model").loadAnalysis(detail) }
        runImmediate { RemoteAiAnalysisRepository(transport, FakeStockDatabaseRepository(), schemaVersion = "v2").loadAnalysis(detail) }

        assertEquals(4, transport.callCount)
    }

    private fun load(content: String): AiAnalysisLoadResult {
        val transport = FakeTransport(success(content))
        return runImmediate { RemoteAiAnalysisRepository(transport, FakeStockDatabaseRepository()).loadAnalysis(detail) }
    }

    private fun success(content: String) = AiTransportResult.Success(content, "deepseek-flash", "req-1", "2026-09-11 10:00:00")

    private fun validJson(): String = """
        {
          "trendType":"STRONG",
          "trendJudgement":"短线偏强，关注高点确认",
          "factSummary":"当前价高于昨收，日内运行在高位区间。",
          "applicablePeriod":"短线观察",
          "observationPlan":${observationPlanJson()},
          "signals":[{"title":"价格位置","status":"积极","explanation":"价格维持强势","evidence":"当前价高于昨收"}],
          "riskLevel":"MEDIUM",
          "primaryRisks":["波动放大","演示行情有限"],
          "invalidationCondition":"跌破当日低点后判断失效"
        }
    """.trimIndent()

    private fun observationPlanJson(): String = """{"focusRangeLow":1680.0,"focusRangeHigh":1780.0,"confirmationCondition":"观察成交量与高点确认","confirmationPrice":1780.0,"referenceTarget":null,"riskBoundary":"跌破1680停止沿用"}"""

    private fun <T> runImmediate(block: suspend () -> T): T {
        var completed: Result<T>? = null
        block.startCoroutine(object : Continuation<T> {
            override val context: CoroutineContext = EmptyCoroutineContext
            override fun resumeWith(result: Result<T>) {
                completed = result
            }
        })
        return checkNotNull(completed) { "Fake 请求不应挂起" }.getOrThrow()
    }

    private class FakeTransport(
        private val result: AiTransportResult,
        private val supported: Boolean = true,
    ) : AiAnalysisTransport {
        var callCount = 0
        var lastRequest: AiAnalysisRequest? = null

        override fun isSupported(): Boolean = supported

        override suspend fun request(request: AiAnalysisRequest): AiTransportResult {
            callCount++
            lastRequest = request
            return result
        }
    }

    private companion object {
        val testScope = object : CoroutineScope {
            override val coroutineContext: CoroutineContext = EmptyCoroutineContext
        }
    }
}
