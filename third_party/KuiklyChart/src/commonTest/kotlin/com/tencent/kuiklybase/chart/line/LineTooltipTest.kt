package com.tencent.kuiklybase.chart.line

import com.tencent.kuiklybase.chart.model.ChartDataPoint
import com.tencent.kuiklybase.chart.model.ChartSeries
import kotlin.test.Test
import kotlin.test.assertEquals

class LineTooltipTest {
    private val series = listOf(
        ChartSeries(
            "华东",
            listOf(ChartDataPoint("1月", 1f, 120f), ChartDataPoint("2月", 2f, 151f)),
            0xFF1677FF,
        ),
        ChartSeries(
            "华南",
            listOf(ChartDataPoint("1月", 1f, 98f), ChartDataPoint("2月", 2f, 138f)),
            0xFFFAAD14,
        ),
    )

    @Test
    fun sharedTooltip_collectsEverySeriesAtSelectedX() {
        val context = buildLineTooltipContext(
            series,
            selectedSeriesIndex = 0,
            selectedPointIndex = 1,
            sharedByX = true,
        )

        assertEquals("2月", context.label)
        assertEquals(listOf("华东", "华南"), context.items.map { it.seriesName })
        assertEquals(listOf(151f, 138f), context.items.map { it.point.y })
    }

    @Test
    fun nonSharedTooltip_containsOnlySelectedPoint() {
        val context = buildLineTooltipContext(
            series,
            selectedSeriesIndex = 1,
            selectedPointIndex = 0,
            sharedByX = false,
        )

        assertEquals(1, context.items.size)
        assertEquals("华南", context.items.single().seriesName)
    }
}
