package com.tencent.kuiklybase.chart.core

import com.tencent.kuiklybase.chart.core.cartesian.PlotRect
import com.tencent.kuiklybase.chart.core.cartesian.PlannedAxisTick
import com.tencent.kuiklybase.chart.model.ChartDataPoint
import com.tencent.kuiklybase.chart.model.ChartSeries
import com.tencent.kuiklybase.chart.model.ChartViewport
import com.tencent.kuiklybase.chart.model.OhlcPoint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LineRenderingTest {

    @Test
    fun annotationTextPosition_keepsGlyphBoundsInsidePlot() {
        val position = resolveAnnotationTextPosition(
            desiredX = 230f,
            desiredBaselineY = 5f,
            textWidth = 60f,
            textAscent = 11f,
            textDescent = 3f,
            plot = PlotRect(40f, 10f, 240f, 210f),
            padding = 2f,
        )

        assertEquals(178f, position.first)
        assertEquals(23f, position.second)
    }

    @Test
    fun containsNonFinitePoint_detectsMissingCoordinates() {
        val finite = ChartSeries(
            name = "finite",
            points = listOf(ChartDataPoint("", 1f, 2f)),
            color = 0xFF000000,
        )
        val missingY = ChartSeries(
            name = "missing",
            points = listOf(ChartDataPoint("", 2f, Float.NaN)),
            color = 0xFF000000,
        )

        assertFalse(ChartCanvasRenderer.containsNonFinitePoint(listOf(finite)))
        assertTrue(ChartCanvasRenderer.containsNonFinitePoint(listOf(finite, missingY)))
    }

    @Test
    fun lineSegments_connectsAcrossMissingPointsWhenEnabled() {
        val segments = ChartCanvasRenderer.lineSegments(
            listOf(1f to 10f, null, null, 4f to 40f),
            connectNulls = true,
        )

        assertEquals(listOf(listOf(1f to 10f, 4f to 40f)), segments)
    }

    @Test
    fun lineSegments_breaksAtMissingPointsWhenDisabled() {
        val segments = ChartCanvasRenderer.lineSegments(
            listOf(null, 1f to 10f, 2f to 20f, null, null, 5f to 50f, null),
            connectNulls = false,
        )

        assertEquals(
            listOf(
                listOf(1f to 10f, 2f to 20f),
                listOf(5f to 50f),
            ),
            segments,
        )
    }

    @Test
    fun stockSegments_breakAcrossWarmupNullsAndNonFiniteValues() {
        val segments = ChartCanvasRenderer.stockLineSegments(
            sourceX = listOf(0f, 1f, 2f, 3f, 4f, 5f),
            values = listOf(null, 10f, 11f, Float.NaN, 13f, 14f),
            visibleX = 0f..5f,
        )

        assertEquals(
            listOf(
                listOf(1f to 10f, 2f to 11f),
                listOf(4f to 13f, 5f to 14f),
            ),
            segments,
        )
    }

    @Test
    fun stockSegments_keepCrossingEndpointsAndOneNeighborAtEachViewportEdge() {
        assertEquals(
            listOf(listOf(0f to 10f, 10f to 20f)),
            ChartCanvasRenderer.stockLineSegments(
                sourceX = listOf(0f, 10f),
                values = listOf(10f, 20f),
                visibleX = 2f..8f,
            ),
        )
        assertEquals(
            listOf(listOf(0f to 0f, 5f to 5f, 10f to 10f)),
            ChartCanvasRenderer.stockLineSegments(
                sourceX = listOf(-5f, 0f, 5f, 10f, 15f),
                values = listOf(-5f, 0f, 5f, 10f, 15f),
                visibleX = 2f..8f,
            ),
        )
    }

    @Test
    fun stockSegments_doNotReconnectAcrossTrueNullOutsideViewport() {
        val segments = ChartCanvasRenderer.stockLineSegments(
            sourceX = listOf(0f, 1f, 2f, 3f),
            values = listOf(10f, null, 20f, 30f),
            visibleX = 1.5f..2.5f,
        )

        assertEquals(listOf(listOf(2f to 20f, 3f to 30f)), segments)
    }

    @Test
    fun stockDataToPixel_handlesExtremeFiniteViewportWithoutOverflow() {
        val plot = PlotRect(0f, 0f, 100f, 100f)
        val viewport = ChartViewport(-Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, Float.MAX_VALUE)

        val center = ChartCanvasRenderer.stockDataToPixel(plot, viewport, 0f, 0f)
        val minimum = ChartCanvasRenderer.stockDataToPixel(
            plot, viewport, -Float.MAX_VALUE, -Float.MAX_VALUE,
        )
        val maximum = ChartCanvasRenderer.stockDataToPixel(
            plot, viewport, Float.MAX_VALUE, Float.MAX_VALUE,
        )

        assertEquals(50f to 50f, center)
        assertEquals(0f to 100f, minimum)
        assertEquals(100f to 0f, maximum)
        assertTrue(listOfNotNull(center, minimum, maximum).all { it.first.isFinite() && it.second.isFinite() })
    }

    @Test
    fun stockDataToPixel_rejectsInvalidPlotCollapsedViewportAndNonFinitePoint() {
        val plot = PlotRect(0f, 0f, 100f, 100f)
        val viewport = ChartViewport(0f, 10f, 0f, 10f)

        assertEquals(null, ChartCanvasRenderer.stockDataToPixel(plot.copy(right = 0f), viewport, 1f, 1f))
        assertEquals(null, ChartCanvasRenderer.stockDataToPixel(plot, viewport.copy(xMax = 0f), 1f, 1f))
        assertEquals(null, ChartCanvasRenderer.stockDataToPixel(plot, viewport.copy(yMax = 0f), 1f, 1f))
        assertEquals(null, ChartCanvasRenderer.stockDataToPixel(plot, viewport, Float.NaN, 1f))
    }

    @Test
    fun stockLinePixelSegments_bailBeforeCanvasWhenAnyTransformIsUnsafe() {
        val extreme = ChartCanvasRenderer.stockLinePixelSegments(
            plot = PlotRect(0f, 0f, 100f, 100f),
            viewport = ChartViewport(-Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, Float.MAX_VALUE),
            segments = listOf(listOf(-Float.MAX_VALUE to -Float.MAX_VALUE, Float.MAX_VALUE to Float.MAX_VALUE)),
        )
        assertEquals(listOf(listOf(0f to 100f, 100f to 0f)), extreme)

        val invalid = ChartCanvasRenderer.stockLinePixelSegments(
            plot = PlotRect(0f, 0f, 0f, 100f),
            viewport = ChartViewport(0f, 10f, 0f, 10f),
            segments = listOf(listOf(0f to 0f, 10f to 10f)),
        )
        assertTrue(invalid.isEmpty())
    }

    @Test
    fun chartValueFormatting_neverOverflowsFiniteFloatLabels() {
        listOf(Float.MAX_VALUE, -Float.MAX_VALUE, Float.MAX_VALUE / 10f).forEach { value ->
            val text = formatChartValue(value)
            assertTrue(!text.contains("NaN", ignoreCase = true), text)
            assertTrue(!text.contains("Infinity", ignoreCase = true), text)
            assertTrue(text.toDoubleOrNull()?.isFinite() == true, text)
        }
    }

    @Test
    fun stockHistogramPlan_filtersNonFiniteAndKeepsSourceXWithZeroInBounds() {
        val bars = ChartCanvasRenderer.stockHistogramBars(
            sourceX = listOf(10f, 20f, 30f, 40f),
            values = listOf(3f, Float.NaN, -2f, 99f),
            visibleX = 5f..35f,
        )

        assertEquals(listOf(10f to 3f, 30f to -2f), bars.map { it.x to it.value })
        assertEquals(-2f..3f, ChartCanvasRenderer.stockHistogramBounds(bars))
    }

    @Test
    fun stockHistogramPixels_useSourceSpacingIncludingNullSlotsAndClipToPlot() {
        val bars = ChartCanvasRenderer.stockHistogramPixelBars(
            plot = PlotRect(0f, 0f, 100f, 100f),
            viewport = ChartViewport(0f, 10f, -10f, 10f),
            sourceX = listOf(0f, 2f, 4f, 10f),
            values = listOf(1f, null, -1f, 2f),
        )

        assertEquals(listOf(0f, 4f, 10f), bars.map { it.sourceX })
        assertEquals(6f, bars[0].width)
        assertEquals(12f, bars[1].width)
        assertEquals(18f, bars[2].width)
        assertTrue(bars.all { it.left >= 0f && it.left + it.width <= 100f })
    }

    @Test
    fun stockHistogramPixels_skipZeroAndGiveSubpixelNonzeroOnePixelHeight() {
        val bars = ChartCanvasRenderer.stockHistogramPixelBars(
            plot = PlotRect(0f, 0f, 100f, 100f),
            viewport = ChartViewport(0f, 2f, -100f, 100f),
            sourceX = listOf(0f, 1f, 2f),
            values = listOf(0f, 0.1f, Float.NaN),
        )

        assertEquals(1, bars.size)
        assertEquals(1f, bars.single().height)
        assertTrue(bars.single().left >= 0f)
        assertTrue(bars.single().left + bars.single().width <= 100f)
    }

    @Test
    fun candleAndVolumePlans_filterOffscreenExtremePointsBeforeTransform() {
        val points = listOf(
            OhlcPoint("visible", 5f, 4f, 8f, 2f, 6f, 40f),
            OhlcPoint("extreme", Float.MAX_VALUE, 1f, Float.MAX_VALUE, -Float.MAX_VALUE, 2f, Float.MAX_VALUE),
        )
        val plot = PlotRect(0f, 0f, 100f, 100f)
        val viewport = ChartViewport(0f, 10f, 0f, 10f)

        val candles = ChartCanvasRenderer.stockCandlestickPixelPlan(plot, viewport, points, 0.7f)
        val volumes = ChartCanvasRenderer.stockVolumePixelPlan(plot, viewport, points, 0.7f)

        assertEquals(listOf(0), candles.map { it.sourceIndex })
        assertEquals(listOf(5f), volumes.map { it.sourceX })
        assertTrue(candles.flatMap { listOf(it.centerX, it.highY, it.lowY, it.openY, it.closeY, it.bodyWidth) }.all(Float::isFinite))
        assertTrue(volumes.flatMap { listOf(it.left, it.top, it.width, it.height) }.all(Float::isFinite))
    }

    @Test
    fun candleAndVolumePlans_excludeSemanticallyMalformedOhlcPoints() {
        val valid = OhlcPoint("valid", 1f, 4f, 8f, 2f, 6f, 40f)
        val points = listOf(
            valid,
            valid.copy(label = "inverted", x = 2f, high = 1f, low = 3f),
            valid.copy(label = "open-outside", x = 3f, open = 9f),
            valid.copy(label = "close-outside", x = 4f, close = 9f),
        )
        val plot = PlotRect(0f, 0f, 100f, 100f)
        val viewport = ChartViewport(0f, 5f, 0f, 50f)

        val candles = ChartCanvasRenderer.stockCandlestickPixelPlan(plot, viewport, points, 0.7f)
        val volumes = ChartCanvasRenderer.stockVolumePixelPlan(plot, viewport, points, 0.7f)

        assertEquals(listOf(0), candles.map { it.sourceIndex })
        assertEquals(listOf(0), volumes.map { it.sourceIndex })
    }

    @Test
    fun stockVolumePlan_drawsPositiveAndNegativeBarsFromZeroBaselineAndClipsEdges() {
        val points = listOf(
            OhlcPoint("positive", 0f, 4f, 8f, 2f, 6f, 20f),
            OhlcPoint("negative", 10f, 6f, 8f, 2f, 4f, -10f),
        )

        val bars = ChartCanvasRenderer.stockVolumePixelPlan(
            plot = PlotRect(0f, 0f, 100f, 100f),
            viewport = ChartViewport(0f, 10f, -10f, 30f),
            points = points,
            candleWidthRatio = 0.7f,
        )

        assertEquals(2, bars.size)
        assertEquals(75f, bars[0].top + bars[0].height)
        assertEquals(75f, bars[1].top)
        assertTrue(bars.all { it.left >= 0f && it.left + it.width <= 100f })
        assertTrue(bars.flatMap { listOf(it.left, it.top, it.width, it.height) }.all(Float::isFinite))
    }

    @Test
    fun paneHeaderPlan_isCompactAndBoundedByPlotWidth() {
        val plot = PlotRect(40f, 8f, 150f, 80f)
        val widths = mapOf("MACD" to 28f, "DIFF:12.34" to 70f, "DEA:56.78" to 65f)
        val items = ChartCanvasRenderer.paneHeaderItems(
            plot = plot,
            title = "MACD",
            summaries = listOf("DIFF:12.34" to 0xFF000001, "DEA:56.78" to 0xFF000002),
            fontSize = 10f,
            measureText = { widths[it] ?: 10f },
        )

        assertTrue(items.isNotEmpty())
        assertEquals("MACD", items.first().text)
        assertTrue(items.all { it.x >= plot.left && it.x + it.estimatedWidth <= plot.right })
        assertTrue(items.size < 3)
    }

    @Test
    fun paneHeaderPlan_usesMeasuredCjkWidthsAndSkipsOverflowingEntries() {
        val measured = mutableListOf<String>()
        val items = ChartCanvasRenderer.paneHeaderItems(
            plot = PlotRect(10f, 5f, 80f, 30f),
            title = "中文宽标题",
            summaries = listOf("摘要" to 1L),
            fontSize = 12f,
            measureText = { text ->
                measured += text
                text.fold(0) { total, character -> total + if (character == '…') 8 else 16 }.toFloat()
            },
        )

        // 放不下的标题直接整段舍弃，避免末尾出现孤立 "…" 占位。
        assertTrue(items.isEmpty())
        assertTrue(items.all { it.x.isFinite() && it.x + it.estimatedWidth <= 76f })
    }

    @Test
    fun paneHeaderPlan_skipsTrailingSummaryWhenItCannotFit() {
        val plot = PlotRect(40f, 8f, 120f, 40f)
        val widths = mapOf("MA" to 14f, "DIFF:12.34" to 50f, "MACD:9.87" to 48f)
        val items = ChartCanvasRenderer.paneHeaderItems(
            plot = plot,
            title = "MA",
            summaries = listOf("DIFF:12.34" to 0xFF000001, "MACD:9.87" to 0xFF000002),
            fontSize = 10f,
            measureText = { widths[it] ?: 10f },
        )

        // 标题 "MA" 装得下，但两个 summary 都太宽 → 仅保留标题。
        assertEquals(listOf("MA"), items.map { it.text })
        assertTrue(items.none { it.text.endsWith("…") })
    }

    @Test
    fun paneHeaderPlan_keepsFittingSummariesAndSkipsOnlyTheOverflowingOne() {
        val plot = PlotRect(40f, 8f, 200f, 40f)
        // 标题 14f + 第一个 summary 50f + 间隔 10f = 74f；剩余宽度 200 - 4 - 44 - 74 = 78f。
        // 第二个 summary 48f 能放下，第三 65f 装不下。
        val widths = mapOf("MA" to 14f, "DIFF:12.34" to 50f, "MACD:9.87" to 48f, "WR:-5.0" to 65f)
        val items = ChartCanvasRenderer.paneHeaderItems(
            plot = plot,
            title = "MA",
            summaries = listOf(
                "DIFF:12.34" to 0xFF000001,
                "MACD:9.87" to 0xFF000002,
                "WR:-5.0" to 0xFF000003,
            ),
            fontSize = 10f,
            measureText = { widths[it] ?: 10f },
        )

        assertEquals(listOf("MA", "DIFF:12.34", "MACD:9.87"), items.map { it.text })
        assertTrue(items.none { it.text.endsWith("…") })
    }

    @Test
    fun paneHeaderPlan_rejectsNonFiniteOrInvertedPlots() {
        val invalid = listOf(
            PlotRect(Float.NaN, 0f, 100f, 20f),
            PlotRect(-Float.MAX_VALUE, 0f, Float.MAX_VALUE, 20f),
            PlotRect(10f, 0f, 5f, 20f),
            PlotRect(0f, 20f, 100f, 10f),
        )
        invalid.forEach { plot ->
            assertTrue(
                ChartCanvasRenderer.paneHeaderItems(plot, "title", emptyList(), 12f) { 20f }.isEmpty(),
            )
        }
    }

    @Test
    fun currentPriceLineEligibility_requiresFiniteVisiblePriceAndValidPlot() {
        val plot = PlotRect(40f, 8f, 240f, 208f)
        val viewport = ChartViewport(0f, 10f, 100f, 200f)

        assertEquals(108f, ChartCanvasRenderer.currentPriceLineY(plot, viewport, 150f))
        assertEquals(null, ChartCanvasRenderer.currentPriceLineY(plot, viewport, 250f))
        assertEquals(null, ChartCanvasRenderer.currentPriceLineY(plot, viewport, Float.NaN))
        assertEquals(null, ChartCanvasRenderer.currentPriceLineY(plot.copy(left = Float.NaN), viewport, 150f))
        assertEquals(
            null,
            ChartCanvasRenderer.currentPriceLineY(
                PlotRect(-Float.MAX_VALUE, 8f, Float.MAX_VALUE, 208f),
                viewport,
                150f,
            ),
        )
        assertEquals(null, ChartCanvasRenderer.currentPriceLineY(plot.copy(bottom = Float.POSITIVE_INFINITY), viewport, 150f))
        assertEquals(null, ChartCanvasRenderer.currentPriceLineY(plot, viewport.copy(yMax = viewport.yMin), 150f))
    }

    @Test
    fun closeLineDashAndEmptyStatePlans_arePureAndSanitized() {
        val points = listOf(
            OhlcPoint("a", 0f, 1f, 2f, 0f, 1.5f),
            OhlcPoint("bad", Float.NaN, 1f, 2f, 0f, Float.NaN),
        )
        val close = ChartCanvasRenderer.stockCloseLineData(points)
        assertEquals(listOf(0f, Float.NaN), close.sourceX)
        assertEquals(listOf(1.5f, null), close.values)

        val pricePlan = ChartCanvasRenderer.currentPriceLinePlan(
            PlotRect(0f, 0f, 100f, 100f),
            ChartViewport(0f, 1f, 0f, 10f),
            price = 5f,
            lineWidth = Float.NaN,
            dashLength = -1f,
            dashGap = Float.NaN,
        )
        assertEquals(50f, pricePlan?.y)
        assertEquals(1f, pricePlan?.lineWidth)
        assertEquals(listOf(4f, 3f), pricePlan?.dashPattern)
        assertEquals(emptyList(), ChartCanvasRenderer.currentPriceLineDashReset())

        assertTrue(ChartCanvasRenderer.shouldDrawPaneEmptyState(PlotRect(0f, 0f, 20f, 20f), "empty"))
        assertFalse(ChartCanvasRenderer.shouldDrawPaneEmptyState(PlotRect(Float.NaN, 0f, 20f, 20f), "empty"))
        assertFalse(ChartCanvasRenderer.shouldDrawPaneEmptyState(PlotRect(0f, 0f, 20f, 20f), "  "))
    }

    @Test
    fun stockCloseLine_breaksAtSemanticallyInvalidOhlcPoints() {
        val points = listOf(
            OhlcPoint("valid-1", 0f, 1f, 2f, 0f, 1.5f),
            OhlcPoint("invalid", 1f, 3f, 2f, 0f, 1.5f),
            OhlcPoint("valid-2", 2f, 1f, 2f, 0f, 1.5f),
        )
        val data = ChartCanvasRenderer.stockCloseLineData(points)

        assertEquals(listOf(1.5f, null, 1.5f), data.values)
        assertEquals(
            listOf(listOf(0f to 1.5f), listOf(2f to 1.5f)),
            ChartCanvasRenderer.stockLineSegments(data.sourceX, data.values, 0f..2f),
        )
    }

    @Test
    fun currentPriceLabelPlan_honorsVisibilityAndStaysInsidePlot() {
        val plot = PlotRect(10f, 20f, 110f, 120f)
        val viewport = ChartViewport(0f, 1f, 0f, 10f)
        val labeled = ChartCanvasRenderer.currentPriceRenderPlan(
            plot, viewport, 9.8f, 2f, 4f, 3f,
            showLabel = true, labelText = "9.80", fontSize = 12f, measuredLabelWidth = 40f,
        )
        val lineOnly = ChartCanvasRenderer.currentPriceRenderPlan(
            plot, viewport, 5f, 2f, 4f, 3f,
            showLabel = false, labelText = "5.00", fontSize = 12f, measuredLabelWidth = 40f,
        )

        assertTrue(labeled?.label != null)
        assertTrue(labeled!!.label!!.left >= plot.left && labeled.label!!.right <= plot.right)
        assertTrue(labeled.label!!.top >= plot.top && labeled.label!!.bottom <= plot.bottom)
        assertEquals(null, lineOnly?.label)
        assertEquals(null, ChartCanvasRenderer.currentPriceRenderPlan(
            plot, viewport, Float.NaN, 1f, 4f, 3f, true, "bad", 12f, 20f,
        ))
    }

    @Test
    fun plannedGridXs_extractsOnlyFinitePositionsInsidePlot() {
        val plot = PlotRect(40f, 8f, 240f, 208f)
        val ticks = listOf(
            PlannedAxisTick(0f, "a", 40f, 38f, 42f),
            PlannedAxisTick(1f, "b", 120f, 118f, 122f),
            PlannedAxisTick(2f, "c", Float.NaN, 0f, 0f),
            PlannedAxisTick(3f, "d", 280f, 278f, 282f),
        )

        assertEquals(listOf(40f, 120f), ChartCanvasRenderer.plannedGridXs(plot, ticks))
    }

    @Test
    fun axisLabelWidthCache_partitionsSameTextByFontSize() {
        val cache = AxisLabelWidthCache(8)
        var measurements = 0

        cache.resolve(12f, "latest") { ++measurements; 30f }
        cache.resolve(12f, "latest") { ++measurements; 30f }
        cache.resolve(16f, "latest") { ++measurements; 40f }

        assertEquals(2, measurements)
        assertEquals(2, cache.size)
    }

    @Test
    fun paneEmptyStateCenter_isFiniteForExtremeFinitePlotCoordinates() {
        val plot = PlotRect(Float.MAX_VALUE / 2f, Float.MAX_VALUE / 2f, Float.MAX_VALUE, Float.MAX_VALUE)

        val center = ChartCanvasRenderer.paneEmptyStateCenter(plot)

        assertTrue(center.first.isFinite())
        assertTrue(center.second.isFinite())
        assertTrue(center.first in plot.left..plot.right)
        assertTrue(center.second in plot.top..plot.bottom)
    }

    @Test
    fun suppliedXAxisPlanIsPreservedWithoutMeasuringOrReplanning() {
        val supplied = listOf(
            PlannedAxisTick(7f, "supplied", 123f, 100f, 146f),
            PlannedAxisTick(2f, "order", 55f, 35f, 75f),
        )
        var measurements = 0

        val resolved = resolveCartesianXAxisTicks(
            plannedXTicks = supplied,
            viewport = ChartViewport(0f, 10f, 0f, 1f),
            plot = PlotRect(40f, 8f, 288f, 180f),
            fontSize = 12f,
            xTicks = listOf(AxisTick(5f, "ignored")),
            measureText = { measurements++; 20f },
        )

        assertEquals(supplied, resolved)
        assertEquals(0, measurements)
    }

    @Test
    fun defaultXAxisPlanRemainsCompatibleWithExistingPlanner() {
        val viewport = ChartViewport(0f, 10f, 0f, 1f)
        val plot = PlotRect(40f, 8f, 288f, 180f)
        val xTicks = listOf(AxisTick(2f, "two"), AxisTick(5f, "five"), AxisTick(8f, "eight"))
        val measure: (String) -> Float = { it.length * 7f }

        assertEquals(
            planCartesianXAxisTicks(viewport, plot, 12f, xTicks, measureText = measure),
            resolveCartesianXAxisTicks(null, viewport, plot, 12f, xTicks, measureText = measure),
        )
    }
}
