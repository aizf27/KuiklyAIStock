package com.example.kuiklyaistock

import com.example.kuiklyaistock.base.BasePager
import com.example.kuiklyaistock.base.BridgeModule
import com.example.kuiklyaistock.model.AiAnalysis
import com.example.kuiklyaistock.model.StockDetail
import com.example.kuiklyaistock.model.StockPosition
import com.example.kuiklyaistock.model.TradeResult
import com.example.kuiklyaistock.repository.AiAnalysisLoadResult
import com.example.kuiklyaistock.repository.AiAnalysisRepository
import com.example.kuiklyaistock.repository.BridgeAiAnalysisTransport
import com.example.kuiklyaistock.repository.MockStockRepository
import com.example.kuiklyaistock.repository.RemoteAiAnalysisRepository
import com.example.kuiklyaistock.repository.PortfolioPersistence
import com.example.kuiklyaistock.repository.PortfolioStore
import com.example.kuiklyaistock.repository.StockRepository
import com.example.kuiklyaistock.repository.StockLoadResult
import com.example.kuiklyaistock.repository.StockRequestTracker
import com.tencent.kuikly.core.annotations.Page
import com.tencent.kuikly.core.base.ViewBuilder
import com.tencent.kuikly.core.coroutines.launch
import com.tencent.kuikly.core.directives.vif
import com.tencent.kuikly.core.directives.velseif
import com.tencent.kuikly.core.directives.velse
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject
import com.tencent.kuikly.core.reactive.handler.observable
import com.tencent.kuikly.core.reactive.handler.observableList
import com.tencent.kuikly.core.views.Scroller
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View
import com.tencent.kuiklybase.chart.model.OhlcPoint

@Page("stock_detail", supportInLocal = true)
internal class StockDetailPage : BasePager() {
    private val repository: StockRepository = MockStockRepository()
    private var loading by observable(true)
    private var detail by observable<StockDetail?>(null)
    private var displayedAnalysis by observable<AiAnalysis?>(null)
    private var aiLoadState by observable(AiLoadState.IDLE)
    private var aiErrorMessage by observable("")
    private var isAiDetailExpanded by observable(false)
    private var errorMessage by observable("")
    private var favoriteCodes by observable(emptyList<String>())
    private var positions by observable(emptyList<StockPosition>())
    private var tradeSide by observable("")
    private var tradeQuantityText by observable("")
    private var tradeErrorMessage by observable("")
    private var selectedChartPeriod by observable(StockChartPeriod.INTRADAY)
    private var expandedAiSignalIndex by observable(-1)
    private var selectedAiQuickQuestion by observable("")
    private var chartCandles by observableList<OhlcPoint>()
    private var removePortfolioObserver: (() -> Unit)? = null
    private val detailRequests = StockRequestTracker()
    private val aiRequests = StockRequestTracker()
    private var aiRepository: AiAnalysisRepository? = null

    override fun created() {
        super.created()
        val bridge = acquireModule<BridgeModule>(BridgeModule.MODULE_NAME)
        PortfolioPersistence.ensureLoaded(bridge)
        aiRepository = RemoteAiAnalysisRepository(BridgeAiAnalysisTransport(bridge))
        removePortfolioObserver = PortfolioStore.subscribe { state ->
            favoriteCodes = state.favoriteCodes
            positions = state.positions
        }
        loadDetail()
    }

