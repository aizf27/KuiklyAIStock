package com.tencent.kuiklybase.chart.stock

import com.tencent.kuikly.core.reactive.collection.ObservableList
import com.tencent.kuiklybase.chart.config.StockAuxiliaryIndicator
import com.tencent.kuiklybase.chart.config.ChartThemeOptions
import com.tencent.kuiklybase.chart.config.StockMainIndicator
import com.tencent.kuiklybase.chart.config.StockPriceDisplayMode
import com.tencent.kuiklybase.chart.config.StockPaneRenderConfig
import com.tencent.kuiklybase.chart.config.StockRenderConfig
import com.tencent.kuiklybase.chart.config.resolveStockTheme
import com.tencent.kuiklybase.chart.config.StockThemePreset
import com.tencent.kuiklybase.chart.core.cartesian.PlotRect
import com.tencent.kuiklybase.chart.core.cartesian.CartesianLayoutEngine
import com.tencent.kuiklybase.chart.model.ChartViewport
import com.tencent.kuiklybase.chart.model.OhlcPoint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.assertSame

class StockChartMathTest {
    private val data = listOf(
        OhlcPoint("D1", 0f, 10f, 12f, 9f, 10f, 100f),
        OhlcPoint("D2", 1f, 10f, 14f, 10f, 12f, 200f),
        OhlcPoint("D3", 2f, 12f, 15f, 11f, 14f, 300f),
    )

    @Test
    fun stockChartView_retainsOneArgumentConstructionContract() {
        val constructor: ((() -> ObservableList<OhlcPoint>) -> StockChartView) = ::StockChartView
        assertNotNull(constructor)
    }

    @Test
    fun movingAverage_startsWhenWindowIsFull() {
        assertEquals(listOf(null, 11f, 13f), stockMovingAverage(data, 2))
    }

    @Test
    fun movingAverage_restartsWarmupAfterInvalidOhlcGap() {
        val gap = data[1].copy(open = Float.NaN, close = Float.NaN)
        val suffix = listOf(
            OhlcPoint("D4", 3f, 20f, 22f, 19f, 20f),
            OhlcPoint("D5", 4f, 20f, 24f, 20f, 24f),
            OhlcPoint("D6", 5f, 24f, 27f, 23f, 26f),
        )

        assertEquals(
            listOf(null, null, null, 17f, 22f, 25f),
            stockMovingAverage(listOf(data[0], gap, data[2]) + suffix, 2),
        )
    }

    @Test
    fun volumeBounds_ignoreMissingVolume() {
        assertEquals(0f..300f, stockVolumeBounds(data))
        assertEquals(0f..200f, stockVolumeBounds(data.mapIndexed { index, point ->
            if (index == 2) point.copy(volume = null) else point
        }))
    }

    @Test
    fun splitPlots_hideVolumeWhenDisabled() {
        val plot = PlotRect(40f, 8f, 300f, 220f)
        val split = splitStockPlots(plot, showVolume = false, volumeRatio = 0.24f)

        assertEquals(plot, split.price)
        assertNull(split.volume)
    }

    @Test
    fun changedStockData_resetsInspectionState() {
        val next = data.mapIndexed { index, point -> point.copy(label = "N$index") }

        assertFalse(stockDataChanged(data, data.toList()))
        assertTrue(stockDataChanged(data, next))
    }

    @Test
    fun splitPlots_reserveGapAndVolumeHeight() {
        val split = splitStockPlots(
            PlotRect(40f, 8f, 300f, 220f),
            showVolume = true,
            volumeRatio = 0.25f,
        )

        assertEquals(158f, split.price.bottom)
        assertEquals(170f, split.volume?.top)
        assertEquals(220f, split.volume?.bottom)
    }

    @Test
    fun averagePoints_keepOriginalXCoordinates() {
        val averages = stockMovingAverage(data, 2)

        assertEquals(listOf(1f to 11f, 2f to 13f), stockAveragePoints(data, averages))
    }

    @Test
    fun volumeMovingAverage_usesOnlyCompleteWindows() {
        assertEquals(listOf(null, 150f, 250f), stockVolumeMovingAverage(data, 2))
    }

