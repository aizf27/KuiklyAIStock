package com.example.kuiklyaistock

import com.example.kuiklyaistock.base.BasePager
import com.example.kuiklyaistock.model.StockDetail
import com.example.kuiklyaistock.repository.MockStockRepository
import com.example.kuiklyaistock.repository.StockRepository
import com.tencent.kuikly.core.annotations.Page
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.ViewBuilder
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject
import com.tencent.kuikly.core.reactive.handler.observable
import com.tencent.kuikly.core.views.Canvas
import com.tencent.kuikly.core.views.Scroller
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View

@Page("stock_detail", supportInLocal = true)
internal class StockDetailPage : BasePager() {
    private val repository: StockRepository = MockStockRepository()
    private var loading by observable(true)
    private var detail by observable<StockDetail?>(null)
    private var errorMessage by observable("")

    override fun created() {
        super.created()
        loadDetail()
    }

    override fun body(): ViewBuilder {
        val ctx = this
        return {
            RouterNavBar {
                attr {
                    title = ctx.detail?.quote?.name ?: "股票详情"
                }
            }
            if (ctx.loading) {
                DetailStateText("正在加载详情...")
            } else if (ctx.errorMessage.isNotEmpty()) {
                DetailStateText(ctx.errorMessage)
            } else {
                ctx.detail?.let { stock ->
                    Scroller {
                        attr {
                            flex(1f)
                            padding(left = 16f, right = 16f, top = 14f, bottom = 24f)
                        }
                        Text {
                            attr {
                                text(stock.quote.name)
                                fontSize(24f)
                                fontWeightBold()
                                color(Color(0xFF111827))
                            }
                        }
                        Text {
                            attr {
                                text("${stock.quote.code}  ·  ${stock.quote.updatedAt}")
                                fontSize(12f)
                                color(Color(0xFF6B7280))
                                marginTop(5f)
                                marginBottom(14f)
                            }
                        }
                        View {
                            attr {
                                backgroundColor(Color.WHITE)
                                borderRadius(8f)
                                padding(16f)
                            }
                            Text {
                                attr {
                                    text(formatPrice(stock.quote.price))
                                    fontSize(32f)
                                    fontWeightBold()
                                    color(Color(0xFF111827))
                                }
                            }
                            Text {
                                attr {
                                    text("${formatSigned(stock.quote.change)}  ${formatPercent(stock.quote.changePercent)}")
                                    fontSize(15f)
                                    color(changeColor(stock.quote.change))
                                    marginTop(6f)
                                }
                            }
                        }
                        Text {
                            attr {
                                text("日内走势")
                                fontSize(17f)
                                fontWeightBold()
                                color(Color(0xFF1F2937))
                                marginTop(20f)
                                marginBottom(8f)
                            }
                        }
                        Canvas({
                            attr {
                                size(ctx.pagerData.pageViewWidth - 32f, 180f)
                            }
                        }) { context, _, _ ->
                            val values = stock.trend.map { it.price }
                            if (values.isNotEmpty()) {
                                val min = values.minOrNull() ?: 0.0
                                val max = values.maxOrNull() ?: 1.0
                                val range = (max - min).takeIf { it > 0 } ?: 1.0
                                val chartWidth = ctx.pagerData.pageViewWidth - 48f
                                val chartHeight = 156f
                                context.beginPath()
                                values.forEachIndexed { index, value ->
                                    val x = 8f + chartWidth * index / (values.size - 1).coerceAtLeast(1)
                                    val y = 12f + chartHeight * (1f - ((value - min) / range).toFloat())
                                    if (index == 0) context.moveTo(x, y) else context.lineTo(x, y)
                                }
                                context.strokeStyle(Color(0xFFE53E3E))
                                context.lineWidth(3f)
                                context.stroke()
                            }
                        }
                        Text {
                            attr {
                                text("基础行情")
                                fontSize(17f)
                                fontWeightBold()
                                color(Color(0xFF1F2937))
                                marginTop(20f)
                                marginBottom(8f)
                            }
                        }
                        View {
                            attr {
                                flexDirectionRow()
                            }
                            Metric("最高", formatPrice(stock.high))
                            Metric("最低", formatPrice(stock.low))
                            Metric("成交量", formatVolume(stock.volume))
                            Metric("成交额", formatTurnover(stock.turnover))
                        }
                    }
                }
            }
        }
    }

    private fun loadDetail() {
        val code = pagerData.params.optString("code").ifEmpty {
            pagerData.params.optString("symbol")
        }
        if (code.isEmpty()) {
            errorMessage = "缺少股票代码，无法加载详情"
        } else {
            detail = repository.getDetail(code)
            if (detail == null) {
                errorMessage = "未找到股票：$code"
            }
        }
        loading = false
    }
}

private fun ViewContainer<*, *>.Metric(label: String, value: String) {
    View {
        attr {
            width(50f)
            marginRight(20f)
            marginBottom(14f)
        }
        Text {
            attr {
                text(label)
                fontSize(12f)
                color(Color(0xFF6B7280))
            }
        }
        Text {
            attr {
                text(value)
                fontSize(15f)
                color(Color(0xFF111827))
                marginTop(4f)
            }
        }
    }
}

private fun ViewContainer<*, *>.DetailStateText(message: String) {
    View {
        attr {
            flex(1f)
            allCenter()
            padding(20f)
        }
        Text {
            attr {
                text(message)
                fontSize(15f)
                color(Color(0xFF6B7280))
            }
        }
    }
}

private fun changeColor(change: Double): Color = if (change >= 0) Color(0xFFE53E3E) else Color(0xFF16A34A)

private fun formatPrice(value: Double): String = value.toString()

private fun formatSigned(value: Double): String = if (value >= 0) "+${formatPrice(value)}" else formatPrice(value)

private fun formatPercent(value: Double): String = if (value >= 0) "+${formatPrice(value)}%" else "${formatPrice(value)}%"

private fun formatVolume(value: Long): String = if (value >= 10_000_000) "${value / 10_000_000.0}千万" else "${value / 10_000.0}万"

private fun formatTurnover(value: Double): String = if (value >= 100_000_000) "${value / 100_000_000.0}亿" else "${value / 10_000.0}万"
