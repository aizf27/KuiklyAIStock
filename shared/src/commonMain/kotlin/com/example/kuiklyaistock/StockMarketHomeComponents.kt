package com.example.kuiklyaistock

import com.example.kuiklyaistock.model.StockQuote
import com.example.kuiklyaistock.model.displayName
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.directives.velse
import com.tencent.kuikly.core.directives.vif
import com.tencent.kuikly.core.views.Input
import com.tencent.kuikly.core.views.Scroller
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View

internal object StockMarketCategories {
    const val MARKET = "大盘"
    const val SECTOR = "板块"
    const val STOCK = "个股"
    val all = listOf(MARKET, SECTOR, STOCK)
}

// 行情页固定顶部，只保留搜索和大盘分类切换。
internal fun ViewContainer<*, *>.StockMarketHomeHeader(page: StockHomePage) {
    View {
        attr {
            width(page.pagerData.pageViewWidth)
            backgroundColor(StockDesignTokens.pageBackground)
            padding(left = StockDesignTokens.pageHorizontalPadding, right = StockDesignTokens.pageHorizontalPadding, top = 12f)
        }
        View {
            attr {
                width(page.stockContentWidth())
                flexDirectionRow()
                alignItemsCenter()
            }
            View { attr { flex(1f) }; StockSearchBar(page) }
            View {
                attr {
                    width(44f)
                    height(44f)
                    allCenter()
                    marginLeft(8f)
                }
                event { click { page.manualRefresh() } }
                Text { attr { text("↻"); fontSize(22f); color(StockDesignTokens.brand) } }
            }
        }
        StockCategoryTabs(
            labels = StockMarketCategories.all,
            selected = { page.selectedMarketCategory },
            width = page.stockContentWidth(),
            onSelected = { page.selectMarketCategory(it) },
        )
    }
}

internal fun ViewContainer<*, *>.StockMarketHomeContent(page: StockHomePage) {
    Scroller {
        attr { flex(1f) }
        View {
            attr {
                width(page.stockContentWidth())
                alignSelfCenter()
                padding(top = StockDesignTokens.sectionSpacing, bottom = StockDesignTokens.pageBottomSpacing)
            }
            vif({ page.selectedMarketCategory != StockMarketCategories.MARKET }) {
                StockMarketEmptyState(
                    page.stockContentWidth(),
                    "${page.selectedMarketCategory}暂未接入真实数据",
                    "当前版本先提供大盘行情",
                )
            }
            velse {
                StockMarketDashboard(
                    summary = page.marketSummary,
                    width = page.stockContentWidth(),
                    minuteOfDay = page.currentMinuteOfDay,
                    sourceLabel = page.dataSource.displayName(),
                    quoteTime = page.quoteTime,
                    expired = page.quoteExpired,
                    missingCount = page.missingQuoteCodes.size,
                )
                StockTimelyInformationCard(
                    width = page.stockContentWidth(),
                    minuteOfDay = page.currentMinuteOfDay,
                    dateText = page.currentDateText,
                )
                StockRankingCard(page, page.filteredMarketQuotes(), page.stockContentWidth())
            }
        }
    }
}

private fun ViewContainer<*, *>.StockSearchBar(page: StockHomePage) {
    View {
        attr {
            flex(1f)
            height(44f)
            backgroundColor(StockDesignTokens.controlBackground)
            borderRadius(StockDesignTokens.sectionRadius)
            flexDirectionRow()
            alignItemsCenter()
        }
        View {
            attr {
                width(36f)
                height(44f)
                allCenter()
            }
            Text { attr { text("⌕"); fontSize(22f); color(StockDesignTokens.secondaryText) } }
        }
        Input {
            attr {
                flex(1f)
                height(44f)
                text(page.searchText)
                placeholder("搜索股票名称或代码")
                placeholderColor(StockDesignTokens.tertiaryText)
                color(StockDesignTokens.primaryText)
                fontSize(14f)
                returnKeyTypeSearch()
                imeNoFullscreen(true)
            }
            event {
                textDidChange(isSyncEdit = true) { params -> page.updateSearchText(params.text) }
                inputReturn { params -> page.submitSearch(params.text) }
            }
        }
        vif({ page.searchText.isNotEmpty() }) {
            View {
                attr {
                    width(StockDesignTokens.minimumTouchTarget)
                    height(44f)
                    allCenter()
                }
                event { click { page.updateSearchText("") } }
                Text { attr { text("×"); fontSize(20f); color(StockDesignTokens.secondaryText) } }
            }
        }
    }
}

