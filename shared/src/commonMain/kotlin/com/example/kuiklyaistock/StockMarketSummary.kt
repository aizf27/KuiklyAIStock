package com.example.kuiklyaistock

import com.example.kuiklyaistock.model.MarketIndexQuote
import com.example.kuiklyaistock.model.MarketSummary
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View
import kotlin.math.abs
import kotlin.math.roundToLong

internal data class StockMarketSessionUi(
    val status: String,
    val description: String,
    val accentColor: Color,
    val backgroundColor: Color,
)

internal fun stockMarketSession(minuteOfDay: Int): StockMarketSessionUi = when {
    minuteOfDay < 9 * 60 + 30 -> StockMarketSessionUi(
        "未开盘",
        "等待 09:30 开盘",
        StockDesignTokens.brand,
        StockDesignTokens.brandBackground,
    )
    minuteOfDay < 11 * 60 + 30 -> StockMarketSessionUi(
        "交易中",
        "上午连续竞价进行中",
        StockDesignTokens.rise,
        StockDesignTokens.riseBackground,
    )
    minuteOfDay < 13 * 60 -> StockMarketSessionUi(
        "午间休市",
        "13:00 恢复交易",
        StockDesignTokens.risk,
        StockDesignTokens.warningBackground,
    )
    minuteOfDay < 14 * 60 + 45 -> StockMarketSessionUi(
        "交易中",
        "下午连续竞价进行中",
        StockDesignTokens.rise,
        StockDesignTokens.riseBackground,
    )
    minuteOfDay < 15 * 60 -> StockMarketSessionUi(
        "即将收盘",
        "距离 15:00 收盘不足 15 分钟",
        StockDesignTokens.risk,
        StockDesignTokens.warningBackground,
    )
    else -> StockMarketSessionUi(
        "已收盘",
        "今日交易已结束",
        StockDesignTokens.brand,
        StockDesignTokens.brandBackground,
    )
}

private fun stockMarketDataDescription(minuteOfDay: Int): String = when {
    minuteOfDay < 9 * 60 + 30 -> "以下为上一个交易日数据"
    minuteOfDay < 11 * 60 + 30 -> "以下为交易时段 Mock 数据"
    minuteOfDay < 13 * 60 -> "以下为上午收盘 Mock 数据"
    minuteOfDay < 15 * 60 -> "以下为当前交易时段 Mock 数据"
    else -> "以下为上一个交易日数据"
}

private fun stockMarketDataTime(minuteOfDay: Int, clockText: String, sampleUpdatedAt: String): String {
    val isTrading = minuteOfDay in (9 * 60 + 30) until (11 * 60 + 30) ||
        minuteOfDay in (13 * 60) until (15 * 60)
    return if (isTrading && clockText != "--:--") {
        "数据时间 $clockText · Mock 数据"
    } else {
        "样本时间 $sampleUpdatedAt · Mock 数据"
    }
}