    @Test
    fun volumePanel_requiresConfiguredVolumeData() {
        assertTrue(shouldShowVolumePanel(data, configured = true))
        assertFalse(shouldShowVolumePanel(data, configured = false))
        assertFalse(shouldShowVolumePanel(data.map { it.copy(volume = null) }, configured = true))
    }

    @Test
    fun compositePlots_reserveToolbarAndVisiblePanesInOrder() {
        val plots = splitCompositeStockPlots(
            plot = PlotRect(44f, 12f, 320f, 420f),
            firstVisible = true,
            secondVisible = true,
            firstRatio = 0.2f,
            secondRatio = 0.2f,
            toolbarHeight = 30f,
        )

        assertEquals(44f, plots.price.left)
        assertEquals(320f, plots.price.right)
        assertEquals(44f, plots.toolbar.left)
        assertEquals(44f, plots.first?.left)
        assertEquals(44f, plots.second?.left)
        assertEquals(320f, plots.toolbar.right)
        assertEquals(320f, plots.first?.right)
        assertEquals(320f, plots.second?.right)
        assertTrue(plots.price.bottom <= plots.toolbar.top)
        assertEquals(30f, plots.toolbar.height)
        assertEquals(8f, plots.first!!.top - plots.toolbar.bottom)
        assertEquals(8f, plots.second!!.top - plots.first!!.bottom)
        assertTrue(plots.second!!.bottom <= 420f)
    }

    @Test
    fun toolbarSlot_usesTheReservedStripBetweenPriceAndFirstPane() {
        val slot = stockToolbarSlotRect(
            width = 360f,
            height = 520f,
            firstVisible = true,
            secondVisible = true,
            firstRatio = 0.2f,
            secondRatio = 0.2f,
        )
        val plots = splitCompositeStockPlots(
            CartesianLayoutEngine.compute(360f, 520f).plot,
            true, true, 0.2f, 0.2f, 30f,
        )

        assertEquals(plots.toolbar, slot)
        assertTrue(plots.price.bottom <= slot.top)
        assertTrue(slot.bottom < plots.first!!.top)
    }

    @Test
    fun compositeToolbarReservation_dependsOnActualToolbarContent() {
        val plot = CartesianLayoutEngine.compute(360f, 520f).plot
        val withoutToolbar = splitCompositeStockPlots(
            plot, true, true, 0.2f, 0.2f, stockCompositeToolbarHeight(false),
        )
        val withToolbar = splitCompositeStockPlots(
            plot, true, true, 0.2f, 0.2f, stockCompositeToolbarHeight(true),
        )

        assertEquals(0f, withoutToolbar.toolbar.height)
        assertEquals(30f, withToolbar.toolbar.height)
        assertEquals(30f, withoutToolbar.price.height - withToolbar.price.height)
        assertTrue(withToolbar.price.bottom <= withToolbar.toolbar.top)
        assertTrue(withToolbar.toolbar.bottom < withToolbar.first!!.top)
    }

    @Test
    fun compositePlots_supportEachPaneCombinationAndReleaseCollapsedHeight() {
        val plot = PlotRect(40f, 8f, 300f, 408f)
        val both = splitCompositeStockPlots(plot, true, true, 0.2f, 0.2f, 30f)
        val first = splitCompositeStockPlots(plot, true, false, 0.2f, 0.2f, 30f)
        val second = splitCompositeStockPlots(plot, false, true, 0.2f, 0.2f, 30f)
        val none = splitCompositeStockPlots(plot, false, false, 0.2f, 0.2f, 30f)

        assertNotNull(first.first)
        assertNull(first.second)
        assertNull(second.first)
        assertNotNull(second.second)
        assertNull(none.first)
        assertNull(none.second)
        assertTrue(first.price.height > both.price.height)
        assertTrue(second.price.height > both.price.height)
        assertTrue(none.price.height > first.price.height)
        assertEquals(30f, none.toolbar.height)
    }

