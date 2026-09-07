package com.example.kuiklyaistock

import com.example.kuiklyaistock.model.AiAnalysis
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View

// 详情页的 AI 分析信息卡片。
internal fun ViewContainer<*, *>.AiAnalysisSection(analysis: AiAnalysis?) {
    Text {
        attr {
            text("AI 分析与解读")
            fontSize(17f)
            fontWeightBold()
            color(Color(0xFF1F2937))
            marginTop(20f)
            marginBottom(8f)
        }
    }
    if (analysis == null) {
        Text {
            attr {
                text("暂无 AI 分析")
                fontSize(14f)
                color(Color(0xFF6B7280))
            }
        }
    } else {
        AnalysisCard("趋势判断", analysis.trendJudgement, Color(0xFFEFF6FF))
        AnalysisCard("操作提示", analysis.operationTip, Color(0xFFF0FDF4))
        AnalysisCard("风险提醒", analysis.riskReminder, Color(0xFFFFF7ED))
        AnalysisCard("信号解读", analysis.signalInterpretation, Color(0xFFF3F4F6))
        AnalysisCard("行情总结", analysis.marketSummary, Color(0xFFF8FAFC))
    }
}

private fun ViewContainer<*, *>.AnalysisCard(title: String, content: String, background: Color) {
    View {
        attr {
            backgroundColor(background)
            borderRadius(8f)
            padding(left = 14f, right = 14f, top = 12f, bottom = 12f)
            marginBottom(8f)
        }
        Text {
            attr {
                text(title)
                fontSize(13f)
                fontWeightBold()
                color(Color(0xFF374151))
            }
        }
        Text {
            attr {
                text(content)
                fontSize(14f)
                color(Color(0xFF4B5563))
                marginTop(5f)
            }
        }
    }
}
