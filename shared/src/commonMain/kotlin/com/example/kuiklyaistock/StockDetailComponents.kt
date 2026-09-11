package com.example.kuiklyaistock

import com.example.kuiklyaistock.model.StockDetail
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.reactive.collection.ObservableList
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View
import com.tencent.kuiklybase.chart.config.StockAuxiliaryIndicator
import com.tencent.kuiklybase.chart.config.StockMainIndicator
import com.tencent.kuiklybase.chart.config.StockPriceDisplayMode
import com.tencent.kuiklybase.chart.config.StockThemePreset
import com.tencent.kuiklybase.chart.model.OhlcPoint
import com.tencent.kuiklybase.chart.stock.StockChart

// 个股详情页 - 顶部信息区
internal fun ViewContainer<*, *>.StockDetailHeader(
    detail: StockDetail,
    width: Float,
    isFavorite: () -> Boolean = { false },
    onFavorite: () -> Unit = {},
) {
    View {
        attr {
            width(width)
            backgroundColor(StockDesignTokens.surface)
            padding(StockDesignTokens.pageHorizontalPadding)
            flexDirectionColumn()
        }
        // 标题行：股票名称 + 代码 + 关注按钮
        View {
            attr {
                width(width - StockDesignTokens.pageHorizontalPadding * 2)
                flexDirectionRow()
                alignItemsCenter()
                justifyContentSpaceBetween()
                marginBottom(12f)
            }
            View {
                attr { flexDirectionRow(); alignItemsCenter() }
                Text {
                    attr {
                        text(detail.quote.name)
                        fontSize(20f)
                        fontWeightBold()
                        color(StockDesignTokens.primaryText)
                        marginRight(8f)
                    }
                }
                Text {
                    attr {
                        text(detail.quote.code)
                        fontSize(14f)
                        color(StockDesignTokens.tertiaryText)
                    }
                }
            }
            // 关注按钮
            View {
                attr {
                    backgroundColor(if (isFavorite()) StockDesignTokens.brandBackground else StockDesignTokens.brand)
                    borderRadius(12f)
                    padding(left = 12f, right = 12f, top = 4f, bottom = 4f)
                }
                event { click { onFavorite() } }
                Text {
                    attr {
                        text(if (isFavorite()) "已关注" else "+ 关注")
                        fontSize(12f)
                        color(if (isFavorite()) StockDesignTokens.brand else StockDesignTokens.surface)
                    }
                }
            }
        }

        // 价格行：当前价 + 涨跌额 + 涨跌幅
        View {
            attr {
                flexDirectionRow()
                alignItemsCenter()
                marginBottom(16f)
            }
            Text {
                attr {
                    text(formatStockPrice(detail.quote.price))
                    fontSize(40f)
                    fontWeightBold()
                    color(stockChangeColor(detail.quote.change))
                    marginRight(12f)
                }
            }
            Text {
                attr {
                    text(formatStockSigned(detail.quote.change))
                    fontSize(18f)
                    fontWeightMedium()
                    color(stockChangeColor(detail.quote.change))
                    marginRight(8f)
                }
            }
            Text {
                attr {
                    text(formatStockPercent(detail.quote.changePercent))
                    fontSize(18f)
                    fontWeightMedium()
                    color(stockChangeColor(detail.quote.change))
                }
            }
        }

        // 关键指标 - 第一行
        View {
            attr {
                flexDirectionRow()
                justifyContentSpaceBetween()
                marginBottom(12f)
            }
            StockDetailMetricItem("今开", formatStockPrice(detail.open))
            StockDetailMetricItem("昨收", formatStockPrice(detail.previousClose))
            StockDetailMetricItem("最高", formatStockPrice(detail.high))
            StockDetailMetricItem("最低", formatStockPrice(detail.low))
        }

        // 关键指标 - 第二行
        View {
            attr {
                flexDirectionRow()
                justifyContentSpaceBetween()
            }
            StockDetailMetricItem("成交量", formatStockVolume(detail.volume))
            StockDetailMetricItem("成交额", formatStockTurnover(detail.turnover))
            StockDetailMetricItem("换手率", "0.42%") // TODO: 从detail获取
            StockDetailMetricItem("市盈率", "5.23") // TODO: 从detail获取
        }
    }
}