    @Test
    fun compositePlots_sanitizeTinyAndNonFiniteInputs() {
        val plots = splitCompositeStockPlots(
            plot = PlotRect(10f, 20f, 5f, 23f),
            firstVisible = true,
            secondVisible = true,
            firstRatio = Float.NaN,
            secondRatio = Float.POSITIVE_INFINITY,
            toolbarHeight = Float.NEGATIVE_INFINITY,
        )

        listOfNotNull(plots.price, plots.toolbar, plots.first, plots.second).forEach { pane ->
            assertTrue(pane.left.isFinite() && pane.top.isFinite())
            assertTrue(pane.right.isFinite() && pane.bottom.isFinite())
            assertTrue(pane.right >= pane.left)
            assertTrue(pane.bottom >= pane.top)
        }
        assertEquals(plots.price.left, plots.toolbar.left)
        assertEquals(plots.price.right, plots.toolbar.right)
        assertTrue(plots.second!!.bottom <= 23f)
    }

    @Test
    fun compositePlots_nonFiniteSizingUsesStableDefaults() {
        val plot = PlotRect(40f, 8f, 300f, 408f)
        val defaults = splitCompositeStockPlots(plot, true, true, 0.2f, 0.2f, 30f)
        val sanitized = splitCompositeStockPlots(
            plot,
            firstVisible = true,
            secondVisible = true,
            firstRatio = Float.NaN,
            secondRatio = Float.POSITIVE_INFINITY,
            toolbarHeight = Float.NaN,
        )

        assertEquals(defaults, sanitized)
    }

    @Test
    fun compositePlots_keepExtremeFiniteEndpointsOrderedAndShared() {
        val plots = listOf(
            splitCompositeStockPlots(
                PlotRect(0f, -Float.MAX_VALUE, 100f, Float.MAX_VALUE),
                true, true, 0.2f, 0.2f, 30f,
            ),
            splitCompositeStockPlots(
                PlotRect(-Float.MAX_VALUE, 0f, Float.MAX_VALUE, 100f),
                true, true, 0.2f, 0.2f, 30f,
            ),
            splitCompositeStockPlots(
                PlotRect(-Float.MIN_VALUE, -Float.MIN_VALUE, Float.MIN_VALUE, Float.MIN_VALUE),
                true, true, 0.2f, 0.2f, 30f,
            ),
        )

        plots.forEach { result ->
            val panes = listOfNotNull(result.price, result.toolbar, result.first, result.second)
            panes.forEach { pane ->
                assertTrue(listOf(pane.left, pane.top, pane.right, pane.bottom).all(Float::isFinite))
                assertTrue(pane.left <= pane.right)
                assertTrue(pane.top <= pane.bottom)
                assertEquals(result.price.left, pane.left)
                assertEquals(result.price.right, pane.right)
            }
        }
    }

    @Test
    fun visibleStockBounds_excludeOffscreenSpikesAndPadConstantOrMissingValues() {
        val points = data + OhlcPoint("spike", 20f, 1_000f, 2_000f, 900f, 1_500f, null)

        val price = visibleStockPriceBounds(points, 0f..2f)
        val constant = visibleStockValueBounds(data, listOf(7f, 7f, 7f), 0f..2f)
        val missing = visibleStockValueBounds(data, listOf(null, Float.NaN, null), 0f..2f)

        assertTrue(price.start <= 9f && price.endInclusive >= 15f)
        assertTrue(price.endInclusive < 100f)
        assertTrue(constant.start < 7f && constant.endInclusive > 7f)
        assertTrue(missing.start.isFinite() && missing.endInclusive.isFinite())
        assertTrue(missing.endInclusive > missing.start)
    }

    @Test
    fun visibleStockBounds_clampPaddingAtFloatExtremes() {
        val positive = visibleStockValueBounds(data, listOf(Float.MAX_VALUE, null, null), 0f..0f)
        val negative = visibleStockValueBounds(data, listOf(-Float.MAX_VALUE, null, null), 0f..0f)

        listOf(positive, negative).forEach { bounds ->
            assertTrue(bounds.start.isFinite())
            assertTrue(bounds.endInclusive.isFinite())
            assertTrue(bounds.start <= bounds.endInclusive)
        }
        assertEquals(Float.MAX_VALUE, positive.endInclusive)
        assertEquals(-Float.MAX_VALUE, negative.start)
    }

