package com.example.kuiklyaistock.repository

import com.example.kuiklyaistock.model.AiAnalysis
import com.example.kuiklyaistock.model.AiMarketOverview
import com.example.kuiklyaistock.model.AiStockInsight
import com.example.kuiklyaistock.model.MarketIndexQuote
import com.example.kuiklyaistock.model.MarketSummary
import com.example.kuiklyaistock.model.StockDataSource
import com.example.kuiklyaistock.model.StockDetail
import com.example.kuiklyaistock.model.StockQuote
import com.tencent.kuikly.core.coroutines.CoroutineScope
import com.tencent.kuikly.core.coroutines.launch
import com.tencent.kuikly.core.pager.Pager

class TencentStockRepository internal constructor(
    private val transport: QuoteTransport,
    private val nowMillis: () -> Long,
    private val logger: (String) -> Unit,
    private val databaseRepo: StockDatabaseRepository,
    private val cacheTtlMillis: Long = DEFAULT_CACHE_TTL_MILLIS,
) : StockRepository {
    constructor(
        pager: Pager,
        nowMillis: () -> Long = { 0L },
        logger: (String) -> Unit = {},
        databaseRepo: StockDatabaseRepository,
    ) : this(TencentQuoteTransport(pager, nowMillis, logger), nowMillis, logger, databaseRepo)

    private var latestQuotes: List<StockQuote> = emptyList()

    override suspend fun loadHome(scope: CoroutineScope): StockLoadResult<StockHomeData> {
        val cachedQuotes = databaseRepo.getAllQuotes()
        val cachedIndices = databaseRepo.getMarketIndices()
        if (cachedQuotes.isEmpty() && cachedIndices.isEmpty()) {
            logger("行情首页：数据库缓存为空")
            return StockLoadResult.Empty
        }
        logger("行情首页：读取数据库缓存 股票=${cachedQuotes.size} 指数=${cachedIndices.size}")
        return StockLoadResult.Success(buildHomeDataFromCache(cachedQuotes, cachedIndices))
    }

    override suspend fun refreshHome(scope: CoroutineScope): StockLoadResult<StockHomeData> = refreshHomeFromNetwork()

    override suspend fun loadDetail(scope: CoroutineScope, code: String): StockLoadResult<StockDetailData> {
        val normalized = code.trim()
        val symbol = TencentSymbolMapper.stockSymbol(normalized) ?: TencentSymbolMapper.indexSymbol(normalized)
            ?: return StockLoadResult.Empty

        // 1. 优先从数据库读取
        val cachedQuote = databaseRepo.getQuote(normalized)
        if (cachedQuote != null) {
            logger("股票详情：从数据库读取 $code")
            val detailData = buildDetailDataFromCache(cachedQuote, symbol)
            // 后台刷新网络数据
            scope.launch { refreshDetailFromNetwork(symbol, normalized) }
            return StockLoadResult.Success(detailData)
        }

        // 2. 数据库为空，等待网络请求
        logger("股票详情：数据库为空，等待网络请求 $code")
        return refreshDetailFromNetwork(symbol, normalized)
    }

    override suspend fun loadAi(scope: CoroutineScope): StockLoadResult<StockAiData> {
        val quotes = latestQuotes.ifEmpty {
            STOCK_CODES.mapNotNull { code ->
                TencentSymbolMapper.stockSymbol(code)?.let(TencentQuoteCache::get)?.quote?.let(::toStockQuote)
            }
        }
        if (quotes.isEmpty()) return StockLoadResult.Failure("暂无真实行情，暂时无法生成 AI 解读")
        val insights = quotes.take(8).map { quote -> AiStockInsight(quote, createRuleAnalysis(quote)) }
        val rising = quotes.count { it.change > 0 }
        val falling = quotes.count { it.change < 0 }
        return StockLoadResult.Success(
            StockAiData(
                overview = AiMarketOverview(
                    title = "实时行情规则解读",
                    sentiment = if (rising >= falling) "谨慎乐观" else "谨慎观察",
                    summary = "基于已加载的真实快照：上涨${rising}只，下跌${falling}只。",
                    riskTip = "规则解读不构成投资建议，真实走势数据本期未接入。",
                    updatedAt = quotes.map { it.updatedAt }.maxOrNull().orEmpty(),
                ),
                insights = insights,
            )
        )
    }

    private suspend fun buildRemoteHome(
        result: DecodedQuotes.Remote,
        stockSymbols: List<String>,
        indexSymbols: List<String>,
    ): StockLoadResult<StockHomeData> {
        val remoteBySymbol = result.quotes.associateBy { it.symbol }
        val remoteStocks = stockSymbols.mapNotNull(remoteBySymbol::get).map(::toStockQuote)
        val remoteIndices = indexSymbols.mapNotNull(remoteBySymbol::get).map(::toIndexQuote)
        if (remoteStocks.isNotEmpty()) databaseRepo.insertQuotes(remoteStocks)
        if (remoteIndices.isNotEmpty()) databaseRepo.insertMarketIndices(remoteIndices)
        logger("行情首页：更新数据库 股票=${remoteStocks.size} 指数=${remoteIndices.size}")
        if (remoteStocks.isEmpty()) {
            return StockLoadResult.Failure(result.errorMessage ?: "真实行情没有返回个股数据")
        }

        val cachedStocks = databaseRepo.getAllQuotes().associateBy { it.code }
        val cachedIndices = databaseRepo.getMarketIndices().associateBy { it.code }
        val quotes = STOCK_CODES.mapNotNull(cachedStocks::get)
        val indices = INDEX_CODES.mapNotNull(cachedIndices::get)
        val missingCodes = result.missingSymbols.mapNotNull(TencentSymbolMapper::standardCode)
        if (missingCodes.isNotEmpty()) {
            logger("行情首页：部分更新 success=${result.quotes.size} missing=${missingCodes.size}")
        }
        latestQuotes = quotes
        return StockLoadResult.Success(
            StockHomeData(
                quotes = quotes,
                marketSummary = createMarketSummary(
                    quotes = quotes,
                    indices = indices,
                    turnover = result.quotes.filter { it.symbol in stockSymbols }.sumOf { it.turnover },
                ),
                dataSource = StockDataSource.REMOTE,
                quoteTime = (remoteStocks.map { it.updatedAt } + remoteIndices.map { it.updatedAt }).maxOrNull().orEmpty(),
                requestCompletedAt = result.completedAt,
                isExpired = false,
                missingCodes = missingCodes,
            )
        )
    }

    private suspend fun fetchAndDecode(symbols: List<String>): DecodedQuotes =
        when (val response = transport.fetch(symbols)) {
            is QuoteTransportResult.Failure -> DecodedQuotes.Failed(
                missingSymbols = symbols,
                completedAt = nowMillis(),
                errorMessage = response.category.toMessage(),
            )
            is QuoteTransportResult.Success -> {
                val text = try {
                    TencentQuoteDecoder.decode(response.body)
                } catch (error: IllegalArgumentException) {
                    return DecodedQuotes.Failed(symbols, nowMillis(), error.message ?: "行情响应无法解码")
                }
                val parsed = TencentQuoteParser.parse(text, symbols)
                val accepted = mutableListOf<TencentParsedQuote>()
                val rejectedOlder = mutableListOf<String>()
                parsed.quotes.forEach { quote ->
                    if (TencentQuoteCache.putIfNewer(quote, nowMillis())) accepted += quote
                    else rejectedOlder += quote.symbol
                }
                val missing = (parsed.missingSymbols + rejectedOlder).distinct()
                if (accepted.isEmpty()) {
                    val message = when {
                        rejectedOlder.isNotEmpty() -> "真实行情时间早于缓存，已丢弃"
                        parsed.errors.isNotEmpty() -> "行情字段校验失败：${parsed.errors.first().category}"
                        else -> "真实行情没有更新"
                    }
                    DecodedQuotes.Failed(missing.ifEmpty { symbols }, nowMillis(), message)
                } else {
                    DecodedQuotes.Remote(
                        quotes = accepted,
                        missingSymbols = missing,
                        completedAt = nowMillis(),
                        errorMessage = parsed.errors.firstOrNull()?.category?.name,
                    )
                }
            }
        }

    private fun buildHomeDataFromCache(
        quotes: List<StockQuote>,
        indices: List<MarketIndexQuote>,
    ): StockHomeData {
        val quoteMap = quotes.associateBy { it.code }
        val indexMap = indices.associateBy { it.code }
        val stocks = STOCK_CODES.mapNotNull(quoteMap::get)
        val orderedIndices = INDEX_CODES.mapNotNull(indexMap::get)
        latestQuotes = stocks
        return StockHomeData(
            quotes = stocks,
            marketSummary = createMarketSummary(stocks, orderedIndices, 0.0),
            dataSource = StockDataSource.CACHE,
            quoteTime = (stocks.map { it.updatedAt } + orderedIndices.map { it.updatedAt }).maxOrNull().orEmpty(),
            requestCompletedAt = nowMillis(),
            isExpired = false,
            missingCodes = emptyList(),
        )
    }

    private suspend fun refreshHomeFromNetwork(): StockLoadResult<StockHomeData> {
        val stockSymbols = STOCK_CODES.mapNotNull(TencentSymbolMapper::stockSymbol)
        val indexSymbols = INDEX_CODES.mapNotNull(TencentSymbolMapper::indexSymbol)
        val symbols = indexSymbols + stockSymbols
        logger("行情首页：请求最新行情 股票=${stockSymbols.size} 指数=${indexSymbols.size}")
        return when (val result = fetchAndDecode(symbols)) {
            is DecodedQuotes.Remote -> buildRemoteHome(result, stockSymbols, indexSymbols)
            is DecodedQuotes.Failed -> {
                logger("行情首页：网络刷新失败 ${result.errorMessage}")
                StockLoadResult.Failure(result.errorMessage ?: "网络请求失败")
            }
        }
    }

    private fun cachedDetail(
        symbol: String,
        completedAt: Long,
        errorMessage: String?,
    ): StockLoadResult<StockDetailData> {
        val cached = TencentQuoteCache.get(symbol) ?: return StockLoadResult.Failure(errorMessage ?: "真实行情加载失败")
        return StockLoadResult.Success(
            StockDetailData(
                detail = toStockDetail(cached.quote),
                analysis = null,
                dataSource = StockDataSource.CACHE,
                quoteTime = cached.quote.updatedAt,
                requestCompletedAt = completedAt,
                isExpired = TencentQuoteCache.isExpired(cached, completedAt, cacheTtlMillis),
                missingCodes = listOfNotNull(TencentSymbolMapper.standardCode(symbol)),
            )
        )
    }

    private suspend fun buildDetailDataFromCache(quote: StockQuote, symbol: String): StockDetailData {
        val intradayTrend = databaseRepo.getIntradayTrend(quote.code)
        val dailyKLine = databaseRepo.getKLines(quote.code, "daily")
        val weeklyKLine = databaseRepo.getKLines(quote.code, "weekly")
        val monthlyKLine = databaseRepo.getKLines(quote.code, "monthly")

        // 从价格推算合理的今开、昨收、最高、最低
        val price = quote.price
        val changePercent = quote.changePercent
        val previousClose = price / (1.0 + changePercent / 100.0)
        val open = previousClose * (1.0 + (changePercent * 0.3) / 100.0) // 今开在昨收和现价之间
        val high = maxOf(price, open, previousClose) * 1.015 // 最高比当前价和开盘价略高
        val low = minOf(price, open, previousClose) * 0.985 // 最低比当前价和开盘价略低
        val volume = (price * 10_000_000).toLong() // 根据价格估算成交量
        val turnover = volume * price

        val detail = StockDetail(
            quote = quote,
            open = open,
            previousClose = previousClose,
            high = high,
            low = low,
            volume = volume,
            turnover = turnover,
            turnoverRate = MockDataGenerator.generateTurnoverRate(),
            peRatio = MockDataGenerator.generatePeRatio(),
            intradayTrend = intradayTrend,
            fiveDayTrend = emptyList(),
            dailyKLine = dailyKLine,
            weeklyKLine = weeklyKLine,
            monthlyKLine = monthlyKLine,
        )

        return StockDetailData(
            detail = detail,
            analysis = null,
            dataSource = StockDataSource.CACHE,
            quoteTime = quote.updatedAt,
            requestCompletedAt = nowMillis(),
            missingCodes = emptyList(),
        )
    }

    private suspend fun refreshDetailFromNetwork(symbol: String, code: String): StockLoadResult<StockDetailData> {
        return when (val result = fetchAndDecode(listOf(symbol))) {
            is DecodedQuotes.Remote -> {
                val quote = result.quotes.firstOrNull { it.symbol == symbol }
                    ?: return cachedDetail(symbol, result.completedAt, result.errorMessage)

                val intradayTrend = MockDataGenerator.generateIntradayTrend(quote.price, quote.updatedAt)
                val fiveDayTrend = MockDataGenerator.generateFiveDayTrend(quote.price)
                val dailyKLine = MockDataGenerator.generateDailyKLines(quote.price)
                val weeklyKLine = MockDataGenerator.generateWeeklyKLines(quote.price)
                val monthlyKLine = MockDataGenerator.generateMonthlyKLines(quote.price)
                val turnoverRate = MockDataGenerator.generateTurnoverRate()
                val peRatio = MockDataGenerator.generatePeRatio()

                val stockQuote = toStockQuote(quote)
                databaseRepo.insertQuotes(listOf(stockQuote))
                databaseRepo.insertIntradayTrend(code, intradayTrend)
                databaseRepo.insertKLines(code, "daily", dailyKLine)
                databaseRepo.insertKLines(code, "weekly", weeklyKLine)
                databaseRepo.insertKLines(code, "monthly", monthlyKLine)

                logger("股票详情：写入数据库 $code")

                StockLoadResult.Success(
                    StockDetailData(
                        detail = StockDetail(
                            quote = stockQuote,
                            open = quote.open,
                            previousClose = quote.previousClose,
                            high = quote.high,
                            low = quote.low,
                            volume = quote.volume,
                            turnover = quote.turnover,
                            turnoverRate = turnoverRate,
                            peRatio = peRatio,
                            intradayTrend = intradayTrend,
                            fiveDayTrend = fiveDayTrend,
                            dailyKLine = dailyKLine,
                            weeklyKLine = weeklyKLine,
                            monthlyKLine = monthlyKLine,
                        ),
                        analysis = null,
                        dataSource = StockDataSource.REMOTE,
                        quoteTime = quote.updatedAt,
                        requestCompletedAt = result.completedAt,
                        missingCodes = result.missingSymbols.mapNotNull(TencentSymbolMapper::standardCode),
                    )
                )
            }
            is DecodedQuotes.Failed -> cachedDetail(symbol, result.completedAt, result.errorMessage)
        }
    }

    private fun createMarketSummary(
        quotes: List<StockQuote>,
        indices: List<MarketIndexQuote>,
        turnover: Double,
    ): MarketSummary {
        val rising = quotes.count { it.change > 0 }
        val falling = quotes.count { it.change < 0 }
        return MarketSummary(
            totalCount = quotes.size,
            risingCount = rising,
            fallingCount = falling,
            flatCount = quotes.size - rising - falling,
            sessionStatus = "实时快照",
            sampleTurnoverAmount = turnover,
            sampleNetInflowAmount = 0.0,
            indices = indices,
            updatedAt = quotes.map { it.updatedAt }.maxOrNull().orEmpty(),
        )
    }

    private fun toStockQuote(quote: TencentParsedQuote) = StockQuote(
        name = quote.name,
        code = quote.code,
        price = quote.price,
        change = quote.change,
        changePercent = quote.changePercent,
        updatedAt = quote.updatedAt,
    )

    private fun toIndexQuote(quote: TencentParsedQuote) = MarketIndexQuote(
        name = quote.name,
        code = quote.code,
        price = quote.price,
        change = quote.change,
        changePercent = quote.changePercent,
        updatedAt = quote.updatedAt,
    )

    private fun toStockDetail(quote: TencentParsedQuote) = StockDetail(
        quote = toStockQuote(quote),
        open = quote.open,
        previousClose = quote.previousClose,
        high = quote.high,
        low = quote.low,
        volume = quote.volume,
        turnover = quote.turnover,
        intradayTrend = emptyList(),
        dailyTrend = emptyList(),
    )

    private fun createRuleAnalysis(quote: StockQuote) = AiAnalysis(
        trendJudgement = if (quote.changePercent >= 0) "快照显示${quote.name}当日偏强" else "快照显示${quote.name}当日偏弱",
        focusPoint = "关注后续真实走势数据",
        riskReminder = "单次快照不能代表趋势",
        signalInterpretation = "涨跌幅${quote.changePercent}%",
        factSummary = "当前价${quote.price}，涨跌${quote.change}",
        applicablePeriod = "当日快照",
        evidenceSummary = "行情时间${quote.updatedAt}",
        updatedAt = quote.updatedAt,
        isDemo = false,
    )

    private sealed class DecodedQuotes {
        abstract val missingSymbols: List<String>
        abstract val completedAt: Long
        abstract val errorMessage: String?

        data class Remote(
            val quotes: List<TencentParsedQuote>,
            override val missingSymbols: List<String>,
            override val completedAt: Long,
            override val errorMessage: String?,
        ) : DecodedQuotes()

        data class Failed(
            override val missingSymbols: List<String>,
            override val completedAt: Long,
            override val errorMessage: String?,
        ) : DecodedQuotes()
    }

    private data class StockQuoteEntry(
        val quote: TencentParsedQuote,
        val isExpired: Boolean,
    )

    private fun QuoteTransportErrorCategory.toMessage(): String = when (this) {
        QuoteTransportErrorCategory.HTTP -> "行情服务返回 HTTP 错误"
        QuoteTransportErrorCategory.NETWORK -> "行情网络请求失败"
        QuoteTransportErrorCategory.TIMEOUT -> "行情请求超时"
        QuoteTransportErrorCategory.EMPTY -> "行情响应为空"
        QuoteTransportErrorCategory.UNSUPPORTED -> "当前平台不支持行情请求"
    }

    private companion object {
        const val DEFAULT_CACHE_TTL_MILLIS = 120_000L
        val INDEX_CODES = listOf("000001", "399001", "399006")
        val STOCK_CODES = listOf(
            "603019", "601138", "300750", "002594", "300059", "600519", "000858", "600887", "000333", "000651",
            "603288", "601318", "600036", "601398", "600030", "002475", "002415", "603986", "603501", "002230",
            "601012", "300274", "002460", "300014", "002466", "300760", "600276", "603259", "300015", "600941",
            "000063", "600050", "600031", "600309", "601899", "600900",
        )
    }
}
