package com.example.kuiklyaistock

import com.example.kuiklyaistock.model.TrendPoint
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.views.Canvas
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View

internal fun ViewContainer<*, *>.StockTrendChart(
    points: List<TrendPoint>,
    width: Float,
    change: Double,
) {
    if (points.isEmpty()) {
        View {
            attr {
                height(180f)
                backgroundColor(Color.WHITE)
                borderRadius(8f)
                allCenter()
            }
            Text {
                attr {
                    text("暂无走势数据")
                    fontSize(14f)
                    color(Color(0xFF6B7280))
                }
            }
        }
        return
    }

    val prices = points.map { it.price }
    val minPrice = prices.minOrNull() ?: 0.0
    val maxPrice = prices.maxOrNull() ?: minPrice
    val currentPrice = points.last().price
    val lineColor = stockChangeColor(change)
    View {
        attr {
            backgroundColor(Color.WHITE)
            borderRadius(8f)
            padding(left = 12f, right = 12f, top = 12f, bottom = 10f)
        }
        View {
            attr {
                flexDirectionRow()
            }
            Text {
                attr {
                    text("高 ${formatStockPrice(maxPrice)}")
                    fontSize(11f)
                    color(Color(0xFF64748B))
                    flex(1f)
                }
            }
            Text {
                attr {
                    text("当前 ${formatStockPrice(currentPrice)}")
                    fontSize(12f)
                    fontWeightBold()
                    color(lineColor)
                }
            }
        }
        Canvas({
            attr {
                size(width - 24f, 132f)
            }
        }) { context, _, _ ->
            val range = (maxPrice - minPrice).takeIf { it > 0 } ?: 1.0
            val chartWidth = width - 40f
            val chartHeight = 108f
            context.beginPath()
            prices.forEachIndexed { index, price ->
                val x = 8f + chartWidth * index / (prices.size - 1).coerceAtLeast(1)
                val y = 12f + chartHeight * (1f - ((price - minPrice) / range).toFloat())
                if (index == 0) context.moveTo(x, y) else context.lineTo(x, y)
            }
            if (prices.size == 1) {
                context.lineTo(chartWidth, 66f)
            }
            context.strokeStyle(lineColor)
            context.lineWidth(3f)
            context.stroke()
        }
        View {
            attr {
                flexDirectionRow()
            }
            Text {
                attr {
                    text(points.first().time)
                    fontSize(11f)
                    color(Color(0xFF94A3B8))
                    flex(1f)
                }
            }
            Text {
                attr {
                    text("低 ${formatStockPrice(minPrice)}")
                    fontSize(11f)
                    color(Color(0xFF64748B))
                    flex(1f)
                }
            }
            Text {
                attr {
                    text(points.last().time)
                    fontSize(11f)
                    color(Color(0xFF94A3B8))
                }
            }
        }
    }
}
