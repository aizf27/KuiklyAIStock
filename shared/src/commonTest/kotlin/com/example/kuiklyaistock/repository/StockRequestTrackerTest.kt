package com.example.kuiklyaistock.repository

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StockRequestTrackerTest {
    @Test
    fun acceptsOnlyLatestRequest() {
        val tracker = StockRequestTracker()
        val first = tracker.next()
        val second = tracker.next()

        assertFalse(tracker.isLatest(first))
        assertTrue(tracker.isLatest(second))
    }

    @Test
    fun invalidationRejectsPendingResult() {
        val tracker = StockRequestTracker()
        val requestId = tracker.next()

        tracker.invalidate()

        assertFalse(tracker.isLatest(requestId))
    }
}
