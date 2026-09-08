package com.example.kuiklyaistock

import com.example.kuiklyaistock.model.MarketSummary
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View

internal fun ViewContainer<*, *>.StockMarketSummary(summary: MarketSummary?) {
    View {
        attr {
            backgroundColor(StockDesignTokens.surface)
            borderRadius(StockDesignTokens.sectionRadius)
            padding(left = 14f, right = 14f, top = 14f, bottom = 14f)
            marginBottom(16f)
        }
        Text {
            attr {
                text("市场概览 · 演示数据")
                fontSize(16f)
                fontWeightBold()
                color(StockDesignTokens.primaryText)
            }
        }
        if (summary == null) {
            Text {
                attr {
                    text("正在同步市场数据...")
                    fontSize(13f)
                    color(StockDesignTokens.secondaryText)
                    marginTop(8f)
                }
            }
        } else {
            View {
                attr {
                    flexDirectionRow()
                    marginTop(12f)
                }
                summary.indices.forEach { index ->
                    MarketIndexItem(index)
                }
            }
            View {
                attr {
                    flexDirectionRow()
                    marginTop(16f)
                }
                MarketSummaryItem("上涨", summary.risingCount.toString(), StockDesignTokens.rise)
                MarketSummaryItem("下跌", summary.fallingCount.toString(), StockDesignTokens.fall)
                MarketSummaryItem("成交额", formatStockTurnover(summary.turnover), StockDesignTokens.primaryText)
            }
            Text {
                attr {
                    text("${summary.sessionStatus} · ${summary.updatedAt}")
                    fontSize(11f)
                    color(StockDesignTokens.tertiaryText)
                    marginTop(10f)
                }
            }
        }
    }
}

private fun ViewContainer<*, *>.MarketIndexItem(index: com.example.kuiklyaistock.model.MarketIndexQuote) {
    View {
        attr { flex(1f) }
        Text {
            attr {
                text(index.name)
                fontSize(12f)
                color(StockDesignTokens.secondaryText)
            }
        }
        Text {
            attr {
                text(formatStockPrice(index.price))
                fontSize(18f)
                fontWeightBold()
                color(StockDesignTokens.primaryText)
                marginTop(4f)
            }
        }
        Text {
            attr {
                text(formatStockPercent(index.changePercent))
                fontSize(12f)
                color(stockChangeColor(index.change))
                marginTop(3f)
            }
        }
    }
}

private fun ViewContainer<*, *>.MarketSummaryItem(label: String, value: String, valueColor: Color) {
    View {
        attr {
            flex(1f)
        }
        Text {
            attr {
                text(value)
                fontSize(20f)
                fontWeightBold()
                color(valueColor)
            }
        }
        Text {
            attr {
                text(label)
                fontSize(12f)
                color(StockDesignTokens.secondaryText)
                marginTop(3f)
            }
        }
    }
}
