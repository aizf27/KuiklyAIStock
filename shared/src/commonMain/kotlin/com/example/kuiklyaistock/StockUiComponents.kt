package com.example.kuiklyaistock

import com.example.kuiklyaistock.model.StockQuote
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.directives.vif
import com.tencent.kuikly.core.views.Input
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

// 自选页使用的表头，显示三列数据：最新价、涨跌、涨跌幅
internal fun ViewContainer<*, *>.StockWatchlistQuoteHeader(width: Float) {
    View {
        attr {
            width(width)
            flexDirectionRow()
            padding(left = StockDesignTokens.minimumTouchTarget, right = 4f, top = 12f, bottom = 8f)
            alignItemsCenter()
        }
        Text { attr { text("名称 / 代码"); fontSize(11f); color(StockDesignTokens.tertiaryText); flex(1f) } }
        View {
            attr { width(50f); alignItemsFlexEnd(); marginRight(8f) }
            Text { attr { text("最新价"); fontSize(11f); color(StockDesignTokens.tertiaryText) } }
        }
        View {
            attr { width(50f); alignItemsFlexEnd(); marginRight(8f) }
            Text { attr { text("涨跌"); fontSize(11f); color(StockDesignTokens.tertiaryText) } }
        }
        View {
            attr { width(60f); alignItemsFlexEnd() }
            Text { attr { text("涨跌幅"); fontSize(11f); color(StockDesignTokens.tertiaryText) } }
        }
    }
}

