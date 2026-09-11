package com.example.kuiklyaistock.repository

import com.example.kuiklyaistock.model.TrendPoint
import com.tencent.kuiklybase.chart.model.OhlcPoint
import kotlin.random.Random

// 模拟数据生成器，用于填充分时、K线、换手率、市盈率等扩展数据
internal object MockDataGenerator {

    // 生成分时数据：9:30-15:00，每分钟一条
    fun generateIntradayTrend(basePrice: Double, updatedAt: String): List<TrendPoint> {
        val result = mutableListOf<TrendPoint>()
        var price = basePrice

        // 9:30-11:30 (120分钟) + 13:00-15:00 (120分钟) = 240分钟
        val morningMinutes = 120
        val afternoonMinutes = 120

        // 上午盘
        for (i in 0 until morningMinutes) {
            val minute = 9 * 60 + 30 + i
            val hour = minute / 60
            val min = minute % 60
            price += Random.nextDouble(-0.015, 0.015) * basePrice
            result.add(TrendPoint(
                label = "${hour.toString().padStart(2, '0')}:${min.toString().padStart(2, '0')}",
                price = price,
                volume = Random.nextDouble(1000.0, 10000.0)
            ))
        }

        // 下午盘
        for (i in 0 until afternoonMinutes) {
            val minute = 13 * 60 + i
            val hour = minute / 60
            val min = minute % 60
            price += Random.nextDouble(-0.015, 0.015) * basePrice
            result.add(TrendPoint(
                label = "${hour.toString().padStart(2, '0')}:${min.toString().padStart(2, '0')}",
                price = price,
                volume = Random.nextDouble(1000.0, 10000.0)
            ))
        }

        return result
    }

    // 生成五日走势：240 个点（每日 48 个 5 分钟 K）
    fun generateFiveDayTrend(basePrice: Double): List<TrendPoint> {
        val result = mutableListOf<TrendPoint>()
        var price = basePrice * 0.98 // 从略低于当前价开始

        for (day in 0 until 5) {
            for (interval in 0 until 48) {
                price += Random.nextDouble(-0.01, 0.01) * basePrice
                result.add(TrendPoint(
                    label = "D${day}T${interval}",
                    price = price,
                    volume = Random.nextDouble(1000.0, 8000.0)
                ))
            }
        }

        return result
    }

    // 生成日 K 线：30 条
    fun generateDailyKLines(basePrice: Double, count: Int = 30): List<OhlcPoint> {
        val result = mutableListOf<OhlcPoint>()
        var currentPrice = basePrice * 0.85 // 从30天前价格开始

        for (i in 0 until count) {
            val open = currentPrice
            val close = open + Random.nextDouble(-0.05, 0.05) * open
            val high = maxOf(open, close) + Random.nextDouble(0.0, 0.02) * open
            val low = minOf(open, close) - Random.nextDouble(0.0, 0.02) * open

            result.add(OhlcPoint(
                label = "Day-${count - i}",
                x = i.toFloat(),
                open = open.toFloat(),
                high = high.toFloat(),
                low = low.toFloat(),
                close = close.toFloat(),
                volume = Random.nextLong(100000, 1000000).toFloat()
            ))

            currentPrice = close
        }

        return result.reversed() // 从旧到新排序
    }

    // 生成周 K 线：30 条
    fun generateWeeklyKLines(basePrice: Double, count: Int = 30): List<OhlcPoint> {
        val result = mutableListOf<OhlcPoint>()
        var currentPrice = basePrice * 0.75

        for (i in 0 until count) {
            val open = currentPrice
            val close = open + Random.nextDouble(-0.08, 0.08) * open
            val high = maxOf(open, close) + Random.nextDouble(0.0, 0.03) * open
            val low = minOf(open, close) - Random.nextDouble(0.0, 0.03) * open

            result.add(OhlcPoint(
                label = "Week-${count - i}",
                x = i.toFloat(),
                open = open.toFloat(),
                high = high.toFloat(),
                low = low.toFloat(),
                close = close.toFloat(),
                volume = Random.nextLong(500000, 5000000).toFloat()
            ))

            currentPrice = close
        }

        return result.reversed()
    }

    // 生成月 K 线：30 条
    fun generateMonthlyKLines(basePrice: Double, count: Int = 30): List<OhlcPoint> {
        val result = mutableListOf<OhlcPoint>()
        var currentPrice = basePrice * 0.60

        for (i in 0 until count) {
            val open = currentPrice
            val close = open + Random.nextDouble(-0.12, 0.12) * open
            val high = maxOf(open, close) + Random.nextDouble(0.0, 0.05) * open
            val low = minOf(open, close) - Random.nextDouble(0.0, 0.05) * open

            result.add(OhlcPoint(
                label = "Month-${count - i}",
                x = i.toFloat(),
                open = open.toFloat(),
                high = high.toFloat(),
                low = low.toFloat(),
                close = close.toFloat(),
                volume = Random.nextLong(2000000, 20000000).toFloat()
            ))

            currentPrice = close
        }

        return result.reversed()
    }

    // 生成换手率：0.5%-15%
    fun generateTurnoverRate(): Double {
        return Random.nextDouble(0.5, 15.0)
    }

    // 生成市盈率：10-50
    fun generatePeRatio(): Double {
        return Random.nextDouble(10.0, 50.0)
    }
}
