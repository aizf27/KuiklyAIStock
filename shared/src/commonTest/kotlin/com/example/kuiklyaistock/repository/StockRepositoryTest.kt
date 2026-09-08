package com.example.kuiklyaistock.repository

import com.tencent.kuikly.core.coroutines.CoroutineScope
import kotlin.coroutines.Continuation
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class StockRepositoryTest {
    private val repository = MockStockRepository(delayMillis = 0)

    @Test
    fun providesFiveStableQuotes() {
        val result = runImmediate { repository.loadHome(testScope) }
        val data = assertIs<StockLoadResult.Success<StockHomeData>>(result).data

        assertEquals(5, data.quotes.size)
        assertTrue(data.quotes.all { it.code.isNotEmpty() && it.name.isNotEmpty() })
        assertEquals("00700", data.quotes.first().code)
    }

    @Test
    fun resolvesDetailAndAnalysisByCode() {
        val result = runImmediate { repository.loadDetail(testScope, "300750") }
        val data = assertIs<StockLoadResult.Success<StockDetailData>>(result).data

        assertEquals("宁德时代", data.detail.quote.name)
        assertTrue(data.detail.intradayTrend.isNotEmpty())
        assertTrue(data.detail.dailyTrend.isNotEmpty())
        assertTrue(data.detail.intradayTrend.all { it.label.isNotEmpty() })
        assertTrue(data.detail.dailyTrend.all { it.label.isNotEmpty() })
        assertTrue(data.detail.previousClose > 0)
        assertNotNull(data.analysis)
        assertTrue(data.analysis.factSummary.isNotEmpty())
        assertTrue(data.analysis.evidenceSummary.isNotEmpty())
        assertTrue(data.analysis.isDemo)
    }

    @Test
    fun calculatesMarketSummary() {
        val result = runImmediate { repository.loadHome(testScope) }
        val summary = assertIs<StockLoadResult.Success<StockHomeData>>(result).data.marketSummary

        assertEquals(5, summary.totalCount)
        assertEquals(3, summary.risingCount)
        assertEquals(2, summary.fallingCount)
        assertEquals(0, summary.flatCount)
        assertEquals("已收盘", summary.sessionStatus)
        assertEquals(2, summary.indices.size)
        assertTrue(summary.indices.all { it.price > 0 && it.updatedAt.isNotEmpty() })
        assertTrue(summary.sampleTurnoverAmount > 0)
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
