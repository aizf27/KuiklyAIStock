package com.example.kuiklyaistock

import com.example.kuiklyaistock.base.BasePager
import com.example.kuiklyaistock.base.BridgeModule
import com.example.kuiklyaistock.model.AiMarketOverview
import com.example.kuiklyaistock.model.AiStockInsight
import com.example.kuiklyaistock.repository.MockStockRepository
import com.example.kuiklyaistock.repository.StockRepository
import com.tencent.kuikly.core.annotations.Page
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.ViewBuilder
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.reactive.handler.observable
import com.tencent.kuikly.core.views.Scroller
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View

@Page("stock_ai", supportInLocal = true)
internal class StockAiPage : BasePager() {
    private val repository: StockRepository = MockStockRepository()
    private var loading by observable(true)
    private var overview by observable<AiMarketOverview?>(null)
    private var insights by observable(emptyList<AiStockInsight>())

    override fun created() {
        super.created()
        overview = repository.getAiMarketOverview()
        insights = repository.getAiInsights()
        loading = false
        acquireModule<BridgeModule>(BridgeModule.MODULE_NAME).log(
            "stock_ai 解读加载成功: ${insights.size}"
        )
    }

    override fun body(): ViewBuilder {
        val ctx = this
        return {
            attr {
                backgroundColor(Color(0xFFF8FAFC))
            }
            RouterNavBar {
                attr {
                    title = "AI 解读"
                    backDisable = true
                }
            }
            if (ctx.loading) {
                StockStateText("正在生成 AI 解读...")
            } else {
                Scroller {
                    attr {
                        flex(1f)
                        padding(left = 16f, right = 16f, top = 18f, bottom = 20f)
                    }
                    Text {
                        attr {
                            text("今日 AI 观点")
                            fontSize(24f)
                            fontWeightBold()
                            color(Color(0xFF111827))
                        }
                    }
                    Text {
                        attr {
                            text("基于固定 Mock 行情生成，仅供演示")
                            fontSize(12f)
                            color(Color(0xFF6B7280))
                            marginTop(4f)
                            marginBottom(14f)
                        }
                    }
                    ctx.overview?.let { AiMarketOverviewCard(it) }
                    Text {
                        attr {
                            text("重点股票")
                            fontSize(17f)
                            fontWeightBold()
                            color(Color(0xFF1F2937))
                            marginTop(18f)
                            marginBottom(10f)
                        }
                    }
                    if (ctx.insights.isEmpty()) {
                        Text {
                            attr {
                                text("暂无重点股票解读")
                                fontSize(14f)
                                color(Color(0xFF6B7280))
                            }
                        }
                    } else {
                        ctx.insights.forEach { insight ->
                            AiStockInsightCard(insight) {
                                ctx.openStockPage("stock_detail", insight.quote.code)
                            }
                        }
                    }
                }
            }
            StockBottomBar(StockTabs.AI) { tab ->
                if (tab == StockTabs.MARKET) {
                    ctx.openStockPage("stock_home")
                } else if (tab == StockTabs.WATCHLIST) {
                    ctx.openStockPage("stock_home")
                }
            }
        }
    }
}

private fun ViewContainer<*, *>.AiMarketOverviewCard(overview: AiMarketOverview) {
    View {
        attr {
            backgroundColor(Color(0xFFEFF6FF))
            borderRadius(8f)
            padding(16f)
        }
        Text {
            attr {
                text(overview.sentiment)
                fontSize(13f)
                fontWeightBold()
                color(Color(0xFF2563EB))
            }
        }
        Text {
            attr {
                text(overview.title)
                fontSize(20f)
                fontWeightBold()
                color(Color(0xFF1E3A8A))
                marginTop(6f)
            }
        }
        Text {
            attr {
                text(overview.summary)
                fontSize(14f)
                color(Color(0xFF334155))
                marginTop(10f)
            }
        }
        Text {
            attr {
                text("风险提示：${overview.riskTip}")
                fontSize(13f)
                color(Color(0xFFB45309))
                marginTop(10f)
            }
        }
        Text {
            attr {
                text("更新 ${overview.updatedAt}")
                fontSize(11f)
                color(Color(0xFF64748B))
                marginTop(10f)
            }
        }
    }
}

private fun ViewContainer<*, *>.AiStockInsightCard(
    insight: AiStockInsight,
    onClick: () -> Unit,
) {
    View {
        attr {
            backgroundColor(Color.WHITE)
            borderRadius(8f)
            padding(14f)
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
                attr { flex(1f) }
                Text {
                    attr {
                        text(insight.quote.name)
                        fontSize(16f)
                        fontWeightBold()
                        color(Color(0xFF111827))
                    }
                }
                Text {
                    attr {
                        text(insight.quote.code)
                        fontSize(11f)
                        color(Color(0xFF6B7280))
                        marginTop(3f)
                    }
                }
            }
            Text {
                attr {
                    text(formatStockPercent(insight.quote.changePercent))
                    fontSize(15f)
                    fontWeightBold()
                    color(stockChangeColor(insight.quote.change))
                }
            }
        }
        Text {
            attr {
                text(insight.analysis.trendJudgement)
                fontSize(14f)
                color(Color(0xFF374151))
                marginTop(10f)
            }
        }
        Text {
            attr {
                text("风险：${insight.analysis.riskReminder}")
                fontSize(12f)
                color(Color(0xFFB45309))
                marginTop(6f)
            }
        }
    }
}
