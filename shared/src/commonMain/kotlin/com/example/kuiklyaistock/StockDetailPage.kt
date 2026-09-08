package com.example.kuiklyaistock

import com.example.kuiklyaistock.base.BasePager
import com.example.kuiklyaistock.base.BridgeModule
import com.example.kuiklyaistock.model.AiAnalysis
import com.example.kuiklyaistock.model.StockDetail
import com.example.kuiklyaistock.repository.MockStockRepository
import com.example.kuiklyaistock.repository.StockRepository
import com.example.kuiklyaistock.repository.WatchlistStore
import com.tencent.kuikly.core.annotations.Page
import com.tencent.kuikly.core.base.ViewBuilder
import com.tencent.kuikly.core.reactive.handler.observable
import com.tencent.kuikly.core.views.Scroller
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View

@Page("stock_detail", supportInLocal = true)
internal class StockDetailPage : BasePager() {
    private val repository: StockRepository = MockStockRepository()
    private var loading by observable(true)
    private var detail by observable<StockDetail?>(null)
    private var analysis by observable<AiAnalysis?>(null)
    private var errorMessage by observable("")
    private var favoriteCodes by observable(emptySet<String>())
    private var selectedChartPeriod by observable(StockChartPeriod.INTRADAY)
    private var removeWatchlistObserver: (() -> Unit)? = null

    override fun created() {
        super.created()
        removeWatchlistObserver = WatchlistStore.subscribe { favoriteCodes = it }
        loadDetail()
    }

    override fun onDestroyPager() {
        removeWatchlistObserver?.invoke()
        removeWatchlistObserver = null
        super.onDestroyPager()
    }

    override fun body(): ViewBuilder {
        val ctx = this
        return {
            attr { backgroundColor(StockDesignTokens.pageBackground) }
            StockTopBar(ctx, "股票详情", true)
            if (ctx.loading) {
                StockLoadingState("正在加载股票详情...")
            } else if (ctx.errorMessage.isNotEmpty()) {
                StockStateText(ctx.errorMessage, "返回行情") {
                    ctx.acquireModule<com.tencent.kuikly.core.module.RouterModule>(com.tencent.kuikly.core.module.RouterModule.MODULE_NAME).closePage()
                }
            } else {
                ctx.detail?.let { stock ->
                    Scroller {
                        attr {
                            flex(1f)
                            padding(
                                left = StockDesignTokens.pageHorizontalPadding,
                                right = StockDesignTokens.pageHorizontalPadding,
                                top = 12f,
                                bottom = 24f,
                            )
                        }
                        StockDetailIdentity(stock, ctx.isFavorite(stock.quote.code)) {
                            ctx.toggleFavorite(stock.quote.code)
                        }
                        StockPricePanel(stock)
                        Text {
                            attr {
                                text("关键行情")
                                fontSize(17f)
                                fontWeightBold()
                                color(StockDesignTokens.primaryText)
                                marginTop(20f)
                                marginBottom(8f)
                            }
                        }
                        StockMetricsPanel(stock)
                        View {
                            attr {
                                flexDirectionRow()
                                alignItemsCenter()
                                marginTop(20f)
                                marginBottom(8f)
                            }
                            Text {
                                attr {
                                    text("走势")
                                    fontSize(17f)
                                    fontWeightBold()
                                    color(StockDesignTokens.primaryText)
                                    flex(1f)
                                }
                            }
                            StockTrendPeriodSelector(ctx.selectedChartPeriod) { period ->
                                ctx.selectChartPeriod(period)
                            }
                        }
                        StockTrendChart(
                            points = if (ctx.selectedChartPeriod == StockChartPeriod.INTRADAY) {
                                stock.intradayTrend
                            } else {
                                stock.dailyTrend
                            },
                            width = ctx.pagerData.pageViewWidth - StockDesignTokens.pageHorizontalPadding * 2f,
                            previousClose = stock.previousClose,
                            change = stock.quote.change,
                        )
                        AiAnalysisSection(ctx.analysis)
                    }
                }
            }
        }
    }