// 关键指标单项
private fun ViewContainer<*, *>.StockDetailMetricItem(label: String, value: String) {
    View {
        attr { flex(1f) }
        Text {
            attr {
                text("$label $value")
                fontSize(12f)
                color(StockDesignTokens.secondaryText)
            }
        }
    }
}

// K线图区域
internal fun ViewContainer<*, *>.StockChartSection(
    width: Float,
    candles: () -> ObservableList<OhlcPoint>,
    selectedPeriod: () -> StockChartPeriod,
    onPeriodChange: (StockChartPeriod) -> Unit,
) {
    View {
        attr {
            width(width)
            backgroundColor(StockDesignTokens.surface)
            padding(StockDesignTokens.pageHorizontalPadding)
            marginTop(8f)
        }

        // 时间周期切换Tab
        View {
            attr {
                flexDirectionRow()
                marginBottom(12f)
            }
            StockChartPeriod.entries.forEach { period ->
                View {
                    attr {
                        backgroundColor(if (selectedPeriod() == period) StockDesignTokens.brand else StockDesignTokens.transparent)
                        borderRadius(8f)
                        height(StockDesignTokens.minimumTouchTarget)
                        flex(1f)
                        allCenter()
                    }
                    event { click { onPeriodChange(period) } }
                    Text {
                        attr {
                            text(period.title)
                            fontSize(14f)
                            if (selectedPeriod() == period) fontWeightMedium()
                            color(if (selectedPeriod() == period) StockDesignTokens.surface else StockDesignTokens.secondaryText)
                        }
                    }
                }
            }
        }

        // KuiklyChart 专业股票图，分时用折线，其余周期用 K 线。
        View {
            attr {
                width(width - StockDesignTokens.pageHorizontalPadding * 2)
                height(300f)
                backgroundColor(StockDesignTokens.surface)
                borderRadius(8f)
            }
            StockChart(candles) {
                attr {
                    flex(1f)
                    preset = StockThemePreset.LIGHT
                    priceDisplayMode = if (selectedPeriod() == StockChartPeriod.INTRADAY) {
                        StockPriceDisplayMode.LINE
                    } else {
                        StockPriceDisplayMode.CANDLE
                    }
                    mainIndicator = if (selectedPeriod() == StockChartPeriod.INTRADAY) {
                        StockMainIndicator.BARE_K
                    } else {
                        StockMainIndicator.MA
                    }
                    firstPane {
                        show = selectedPeriod() != StockChartPeriod.INTRADAY
                        indicator = StockAuxiliaryIndicator.VOLUME
                        heightRatio = 0.24f
                    }
                    secondPane { show = false }
                    interaction {
                        enableCrosshair = true
                        enableLongPressInspect = true
                        enableScale = true
                        enablePan = true
                        enableReset = true
                    }
                }
            }
        }
    }
}

// 交易按钮区
internal fun ViewContainer<*, *>.StockTradeButtons(
    width: Float,
    onBuy: () -> Unit = {},
    onSell: () -> Unit = {},
) {
    View {
        attr {
            width(width)
            backgroundColor(StockDesignTokens.surface)
            padding(StockDesignTokens.pageHorizontalPadding)
            flexDirectionRow()
            justifyContentSpaceBetween()
            marginTop(8f)
        }

        // 买入按钮
        View {
            attr {
                flex(1f)
                height(40f)
                backgroundColor(StockDesignTokens.rise)
                borderRadius(8f)
                allCenter()
                marginRight(12f)
            }
            event { click { onBuy() } }
            Text {
                attr {
                    text("买入")
                    fontSize(16f)
                    fontWeightMedium()
                    color(StockDesignTokens.surface)
                }
            }
        }

        // 卖出按钮
        View {
            attr {
                flex(1f)
                height(40f)
                backgroundColor(StockDesignTokens.fall)
                borderRadius(8f)
                allCenter()
            }
            event { click { onSell() } }
            Text {
                attr {
                    text("卖出")
                    fontSize(16f)
                    fontWeightMedium()
                    color(StockDesignTokens.surface)
                }
            }
        }
    }
}