    @Test
    fun auxiliaryPaneResolution_isExhaustiveAndMissingBarsDegradeToEmpty() {
        val withoutVolume = data.map { it.copy(volume = null) }

        assertTrue(resolveAuxiliaryPaneData(data, StockAuxiliaryIndicator.NONE) is AuxiliaryPaneData.Empty)
        assertTrue(resolveAuxiliaryPaneData(withoutVolume, StockAuxiliaryIndicator.VOLUME) is AuxiliaryPaneData.Empty)
        assertTrue(resolveAuxiliaryPaneData(withoutVolume, StockAuxiliaryIndicator.AMOUNT) is AuxiliaryPaneData.Empty)
        assertTrue(resolveAuxiliaryPaneData(data, StockAuxiliaryIndicator.VOLUME) is AuxiliaryPaneData.Volume)
        assertTrue(resolveAuxiliaryPaneData(data, StockAuxiliaryIndicator.AMOUNT) is AuxiliaryPaneData.Amount)
        assertTrue(resolveAuxiliaryPaneData(data, StockAuxiliaryIndicator.MACD) is AuxiliaryPaneData.Macd)
        assertTrue(resolveAuxiliaryPaneData(data, StockAuxiliaryIndicator.KDJ) is AuxiliaryPaneData.Empty)
        assertTrue(resolveAuxiliaryPaneData(emptyList(), StockAuxiliaryIndicator.RSI) is AuxiliaryPaneData.Empty)
        assertTrue(resolveAuxiliaryPaneData(data, StockAuxiliaryIndicator.WR) is AuxiliaryPaneData.Empty)
    }

    @Test
    fun auxiliaryVolumeAndAmount_maskInvalidSourceIndices() {
        val invalid = data[1].copy(open = 100f, volume = 999f)
        val source = listOf(data[0], invalid, data[2])

        val volume = resolveAuxiliaryPaneData(source, StockAuxiliaryIndicator.VOLUME) as AuxiliaryPaneData.Volume
        val amount = resolveAuxiliaryPaneData(source, StockAuxiliaryIndicator.AMOUNT) as AuxiliaryPaneData.Amount

        assertEquals(listOf(100f, null, 300f), volume.values)
        assertNull(volume.average[1])
        assertEquals(listOf(1_000f, null, 4_200f), amount.values)
    }

    @Test
    fun auxiliaryVolume_isEmptyWhenFiniteVolumesBelongOnlyToInvalidOhlcPoints() {
        val source = data.map { point -> point.copy(open = point.high + 1f) }

        assertTrue(resolveAuxiliaryPaneData(source, StockAuxiliaryIndicator.VOLUME) is AuxiliaryPaneData.Empty)
    }

    @Test
    fun auxiliaryKdj_restartsSeedForEachValidOhlcRun() {
        val prefix = List(12) { index -> OhlcPoint("P$index", index.toFloat(), 9f, 10f, 0f, 9f) }
        val invalid = OhlcPoint("gap", 12f, 11f, 10f, 0f, 9f)
        val suffix = List(12) { index -> OhlcPoint("S$index", (index + 13).toFloat(), 1f, 10f, 0f, 1f) }
        val source = prefix + invalid + suffix

        val expectedSuffix = kdj(suffix)
        val actual = (resolveAuxiliaryPaneData(source, StockAuxiliaryIndicator.KDJ) as AuxiliaryPaneData.Kdj).result
        val carried = kdj(source)
        val suffixStart = prefix.size + 1
        val firstComputedSuffixIndex = suffixStart + 8

        assertNull(actual.k[prefix.size])
        assertEquals(expectedSuffix.k, actual.k.drop(suffixStart))
        assertEquals(expectedSuffix.d, actual.d.drop(suffixStart))
        assertEquals(expectedSuffix.j, actual.j.drop(suffixStart))
        assertFalse(actual.k[firstComputedSuffixIndex] == carried.k[firstComputedSuffixIndex])
    }

