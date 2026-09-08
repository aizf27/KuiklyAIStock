package com.example.kuiklyaistock

import com.example.kuiklyaistock.model.TrendPoint
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.views.Canvas
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View

internal enum class StockChartPeriod(val title: String) {
    INTRADAY("分时"),
    DAILY("日K"),
}

internal fun ViewContainer<*, *>.StockTrendPeriodSelector(
    selectedPeriod: () -> StockChartPeriod,
    onPeriodSelected: (StockChartPeriod) -> Unit,
) {
    View {
        attr { flexDirectionRow() }
        StockChartPeriod.entries.forEach { period ->
            View {
                attr {
                    width(StockDesignTokens.minimumTouchTarget)
                    height(StockDesignTokens.minimumTouchTarget)
                    allCenter()
                    backgroundColor(
                        if (period == selectedPeriod()) StockDesignTokens.brandBackground else StockDesignTokens.surface
                    )
                }
                event { click { onPeriodSelected(period) } }
                Text {
                    attr {
                        text(period.title)
                        fontSize(13f)
                        fontWeightBold()
                        color(if (period == selectedPeriod()) StockDesignTokens.brand else StockDesignTokens.secondaryText)
                    }
                }
            }
        }
    }
}

internal fun ViewContainer<*, *>.StockTrendChart(
    points: List<TrendPoint>,
    width: Float,
    previousClose: Double,
    change: Double,
) {
    if (points.isEmpty()) {
        View {
            attr {
                height(180f)
                backgroundColor(StockDesignTokens.surface)
                borderRadius(StockDesignTokens.sectionRadius)
                allCenter()
            }
            Text {
                attr {
                    text("暂无走势数据")
                    fontSize(14f)
                    color(StockDesignTokens.secondaryText)
                }
            }
        }
        return
    }

    val prices = points.map { it.price }
    val minPrice = (prices.minOrNull() ?: previousClose).coerceAtMost(previousClose)
    val maxPrice = (prices.maxOrNull() ?: previousClose).coerceAtLeast(previousClose)
    val currentPrice = points.last().price
    val lineColor = stockChangeColor(change)
    val canvasWidth = (width - 24f).coerceAtLeast(1f)
    View {
        attr {
            backgroundColor(StockDesignTokens.surface)
            borderRadius(StockDesignTokens.sectionRadius)
            padding(left = 12f, right = 12f, top = 12f, bottom = 10f)
        }
        View {
            attr { flexDirectionRow() }
            Text {
                attr {
                    text("高 ${formatStockPrice(maxPrice)}")
                    fontSize(11f)
                    color(StockDesignTokens.secondaryText)
                    flex(1f)
                }
            }
            Text {
                attr {
                    text("昨收 ${formatStockPrice(previousClose)}")
                    fontSize(11f)
                    color(StockDesignTokens.tertiaryText)
                    marginRight(10f)
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
            attr { size(canvasWidth, 132f) }
        }) { context, _, _ ->
            val range = (maxPrice - minPrice).takeIf { it > 0 } ?: 1.0
            val chartWidth = (canvasWidth - 16f).coerceAtLeast(1f)
            val chartHeight = 108f
            val baselineY = 12f + chartHeight * (1f - ((previousClose - minPrice) / range).toFloat())
            context.beginPath()
            context.moveTo(8f, baselineY)
            context.lineTo(8f + chartWidth, baselineY)
            context.strokeStyle(StockDesignTokens.tertiaryText)
            context.lineWidth(1f)
            context.stroke()

            context.beginPath()
            if (prices.size == 1) {
                val y = 12f + chartHeight * (1f - ((prices.first() - minPrice) / range).toFloat())
                context.moveTo(8f, y)
                context.lineTo(8f + chartWidth, y)
            } else {
                prices.forEachIndexed { index, price ->
                    val x = 8f + chartWidth * index / (prices.size - 1)
                    val y = 12f + chartHeight * (1f - ((price - minPrice) / range).toFloat())
                    if (index == 0) context.moveTo(x, y) else context.lineTo(x, y)
                }
            }
            context.strokeStyle(lineColor)
            context.lineWidth(3f)
            context.stroke()
        }
        View {
            attr { flexDirectionRow() }
            Text {
                attr {
                    text(points.first().label)
                    fontSize(11f)
                    color(StockDesignTokens.tertiaryText)
                    flex(1f)
                }
            }
            Text {
                attr {
                    text("低 ${formatStockPrice(minPrice)}")
                    fontSize(11f)
                    color(StockDesignTokens.secondaryText)
                    flex(1f)
                }
            }
            Text {
                attr {
                    text(points.last().label)
                    fontSize(11f)
                    color(StockDesignTokens.tertiaryText)
                }
            }
        }
    }
}
