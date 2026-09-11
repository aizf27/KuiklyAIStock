package com.example.kuiklyaistock.repository

import com.example.kuiklyaistock.model.StockDataSource
import com.tencent.kuikly.core.coroutines.CoroutineScope
import kotlin.coroutines.Continuation
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class TencentStockRepositoryTest {
    private var now = 1_000L

    @BeforeTest
    fun clearCache() {
        TencentQuoteCache.clear()
    }

    @AfterTest
    fun clearCacheAfterTest() {
        TencentQuoteCache.clear()
    }

    @Test
    fun loadsRemoteDetailWithoutFakeTrends() {
        val repository = repository(success(record("sh600519")))

        val data = assertSuccess(runImmediate { repository.loadDetail(testScope, "600519") })

        assertEquals(StockDataSource.REMOTE, data.dataSource)
        assertEquals("2026-09-11 10:30:45", data.quoteTime)
        assertEquals(1_234_500L, data.detail.volume)
        assertEquals(193_525_000.0, data.detail.turnover)
        assertTrue(data.detail.intradayTrend.isEmpty())
        assertTrue(data.detail.dailyTrend.isEmpty())
    }

    @Test
    fun fallsBackToFreshAndExpiredCache() {
        val transport = FakeQuoteTransport(mutableListOf(success(record("sh600519"))))
        val repository = repository(transport)
        assertSuccess(runImmediate { repository.loadDetail(testScope, "600519") })

        now = 2_000L
        transport.results += failure()
        val fresh = assertSuccess(runImmediate { repository.loadDetail(testScope, "600519") })
        assertEquals(StockDataSource.CACHE, fresh.dataSource)
        assertEquals(false, fresh.isExpired)

        now = 130_002L
        transport.results += failure()
        val expired = assertSuccess(runImmediate { repository.loadDetail(testScope, "600519") })
        assertEquals(StockDataSource.CACHE, expired.dataSource)
        assertEquals(true, expired.isExpired)
    }

    @Test
    fun sharesCacheBetweenHomeAndDetailRepositories() {
        val homeRepository = repository(success(record("sh600519")))
        val home = assertSuccess(runImmediate { homeRepository.loadHome(testScope) })
        assertEquals(StockDataSource.REMOTE, home.dataSource)
        assertTrue(home.missingCodes.isNotEmpty())

        val detailRepository = repository(failure())
        val detail = assertSuccess(runImmediate { detailRepository.loadDetail(testScope, "600519") })
        assertEquals(StockDataSource.CACHE, detail.dataSource)
        assertEquals(home.quotes.single().price, detail.detail.quote.price)
    }

    @Test
    fun keepsSuccessfulRowsWhenBatchResponseIsPartial() {
        val response = listOf(
            record("sh600519"),
            record("sz300750", price = "220.50", high = "225.00", low = "210.00"),
        ).joinToString("\n")
        val repository = repository(success(response))

        val home = assertSuccess(runImmediate { repository.loadHome(testScope) })

        assertEquals(StockDataSource.REMOTE, home.dataSource)
        assertEquals(setOf("600519", "300750"), home.quotes.map { it.code }.toSet())
        assertTrue(home.missingCodes.contains("603019"))
    }

    @Test
    fun marksPartialRemoteHomeExpiredWhenMergedCacheIsExpired() {
        val transport = FakeQuoteTransport(
            mutableListOf(
                success(record("sh600519")),
                success(record("sz300750", price = "220.50", high = "225.00", low = "210.00")),
            )
        )
        val repository = repository(transport)
        assertSuccess(runImmediate { repository.loadHome(testScope) })

        now = 130_002L
        val home = assertSuccess(runImmediate { repository.loadHome(testScope) })

        assertEquals(StockDataSource.REMOTE, home.dataSource)
        assertEquals(setOf("600519", "300750"), home.quotes.map { it.code }.toSet())
        assertTrue(home.isExpired)
    }

    @Test
    fun rejectsOlderRemoteQuoteAndKeepsNewerCache() {
        val transport = FakeQuoteTransport(
            mutableListOf(
                success(record("sh600519", time = "20260911103045", price = "1568.20")),
                success(record("sh600519", time = "20260911100000", price = "1500.00", high = "1580.00")),
            )
        )
        val repository = repository(transport)
        val first = assertSuccess(runImmediate { repository.loadDetail(testScope, "600519") })
        now = 2_000L
        val second = assertSuccess(runImmediate { repository.loadDetail(testScope, "600519") })

        assertEquals(1568.20, first.detail.quote.price)
        assertEquals(1568.20, second.detail.quote.price)
        assertEquals(StockDataSource.CACHE, second.dataSource)
        assertEquals("2026-09-11 10:30:45", second.quoteTime)
    }

    @Test
    fun returnsFailureWhenRemoteAndCacheAreUnavailable() {
        val repository = repository(failure(QuoteTransportErrorCategory.TIMEOUT))

        val result = runImmediate { repository.loadDetail(testScope, "600519") }

        assertEquals("行情请求超时", assertIs<StockLoadResult.Failure>(result).message)
    }

    private fun repository(result: QuoteTransportResult) = repository(FakeQuoteTransport(mutableListOf(result)))

    private fun repository(transport: QuoteTransport) = TencentStockRepository(
        transport = transport,
        nowMillis = { now },
        logger = {},
        cacheTtlMillis = 120_000L,
    )

    private fun success(body: String) = QuoteTransportResult.Success(body.encodeToByteArray(), 200)

    private fun failure(category: QuoteTransportErrorCategory = QuoteTransportErrorCategory.NETWORK) =
        QuoteTransportResult.Failure(category)

    private fun <T> assertSuccess(result: StockLoadResult<T>): T = assertIs<StockLoadResult.Success<T>>(result).data

    private fun record(
        symbol: String,
        name: String = "Moutai",
        code: String = symbol.drop(2),
        price: String = "1568.20",
        previousClose: String = "1581.80",
        open: String = "1575.00",
        volumeLots: String = "12345",
        time: String = "20260911103045",
        change: String = "-13.60",
        percent: String = "-0.86",
        high: String = "1580.00",
        low: String = "1560.00",
        turnoverWan: String = "19352.50",
    ): String {
        val fields = MutableList(38) { "" }
        fields[1] = name
        fields[2] = code
        fields[3] = price
        fields[4] = previousClose
        fields[5] = open
        fields[6] = volumeLots
        fields[30] = time
        fields[31] = change
        fields[32] = percent
        fields[33] = high
        fields[34] = low
        fields[37] = turnoverWan
        return """v_$symbol="${fields.joinToString("~")}";"""
    }

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

    private class FakeQuoteTransport(
        val results: MutableList<QuoteTransportResult>,
    ) : QuoteTransport {
        override suspend fun fetch(symbols: List<String>): QuoteTransportResult = results.removeAt(0)
    }

    private companion object {
        val testScope = object : CoroutineScope {
            override val coroutineContext: CoroutineContext = EmptyCoroutineContext
        }
    }
}
