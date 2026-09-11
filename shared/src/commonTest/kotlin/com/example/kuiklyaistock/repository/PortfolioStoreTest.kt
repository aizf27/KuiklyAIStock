package com.example.kuiklyaistock.repository

import com.example.kuiklyaistock.model.PortfolioLoadResult
import com.example.kuiklyaistock.model.TradeResult
import kotlin.math.abs
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PortfolioStoreTest {
    @BeforeTest
    fun setUp() {
        PortfolioStore.resetForTest()
    }

    @AfterTest
    fun tearDown() {
        PortfolioStore.resetForTest()
    }

    @Test
    fun favoritesKeepOrderAndAppendAfterReAdd() {
        assertTrue(PortfolioStore.toggleFavorite("600519"))
        assertTrue(PortfolioStore.toggleFavorite("300750"))
        assertFalse(PortfolioStore.toggleFavorite("600519"))
        assertTrue(PortfolioStore.toggleFavorite("600519"))

        assertEquals(listOf("300750", "600519"), PortfolioStore.snapshot().favoriteCodes)
    }

    @Test
    fun favoriteMoveSupportsEdgesAndRejectsInvalidIndices() {
        listOf("600519", "300750", "002594").forEach(PortfolioStore::toggleFavorite)

        assertTrue(PortfolioStore.moveFavorite(2, 0))
        assertEquals(listOf("002594", "600519", "300750"), PortfolioStore.snapshot().favoriteCodes)
        assertTrue(PortfolioStore.moveFavorite(0, 2))
        assertEquals(listOf("600519", "300750", "002594"), PortfolioStore.snapshot().favoriteCodes)
        assertFalse(PortfolioStore.moveFavorite(-1, 0))
        assertFalse(PortfolioStore.moveFavorite(0, 3))
        assertFalse(PortfolioStore.moveFavorite(1, 1))
    }

    @Test
    fun removeFavoriteKeepsRemainingOrder() {
        listOf("600519", "300750", "002594").forEach(PortfolioStore::toggleFavorite)

        assertTrue(PortfolioStore.removeFavorite(" 300750 "))
        assertEquals(listOf("600519", "002594"), PortfolioStore.snapshot().favoriteCodes)
        assertFalse(PortfolioStore.removeFavorite("300750"))
    }

    @Test
    fun jsonRoundTripKeepsVersionFavoritesAndPositions() {
        PortfolioStore.toggleFavorite("600519")
        PortfolioStore.toggleFavorite("300750")
        PortfolioStore.buy("300750", 200, 100.0)
        val serialized = PortfolioStore.serialize()

        PortfolioStore.resetForTest()
        val result = PortfolioStore.load(serialized)

        assertIs<PortfolioLoadResult.Success>(result)
        assertEquals(PortfolioStore.STATE_VERSION, PortfolioStore.snapshot().version)
        assertEquals(listOf("600519", "300750"), PortfolioStore.snapshot().favoriteCodes)
        assertEquals(200, PortfolioStore.snapshot().positions.single().quantity)
        assertEquals(100.0, PortfolioStore.snapshot().positions.single().averageCost)
    }

    @Test
    fun emptyAndBrokenJsonFallBackToEmptyState() {
        assertIs<PortfolioLoadResult.Success>(PortfolioStore.load(""))
        assertEquals(emptyList(), PortfolioStore.snapshot().favoriteCodes)

        PortfolioStore.toggleFavorite("600519")
        assertIs<PortfolioLoadResult.Failure>(PortfolioStore.load("{broken"))
        assertEquals(emptyList(), PortfolioStore.snapshot().favoriteCodes)
        assertEquals(emptyList(), PortfolioStore.snapshot().positions)

        assertIs<PortfolioLoadResult.Failure>(PortfolioStore.load("""{"version":99}"""))
        assertTrue(PortfolioStore.isLoaded())
    }

    @Test
    fun loadFiltersInvalidAndDuplicatePositions() {
        val json = """{"version":1,"favoriteCodes":["600519","600519",""],"positions":[{"code":"300750","quantity":200,"averageCost":100.0},{"code":"300750","quantity":300,"averageCost":110.0},{"code":"002594","quantity":50,"averageCost":200.0},{"code":"600519","quantity":100,"averageCost":0.0}]}"""

        assertIs<PortfolioLoadResult.Success>(PortfolioStore.load(json))
        assertEquals(listOf("600519"), PortfolioStore.snapshot().favoriteCodes)
        assertEquals(1, PortfolioStore.snapshot().positions.size)
        assertEquals("300750", PortfolioStore.snapshot().positions.single().code)
    }

    @Test
    fun repeatedBuyUsesWeightedAverageCost() {
        assertIs<TradeResult.Success>(PortfolioStore.buy("300750", 100, 100.0))
        assertIs<TradeResult.Success>(PortfolioStore.buy("300750", 300, 120.0))

        val position = PortfolioStore.snapshot().positions.single()
        assertEquals(400, position.quantity)
        assertTrue(abs(position.averageCost - 115.0) < 0.000001)
    }

    @Test
    fun sellKeepsAverageCostRejectsExcessAndRemovesClearedPosition() {
        PortfolioStore.buy("300750", 500, 123.45)

        val partial = assertIs<TradeResult.Success>(PortfolioStore.sell("300750", 200))
        assertEquals(300, partial.position?.quantity)
        assertEquals(123.45, partial.position?.averageCost)
        assertIs<TradeResult.Failure>(PortfolioStore.sell("300750", 400))
        assertEquals(300, PortfolioStore.snapshot().positions.single().quantity)

        val cleared = assertIs<TradeResult.Success>(PortfolioStore.sell("300750", 300))
        assertNull(cleared.position)
        assertTrue(PortfolioStore.snapshot().positions.isEmpty())
    }

    @Test
    fun tradesRejectNonBoardLotQuantities() {
        assertIs<TradeResult.Failure>(PortfolioStore.buy("300750", 0, 100.0))
        assertIs<TradeResult.Failure>(PortfolioStore.buy("300750", 150, 100.0))
        assertIs<TradeResult.Failure>(PortfolioStore.buy("300750", 100, 0.0))
        PortfolioStore.buy("300750", 100, 100.0)
        assertIs<TradeResult.Failure>(PortfolioStore.sell("300750", 50))
    }

    @Test
    fun observersReceiveImmutableSnapshotsUntilUnsubscribed() {
        val snapshots = mutableListOf<List<String>>()
        val unsubscribe = PortfolioStore.subscribe { snapshots.add(it.favoriteCodes) }
        PortfolioStore.toggleFavorite("600519")
        unsubscribe()
        PortfolioStore.toggleFavorite("300750")

        assertEquals(listOf(emptyList(), listOf("600519")), snapshots)
    }
}

