package com.example.kuiklyaistock.repository

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StockRepositoryTest {
    private val repository = MockStockRepository()

    @Test
    fun providesFiveStableQuotes() {
        val quotes = repository.getQuotes()

        assertEquals(5, quotes.size)
        assertTrue(quotes.all { it.code.isNotEmpty() && it.name.isNotEmpty() })
        assertEquals("00700", quotes.first().code)
    }

    @Test
    fun resolvesDetailAndAnalysisByCode() {
        val detail = repository.getDetail("300750")
        val analysis = repository.getAiAnalysis("300750")

        assertNotNull(detail)
        assertEquals("宁德时代", detail.quote.name)
        assertTrue(detail.trend.isNotEmpty())
        assertNotNull(analysis)
        assertTrue(analysis.marketSummary.isNotEmpty())
    }

    @Test
    fun calculatesMarketSummary() {
        val summary = repository.getMarketSummary()

        assertEquals(5, summary.totalCount)
        assertEquals(3, summary.risingCount)
        assertEquals(2, summary.fallingCount)
        assertEquals(0, summary.flatCount)
    }

    @Test
    fun returnsNullForUnknownCode() {
        assertNull(repository.getDetail("UNKNOWN"))
        assertNull(repository.getAiAnalysis("UNKNOWN"))
    }
}