    @Test
    fun paneBounds_useVisibleValuesAndIncludeZeroForBarSemantics() {
        val source = data + OhlcPoint("offscreen", 20f, 10f, 10f, 10f, 10f, 100_000f)
        val volume = AuxiliaryPaneData.Volume(source.map { it.volume }, stockVolumeMovingAverage(source, 5))
        val macd = AuxiliaryPaneData.Macd(
            MacdResult(
                diff = listOf(2f, 3f, 4f, 50_000f),
                dea = listOf(1f, 2f, 3f, 50_000f),
                histogram = listOf(-2f, 1f, 2f, 50_000f),
            ),
        )

        val volumeBounds = auxiliaryPaneBounds(source, volume, 0f..2f)
        val macdBounds = auxiliaryPaneBounds(source, macd, 0f..2f)

        assertEquals(0f, volumeBounds.start)
        assertTrue(volumeBounds.endInclusive < 1_000f)
        assertTrue(macdBounds.start < -2f)
        assertTrue(macdBounds.endInclusive < 100f)
    }

    @Test
    fun volumeAndAmountBounds_keepExactZeroBaselineAndFiniteUpperPadding() {
        val zeroSource = data.map { it.copy(volume = 0f) }
        val zeroVolume = AuxiliaryPaneData.Volume(List(3) { 0f }, List(3) { null })
        val amount = AuxiliaryPaneData.Amount(listOf(1_000f, 2_000f, 4_200f))

        val volumeBounds = auxiliaryPaneBounds(zeroSource, zeroVolume, 0f..2f)
        val amountBounds = auxiliaryPaneBounds(data, amount, 0f..2f)

        assertEquals(0f, volumeBounds.start)
        assertTrue(volumeBounds.endInclusive.isFinite() && volumeBounds.endInclusive > 0f)
        assertEquals(0f, amountBounds.start)
        assertTrue(amountBounds.endInclusive > 4_200f)
    }

    @Test
    fun priceBounds_includeVisibleMainIndicatorButExcludeOffscreenValues() {
        val source = data + OhlcPoint("offscreen", 20f, 1_000f, 2_000f, 900f, 1_500f, null)
        val indicator = listOf(StockLineResult("test", 0L, listOf(8f, 16f, 17f, 20_000f)))
        val bounds = visibleCompositePriceBounds(source, indicator, 0f..2f)

        assertTrue(bounds.start < 8f)
        assertTrue(bounds.endInclusive > 17f)
        assertTrue(bounds.endInclusive < 100f)
    }

    @Test
    fun mainIndicatorPlanAndBounds_maskCachedValuesAtInvalidSourceIndices() {
        val invalid = data[1].copy(open = 100f)
        val source = listOf(data[0], invalid, data[2])
        val cached = StockLineResult("cached", 0L, listOf(10f, 10_000f, 14f))

        val planned = stockLineData(source, cached.values)
        assertEquals(listOf(10f, null, 14f), planned.values)
        val bounds = visibleCompositePriceBounds(source, listOf(cached), 0f..2f)
        assertTrue(bounds.endInclusive < 100f)
    }

    @Test
    fun modeAndIndicatorMapping_isExhaustiveAndKeepsSourceCoordinates() {
        assertEquals(StockPriceDisplayMode.CANDLE, resolvePriceRenderMode(StockPriceDisplayMode.CANDLE))
        assertEquals(StockPriceDisplayMode.LINE, resolvePriceRenderMode(StockPriceDisplayMode.LINE))
        assertTrue(resolveMainIndicatorLines(data, StockMainIndicator.BARE_K).isEmpty())
        listOf(
            StockMainIndicator.MA,
            StockMainIndicator.BOLL,
            StockMainIndicator.EXPMA,
            StockMainIndicator.BBI,
            StockMainIndicator.ENE,
        ).forEach { indicator ->
            assertTrue(resolveMainIndicatorLines(data, indicator).isNotEmpty())
            assertTrue(resolveMainIndicatorLines(data, indicator).all { it.values.size == data.size })
        }
    }

