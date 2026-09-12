package com.example.kuiklyaistock

import com.example.kuiklyaistock.base.BasePager
import com.example.kuiklyaistock.base.BridgeModule
import com.example.kuiklyaistock.base.setTimeout
import com.example.kuiklyaistock.model.AiMarketOverview
import com.example.kuiklyaistock.model.AiStockInsight
import com.example.kuiklyaistock.model.MarketSummary
import com.example.kuiklyaistock.model.StockPosition
import com.example.kuiklyaistock.model.StockDataSource
import com.example.kuiklyaistock.model.displayName
import com.example.kuiklyaistock.model.StockQuote
import com.example.kuiklyaistock.repository.TencentStockRepository
import com.example.kuiklyaistock.repository.PortfolioPersistence
import com.example.kuiklyaistock.repository.PortfolioStore
import com.example.kuiklyaistock.repository.StockHomeData
import com.example.kuiklyaistock.repository.StockLoadResult
import com.example.kuiklyaistock.repository.StockRepository
import com.example.kuiklyaistock.repository.StockRequestTracker
import com.example.kuiklyaistock.repository.searchStockQuotes
import com.example.kuiklyaistock.repository.DatabaseFactory
import com.example.kuiklyaistock.repository.SqlDelightStockDatabaseRepository
import com.tencent.kuikly.core.annotations.Page
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.ViewBuilder
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.coroutines.launch
import com.tencent.kuikly.core.directives.velse
import com.tencent.kuikly.core.directives.velseif
import com.tencent.kuikly.core.directives.vfor
import com.tencent.kuikly.core.directives.vif
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject
import com.tencent.kuikly.core.reactive.collection.ObservableList
import com.tencent.kuikly.core.reactive.handler.observable
import com.tencent.kuikly.core.reactive.handler.observableList
import com.tencent.kuikly.core.views.Scroller
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View

@Page("stock_home", supportInLocal = true)
internal class StockHomePage : BasePager() {
    private val repository: StockRepository by lazy {
        val bridge = acquireModule<BridgeModule>(BridgeModule.MODULE_NAME)
        val databaseRepo = SqlDelightStockDatabaseRepository(
            DatabaseFactory.getDatabaseFromPager(this),
            nowMillis = { bridge.currentTimeStamp() }
        )
        TencentStockRepository(
            pager = this,
            nowMillis = { bridge.currentTimeStamp() },
            logger = { bridge.log(it) },
            databaseRepo = databaseRepo,
        )
    }
    private var loading by observable(true)
    internal var quotes by observable(emptyList<StockQuote>())
    internal var marketSummary by observable<MarketSummary?>(null)
    private var overview by observable<AiMarketOverview?>(null)
    private var insights by observable(emptyList<AiStockInsight>())
    private var errorMessage by observable("")
    internal var dataSource by observable(StockDataSource.REMOTE)
    internal var quoteTime by observable("")
    internal var quoteExpired by observable(false)
    internal var missingQuoteCodes by observable(emptyList<String>())
    internal var favoriteCodes by observable(emptyList<String>())
    internal var positions by observable(emptyList<StockPosition>())
    internal var watchlistEditing by observable(false)
    internal var selectedTab by observable(StockTabs.MARKET)
    internal var selectedMarketCategory by observable(StockMarketCategories.MARKET)
    internal var selectedWatchlistTab by observable(WatchlistTabs.WATCHLIST)
    internal var searchText by observable("")
    private var searchQuoteResults by observableList<StockQuote>()
    private var favoriteQuoteResults by observableList<StockQuote>()
    internal var currentMinuteOfDay by observable(15 * 60)
    internal var currentDateText by observable("")
    private var removePortfolioObserver: (() -> Unit)? = null
    private var draggingFavoriteCode = ""
    private var dragStartY = 0f
    private var dragCurrentIndex = -1
    private val contentRequests = StockRequestTracker()
    private var marketClockActive = false
    private var quoteRefreshActive = false
    private var contentLoading = false

    override fun created() {
        super.created()
        marketClockActive = true
        quoteRefreshActive = true
        val bridge = acquireModule<BridgeModule>(BridgeModule.MODULE_NAME)
        PortfolioPersistence.ensureLoaded(bridge)
        removePortfolioObserver = PortfolioStore.subscribe { state ->
            favoriteCodes = state.favoriteCodes
            positions = state.positions
            updateFavoriteQuotes() // 更新自选股列表
        }
        refreshMarketClock()
        scheduleMarketClockRefresh()
        scheduleQuoteRefresh()
        loadContent()
    }

