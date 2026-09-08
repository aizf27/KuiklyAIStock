package com.example.kuiklyaistock.repository

import com.example.kuiklyaistock.model.StockQuote
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WatchlistStoreTest {
    @BeforeTest
    fun setUp() {
        WatchlistStore.resetForTest()
    }

    @AfterTest
    fun tearDown() {
        WatchlistStore.resetForTest()
    }

    @Test
    fun notifiesSnapshotsAndStopsAfterUnsubscribe() {
        val snapshots = mutableListOf<Set<String>>()
        val unsubscribe = WatchlistStore.subscribe { snapshots.add(it) }

        assertTrue(WatchlistStore.toggle("00700"))
        assertFalse(WatchlistStore.toggle("00700"))
        unsubscribe()
        WatchlistStore.toggle("300750")

        assertEquals(listOf(emptySet(), setOf("00700"), emptySet()), snapshots)
    }

    @Test
    fun filtersQuotesByFavoriteCodesWithoutChangingOrder() {
        val quotes = listOf(quote("00700"), quote("300750"), quote("600519"))

        val result = WatchlistStore.filterFavorite(quotes, setOf("600519", "00700"))

        assertEquals(listOf("00700", "600519"), result.map { it.code })
    }

    private fun quote(code: String) = StockQuote(
        name = code,
        code = code,
        price = 1.0,
        change = 0.0,
        changePercent = 0.0,
        updatedAt = "2026-09-08 00:00",
    )
}
