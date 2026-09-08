package com.example.kuiklyaistock

import com.example.kuiklyaistock.base.BasePager
import com.example.kuiklyaistock.base.BridgeModule
import com.example.kuiklyaistock.model.AiMarketOverview
import com.example.kuiklyaistock.model.AiStockInsight
import com.example.kuiklyaistock.model.MarketSummary
import com.example.kuiklyaistock.model.StockQuote
import com.example.kuiklyaistock.repository.MockStockRepository
import com.example.kuiklyaistock.repository.StockRepository
import com.example.kuiklyaistock.repository.StockLoadResult
import com.example.kuiklyaistock.repository.StockRequestTracker
import com.example.kuiklyaistock.repository.WatchlistStore
import com.tencent.kuikly.core.annotations.Page
import com.tencent.kuikly.core.base.ViewBuilder
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.coroutines.launch
import com.tencent.kuikly.core.directives.vif
import com.tencent.kuikly.core.directives.velseif
import com.tencent.kuikly.core.directives.velse
import com.tencent.kuikly.core.reactive.handler.observable
import com.tencent.kuikly.core.views.Scroller
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View

@Page("stock_home", supportInLocal = true)
internal class StockHomePage : BasePager() {
    private val repository: StockRepository = MockStockRepository()
    private var loading by observable(true)
    internal var quotes by observable(emptyList<StockQuote>())
    internal var marketSummary by observable<MarketSummary?>(null)
    private var overview by observable<AiMarketOverview?>(null)
    private var insights by observable(emptyList<AiStockInsight>())
    private var errorMessage by observable("")
    internal var favoriteCodes by observable(emptySet<String>())
    internal var selectedTab by observable(StockTabs.MARKET)
    private var removeWatchlistObserver: (() -> Unit)? = null
    private val contentRequests = StockRequestTracker()

    override fun created() {
        super.created()
        removeWatchlistObserver = WatchlistStore.subscribe { favoriteCodes = it }
        loadContent()
    }

    override fun onDestroyPager() {
        contentRequests.invalidate()
        removeWatchlistObserver?.invoke()
        removeWatchlistObserver = null
        super.onDestroyPager()
    }

    override fun body(): ViewBuilder {
        val ctx = this
        return {
            attr { backgroundColor(StockDesignTokens.pageBackground) }
            StockTopBar(ctx, { if (ctx.selectedTab == StockTabs.AI) "AI 解读" else "AI 股票" }, false)
            vif({ ctx.loading && !ctx.hasSelectedTabContent() }) {
                StockLoadingState("正在加载演示数据...")
            }
            velseif({ ctx.errorMessage.isNotEmpty() && !ctx.hasSelectedTabContent() }) {
                StockStateText(ctx.errorMessage, "重新加载") { ctx.loadContent() }
            }
            velse {
                vif({ ctx.errorMessage.isNotEmpty() }) {
                    StockRetryBanner(ctx.errorMessage) { ctx.loadContent() }
                }
                vif({ ctx.selectedTab == StockTabs.AI }) {
                    StockAiContent(ctx.overview, ctx.insights) { ctx.openDetail(it) }
                }
                velse {
                    StockMarketContent(ctx)
                }
            }
            StockBottomBar({ ctx.selectedTab }) { tab ->
                if (ctx.selectedTab != tab) {
                    ctx.selectedTab = tab
                    ctx.acquireModule<BridgeModule>(BridgeModule.MODULE_NAME).log("stock_home 切换根Tab: $tab")
                }
            }
        }
    }

