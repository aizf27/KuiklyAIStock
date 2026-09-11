package com.example.kuiklyaistock

import com.example.kuiklyaistock.base.BasePager
import com.example.kuiklyaistock.base.BridgeModule
import com.example.kuiklyaistock.base.setTimeout
import com.example.kuiklyaistock.model.AiMarketOverview
import com.example.kuiklyaistock.model.AiStockInsight
import com.example.kuiklyaistock.model.MarketSummary
import com.example.kuiklyaistock.model.StockPosition
import com.example.kuiklyaistock.model.StockQuote
import com.example.kuiklyaistock.repository.MockStockRepository
import com.example.kuiklyaistock.repository.PortfolioPersistence
import com.example.kuiklyaistock.repository.PortfolioStore
import com.example.kuiklyaistock.repository.StockLoadResult
import com.example.kuiklyaistock.repository.StockRepository
import com.example.kuiklyaistock.repository.StockRequestTracker
import com.example.kuiklyaistock.repository.searchStockQuotes
import com.tencent.kuikly.core.annotations.Page
import com.tencent.kuikly.core.base.Color
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
    internal var favoriteCodes by observable(emptyList<String>())
    internal var positions by observable(emptyList<StockPosition>())
    internal var watchlistEditing by observable(false)
    internal var selectedTab by observable(StockTabs.MARKET)
    internal var selectedMarketCategory by observable(StockMarketCategories.MARKET)
    internal var selectedWatchlistTab by observable(WatchlistTabs.WATCHLIST)
    internal var searchText by observable("")
    internal var currentMinuteOfDay by observable(15 * 60)
    internal var currentClockText by observable("--:--")
    internal var currentDateText by observable("")
    private var removePortfolioObserver: (() -> Unit)? = null
    private var draggingFavoriteCode = ""
    private var dragStartY = 0f
    private var dragCurrentIndex = -1
    private val contentRequests = StockRequestTracker()
    private var marketClockActive = false

    override fun created() {
        super.created()
        marketClockActive = true
        val bridge = acquireModule<BridgeModule>(BridgeModule.MODULE_NAME)
        PortfolioPersistence.ensureLoaded(bridge)
        removePortfolioObserver = PortfolioStore.subscribe { state ->
            favoriteCodes = state.favoriteCodes
            positions = state.positions
        }
        refreshMarketClock()
        scheduleMarketClockRefresh()
        loadContent()
    }

    override fun onDestroyPager() {
        marketClockActive = false
        contentRequests.invalidate()
        removePortfolioObserver?.invoke()
        removePortfolioObserver = null
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
                    if (ctx.selectedTab == StockTabs.WATCHLIST) ctx.updateWatchlistEditing(false)
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

    internal fun filteredMarketQuotes(): List<StockQuote> = searchStockQuotes(quotes, searchText)

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
        val bridge = acquireModule<BridgeModule>(BridgeModule.MODULE_NAME)
        val favorite = PortfolioStore.toggleFavorite(code)
        PortfolioPersistence.save(bridge)
        bridge.log("stock_home 自选${if (favorite) "添加" else "移除"}: $code")
    }

    internal fun favoriteQuotes(): List<StockQuote> {
        val quotesByCode = quotes.associateBy { it.code }
        return favoriteCodes.mapNotNull(quotesByCode::get)
    }

    internal fun searchResults(): List<StockQuote> = filteredMarketQuotes()

    internal fun holdingRows(): List<Pair<StockPosition, StockQuote>> {
        val quotesByCode = quotes.associateBy { it.code }
        return positions.mapNotNull { position -> quotesByCode[position.code]?.let { position to it } }
    }

    internal fun updateWatchlistEditing(editing: Boolean) {
        watchlistEditing = editing
        if (!editing) finishFavoriteDrag()
        acquireModule<BridgeModule>(BridgeModule.MODULE_NAME).log("watchlist_page ${if (editing) "进入" else "退出"}编辑模式")
    }

    internal fun removeFavorite(code: String) {
        if (PortfolioStore.removeFavorite(code)) {
            if (draggingFavoriteCode == code) finishFavoriteDrag()
            PortfolioPersistence.save(acquireModule(BridgeModule.MODULE_NAME))
        }
    }

    internal fun dragFavorite(code: String, state: String, y: Float) {
        when (state) {
            "start" -> {
                if (!watchlistEditing || favoriteCodes.size < 2) return
                draggingFavoriteCode = code
                dragStartY = y
                dragCurrentIndex = favoriteCodes.indexOf(code)
            }
            "move" -> {
                if (draggingFavoriteCode != code || dragCurrentIndex < 0 || favoriteCodes.size < 2) return
                val offsetRows = ((y - dragStartY) / StockDesignTokens.quoteRowHeight).toInt()
                val target = (dragCurrentIndex + offsetRows).coerceIn(0, favoriteCodes.lastIndex)
                if (target != dragCurrentIndex && PortfolioStore.moveFavorite(dragCurrentIndex, target)) {
                    dragCurrentIndex = target
                    dragStartY = y
                }
            }
            "end", "cancel" -> finishFavoriteDrag()
        }
    }

    private fun finishFavoriteDrag() {
        if (draggingFavoriteCode.isNotEmpty()) {
            PortfolioPersistence.save(acquireModule(BridgeModule.MODULE_NAME))
            acquireModule<BridgeModule>(BridgeModule.MODULE_NAME).log("watchlist_page 排序已保存")
        }
        draggingFavoriteCode = ""
        dragCurrentIndex = -1
        dragStartY = 0f
    }
}