// 行情首页使用一个主卡片承载市场状态、指数和涨跌分布。
internal fun ViewContainer<*, *>.StockMarketDashboard(
    summary: MarketSummary?,
    width: Float,
    minuteOfDay: Int,
    clockText: String,
) {
    val session = stockMarketSession(minuteOfDay)
    val contentWidth = (width - StockDesignTokens.cardPadding * 2f).coerceAtLeast(0f)
    val indexGap = 8f
    val indexWidth = ((contentWidth - indexGap * 2f) / 3f).coerceAtLeast(0f)
    View {
        attr {
            width(width)
            backgroundColor(StockDesignTokens.surface)
            borderRadius(StockDesignTokens.sectionRadius)
            padding(StockDesignTokens.cardPadding)
            marginBottom(StockDesignTokens.pageSectionSpacing)
        }
        View {
            attr { width(contentWidth); flexDirectionRow(); alignItemsCenter() }
            View {
                attr {
                    backgroundColor(session.backgroundColor)
                    borderRadius(18f)
                    padding(left = 10f, right = 10f, top = 7f, bottom = 7f)
                }
                Text { attr { text("●  ${session.status}"); fontSize(12f); fontWeightBold(); color(session.accentColor) } }
            }
            Text {
                attr {
                    text("A股大盘")
                    fontSize(12f)
                    fontWeightBold()
                    color(StockDesignTokens.secondaryText)
                    marginLeft(10f)
                    flex(1f)
                }
            }
            if (summary != null) {
                Text {
                    attr {
                        text(formatStockFinancialAmount(summary.sampleNetInflowAmount))
                        fontSize(21f)
                        fontWeightBold()
                        color(stockChangeColor(summary.sampleNetInflowAmount))
                    }
                }
            }
        }
        View {
            attr { width(contentWidth); flexDirectionRow(); alignItemsCenter(); marginTop(10f) }
            Text {
                attr {
                    text(stockMarketDataDescription(minuteOfDay))
                    fontSize(12f)
                    color(StockDesignTokens.secondaryText)
                    flex(1f)
                }
            }
            Text { attr { text("样本资金净流入"); fontSize(11f); color(StockDesignTokens.tertiaryText) } }
        }
        if (summary == null) {
            Text { attr { text("正在同步市场数据..."); fontSize(13f); color(StockDesignTokens.secondaryText); marginTop(18f) } }
        } else {
            View {
                attr { width(contentWidth); flexDirectionRow(); marginTop(18f) }
                summary.indices.take(3).forEachIndexed { index, quote ->
                    MarketDashboardIndexItem(quote, indexWidth)
                    if (index < 2) View { attr { width(indexGap); height(1f) } }
                }
            }
            View {
                attr { width(contentWidth); flexDirectionRow(); alignItemsCenter(); marginTop(18f) }
                Text {
                    attr {
                        text("跌 ${summary.fallingCount}")
                        fontSize(15f)
                        fontWeightBold()
                        color(StockDesignTokens.fall)
                        flex(1f)
                    }
                }
                Text {
                    attr {
                        text("涨 ${summary.risingCount}")
                        fontSize(15f)
                        fontWeightBold()
                        color(StockDesignTokens.rise)
                    }
                }
            }
            val total = summary.totalCount.coerceAtLeast(1)
            val separatorWidth = if (summary.flatCount > 0) 6f else 10f
            val availableTrackWidth = (contentWidth - separatorWidth).coerceAtLeast(0f)
            val fallingWidth = availableTrackWidth * summary.fallingCount / total
            val risingWidth = (availableTrackWidth - fallingWidth).coerceAtLeast(0f)
            View {
                attr {
                    width(contentWidth)
                    height(10f)
                    flexDirectionRow()
                    alignItemsCenter()
                    marginTop(10f)
                }
                View { attr { width(fallingWidth); height(8f); backgroundColor(StockDesignTokens.fall); borderRadius(4f) } }
                View {
                    attr {
                        width(separatorWidth)
                        height(if (summary.flatCount > 0) 10f else 6f)
                        backgroundColor(StockDesignTokens.flat)
                        borderRadius(3f)
                    }
                }
                View { attr { width(risingWidth); height(8f); backgroundColor(StockDesignTokens.rise); borderRadius(4f) } }
            }
            Text {
                attr {
                    text("样本总成交额  ${formatStockTurnover(summary.sampleTurnoverAmount)}")
                    fontSize(15f)
                    fontWeightBold()
                    color(StockDesignTokens.primaryText)
                    marginTop(18f)
                }
            }
            Text {
                attr {
                    text(stockMarketDataTime(minuteOfDay, clockText, summary.updatedAt))
                    fontSize(10f)
                    color(StockDesignTokens.tertiaryText)
                    marginTop(8f)
                }
            }
        }
    }
}

