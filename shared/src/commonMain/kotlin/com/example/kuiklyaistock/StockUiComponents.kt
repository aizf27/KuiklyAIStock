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
    if (change > 0) StockDesignTokens.rise else if (change < 0) StockDesignTokens.fall else StockDesignTokens.flat

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

private fun formatStockUnitValue(value: Double): String {
    val roundedTenths = (value * 10).roundToLong()
    val absolute = abs(roundedTenths)
    val sign = if (roundedTenths < 0) "-" else ""
    return if (absolute % 10 == 0L) {
        "$sign${absolute / 10}"
    } else {
        "$sign${absolute / 10}.${absolute % 10}"
    }
}

internal fun formatStockVolume(value: Long): String =
    if (value >= 10_000_000) "${formatStockUnitValue(value / 10_000_000.0)}千万"
    else "${formatStockUnitValue(value / 10_000.0)}万"

internal fun formatStockTurnover(value: Double): String =
    if (value >= 100_000_000) "${formatStockUnitValue(value / 100_000_000.0)}亿"
    else "${formatStockUnitValue(value / 10_000.0)}万"

// 可复用的股票列表行，星标和整行点击分别交给页面处理。
internal fun ViewContainer<*, *>.StockQuoteRow(
    quote: StockQuote,
    favorite: Boolean = false,
    onFavorite: () -> Unit = {},
    onClick: () -> Unit,
) {
    View {
        attr {
            height(StockDesignTokens.quoteRowHeight)
            backgroundColor(StockDesignTokens.surface)
            padding(left = 4f, right = 4f)
            flexDirectionRow()
            alignItemsCenter()
        }
        event {
            click { onClick() }
        }
        View {
            attr {
                width(StockDesignTokens.minimumTouchTarget)
                height(StockDesignTokens.minimumTouchTarget)
                allCenter()
            }
            event { click { onFavorite() } }
            Text {
                attr {
                    text(if (favorite) "★" else "☆")
                    fontSize(20f)
                    color(if (favorite) StockDesignTokens.risk else StockDesignTokens.tertiaryText)
                }
            }
        }
        View {
            attr {
                flex(1f)
                flexDirectionRow()
                alignItemsCenter()
            }
            View {
                attr { flex(1f) }
                Text {
                    attr {
                        text(quote.name)
                        fontSize(16f)
                        fontWeightBold()
                        color(StockDesignTokens.primaryText)
                    }
                }
                Text {
                    attr {
                        text(quote.code)
                        fontSize(11f)
                        color(StockDesignTokens.secondaryText)
                        marginTop(3f)
                    }
                }
            }
            View {
                attr {
                    width(112f)
                    alignItemsFlexEnd()
                }
                Text {
                    attr {
                        text(formatStockPrice(quote.price))
                        fontSize(17f)
                        fontWeightBold()
                        color(StockDesignTokens.primaryText)
                    }
                }
                Text {
                    attr {
                        text("${formatStockSigned(quote.change)}  ${formatStockPercent(quote.changePercent)}")
                        fontSize(12f)
                        color(stockChangeColor(quote.change))
                        marginTop(3f)
                    }
                }
            }
        }
    }
    View {
        attr {
            height(1f)
            backgroundColor(StockDesignTokens.divider)
            marginLeft(StockDesignTokens.minimumTouchTarget)
        }
    }
}

internal fun ViewContainer<*, *>.StockMetric(label: String, value: String) {
    View {
        attr {
            flex(1f)
            marginBottom(16f)
        }
        Text {
            attr {
                text(label)
                fontSize(12f)
                color(StockDesignTokens.secondaryText)
            }
        }
        Text {
            attr {
                text(value)
                fontSize(15f)
                color(StockDesignTokens.primaryText)
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
                color(StockDesignTokens.secondaryText)
            }
        }
        if (actionTitle.isNotEmpty()) {
            Text {
                attr {
                    text(actionTitle)
                    fontSize(14f)
                    fontWeightBold()
                color(StockDesignTokens.brand)
                    marginTop(14f)
                }
                event {
                    click { onAction() }
                }
            }
        }
    }
}

internal fun ViewContainer<*, *>.StockLoadingState(message: String) {
    View {
        attr {
            flex(1f)
            padding(StockDesignTokens.pageHorizontalPadding)
        }
        Text {
            attr {
                text(message)
                fontSize(14f)
                color(StockDesignTokens.secondaryText)
                marginBottom(14f)
            }
        }
        repeat(3) {
            View {
                attr {
                    height(StockDesignTokens.quoteRowHeight)
                    backgroundColor(StockDesignTokens.surface)
                    marginBottom(1f)
                }
            }
        }
    }
}

internal fun ViewContainer<*, *>.StockInlineEmptyState(
    message: String,
    actionTitle: String,
    onAction: () -> Unit,
) {
    View {
        attr {
            padding(top = 28f, bottom = 28f)
            allCenter()
        }
        Text {
            attr {
                text(message)
                fontSize(14f)
                color(StockDesignTokens.secondaryText)
            }
        }
        Text {
            attr {
                text(actionTitle)
                fontSize(14f)
                fontWeightBold()
                color(StockDesignTokens.brand)
                marginTop(12f)
            }
            event { click { onAction() } }
        }
    }
}
