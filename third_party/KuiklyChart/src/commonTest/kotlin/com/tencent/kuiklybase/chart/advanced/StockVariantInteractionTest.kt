package com.tencent.kuiklybase.chart.advanced

import com.tencent.kuiklybase.chart.config.StockThemePreset
import com.tencent.kuiklybase.chart.core.AxisLabelWidthCache
import com.tencent.kuiklybase.chart.model.ChartDataPoint
import com.tencent.kuiklybase.chart.model.OhlcPoint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StockVariantInteractionTest {
    @Test
    fun everyAdvancedStockKind_supportsHorizontalViewport() {
        val kinds = listOf(
            AdvancedChartKind.OHLC,
            AdvancedChartKind.STOCK_AREA,
            AdvancedChartKind.STOCK_LINE,
            AdvancedChartKind.RENKO,
            AdvancedChartKind.KAGI,
            AdvancedChartKind.POINT_FIGURE,
        )

        assertTrue(kinds.all(::supportsStockViewport))
    }

    @Test
    fun viewport_clampsAndKeepsMinimumSpan() {
        val endAnchored = normalizeStockViewport(0.95f, 1.2f, 0.16f)
        assertEquals(0.75f, endAnchored.start, 0.0001f)
        assertEquals(1f, endAnchored.endInclusive, 0.0001f)

        val startAnchored = normalizeStockViewport(-0.2f, 0.4f, 0.16f)
        assertEquals(0f, startAnchored.start, 0.0001f)
        assertEquals(0.6f, startAnchored.endInclusive, 0.0001f)

        val minimum = normalizeStockViewport(0.5f, 0.55f, 0.16f)
        assertEquals(0.5f, minimum.start, 0.0001f)
        assertEquals(0.66f, minimum.endInclusive, 0.0001f)
    }

    @Test
    fun latestAnchor_respectsConfiguredMinimumVisibleRatio() {
        val viewport = latestAnchoredStockViewport(initialVisibleRatio = 0.05f, minimumVisibleRatio = 0.3f)
        assertEquals(0.7f, viewport.start, 0.0001f)
        assertEquals(1f, viewport.endInclusive, 0.0001f)

        val full = latestAnchoredStockViewport(initialVisibleRatio = 1.5f, minimumVisibleRatio = 0.3f)
        assertEquals(0f, full.start, 0.0001f)
        assertEquals(1f, full.endInclusive, 0.0001f)
    }

    @Test
    fun visibleRange_mapsSlotsBackToSourceIndices() {
        assertEquals(6..11, visibleStockIndexRange(12, 0.5f, 1f))
        assertEquals(8, sourceIndexForVisibleSlot(slot = 2, count = 12, start = 0.5f, end = 1f))
    }

    @Test
    fun everyStockKind_usesOneViewportMappingForRangePixelsAndHits() {
        val cases = listOf(
            AdvancedChartKind.OHLC to StockViewportMode.SLOT,
            AdvancedChartKind.STOCK_AREA to StockViewportMode.CONTINUOUS,
            AdvancedChartKind.STOCK_LINE to StockViewportMode.CONTINUOUS,
            AdvancedChartKind.RENKO to StockViewportMode.SLOT,
            AdvancedChartKind.KAGI to StockViewportMode.CONTINUOUS,
            AdvancedChartKind.POINT_FIGURE to StockViewportMode.SLOT,
        )

        cases.forEach { (kind, expectedMode) ->
            val mapping = stockViewportMapping(kind, count = 10, start = 0.4f, end = 0.8f)

            assertEquals(expectedMode, mapping.mode, kind.name)
            assertEquals(4..7, mapping.visibleSourceIndices, kind.name)
            assertEquals(6, mapping.sourceIndexAtPlotRatio(0.625f), kind.name)
            assertEquals(
                if (expectedMode == StockViewportMode.SLOT) 0.625f else (6f / 9f - 0.4f) / 0.4f,
                mapping.plotRatioForSourceIndex(6),
                0.0001f,
                kind.name,
            )
            assertEquals(
                if (expectedMode == StockViewportMode.SLOT) 4..7 else 3..8,
                mapping.renderSourceIndices,
                kind.name,
            )
        }
    }

    @Test
    fun everyStockKind_excludesOffscreenSpikesFromVisibleBounds() {
        val lower = listOf(-900f, 9f, 10f, 11f, 12f, 900f)
        val upper = listOf(-800f, 11f, 12f, 13f, 14f, 1_000f)

        listOf(
            AdvancedChartKind.OHLC,
            AdvancedChartKind.STOCK_AREA,
            AdvancedChartKind.STOCK_LINE,
            AdvancedChartKind.RENKO,
            AdvancedChartKind.KAGI,
            AdvancedChartKind.POINT_FIGURE,
        ).forEach { kind ->
            val mapping = stockViewportMapping(kind, lower.size, 0.2f, 0.8f)
            val bounds = visibleStockValueBounds(mapping, lower, upper)

            assertEquals(9f, bounds?.first, kind.name)
            assertEquals(14f, bounds?.second, kind.name)
        }
    }

    @Test
    fun stockViewportMapping_handlesEmptySingleAndTinyData() {
        val empty = stockViewportMapping(AdvancedChartKind.STOCK_LINE, 0, 0.4f, 0.6f)
        assertTrue(empty.visibleSourceIndices.isEmpty())
        assertEquals(-1, empty.sourceIndexAtPlotRatio(0.5f))
        assertEquals(null, visibleStockValueBounds(empty, emptyList()))

        val single = stockViewportMapping(AdvancedChartKind.STOCK_LINE, 1, 0.4f, 0.6f)
        assertEquals(0..0, single.visibleSourceIndices)
        assertEquals(0, single.sourceIndexAtPlotRatio(0.5f))
        assertEquals(0.5f, single.plotRatioForSourceIndex(0))
        assertEquals(7f to 7f, visibleStockValueBounds(single, listOf(7f)))

        val tiny = stockViewportMapping(AdvancedChartKind.STOCK_LINE, 2, 0.42f, 0.58f)
        assertTrue(tiny.visibleSourceIndices.isEmpty())
        assertEquals(0..1, tiny.renderSourceIndices)
        assertEquals(-1, tiny.sourceIndexAtPlotRatio(0.5f))
    }

    @Test
    fun invalidAdvancedStockElements_areNotSelectable() {
        val malformed = OhlcPoint("bad", 0f, 10f, 9f, 11f, 10f)
        assertEquals(null, AdvancedChartData.Ohlc(listOf(malformed)).selection(0))
        listOf(
            AdvancedChartKind.STOCK_LINE,
            AdvancedChartKind.STOCK_AREA,
            AdvancedChartKind.RENKO,
            AdvancedChartKind.KAGI,
        ).forEach { kind ->
            assertEquals(
                null,
                AdvancedChartData.Points(kind, listOf(ChartDataPoint("bad", 0f, Float.NaN))).selection(0),
                kind.name,
            )
        }
        assertEquals(null, AdvancedChartData.PointFigure(listOf(PointFigureColumn("bad", -1, true))).selection(0))
    }

    @Test
    fun continuousStockGeometry_splitsAtEveryInvalidPoint() {
        val items = listOf(
            ChartDataPoint("A", 0f, 1f),
            ChartDataPoint("gap", 1f, Float.NaN),
            ChartDataPoint("B", 2f, 2f),
            ChartDataPoint("C", 3f, 3f),
            ChartDataPoint("gap2", 4f, Float.POSITIVE_INFINITY),
        )
        val runs = visibleFiniteStockRuns(
            stockViewportMapping(AdvancedChartKind.STOCK_LINE, items.size, 0f, 1f),
            items,
        )

        assertEquals(listOf(listOf(0), listOf(2, 3)), runs.map { run -> run.map { it.index } })
        assertTrue(runs.flatten().all { it.value.x.isFinite() && it.value.y.isFinite() })
    }

    @Test
    fun continuousBounds_useClippedBoundaryValuesWhenNoMarkerIsVisible() {
        val mapping = stockViewportMapping(AdvancedChartKind.STOCK_LINE, 5, 0.3f, 0.7f)
        val bounds = visibleStockValueBounds(mapping, listOf(-1_000f, 10f, 12f, 14f, 1_000f))

        assertEquals(10.4f, bounds?.first ?: Float.NaN, 0.0001f)
        assertEquals(13.6f, bounds?.second ?: Float.NaN, 0.0001f)
    }

    @Test
    fun kagiBounds_useVisibleHorizontalStepInsteadOfLinearInterpolation() {
        val mapping = stockViewportMapping(AdvancedChartKind.KAGI, 2, 0.42f, 0.58f)

        val bounds = kagiVisibleValueBounds(mapping, listOf(0f, 100f))

        assertTrue(100f in bounds)
        assertTrue(bounds.start > 58f)
        assertTrue(bounds.start.isFinite())
        assertTrue(bounds.endInclusive.isFinite())
    }

    @Test
    fun kagiBounds_includeVerticalEndpointsAndFractionalHorizontalWindows() {
        val values = listOf(-1_000f, 10f, 30f, 20f, 1_000f)
        val vertical = kagiVisibleValueBounds(
            stockViewportMapping(AdvancedChartKind.KAGI, values.size, 0.5f, 0.6f),
            values,
        )
        val fractional = kagiVisibleValueBounds(
            stockViewportMapping(AdvancedChartKind.KAGI, values.size, 0.3f, 0.45f),
            values,
        )

        assertTrue(20f in vertical)
        assertTrue(30f in vertical)
        assertTrue(30f in fractional)
        assertTrue(fractional.start > 10f)
    }

    @Test
    fun kagiBounds_excludeSegmentsOutsideViewport() {
        val bounds = kagiVisibleValueBounds(
            stockViewportMapping(AdvancedChartKind.KAGI, 5, 0.3f, 0.45f),
            listOf(-1_000f, 10f, 30f, 20f, 1_000f),
        )

        assertTrue(bounds.start > -1_000f)
        assertTrue(bounds.endInclusive < 1_000f)
    }

    @Test
    fun kagiBounds_returnFiniteDefaultForEmptySingleAndInvalidData() {
        val empty = kagiVisibleValueBounds(
            stockViewportMapping(AdvancedChartKind.KAGI, 0, 0.4f, 0.6f),
            emptyList(),
        )
        val single = kagiVisibleValueBounds(
            stockViewportMapping(AdvancedChartKind.KAGI, 1, 0.4f, 0.6f),
            listOf(7f),
        )
        val invalid = kagiVisibleValueBounds(
            stockViewportMapping(AdvancedChartKind.KAGI, 2, 0.4f, 0.6f),
            listOf(Float.NaN, Float.POSITIVE_INFINITY),
        )

        assertEquals(0f..1f, empty)
        assertEquals(0f..1f, single)
        assertEquals(0f..1f, invalid)
    }

    @Test
    fun selectedSourceIndex_isHiddenOffscreenAndInvalidatedOnlyByDataSize() {
        listOf(
            AdvancedChartKind.OHLC,
            AdvancedChartKind.STOCK_AREA,
            AdvancedChartKind.STOCK_LINE,
            AdvancedChartKind.RENKO,
            AdvancedChartKind.KAGI,
            AdvancedChartKind.POINT_FIGURE,
        ).forEach { kind ->
            val mapping = stockViewportMapping(kind, 10, 0.4f, 0.8f)

            assertEquals(StockSelectionVisibility.NONE, stockSelectionVisibility(-1, 10, mapping), kind.name)
            assertEquals(StockSelectionVisibility.HIDDEN, stockSelectionVisibility(2, 10, mapping), kind.name)
            assertEquals(StockSelectionVisibility.VISIBLE, stockSelectionVisibility(6, 10, mapping), kind.name)
            assertEquals(StockSelectionVisibility.INVALID, stockSelectionVisibility(10, 10, mapping), kind.name)
        }
    }

    @Test
    fun selectionToggle_clearsRepeatedOrMissedSelection() {
        assertEquals(4, toggleAdvancedSelection(current = 2, hit = 4))
        assertEquals(-1, toggleAdvancedSelection(current = 4, hit = 4))
        assertEquals(-1, toggleAdvancedSelection(current = 4, hit = -1))
    }

    @Test
    fun tooltip_usesVariantSpecificSummary() {
        val pointFigure = AdvancedChartSelection(
            kind = AdvancedChartKind.POINT_FIGURE,
            index = 2,
            label = "P3",
            value = 5f,
            summary = "列高:5",
        )
        assertEquals("P3  列高:5", formatStockSelection(pointFigure))

        val price = AdvancedChartSelection(AdvancedChartKind.KAGI, 1, "K2", 7.5f)
        assertEquals("K2  价格:7.5", formatStockSelection(price))
    }

    @Test
    fun advancedStockDefaults_enableBrokerInteractions() {
        val attr = AdvancedChartAttr()
        applyStockInteractionDefaults(attr, AdvancedChartKind.OHLC)

        assertEquals(StockThemePreset.LIGHT, attr.preset)
        assertTrue(attr.interaction.enableLongPressInspect)
        assertTrue(attr.interaction.enableCrosshair)
        assertTrue(attr.interaction.enablePan)
        assertTrue(attr.interaction.enableScale)
        assertTrue(attr.interaction.enableReset)
    }

    @Test
    fun advancedStockAxis_plansZoomedSourceIndicesWithoutOverlapOrForcedEdges() {
        val labels = List(64) { index -> "2026-08-${(index + 1).toString().padStart(2, '0')}" }

        listOf(1f, 0.65f, 0.35f, 0.16f).forEach { ratio ->
            val visibleStart = (labels.size - labels.size * ratio).coerceAtLeast(0f)
            val planned = planAdvancedAxisLabels(
                candidates = labels.mapIndexed(::AdvancedAxisLabel),
                visibleMin = visibleStart,
                visibleMax = labels.lastIndex.toFloat(),
                plotLeft = 34f,
                plotWidth = 248f,
                fontSize = 10f,
                measureText = { 68f },
            )

            assertTrue(planned.isNotEmpty(), "ratio=$ratio")
            assertTrue(planned.all { it.left >= 36f && it.right <= 280f }, "ratio=$ratio")
            assertTrue(
                planned.zipWithNext().all { (left, right) -> left.right + 8f <= right.left },
                "ratio=$ratio",
            )
            assertTrue(planned.none { it.value == labels.lastIndex.toFloat() }, "ratio=$ratio")
            assertTrue(planned.all { tick -> labels[tick.value.toInt()] == tick.text }, "ratio=$ratio")
        }
    }

    @Test
    fun slotCenteredAxis_matchesRendererTransformForSmallFractionalWindow() {
        val candidates = (10..12).map { AdvancedAxisLabel(it, "D$it") }
        val bounds = slotCenteredAdvancedAxisBounds(10, 12)
        val planned = planAdvancedAxisLabels(
            candidates = candidates,
            visibleMin = bounds.first,
            visibleMax = bounds.second,
            plotLeft = 34f,
            plotWidth = 248f,
            fontSize = 10f,
            measureText = { 8f },
        )
        val slot = 248f / 3f

        assertTrue(planned.isNotEmpty())
        planned.forEach { tick ->
            val rendererX = 34f + slot * (tick.value - 10f + 0.5f)
            assertEquals(rendererX, tick.x, 0.001f)
        }
    }

    @Test
    fun continuousAxis_matchesViewportTransformForFractionalZoom() {
        val count = 20
        val viewportStart = 0.375f
        val viewportEnd = 0.525f
        val bounds = continuousAdvancedAxisBounds(count, viewportStart, viewportEnd)
        val planned = planAdvancedAxisLabels(
            candidates = List(count) { AdvancedAxisLabel(it, "D$it") },
            visibleMin = bounds.first,
            visibleMax = bounds.second,
            plotLeft = 34f,
            plotWidth = 248f,
            fontSize = 10f,
            measureText = { 8f },
        )

        assertTrue(planned.size in 2..3)
        planned.forEach { tick ->
            val ratio = tick.value / (count - 1f)
            val rendererX = 34f + 248f * (ratio - viewportStart) / (viewportEnd - viewportStart)
            assertEquals(rendererX, tick.x, 0.001f)
        }
    }

    @Test
    fun everyStockKind_ticksAlignWithActualMarkerTransformAcrossWidthsAndZooms() {
        val kinds = listOf(
            AdvancedChartKind.OHLC,
            AdvancedChartKind.STOCK_AREA,
            AdvancedChartKind.STOCK_LINE,
            AdvancedChartKind.RENKO,
            AdvancedChartKind.KAGI,
            AdvancedChartKind.POINT_FIGURE,
        )

        kinds.forEach { kind ->
            listOf(120f, 248f).forEach { width ->
                listOf(0.65f, 0.35f, 0.16f).forEach { ratio ->
                    val mapping = stockViewportMapping(kind, 40, 1f - ratio, 1f)
                    val planned = planAdvancedAxisLabels(
                        candidates = List(40) { AdvancedAxisLabel(it, "D$it") },
                        visibleMin = mapping.axisBounds.first,
                        visibleMax = mapping.axisBounds.second,
                        plotLeft = 34f,
                        plotWidth = width,
                        fontSize = 10f,
                        measureText = { 16f },
                    )

                    assertTrue(planned.isNotEmpty(), "$kind width=$width ratio=$ratio")
                    planned.forEach { tick ->
                        val markerX = 34f + width * mapping.plotRatioForSourceIndex(tick.value.toInt())
                        assertEquals(markerX, tick.x, 0.001f, "$kind width=$width ratio=$ratio")
                    }
                    assertTrue(
                        planned.zipWithNext().all { (left, right) -> left.right + 8f <= right.left },
                        "$kind width=$width ratio=$ratio",
                    )
                }
            }
        }
    }

    @Test
    fun advancedAxis_measuresOnlyVisibleLabelsAndCachesWidths() {
        val cache = AxisLabelWidthCache(maxEntries = 8)
        val candidates = List(20) { AdvancedAxisLabel(it, "D$it") }
        var measurements = 0
        repeat(2) {
            planAdvancedAxisLabels(
                candidates = candidates,
                visibleMin = 8f,
                visibleMax = 10f,
                plotLeft = 34f,
                plotWidth = 248f,
                fontSize = 10f,
                widthCache = cache,
                measureText = { measurements++; 12f },
            )
        }

        assertEquals(4, measurements)
        assertTrue(cache.size <= 8)
    }

    @Test
    fun emptyAdvancedAxis_hasNoLabelsOrMeasurements() {
        var measurements = 0
        val planned = planAdvancedAxisLabels(
            candidates = emptyList(),
            visibleMin = 0f,
            visibleMax = 1f,
            plotLeft = 34f,
            plotWidth = 248f,
            fontSize = 10f,
            measureText = { measurements++; 12f },
        )

        assertTrue(planned.isEmpty())
        assertEquals(0, measurements)
    }
}
