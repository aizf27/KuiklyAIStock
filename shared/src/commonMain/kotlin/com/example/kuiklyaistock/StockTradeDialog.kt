package com.example.kuiklyaistock

import com.example.kuiklyaistock.model.StockDetail
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.directives.vif
import com.tencent.kuikly.core.views.Input
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View

internal fun ViewContainer<*, *>.StockTradeDialog(
    width: Float,
    height: Float,
    stock: StockDetail,
    side: () -> String,
    quantityText: () -> String,
    errorMessage: () -> String,
    availableQuantity: () -> Int,
    onQuantityChange: (String) -> Unit,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
) {
    val dialogWidth = (width - StockDesignTokens.pageHorizontalPadding * 2f)
        .coerceAtMost(420f)
        .coerceAtLeast(0f)
    View {
        attr {
            absolutePosition(left = 0f, top = 0f)
            size(width, height)
            backgroundColor(Color(0x66000000))
            allCenter()
            padding(left = StockDesignTokens.pageHorizontalPadding, right = StockDesignTokens.pageHorizontalPadding)
        }
        View {
            attr {
                width(dialogWidth)
                backgroundColor(StockDesignTokens.surface)
                borderRadius(StockDesignTokens.sectionRadius)
                padding(StockDesignTokens.cardPadding)
            }
            Text {
                attr {
                    text(if (side() == TradeSides.BUY) "模拟买入" else "模拟卖出")
                    fontSize(19f)
                    fontWeightBold()
                    color(StockDesignTokens.primaryText)
                }
            }
            Text {
                attr {
                    text("${stock.quote.name}  ${stock.quote.code}")
                    fontSize(13f)
                    color(StockDesignTokens.secondaryText)
                    marginTop(6f)
                    marginBottom(18f)
                }
            }
            StockTradeInfoRow("成交价格", "${formatStockPrice(stock.quote.price)} 元")
            vif({ side() == TradeSides.SELL }) {
                StockTradeInfoRow("可卖数量", "${availableQuantity()} 股")
            }
            Text {
                attr {
                    text("交易股数")
                    fontSize(13f)
                    color(StockDesignTokens.secondaryText)
                    marginTop(18f)
                    marginBottom(8f)
                }
            }
            View {
                attr {
                    height(48f)
                    backgroundColor(StockDesignTokens.controlBackground)
                    borderRadius(8f)
                    flexDirectionRow()
                    alignItemsCenter()
                    padding(left = 12f, right = 12f)
                }
                Input {
                    attr {
                        flex(1f)
                        height(48f)
                        text(quantityText())
                        placeholder("请输入 100 的整数倍")
                        placeholderColor(StockDesignTokens.tertiaryText)
                        color(StockDesignTokens.primaryText)
                        fontSize(16f)
                        keyboardTypeNumber()
                        imeNoFullscreen(true)
                    }
                    event {
                        textDidChange(isSyncEdit = true) { params -> onQuantityChange(params.text) }
                        inputReturn { onConfirm() }
                    }
                }
                Text {
                    attr {
                        text("股")
                        fontSize(14f)
                        color(StockDesignTokens.secondaryText)
                    }
                }
            }
            Text {
                attr {
                    text("股数必须是正整数且为 100 的整数倍")
                    fontSize(11f)
                    color(StockDesignTokens.tertiaryText)
                    marginTop(8f)
                }
            }
            vif({ errorMessage().isNotEmpty() }) {
                Text {
                    attr {
                        text(errorMessage())
                        fontSize(12f)
                        color(StockDesignTokens.risk)
                        marginTop(8f)
                    }
                }
            }
            View {
                attr {
                    flexDirectionRow()
                    marginTop(22f)
                }
                View {
                    attr {
                        flex(1f)
                        height(44f)
                        backgroundColor(StockDesignTokens.controlBackground)
                        borderRadius(8f)
                        allCenter()
                        marginRight(12f)
                    }
                    event { click { onCancel() } }
                    Text {
                        attr {
                            text("取消")
                            fontSize(15f)
                            fontWeightMedium()
                            color(StockDesignTokens.secondaryText)
                        }
                    }
                }
                View {
                    attr {
                        flex(1f)
                        height(44f)
                        backgroundColor(if (side() == TradeSides.BUY) StockDesignTokens.rise else StockDesignTokens.fall)
                        borderRadius(8f)
                        allCenter()
                    }
                    event { click { onConfirm() } }
                    Text {
                        attr {
                            text(if (side() == TradeSides.BUY) "确认买入" else "确认卖出")
                            fontSize(15f)
                            fontWeightMedium()
                            color(StockDesignTokens.surface)
                        }
                    }
                }
            }
        }
    }
}

private fun ViewContainer<*, *>.StockTradeInfoRow(label: String, value: String) {
    View {
        attr {
            height(32f)
            flexDirectionRow()
            alignItemsCenter()
        }
        Text {
            attr {
                text(label)
                fontSize(13f)
                color(StockDesignTokens.secondaryText)
                flex(1f)
            }
        }
        Text {
            attr {
                text(value)
                fontSize(15f)
                fontWeightSemiBold()
                color(StockDesignTokens.primaryText)
            }
        }
    }
}