private fun ViewContainer<*, *>.StockWatchlistContent(page: StockHomePage) {
    View {
        attr { flex(1f) }
        StockWatchlistTopArea(page)
        StockWatchlistTabs(page.stockContentWidth(), { page.selectedWatchlistTab }) { tab ->
            if (page.selectedWatchlistTab != tab) {
                page.updateWatchlistEditing(false)
                page.selectedWatchlistTab = tab
                page.acquireModule<BridgeModule>(BridgeModule.MODULE_NAME).log("watchlist_page 切换Tab: $tab")
            }
        }
        Scroller {
            attr { flex(1f) }
            View {
                attr {
                    width(page.stockContentWidth())
                    alignSelfCenter()
                    paddingBottom(StockDesignTokens.pageBottomSpacing)
                }
                vif({ page.searchText.trim().isNotEmpty() }) {
                    StockSearchResults(page)
                }
                velse {
                    vif({ page.selectedWatchlistTab == WatchlistTabs.WATCHLIST }) {
                        vif({ page.favoriteQuotes().isNotEmpty() }) {
                            StockWatchlistToolbar(page)
                            StockWatchlistQuoteList(page)
                            StockWatchlistFooter()
                        }
                        velse { StockWatchlistEmptyState(page) }
                    }
                    velse {
                        vif({ page.holdingRows().isNotEmpty() }) { StockHoldingsList(page) }
                        velse { StockHoldingsEmptyState() }
                    }
                }
            }
        }
    }
}

private fun ViewContainer<*, *>.StockWatchlistTopArea(page: StockHomePage) {
    View {
        attr {
            backgroundColor(StockDesignTokens.surface)
            padding(left = StockDesignTokens.pageHorizontalPadding, right = StockDesignTokens.pageHorizontalPadding, top = 12f, bottom = 12f)
        }
        StockSearchInput(
            page.searchText,
            "搜索股票名称或六位代码",
            { page.updateSearchText(it) },
            { page.submitSearch(it) },
        )
    }
}

private fun ViewContainer<*, *>.StockWatchlistToolbar(page: StockHomePage) {
    View {
        attr {
            backgroundColor(StockDesignTokens.surface)
            borderRadius(12f)
            height(52f)
            flexDirectionRow()
            alignItemsCenter()
            padding(left = 16f, right = 8f)
            marginTop(12f)
        }
        Text {
            attr {
                text("自选股票  ${page.favoriteQuotes().size}")
                fontSize(14f)
                fontWeightBold()
                color(StockDesignTokens.primaryText)
                flex(1f)
            }
        }
        Text {
            attr {
                text(if (page.watchlistEditing) "拖动右侧手柄排序" else "手动排序")
                fontSize(12f)
                color(StockDesignTokens.secondaryText)
                marginRight(8f)
            }
        }
        View {
            attr {
                width(56f)
                height(44f)
                allCenter()
            }
            event { click { page.updateWatchlistEditing(!page.watchlistEditing) } }
            Text {
                attr {
                    text(if (page.watchlistEditing) "完成" else "编辑")
                    fontSize(13f)
                    fontWeightBold()
                    color(StockDesignTokens.brand)
                }
            }
        }
    }
}

private fun ViewContainer<*, *>.StockWatchlistQuoteList(page: StockHomePage) {
    View {
        attr {
            backgroundColor(StockDesignTokens.surface)
            borderRadius(12f)
            marginTop(8f)
        }
        StockWatchlistQuoteHeader(page.stockContentWidth())
        View { attr { height(1f); backgroundColor(StockDesignTokens.divider); marginLeft(16f) } }
        page.favoriteQuotes().forEach { quote ->
            StockWatchlistQuoteRow(
                quote = quote,
                width = page.stockContentWidth(),
                favorite = { quote.code in page.favoriteCodes },
                onFavorite = { page.toggleFavorite(quote.code) },
                editing = { page.watchlistEditing },
                onRemove = { page.removeFavorite(quote.code) },
                onDrag = { state, y -> page.dragFavorite(quote.code, state, y) },
                onClick = { if (!page.watchlistEditing) page.openDetail(quote.code) },
            )
        }
    }
}