    @Test
    fun sharedAxisInput_containsCompleteLabelsAndExactViewport() {
        val theme = resolveStockTheme(ChartThemeOptions(), StockThemePreset.LIGHT)
        val viewport = ChartViewport(1f, 2f, 0f, 1f)
        val plot = PlotRect(40f, 10f, 300f, 200f)
        val input = stockAxisTickInput(
            data,
            viewport,
            plot,
            theme,
            measuredWidths = mapOf(0f to 20f, 1f to 21f, 2f to 22f),
            compactEllipsisWidth = 8f,
        )

        assertEquals(1f, input.visibleMin)
        assertEquals(2f, input.visibleMax)
        assertEquals(listOf("D1", "D2", "D3"), input.candidates.map { it.text })
        assertEquals(listOf(20f, 21f, 22f), input.candidates.map { it.measuredWidth })
        assertEquals("…", input.fallbackText)
        assertEquals(8f, input.fallbackMeasuredWidth)
    }

    @Test
    fun selectionMapping_onlyReturnsNearestVisibleCandleInsidePricePlot() {
        val plot = PlotRect(40f, 10f, 300f, 200f)
        val viewport = ChartViewport(1f, 2f, 8f, 16f)

        assertEquals(1, nearestVisibleStockIndex(data, viewport, plot, 42f, 100f, 24f))
        assertEquals(2, nearestVisibleStockIndex(data, viewport, plot, 298f, 100f, 24f))
        assertNull(nearestVisibleStockIndex(data, viewport, plot, 10f, 100f, 24f))
        assertNull(nearestVisibleStockIndex(data, viewport, plot, 100f, 220f, 24f))
        val invalid = data.toMutableList().apply { this[1] = this[1].copy(close = Float.NaN) }
        assertNull(nearestVisibleStockIndex(invalid, viewport, plot, 42f, 100f, 24f))
    }

    @Test
    fun compositeCrosshair_spansAllVisiblePanesButHorizontalStaysInPrice() {
        val plots = CompositeStockPlots(
            price = PlotRect(40f, 10f, 300f, 180f),
            toolbar = PlotRect(40f, 180f, 300f, 210f),
            first = PlotRect(40f, 218f, 300f, 300f),
            second = PlotRect(40f, 308f, 300f, 390f),
        )

        val plan = stockCompositeCrosshairPlan(plots, x = 120f, priceY = 80f)

        assertEquals(10f, plan?.verticalTop)
        assertEquals(390f, plan?.verticalBottom)
        assertEquals(40f, plan?.horizontalLeft)
        assertEquals(300f, plan?.horizontalRight)
        assertEquals(80f, plan?.horizontalY)

        val verticalOnly = stockCompositeCrosshairPlan(plots, x = 120f, priceY = null)
        assertNull(verticalOnly?.horizontalY)
        assertEquals(390f, verticalOnly?.verticalBottom)
    }

    @Test
    fun currentPrice_requiresLatestPointXToBeVisibleAndParticipatesInBounds() {
        assertEquals(14f, visibleCurrentPrice(data, 0f..2f))
        assertNull(visibleCurrentPrice(data, 0f..1.5f))

        val bounds = visibleCompositePriceBounds(
            points = data.dropLast(1),
            lines = emptyList(),
            visibleX = 0f..2f,
            currentPrice = 50f,
        )
        assertTrue(bounds.endInclusive > 50f)
    }

    @Test
    fun pricePaneSections_reserveHeaderContentAndMainOnlyXAxis() {
        val sections = splitStockPricePane(PlotRect(10f, 20f, 110f, 120f), 18f, 16f)
        assertEquals(20f, sections.header.top)
        assertEquals(38f, sections.header.bottom)
        assertEquals(sections.header.bottom, sections.content.top)
        assertEquals(sections.content.bottom, sections.xAxis.top)
        assertEquals(120f, sections.xAxis.bottom)
        assertEquals(16f, sections.xAxis.height)

        val auxiliary = splitStockPricePane(PlotRect(0f, 0f, 50f, 80f), 18f, 0f)
        assertEquals(auxiliary.content.bottom, auxiliary.xAxis.top)
        assertEquals(0f, auxiliary.xAxis.height)

        val tiny = splitStockPricePane(PlotRect(0f, 0f, 50f, 8f), 18f, 16f)
        assertEquals(tiny.header.bottom, tiny.content.top)
        assertEquals(tiny.content.bottom, tiny.xAxis.top)
        assertTrue(tiny.header.top <= tiny.header.bottom && tiny.content.top <= tiny.content.bottom && tiny.xAxis.top <= tiny.xAxis.bottom)

        val sanitized = splitStockPricePane(
            PlotRect(Float.NaN, Float.NaN, Float.POSITIVE_INFINITY, 40f),
            Float.NaN,
            Float.POSITIVE_INFINITY,
        )
        listOf(sanitized.header, sanitized.content, sanitized.xAxis).forEach { rect ->
            assertTrue(listOf(rect.left, rect.top, rect.right, rect.bottom).all(Float::isFinite))
            assertTrue(rect.left <= rect.right && rect.top <= rect.bottom)
        }
    }