    override fun onDestroyPager() {
        marketClockActive = false
        quoteRefreshActive = false
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
                StockLoadingState("正在加载实时行情...")
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
        if (contentLoading) return
        contentLoading = true
        val requestId = contentRequests.next()
        loading = !hasSelectedTabContent()
        errorMessage = ""
        acquireModule<BridgeModule>(BridgeModule.MODULE_NAME).log("stock_home 开始加载: $requestId")
        lifecycleScope.launch {
            var hasHomeContent = marketSummary != null && quotes.isNotEmpty()
            val cachedResult = repository.loadHome(this)
            if (!contentRequests.isLatest(requestId)) {
                contentLoading = false
                acquireModule<BridgeModule>(BridgeModule.MODULE_NAME).log("stock_home 丢弃过期缓存结果: $requestId")
                return@launch
            }
            when (cachedResult) {
                is StockLoadResult.Success -> {
                    applyHomeData(cachedResult.data)
                    hasHomeContent = cachedResult.data.quotes.isNotEmpty()
                    loading = false
                    acquireModule<BridgeModule>(BridgeModule.MODULE_NAME).log("stock_home 已展示数据库缓存: $requestId")
                }
                StockLoadResult.Empty -> Unit
                is StockLoadResult.Failure -> errorMessage = cachedResult.message
            }

            val refreshResult = repository.refreshHome(this)
            if (!contentRequests.isLatest(requestId)) {
                contentLoading = false
                acquireModule<BridgeModule>(BridgeModule.MODULE_NAME).log("stock_home 丢弃过期网络结果: $requestId")
                return@launch
            }
            when (refreshResult) {
                is StockLoadResult.Success -> {
                    applyHomeData(refreshResult.data)
                    hasHomeContent = refreshResult.data.quotes.isNotEmpty()
                    errorMessage = ""
                    acquireModule<BridgeModule>(BridgeModule.MODULE_NAME).log("stock_home 网络行情已替换缓存: $requestId")
                }
                StockLoadResult.Empty -> errorMessage = if (hasHomeContent) "最新行情为空，点击重试" else "暂无行情数据"
                is StockLoadResult.Failure -> {
                    errorMessage = if (hasHomeContent) "最新行情加载失败，点击重试" else refreshResult.message
                    acquireModule<BridgeModule>(BridgeModule.MODULE_NAME).log("stock_home 网络刷新失败，保留缓存: ${refreshResult.message}")
                }
            }

            val aiResult = repository.loadAi(this)
            if (!contentRequests.isLatest(requestId)) {
                contentLoading = false
                acquireModule<BridgeModule>(BridgeModule.MODULE_NAME).log("stock_home 丢弃过期AI结果: $requestId")
                return@launch
            }
            when (aiResult) {
                is StockLoadResult.Success -> {
                    overview = aiResult.data.overview
                    insights = aiResult.data.insights
                }
                StockLoadResult.Empty -> if (errorMessage.isEmpty() && !hasHomeContent) errorMessage = "暂无 AI 解读数据"
                is StockLoadResult.Failure -> if (errorMessage.isEmpty() && !hasHomeContent) errorMessage = aiResult.message
            }
            loading = false
            contentLoading = false
            if (errorMessage.isEmpty()) {
                acquireModule<BridgeModule>(BridgeModule.MODULE_NAME).log("stock_home 加载成功: $requestId")
            } else {
                acquireModule<BridgeModule>(BridgeModule.MODULE_NAME).log("stock_home 加载完成并提示重试: $errorMessage")
            }
        }
    }

