package com.example.kuiklyaistock

import com.example.kuiklyaistock.model.StockQuote
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View
import kotlin.math.abs
import kotlin.math.roundToLong

// 统一股票涨跌颜色，避免不同页面出现相反的颜色规则。
internal fun stockChangeColor(change: Double): Color =
    if (change > 0) Color(0xFFE53E3E) else if (change < 0) Color(0xFF16A34A) else Color(0xFF6B7280)

internal fun formatStockPrice(value: Double): String {
    val scaled = (value * 100).roundToLong()
    val absolute = abs(scaled)
    val decimals = absolute % 100
    val sign = if (scaled < 0) "-" else ""
    return "$sign${absolute / 100}.${if (decimals < 10) "0$decimals" else decimals}"
}

internal fun formatStockSigned(value: Double): String =
    if (value > 0) "+${formatStockPrice(value)}" else formatStockPrice(value)

internal fun formatStockPercent(value: Double): String =
    if (value > 0) "+${formatStockPrice(value)}%" else "${formatStockPrice(value)}%"

internal fun formatStockVolume(value: Long): String =
    if (value >= 10_000_000) "${value / 10_000_000.0}千万" else "${value / 10_000.0}万"

internal fun formatStockTurnover(value: Double): String =
    if (value >= 100_000_000) "${value / 100_000_000.0}亿" else "${value / 10_000.0}万"

// 可复用的股票列表行，星标和整行点击分别交给页面处理。
internal fun ViewContainer<*, *>.StockQuoteRow(
    quote: StockQuote,
    favorite: Boolean = false,
    onFavorite: () -> Unit = {},
    onClick: () -> Unit,
) {
    View {
        attr {
            backgroundColor(Color.WHITE)
            borderRadius(8f)
            padding(left = 14f, right = 14f, top = 14f, bottom = 14f)
            marginBottom(10f)
        }
        event {
            click { onClick() }
        }
        View {
            attr {
                flexDirectionRow()
                alignItemsCenter()
            }
            Text {
                attr {
                    text(if (favorite) "★" else "☆")
                    fontSize(22f)
                    color(if (favorite) Color(0xFFF59E0B) else Color(0xFF9CA3AF))
                    marginRight(10f)
                }
                event {
                    click { onFavorite() }
                }
            }
            View {
                attr {
                    flex(1f)
                }
                Text {
                    attr {
                        text(quote.name)
                        fontSize(17f)
                        fontWeightBold()
                        color(Color(0xFF111827))
                    }
                }
                Text {
                    attr {
                        text(quote.code)
                        fontSize(12f)
                        color(Color(0xFF6B7280))
                        marginTop(4f)
                    }
                }
            }
            Text {
                attr {
                    text(if (quote.isRising) "↗" else if (quote.isFalling) "↘" else "→")
                    fontSize(20f)
                    fontWeightBold()
                    color(stockChangeColor(quote.change))
                    marginRight(10f)
                }
            }
            View {
                attr {
                    width(92f)
                    alignItemsFlexEnd()
                }
                Text {
                    attr {
                        text(formatStockPrice(quote.price))
                        fontSize(18f)
                        fontWeightBold()
                        color(Color(0xFF111827))
                    }
                }
                Text {
                    attr {
                        text("${formatStockSigned(quote.change)}  ${formatStockPercent(quote.changePercent)}")
                        fontSize(12f)
                        color(stockChangeColor(quote.change))
                        marginTop(4f)
                    }
                }
            }
        }
    }
}

internal fun ViewContainer<*, *>.StockMetric(label: String, value: String) {
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

internal fun ViewContainer<*, *>.StockStateText(
    message: String,
    actionTitle: String = "",
    onAction: () -> Unit = {},
) {
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
        if (actionTitle.isNotEmpty()) {
            Text {
                attr {
                    text(actionTitle)
                    fontSize(14f)
                    fontWeightBold()
                    color(Color(0xFF2563EB))
                    marginTop(14f)
                }
                event {
                    click { onAction() }
                }
            }
        }
    }
}
