package com.tencent.kuiklybase.chart.advanced

import com.tencent.kuiklybase.chart.model.ChartDataPoint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class AdvancedChartContractsTest {
    @Test
    fun advancedChartKind_exposesFourteenPublicChartTypes() {
        assertEquals(
            listOf(
                AdvancedChartKind.DUAL_AXIS_BAR,
                AdvancedChartKind.WATERFALL,
                AdvancedChartKind.HISTOGRAM,
                AdvancedChartKind.BULLET,
                AdvancedChartKind.HALF_DONUT,
                AdvancedChartKind.ROSE,
                AdvancedChartKind.SUNBURST,
                AdvancedChartKind.NESTED_PIE,
                AdvancedChartKind.OHLC,
                AdvancedChartKind.STOCK_AREA,
                AdvancedChartKind.STOCK_LINE,
                AdvancedChartKind.RENKO,
                AdvancedChartKind.KAGI,
                AdvancedChartKind.POINT_FIGURE,
            ),
            AdvancedChartKind.entries,
        )
    }

    @Test
    fun publicModels_keepOnlyChartSpecificFields() {
        assertEquals("一月", DualAxisPoint("一月", 42f, 31f).label)
        assertEquals(true, WaterfallPoint("总计", 95f, isTotal = true).isTotal)
        assertEquals(12f, HistogramBin("0-10", 12f).value)
        assertEquals(0.86f, BulletChartItem("收入", 0.78f, 0.86f).target)
        assertEquals(1, SunburstNode("华东", 38f, 0xFF1677FF, depth = 1).depth)
        assertEquals(NestedPieRing.OUTER, NestedPieSlice("搜索", 18f, 0xFF69B1FF, NestedPieRing.OUTER).ring)
        assertEquals(true, PointFigureColumn("X1", 3, rising = true).rising)
        assertEquals(102f, ChartDataPoint("D1", 0f, 102f).y)
    }

    @Test
    fun eventDsl_registersSelectionAndViewportHandlers() {
        val event = AdvancedChartEvent()
        event.itemClick { }
        event.viewportChange { _, _ -> }

        assertNotNull(event.onItemClick)
        assertNotNull(event.onViewportChange)
    }
}
