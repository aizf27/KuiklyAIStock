package com.example.kuiklyaistock

import com.example.kuiklyaistock.base.BasePager
import com.example.kuiklyaistock.base.BridgeModule
import com.example.kuiklyaistock.model.StockDetail
import com.example.kuiklyaistock.model.AiAnalysis
import com.example.kuiklyaistock.repository.MockStockRepository
import com.example.kuiklyaistock.repository.StockRepository
import com.example.kuiklyaistock.repository.WatchlistStore
import com.tencent.kuikly.core.annotations.Page
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.ViewBuilder
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject
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
    private var favoriteVersion by observable(0)

    override fun created() {
        super.created()
        loadDetail()
    }

    override fun body(): ViewBuilder {
        val ctx = this
        return {
            attr {
                backgroundColor(Color(0xFFF8FAFC))
            }
            RouterNavBar {
                attr {
                    title = ctx.detail?.quote?.name ?: "股票详情"
                }
            }
            if (ctx.loading) {
                StockStateText("正在加载详情...")
            } else if (ctx.errorMessage.isNotEmpty()) {
                StockStateText(ctx.errorMessage)
            } else {
                ctx.detail?.let { stock ->
                    Scroller {
                        attr {
                            flex(1f)
                            padding(left = 16f, right = 16f, top = 14f, bottom = 24f)
                        }
                        View {
                            attr {
                                flexDirectionRow()
                                alignItemsCenter()
                                marginBottom(14f)
                            }
                            View {
                                attr {
                                    flex(1f)
                                }
                                Text {
                                    attr {
                                        text(stock.quote.name)
                                        fontSize(24f)
                                        fontWeightBold()
                                        color(Color(0xFF111827))
                                    }
                                }
                                Text {
                                    attr {
                                        text("${stock.quote.code} · ${stock.quote.updatedAt}")
                                        fontSize(12f)
                                        color(Color(0xFF6B7280))
                                        marginTop(5f)
                                    }
                                }
                            }
                            Text {
                                attr {
                                    text(if (ctx.isFavorite(stock.quote.code)) "★" else "☆")
                                    fontSize(28f)
                                    color(
                                        if (ctx.isFavorite(stock.quote.code)) Color(0xFFF59E0B)
                                        else Color(0xFF9CA3AF)
                                    )
                                }
                                event {
                                    click { ctx.toggleFavorite(stock.quote.code) }
                                }
                            }
                        }
                        View {
                            attr {
                                backgroundColor(Color.WHITE)
                                borderRadius(8f)
                                padding(16f)
                            }
                            Text {
                                attr {
                                    text(formatStockPrice(stock.quote.price))
                                    fontSize(32f)
                                    fontWeightBold()
                                    color(Color(0xFF111827))
                                }
                            }
                            Text {
                                attr {
                                    text("${formatStockSigned(stock.quote.change)}  ${formatStockPercent(stock.quote.changePercent)}")
                                    fontSize(15f)
                                    color(stockChangeColor(stock.quote.change))
                                    marginTop(6f)
                                }
                            }
                        }
                        Text {
                            attr {
                                text("日内走势")
                                fontSize(17f)
                                fontWeightBold()
                                color(Color(0xFF1F2937))
                                marginTop(20f)
                                marginBottom(8f)
                            }
                        }
                        StockTrendChart(
                            points = stock.trend,
                            width = ctx.pagerData.pageViewWidth - 32f,
                            change = stock.quote.change,
                        )
                        Text {
                            attr {
                                text("基础行情")
                                fontSize(17f)
                                fontWeightBold()
                                color(Color(0xFF1F2937))
                                marginTop(20f)
                                marginBottom(8f)
                            }
                        }
                        View {
                            attr {
                                flexDirectionRow()
                                backgroundColor(Color.WHITE)
                                borderRadius(8f)
                                padding(left = 14f, right = 14f, top = 14f, bottom = 4f)
                            }
                            StockMetric("最高", formatStockPrice(stock.high))
                            StockMetric("最低", formatStockPrice(stock.low))
                            StockMetric("成交量", formatStockVolume(stock.volume))
                            StockMetric("成交额", formatStockTurnover(stock.turnover))
                        }
                        AiAnalysisSection(ctx.analysis)
                    }
                }
            }
        }
    }

    private fun loadDetail() {
        val code = pagerData.params.optString("code").ifEmpty {
            pagerData.params.optString("symbol")
        }.trim()
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

    private fun isFavorite(code: String): Boolean {
        favoriteVersion
        return WatchlistStore.isFavorite(code)
    }

    private fun toggleFavorite(code: String) {
        val favorite = WatchlistStore.toggle(code)
        favoriteVersion += 1
        acquireModule<BridgeModule>(BridgeModule.MODULE_NAME).log(
            "stock_detail 自选${if (favorite) "添加" else "移除"}: $code"
        )
    }
}
