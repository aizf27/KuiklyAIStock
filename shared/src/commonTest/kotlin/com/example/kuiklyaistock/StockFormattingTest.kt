package com.example.kuiklyaistock

import kotlin.test.Test
import kotlin.test.assertEquals

class StockFormattingTest {
    @Test
    fun removesRedundantDecimalFromIntegerUnits() {
        assertEquals("2千万", formatStockVolume(20_000_000))
        assertEquals("3亿", formatStockTurnover(300_000_000.0))
    }

    @Test
    fun keepsOneDecimalForCompactUnits() {
        assertEquals("1.2千万", formatStockVolume(12_000_000))
        assertEquals("123.5万", formatStockTurnover(1_234_567.0))
    }

    @Test
    fun formatsPricePercentAndUnitBoundaries() {
        assertEquals("12.35", formatStockPrice(12.345))
        assertEquals("+0.50", formatStockSigned(0.5))
        assertEquals("-0.50%", formatStockPercent(-0.5))
        assertEquals("0万", formatStockVolume(0))
        assertEquals("1千万", formatStockVolume(10_000_000))
        assertEquals("1亿", formatStockTurnover(100_000_000.0))
    }

    @Test
    fun mapsRiseFallAndFlatColors() {
        assertEquals(StockDesignTokens.rise, stockChangeColor(0.01))
        assertEquals(StockDesignTokens.fall, stockChangeColor(-0.01))
        assertEquals(StockDesignTokens.flat, stockChangeColor(0.0))
    }
}
