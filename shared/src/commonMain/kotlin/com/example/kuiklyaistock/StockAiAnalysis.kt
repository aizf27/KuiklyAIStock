package com.example.kuiklyaistock

import com.example.kuiklyaistock.model.AiAnalysis
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View

// 详情页将市场事实、AI 观点与风险分层呈现，避免被理解为确定性投资建议。
internal fun ViewContainer<*, *>.AiAnalysisSection(analysis: AiAnalysis?) {
    Text {
        attr {
            text("AI 观点参考")
            fontSize(17f)
            fontWeightBold()
            color(StockDesignTokens.primaryText)
            marginTop(StockDesignTokens.pageSectionSpacing)
            marginBottom(8f)
        }
    }
    if (analysis == null) {
        Text {
            attr {
                text("暂无 AI 解读")
                fontSize(14f)
                color(StockDesignTokens.secondaryText)
            }
        }
    } else {
        View {
            attr {
                backgroundColor(StockDesignTokens.surface)
                borderRadius(StockDesignTokens.sectionRadius)
                padding(StockDesignTokens.cardPadding)
            }
            Text {
                attr {
                    text("观点参考 · ${analysis.applicablePeriod}${if (analysis.isDemo) " · 演示数据" else ""}")
                    fontSize(12f)
                    fontWeightBold()
                    color(StockDesignTokens.brand)
                }
            }
            AiAnalysisItem("事实摘要", analysis.factSummary)
            AiAnalysisItem("观点判断", analysis.trendJudgement)
            AiAnalysisItem("关注要点", analysis.focusPoint)
            AiAnalysisItem("信号解读", analysis.signalInterpretation)
            AiAnalysisItem("依据", analysis.evidenceSummary)
            AiAnalysisItem("风险提示", analysis.riskReminder, StockDesignTokens.risk)
            Text {
                attr {
                    text("数据截至 ${analysis.updatedAt}")
                    fontSize(11f)
                    color(StockDesignTokens.tertiaryText)
                    marginTop(10f)
                }
            }
        }
    }
}

private fun ViewContainer<*, *>.AiAnalysisItem(
    title: String,
    content: String,
    titleColor: com.tencent.kuikly.core.base.Color = StockDesignTokens.secondaryText,
) {
    View {
        attr { marginTop(12f) }
        Text {
            attr {
                text(title)
                fontSize(12f)
                fontWeightBold()
                color(titleColor)
            }
        }
        Text {
            attr {
                text(content)
                fontSize(14f)
                color(StockDesignTokens.primaryText)
                marginTop(4f)
            }
        }
    }
}