// 行情页使用的表头，显示两列数据：最新价、涨跌幅
internal fun ViewContainer<*, *>.StockQuoteListHeader(width: Float) {
    View {
        attr {
            width(width)
            flexDirectionRow()
            padding(left = StockDesignTokens.minimumTouchTarget, right = 4f, top = 12f, bottom = 8f)
        }
        Text { attr { text("名称 / 代码"); fontSize(11f); color(StockDesignTokens.tertiaryText); flex(1f) } }
        View {
            attr { width(StockDesignTokens.quoteValueColumnWidth); alignItemsFlexEnd() }
            Text { attr { text("最新 / 涨跌幅"); fontSize(11f); color(StockDesignTokens.tertiaryText) } }
        }
    }
}
// 自选页专用的股票行，显示三列数据：最新价、涨跌、涨跌幅
internal fun ViewContainer<*, *>.StockWatchlistQuoteRow(
    quote: StockQuote,
    width: Float,
    favorite: () -> Boolean = { false },
    onFavorite: () -> Unit = {},
    editing: () -> Boolean = { false },
    onRemove: () -> Unit = {},
    onDrag: (String, Float) -> Unit = { _, _ -> },
    onClick: () -> Unit,
) {
    View {
        attr {
            width(width)
            height(StockDesignTokens.quoteRowHeight)
            backgroundColor(StockDesignTokens.surface)
            padding(right = 4f)
            flexDirectionRow()
            alignItemsCenter()
        }
        event {
            click { if (!editing()) onClick() }
        }
        View {
            attr {
                width(StockDesignTokens.minimumTouchTarget)
                height(StockDesignTokens.minimumTouchTarget)
                allCenter()
            }
            event { click { if (editing()) onRemove() else onFavorite() } }
            Text {
                attr {
                    text(if (editing()) "−" else if (favorite()) "★" else "☆")
                    fontSize(if (editing()) 24f else 20f)
                    color(if (editing()) StockDesignTokens.fall else if (favorite()) StockDesignTokens.risk else StockDesignTokens.tertiaryText)
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
                attr {
                    flex(1f)
                    marginRight(8f)
                }
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
            // 最新价列
            View {
                attr {
                    width(50f)
                    alignItemsFlexEnd()
                    marginRight(8f)
                }
                Text {
                    attr {
                        text(formatStockPrice(quote.price))
                        fontSize(14f)
                        fontWeightBold()
                        color(stockChangeColor(quote.change))
                    }
                }
            }
            // 涨跌列（绝对值）
            View {
                attr {
                    width(50f)
                    alignItemsFlexEnd()
                    marginRight(8f)
                }
                Text {
                    attr {
                        text(formatStockSigned(quote.change))
                        fontSize(13f)
                        fontWeightSemiBold()
                        color(stockChangeColor(quote.change))
                    }
                }
            }
            // 涨跌幅列
            View {
                attr {
                    width(60f)
                    alignItemsFlexEnd()
                }
                Text {
                    attr {
                        text(formatStockPercent(quote.changePercent))
                        fontSize(13f)
                        fontWeightSemiBold()
                        color(stockChangeColor(quote.change))
                    }
                }
            }
        }
        vif({ editing() }) {
            View {
                attr {
                    width(36f)
                    height(StockDesignTokens.minimumTouchTarget)
                    allCenter()
                }
                event {
                    pan { params -> onDrag(params.state, params.y) }
                }
                Text { attr { text("≡"); fontSize(22f); color(StockDesignTokens.secondaryText) } }
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

// 行情页使用的股票列表行，星标和整行点击分别交给页面处理。
internal fun ViewContainer<*, *>.StockQuoteRow(
    quote: StockQuote,
    width: Float,
    favorite: () -> Boolean = { false },
    onFavorite: () -> Unit = {},
    onClick: () -> Unit,
) {
    View {
        attr {
            width(width)
            height(StockDesignTokens.quoteRowHeight)
            backgroundColor(StockDesignTokens.surface)
            padding(right = 4f)
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
                    text(if (favorite()) "★" else "☆")
                    fontSize(20f)
                    color(if (favorite()) StockDesignTokens.risk else StockDesignTokens.tertiaryText)
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
                attr {
                    flex(1f)
                    marginRight(8f)
                }
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
                    width(StockDesignTokens.quoteValueColumnWidth)
                    alignItemsFlexEnd()
                    marginRight(3f)
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

internal fun ViewContainer<*, *>.StockRetryBanner(
    message: String,
    onRetry: () -> Unit,
) {
    View {
        attr {
            backgroundColor(StockDesignTokens.warningBackground)
            padding(left = StockDesignTokens.pageHorizontalPadding, right = StockDesignTokens.pageHorizontalPadding, top = 8f, bottom = 8f)
            flexDirectionRow()
            alignItemsCenter()
        }
        Text { attr { text(message); fontSize(12f); color(StockDesignTokens.risk); flex(1f) } }
        View {
            attr { padding(8f) }
            event { click { onRetry() } }
            Text { attr { text("重试"); fontSize(13f); fontWeightBold(); color(StockDesignTokens.brand) } }
        }
    }
}

// 自选页的 Tab 切换组件：自选股 / 持仓股
internal fun ViewContainer<*, *>.StockWatchlistTabs(
    width: Float,
    selectedTab: () -> String,
    onTabSelected: (String) -> Unit,
) {
    val tabs = listOf(
        WatchlistTabs.WATCHLIST to "自选股",
        WatchlistTabs.HOLDINGS to "持仓股",
    )
    View {
        attr {
            width(width)
            height(48f)
            backgroundColor(StockDesignTokens.surface)
            flexDirectionRow()
            justifyContentSpaceAround() // 改为均匀分布，消除右边空格
        }
        tabs.forEach { (tab, label) ->
            View {
                attr {
                    flex(1f)
                    height(48f)
                    alignItemsCenter()
                    justifyContentCenter()
                }
                event { click { onTabSelected(tab) } }
                Text {
                    attr {
                        text(label)
                        fontSize(16f)
                        if (selectedTab() == tab) fontWeightBold() else fontWeightMedium()
                        color(if (selectedTab() == tab) StockDesignTokens.primaryText else StockDesignTokens.secondaryText)
                    }
                }
                View {
                    attr {
                        width(if (selectedTab() == tab) 28f else 0f)
                        height(2f)
                        backgroundColor(if (selectedTab() == tab) StockDesignTokens.brand else StockDesignTokens.transparent)
                        borderRadius(1f)
                        marginTop(7f)
                    }
                }
            }
        }
    }
}

// 自选页的 Tab 常量
internal object WatchlistTabs {
    const val WATCHLIST = "自选股"
    const val HOLDINGS = "持仓股"
}

internal fun ViewContainer<*, *>.StockSearchInput(
    value: String,
    placeholder: String,
    onTextChange: (String) -> Unit,
    onSubmit: (String) -> Unit,
) {
    View {
        attr {
            height(44f)
            backgroundColor(StockDesignTokens.controlBackground)
            borderRadius(12f)
            flexDirectionRow()
            alignItemsCenter()
            padding(left = 14f, right = 14f)
        }
        Text {
            attr {
                text("⌕")
                fontSize(22f)
                color(StockDesignTokens.secondaryText)
                marginRight(12f)
            }
        }
        Input {
            attr {
                flex(1f)
                height(44f)
                text(value)
                placeholder(placeholder)
                placeholderColor(StockDesignTokens.tertiaryText)
                color(StockDesignTokens.primaryText)
                fontSize(14f)
                returnKeyTypeSearch()
                imeNoFullscreen(true)
            }
            event {
                textDidChange(isSyncEdit = true) { params -> onTextChange(params.text) }
                inputReturn { params -> onSubmit(params.text) }
            }
        }
    }
}
