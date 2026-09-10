package com.example.kuiklyaistock

import com.example.kuiklyaistock.base.BasePager
import com.example.kuiklyaistock.base.BridgeModule
import com.example.kuiklyaistock.model.AiAnalysis
import com.example.kuiklyaistock.model.StockDetail
import com.example.kuiklyaistock.repository.MockStockRepository
import com.example.kuiklyaistock.repository.StockRepository
import com.example.kuiklyaistock.repository.StockLoadResult
import com.example.kuiklyaistock.repository.StockRequestTracker
import com.example.kuiklyaistock.repository.WatchlistStore
import com.tencent.kuikly.core.annotations.Page
import com.tencent.kuikly.core.base.ViewBuilder
import com.tencent.kuikly.core.coroutines.launch
import com.tencent.kuikly.core.directives.vif
import com.tencent.kuikly.core.directives.velseif
import com.tencent.kuikly.core.directives.velse
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
    private val detailRequests = StockRequestTracker()

    override fun created() {
        super.created()
        removeWatchlistObserver = WatchlistStore.subscribe { favoriteCodes = it }
        loadDetail()
    }

    override fun onDestroyPager() {
        detailRequests.invalidate()
        removeWatchlistObserver?.invoke()
        removeWatchlistObserver = null
        super.onDestroyPager()
    }

    override fun body(): ViewBuilder {
        val ctx = this
        return {
            attr { backgroundColor(StockDesignTokens.pageBackground) }
            StockTopBar(ctx, { "股票详情" }, true)
            vif({ ctx.loading && ctx.detail == null }) {
                StockLoadingState("正在加载股票详情...")
            }
            velseif({ ctx.errorMessage.isNotEmpty() && ctx.detail == null }) {
                val canRetry = ctx.hasValidCode()
                StockStateText(ctx.errorMessage, if (canRetry) "重新加载" else "返回行情") {
                    if (canRetry) ctx.loadDetail()
                    else ctx.acquireModule<com.tencent.kuikly.core.module.RouterModule>(com.tencent.kuikly.core.module.RouterModule.MODULE_NAME).closePage()
                }
            }
            velse {
                ctx.detail?.let { stock ->
                    Scroller {
                        attr { flex(1f) }
                        View {
                            attr {
                                width(ctx.stockContentWidth())
                                alignSelfCenter()
                                padding(
                                    top = 12f,
                                    bottom = StockDesignTokens.pageBottomSpacing + ctx.stockBottomInset(),
                                )
                            }
                            StockDetailIdentity(stock, { ctx.isFavorite(stock.quote.code) }) {
                                ctx.toggleFavorite(stock.quote.code)
                            }
                            StockPricePanel(stock, ctx.stockContentWidth())
                            Text {
                                attr {
                                    text("关键行情")
                                    fontSize(17f)
                                    fontWeightBold()
                                    color(StockDesignTokens.primaryText)
                                    marginTop(StockDesignTokens.pageSectionSpacing)
                                    marginBottom(8f)
                                }
                            }
                            StockMetricsPanel(stock, ctx.stockContentWidth())
                            View {
                                attr {
                                    flexDirectionRow()
                                    alignItemsCenter()
                                    marginTop(StockDesignTokens.pageSectionSpacing)
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
                                StockTrendPeriodSelector({ ctx.selectedChartPeriod }) { period ->
                                    ctx.selectChartPeriod(period)
                                }
                            }
                            vif({ ctx.selectedChartPeriod == StockChartPeriod.INTRADAY }) {
                                StockTrendChart(
                                    points = stock.intradayTrend,
                                    width = ctx.stockContentWidth(),
                                    previousClose = stock.previousClose,
                                    change = stock.quote.change,
                                )
                            }
                            velse {
                                StockTrendChart(
                                    points = stock.dailyTrend,
                                    width = ctx.stockContentWidth(),
                                    previousClose = stock.previousClose,
                                    change = stock.quote.change,
                                )
                            }
                            AiAnalysisSection(ctx.analysis)
                        }
                    }
                }
            }
        }
    }
    private fun loadDetail() {
        val code = pagerData.params.optString("code").trim()
        if (code.isEmpty()) {
            loading = false
            errorMessage = "缺少股票代码，无法加载详情"
            acquireModule<BridgeModule>(BridgeModule.MODULE_NAME).log("stock_detail 缺少股票代码")
            return
        }
        val requestId = detailRequests.next()
        loading = true
        errorMessage = ""
        acquireModule<BridgeModule>(BridgeModule.MODULE_NAME).log("stock_detail 开始加载: $code")
        lifecycleScope.launch {
            when (val result = repository.loadDetail(this, code)) {
                is StockLoadResult.Success -> {
                    if (!acceptResult(requestId, code)) return@launch
                    detail = result.data.detail
                    analysis = result.data.analysis
                    errorMessage = ""
                    acquireModule<BridgeModule>(BridgeModule.MODULE_NAME).log("stock_detail 加载成功: $code")
                }
                StockLoadResult.Empty -> {
                    if (!acceptResult(requestId, code)) return@launch
                    errorMessage = "未找到股票：$code"
                    acquireModule<BridgeModule>(BridgeModule.MODULE_NAME).log("stock_detail 未找到股票: $code")
                }
                is StockLoadResult.Failure -> {
                    if (!acceptResult(requestId, code)) return@launch
                    errorMessage = result.message
                    acquireModule<BridgeModule>(BridgeModule.MODULE_NAME).log("stock_detail 加载失败: $code, ${result.message}")
                }
            }
            loading = false
        }
    }

    private fun hasValidCode(): Boolean = pagerData.params.optString("code").trim().isNotEmpty()

    private fun acceptResult(requestId: Int, code: String): Boolean {
        if (detailRequests.isLatest(requestId)) return true
        acquireModule<BridgeModule>(BridgeModule.MODULE_NAME).log("stock_detail 丢弃过期结果: $code, $requestId")
        return false
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
    favorite: () -> Boolean,
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
                    text(if (favorite()) "★" else "☆")
                    fontSize(24f)
                    color(if (favorite()) StockDesignTokens.risk else StockDesignTokens.tertiaryText)
                }
            }
        }
    }
}

