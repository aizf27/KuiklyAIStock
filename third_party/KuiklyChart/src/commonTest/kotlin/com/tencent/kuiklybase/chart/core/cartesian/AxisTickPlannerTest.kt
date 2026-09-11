package com.tencent.kuiklybase.chart.core.cartesian

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AxisTickPlannerTest {
    @Test
    fun narrowCategoryLabelsNeverOverlap() {
        val result = AxisTickPlanner.plan(
            input(
                plotWidth = 100f,
                domain = AxisTickDomain.CATEGORY_INDEX,
                candidates = candidates(0..7, measuredWidth = 34f),
            ),
        )

        assertTrue(result.size >= 2)
        result.zipWithNext().forEach { (left, right) ->
            assertTrue(left.right + 6f <= right.left)
        }
    }

    @Test
    fun retriesWithDenserCandidatesWhenSteppedEdgesAreRejected() {
        val result = AxisTickPlanner.plan(
            input(
                plotWidth = 100f,
                domain = AxisTickDomain.NUMERIC,
                candidates = listOf(0f, 2f, 8f, 10f).map { value ->
                    AxisTickCandidate(value, "label-$value", 20f)
                },
            ),
        )

        assertTrue(result.size >= 2)
        assertTrue(result.zipWithNext().all { (left, right) -> left.right + 6f <= right.left })
    }

    @Test
    fun retryAlwaysRespectsConfiguredEdgePadding() {
        val result = AxisTickPlanner.plan(
            AxisTickPlanInput(
                visibleMin = 0f,
                visibleMax = 10f,
                plotLeft = 0f,
                plotWidth = 100f,
                fontSize = 12f,
                edgePadding = 20f,
                domain = AxisTickDomain.NUMERIC,
                candidates = listOf(
                    AxisTickCandidate(2f, "two", 10f),
                    AxisTickCandidate(8f, "eight", 10f),
                ),
            ),
        )

        assertEquals(1, result.size)
        assertEquals(50f, result.single().x)
    }

    @Test
    fun heterogeneousWidthsChooseMaximumNumberOfNonOverlappingTicks() {
        val result = AxisTickPlanner.plan(
            input(
                plotWidth = 100f,
                domain = AxisTickDomain.NUMERIC,
                candidates = listOf(
                    AxisTickCandidate(5f, "wide", 90f),
                    AxisTickCandidate(6f, "narrow-six", 4f),
                    AxisTickCandidate(8f, "narrow-eight", 4f),
                ),
            ),
        )

        assertEquals(listOf(6f, 8f), result.map { it.value })
    }

    @Test
    fun planningIsDeterministic() {
        val input = input(
            plotWidth = 180f,
            domain = AxisTickDomain.NUMERIC,
            candidates = candidates(0..10, measuredWidth = 24f),
        )

        assertEquals(AxisTickPlanner.plan(input), AxisTickPlanner.plan(input))
    }

    @Test
    fun customTimeNiceStepRoundsEstimatedTwelveToFifteen() {
        assertEquals(
            15f,
            AxisTickPlanner.selectNiceStep(
                estimatedStep = 12f,
                domain = AxisTickDomain.TIME,
                allowedSteps = listOf(1f, 5f, 15f, 30f),
            ),
        )
    }

    @Test
    fun defaultNumericAndCategoryNiceStepsIncludeFifteenAndThirty() {
        listOf(AxisTickDomain.NUMERIC, AxisTickDomain.CATEGORY_INDEX).forEach { domain ->
            assertEquals(15f, AxisTickPlanner.selectNiceStep(12f, domain), domain.name)
            assertEquals(30f, AxisTickPlanner.selectNiceStep(25f, domain), domain.name)
            assertEquals(1.5f, AxisTickPlanner.selectNiceStep(1.2f, domain), domain.name)
        }
    }

    @Test
    fun normalWidthTargetsAtLeastTwoLabels() {
        val result = AxisTickPlanner.plan(
            input(
                plotWidth = 240f,
                domain = AxisTickDomain.NUMERIC,
                candidates = listOf(1f, 3f, 7f, 9f).map { value ->
                    AxisTickCandidate(value, "label-$value", 20f)
                },
            ),
        )

        assertTrue(result.size >= 2)
    }

    @Test
    fun extremelyNarrowPlotRejectsCenteredLabelThatCannotFit() {
        val result = AxisTickPlanner.plan(
            input(
                plotLeft = 10f,
                plotWidth = 8f,
                domain = AxisTickDomain.CATEGORY_INDEX,
                candidates = candidates(0..4, measuredWidth = 30f),
            ),
        )

        assertTrue(result.isEmpty())
    }

    @Test
    fun extremelyNarrowPlotUsesMeasuredCompactFallbackWhenItFits() {
        val result = AxisTickPlanner.plan(
            input(
                plotLeft = 10f,
                plotWidth = 8f,
                domain = AxisTickDomain.CATEGORY_INDEX,
                candidates = candidates(0..4, measuredWidth = 30f),
                fallbackText = "…",
                fallbackMeasuredWidth = 3f,
            ),
        )

        assertEquals(1, result.size)
        assertEquals("…", result.single().text)
        assertEquals(4f, result.single().value)
        assertEquals(14f, result.single().x)
        assertTrue(result.single().left >= 12f)
        assertTrue(result.single().right <= 16f)
    }

    @Test
    fun extremelyNarrowPlotReturnsEmptyWhenMeasuredCompactFallbackCannotFit() {
        val result = AxisTickPlanner.plan(
            input(
                plotLeft = 10f,
                plotWidth = 8f,
                domain = AxisTickDomain.CATEGORY_INDEX,
                candidates = candidates(0..4, measuredWidth = 30f),
                fallbackText = "…",
                fallbackMeasuredWidth = 5f,
            ),
        )

        assertTrue(result.isEmpty())
    }

    @Test
    fun compactFallbackSourceValueIsDeterministicForCenterTies() {
        val plan = input(
            plotWidth = 8f,
            domain = AxisTickDomain.NUMERIC,
            candidates = listOf(
                AxisTickCandidate(6f, "right", 30f),
                AxisTickCandidate(4f, "left", 30f),
            ),
            fallbackText = "…",
            fallbackMeasuredWidth = 3f,
        )

        assertEquals(4f, AxisTickPlanner.plan(plan).single().value)
        assertEquals(AxisTickPlanner.plan(plan), AxisTickPlanner.plan(plan))
    }

    @Test
    fun collapsedDomainUsesCompactFallbackWhenFullLabelCannotFit() {
        val result = AxisTickPlanner.plan(
            AxisTickPlanInput(
                visibleMin = 3f,
                visibleMax = 3f,
                plotLeft = 10f,
                plotWidth = 8f,
                fontSize = 12f,
                domain = AxisTickDomain.NUMERIC,
                candidates = listOf(AxisTickCandidate(3f, "three", 30f)),
                fallbackText = "…",
                fallbackMeasuredWidth = 3f,
            ),
        )

        assertEquals("…", result.single().text)
        assertEquals(3f, result.single().value)
    }

    @Test
    fun labelWiderThanPaddedPlotNeverReturnsOverflowingBounds() {
        val result = AxisTickPlanner.plan(
            input(
                plotLeft = 10f,
                plotWidth = 40f,
                domain = AxisTickDomain.NUMERIC,
                candidates = listOf(AxisTickCandidate(5f, "too-wide", 39f)),
            ),
        )

        assertTrue(result.isEmpty())
    }

    @Test
    fun fallbackChoosesNearestFittingCandidateInsteadOfNearestTooWideCandidate() {
        val result = AxisTickPlanner.plan(
            input(
                plotWidth = 40f,
                domain = AxisTickDomain.NUMERIC,
                candidates = listOf(
                    AxisTickCandidate(0f, "left", 10f),
                    AxisTickCandidate(5f, "center-too-wide", 39f),
                    AxisTickCandidate(10f, "right", 10f),
                ),
                fallbackText = "…",
                fallbackMeasuredWidth = 5f,
            ),
        )

        assertEquals(listOf(0f), result.map { it.value })
        assertEquals(listOf("left"), result.map { it.text })
        assertTrue(result.single().left >= 2f)
        assertTrue(result.single().right <= 38f)
        assertTrue(result.single().x.isFinite())
    }

    @Test
    fun partiallyOutOfBoundsEdgeLabelsAreSkipped() {
        val result = AxisTickPlanner.plan(
            input(
                plotLeft = 10f,
                plotWidth = 100f,
                domain = AxisTickDomain.NUMERIC,
                candidates = listOf(
                    AxisTickCandidate(0f, "left", 30f),
                    AxisTickCandidate(5f, "middle", 20f),
                    AxisTickCandidate(10f, "right", 30f),
                ),
                allowedSteps = listOf(5f),
            ),
        )

        assertEquals(listOf("middle"), result.map { it.text })
    }

    @Test
    fun emptyAndSingleInputsAreSafe() {
        assertTrue(
            AxisTickPlanner.plan(
                input(plotWidth = 100f, domain = AxisTickDomain.NUMERIC, candidates = emptyList()),
            ).isEmpty(),
        )

        val single = AxisTickPlanner.plan(
            AxisTickPlanInput(
                visibleMin = 3f,
                visibleMax = 3f,
                plotLeft = 10f,
                plotWidth = 100f,
                fontSize = 12f,
                domain = AxisTickDomain.NUMERIC,
                candidates = listOf(AxisTickCandidate(3f, "three", 20f)),
            ),
        )
        assertEquals(1, single.size)
        assertEquals(60f, single.single().x)
    }

    @Test
    fun singleFittingCandidateKeepsItsDataCoordinate() {
        val result = AxisTickPlanner.plan(
            input(
                plotLeft = 10f,
                plotWidth = 100f,
                domain = AxisTickDomain.NUMERIC,
                candidates = listOf(AxisTickCandidate(2f, "two", 20f)),
            ),
        )

        assertEquals(1, result.size)
        assertEquals(30f, result.single().x)
        assertEquals(20f, result.single().left)
        assertEquals(40f, result.single().right)
    }

    @Test
    fun filtersInvisibleAndDuplicateValues() {
        val result = AxisTickPlanner.plan(
            input(
                plotWidth = 200f,
                domain = AxisTickDomain.NUMERIC,
                candidates = listOf(
                    AxisTickCandidate(-1f, "outside", 10f),
                    AxisTickCandidate(2f, "first", 10f),
                    AxisTickCandidate(2f, "duplicate", 10f),
                    AxisTickCandidate(8f, "eight", 10f),
                    AxisTickCandidate(11f, "outside", 10f),
                ),
            ),
        )

        assertEquals(1, result.count { it.value == 2f })
        assertTrue(result.all { it.value in 0f..10f })
    }

    @Test
    fun invalidGeometryReturnsEmpty() {
        val valid = input(
            plotWidth = 100f,
            domain = AxisTickDomain.NUMERIC,
            candidates = listOf(AxisTickCandidate(5f, "five", 20f)),
        )
        val invalidInputs = listOf(
            valid.copy(plotLeft = Float.NaN),
            valid.copy(plotWidth = Float.POSITIVE_INFINITY),
            valid.copy(visibleMin = Float.NaN),
            valid.copy(visibleMax = Float.NEGATIVE_INFINITY),
            valid.copy(fontSize = Float.NaN),
            valid.copy(fontSize = 0f),
        )

        invalidInputs.forEach { invalid ->
            assertTrue(AxisTickPlanner.plan(invalid).isEmpty())
        }
    }

    @Test
    fun invalidCandidateValuesAndWidthsAreFiltered() {
        val result = AxisTickPlanner.plan(
            input(
                plotWidth = 100f,
                domain = AxisTickDomain.NUMERIC,
                candidates = listOf(
                    AxisTickCandidate(Float.NaN, "nan-value", 20f),
                    AxisTickCandidate(Float.POSITIVE_INFINITY, "infinite-value", 20f),
                    AxisTickCandidate(2f, "nan-width", Float.NaN),
                    AxisTickCandidate(3f, "infinite-width", Float.POSITIVE_INFINITY),
                    AxisTickCandidate(4f, "zero-width", 0f),
                    AxisTickCandidate(5f, "valid", 20f),
                ),
            ),
        )

        assertEquals(listOf("valid"), result.map { it.text })
        assertTrue(result.all { it.x.isFinite() && it.left.isFinite() && it.right.isFinite() })
    }

    private fun input(
        plotLeft: Float = 0f,
        plotWidth: Float,
        domain: AxisTickDomain,
        candidates: List<AxisTickCandidate>,
        allowedSteps: List<Float> = emptyList(),
        fallbackText: String? = null,
        fallbackMeasuredWidth: Float = 0f,
    ) = AxisTickPlanInput(
        visibleMin = 0f,
        visibleMax = 10f,
        plotLeft = plotLeft,
        plotWidth = plotWidth,
        fontSize = 12f,
        domain = domain,
        candidates = candidates,
        allowedSteps = allowedSteps,
        fallbackText = fallbackText,
        fallbackMeasuredWidth = fallbackMeasuredWidth,
    )

    private fun candidates(values: IntRange, measuredWidth: Float): List<AxisTickCandidate> =
        values.map { value -> AxisTickCandidate(value.toFloat(), "label-$value", measuredWidth) }
}
