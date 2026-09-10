package com.example.kuiklyaistock

import com.example.kuiklyaistock.base.BasePager
import com.example.kuiklyaistock.base.BridgeModule
import com.example.kuiklyaistock.base.setTimeout
import com.example.kuiklyaistock.model.AiMarketOverview
import com.example.kuiklyaistock.model.AiStockInsight
import com.example.kuiklyaistock.model.MarketSummary
import com.example.kuiklyaistock.model.StockQuote
import com.example.kuiklyaistock.repository.MockStockRepository
import com.example.kuiklyaistock.repository.StockLoadResult
import com.example.kuiklyaistock.repository.StockRepository
import com.example.kuiklyaistock.repository.StockRequestTracker
import com.example.kuiklyaistock.repository.WatchlistStore
import com.tencent.kuikly.core.annotations.Page
import com.tencent.kuikly.core.base.ViewBuilder
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.coroutines.launch
import com.tencent.kuikly.core.directives.velse
import com.tencent.kuikly.core.directives.velseif
import com.tencent.kuikly.core.directives.vif
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject
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
    internal var selectedMarketCategory by observable(StockMarketCategories.MARKET)
    internal var selectedWatchlistTab by observable(WatchlistTabs.WATCHLIST)
    internal var searchText by observable("")
    internal var currentMinuteOfDay by observable(15 * 60)
    internal var currentClockText by observable("--:--")
    internal var currentDateText by observable("")
    private var removeWatchlistObserver: (() -> Unit)? = null
    private val contentRequests = StockRequestTracker()
    private var marketClockActive = false

    override fun created() {
        super.created()
        marketClockActive = true
        removeWatchlistObserver = WatchlistStore.subscribe { favoriteCodes = it }
        refreshMarketClock()
        scheduleMarketClockRefresh()
        loadContent()
    }

    override fun onDestroyPager() {
        marketClockActive = false
        contentRequests.invalidate()
        removeWatchlistObserver?.invoke()
        removeWatchlistObserver = null
        super.onDestroyPager()
    }

    override fun body(): ViewBuilder {
        val ctx = this
        return {
            attr { backgroundColor(StockDesignTokens.pageBackground) }
            StockTopBar(
                ctx,
                {
                    when (ctx.selectedTab) {
                        StockTabs.WATCHLIST -> "自选"
                        StockTabs.AI -> "AI 解读"
                        else -> "行情"
                    }
                },
                false,
            )
            vif({ ctx.selectedTab == StockTabs.MARKET }) {
                StockMarketHomeHeader(ctx)
            }
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
                    StockAiContent(ctx.overview, ctx.insights, ctx.stockContentWidth()) { ctx.openDetail(it) }
                }
                velseif({ ctx.selectedTab == StockTabs.WATCHLIST }) {
                    StockWatchlistContent(ctx)
                }
                velse {
                    StockMarketHomeContent(ctx)
                }
            }
            StockBottomBar(ctx, { ctx.selectedTab }) { tab ->
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
                    // 先写入概览，再展示行情内容，避免首次渲染停留在同步状态。
                    marketSummary = homeResult.data.marketSummary
                    quotes = homeResult.data.quotes
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

    private fun refreshMarketClock() {
        val bridge = acquireModule<BridgeModule>(BridgeModule.MODULE_NAME)
        val timestamp = bridge.currentTimeStamp()
        if (timestamp <= 0L) return
        val timeText = bridge.dateFormatter(timestamp, "HH:mm")
        val timeParts = timeText.split(":")
        val hour = timeParts.getOrNull(0)?.toIntOrNull()
        val minute = timeParts.getOrNull(1)?.toIntOrNull()
        if (hour != null && minute != null && hour in 0..23 && minute in 0..59) {
            currentMinuteOfDay = hour * 60 + minute
            currentClockText = timeText
        }
        bridge.dateFormatter(timestamp, "MM-dd").takeIf { it.isNotEmpty() }?.let { currentDateText = it }
    }

    private fun scheduleMarketClockRefresh() {
        setTimeout(60_000) {
            if (marketClockActive) {
                refreshMarketClock()
                scheduleMarketClockRefresh()
            }
        }
    }

    private fun hasSelectedTabContent(): Boolean = when (selectedTab) {
        StockTabs.AI -> overview != null || insights.isNotEmpty()
        else -> marketSummary != null && quotes.isNotEmpty()
    }

    internal fun filteredMarketQuotes(): List<StockQuote> {
        val query = searchText.trim().lowercase()
        return quotes
            .filter { it.code.length == 6 }
            .filter { query.isEmpty() || it.name.lowercase().contains(query) || it.code.lowercase().contains(query) }
    }

    internal fun updateSearchText(text: String) {
        searchText = text
    }

    internal fun submitSearch(text: String) {
        searchText = text
        acquireModule<BridgeModule>(BridgeModule.MODULE_NAME).closeKeyboard(JSONObject())
        acquireModule<BridgeModule>(BridgeModule.MODULE_NAME).log("stock_home 提交搜索: ${text.trim()}")
    }

    internal fun selectMarketCategory(category: String) {
        if (selectedMarketCategory != category) {
            selectedMarketCategory = category
            acquireModule<BridgeModule>(BridgeModule.MODULE_NAME).log("stock_home 切换行情分类: $category")
        }
    }

    internal fun openDetail(code: String) {
        acquireModule<BridgeModule>(BridgeModule.MODULE_NAME).log("stock_home 跳转详情: $code")
        openStockDetail(code)
    }

    internal fun toggleFavorite(code: String) {
        val favorite = WatchlistStore.toggle(code)
        acquireModule<BridgeModule>(BridgeModule.MODULE_NAME).log("stock_home 自选${if (favorite) "添加" else "移除"}: $code")
    }
}

private fun ViewContainer<*, *>.StockWatchlistContent(page: StockHomePage) {
    Scroller {
        attr { flex(1f) }
        View {
            attr {
                width(page.stockContentWidth())
                alignSelfCenter()
                paddingBottom(StockDesignTokens.pageBottomSpacing)
            }
            // 自选股 / 持仓股 Tab 切换
            StockWatchlistTabs(page.stockContentWidth(), { page.selectedWatchlistTab }) { tab ->
                page.selectedWatchlistTab = tab
                page.acquireModule<BridgeModule>(BridgeModule.MODULE_NAME).log("watchlist_page 切换Tab: $tab")
            }
            View {
                attr {
                    padding(top = 12f)
                }
                Text { attr { text("我的自选"); fontSize(20f); fontWeightBold(); color(StockDesignTokens.primaryText) } }
                Text { attr { text("本地收藏 · 演示数据"); fontSize(12f); color(StockDesignTokens.secondaryText); marginTop(4f); marginBottom(12f) } }
                StockMarketSummary(page.marketSummary, page.stockContentWidth())
                // 使用自选页专用的表头，显示三列数据
                StockWatchlistQuoteHeader(page.stockContentWidth())
                page.quotes.forEach { quote ->
                    vif({ quote.code in page.favoriteCodes }) {
                        // 使用自选页专用的股票行，显示三列数据
                        StockWatchlistQuoteRow(
                            quote,
                            page.stockContentWidth(),
                            { quote.code in page.favoriteCodes },
                            { page.toggleFavorite(quote.code) },
                        ) { page.openDetail(quote.code) }
                    }
                }
                vif({ page.quotes.none { it.code in page.favoriteCodes } }) {
                    StockInlineEmptyState("暂无自选股票", "去行情添加") { page.selectedTab = StockTabs.MARKET }
                }
            }
        }
    }
}