private fun com.tencent.kuikly.core.base.ViewContainer<*, *>.StockPricePanel(stock: StockDetail, width: Float) {
    View {
        attr {
            width(width)
            backgroundColor(StockDesignTokens.surface)
            borderRadius(StockDesignTokens.sectionRadius)
            padding(StockDesignTokens.cardPadding)
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
        View {
            attr {
                height(1f)
                backgroundColor(StockDesignTokens.divider)
                marginTop(14f)
            }
        }
    }
}

private fun com.tencent.kuikly.core.base.ViewContainer<*, *>.StockMetricsPanel(stock: StockDetail, width: Float) {
    View {
        attr {
            width(width)
            backgroundColor(StockDesignTokens.surface)
            borderRadius(StockDesignTokens.sectionRadius)
            padding(left = StockDesignTokens.cardPadding, right = StockDesignTokens.cardPadding, top = StockDesignTokens.cardPadding, bottom = 2f)
        }
        View {
            attr { flexDirectionRow() }
            StockMetric("今开", formatStockPrice(stock.open))
            StockMetric("昨收", formatStockPrice(stock.previousClose))
        }
        StockMetricsDivider()
        View {
            attr { flexDirectionRow() }
            StockMetric("最高", formatStockPrice(stock.high))
            StockMetric("最低", formatStockPrice(stock.low))
        }
        StockMetricsDivider()
        View {
            attr { flexDirectionRow() }
            StockMetric("成交量", formatStockVolume(stock.volume))
            StockMetric("成交额", formatStockTurnover(stock.turnover))
        }
    }
}

private fun com.tencent.kuikly.core.base.ViewContainer<*, *>.StockMetricsDivider() {
    View {
        attr {
            height(1f)
            backgroundColor(StockDesignTokens.divider)
            marginBottom(14f)
        }
    }
}
