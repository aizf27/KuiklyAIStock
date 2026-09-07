package com.example.kuiklyaistock

import com.example.kuiklyaistock.base.BasePager
import com.example.kuiklyaistock.model.StockQuote
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
    private var errorMessage by observable("")

    override fun created() {
        super.created()
        loadQuotes()
    }

    override fun body(): ViewBuilder {
        val ctx = this
        return {
            RouterNavBar {
                attr {
                    title = "AI 股票行情"
                    backDisable = true
                }
            }
            if (ctx.loading) {
                StateText("正在加载行情...")
            } else if (ctx.errorMessage.isNotEmpty()) {
                StateText(ctx.errorMessage)
            } else {
                Scroller {
                    attr {
                        flex(1f)
                        padding(left = 16f, right = 16f, top = 12f, bottom = 20f)
                    }
                    Text {
                        attr {
                            text("自选行情")
                            fontSize(24f)
                            fontWeightBold()
                            color(Color(0xFF1F2937))
                            marginBottom(4f)
                        }
                    }
                    Text {
                        attr {
                            text("实时 Mock 数据 · ${ctx.quotes.size} 只股票")
                            fontSize(13f)
                            color(Color(0xFF6B7280))
                            marginBottom(12f)
                        }
                    }
                    ctx.quotes.forEach { quote ->
                        StockQuoteRow(quote) {
                            ctx.openDetail(quote.code)
                        }
                    }
                }
            }
        }
    }

    private fun loadQuotes() {
        val result = repository.getQuotes()
        quotes = result
        errorMessage = if (result.isEmpty()) "暂无行情数据" else ""
        loading = false
    }

    private fun openDetail(code: String) {
        val pageData = JSONObject().apply {
            put("code", code)
            put("symbol", code)
        }
        acquireModule<RouterModule>(RouterModule.MODULE_NAME).openPage("stock_detail", pageData)
    }
}

private fun ViewContainer<*, *>.StockQuoteRow(quote: StockQuote, onClick: () -> Unit) {
    val changeColor = if (quote.change >= 0) Color(0xFFE53E3E) else Color(0xFF16A34A)
    View {
        attr {
            backgroundColor(Color.WHITE)
            borderRadius(8f)
            padding(left = 14f, right = 14f, top = 14f, bottom = 14f)
            marginBottom(10f)
        }
        event {
            click { onClick() }
        }
        View {
            attr {
                flexDirectionRow()
                alignItemsCenter()
            }
            View {
                attr {
                    flex(1f)
                }
                Text {
                    attr {
                        text(quote.name)
                        fontSize(17f)
                        fontWeightBold()
                        color(Color(0xFF111827))
                    }
                }
                Text {
                    attr {
                        text(quote.code)
                        fontSize(12f)
                        color(Color(0xFF6B7280))
                        marginTop(4f)
                    }
                }
            }
            View {
                attr {
                    width(92f)
                    alignItemsFlexEnd()
                }
                Text {
                    attr {
                        text(formatPrice(quote.price))
                        fontSize(18f)
                        fontWeightBold()
                        color(Color(0xFF111827))
                    }
                }
                Text {
                    attr {
                        text("${formatSigned(quote.change)}  ${formatPercent(quote.changePercent)}")
                        fontSize(12f)
                        color(changeColor)
                        marginTop(4f)
                    }
                }
            }
        }
    }
}

private fun ViewContainer<*, *>.StateText(message: String) {
    View {
        attr {
            flex(1f)
            allCenter()
            padding(20f)
        }
        Text {
            attr {
                text(message)
                fontSize(15f)
                color(Color(0xFF6B7280))
            }
        }
    }
}

private fun formatPrice(value: Double): String = value.toString()

private fun formatSigned(value: Double): String = if (value >= 0) "+${formatPrice(value)}" else formatPrice(value)

private fun formatPercent(value: Double): String = if (value >= 0) "+${formatPrice(value)}%" else "${formatPrice(value)}%"
