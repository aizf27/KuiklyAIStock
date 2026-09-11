package com.tencent.kuiklybase.chart.core.cartesian

import com.tencent.kuikly.core.base.event.Touch
import com.tencent.kuikly.core.base.event.TouchParams
import com.tencent.kuiklybase.chart.core.planCartesianXAxisTicks
import com.tencent.kuiklybase.chart.core.AxisTick
import com.tencent.kuiklybase.chart.core.generateNumericXAxisTicks
import com.tencent.kuiklybase.chart.core.AxisLabelWidthCache
import com.tencent.kuiklybase.chart.model.ChartSeries
import com.tencent.kuiklybase.chart.model.ChartViewport
import com.tencent.kuiklybase.chart.model.ChartDataPoint
import com.tencent.kuiklybase.chart.config.ChartInteractionConfig
import com.tencent.kuiklybase.chart.config.CartesianChartAttr
import com.tencent.kuiklybase.chart.config.ChartViewportCommand
import com.tencent.kuiklybase.chart.config.ChartViewportRequest
import com.tencent.kuiklybase.chart.config.VisibleAnchor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CartesianEngineTest {
    @Test
    fun layoutEngine_computesPlotWithinBounds() {
        val layout = CartesianLayoutEngine.compute(400f, 300f)
        assertTrue(layout.plot.width > 0f)
        assertTrue(layout.plot.height > 0f)
        assertTrue(layout.plot.left >= 0f)
        assertTrue(layout.plot.top >= 0f)
        assertTrue(layout.plot.right <= 400f)
        assertTrue(layout.plot.bottom <= 300f)
    }

    @Test
    fun scale_mapsDataToPixel() {
        val plot = PlotRect(40f, 36f, 380f, 240f)
        val viewport = ChartViewport(0f, 10f, 0f, 100f)
        val scale = CartesianScale(plot, viewport)
        assertEquals(40f, scale.toPixelX(0f))
        assertEquals(380f, scale.toPixelX(10f))
        assertEquals(240f, scale.toPixelY(0f))
        assertEquals(36f, scale.toPixelY(100f))
    }

    @Test
    fun viewport_fromSeries_addsPadding() {
        val series = listOf(
            ChartSeries(
                name = "A",
                color = 0xFF000000,
                points = listOf(
                    com.tencent.kuiklybase.chart.model.ChartDataPoint("a", 0f, 10f),
                    com.tencent.kuiklybase.chart.model.ChartDataPoint("b", 5f, 50f),
                ),
            ),
        )
        val vp = ChartViewport.fromSeries(series)
        assertTrue(vp.xMin < 0f)
        assertTrue(vp.xMax > 5f)
        assertTrue(vp.yMax > 50f)
    }

    @Test
    fun viewport_handlesSinglePointAndAllNegativeValues() {
        val single = ChartSeries("single", listOf(ChartDataPoint("", 4f, -8f)), 0L)
        val viewport = ChartViewport.fromSeries(listOf(single))
        assertTrue(viewport.xMin < 4f && viewport.xMax > 4f)
        assertTrue(viewport.yMin < -8f && viewport.yMax > -8f)

        val negative = ChartSeries(
            "negative",
            listOf(ChartDataPoint("", -5f, -10f), ChartDataPoint("", -2f, -3f)),
            0L,
        )
        val negativeViewport = ChartViewport.fromSeries(listOf(negative))
        assertTrue(negativeViewport.xMax < 0f)
        assertTrue(negativeViewport.yMax >= 0f)
    }

    @Test
    fun viewport_ignoresNonFinitePoints() {
        val series = ChartSeries(
            "invalid",
            listOf(ChartDataPoint("", Float.NaN, 1f), ChartDataPoint("", 2f, 3f)),
            0L,
        )
        val viewport = ChartViewport.fromSeries(listOf(series))
        assertTrue(viewport.xMin < 2f && viewport.xMax > 2f)
    }

    @Test
    fun dataChange_updatesOnlyAutomaticViewport() {
        val current = ChartViewport(0f, 10f, 0f, 10f)
        val updated = ChartViewport(10f, 20f, 10f, 20f)
        assertEquals(updated, resolveViewportAfterDataChange(current, updated, false))
        assertEquals(current, resolveViewportAfterDataChange(current, updated, true))
    }

    @Test
    fun pinchUsesCumulativeScaleFromGestureStart() {
        val interaction = ChartInteractionConfig().apply { enableScale = true }
        val changes = mutableListOf<ChartViewport>()
        val controller = ChartGestureController(
            interaction,
            ChartViewport(0f, 10f, 0f, 10f),
            ChartViewport(0f, 10f, 0f, 10f),
            changes::add,
        )
        val plot = PlotRect(0f, 0f, 100f, 100f)
        controller.onPinchStart()
        controller.onPinch(2f, 50f, 50f, CartesianScale(plot, controller.currentViewport()))
        controller.onPinch(4f, 50f, 50f, CartesianScale(plot, controller.currentViewport()))
        assertEquals(2.5f, controller.currentViewport().xMax - controller.currentViewport().xMin)
    }

    @Test
    fun clampedUserViewport_staysInsideChangedDataBounds() {
        val current = ChartViewport(80f, 100f, 0f, 10f)
        val newBounds = ChartViewport(0f, 50f, -5f, 15f)
        val home = ChartViewport(22.5f, 50f, -5f, 15f)

        assertEquals(
            ChartViewport(30f, 50f, 0f, 10f),
            resolveViewportAfterDataChange(current, home, true, newBounds, clampToData = true, lockY = true),
        )
    }

    @Test
    fun stockStyleClamping_preventsRepeatedCommandsFromLeavingData() {
        val controller = ChartGestureController(
            ChartInteractionConfig().apply {
                enablePan = true
                clampToData = true
                lockY = true
            },
            ChartViewport(45f, 100f, 0f, 10f),
            ChartViewport(0f, 100f, 0f, 10f),
            {},
        )

        repeat(20) { controller.panByRatio(0.2f) }
        assertEquals(45f, controller.currentViewport().xMin)
        assertEquals(100f, controller.currentViewport().xMax)
        repeat(20) { controller.panByRatio(-0.2f) }
        assertEquals(0f, controller.currentViewport().xMin)
        assertEquals(55f, controller.currentViewport().xMax)
    }

    @Test
    fun viewportCommands_defaultToNoOpRequest() {
        assertEquals(ChartViewportRequest(), CartesianChartAttr().viewportRequest)
    }

    @Test
    fun zoomBy_centersZoomAndKeepsYWhenLocked() {
        val controller = ChartGestureController(
            ChartInteractionConfig().apply {
                enableScale = true
                lockY = true
                clampToData = false
            },
            ChartViewport(2f, 8f, 10f, 30f),
            ChartViewport(0f, 10f, 0f, 100f),
        ) {}

        controller.zoomBy(1.25f)
        assertEquals(2.6f, controller.currentViewport().xMin, 0.0001f)
        assertEquals(7.4f, controller.currentViewport().xMax, 0.0001f)
        assertEquals(10f, controller.currentViewport().yMin)
        assertEquals(30f, controller.currentViewport().yMax)

        controller.zoomBy(0.8f)
        assertEquals(2f, controller.currentViewport().xMin, 0.0001f)
        assertEquals(8f, controller.currentViewport().xMax, 0.0001f)
    }

    @Test
    fun zoomBy_clampsFactorAndStopsAtExistingMinimumSpan() {
        val controller = ChartGestureController(
            ChartInteractionConfig().apply {
                enableScale = true
                lockY = true
                clampToData = false
            },
            ChartViewport(0f, 10f, 0f, 10f),
            ChartViewport(0f, 10f, 0f, 10f),
        ) {}

        repeat(8) { controller.zoomBy(100f) }
        assertEquals(0.2f, controller.currentViewport().xMax - controller.currentViewport().xMin, 0.0001f)
    }

    @Test
    fun zoomBy_clampsFactorsBelowHalfToHalf() {
        val controller = ChartGestureController(
            ChartInteractionConfig().apply {
                enableScale = true
                lockY = true
                clampToData = false
            },
            ChartViewport(4f, 6f, 0f, 10f),
            ChartViewport(0f, 10f, 0f, 10f),
        ) {}

        controller.zoomBy(0.1f)
        assertEquals(3f, controller.currentViewport().xMin)
        assertEquals(7f, controller.currentViewport().xMax)
    }

    @Test
    fun panByRatio_movesInRequestedDirectionAndClampsToBounds() {
        val controller = ChartGestureController(
            ChartInteractionConfig().apply {
                enablePan = true
                lockY = true
                clampToData = true
            },
            ChartViewport(3f, 7f, 10f, 30f),
            ChartViewport(0f, 10f, 0f, 100f),
        ) {}

        controller.panByRatio(0.5f)
        assertEquals(5f, controller.currentViewport().xMin)
        assertEquals(9f, controller.currentViewport().xMax)

        controller.panByRatio(-1f)
        assertEquals(1f, controller.currentViewport().xMin)
        assertEquals(5f, controller.currentViewport().xMax)

        controller.panByRatio(-5f)
        assertEquals(0f, controller.currentViewport().xMin)
        assertEquals(4f, controller.currentViewport().xMax)
        assertEquals(10f, controller.currentViewport().yMin)
        assertEquals(30f, controller.currentViewport().yMax)
    }

    @Test
    fun panByRatio_clampsRatiosAboveOneToOne() {
        val controller = ChartGestureController(
            ChartInteractionConfig().apply {
                enablePan = true
                clampToData = false
            },
            ChartViewport(2f, 4f, 0f, 10f),
            ChartViewport(0f, 10f, 0f, 10f),
        ) {}

        controller.panByRatio(5f)
        assertEquals(4f, controller.currentViewport().xMin)
        assertEquals(6f, controller.currentViewport().xMax)
    }

    @Test
    fun clampedNoOpViewportChangesDoNotEmitCallbacks() {
        val panChanges = mutableListOf<ChartViewport>()
        val boundaryPan = ChartGestureController(
            ChartInteractionConfig().apply {
                enablePan = true
                clampToData = true
            },
            ChartViewport(8f, 10f, 0f, 10f),
            ChartViewport(0f, 10f, 0f, 10f),
            panChanges::add,
        )
        boundaryPan.panByRatio(1f)
        assertTrue(panChanges.isEmpty())

        val zoomChanges = mutableListOf<ChartViewport>()
        val fullRangeZoom = ChartGestureController(
            ChartInteractionConfig().apply {
                enableScale = true
                lockY = true
                clampToData = true
            },
            ChartViewport(0f, 10f, 0f, 10f),
            ChartViewport(0f, 10f, 0f, 10f),
            zoomChanges::add,
        )
        fullRangeZoom.zoomBy(0.5f)
        assertTrue(zoomChanges.isEmpty())
    }

    @Test
    fun actualViewportChangeEmitsExactlyOneCallback() {
        val changes = mutableListOf<ChartViewport>()
        val controller = ChartGestureController(
            ChartInteractionConfig().apply {
                enablePan = true
                clampToData = true
            },
            ChartViewport(2f, 6f, 0f, 10f),
            ChartViewport(0f, 10f, 0f, 10f),
            changes::add,
        )

        controller.panByRatio(0.25f)
        assertEquals(1, changes.size)
        assertEquals(controller.currentViewport(), changes.single())
    }

    @Test
    fun clampedNoOpLeavesAutomaticViewportEligibleForDataSync() {
        var hasUserViewportOverride = false
        val current = ChartViewport(8f, 10f, 0f, 10f)
        val controller = ChartGestureController(
            ChartInteractionConfig().apply {
                enablePan = true
                clampToData = true
            },
            current,
            ChartViewport(0f, 10f, 0f, 10f),
        ) { hasUserViewportOverride = true }

        controller.panByRatio(1f)
        val newHome = ChartViewport(20f, 30f, 0f, 10f)
        assertEquals(
            newHome,
            resolveViewportAfterDataChange(controller.currentViewport(), newHome, hasUserViewportOverride),
        )
    }

    @Test
    fun viewportMethods_areNoOpWhenTheirInteractionsAreDisabled() {
        val initial = ChartViewport(2f, 8f, 10f, 30f)
        val changes = mutableListOf<ChartViewport>()
        val controller = ChartGestureController(
            ChartInteractionConfig().apply {
                enableScale = false
                enablePan = false
                clampToData = false
            },
            initial,
            ChartViewport(0f, 10f, 0f, 100f),
            changes::add,
        )

        controller.zoomBy(2f)
        controller.panByRatio(1f)
        assertEquals(initial, controller.currentViewport())
        assertTrue(changes.isEmpty())
    }

    @Test
    fun viewportMethods_ignoreNonFiniteInputs() {
        val initial = ChartViewport(2f, 8f, 10f, 30f)
        val changes = mutableListOf<ChartViewport>()
        val controller = ChartGestureController(
            ChartInteractionConfig().apply {
                enableScale = true
                enablePan = true
                lockY = true
                clampToData = false
            },
            initial,
            ChartViewport(0f, 10f, 0f, 100f),
            changes::add,
        )

        controller.zoomBy(Float.NaN)
        controller.panByRatio(Float.NaN)
        controller.zoomBy(Float.POSITIVE_INFINITY)
        controller.zoomBy(Float.NEGATIVE_INFINITY)
        controller.panByRatio(Float.POSITIVE_INFINITY)
        controller.panByRatio(Float.NEGATIVE_INFINITY)
        assertEquals(initial, controller.currentViewport())
        assertTrue(changes.isEmpty())
    }

    @Test
    fun viewportCommandDispatch_acceptsFirstAndNewSequencesOnly() {
        val first = resolveViewportCommandDispatch(
            handledSequence = null,
            request = ChartViewportRequest(7, ChartViewportCommand.ZOOM_IN),
        )
        assertEquals(7, first.handledSequence)
        assertEquals(ChartViewportCommand.ZOOM_IN, first.command)

        val repeated = resolveViewportCommandDispatch(first.handledSequence, ChartViewportRequest(7, ChartViewportCommand.ZOOM_IN))
        assertEquals(null, repeated.command)

        val next = resolveViewportCommandDispatch(repeated.handledSequence, ChartViewportRequest(8, ChartViewportCommand.ZOOM_IN))
        assertEquals(ChartViewportCommand.ZOOM_IN, next.command)
    }

    @Test
    fun viewportCommandDispatch_neverLowersSequenceWatermark() {
        val stale = resolveViewportCommandDispatch(8, ChartViewportRequest(7, ChartViewportCommand.PAN_LEFT))
        assertEquals(8, stale.handledSequence)
        assertEquals(null, stale.command)

        val repeated = resolveViewportCommandDispatch(stale.handledSequence, ChartViewportRequest(8, ChartViewportCommand.PAN_RIGHT))
        assertEquals(8, repeated.handledSequence)
        assertEquals(null, repeated.command)

        val newer = resolveViewportCommandDispatch(repeated.handledSequence, ChartViewportRequest(9, ChartViewportCommand.PAN_RIGHT))
        assertEquals(9, newer.handledSequence)
        assertEquals(ChartViewportCommand.PAN_RIGHT, newer.command)
    }

    @Test
    fun viewportCommandDispatch_preservesResetAndTreatsNoneAsNoOp() {
        val reset = resolveViewportCommandDispatch(null, ChartViewportRequest(1, ChartViewportCommand.RESET))
        assertEquals(ChartViewportCommand.RESET, reset.command)

        val none = resolveViewportCommandDispatch(reset.handledSequence, ChartViewportRequest(2, ChartViewportCommand.NONE))
        assertEquals(2, none.handledSequence)
        assertEquals(null, none.command)
    }

    @Test
    fun viewportCommandExecutor_mapsEveryCommand() {
        val zoomFactors = mutableListOf<Float>()
        val panRatios = mutableListOf<Float>()
        var resets = 0
        val executor = ViewportCommandExecutor(
            zoomBy = zoomFactors::add,
            panByRatio = panRatios::add,
            reset = { resets += 1 },
        )

        ChartViewportCommand.entries.forEach(executor::execute)

        assertEquals(listOf(1.25f, 0.8f), zoomFactors)
        assertEquals(listOf(-0.2f, 0.2f), panRatios)
        assertEquals(1, resets)
    }

    @Test
    fun viewportCommandQueue_schedulesOneDrainAcrossRepeatedRedraws() {
        val scheduled = mutableListOf<() -> Unit>()
        val executed = mutableListOf<ChartViewportCommand>()
        val queue = ViewportCommandQueue(
            scheduleDrain = scheduled::add,
            execute = executed::add,
        )
        val request = ChartViewportRequest(1, ChartViewportCommand.ZOOM_IN)

        repeat(3) { queue.offer(request) }
        assertEquals(1, scheduled.size)
        assertTrue(executed.isEmpty())

        scheduled.removeAt(0).invoke()
        assertEquals(listOf(ChartViewportCommand.ZOOM_IN), executed)
        queue.offer(request)
        assertTrue(scheduled.isEmpty())
    }

    @Test
    fun viewportCommandQueue_noneAdvancesSequenceWithoutScheduling() {
        val scheduled = mutableListOf<() -> Unit>()
        val executed = mutableListOf<ChartViewportCommand>()
        val queue = ViewportCommandQueue(scheduled::add, executed::add)

        queue.offer(ChartViewportRequest())
        assertTrue(scheduled.isEmpty())
        assertTrue(executed.isEmpty())

        queue.offer(ChartViewportRequest(1, ChartViewportCommand.RESET))
        assertEquals(1, scheduled.size)
        scheduled.removeAt(0).invoke()
        assertEquals(listOf(ChartViewportCommand.RESET), executed)
    }

    @Test
    fun viewportCommandQueue_staleNoneDoesNotDisturbPendingCommand() {
        val scheduled = mutableListOf<() -> Unit>()
        val executed = mutableListOf<ChartViewportCommand>()
        val queue = ViewportCommandQueue(scheduled::add, executed::add)

        queue.offer(ChartViewportRequest(5, ChartViewportCommand.RESET))
        queue.offer(ChartViewportRequest(4, ChartViewportCommand.NONE))
        queue.offer(ChartViewportRequest(5, ChartViewportCommand.NONE))
        assertEquals(1, scheduled.size)

        scheduled.removeAt(0).invoke()
        assertEquals(listOf(ChartViewportCommand.RESET), executed)
    }

    @Test
    fun viewportCommandQueue_closeCancelsPendingDrainPermanently() {
        val scheduled = mutableListOf<() -> Unit>()
        val executed = mutableListOf<ChartViewportCommand>()
        val queue = ViewportCommandQueue(scheduled::add, executed::add)

        queue.offer(ChartViewportRequest(1, ChartViewportCommand.ZOOM_IN))
        val capturedDrain = scheduled.single()
        queue.close()
        capturedDrain()
        queue.offer(ChartViewportRequest(2, ChartViewportCommand.PAN_RIGHT))

        assertTrue(executed.isEmpty())
        assertEquals(1, scheduled.size)
    }

    @Test
    fun viewportCommandQueue_preservesConsumedSequenceAcrossReload() {
        val scheduled = mutableListOf<() -> Unit>()
        val executed = mutableListOf<ChartViewportCommand>()
        var handled: Int? = null
        var queue = ViewportCommandQueue(scheduled::add, executed::add, handled) { handled = it }

        queue.offer(ChartViewportRequest(7, ChartViewportCommand.PAN_LEFT))
        scheduled.removeAt(0).invoke()
        queue.close()
        queue = ViewportCommandQueue(scheduled::add, executed::add, handled) { handled = it }
        queue.offer(ChartViewportRequest(7, ChartViewportCommand.PAN_LEFT))
        assertTrue(scheduled.isEmpty())

        queue.offer(ChartViewportRequest(8, ChartViewportCommand.PAN_RIGHT))
        scheduled.removeAt(0).invoke()
        assertEquals(listOf(ChartViewportCommand.PAN_LEFT, ChartViewportCommand.PAN_RIGHT), executed)
    }

    @Test
    fun viewportCommandDrainScheduler_cancelsRaceAndSupportsNewLifecycle() {
        val callbacks = mutableMapOf<String, () -> Unit>()
        val cleared = mutableListOf<String>()
        var nextRef = 0
        val scheduler = ViewportCommandDrainScheduler(
            setTimeout = { callback ->
                (++nextRef).toString().also { callbacks[it] = callback }
            },
            clearTimeout = cleared::add,
        )
        var drains = 0

        scheduler.activate()
        scheduler.schedule { drains += 1 }
        scheduler.deactivate()
        assertEquals(listOf("1"), cleared)
        callbacks.getValue("1").invoke()
        assertEquals(0, drains)

        scheduler.activate()
        scheduler.schedule {
            drains += 1
            scheduler.schedule { drains += 10 }
        }
        callbacks.getValue("2").invoke()
        assertEquals(1, drains)
        callbacks.getValue("3").invoke()
        assertEquals(11, drains)
    }

    @Test
    fun viewportCommandQueue_handlesCallerSequenceMutationWithoutReentrantDuplicate() {
        val scheduled = mutableListOf<() -> Unit>()
        val zoomFactors = mutableListOf<Float>()
        val panRatios = mutableListOf<Float>()
        lateinit var queue: ViewportCommandQueue
        val executor = ViewportCommandExecutor(
            zoomBy = { factor ->
                zoomFactors += factor
                val next = ChartViewportRequest(2, ChartViewportCommand.PAN_RIGHT)
                queue.offer(next)
                queue.offer(next)
            },
            panByRatio = panRatios::add,
            reset = {},
        )
        queue = ViewportCommandQueue(
            scheduleDrain = scheduled::add,
            execute = executor::execute,
        )

        queue.offer(ChartViewportRequest(1, ChartViewportCommand.ZOOM_IN))
        scheduled.removeAt(0).invoke()
        assertEquals(listOf(1.25f), zoomFactors)
        assertTrue(panRatios.isEmpty())
        assertEquals(1, scheduled.size)

        scheduled.removeAt(0).invoke()
        assertEquals(listOf(0.2f), panRatios)
        assertTrue(scheduled.isEmpty())
    }

    @Test
    fun panMovesViewportWithoutBrush() {
        val interaction = ChartInteractionConfig().apply {
            enablePan = true
            enableDragSelect = true
            brushZoom = true
            clampToData = false
        }
        val initial = ChartViewport(0f, 10f, 0f, 10f)
        val changes = mutableListOf<ChartViewport>()
        val controller = ChartGestureController(interaction, initial, initial, changes::add)
        val scale = CartesianScale(PlotRect(0f, 0f, 100f, 100f), initial)
        controller.onPanStart(20f, 50f, scale)
        controller.onPanMove(40f, 50f, scale)
        controller.onPanEnd()
        // 未长按武装时单指拖动走平移，不框选
        assertTrue(changes.isNotEmpty())
        assertTrue(controller.currentViewport().xMin < initial.xMin)
    }

    @Test
    fun panWorksFromInitialFocusedWindowWithoutPinch() {
        // clamp 开着，开局居中 55% 窗口，不捏合也应能左右拖
        val interaction = ChartInteractionConfig().apply {
            enablePan = true
            clampToData = true
            lockY = true
            initialVisibleRatio = 0.55f
            initialVisibleAnchor = VisibleAnchor.CENTER
        }
        val bounds = ChartViewport(0f, 10f, 0f, 100f)
        val home = bounds.focusedXWindow(
            interaction.initialVisibleRatio,
            interaction.initialVisibleAnchor,
        )
        assertTrue(home.xMax - home.xMin < bounds.xMax - bounds.xMin)
        assertTrue(home.xMin > bounds.xMin)
        assertTrue(home.xMax < bounds.xMax)

        val changes = mutableListOf<ChartViewport>()
        val controller = ChartGestureController(interaction, home, bounds, changes::add)
        val scale = CartesianScale(PlotRect(0f, 0f, 100f, 100f), home)
        controller.onPanStart(50f, 50f, scale)
        controller.onPanMove(80f, 50f, scale)
        controller.onPanEnd()

        assertTrue(changes.isNotEmpty())
        assertTrue(controller.currentViewport().xMin < home.xMin)
        assertTrue(controller.currentViewport().xMin >= bounds.xMin)
    }

    @Test
    fun panIsNoOpWhenViewportAlreadyFullWidth() {
        // 旧问题：视口=全量时 clamp 会吞掉平移；证明「必须先放大」的根因仍存在于全幅态
        val interaction = ChartInteractionConfig().apply {
            enablePan = true
            clampToData = true
        }
        val full = ChartViewport(0f, 10f, 0f, 10f)
        val controller = ChartGestureController(interaction, full, full) {}
        val scale = CartesianScale(PlotRect(0f, 0f, 100f, 100f), full)
        controller.onPanStart(20f, 50f, scale)
        controller.onPanMove(80f, 50f, scale)
        assertEquals(0f, controller.currentViewport().xMin)
        assertEquals(10f, controller.currentViewport().xMax)
    }

    @Test
    fun longPressBrushZoomsIntoSelection() {
        val interaction = ChartInteractionConfig().apply {
            enablePan = true
            enableDragSelect = true
            brushZoom = true
            clampToData = false
        }
        val initial = ChartViewport(0f, 10f, 0f, 10f)
        val controller = ChartGestureController(interaction, initial, initial) {}
        val scale = CartesianScale(PlotRect(0f, 0f, 100f, 100f), initial)
        controller.onBrushStart(20f, scale)
        controller.onBrushMove(80f, scale)
        val range = assertNotNull(controller.onBrushEnd())
        assertEquals(2f, range.start)
        assertEquals(8f, range.endInclusive)
        // brushZoom 后视口收窄到选区附近
        assertTrue(controller.currentViewport().xMax - controller.currentViewport().xMin < 10f)
        assertTrue(controller.currentViewport().xMin >= 1.5f)
        assertTrue(controller.currentViewport().xMax <= 8.5f)
    }

    @Test
    fun longPressBrushContinuesWhenNormalPanIsDisabled() {
        val interaction = ChartInteractionConfig().apply {
            enablePan = false
            enableDragSelect = true
            brushZoom = false
            clampToData = false
        }
        val initial = ChartViewport(0f, 10f, 0f, 10f)
        val controller = ChartGestureController(interaction, initial, initial) {}
        var finished: ClosedFloatingPointRange<Float>? = null
        val handler = ChartTouchViewportHandler(
            interaction = interaction,
            controllerProvider = { controller },
            scaleProvider = { CartesianScale(PlotRect(0f, 0f, 100f, 100f), initial) },
            onBrushRangeChanged = {},
            onBrushFinished = { finished = it },
            onCrosshair = { _, _ -> },
        )

        handler.beginBrush(20f)
        handler.onNativePan("move", 80f, 50f)
        handler.onNativePan("end", 80f, 50f)

        val range = assertNotNull(finished)
        assertEquals(2f, range.start)
        assertEquals(8f, range.endInclusive)
    }

    @Test
    fun explicitTouchLifecycleEndsAndRestartsPinchWithoutActionField() {
        val interaction = ChartInteractionConfig().apply {
            enableScale = true
            clampToData = false
        }
        val initial = ChartViewport(0f, 10f, 0f, 10f)
        val controller = ChartGestureController(interaction, initial, initial) {}
        val plot = PlotRect(0f, 0f, 100f, 100f)
        val handler = ChartTouchViewportHandler(
            interaction = interaction,
            controllerProvider = { controller },
            scaleProvider = { CartesianScale(plot, controller.currentViewport()) },
            onBrushRangeChanged = {},
            onBrushFinished = {},
            onCrosshair = { _, _ -> },
        )

        handler.onTouchDown(touchParams(40f, 60f))
        handler.onTouchMove(touchParams(30f, 70f))
        handler.onTouchUp(touchParams(30f))
        assertEquals(5f, controller.currentViewport().xMax - controller.currentViewport().xMin)

        handler.onTouchDown(touchParams(40f, 60f))
        handler.onTouchMove(touchParams(40f, 60f))
        assertEquals(5f, controller.currentViewport().xMax - controller.currentViewport().xMin)
    }

    @Test
    fun lockYKeepsVerticalRangeOnPinch() {
        val interaction = ChartInteractionConfig().apply {
            enableScale = true
            lockY = true
            clampToData = false
        }
        val initial = ChartViewport(0f, 10f, 0f, 10f)
        val controller = ChartGestureController(interaction, initial, initial) {}
        val plot = PlotRect(0f, 0f, 100f, 100f)
        controller.onPinchStart()
        controller.onPinch(2f, 50f, 50f, CartesianScale(plot, initial))
        assertEquals(0f, controller.currentViewport().yMin)
        assertEquals(10f, controller.currentViewport().yMax)
        assertEquals(5f, controller.currentViewport().xMax - controller.currentViewport().xMin)
    }

    @Test
    fun focusedXWindow_endsAtRightEdge() {
        val full = ChartViewport(0f, 10f, 0f, 100f)
        val home = full.focusedXWindow(0.5f, com.tencent.kuiklybase.chart.config.VisibleAnchor.END)
        assertEquals(5f, home.xMin)
        assertEquals(10f, home.xMax)
        assertEquals(0f, home.yMin)
        assertEquals(100f, home.yMax)
    }

    @Test
    fun tooltipPosition_usesCanvasOffsetPlusLocalPoint() {
        val tip = resolveTooltipPosition(12f, 34f, 50f, 80f)
        assertEquals(62f, tip.first)
        assertEquals(86f, tip.second)
    }

    @Test
    fun tooltipPosition_flipsToLeftNearRightEdge() {
        val tip = resolveTooltipPosition(
            canvasOffsetX = 0f,
            canvasOffsetY = 0f,
            localX = 280f,
            localY = 80f,
            containerWidth = 300f,
            tooltipWidth = 100f,
        )
        assertEquals(172f, tip.first)
        assertEquals(52f, tip.second)
    }

    @Test
    fun tooltipPosition_staysRightWhenSpaceAllows() {
        val tip = resolveTooltipPosition(
            canvasOffsetX = 0f,
            canvasOffsetY = 0f,
            localX = 40f,
            localY = 80f,
            containerWidth = 300f,
            tooltipWidth = 100f,
        )
        assertEquals(48f, tip.first)
    }

    @Test
    fun plannedXAxisTicks_keepLongLabelsInsidePlotWithoutOverlap() {
        val ticks = List(32) { index -> AxisTick(index.toFloat(), "2026-08-${index + 1}") }

        val planned = planCartesianXAxisTicks(
            viewport = ChartViewport(12f, 31f, 0f, 1f),
            plot = PlotRect(40f, 8f, 288f, 180f),
            fontSize = 12f,
            xTicks = ticks,
            measureText = { 68f },
        )

        assertTrue(planned.isNotEmpty())
        assertTrue(planned.all { it.left >= 42f && it.right <= 286f })
        assertTrue(planned.zipWithNext().all { (left, right) -> left.right + 6f <= right.left })
        assertTrue(planned.none { it.value == 12f || it.value == 31f })
        assertTrue(planned.all { tick -> ticks.any { it.value == tick.value && it.text == tick.text } })
    }

    @Test
    fun selectionOverlayVisibility_hidesOffscreenSelectionWithoutAutoResurrection() {
        assertTrue(shouldHideSelectionOverlay(hasSelection = true, selectionVisible = false))
        assertFalse(shouldHideSelectionOverlay(hasSelection = true, selectionVisible = true))
        assertFalse(shouldHideSelectionOverlay(hasSelection = false, selectionVisible = false))
        assertFalse(resolveSelectionOverlayVisibility(wasVisible = false, selectionVisible = true))
    }

    @Test
    fun plannedNumericXAxisTicks_useNiceValuesAndRespectBoundaries() {
        val planned = planCartesianXAxisTicks(
            viewport = ChartViewport(3.2f, 38.7f, 0f, 1f),
            plot = PlotRect(40f, 8f, 288f, 180f),
            fontSize = 12f,
            xTicks = null,
            measureText = { text -> text.length * 7f },
        )

        assertTrue(planned.isNotEmpty())
        assertTrue(planned.all { it.value in 3.2f..38.7f })
        assertTrue(planned.all { it.left >= 42f && it.right <= 286f })
        assertTrue(planned.zipWithNext().all { (left, right) -> left.right + 6f <= right.left })
        assertTrue(planned.all { it.value % 6f == 0f })
    }

    @Test
    fun plannedNumericXAxisTicks_keepSubUnitLabelsDistinct() {
        val planned = planCartesianXAxisTicks(
            viewport = ChartViewport(0.012f, 0.083f, 0f, 1f),
            plot = PlotRect(40f, 8f, 288f, 180f),
            fontSize = 12f,
            xTicks = null,
            measureText = { text -> text.length * 7f },
        )

        assertTrue(planned.size >= 2)
        assertEquals(planned.size, planned.map { it.text }.distinct().size)
    }

    @Test
    fun numericXAxisCandidates_keepTinyPositiveAndNegativeLabelsDistinct() {
        listOf(
            1e-8f to 8e-8f,
            -8e-8f to -1e-8f,
        ).forEach { (first, second) ->
            val candidates = generateNumericXAxisTicks(first, second)

            assertTrue(candidates.size in 2..64, "$first..$second")
            assertTrue(candidates.all { it.value.isFinite() }, "$first..$second")
            assertEquals(candidates.size, candidates.map { it.text }.distinct().size, "$first..$second")
            assertTrue(candidates.all { 'E' in it.text || 'e' in it.text }, "$first..$second")
        }
    }

    @Test
    fun numericXAxisCandidates_areFiniteAndBoundedAcrossExtremeRanges() {
        val ranges = listOf(
            -Float.MAX_VALUE to Float.MAX_VALUE,
            Float.MAX_VALUE / 2f to Float.MAX_VALUE,
            -Float.MAX_VALUE to -Float.MAX_VALUE / 2f,
            -1_000_000_000f to 1_000_000_000f,
            38.7f to 3.2f,
            0.012f to 0.083f,
        )

        ranges.forEach { (first, second) ->
            val candidates = generateNumericXAxisTicks(first, second)
            assertTrue(candidates.isNotEmpty(), "$first..$second")
            assertTrue(candidates.size <= 64, "$first..$second produced ${candidates.size}")
            assertTrue(candidates.all { it.value.isFinite() }, "$first..$second")
            assertTrue(candidates.all { tick ->
                !tick.text.contains("NaN", ignoreCase = true) &&
                    !tick.text.contains("Infinity", ignoreCase = true) &&
                    tick.text.toDoubleOrNull()?.isFinite() == true
            }, "$first..$second labels=${candidates.map { it.text }}")

            val planned = planCartesianXAxisTicks(
                viewport = ChartViewport(first, second, 0f, 1f),
                plot = PlotRect(40f, 8f, 288f, 180f),
                fontSize = 12f,
                xTicks = null,
                measureText = { 24f },
            )
            assertTrue(planned.isNotEmpty(), "$first..$second")
            assertTrue(planned.size <= 64, "$first..$second")
            assertTrue(planned.all { it.value.isFinite() && it.x.isFinite() }, "$first..$second")
        }
    }

    @Test
    fun numericXAxisCandidates_rejectNonFiniteOrCollapsedRanges() {
        assertTrue(generateNumericXAxisTicks(Float.NaN, 1f).isEmpty())
        assertTrue(generateNumericXAxisTicks(1f, Float.POSITIVE_INFINITY).isEmpty())
        assertTrue(generateNumericXAxisTicks(5f, 5f).isEmpty())
    }

    @Test
    fun emptyCategoryTicks_fallBackToNumericCandidates() {
        val planned = planCartesianXAxisTicks(
            viewport = ChartViewport(0f, 10f, 0f, 1f),
            plot = PlotRect(40f, 8f, 288f, 180f),
            fontSize = 12f,
            xTicks = emptyList(),
            measureText = { 20f },
        )

        assertTrue(planned.isNotEmpty())
        assertTrue(planned.all { it.text.toFloatOrNull() != null })
    }

    @Test
    fun cartesianAxis_measuresOnlyVisibleLabelsAndCachesWidths() {
        val cache = AxisLabelWidthCache(maxEntries = 8)
        val ticks = List(20) { AxisTick(it.toFloat(), "D$it") }
        var measurements = 0
        repeat(2) {
            planCartesianXAxisTicks(
                viewport = ChartViewport(8f, 10f, 0f, 1f),
                plot = PlotRect(40f, 8f, 288f, 180f),
                fontSize = 12f,
                xTicks = ticks,
                widthCache = cache,
                measureText = { measurements++; 20f },
            )
        }

        assertEquals(4, measurements)
        assertTrue(cache.size <= 8)
    }

    @Test
    fun axisTicksFromSeries_usesFirstNonEmptySeries() {
        assertTrue(com.tencent.kuiklybase.chart.core.ChartCanvasRenderer.axisTicksFromSeries(emptyList()).isEmpty())
        val ticks = com.tencent.kuiklybase.chart.core.ChartCanvasRenderer.axisTicksFromSeries(
            listOf(
                ChartSeries("empty", emptyList(), 0xFF000000),
                ChartSeries(
                    "values",
                    listOf(ChartDataPoint("A", 2f, 4f), ChartDataPoint("B", 3f, 5f)),
                    0xFF000000,
                ),
            ),
        )

        assertEquals(listOf(2f, 3f), ticks.map { it.value })
        assertEquals(listOf("A", "B"), ticks.map { it.text })
    }

    @Test
    fun viewport_fromStackedSeries_sumsCategoryTotals() {
        val series = listOf(
            ChartSeries(
                "A",
                listOf(ChartDataPoint("Q1", 0f, 10f), ChartDataPoint("Q2", 1f, 20f)),
                0xFF0000FF,
            ),
            ChartSeries(
                "B",
                listOf(ChartDataPoint("Q1", 0f, 5f), ChartDataPoint("Q2", 1f, 15f)),
                0xFFFF0000,
            ),
        )
        val vp = ChartViewport.fromSeries(series, isCategoryX = true, stacked = true)
        assertTrue(vp.yMax > 35f)
    }

    @Test
    fun viewport_fromOhlc_coversHighLow() {
        val points = listOf(
            com.tencent.kuiklybase.chart.model.OhlcPoint("D1", 0f, 10f, 15f, 8f, 12f),
            com.tencent.kuiklybase.chart.model.OhlcPoint("D2", 1f, 12f, 18f, 11f, 14f),
        )
        val vp = ChartViewport.fromOhlc(points)
        assertTrue(vp.yMin < 8f)
        assertTrue(vp.yMax > 18f)
    }

    @Test
    fun viewport_fromOhlc_ignoresMalformedPointsWithoutLosingValidBounds() {
        val valid = com.tencent.kuiklybase.chart.model.OhlcPoint("valid", 4f, 10f, 15f, 8f, 12f)
        val points = listOf(
            valid,
            valid.copy(label = "nan-open", x = 100f, open = Float.NaN),
            valid.copy(label = "inverted", x = 200f, high = 7f, low = 9f),
            valid.copy(label = "outside", x = 300f, close = 20f),
        )

        assertEquals(ChartViewport.fromOhlc(listOf(valid)), ChartViewport.fromOhlc(points))
    }

    @Test
    fun viewport_fromOhlc_allMalformedFallsBackToUnitViewport() {
        val malformed = listOf(
            com.tencent.kuiklybase.chart.model.OhlcPoint("nan", 0f, Float.NaN, 2f, 0f, 1f),
            com.tencent.kuiklybase.chart.model.OhlcPoint("inverted", 1f, 1f, 0f, 2f, 1f),
        )

        assertEquals(ChartViewport(0f, 1f, 0f, 1f), ChartViewport.fromOhlc(malformed))
    }

    @Test
    fun axisTicksFromSeries_preferPointLabels() {
        val series = listOf(
            ChartSeries(
                "sales",
                listOf(
                    ChartDataPoint("一月", 0f, 10f),
                    ChartDataPoint("二月", 1f, 20f),
                    ChartDataPoint("", 2f, 15f),
                ),
                0xFF4F8FFF,
            ),
        )
        val ticks = com.tencent.kuiklybase.chart.core.ChartCanvasRenderer.axisTicksFromSeries(series)
        assertEquals(3, ticks.size)
        assertEquals("一月", ticks[0].text)
        assertEquals("二月", ticks[1].text)
        assertEquals("2", ticks[2].text)
    }

    @Test
    fun hitTester_findsNearestPoint() {
        val series = listOf(
            ChartSeries(
                name = "A",
                color = 0xFF000000,
                points = listOf(
                    com.tencent.kuiklybase.chart.model.ChartDataPoint("", 0f, 0f),
                    com.tencent.kuiklybase.chart.model.ChartDataPoint("", 10f, 10f),
                ),
            ),
        )
        val plot = PlotRect(0f, 0f, 100f, 100f)
        val scale = CartesianScale(plot, ChartViewport(0f, 10f, 0f, 10f))
        val hit = CartesianHitTester.nearestPoint(series, scale, 100f, 0f, threshold = 20f)
        assertNotNull(hit)
        assertEquals(1, hit.pointIndex)
    }

    @Test
    fun selectedPointCrosshair_recomputesAfterViewportChanges() {
        val series = listOf(
            ChartSeries("趋势", listOf(ChartDataPoint("中点", 5f, 50f)), 0L),
        )
        val plot = PlotRect(40f, 10f, 240f, 210f)
        val selection = com.tencent.kuiklybase.chart.model.ChartSelection.Cartesian(0, 0, "中点")

        val full = resolveCartesianSelectionCrosshair(
            series, selection, plot, ChartViewport(0f, 10f, 0f, 100f),
        )
        val zoomed = resolveCartesianSelectionCrosshair(
            series, selection, plot, ChartViewport(4f, 8f, 0f, 100f),
        )

        assertEquals(140f, full?.first)
        assertEquals(90f, zoomed?.first)
        assertEquals(110f, zoomed?.second)
    }

    @Test
    fun selectedPointCrosshair_hidesWhenPointLeavesPlot() {
        val series = listOf(
            ChartSeries("trend", listOf(ChartDataPoint("start", 1f, 50f)), 0L),
        )
        val crosshair = resolveCartesianSelectionCrosshair(
            series,
            com.tencent.kuiklybase.chart.model.ChartSelection.Cartesian(0, 0, "start"),
            PlotRect(40f, 10f, 240f, 210f),
            ChartViewport(4f, 8f, 0f, 100f),
        )

        assertEquals(null, crosshair)
    }

    @Test
    fun stackedBarHitTester_selectsActualStackSegment() {
        val series = listOf(
            ChartSeries("bottom", listOf(ChartDataPoint("A", 0f, 40f)), 0L),
            ChartSeries("top", listOf(ChartDataPoint("A", 0f, 60f)), 0L),
        )
        val scale = CartesianScale(PlotRect(0f, 0f, 100f, 100f), ChartViewport(-1f, 1f, 0f, 100f))

        val hit = CartesianHitTester.nearestBar(
            series,
            scale,
            x = 50f,
            y = 20f,
            stacked = true,
        )

        assertNotNull(hit)
        assertEquals(1, hit.seriesIndex)
    }

    @Test
    fun groupedBarHitTester_selectsSecondSeriesBar() {
        val series = listOf(
            ChartSeries("direct", listOf(ChartDataPoint("A", 0f, 40f)), 0L),
            ChartSeries("partner", listOf(ChartDataPoint("A", 0f, 60f)), 0L),
        )
        val scale = CartesianScale(PlotRect(0f, 0f, 100f, 100f), ChartViewport(-1f, 1f, 0f, 100f))

        val hit = CartesianHitTester.nearestBar(series, scale, x = 75f, y = 60f, grouped = true)

        assertNotNull(hit)
        assertEquals(1, hit.seriesIndex)
    }

    @Test
    fun stackedHorizontalBarHitTester_selectsActualStackSegment() {
        val series = listOf(
            ChartSeries("left", listOf(ChartDataPoint("A", 0f, 40f)), 0L),
            ChartSeries("right", listOf(ChartDataPoint("A", 0f, 60f)), 0L),
        )
        val scale = CartesianScale(PlotRect(0f, 0f, 100f, 100f), ChartViewport(0f, 100f, -1f, 1f))

        val hit = CartesianHitTester.nearestHorizontalBar(
            series,
            scale,
            x = 80f,
            y = 50f,
            stacked = true,
        )

        assertNotNull(hit)
        assertEquals(1, hit.seriesIndex)
    }

    @Test
    fun barHitTester_ignoresBlankArea() {
        val series = listOf(
            ChartSeries("bars", listOf(ChartDataPoint("A", 0f, 40f)), 0L),
        )
        val scale = CartesianScale(PlotRect(0f, 0f, 100f, 100f), ChartViewport(-1f, 1f, 0f, 100f))

        assertEquals(null, CartesianHitTester.nearestBar(series, scale, x = 50f, y = 40f))
    }

    @Test
    fun horizontalBarHitTester_ignoresBlankArea() {
        val series = listOf(
            ChartSeries("bars", listOf(ChartDataPoint("A", 0f, 40f)), 0L),
        )
        val scale = CartesianScale(PlotRect(0f, 0f, 100f, 100f), ChartViewport(0f, 100f, -1f, 1f))

        assertEquals(null, CartesianHitTester.nearestHorizontalBar(series, scale, x = 80f, y = 20f))
    }

    @Test
    fun interactiveLegendFiltersWithoutMutatingSourceSeries() {
        val source = listOf(
            ChartSeries("A", emptyList(), 0L),
            ChartSeries("B", emptyList(), 0L),
        )
        val hidden = toggleHiddenSeries(emptySet(), "A")

        assertEquals(listOf("B"), filterVisibleSeries(source, hidden).map { it.name })
        assertEquals(listOf("A", "B"), source.map { it.name })
        assertTrue(toggleHiddenSeries(hidden, "A").isEmpty())
    }

    @Test
    fun longPress_prefersBrushThenInspect() {
        val interaction = ChartInteractionConfig().apply {
            enableDragSelect = true
            enableLongPressInspect = true
        }
        assertEquals(LongPressAction.BRUSH, resolveLongPressAction(interaction))

        interaction.enableDragSelect = false
        assertEquals(LongPressAction.INSPECT, resolveLongPressAction(interaction))

        interaction.enableLongPressInspect = false
        assertEquals(LongPressAction.NONE, resolveLongPressAction(interaction))
    }

    private fun touchParams(vararg xCoordinates: Float): TouchParams {
        val touches = xCoordinates.mapIndexed { index, x ->
            Touch(x, 50f, x, 50f, index.toFloat(), index.toLong())
        }
        return TouchParams(
            x = xCoordinates.firstOrNull() ?: 0f,
            y = 50f,
            pageX = xCoordinates.firstOrNull() ?: 0f,
            pageY = 50f,
            timestamp = 0L,
            pointerId = 0,
            action = "",
            touches = touches,
            consumed = false,
        )
    }
}