    @Test
    fun displayIndex_prefersVisibleValidSelectionThenLatestVisibleValidPoint() {
        val points = listOf(
            OhlcPoint("outside", 0f, 10f, 12f, 9f, 11f),
            OhlcPoint("visible", 1f, 11f, 13f, 10f, 12f),
            OhlcPoint("invalid", 2f, 12f, 10f, 14f, 13f),
            OhlcPoint("latest-visible", 3f, 13f, 15f, 12f, 14f),
            OhlcPoint("after-viewport", 4f, 14f, 16f, 13f, 15f),
        )
        val viewport = ChartViewport(1f, 3f, 0f, 20f)

        assertEquals(1, stockDisplayIndex(points, viewport, selectedIndex = 1))
        assertEquals(3, stockDisplayIndex(points, viewport, selectedIndex = 0))
        assertEquals(3, stockDisplayIndex(points, viewport, selectedIndex = 2))
        assertEquals(3, stockDisplayIndex(points, viewport, selectedIndex = null))
        assertNull(stockDisplayIndex(points, ChartViewport(8f, 9f, 0f, 20f), selectedIndex = null))
    }

    @Test
    fun paneHeaderValues_reserveLeftSlotOnlyForCustomBuilder() {
        val header = PlotRect(10f, 20f, 210f, 38f)
        assertEquals(header, stockPaneHeaderValueRect(header, hasCustomBuilder = false))
        assertEquals(90f, stockPaneHeaderValueRect(header, hasCustomBuilder = true).left)
        assertEquals(header.right, stockPaneHeaderValueRect(header, hasCustomBuilder = true).right)
    }

    @Test
    fun overlayState_mountsAtZeroThenTracksLayoutAndCollapseWithoutStaleHitRects() {
        val config = StockRenderConfig(
            revision = 1,
            priceDisplayMode = StockPriceDisplayMode.CANDLE,
            mainIndicator = StockMainIndicator.MA,
            firstPane = StockPaneRenderConfig(true, 0.2f, StockAuxiliaryIndicator.VOLUME),
            secondPane = StockPaneRenderConfig(true, 0.2f, StockAuxiliaryIndicator.MACD),
            panesCollapsed = false,
            candleWidthRatio = 0.6f,
            preset = StockThemePreset.LIGHT,
        )
        val builders = StockOverlayBuilders(toolbar = true, mainHeader = true, firstHeader = true, secondHeader = true)

        val initial = resolveStockOverlayState(PlotRect(0f, 0f, 0f, 0f), config, builders)
        assertTrue(initial.toolbar.mounted)
        assertTrue(initial.mainHeader.mounted)
        assertFalse(initial.toolbar.visible)
        assertEquals(0f, initial.toolbar.rect.width)

        val expanded = resolveStockOverlayState(PlotRect(20f, 10f, 320f, 490f), config, builders)
        assertTrue(expanded.toolbar.visible)
        assertTrue(expanded.mainHeader.visible)
        assertTrue(expanded.firstHeader.visible)
        assertTrue(expanded.secondHeader.visible)
        assertTrue(expanded.toolbar.rect.width > 0f)

        val collapsed = resolveStockOverlayState(
            PlotRect(20f, 10f, 320f, 490f),
            config.copy(revision = 2, panesCollapsed = true),
            builders,
        )
        assertTrue(collapsed.toolbar.visible)
        assertTrue(collapsed.mainHeader.visible)
        assertFalse(collapsed.firstHeader.visible)
        assertFalse(collapsed.secondHeader.visible)
        assertEquals(0f, collapsed.firstHeader.rect.width)
        assertEquals(0f, collapsed.secondHeader.rect.height)

        val defaults = resolveStockOverlayState(
            PlotRect(20f, 10f, 320f, 490f),
            config,
            StockOverlayBuilders(),
        )
        assertFalse(defaults.toolbar.mounted)
        assertFalse(defaults.mainHeader.mounted)
    }

