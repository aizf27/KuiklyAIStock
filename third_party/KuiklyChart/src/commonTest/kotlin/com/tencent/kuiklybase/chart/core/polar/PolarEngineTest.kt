package com.tencent.kuiklybase.chart.core.polar

import com.tencent.kuiklybase.chart.model.ChartSlice
import com.tencent.kuiklybase.chart.model.RadarDimension
import com.tencent.kuiklybase.chart.model.RadarSeries
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PolarEngineTest {
    @Test
    fun layoutEngine_computesDonutInnerRadius() {
        val layout = PolarLayoutEngine.compute(300f, 300f, ringWidth = 40f, isDonut = true)
        assertTrue(layout.outerRadius > layout.innerRadius)
        assertEquals(150f, layout.centerX)
        assertEquals(150f, layout.centerY)
    }

    @Test
    fun hitTester_findsSlice() {
        val slices = listOf(
            ChartSlice("A", 25f, 0xFFFF0000),
            ChartSlice("B", 75f, 0xFF00FF00),
        )
        val layout = PolarLayout(150f, 150f, 100f, 0f)
        val hit = PolarHitTester.hitSlice(layout, slices, -90f, 150f, 70f)
        assertNotNull(hit)
    }

    @Test
    fun hitTester_ignoresNegativeSliceValues() {
        val slices = listOf(
            ChartSlice("neg", -50f, 0xFFFF0000),
            ChartSlice("pos", 100f, 0xFF00FF00),
        )
        val layout = PolarLayout(150f, 150f, 100f, 0f)
        // Negative value contributes 0 sweep; click near top (startAngle -90) should hit the positive slice.
        val hit = PolarHitTester.hitSlice(layout, slices, -90f, 150f, 70f)
        assertEquals(1, hit)
    }

    @Test
    fun radarValueRatio_guardsNonPositiveMaxAndNonFinite() {
        assertEquals(0f, PolarScale.radarValueRatio(50f, 0f))
        assertEquals(0f, PolarScale.radarValueRatio(50f, -10f))
        assertEquals(0f, PolarScale.radarValueRatio(Float.NaN, 100f))
        assertEquals(0.5f, PolarScale.radarValueRatio(50f, 100f))
        assertEquals(1f, PolarScale.radarValueRatio(200f, 100f))
    }

    @Test
    fun radarHitTester_returnsNearestSeriesAndDimension() {
        val dimensions = listOf(
            RadarDimension("A", 100f),
            RadarDimension("B", 100f),
        )
        val series = listOf(
            RadarSeries("inner", listOf(25f, 25f), 0xFFFF0000),
            RadarSeries("outer", listOf(75f, 75f), 0xFF00FF00),
        )

        val hit = PolarScale.hitRadarPoint(
            centerX = 100f,
            centerY = 100f,
            radius = 80f,
            dimensions = dimensions,
            series = series,
            x = 100f,
            y = 40f,
        )

        assertEquals(1 to 0, hit)
    }
}
