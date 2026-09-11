package com.tencent.kuiklybase.chart.area

import com.tencent.kuiklybase.chart.model.ChartDataPoint
import com.tencent.kuiklybase.chart.model.ChartSeries
import kotlin.test.Test
import kotlin.test.assertEquals

class AreaViewportTest {
    @Test
    fun stackedViewport_containsIntermediatePositiveAndNegativePrefixes() {
        val series = listOf(
            ChartSeries("first", listOf(ChartDataPoint("A", 0f, -100f)), 0L),
            ChartSeries("second", listOf(ChartDataPoint("A", 0f, 160f)), 0L),
            ChartSeries("third", listOf(ChartDataPoint("A", 0f, -60f)), 0L),
        )

        val viewport = stackedAreaViewport(series)

        assertEquals(-112.8f, viewport.yMin)
        assertEquals(72.8f, viewport.yMax)
    }

    @Test
    fun streamViewport_centersLargestStackWithPadding() {
        val series = listOf(
            ChartSeries(
                name = "first",
                color = 0L,
                points = listOf(
                    ChartDataPoint("A", 0f, 40f),
                    ChartDataPoint("B", 1f, 60f),
                ),
            ),
            ChartSeries(
                name = "second",
                color = 0L,
                points = listOf(
                    ChartDataPoint("A", 0f, 30f),
                    ChartDataPoint("B", 1f, 40f),
                ),
            ),
        )

        val viewport = streamViewport(series)

        assertEquals(-54f, viewport.yMin)
        assertEquals(54f, viewport.yMax)
    }
}
