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
}
