package com.example.kuiklyaistock.repository

import com.example.kuiklyaistock.model.MarketIndexQuote
import com.example.kuiklyaistock.model.StockDataSource
import com.example.kuiklyaistock.model.StockQuote
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
    fun loadsHomeCacheWithoutNetworkRequest() {
        val database = FakeStockDatabaseRepository()
        runImmediate {
            database.insertQuotes(listOf(stockQuote()))
            database.insertMarketIndices(marketIndices())
        }
        val transport = FakeQuoteTransport(mutableListOf(failure()))
        val repository = repository(transport, database)

        val data = assertSuccess(runImmediate { repository.loadHome(testScope) })

        assertEquals(0, transport.callCount)
        assertEquals(StockDataSource.CACHE, data.dataSource)
        assertEquals(listOf("600519"), data.quotes.map { it.code })
        assertEquals(listOf("000001", "399001", "399006"), data.marketSummary.indices.map { it.code })
    }

    @Test
    fun refreshPersistsStocksAndIndices() {
        val database = FakeStockDatabaseRepository()
        val repository = repository(success(homeResponse()), database)

        val data = assertSuccess(runImmediate { repository.refreshHome(testScope) })

        assertEquals(StockDataSource.REMOTE, data.dataSource)
        assertEquals(listOf("600519"), runImmediate { database.getAllQuotes() }.map { it.code })
        assertEquals(listOf("000001", "399001", "399006"), runImmediate { database.getMarketIndices() }.map { it.code })
        assertTrue(runImmediate { database.getAllQuotes() }.none { it.code.startsWith("IDX:") })
    }

    @Test
    fun restoresIndicesFromDatabaseAfterMemoryCacheIsCleared() {
        val database = FakeStockDatabaseRepository()
        assertSuccess(runImmediate { repository(success(homeResponse()), database).refreshHome(testScope) })
        TencentQuoteCache.clear()

        val restored = assertSuccess(runImmediate { repository(failure(), database).loadHome(testScope) })

        assertEquals(StockDataSource.CACHE, restored.dataSource)
        assertEquals(3, restored.marketSummary.indices.size)
        assertEquals("000001", restored.marketSummary.indices.first().code)
    }

    @Test
    fun keepsDatabaseCacheWhenRefreshFails() {
        val database = FakeStockDatabaseRepository()
        runImmediate {
            database.insertQuotes(listOf(stockQuote()))
            database.insertMarketIndices(marketIndices())
        }
        val repository = repository(failure(QuoteTransportErrorCategory.TIMEOUT), database)

        val refresh = runImmediate { repository.refreshHome(testScope) }
        val cached = assertSuccess(runImmediate { repository.loadHome(testScope) })

        assertEquals("行情请求超时", assertIs<StockLoadResult.Failure>(refresh).message)
        assertEquals(1_568.20, cached.quotes.single().price)
        assertEquals(3, cached.marketSummary.indices.size)
    }

    @Test
    fun loadsRemoteDetailAndPersistsGeneratedTrends() {
        val repository = repository(success(record("sh600519")))

        val data = assertSuccess(runImmediate { repository.loadDetail(testScope, "600519") })

        assertEquals(StockDataSource.REMOTE, data.dataSource)
        assertEquals("2026-09-11 10:30:45", data.quoteTime)
        assertEquals(1_234_500L, data.detail.volume)
        assertEquals(193_525_000.0, data.detail.turnover)
        assertTrue(data.detail.intradayTrend.isNotEmpty())
        assertTrue(data.detail.dailyKLine.isNotEmpty())
    }

    @Test
    fun sharesMemoryCacheBetweenHomeAndDetailRepositories() {
        val home = assertSuccess(runImmediate { repository(success(homeResponse())).refreshHome(testScope) })

        val detail = assertSuccess(runImmediate { repository(failure()).loadDetail(testScope, "600519") })

        assertEquals(StockDataSource.CACHE, detail.dataSource)
        assertEquals(home.quotes.single().price, detail.detail.quote.price)
    }

    @Test
    fun keepsSuccessfulRowsWhenBatchResponseIsPartial() {
        val response = listOf(
            record("sh600519"),
            record("sz300750", price = "220.50", high = "225.00", low = "210.00"),
        ).joinToString("\n")
        val home = assertSuccess(runImmediate { repository(success(response)).refreshHome(testScope) })

        assertEquals(StockDataSource.REMOTE, home.dataSource)
        assertEquals(setOf("600519", "300750"), home.quotes.map { it.code }.toSet())
        assertTrue(home.missingCodes.contains("603019"))
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

    private fun repository(
        result: QuoteTransportResult,
        database: StockDatabaseRepository = FakeStockDatabaseRepository(),
    ) = repository(FakeQuoteTransport(mutableListOf(result)), database)

    private fun repository(
        transport: QuoteTransport,
        database: StockDatabaseRepository = FakeStockDatabaseRepository(),
    ) = TencentStockRepository(
        transport = transport,
        nowMillis = { now },
        logger = {},
        databaseRepo = database,
        cacheTtlMillis = 120_000L,
    )

    private fun homeResponse(): String = listOf(
        record("sh600519"),
        record("sh000001", name = "上证指数", price = "3951.51", previousClose = "3940.55", open = "3945.00", change = "10.96", percent = "0.28", high = "3960.00", low = "3930.00"),
        record("sz399001", name = "深证成指", price = "13723.32", previousClose = "13703.21", open = "13710.00", change = "20.11", percent = "0.15", high = "13750.00", low = "13680.00"),
        record("sz399006", name = "创业板指", price = "3354.97", previousClose = "3359.72", open = "3360.00", change = "-4.75", percent = "-0.14", high = "3370.00", low = "3340.00"),
    ).joinToString("\n")

    private fun stockQuote() = StockQuote("贵州茅台", "600519", 1_568.20, -13.60, -0.86, "2026-09-11 10:30:45")

    private fun marketIndices() = listOf(
        MarketIndexQuote("上证指数", "000001", 3_951.51, 10.96, 0.28, "2026-09-11 10:30:45"),
        MarketIndexQuote("深证成指", "399001", 13_723.32, 20.11, 0.15, "2026-09-11 10:30:45"),
        MarketIndexQuote("创业板指", "399006", 3_354.97, -4.75, -0.14, "2026-09-11 10:30:45"),
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
        var callCount = 0

        override suspend fun fetch(symbols: List<String>): QuoteTransportResult {
            callCount++
            return results.removeAt(0)
        }
    }

    private companion object {
        val testScope = object : CoroutineScope {
            override val coroutineContext: CoroutineContext = EmptyCoroutineContext
        }
    }
}