private fun ViewContainer<*, *>.StockCategoryTabs(
    labels: List<String>,
    selected: () -> String,
    width: Float,
    onSelected: (String) -> Unit,
) {
    View {
        attr {
            width(width)
            height(48f)
            flexDirectionRow()
        }
        labels.forEach { label ->
            View {
                attr {
                    flex(1f)
                    height(48f)
                    alignItemsCenter()
                    justifyContentCenter()
                }
                event { click { onSelected(label) } }
                Text {
                    attr {
                        text(label)
                        fontSize(14f)
                        fontWeightBold()
                        color(if (selected() == label) StockDesignTokens.primaryText else StockDesignTokens.secondaryText)
                    }
                }
                View {
                    attr {
                        width(if (selected() == label) 28f else 0f)
                        height(2f)
                        backgroundColor(if (selected() == label) StockDesignTokens.brand else StockDesignTokens.transparent)
                        borderRadius(1f)
                        marginTop(7f)
                    }
                }
            }
        }
    }
}

private fun ViewContainer<*, *>.StockMarketEmptyState(width: Float, title: String, message: String) {
    View {
        attr {
            width(width)
            backgroundColor(StockDesignTokens.surface)
            borderRadius(StockDesignTokens.sectionRadius)
            padding(top = 44f, bottom = 44f, left = StockDesignTokens.cardPadding, right = StockDesignTokens.cardPadding)
            allCenter()
        }
        Text { attr { text(title); fontSize(17f); fontWeightBold(); color(StockDesignTokens.primaryText) } }
        Text { attr { text(message); fontSize(13f); color(StockDesignTokens.secondaryText); marginTop(8f) } }
    }
}

private data class StockTimelyInformation(
    val title: String,
    val headline: String,
    val summary: String,
    val publishTime: String,
)

private fun stockTimelyInformation(minuteOfDay: Int): StockTimelyInformation = when {
    minuteOfDay < 11 * 60 + 30 -> StockTimelyInformation(
        title = "盘前必读",
        headline = "关注政策预期与高景气方向的开盘表现",
        summary = "隔夜市场震荡，A股样本关注科技、新能源和消费权重的量价变化。",
        publishTime = "08:20",
    )
    minuteOfDay < 15 * 60 -> StockTimelyInformation(
        title = "午盘点评",
        headline = "早盘指数分化，热点轮动速度加快",
        summary = "样本股票涨跌互现，午后重点观察成交量能否放大以及权重板块承接。",
        publishTime = "11:45",
    )
    minuteOfDay < 19 * 60 -> StockTimelyInformation(
        title = "收盘点评",
        headline = "三大指数涨跌不一，热点轮动加快",
        summary = "市场情绪保持活跃，成交额温和放大。题材轮动加快，注意短线波动风险。",
        publishTime = "15:08",
    )
    else -> StockTimelyInformation(
        title = "晚间复盘",
        headline = "复盘今日强弱线索，关注次日量价确认",
        summary = "晚间信息仅为 Mock 展示，次日需结合公告、政策和实际开盘数据重新判断。",
        publishTime = "19:30",
    )
}

private fun ViewContainer<*, *>.StockTimelyInformationCard(
    width: Float,
    minuteOfDay: Int,
    dateText: String,
) {
    val information = stockTimelyInformation(minuteOfDay)
    val shellPadding = 1f
    val horizontalPadding = 14f
    val cardWidth = width - shellPadding * 2f
    val contentWidth = cardWidth - horizontalPadding * 2f
    View {
        attr {
            width(width)
            backgroundColor(StockDesignTokens.informationCardShadow)
            borderRadius(StockDesignTokens.sectionRadius + 1f)
            padding(left = shellPadding, right = shellPadding, top = shellPadding, bottom = 3f)
            marginBottom(20f)
        }
        View {
            attr {
                width(cardWidth)
                backgroundColor(StockDesignTokens.informationCardBackground)
                borderRadius(StockDesignTokens.sectionRadius)
                padding(left = horizontalPadding, right = horizontalPadding, top = 12f, bottom = 12f)
            }
            View {
                attr { width(contentWidth); flexDirectionRow(); alignItemsCenter() }
                View {
                    attr {
                        backgroundColor(StockDesignTokens.riseBackground)
                        borderRadius(14f)
                        padding(left = 10f, right = 10f, top = 5f, bottom = 5f)
                    }
                    Text { attr { text(information.title); fontSize(11f); fontWeightBold(); color(StockDesignTokens.rise) } }
                }
                Text {
                    attr {
                        text("${if (dateText.isEmpty()) "今日" else dateText}  ${information.publishTime}")
                        fontSize(10f)
                        color(StockDesignTokens.tertiaryText)
                        marginLeft(8f)
                        flex(1f)
                        textAlignRight()
                    }
                }
                Text { attr { text("›"); fontSize(21f); color(StockDesignTokens.tertiaryText); marginLeft(3f) } }
            }
            Text {
                attr {
                    text(information.headline)
                    fontSize(15f)
                    fontWeightBold()
                    color(StockDesignTokens.primaryText)
                    marginTop(10f)
                }
            }
            Text {
                attr {
                    text(information.summary)
                    fontSize(12f)
                    color(StockDesignTokens.secondaryText)
                    marginTop(6f)
                }
            }
            Text {
                attr {
                    text("Mock 资讯  ·  仅作界面演示")
                    fontSize(10f)
                    color(StockDesignTokens.tertiaryText)
                    marginTop(9f)
                }
            }
        }
    }
}

