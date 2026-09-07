package com.example.kuiklyaistock

import com.example.kuiklyaistock.model.MarketSummary
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View

internal fun ViewContainer<*, *>.StockMarketSummary(summary: MarketSummary?) {
    View {
        attr {
            backgroundColor(Color.WHITE)
            borderRadius(8f)
            padding(left = 14f, right = 14f, top = 14f, bottom = 14f)
            marginBottom(16f)
        }
        Text {
            attr {
                text("市场概览")
                fontSize(16f)
                fontWeightBold()
                color(Color(0xFF1F2937))
            }
        }
        if (summary == null) {
            Text {
                attr {
                    text("正在同步市场数据...")
                    fontSize(13f)
                    color(Color(0xFF6B7280))
                    marginTop(8f)
                }
            }
        } else {
            View {
                attr {
                    flexDirectionRow()
                    marginTop(12f)
                }
                MarketSummaryItem("股票", summary.totalCount.toString(), Color(0xFF334155))
                MarketSummaryItem("上涨", summary.risingCount.toString(), Color(0xFFE53E3E))
                MarketSummaryItem("下跌", summary.fallingCount.toString(), Color(0xFF16A34A))
            }
            Text {
                attr {
                    text("更新时间 ${summary.updatedAt}")
                    fontSize(11f)
                    color(Color(0xFF9CA3AF))
                    marginTop(10f)
                }
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
                color(Color(0xFF6B7280))
                marginTop(3f)
            }
        }
    }
}