private fun ViewContainer<*, *>.StockSearchResults(page: StockHomePage) {
    val results = page.searchResults()
    vif({ results.isNotEmpty() }) {
        View {
            attr {
                backgroundColor(StockDesignTokens.surface)
                borderRadius(12f)
                marginTop(12f)
            }
            View {
                attr { height(44f); padding(left = 16f, right = 16f); flexDirectionRow(); alignItemsCenter() }
                Text { attr { text("搜索结果  ${results.size}"); fontSize(14f); fontWeightBold(); color(StockDesignTokens.primaryText); flex(1f) } }
                Text { attr { text("可收藏或进入详情"); fontSize(11f); color(StockDesignTokens.tertiaryText) } }
            }
            results.forEach { quote ->
                StockWatchlistQuoteRow(
                    quote = quote,
                    width = page.stockContentWidth(),
                    favorite = { quote.code in page.favoriteCodes },
                    onFavorite = { page.toggleFavorite(quote.code) },
                    onClick = { page.openDetail(quote.code) },
                )
            }
        }
    }
    velse {
        StockSimpleEmptyState("未找到匹配股票", "请尝试股票名称或六位代码")
    }
}

private fun ViewContainer<*, *>.StockHoldingsList(page: StockHomePage) {
    View {
        attr { marginTop(12f) }
        page.holdingRows().forEach { (position, quote) ->
            val marketValue = quote.price * position.quantity
            val profit = (quote.price - position.averageCost) * position.quantity
            View {
                attr {
                    backgroundColor(StockDesignTokens.surface)
                    borderRadius(12f)
                    padding(16f)
                    marginBottom(10f)
                }
                event { click { page.openDetail(quote.code) } }
                View {
                    attr { flexDirectionRow(); alignItemsCenter() }
                    View {
                        attr { flex(1f) }
                        Text { attr { text(quote.name); fontSize(16f); fontWeightBold(); color(StockDesignTokens.primaryText) } }
                        Text { attr { text(quote.code); fontSize(11f); color(StockDesignTokens.secondaryText); marginTop(3f) } }
                    }
                    Text { attr { text(formatStockPrice(quote.price)); fontSize(17f); fontWeightBold(); color(stockChangeColor(quote.change)) } }
                }
                View {
                    attr { flexDirectionRow(); marginTop(14f) }
                    StockHoldingMetric("持仓", "${position.quantity}股")
                    StockHoldingMetric("平均成本", formatStockPrice(position.averageCost))
                    StockHoldingMetric("市值", formatStockPrice(marketValue))
                    StockHoldingMetric("浮动盈亏", formatStockSigned(profit), stockChangeColor(profit))
                }
            }
        }
    }
}

private fun ViewContainer<*, *>.StockHoldingMetric(label: String, value: String, valueColor: Color = StockDesignTokens.primaryText) {
    View {
        attr { flex(1f) }
        Text { attr { text(label); fontSize(10f); color(StockDesignTokens.tertiaryText) } }
        Text { attr { text(value); fontSize(12f); fontWeightSemiBold(); color(valueColor); marginTop(4f) } }
    }
}

private fun ViewContainer<*, *>.StockWatchlistFooter() {
    Text {
        attr {
            text("行情更新时间  2026-09-10 15:00  ·  演示数据")
            fontSize(11f)
            color(StockDesignTokens.tertiaryText)
            marginTop(12f)
        }
    }
}

private fun ViewContainer<*, *>.StockWatchlistEmptyState(page: StockHomePage) {
    StockSimpleEmptyState("暂无自选股票", "去行情页收藏，或直接使用上方搜索框")
    View {
        attr {
            width(136f)
            height(44f)
            backgroundColor(StockDesignTokens.brand)
            borderRadius(12f)
            allCenter()
            alignSelfCenter()
            marginTop(16f)
        }
        event { click { page.selectedTab = StockTabs.MARKET } }
        Text { attr { text("去行情添加  →"); fontSize(14f); fontWeightMedium(); color(Color.WHITE) } }
    }
}

private fun ViewContainer<*, *>.StockHoldingsEmptyState() {
    StockSimpleEmptyState("暂无持仓股票", "可在个股详情页模拟买入")
}

private fun ViewContainer<*, *>.StockSimpleEmptyState(title: String, message: String) {
    View {
        attr {
            backgroundColor(StockDesignTokens.surface)
            borderRadius(12f)
            padding(top = 48f, bottom = 48f)
            marginTop(48f)
            allCenter()
        }
        Text { attr { text("☆"); fontSize(42f); color(StockDesignTokens.brand) } }
        Text { attr { text(title); fontSize(18f); fontWeightBold(); color(StockDesignTokens.primaryText); marginTop(16f) } }
        Text { attr { text(message); fontSize(14f); color(StockDesignTokens.secondaryText); marginTop(10f) } }
    }
}