private fun ViewContainer<*, *>.StockRankingCard(
    page: StockHomePage,
    quotes: List<StockQuote>,
    width: Float,
) {
    View {
        attr {
            width(width)
            flexDirectionRow()
            alignItemsFlexEnd()
            marginBottom(10f)
        }
        Text { attr { text("股票排行"); fontSize(20f); fontWeightBold(); color(StockDesignTokens.primaryText); flex(1f) } }
        Text { attr { text("按涨幅排序  ·  Mock"); fontSize(11f); color(StockDesignTokens.tertiaryText) } }
    }
    View {
        attr {
            width(width)
            backgroundColor(StockDesignTokens.surface)
            borderRadius(StockDesignTokens.sectionRadius)
            padding(left = StockDesignTokens.cardPadding, right = StockDesignTokens.cardPadding)
        }
        val contentWidth = (width - StockDesignTokens.cardPadding * 2f).coerceAtLeast(0f)
        StockRankingHeader(contentWidth)
        if (quotes.isEmpty()) {
            View {
                attr { width(contentWidth); padding(top = 32f, bottom = 32f); allCenter() }
                Text {
                    attr {
                        text(if (page.searchText.isEmpty()) "暂无排行数据" else "未找到匹配的股票")
                        fontSize(14f)
                        color(StockDesignTokens.secondaryText)
                    }
                }
                vif({ page.searchText.isNotEmpty() }) {
                    Text {
                        attr { text("清除搜索"); fontSize(14f); fontWeightBold(); color(StockDesignTokens.brand); marginTop(12f) }
                        event { click { page.updateSearchText("") } }
                    }
                }
            }
        } else {
            quotes.forEachIndexed { index, quote ->
                StockRankingRow(
                    quote = quote,
                    width = contentWidth,
                    favorite = { quote.code in page.favoriteCodes },
                    onFavorite = { page.toggleFavorite(quote.code) },
                    onClick = { page.openDetail(quote.code) },
                )
                if (index < quotes.lastIndex) {
                    View { attr { width(contentWidth); height(1f); backgroundColor(StockDesignTokens.divider) } }
                }
            }
        }
    }
}

private fun ViewContainer<*, *>.StockRankingHeader(width: Float) {
    View {
        attr { width(width); height(42f); flexDirectionRow(); alignItemsCenter() }
        Text { attr { text("名称 / 代码"); fontSize(11f); color(StockDesignTokens.tertiaryText); flex(1f) } }
        Text { attr { text("最新价"); width(62f); fontSize(11f); color(StockDesignTokens.tertiaryText); textAlignRight() } }
        Text { attr { text("涨跌幅"); width(66f); fontSize(11f); color(StockDesignTokens.tertiaryText); textAlignRight() } }
        View { attr { width(32f); height(1f) } }
    }
    View { attr { width(width); height(1f); backgroundColor(StockDesignTokens.divider) } }
}

private fun ViewContainer<*, *>.StockRankingRow(
    quote: StockQuote,
    width: Float,
    favorite: () -> Boolean,
    onFavorite: () -> Unit,
    onClick: () -> Unit,
) {
    val changeColor = stockChangeColor(quote.change)
    View {
        attr {
            width(width)
            height(74f)
            flexDirectionRow()
            alignItemsCenter()
        }
        event { click { onClick() } }
        View {
            attr { flex(1f); marginRight(6f) }
            Text { attr { text(quote.name); fontSize(15f); fontWeightBold(); color(StockDesignTokens.primaryText) } }
            Text { attr { text(quote.code); fontSize(11f); color(StockDesignTokens.tertiaryText); marginTop(5f) } }
        }
        Text {
            attr {
                text(formatStockPrice(quote.price))
                width(62f)
                fontSize(15f)
                fontWeightBold()
                color(changeColor)
                textAlignRight()
            }
        }
        Text {
            attr {
                text(formatStockPercent(quote.changePercent))
                width(66f)
                fontSize(15f)
                fontWeightBold()
                color(changeColor)
                textAlignRight()
            }
        }
        View {
            attr { width(32f); height(StockDesignTokens.minimumTouchTarget); allCenter() }
            event { click { onFavorite() } }
            Text {
                attr {
                    text(if (favorite()) "★" else "☆")
                    fontSize(20f)
                    color(if (favorite()) StockDesignTokens.risk else StockDesignTokens.tertiaryText)
                }
            }
        }
    }
}
