package com.example.kuiklyaistock

import com.example.kuiklyaistock.model.AiMarketOverview
import com.example.kuiklyaistock.model.AiStockInsight
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.views.Scroller
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View

// 根 Tab 的 AI 内容，不再作为独立路由页面注册。
internal fun ViewContainer<*, *>.StockAiContent(
    overview: AiMarketOverview?,
    insights: List<AiStockInsight>,
    onStockClick: (String) -> Unit,
) {
    Scroller {
        attr { flex(1f); padding(left = StockDesignTokens.pageHorizontalPadding, right = StockDesignTokens.pageHorizontalPadding, top = 12f, bottom = 20f) }
        Text { attr { text("市场观点"); fontSize(20f); fontWeightBold(); color(StockDesignTokens.primaryText) } }
        Text { attr { text("观点参考 · 演示数据，不构成投资建议"); fontSize(12f); color(StockDesignTokens.secondaryText); marginTop(4f); marginBottom(12f) } }
        overview?.let { AiMarketOverviewCard(it) }
        Text { attr { text("重点股票"); fontSize(17f); fontWeightBold(); color(StockDesignTokens.primaryText); marginTop(20f); marginBottom(8f) } }
        if (insights.isEmpty()) {
            StockInlineEmptyState("暂无重点股票解读", "返回行情") { }
        } else {
            insights.forEach { insight -> AiStockInsightCard(insight) { onStockClick(insight.quote.code) } }
        }
    }
}

private fun ViewContainer<*, *>.AiMarketOverviewCard(overview: AiMarketOverview) {
    View {
        attr { backgroundColor(StockDesignTokens.surface); borderRadius(StockDesignTokens.sectionRadius); padding(16f) }
        Text { attr { text("市场事实与观点"); fontSize(12f); fontWeightBold(); color(StockDesignTokens.brand) } }
        Text { attr { text(overview.title); fontSize(19f); fontWeightBold(); color(StockDesignTokens.primaryText); marginTop(6f) } }
        Text { attr { text("观点判断：${overview.sentiment}"); fontSize(13f); color(StockDesignTokens.secondaryText); marginTop(10f) } }
        Text { attr { text("事实摘要：${overview.summary}"); fontSize(14f); color(StockDesignTokens.primaryText); marginTop(6f) } }
        Text { attr { text("风险提示：${overview.riskTip}"); fontSize(13f); color(StockDesignTokens.risk); marginTop(10f) } }
        Text { attr { text("数据截至 ${overview.updatedAt} · 演示数据"); fontSize(11f); color(StockDesignTokens.tertiaryText); marginTop(10f) } }
    }
}

private fun ViewContainer<*, *>.AiStockInsightCard(insight: AiStockInsight, onClick: () -> Unit) {
    View {
        attr { backgroundColor(StockDesignTokens.surface); borderRadius(StockDesignTokens.sectionRadius); padding(14f); marginBottom(8f) }
        event { click { onClick() } }
        Text { attr { text(insight.quote.name); fontSize(16f); fontWeightBold(); color(StockDesignTokens.primaryText) } }
        Text { attr { text("${insight.quote.code} · ${insight.analysis.applicablePeriod}"); fontSize(11f); color(StockDesignTokens.secondaryText); marginTop(3f) } }
        Text { attr { text("观点参考：${insight.analysis.trendJudgement}"); fontSize(14f); color(StockDesignTokens.primaryText); marginTop(10f) } }
        Text { attr { text("信号解读：${insight.analysis.signalInterpretation}"); fontSize(12f); color(StockDesignTokens.secondaryText); marginTop(6f) } }
        Text { attr { text("风险：${insight.analysis.riskReminder}"); fontSize(12f); color(StockDesignTokens.risk); marginTop(6f) } }
    }
}