private fun formatStockFinancialAmount(value: Double): String {
    val scaled = (abs(value) / 100_000_000.0 * 100.0).roundToLong()
    val integerPart = scaled / 100
    val decimalPart = scaled % 100
    val decimalText = if (decimalPart < 10) "0$decimalPart" else decimalPart.toString()
    val sign = if (value > 0) "+" else if (value < 0) "-" else ""
    return "$sign$integerPart.${decimalText}亿"
}

private fun ViewContainer<*, *>.MarketDashboardIndexItem(index: MarketIndexQuote, width: Float) {
    val rising = index.change > 0
    View {
        attr {
            width(width)
            height(98f)
            backgroundColor(if (rising) StockDesignTokens.riseBackground else StockDesignTokens.fallBackground)
            borderRadius(10f)
            padding(top = 12f, bottom = 10f)
            alignItemsCenter()
        }
        Text { attr { text(index.name); fontSize(12f); fontWeightBold(); color(StockDesignTokens.primaryText) } }
        Text {
            attr {
                text(formatStockPrice(index.price))
                fontSize(17f)
                fontWeightBold()
                color(stockChangeColor(index.change))
                marginTop(9f)
            }
        }
        Text {
            attr {
                text("${formatStockSigned(index.change)}  ${formatStockPercent(index.changePercent)}")
                fontSize(10f)
                fontWeightBold()
                color(stockChangeColor(index.change))
                marginTop(8f)
            }
        }
    }
}

// 自选页暂时保留原有市场概览，避免本次行情首页重构改变其业务结构。
internal fun ViewContainer<*, *>.StockMarketSummary(summary: MarketSummary?, width: Float) {
    View {
        attr {
            width(width)
            backgroundColor(StockDesignTokens.surface)
            borderRadius(StockDesignTokens.sectionRadius)
            padding(StockDesignTokens.cardPadding)
            marginBottom(StockDesignTokens.sectionSpacing)
        }
        Text { attr { text("市场概览 · 演示数据"); fontSize(16f); fontWeightBold(); color(StockDesignTokens.primaryText) } }
        if (summary == null) {
            Text { attr { text("正在同步市场数据..."); fontSize(13f); color(StockDesignTokens.secondaryText); marginTop(8f) } }
        } else {
            View {
                attr { flexDirectionRow(); marginTop(12f) }
                summary.indices.forEach { index -> MarketIndexItem(index) }
            }
            View {
                attr { flexDirectionRow(); marginTop(16f) }
                MarketSummaryItem("上涨", summary.risingCount.toString(), StockDesignTokens.rise)
                MarketSummaryItem("下跌", summary.fallingCount.toString(), StockDesignTokens.fall)
                MarketSummaryItem("样本成交额", formatStockTurnover(summary.sampleTurnoverAmount), StockDesignTokens.primaryText)
            }
            Text { attr { text("${summary.sessionStatus} · ${summary.updatedAt}"); fontSize(11f); color(StockDesignTokens.tertiaryText); marginTop(10f) } }
        }
    }
}

private fun ViewContainer<*, *>.MarketIndexItem(index: MarketIndexQuote) {
    View {
        attr { flex(1f) }
        Text { attr { text(index.name); fontSize(12f); color(StockDesignTokens.secondaryText) } }
        Text { attr { text(formatStockPrice(index.price)); fontSize(18f); fontWeightBold(); color(StockDesignTokens.primaryText); marginTop(4f) } }
        Text { attr { text(formatStockPercent(index.changePercent)); fontSize(12f); color(stockChangeColor(index.change)); marginTop(3f) } }
    }
}

private fun ViewContainer<*, *>.MarketSummaryItem(label: String, value: String, valueColor: Color) {
    View {
        attr { flex(1f) }
        Text { attr { text(value); fontSize(20f); fontWeightBold(); color(valueColor) } }
        Text { attr { text(label); fontSize(12f); color(StockDesignTokens.secondaryText); marginTop(3f) } }
    }
}
