package com.tencent.kuiklybase.chart.stock

import com.tencent.kuiklybase.chart.model.OhlcPoint
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StockIndicatorsTest {
    @Test
    fun amount_usesCloseTimesVolumeAndKeepsMissingValuesNull() {
        val points = listOf(point(close = 10f, volume = 100f), point(close = 12f, volume = 200f), point(close = 9f))

        assertEquals(listOf(1000f, 2400f, null), stockAmount(points))
    }

    @Test
    fun amount_emptyMissingAndOverflowInputsNeverProduceNonFiniteValues() {
        assertTrue(stockAmount(emptyList()).isEmpty())
        assertEquals(listOf(null, null), stockAmount(listOf(point(10f), point(Float.MAX_VALUE, volume = 2f))))
    }

    @Test
    fun amount_masksVolumeAtInvalidOhlcIndices() {
        val invalid = point(close = 10f, volume = 100f, open = 20f, high = 15f, low = 5f)

        assertEquals(listOf(null), stockAmount(listOf(invalid)))
    }

    @Test
    fun movingAverage_waitsForAFullWindow() {
        assertEquals(listOf(null, null, 2f, 3f), ma(listOf(1f, 2f, 3f, 4f), 3))
    }

    @Test
    fun stockMovingAverageLines_useFiveTenTwentyAndThirtyPeriods() {
        val result = stockMaLines((1..30).map { point(it.toFloat()) })

        assertEquals(listOf("MA5", "MA10", "MA20", "MA30"), result.map { it.name })
        assertClose(28f, result[0].values.last())
        assertClose(25.5f, result[1].values.last())
        assertClose(20.5f, result[2].values.last())
        assertClose(15.5f, result[3].values.last())
    }

    @Test
    fun closeBasedMainIndicators_restartAfterInvalidOhlcGaps() {
        val points = (1..4).map { point(it.toFloat()) } +
            point(close = 1000f, open = 1001f, high = 1000f, low = 999f) +
            (10..14).map { point(it.toFloat()) }

        assertEquals(listOf(null, null, null, null, null, null, null, null, null, 12f), stockMaLines(points)[0].values)
        assertEquals(null, stockExpmaLines(points)[0].values[4])
        assertClose(10f, stockExpmaLines(points)[0].values[5])
    }

    @Test
    fun boll_usesPopulationDeviationOverTheFullWindow() {
        val result = boll((1..20).map(Int::toFloat))

        assertEquals(listOf("BOLL", "UP", "DN"), result.map { it.name })
        assertClose(10.5f, result[0].values.last())
        assertClose(22.032562f, result[1].values.last())
        assertClose(-1.0325623f, result[2].values.last())
        assertTrue(result.all { line -> line.values.take(19).all { it == null } })
    }

    @Test
    fun ema_startsAtTheFirstClose() {
        assertNullableListClose(listOf(1f, 1.5f, 2.25f), ema(listOf(1f, 2f, 3f), 3))
    }

    @Test
    fun expma_usesTwelveAndFiftyPeriodLinesWithDeterministicColors() {
        val result = expma(listOf(10f, 12f))

        assertEquals(listOf("EXPMA12", "EXPMA50"), result.map { it.name })
        assertEquals(0xFF14A9D6, result[0].color)
        assertEquals(0xFFE7B900, result[1].color)
        assertClose(10.307692f, result[0].values.last())
        assertClose(10.078431f, result[1].values.last())
    }

    @Test
    fun emittedLineFamilies_useStableDistinctLiteralColors() {
        val points = (1..30).map { point(it.toFloat()) }

        assertEquals(
            mapOf("MA5" to 0xFF14A9D6, "MA10" to 0xFFE7B900, "MA20" to 0xFFE24AE3, "MA30" to 0xFF7B6FE8),
            stockMaLines(points).associate { it.name to it.color },
        )
        assertEquals(
            mapOf("BOLL" to 0xFF14A9D6, "UP" to 0xFFE7B900, "DN" to 0xFFE24AE3),
            stockBollLines(points).associate { it.name to it.color },
        )
        assertEquals(mapOf("BBI" to 0xFF7B6FE8), stockBbiLines(points).associate { it.name to it.color })
        assertEquals(
            mapOf("UPPER" to 0xFFFF8A34, "ENE" to 0xFF14A9D6, "LOWER" to 0xFF36B37E),
            stockEneLines(points).associate { it.name to it.color },
        )
        assertEquals(
            mapOf("RSI6" to 0xFF14A9D6, "RSI12" to 0xFFE7B900, "RSI24" to 0xFFE24AE3),
            stockRsiLines(points).associate { it.name to it.color },
        )
    }

    @Test
    fun bbi_requiresAllFourMovingAverages() {
        val result = bbi((1..24).map(Int::toFloat))

        assertTrue(result.take(23).all { it == null })
        assertClose(18.875f, result.last())
    }

    @Test
    fun ene_appliesElevenAndNinePercentBandsToMaTen() {
        val result = ene((1..10).map(Int::toFloat))

        assertEquals(listOf("UPPER", "ENE", "LOWER"), result.map { it.name })
        assertClose(6.105f, result[0].values.last())
        assertClose(5.5f, result[1].values.last())
        assertClose(5.005f, result[2].values.last())
    }

    @Test
    fun macd_preservesSourceAlignmentAndUsesImmediateEmaWarmup() {
        val result = macd(listOf(1f, 2f))

        assertNullableListClose(listOf(0f, 0.07977208f), result.diff)
        assertNullableListClose(listOf(0f, 0.015954416f), result.dea)
        assertNullableListClose(listOf(0f, 0.12763533f), result.histogram)
    }

    @Test
    fun macdAndRsi_restartTheirStateAfterInvalidOhlcGaps() {
        val prefix = (1..30).map { point(it.toFloat()) }
        val invalid = point(close = 10_000f, open = 10_001f, high = 10_000f, low = 9_999f)
        val suffix = (40..70).map { point(it.toFloat()) }
        val points = prefix + invalid + suffix

        val expectedMacd = macd(suffix.map { it.close })
        val actualMacd = stockMacd(points)
        assertNull(actualMacd.diff[prefix.size])
        assertNullableListClose(expectedMacd.diff, actualMacd.diff.drop(prefix.size + 1))
        assertNullableListClose(expectedMacd.dea, actualMacd.dea.drop(prefix.size + 1))
        assertNullableListClose(expectedMacd.histogram, actualMacd.histogram.drop(prefix.size + 1))

        val expectedRsi = stockRsiLines(suffix)
        val actualRsi = stockRsiLines(points)
        expectedRsi.zip(actualRsi).forEach { (expected, actual) ->
            assertNull(actual.values[prefix.size])
            assertNullableListClose(expected.values, actual.values.drop(prefix.size + 1))
        }
    }

    @Test
    fun kdj_usesSeedFiftyAndChineseSmoothing() {
        val points = listOf(
            point(close = 5f, high = 10f, low = 0f),
            point(close = 10f, high = 10f, low = 0f),
            point(close = 0f, high = 10f, low = 0f),
        )
        val result = kdj(points, period = 3)

        assertEquals(listOf(null, null), result.k.take(2))
        assertClose(33.333332f, result.k.last())
        assertClose(44.444443f, result.d.last())
        assertClose(11.111107f, result.j.last())
    }

    @Test
    fun kdj_flatWindowUsesNeutralRsv() {
        val result = kdj(List(3) { point(close = 5f, high = 5f, low = 5f) }, period = 3)

        assertClose(50f, result.k.last())
        assertClose(50f, result.d.last())
        assertClose(50f, result.j.last())
    }

    @Test
    fun rsi_handlesRisingFallingConstantAndMixedChanges() {
        assertEquals(listOf(null, null, 100f), rsi(listOf(1f, 2f, 3f), 2))
        assertEquals(listOf(null, null, 0f), rsi(listOf(3f, 2f, 1f), 2))
        assertEquals(listOf(null, null, 50f), rsi(listOf(2f, 2f, 2f), 2))
        assertClose(66.66667f, rsi(listOf(1f, 3f, 2f), 2).last())
    }

    @Test
    fun rsi_usesWilderSmoothingAfterTheSeedWindow() {
        assertNullableListClose(listOf(null, null, 100f, 60f), rsi(listOf(1f, 3f, 4f, 3f), 2))
    }

    @Test
    fun stockRsiLines_useSixTwelveAndTwentyFourPeriods() {
        val result = stockRsiLines((1..25).map { point(it.toFloat()) })

        assertEquals(listOf("RSI6", "RSI12", "RSI24"), result.map { it.name })
        assertTrue(result.all { it.values.last() == 100f })
    }

    @Test
    fun wr_waitsForWindowAndHandlesFlatRanges() {
        val directional = listOf(
            point(close = 5f, high = 10f, low = 0f),
            point(close = 10f, high = 10f, low = 0f),
            point(close = 0f, high = 10f, low = 0f),
        )

        assertEquals(listOf(null, null, 100f), wr(directional, 3))
        assertEquals(listOf(null, null, 0f), wr(List(3) { point(close = 5f, high = 5f, low = 5f) }, 3))
    }

    @Test
    fun kdjAndWr_rejectMalformedValuesAnywhereInTheWindow() {
        val malformedWindows = listOf(
            listOf(point(5f, 10f, 0f), point(5f, Float.NaN, 0f), point(5f, 10f, 0f)),
            listOf(point(5f, 10f, 0f), point(5f, 0f, 10f), point(5f, 10f, 0f)),
            listOf(point(5f, 10f, 0f), point(11f, 10f, 0f), point(5f, 10f, 0f)),
            listOf(point(5f, 10f, 0f), point(5f, 10f, 0f, x = Float.NaN), point(5f, 10f, 0f)),
            listOf(point(5f, 10f, 0f), point(5f, 10f, 0f, open = Float.NaN), point(5f, 10f, 0f)),
            listOf(point(5f, 10f, 0f), point(5f, 10f, 0f, open = 11f), point(5f, 10f, 0f)),
        )

        malformedWindows.forEach { points ->
            assertNull(kdj(points, 3).k.last())
            assertNull(kdj(points, 3).d.last())
            assertNull(kdj(points, 3).j.last())
            assertNull(wr(points, 3).last())
        }
    }

    @Test
    fun expmaBbiEneKdjAndWr_coverEmptyShortAndConstantInputsWithoutNonFiniteValues() {
        assertTrue(expma(emptyList()).all { it.values.isEmpty() })
        assertEquals(listOf(null), bbi(listOf(4f)))
        assertTrue(ene(listOf(4f)).all { it.values == listOf(null) })
        assertEquals(listOf(null), kdj(listOf(point(4f)), 2).k)
        assertEquals(listOf(null), wr(listOf(point(4f)), 2))

        assertAllFiniteOrNull(expma(List(50) { 4f }).flatMap { it.values })
        assertAllFiniteOrNull(bbi(List(24) { 4f }))
        assertAllFiniteOrNull(ene(List(10) { 4f }).flatMap { it.values })
        val constantKdj = kdj(List(9) { point(4f) })
        assertAllFiniteOrNull(constantKdj.k + constantKdj.d + constantKdj.j)
        assertAllFiniteOrNull(wr(List(10) { point(4f) }))
    }

    @Test
    fun emptyAndShortInputsPreserveShapeWithoutNonFiniteValues() {
        assertTrue(ma(emptyList(), 3).isEmpty())
        assertEquals(listOf<Float?>(null), ma(listOf(1f), 2))
        assertTrue(macd(emptyList()).diff.isEmpty())
        assertEquals(listOf<Float?>(null), rsi(listOf(1f), 6))
        assertAllFiniteOrNull(boll(List(20) { 4f }).flatMap { it.values })
        assertAllFiniteOrNull(macd(List(30) { 4f }).diff + macd(List(30) { 4f }).dea + macd(List(30) { 4f }).histogram)
    }

    @Test
    fun allValidPointWrappersPreserveTheExistingCloseOnlyOutputs() {
        val points = (1..30).map { point(it.toFloat()) }

        assertEquals(ma(points.map { it.close }, 5), stockMaLines(points)[0].values)
        assertEquals(boll(points.map { it.close }), stockBollLines(points))
        assertEquals(expma(points.map { it.close }), stockExpmaLines(points))
        assertEquals(bbi(points.map { it.close }), stockBbiLines(points).single().values)
        assertEquals(ene(points.map { it.close }), stockEneLines(points))
        assertEquals(macd(points.map { it.close }), stockMacd(points))
    }

    @Test
    fun helpersRejectNonPositivePeriods() {
        assertFailsWith<IllegalArgumentException> { ma(listOf(1f), 0) }
        assertFailsWith<IllegalArgumentException> { ema(listOf(1f), -1) }
        assertFailsWith<IllegalArgumentException> { boll(listOf(1f), period = 0) }
        assertFailsWith<IllegalArgumentException> { ene(listOf(1f), period = 0) }
        assertFailsWith<IllegalArgumentException> { kdj(listOf(point(1f)), period = 0) }
        assertFailsWith<IllegalArgumentException> { rsi(listOf(1f), 0) }
        assertFailsWith<IllegalArgumentException> { wr(listOf(point(1f)), 0) }
        assertFailsWith<IllegalArgumentException> { smoothedNullable(listOf(1f), 0, 50f) }
    }

    private fun point(
        close: Float,
        high: Float = close,
        low: Float = close,
        volume: Float? = null,
        x: Float = 0f,
        open: Float = close,
    ) = OhlcPoint("", x, open, high, low, close, volume)

    private fun assertClose(expected: Float, actual: Float?, tolerance: Float = 0.0001f) {
        assertTrue(actual != null, "expected $expected but was null")
        assertTrue(abs(expected - actual) <= tolerance, "expected $expected but was $actual")
    }

    private fun assertNullableListClose(expected: List<Float?>, actual: List<Float?>) {
        assertEquals(expected.size, actual.size)
        expected.zip(actual).forEach { (expectedValue, actualValue) ->
            if (expectedValue == null) assertNull(actualValue) else assertClose(expectedValue, actualValue)
        }
    }

    private fun assertAllFiniteOrNull(values: List<Float?>) {
        assertTrue(values.all { it == null || it.isFinite() })
    }
}
