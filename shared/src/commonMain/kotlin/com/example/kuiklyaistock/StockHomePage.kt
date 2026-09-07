package com.example.kuiklyaistock

import com.example.kuiklyaistock.base.BasePager
import com.example.kuiklyaistock.base.BridgeModule
import com.example.kuiklyaistock.model.StockQuote
import com.example.kuiklyaistock.model.MarketSummary
import com.example.kuiklyaistock.repository.MockStockRepository
import com.example.kuiklyaistock.repository.StockRepository
import com.tencent.kuikly.core.annotations.Page
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.ViewBuilder
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.module.RouterModule
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject
import com.tencent.kuikly.core.reactive.handler.observable
import com.tencent.kuikly.core.views.Scroller
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View

@Page("stock_home", supportInLocal = true)
internal class StockHomePage : BasePager() {
    private val repository: StockRepository = MockStockRepository()
    private var loading by observable(true)
    private var quotes by observable(emptyList<StockQuote>())
    private var marketSummary by observable<MarketSummary?>(null)
    private var errorMessage by observable("")
    private var selectedTab by observable(StockTabs.MARKET)

    override fun created() {
        super.created()
        loadQuotes()
    }

    override fun body(): ViewBuilder {
        val ctx = this
        return {
            attr {
                backgroundColor(Color(0xFFF8FAFC))
            }
            RouterNavBar {
                attr {
                    title = "AI 股票行情"
                    backDisable = true
                }
            }
            if (ctx.loading) {
                StockStateText("正在加载行情...")
            } else if (ctx.errorMessage.isNotEmpty()) {
                StockStateText(ctx.errorMessage)
            } else {
                Scroller {
                    attr {
                        flex(1f)
                        padding(left = 16f, right = 16f, top = 14f, bottom = 20f)
                    }
                    Text {
                        attr {
                            text("行情中心")
                            fontSize(24f)
                            fontWeightBold()
                            color(Color(0xFF1F2937))
                            marginBottom(4f)
                        }
                    }
                    Text {
                        attr {
                            text("盘中行情 · ${ctx.quotes.size} 只股票")
                            fontSize(13f)
                            color(Color(0xFF6B7280))
                            marginBottom(12f)
                        }
                        }
                    StockMarketSummary(ctx.marketSummary)
                    Text {
                        attr {
                            text("全部行情")
                            fontSize(17f)
                            fontWeightBold()
                            color(Color(0xFF1F2937))
                            marginBottom(10f)
                        }
                    }
                    ctx.quotes.forEach { quote ->
                        StockQuoteRow(quote) {
                            ctx.openDetail(quote.code)
                        }
                    }
                }
            }
            StockBottomBar(ctx.selectedTab) { tab ->
                ctx.selectedTab = tab
                if (tab == StockTabs.AI) {
                    ctx.openStockPage("stock_ai")
                }
            }
        }
    }

    private fun loadQuotes() {
        val result = repository.getQuotes()
        quotes = result
        marketSummary = repository.getMarketSummary()
        errorMessage = if (result.isEmpty()) "暂无行情数据" else ""
        acquireModule<BridgeModule>(BridgeModule.MODULE_NAME).log(
            if (result.isEmpty()) "stock_home 行情加载为空" else "stock_home 行情加载成功: ${result.size}"
        )
        loading = false
    }

    private fun openDetail(code: String) {
        val pageData = JSONObject().apply {
            put("code", code)
            put("symbol", code)
        }
        acquireModule<BridgeModule>(BridgeModule.MODULE_NAME).log("stock_home 跳转详情: $code")
        acquireModule<RouterModule>(RouterModule.MODULE_NAME).openPage("stock_detail", pageData)
    }
}