    private fun loadDetail() {
        val code = pagerData.params.optString("code").trim()
        if (code.isEmpty()) {
            errorMessage = "缺少股票代码，无法加载详情"
            acquireModule<BridgeModule>(BridgeModule.MODULE_NAME).log("stock_detail 缺少股票代码")
        } else {
            detail = repository.getDetail(code)
            if (detail == null) {
                errorMessage = "未找到股票：$code"
                acquireModule<BridgeModule>(BridgeModule.MODULE_NAME).log("stock_detail 未找到股票: $code")
            } else {
                analysis = repository.getAiAnalysis(code)
                acquireModule<BridgeModule>(BridgeModule.MODULE_NAME).log("stock_detail 加载成功: $code")
            }
        }
        loading = false
    }

    private fun selectChartPeriod(period: StockChartPeriod) {
        selectedChartPeriod = period
        acquireModule<BridgeModule>(BridgeModule.MODULE_NAME).log("stock_detail 切换走势周期: ${period.title}")
    }

    private fun isFavorite(code: String): Boolean {
        return code in favoriteCodes
    }

    private fun toggleFavorite(code: String) {
        val favorite = WatchlistStore.toggle(code)
        acquireModule<BridgeModule>(BridgeModule.MODULE_NAME).log(
            "stock_detail 自选${if (favorite) "添加" else "移除"}: $code"
        )
    }
}

private fun com.tencent.kuikly.core.base.ViewContainer<*, *>.StockDetailIdentity(
    stock: StockDetail,
    favorite: Boolean,
    onFavorite: () -> Unit,
) {
    View {
        attr {
            flexDirectionRow()
            alignItemsCenter()
            marginBottom(12f)
        }
        View {
            attr { flex(1f) }
            Text {
                attr {
                    text(stock.quote.name)
                    fontSize(22f)
                    fontWeightBold()
                    color(StockDesignTokens.primaryText)
                }
            }
            Text {
                attr {
                    text("${stock.quote.code} · ${stock.quote.updatedAt} · 演示数据")
                    fontSize(12f)
                    color(StockDesignTokens.secondaryText)
                    marginTop(4f)
                }
            }
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
                    fontSize(24f)
                    color(if (favorite) StockDesignTokens.risk else StockDesignTokens.tertiaryText)
                }
            }
        }
    }
}

private fun com.tencent.kuikly.core.base.ViewContainer<*, *>.StockPricePanel(stock: StockDetail) {
    View {
        attr {
            backgroundColor(StockDesignTokens.surface)
            borderRadius(StockDesignTokens.sectionRadius)
            padding(16f)
        }
        View {
            attr {
                flexDirectionRow()
                alignItemsFlexEnd()
            }
            Text {
                attr {
                    text(formatStockPrice(stock.quote.price))
                    fontSize(32f)
                    fontWeightBold()
                    color(StockDesignTokens.primaryText)
                    flex(1f)
                }
            }
            Text {
                attr {
                    text("${formatStockSigned(stock.quote.change)}  ${formatStockPercent(stock.quote.changePercent)}")
                    fontSize(15f)
                    fontWeightBold()
                    color(stockChangeColor(stock.quote.change))
                }
            }
        }
        Text {
            attr {
                text("${stock.quote.updatedAt} · 演示数据")
                fontSize(11f)
                color(StockDesignTokens.tertiaryText)
                marginTop(6f)
            }
        }
    }
}

private fun com.tencent.kuikly.core.base.ViewContainer<*, *>.StockMetricsPanel(stock: StockDetail) {
    View {
        attr {
            backgroundColor(StockDesignTokens.surface)
            borderRadius(StockDesignTokens.sectionRadius)
            padding(left = 14f, right = 14f, top = 14f, bottom = 2f)
        }
        View {
            attr { flexDirectionRow() }
            StockMetric("今开", formatStockPrice(stock.open))
            StockMetric("昨收", formatStockPrice(stock.previousClose))
        }
        View {
            attr { flexDirectionRow() }
            StockMetric("最高", formatStockPrice(stock.high))
            StockMetric("最低", formatStockPrice(stock.low))
        }
        View {
            attr { flexDirectionRow() }
            StockMetric("成交量", formatStockVolume(stock.volume))
            StockMetric("成交额", formatStockTurnover(stock.turnover))
        }
    }
}
