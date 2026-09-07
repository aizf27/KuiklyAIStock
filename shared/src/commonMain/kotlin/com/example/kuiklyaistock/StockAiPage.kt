package com.example.kuiklyaistock

import com.example.kuiklyaistock.base.BasePager
import com.tencent.kuikly.core.annotations.Page
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.ViewBuilder
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.views.Scroller
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View

// AI 解读独立入口，具体内容在阶段 8 接入 Repository。
@Page("stock_ai", supportInLocal = true)
internal class StockAiPage : BasePager() {
    private var selectedTab = StockTabs.AI

    override fun body(): ViewBuilder {
        val ctx = this
        return {
            RouterNavBar {
                attr {
                    title = "AI 解读"
                    backDisable = true
                }
            }
            Scroller {
                attr {
                    flex(1f)
                    padding(left = 16f, right = 16f, top = 18f, bottom = 20f)
                }
                View {
                    attr {
                        backgroundColor(Color(0xFFEFF6FF))
                        borderRadius(8f)
                        padding(16f)
                    }
                    Text {
                        attr {
                            text("AI 市场解读")
                            fontSize(22f)
                            fontWeightBold()
                            color(Color(0xFF1E3A8A))
                        }
                    }
                    Text {
                        attr {
                            text("重点股票趋势、风险与行情总结")
                            fontSize(14f)
                            color(Color(0xFF475569))
                            marginTop(6f)
                        }
                    }
                }
                Text {
                    attr {
                        text("AI 解读内容将在下一阶段接入 Mock Repository")
                        fontSize(14f)
                        color(Color(0xFF6B7280))
                        marginTop(20f)
                    }
                }
            }
            StockBottomBar(ctx.selectedTab) { tab ->
                if (tab == StockTabs.MARKET) {
                    ctx.openStockPage("stock_home")
                } else if (tab == StockTabs.WATCHLIST) {
                    ctx.openStockPage("stock_home")
                }
            }
        }
    }
}
