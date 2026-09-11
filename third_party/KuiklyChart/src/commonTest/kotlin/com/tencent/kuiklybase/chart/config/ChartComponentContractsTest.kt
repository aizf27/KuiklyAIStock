package com.tencent.kuiklybase.chart.config

import com.tencent.kuiklybase.chart.model.ChartDataPoint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ChartComponentContractsTest {
    @Test
    fun stockChart_defaultsToHorizontalTimeSeriesInteraction() {
        val attr = StockChartAttr()

        assertEquals(StockThemePreset.LIGHT, attr.preset)
        assertEquals(StockPriceDisplayMode.CANDLE, attr.priceDisplayMode)
        assertEquals(StockMainIndicator.BARE_K, attr.mainIndicator)
        assertFalse(attr.firstPane.show)
        assertEquals(0.2f, attr.firstPane.heightRatio)
        assertEquals(StockAuxiliaryIndicator.NONE, attr.firstPane.indicator)
        assertFalse(attr.secondPane.show)
        assertEquals(0.2f, attr.secondPane.heightRatio)
        assertEquals(StockAuxiliaryIndicator.NONE, attr.secondPane.indicator)
        assertFalse(attr.panesCollapsed)
        assertTrue(attr.currentPriceLine.show)
        assertTrue(attr.currentPriceLine.showLabel)
        assertTrue(attr.currentPriceLine.dashLength > 0f)
        assertTrue(attr.currentPriceLine.dashGap > 0f)
        assertFalse(attr.movingAverages.show)
        assertTrue(attr.movingAverages.lines.isEmpty())
        assertFalse(attr.volumePanel.show)
        assertTrue(attr.volumePanel.averageLines.isEmpty())
        assertTrue(attr.interaction.enableLongPressInspect)
        assertTrue(attr.interaction.enablePan)
        assertTrue(attr.interaction.enableScale)
        assertTrue(attr.interaction.enableReset)
        assertTrue(attr.interaction.enableCrosshair)
        assertTrue(attr.interaction.lockY)
        assertTrue(attr.interaction.clampToData)
        assertEquals(0.55f, attr.interaction.initialVisibleRatio)
        assertEquals(VisibleAnchor.END, attr.interaction.initialVisibleAnchor)
    }

    @Test
    fun stockRenderConfig_advancesForRendererAndNestedPaneChanges() {
        val attr = StockChartAttr()
        val initial = attr.renderConfig

        attr.mainIndicator = StockMainIndicator.BOLL
        val mainChanged = attr.renderConfig
        assertTrue(mainChanged.revision > initial.revision)
        assertEquals(StockMainIndicator.BOLL, mainChanged.mainIndicator)

        attr.priceDisplayMode = StockPriceDisplayMode.LINE
        attr.panesCollapsed = true
        attr.firstPane.show = true
        attr.firstPane.heightRatio = 0.26f
        attr.firstPane.indicator = StockAuxiliaryIndicator.AMOUNT
        attr.secondPane.show = true
        attr.secondPane.heightRatio = 0.24f
        attr.secondPane.indicator = StockAuxiliaryIndicator.RSI

        val changed = attr.renderConfig
        assertTrue(changed.revision > mainChanged.revision)
        assertEquals(StockPriceDisplayMode.LINE, changed.priceDisplayMode)
        assertTrue(changed.panesCollapsed)
        assertEquals(StockPaneRenderConfig(true, 0.26f, StockAuxiliaryIndicator.AMOUNT), changed.firstPane)
        assertEquals(StockPaneRenderConfig(true, 0.24f, StockAuxiliaryIndicator.RSI), changed.secondPane)

        val stableRevision = changed.revision
        attr.secondPane.heightRatio = 0.24f
        assertEquals(stableRevision, attr.renderConfig.revision)
    }

    @Test
    fun stockPaneSlots_exposeExactIndicatorWhitelists() {
        assertEquals(
            setOf(StockAuxiliaryIndicator.VOLUME, StockAuxiliaryIndicator.MACD, StockAuxiliaryIndicator.AMOUNT),
            StockPaneSlot.FIRST.allowedIndicators(),
        )
        assertEquals(
            setOf(
                StockAuxiliaryIndicator.MACD,
                StockAuxiliaryIndicator.KDJ,
                StockAuxiliaryIndicator.RSI,
                StockAuxiliaryIndicator.WR,
                StockAuxiliaryIndicator.BBD,
            ),
            StockPaneSlot.SECOND.allowedIndicators(),
        )
    }

    @Test
    fun stockPane_invalidIndicatorAssignmentSilentlyDegradesToNone() {
        val first = StockAuxiliaryPaneConfig(StockPaneSlot.FIRST)
        val second = StockAuxiliaryPaneConfig(StockPaneSlot.SECOND)

        first.indicator = StockAuxiliaryIndicator.RSI
        second.indicator = StockAuxiliaryIndicator.VOLUME

        assertEquals(StockAuxiliaryIndicator.NONE, first.indicator)
        assertEquals(StockAuxiliaryIndicator.NONE, second.indicator)
    }

    @Test
    fun stockPane_acceptsNoneAndAllowedIndicators() {
        val first = StockAuxiliaryPaneConfig(StockPaneSlot.FIRST)
        val second = StockAuxiliaryPaneConfig(StockPaneSlot.SECOND)

        first.indicator = StockAuxiliaryIndicator.AMOUNT
        second.indicator = StockAuxiliaryIndicator.KDJ
        assertEquals(StockAuxiliaryIndicator.AMOUNT, first.indicator)
        assertEquals(StockAuxiliaryIndicator.KDJ, second.indicator)

        first.indicator = StockAuxiliaryIndicator.NONE
        second.indicator = StockAuxiliaryIndicator.NONE
        assertEquals(StockAuxiliaryIndicator.NONE, first.indicator)
        assertEquals(StockAuxiliaryIndicator.NONE, second.indicator)
    }

    @Test
    fun stockFloatConfigs_sanitizeNonFiniteAndOutOfRangeAssignments() {
        val pane = StockAuxiliaryPaneConfig(StockPaneSlot.FIRST)
        val currentPrice = StockCurrentPriceLineConfig()

        pane.heightRatio = Float.NaN
        assertEquals(0.2f, pane.heightRatio)
        pane.heightRatio = Float.POSITIVE_INFINITY
        assertEquals(0.2f, pane.heightRatio)
        pane.heightRatio = -1f
        assertEquals(0.2f, pane.heightRatio)
        pane.heightRatio = 4f
        assertEquals(0.45f, pane.heightRatio)

        currentPrice.lineWidth = Float.NaN
        currentPrice.dashLength = Float.NEGATIVE_INFINITY
        currentPrice.dashGap = -1f
        assertEquals(1f, currentPrice.lineWidth)
        assertEquals(4f, currentPrice.dashLength)
        assertEquals(3f, currentPrice.dashGap)
    }

    @Test
    fun legacyStockRatios_sanitizeNonFiniteValuesAndClampToRendererRanges() {
        val attr = StockChartAttr()

        listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY).forEach { invalid ->
            attr.volumePanel.heightRatio = invalid
            attr.candleWidthRatio = invalid
            assertEquals(0.24f, attr.volumePanel.heightRatio)
            assertEquals(0.6f, attr.candleWidthRatio)
        }

        attr.volumePanel.heightRatio = -1f
        attr.candleWidthRatio = -1f
        assertEquals(0.16f, attr.volumePanel.heightRatio)
        assertEquals(0.1f, attr.candleWidthRatio)

        attr.volumePanel.heightRatio = 2f
        attr.candleWidthRatio = 2f
        assertEquals(0.4f, attr.volumePanel.heightRatio)
        assertEquals(1f, attr.candleWidthRatio)

        attr.volumePanel.heightRatio = 0.26f
        attr.candleWidthRatio = 0.72f
        assertEquals(0.26f, attr.volumePanel.heightRatio)
        assertEquals(0.72f, attr.candleWidthRatio)
    }

    @Test
    fun stockAverageLine_rejectsInvalidDirectConstruction() {
        assertFailsWith<IllegalArgumentException> { StockAverageLine(0, 0xFF000000) }
        assertFailsWith<IllegalArgumentException> { StockAverageLine(-1, 0xFF000000) }
    }

    @Test
    fun stockChart_legacyIndicatorDslRemainsCompatibleWithTypedDefaults() {
        val attr = StockChartAttr().apply {
            movingAverages {
                show = true
                line(5, 0xFF262626)
                line(10, 0xFFFAAD14, "MA10")
            }
            volumePanel {
                show = true
                heightRatio = 0.26f
                average(5, 0xFF595959)
            }
        }

        assertEquals(StockMainIndicator.BARE_K, attr.mainIndicator)
        assertTrue(attr.movingAverages.show)
        assertEquals(listOf(5, 10), attr.movingAverages.lines.map { it.period })
        assertEquals("MA10", attr.movingAverages.lines.last().label)
        assertTrue(attr.volumePanel.show)
        assertEquals(0.26f, attr.volumePanel.heightRatio)
        assertEquals(5, attr.volumePanel.averageLines.single().period)
        assertFalse(attr.firstPane.show)
        assertFalse(attr.secondPane.show)
    }

    @Test
    fun stockChart_indicatorDslReplacesRepeatedPeriods() {
        val attr = StockChartAttr().apply {
            movingAverages {
                line(5, 0xFF111111)
                line(5, 0xFF222222, "MA5 updated")
            }
            volumePanel {
                average(10, 0xFF333333)
                average(10, 0xFF444444, "VMA10 updated")
            }
        }

        assertEquals(1, attr.movingAverages.lines.size)
        assertEquals(0xFF222222, attr.movingAverages.lines.single().color)
        assertEquals("MA5 updated", attr.movingAverages.lines.single().label)
        assertEquals(1, attr.volumePanel.averageLines.size)
        assertEquals(0xFF444444, attr.volumePanel.averageLines.single().color)
        assertEquals("VMA10 updated", attr.volumePanel.averageLines.single().label)
    }

    @Test
    fun darkStockPreset_resolvesBrokerPaletteAndCustomOverrides() {
        val options = ChartThemeOptions()
        val dark = resolveStockTheme(options, StockThemePreset.DARK)
        assertEquals(0xFF101620, dark.backgroundColor)
        assertEquals(0xFFE15A5A, dark.upColor)
        assertEquals(0xFF28AD78, dark.downColor)

        options.primaryColor = 0xFF8B5CF6
        assertEquals(0xFF8B5CF6, resolveStockTheme(options, StockThemePreset.DARK).primaryColor)
    }

    @Test
    fun lineChart_defaultsToStraightSegmentsWithBasicChromeAndInteraction() {
        val attr = LineChartAttr()

        assertFalse(attr.smooth)
        assertTrue(attr.showPoints)
        assertTrue(attr.xAxis.show)
        assertTrue(attr.yAxis.show)
        assertTrue(attr.grid.show)
        assertTrue(attr.interaction.enableTap)
        assertFalse(attr.interaction.enablePan)
        assertFalse(attr.interaction.enableScale)
        assertFalse(attr.interaction.enableReset)
        assertFalse(attr.interaction.enableCrosshair)
        // 增强能力默认值：连接缺失值开启，下方填充关闭，无阈值与注释
        assertTrue(attr.connectNulls)
        assertFalse(attr.fillBelow)
        assertTrue(attr.thresholds.isEmpty())
        assertTrue(attr.annotations.isEmpty())
    }

    @Test
    fun lineChart_thresholdAndAnnotationDslAcceptsEntries() {
        val attr = LineChartAttr().apply {
            thresholds {
                add(ChartThresholdConfig(value = 55f, label = "警戒 55°"))
            }
            annotations {
                add(ChartAnnotationConfig(text = "峰值", dataX = 5f, dataY = 96f))
            }
        }

        assertEquals(1, attr.thresholds.size)
        assertEquals("警戒 55°", attr.thresholds[0].label)
        assertEquals(1, attr.annotations.size)
        assertEquals("峰值", attr.annotations[0].text)
    }

    @Test
    fun lineChart_tooltipDslSupportsSharedXAndCustomFormatter() {
        val attr = LineChartAttr().apply {
            tooltip {
                sharedByX = true
                formatter { context -> "${context.label}:${context.items.size}" }
            }
        }
        val context = ChartTooltipContext(
            label = "3月",
            x = 3f,
            items = listOf(
                ChartTooltipItem("华东", ChartDataPoint("3月", 3f, 151f), 0, 2),
                ChartTooltipItem("华南", ChartDataPoint("3月", 3f, 138f), 1, 2),
            ),
        )

        assertTrue(attr.tooltip.sharedByX)
        assertEquals("3月:2", attr.tooltip.format(context))
    }

    @Test
    fun cartesianEvent_exposesOnlyCartesianCallbacks() {
        val event = CartesianChartEvent()
        assertNull(event.onPointClick)
        assertNull(event.onViewportChange)
        assertNull(event.onSelectionChange)
        assertNull(event.onDragSelect)
        // Compile-time contract: CartesianChartEvent has no slice/radar fields.
        // Runtime smoke: registration helpers exist and assign handlers.
        event.pointClick { _, _, _ -> }
        event.dragSelect { }
        event.viewportChange { }
        event.selectionChange { }
        assertTrue(event.onPointClick != null)
        assertTrue(event.onDragSelect != null)
        assertTrue(event.onViewportChange != null)
        assertTrue(event.onSelectionChange != null)
    }

    @Test
    fun pieAndRadarEvents_areSplitByChartType() {
        val pie = PieChartEvent()
        pie.sliceClick { _, _ -> }
        assertTrue(pie.onSliceClick != null)

        val radar = RadarChartEvent()
        radar.radarClick { _, _, _ -> }
        assertTrue(radar.onRadarClick != null)
    }

    @Test
    fun themeOptions_resolved_returnsImmutableSnapshot() {
        val options = ChartThemeOptions().apply {
            primaryColor = 0xFF6C5CE7
            fontSize = 14f
        }
        val snapshot = options.resolved()
        assertEquals(0xFF6C5CE7, snapshot.primaryColor)
        assertEquals(14f, snapshot.fontSize)
        options.primaryColor = 0xFF000000
        assertEquals(0xFF6C5CE7, snapshot.primaryColor)
    }
}
