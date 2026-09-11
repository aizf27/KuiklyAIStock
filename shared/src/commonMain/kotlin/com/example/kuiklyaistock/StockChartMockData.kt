package com.example.kuiklyaistock

import com.example.kuiklyaistock.model.StockDetail
import com.tencent.kuiklybase.chart.model.OhlcPoint
import kotlin.math.max
import kotlin.math.min

// K 线数据替换边界：接入真实行情后仅需替换此处的数据源。
internal fun mockStockCandles(
    stock: StockDetail,
    period: StockChartPeriod,
): List<OhlcPoint> {
    val pointCount = when (period) {
        StockChartPeriod.INTRADAY -> 48
        StockChartPeriod.FIVE_DAY -> 50
        StockChartPeriod.DAILY -> 60
        StockChartPeriod.WEEKLY -> 52
        StockChartPeriod.MONTHLY -> 24
    }
    val amplitude = when (period) {
        StockChartPeriod.INTRADAY -> 0.08f
        StockChartPeriod.FIVE_DAY -> 0.14f
        StockChartPeriod.DAILY -> 0.28f
        StockChartPeriod.WEEKLY -> 0.72f
        StockChartPeriod.MONTHLY -> 1.45f
    }
    val seed = stock.quote.code.fold(0) { result, char -> result + char.code }
    var previousClose = stock.previousClose.toFloat()
    return (0 until pointCount).map { index ->
        val wave = ((index * 7 + seed) % 11 - 5) * amplitude
        val drift = ((index + seed) % 5 - 2) * amplitude * 0.22f
        val open = (previousClose + drift).coerceAtLeast(0.01f)
        val close = (open + wave).coerceAtLeast(0.01f)
        val wick = (1f + (index + seed) % 4) * amplitude * 0.48f
        val high = max(open, close) + wick
        val low = (min(open, close) - wick * 0.82f).coerceAtLeast(0.01f)
        OhlcPoint(
            label = mockCandleLabel(period, index),
            x = index.toFloat(),
            open = open,
            high = high,
            low = low,
            close = close,
            volume = 80_000f + ((index * 37 + seed) % 180) * 1_350f,
        ).also { previousClose = close }
    }
}

private fun mockCandleLabel(period: StockChartPeriod, index: Int): String = when (period) {
    StockChartPeriod.INTRADAY -> mockIntradayTime(index)
    StockChartPeriod.FIVE_DAY -> "第${index / 10 + 1}日 ${mockIntradayTime(index % 10 * 5)}"
    StockChartPeriod.DAILY -> "日K${index + 1}"
    StockChartPeriod.WEEKLY -> "周K${index + 1}"
    StockChartPeriod.MONTHLY -> "月K${index + 1}"
}

private fun mockIntradayTime(index: Int): String {
    val minutesFromOpen = index * 5
    val totalMinutes = if (minutesFromOpen < 120) {
        9 * 60 + 30 + minutesFromOpen
    } else {
        13 * 60 + minutesFromOpen - 120
    }
    return "${totalMinutes / 60}:${(totalMinutes % 60).toString().padStart(2, '0')}"
}