    private fun loadContent() {
        val requestId = contentRequests.next()
        loading = true
        errorMessage = ""
        acquireModule<BridgeModule>(BridgeModule.MODULE_NAME).log("stock_home 开始加载: $requestId")
        lifecycleScope.launch {
            val homeResult = repository.loadHome(this)
            if (!contentRequests.isLatest(requestId)) {
                acquireModule<BridgeModule>(BridgeModule.MODULE_NAME).log("stock_home 丢弃过期行情结果: $requestId")
                return@launch
            }
            when (homeResult) {
                is StockLoadResult.Success -> {
                    quotes = homeResult.data.quotes
                    marketSummary = homeResult.data.marketSummary
                }
                StockLoadResult.Empty -> errorMessage = "暂无行情数据"
                is StockLoadResult.Failure -> errorMessage = homeResult.message
            }

            val aiResult = repository.loadAi(this)
            if (!contentRequests.isLatest(requestId)) {
                acquireModule<BridgeModule>(BridgeModule.MODULE_NAME).log("stock_home 丢弃过期AI结果: $requestId")
                return@launch
            }
            when (aiResult) {
                is StockLoadResult.Success -> {
                    overview = aiResult.data.overview
                    insights = aiResult.data.insights
                }
                StockLoadResult.Empty -> if (errorMessage.isEmpty()) errorMessage = "暂无 AI 解读数据"
                is StockLoadResult.Failure -> if (errorMessage.isEmpty()) errorMessage = aiResult.message
            }
            loading = false
            if (errorMessage.isEmpty()) {
                acquireModule<BridgeModule>(BridgeModule.MODULE_NAME).log("stock_home 加载成功: $requestId")
            } else {
                acquireModule<BridgeModule>(BridgeModule.MODULE_NAME).log("stock_home 加载失败: $errorMessage")
            }
        }
    }

    private fun hasSelectedTabContent(): Boolean =
        if (selectedTab == StockTabs.AI) overview != null || insights.isNotEmpty() else quotes.isNotEmpty()

    internal fun openDetail(code: String) {
        acquireModule<BridgeModule>(BridgeModule.MODULE_NAME).log("stock_home 跳转详情: $code")
        openStockDetail(code)
    }

    internal fun toggleFavorite(code: String) {
        val favorite = WatchlistStore.toggle(code)
        acquireModule<BridgeModule>(BridgeModule.MODULE_NAME).log("stock_home 自选${if (favorite) "添加" else "移除"}: $code")
    }
}

private fun ViewContainer<*, *>.StockMarketContent(page: StockHomePage) {
    Scroller {
        attr { flex(1f); padding(left = StockDesignTokens.pageHorizontalPadding, right = StockDesignTokens.pageHorizontalPadding, top = 12f, bottom = 20f) }
        Text { attr { text(if (page.selectedTab == StockTabs.WATCHLIST) "我的自选" else "全部行情"); fontSize(20f); fontWeightBold(); color(StockDesignTokens.primaryText) } }
        Text { attr { text("${page.marketSummary?.sessionStatus ?: "数据同步中"} · 演示数据"); fontSize(12f); color(StockDesignTokens.secondaryText); marginTop(4f); marginBottom(12f) } }
        StockMarketSummary(page.marketSummary)
        StockQuoteListHeader()
        page.quotes.forEach { quote ->
            vif({ page.selectedTab != StockTabs.WATCHLIST || quote.code in page.favoriteCodes }) {
                StockQuoteRow(quote, { quote.code in page.favoriteCodes }, { page.toggleFavorite(quote.code) }) { page.openDetail(quote.code) }
            }
        }
        vif({ page.selectedTab == StockTabs.WATCHLIST && page.quotes.none { it.code in page.favoriteCodes } }) {
            StockInlineEmptyState("暂无自选股票", "去行情添加") { page.selectedTab = StockTabs.MARKET }
        }
    }
}

private fun com.tencent.kuikly.core.base.ViewContainer<*, *>.StockQuoteListHeader() {
    View {
        attr { flexDirectionRow(); padding(left = StockDesignTokens.minimumTouchTarget, right = 4f, bottom = 6f) }
        Text { attr { text("名称 / 代码"); fontSize(11f); color(StockDesignTokens.tertiaryText); flex(1f) } }
        View { attr { width(112f); alignItemsFlexEnd() }; Text { attr { text("最新 / 涨跌幅"); fontSize(11f); color(StockDesignTokens.tertiaryText) } } }
    }
}