    private fun applyHomeData(data: StockHomeData) {
        marketSummary = data.marketSummary
        quotes = data.quotes
        updateSearchResults()
        updateFavoriteQuotes() // 更新自选股列表
        dataSource = data.dataSource
        quoteTime = data.quoteTime
        quoteExpired = data.isExpired
        missingQuoteCodes = data.missingCodes
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
        }
        // 资讯卡片只显示年月日，格式改为 yyyy-MM-dd
        bridge.dateFormatter(timestamp, "yyyy-MM-dd").takeIf { it.isNotEmpty() }?.let { currentDateText = it }
    }

    // 获取当前实时时间文本（HH:mm 格式）
    internal fun getCurrentTimeText(): String {
        val bridge = acquireModule<BridgeModule>(BridgeModule.MODULE_NAME)
        val timestamp = bridge.currentTimeStamp()
        return if (timestamp > 0L) bridge.dateFormatter(timestamp, "HH:mm") else "--:--"
    }

    private fun scheduleMarketClockRefresh() {
        setTimeout(60_000) {
            if (marketClockActive) {
                refreshMarketClock()
                scheduleMarketClockRefresh()
            }
        }
    }

    private fun scheduleQuoteRefresh() {
        setTimeout(60_000) {
            if (quoteRefreshActive) {
                if (selectedTab == StockTabs.MARKET) loadContent()
                scheduleQuoteRefresh()
            }
        }
    }

    private fun hasSelectedTabContent(): Boolean = when (selectedTab) {
        StockTabs.AI -> overview != null || insights.isNotEmpty()
        else -> marketSummary != null && quotes.isNotEmpty()
    }

    internal fun filteredMarketQuotes(): List<StockQuote> = searchQuoteResults

    internal fun observableSearchResults(): ObservableList<StockQuote> = searchQuoteResults

    internal fun updateSearchText(text: String) {
        searchText = text
        updateSearchResults()
    }

    internal fun submitSearch(text: String) {
        searchText = text
        updateSearchResults()
        acquireModule<BridgeModule>(BridgeModule.MODULE_NAME).closeKeyboard(JSONObject())
        acquireModule<BridgeModule>(BridgeModule.MODULE_NAME).log("stock_home 提交搜索: ${text.trim()}")
    }

    private fun updateSearchResults() {
        searchQuoteResults.clear()
        searchQuoteResults.addAll(searchStockQuotes(quotes, searchText))
    }

    // 更新自选股列表
    private fun updateFavoriteQuotes() {
        val quotesByCode = quotes.associateBy { it.code }
        val newFavorites = favoriteCodes.mapNotNull(quotesByCode::get)
        favoriteQuoteResults.clear()
        favoriteQuoteResults.addAll(newFavorites)
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

    internal fun manualRefresh() {
        acquireModule<BridgeModule>(BridgeModule.MODULE_NAME).log("stock_home 手动刷新")
        loadContent()
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

    // 返回可观察的自选股列表，用于响应式渲染
    internal fun observableFavoriteQuotes(): ObservableList<StockQuote> = favoriteQuoteResults

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
                // 只有拖动距离超过半个卡片高度才触发交换，降低更新频率
                val offsetRows = ((y - dragStartY) / StockDesignTokens.quoteRowHeight).toInt()
                if (offsetRows == 0) return // 移动距离不够，不更新

                val target = (dragCurrentIndex + offsetRows).coerceIn(0, favoriteCodes.lastIndex)
                if (target != dragCurrentIndex && PortfolioStore.moveFavorite(dragCurrentIndex, target)) {
                    dragCurrentIndex = target
                    dragStartY = y // 更新基准位置
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
        StockWatchlistTabs(page.pagerData.pageViewWidth, { page.selectedWatchlistTab }) { tab ->
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
                            StockWatchlistFooter(page)
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
        // 使用 vfor 遍历可观察列表，拖动排序时自动响应变化
        vfor({ page.observableFavoriteQuotes() }) { quote ->
            View {
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
}

private fun ViewContainer<*, *>.StockSearchResults(page: StockHomePage) {
    // 直接使用可观察列表，确保响应式更新
    vif({ page.observableSearchResults().isNotEmpty() }) {
        View {
            attr {
                backgroundColor(StockDesignTokens.surface)
                borderRadius(12f)
                marginTop(12f)
            }
            View {
                attr { height(44f); padding(left = 16f, right = 16f); flexDirectionRow(); alignItemsCenter() }
                Text { attr { text("搜索结果  ${page.observableSearchResults().size}"); fontSize(14f); fontWeightBold(); color(StockDesignTokens.primaryText); flex(1f) } }
                Text { attr { text("可收藏或进入详情"); fontSize(11f); color(StockDesignTokens.tertiaryText) } }
            }
            // 使用 vfor 遍历可观察列表，自动响应变化
            vfor({ page.observableSearchResults() }) { quote ->
                View {
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

private fun ViewContainer<*, *>.StockWatchlistFooter(page: StockHomePage) {
    Text {
        attr {
            text(
                "行情时间 ${page.quoteTime.ifEmpty { "--" }} · ${page.dataSource.displayName()}" +
                    (if (page.quoteExpired) "（已过期）" else "") +
                    (if (page.missingQuoteCodes.isNotEmpty()) " · ${page.missingQuoteCodes.size}只未更新" else "")
            )
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
