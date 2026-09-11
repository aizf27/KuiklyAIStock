package com.tencent.kuiklybase.chart.pie

import com.tencent.kuiklybase.chart.model.ChartSlice
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PolarLegendInteractionTest {
    @Test
    fun interactiveLegendFiltersSlicesWithoutMutatingSource() {
        val source = listOf(
            ChartSlice("移动端", 10f, 0L),
            ChartSlice("桌面端", 20f, 0L),
        )

        val hidden = toggleHiddenPolarItem(emptySet(), "移动端")

        assertEquals(listOf("桌面端"), filterVisibleSlices(source, hidden).map { it.label })
        assertEquals(listOf("移动端", "桌面端"), source.map { it.label })
        assertTrue(toggleHiddenPolarItem(hidden, "移动端").isEmpty())
    }
}