    override fun onDestroyPager() {
        detailRequests.invalidate()
        aiRequests.invalidate()
        removePortfolioObserver?.invoke()
        removePortfolioObserver = null
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
                                    bottom = StockDesignTokens.pageBottomSpacing + ctx.stockBottomInset(),
                                )
                            }
                            // 使用新的顶部信息区组件
                            StockDetailHeader(
                                detail = stock,
                                width = ctx.stockContentWidth(),
                                isFavorite = { ctx.isFavorite(stock.quote.code) },
                                onFavorite = { ctx.toggleFavorite(stock.quote.code) }
                            )

                            // 使用新的K线图区组件
                            StockChartSection(
                                width = ctx.stockContentWidth(),
                                candles = { ctx.chartCandles },
                                selectedPeriod = { ctx.selectedChartPeriod },
                                onPeriodChange = { ctx.selectChartPeriod(it) },
                            )

                            // 使用新的交易按钮组件
                            StockTradeButtons(
                                width = ctx.stockContentWidth(),
                                onBuy = { ctx.openTrade(TradeSides.BUY, stock) },
                                onSell = { ctx.openTrade(TradeSides.SELL, stock) },
                            )
                            AiAnalysisSection(
                                analysis = { ctx.displayedAnalysis },
                                loadState = { ctx.aiLoadState },
                                errorMessage = { ctx.aiErrorMessage },
                                isDetailExpanded = { ctx.isAiDetailExpanded },
                                width = ctx.stockContentWidth(),
                                expandedSignalIndex = { ctx.expandedAiSignalIndex },
                                selectedQuickQuestion = { ctx.selectedAiQuickQuestion },
                                onSignalToggle = { ctx.toggleAiSignal(it) },
                                onQuickQuestion = { ctx.selectAiQuickQuestion(it) },
                                onDetailToggle = { ctx.toggleAiDetail() },
                                onRetry = { ctx.retryAiAnalysis() },
                            )
                        }
                    }
                }
            }
            vif({ ctx.tradeSide.isNotEmpty() && ctx.detail != null }) {
                ctx.detail?.let { stock ->
                    StockTradeDialog(
                        width = ctx.pagerData.pageViewWidth,
                        height = ctx.pagerData.pageViewHeight,
                        stock = stock,
                        side = { ctx.tradeSide },
                        quantityText = { ctx.tradeQuantityText },
                        errorMessage = { ctx.tradeErrorMessage },
                        availableQuantity = { ctx.currentPosition(stock.quote.code)?.quantity ?: 0 },
                        onQuantityChange = { ctx.updateTradeQuantity(it) },
                        onCancel = { ctx.closeTrade() },
                        onConfirm = { ctx.submitTrade(stock) },
                    )
                }
            }
        }
    }

    private fun loadDetail() {
        val code = pagerData.params.optString("code").trim()
        if (code.isEmpty()) {
            resetAiInteractionState()
            displayedAnalysis = null
            aiLoadState = AiLoadState.IDLE
            aiErrorMessage = ""
            loading = false
            errorMessage = "缺少股票代码，无法加载详情"
            acquireModule<BridgeModule>(BridgeModule.MODULE_NAME).log("stock_detail 缺少股票代码")
            return
        }
        val requestId = detailRequests.next()
        aiRequests.invalidate()
        resetAiInteractionState()
        displayedAnalysis = null
        aiLoadState = AiLoadState.IDLE
        aiErrorMessage = ""
        loading = true
        errorMessage = ""
        acquireModule<BridgeModule>(BridgeModule.MODULE_NAME).log("stock_detail 开始加载: $code")
        lifecycleScope.launch {
            when (val result = repository.loadDetail(this, code)) {
                is StockLoadResult.Success -> {
                    if (!acceptResult(requestId, code)) return@launch
                    displayedAnalysis = result.data.analysis
                    detail = result.data.detail
                    refreshChartData(result.data.detail)
                    loadRemoteAnalysis(result.data.detail)
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
        if (selectedChartPeriod == period) return
        selectedChartPeriod = period
        detail?.let(::refreshChartData)
        acquireModule<BridgeModule>(BridgeModule.MODULE_NAME).log("stock_detail 切换走势周期: ${period.title}")
    }

    private fun refreshChartData(stock: StockDetail) {
        chartCandles.clear()
        chartCandles.addAll(mockStockCandles(stock, selectedChartPeriod))
    }

    private fun resetAiInteractionState() {
        expandedAiSignalIndex = -1
        selectedAiQuickQuestion = ""
        isAiDetailExpanded = false
    }

    private fun loadRemoteAnalysis(stock: StockDetail) {
        val remoteRepository = aiRepository ?: return
        val requestId = aiRequests.next()
        aiLoadState = AiLoadState.LOADING
        aiErrorMessage = ""
        lifecycleScope.launch {
            when (val result = remoteRepository.loadAnalysis(stock)) {
                is AiAnalysisLoadResult.Success -> {
                    if (!acceptAiResult(requestId, stock.quote.code)) return@launch
                    displayedAnalysis = result.analysis
                    aiLoadState = AiLoadState.SUCCESS
                    aiErrorMessage = ""
                    acquireModule<BridgeModule>(BridgeModule.MODULE_NAME).log(
                        "stock_detail AI分析成功: ${stock.quote.code}, source=${result.analysis.source}"
                    )
                }
                is AiAnalysisLoadResult.Failure -> {
                    if (!acceptAiResult(requestId, stock.quote.code)) return@launch
                    aiLoadState = AiLoadState.FAILURE
                    aiErrorMessage = result.message
                    acquireModule<BridgeModule>(BridgeModule.MODULE_NAME).log(
                        "stock_detail AI分析失败: ${stock.quote.code}, type=${result.type}"
                    )
                }
                AiAnalysisLoadResult.Unsupported -> {
                    if (!acceptAiResult(requestId, stock.quote.code)) return@launch
                    aiLoadState = AiLoadState.IDLE
                    aiErrorMessage = ""
                }
            }
        }
    }

    private fun acceptAiResult(requestId: Int, code: String): Boolean {
        if (aiRequests.isLatest(requestId) && detail?.quote?.code == code) return true
        acquireModule<BridgeModule>(BridgeModule.MODULE_NAME).log(
            "stock_detail 丢弃过期AI结果: $code, $requestId"
        )
        return false
    }

    private fun retryAiAnalysis() {
        detail?.let {
            resetAiInteractionState()
            loadRemoteAnalysis(it)
        }
    }

    private fun toggleAiDetail() {
        isAiDetailExpanded = !isAiDetailExpanded
        if (!isAiDetailExpanded) expandedAiSignalIndex = -1
        acquireModule<BridgeModule>(BridgeModule.MODULE_NAME).log(
            "stock_detail ${if (isAiDetailExpanded) "展开" else "收起"}完整AI分析: ${detail?.quote?.code.orEmpty()}"
        )
    }

    private fun toggleAiSignal(index: Int) {
        expandedAiSignalIndex = if (expandedAiSignalIndex == index) -1 else index
        val code = detail?.quote?.code.orEmpty()
        acquireModule<BridgeModule>(BridgeModule.MODULE_NAME).log(
            "stock_detail ${if (expandedAiSignalIndex == index) "展开" else "收起"}AI信号依据: $code, $index"
        )
    }

    private fun selectAiQuickQuestion(question: String) {
        selectedAiQuickQuestion = if (selectedAiQuickQuestion == question) "" else question
        val code = detail?.quote?.code.orEmpty()
        acquireModule<BridgeModule>(BridgeModule.MODULE_NAME).log(
            "stock_detail ${if (selectedAiQuickQuestion.isEmpty()) "收起" else "查看"}AI预设解读: $code, $question"
        )
    }

    private fun isFavorite(code: String): Boolean {
        return code in favoriteCodes
    }

    private fun toggleFavorite(code: String) {
        val bridge = acquireModule<BridgeModule>(BridgeModule.MODULE_NAME)
        val favorite = PortfolioStore.toggleFavorite(code)
        PortfolioPersistence.save(bridge)
        bridge.log(
            "stock_detail 自选${if (favorite) "添加" else "移除"}: $code"
        )
    }

    private fun currentPosition(code: String): StockPosition? = positions.firstOrNull { it.code == code }

    private fun openTrade(side: String, stock: StockDetail) {
        tradeSide = side
        tradeQuantityText = ""
        tradeErrorMessage = ""
        acquireModule<BridgeModule>(BridgeModule.MODULE_NAME).log(
            "stock_detail 打开${if (side == TradeSides.BUY) "买入" else "卖出"}弹层: ${stock.quote.code}"
        )
    }

    private fun updateTradeQuantity(text: String) {
        tradeQuantityText = text.filter { it.isDigit() }
        if (tradeErrorMessage.isNotEmpty()) tradeErrorMessage = ""
    }

    private fun closeTrade() {
        val bridge = acquireModule<BridgeModule>(BridgeModule.MODULE_NAME)
        bridge.closeKeyboard(JSONObject())
        bridge.log("stock_detail 关闭交易弹层")
        tradeSide = ""
        tradeQuantityText = ""
        tradeErrorMessage = ""
    }

    private fun submitTrade(stock: StockDetail) {
        val bridge = acquireModule<BridgeModule>(BridgeModule.MODULE_NAME)
        val quantity = tradeQuantityText.trim().toIntOrNull()
        if (quantity == null) {
            handleTradeFailure("请输入正整数股数", bridge, stock.quote.code)
            return
        }
        val result = if (tradeSide == TradeSides.BUY) {
            PortfolioStore.buy(stock.quote.code, quantity, stock.quote.price)
        } else {
            PortfolioStore.sell(stock.quote.code, quantity)
        }
        when (result) {
            is TradeResult.Success -> {
                val action = if (tradeSide == TradeSides.BUY) "买入" else "卖出"
                PortfolioPersistence.save(bridge)
                bridge.closeKeyboard(JSONObject())
                bridge.toast("$action 成功")
                bridge.log("stock_detail $action 成功: ${stock.quote.code}, ${quantity}股, ${stock.quote.price}")
                tradeSide = ""
                tradeQuantityText = ""
                tradeErrorMessage = ""
            }
            is TradeResult.Failure -> handleTradeFailure(result.message, bridge, stock.quote.code)
        }
    }

    private fun handleTradeFailure(message: String, bridge: BridgeModule, code: String) {
        tradeErrorMessage = message
        bridge.toast(message)
        bridge.log("stock_detail 交易失败: $code, $message")
    }
}

internal object TradeSides {
    const val BUY = "buy"
    const val SELL = "sell"
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