    @Test
    fun compositePriceDecision_rendersEmptyStateWithoutSyntheticChartForEmptyData() {
        assertEquals(CompositePriceRenderDecision.EMPTY, compositePriceRenderDecision(emptyList()))
        assertEquals(CompositePriceRenderDecision.DATA, compositePriceRenderDecision(data))
    }

    @Test
    fun stockMath_excludesMalformedOhlcPointsAcrossEveryPriceConsumer() {
        val valid = OhlcPoint("valid", 1f, 10f, 15f, 8f, 12f, 100f)
        val malformed = valid.copy(label = "malformed", x = 2f, high = 7f, low = 9f, close = 1_000f)
        val mixed = listOf(valid, malformed)
        val validOnlyCompositeBounds = visibleCompositePriceBounds(
            listOf(valid),
            listOf(StockLineResult("line", 0L, listOf(11f))),
            0f..3f,
        )

        assertEquals(CompositePriceRenderDecision.DATA, compositePriceRenderDecision(mixed))
        assertEquals(CompositePriceRenderDecision.EMPTY, compositePriceRenderDecision(listOf(malformed)))
        assertEquals(visibleStockPriceBounds(listOf(valid), 0f..3f), visibleStockPriceBounds(mixed, 0f..3f))
        assertEquals(0f..100f, stockVolumeBounds(mixed))
        assertEquals(
            visibleStockValueBounds(listOf(valid), listOf(11f), 0f..3f),
            visibleStockValueBounds(mixed, listOf(11f, 2_000f), 0f..3f),
        )
        assertEquals(
            auxiliaryPaneBounds(
                listOf(valid),
                AuxiliaryPaneData.Volume(listOf(100f), listOf(null)),
                0f..3f,
            ),
            auxiliaryPaneBounds(
                mixed,
                AuxiliaryPaneData.Volume(listOf(100f, 10_000f), listOf(null, 20_000f)),
                0f..3f,
            ),
        )
        assertEquals(
            validOnlyCompositeBounds,
            visibleCompositePriceBounds(
                mixed,
                listOf(StockLineResult("line", 0L, listOf(11f, 2_000f))),
                0f..3f,
            ),
        )
        assertNull(visibleCurrentPrice(mixed, 0f..3f))
        assertNull(
            nearestVisibleStockIndex(
                mixed,
                ChartViewport(0f, 3f, 0f, 20f),
                PlotRect(0f, 0f, 300f, 200f),
                x = 200f,
                y = 100f,
                hitRadius = 10f,
            ),
        )
    }

    @Test
    fun stockAxisFramePlan_sharesExactTickListAcrossEveryConsumer() {
        val ticks = emptyList<com.tencent.kuiklybase.chart.core.cartesian.PlannedAxisTick>()
        val frame = stockAxisFramePlan(ticks)

        assertSame(ticks, frame.gridTicks)
        assertSame(frame.gridTicks, frame.priceAxisTicks)
        assertSame(frame.priceAxisTicks, frame.volumeAxisTicks)
    }

    @Test
    fun stockComputationCache_reusesFamilyAndInvalidatesOnSnapshotChange() {
        val cache = StockComputationCache()
        var computations = 0
        fun resolve(source: List<OhlcPoint>) = cache.resolve(source, "MA:5") { ++computations }

        assertEquals(1, resolve(data))
        assertEquals(1, resolve(data.toList()))
        assertEquals(1, computations)
        assertEquals(2, resolve(data + data.last().copy(x = 3f)))
        assertEquals(2, computations)
    }
}
