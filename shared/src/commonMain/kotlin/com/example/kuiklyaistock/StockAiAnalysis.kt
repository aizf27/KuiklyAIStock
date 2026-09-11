package com.example.kuiklyaistock

import com.example.kuiklyaistock.model.AiAnalysis
import com.example.kuiklyaistock.model.AiAnalysisSource
import com.example.kuiklyaistock.model.AiRiskLevel
import com.example.kuiklyaistock.model.AiSignal
import com.example.kuiklyaistock.model.AiTrendType
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.directives.velse
import com.tencent.kuikly.core.directives.vif
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View

internal object AiQuickQuestions {
    const val WHY = "为什么这样判断？"
    const val SIGNALS = "关注什么信号？"
    const val RISKS = "主要风险是什么？"
    val ALL = listOf(WHY, SIGNALS, RISKS)
}

internal enum class AiLoadState {
    IDLE,
    LOADING,
    SUCCESS,
    FAILURE,
}

// 默认只展示核心结论，完整依据按需展开。
internal fun ViewContainer<*, *>.AiAnalysisSection(
    analysis: () -> AiAnalysis?,
    width: Float,
    loadState: () -> AiLoadState = { AiLoadState.IDLE },
    errorMessage: () -> String = { "" },
    isDetailExpanded: () -> Boolean = { false },
    expandedSignalIndex: () -> Int = { -1 },
    selectedQuickQuestion: () -> String = { "" },
    onSignalToggle: (Int) -> Unit = {},
    onQuickQuestion: (String) -> Unit = {},
    onDetailToggle: () -> Unit = {},
    onRetry: () -> Unit = {},
) {
    val contentWidth = width - StockDesignTokens.pageHorizontalPadding * 2
    View {
        attr {
            width(width)
            padding(
                left = StockDesignTokens.pageHorizontalPadding,
                right = StockDesignTokens.pageHorizontalPadding,
                top = StockDesignTokens.pageSectionSpacing,
            )
        }
        View {
            attr { flexDirectionRow(); alignItemsCenter(); marginBottom(8f) }
            Text {
                attr {
                    text("AI 分析")
                    fontSize(17f)
                    fontWeightBold()
                    color(StockDesignTokens.primaryText)
                    flex(1f)
                }
            }
            vif({ loadState() == AiLoadState.LOADING }) {
                Text {
                    attr {
                        text("正在重新分析…")
                        fontSize(11f)
                        color(StockDesignTokens.brand)
                    }
                }
            }
        }
        vif({ analysis() == null }) {
            View {
                attr {
                    width(contentWidth)
                    backgroundColor(StockDesignTokens.surface)
                    borderRadius(StockDesignTokens.sectionRadius)
                    padding(StockDesignTokens.cardPadding)
                }
                Text {
                    attr {
                        text("暂无 AI 解读")
                        fontSize(14f)
                        color(StockDesignTokens.secondaryText)
                    }
                }
            }
        }
        velse {
            vif({ analysis()?.source == AiAnalysisSource.MOCK }) {
                analysis()?.let {
                    AiAnalysisContent(it, contentWidth, loadState, errorMessage, isDetailExpanded, expandedSignalIndex, selectedQuickQuestion, onSignalToggle, onQuickQuestion, onDetailToggle, onRetry)
                }
            }
            vif({ analysis()?.source == AiAnalysisSource.CACHE }) {
                analysis()?.let {
                    AiAnalysisContent(it, contentWidth, loadState, errorMessage, isDetailExpanded, expandedSignalIndex, selectedQuickQuestion, onSignalToggle, onQuickQuestion, onDetailToggle, onRetry)
                }
            }
            vif({ analysis()?.source == AiAnalysisSource.REMOTE }) {
                analysis()?.let {
                    AiAnalysisContent(it, contentWidth, loadState, errorMessage, isDetailExpanded, expandedSignalIndex, selectedQuickQuestion, onSignalToggle, onQuickQuestion, onDetailToggle, onRetry)
                }
            }
        }
    }
}

private fun ViewContainer<*, *>.AiAnalysisContent(
    analysis: AiAnalysis,
    width: Float,
    loadState: () -> AiLoadState,
    errorMessage: () -> String,
    isDetailExpanded: () -> Boolean,
    expandedSignalIndex: () -> Int,
    selectedQuickQuestion: () -> String,
    onSignalToggle: (Int) -> Unit,
    onQuickQuestion: (String) -> Unit,
    onDetailToggle: () -> Unit,
    onRetry: () -> Unit,
) {
    AiConclusionCard(analysis, width, isDetailExpanded, onDetailToggle)
    vif({ loadState() == AiLoadState.FAILURE }) {
        AiFailureHint(width, errorMessage(), onRetry)
    }
    vif({ isDetailExpanded() }) {
        AiFactSummaryCard(analysis, width)
        AiObservationPlanCard(analysis, width)
        AiSignalSection(analysis, width, expandedSignalIndex, onSignalToggle)
        AiRiskCard(analysis, width)
        AiAnalysisFooter(analysis, width)
    }
    AiQuickQuestionSection(analysis, width, selectedQuickQuestion, onQuickQuestion)
}

private fun ViewContainer<*, *>.AiConclusionCard(
    analysis: AiAnalysis,
    width: Float,
    expanded: () -> Boolean,
    onDetailToggle: () -> Unit,
) {
    val colors = trendColors(analysis.trendType)
    val primaryRisk = analysis.primaryRisks.firstOrNull() ?: analysis.riskReminder
    View {
        attr {
            width(width)
            backgroundColor(colors.second)
            borderRadius(StockDesignTokens.sectionRadius)
            padding(StockDesignTokens.cardPadding)
        }
        View {
            attr { flexDirectionRow(); alignItemsCenter() }
            Text {
                attr {
                    text(aiSourceLabel(analysis.source))
                    fontSize(11f)
                    fontWeightBold()
                    color(StockDesignTokens.brand)
                    flex(1f)
                }
            }
            Text {
                attr {
                    text("${analysis.trendType.label} · ${analysis.applicablePeriod}")
                    fontSize(12f)
                    fontWeightBold()
                    color(colors.first)
                }
            }
        }
        Text {
            attr {
                text(analysis.trendJudgement)
                fontSize(16f)
                fontWeightBold()
                color(StockDesignTokens.primaryText)
                marginTop(10f)
            }
        }
        Text {
            attr {
                text("观察：${analysis.observationPlan?.confirmationCondition ?: analysis.focusPoint}")
                fontSize(13f)
                color(StockDesignTokens.secondaryText)
                marginTop(10f)
            }
        }
        Text {
            attr {
                text("风险：$primaryRisk")
                fontSize(13f)
                color(StockDesignTokens.secondaryText)
                marginTop(6f)
            }
        }
        View {
            attr {
                height(StockDesignTokens.minimumTouchTarget)
                justifyContentCenter()
                marginTop(6f)
            }
            event { click { onDetailToggle() } }
            vif({ expanded() }) {
                Text {
                    attr { text("收起完整分析"); fontSize(13f); fontWeightBold(); color(StockDesignTokens.brand) }
                }
            }
            velse {
                Text {
                    attr { text("查看完整分析"); fontSize(13f); fontWeightBold(); color(StockDesignTokens.brand) }
                }
            }
        }
    }
}

private fun ViewContainer<*, *>.AiFailureHint(width: Float, message: String, onRetry: () -> Unit) {
    View {
        attr {
            width(width)
            backgroundColor(StockDesignTokens.surface)
            borderRadius(8f)
            padding(left = 12f, right = 12f, top = 8f, bottom = 8f)
            marginTop(8f)
            flexDirectionRow()
            alignItemsCenter()
        }
        Text {
            attr {
                text(message.ifBlank { "远程分析失败，当前展示演示解读" })
                fontSize(11f)
                color(StockDesignTokens.secondaryText)
                flex(1f)
            }
        }
        View {
            attr { width(80f); height(StockDesignTokens.minimumTouchTarget); allCenter() }
            event { click { onRetry() } }
            Text {
                attr { text("重新分析"); fontSize(12f); fontWeightBold(); color(StockDesignTokens.brand) }
            }
        }
    }
}

private fun ViewContainer<*, *>.AiFactSummaryCard(analysis: AiAnalysis, width: Float) {
    View {
        attr {
            width(width)
            backgroundColor(StockDesignTokens.surface)
            borderRadius(StockDesignTokens.sectionRadius)
            padding(StockDesignTokens.cardPadding)
            marginTop(12f)
        }
        AiCardTitle("事实摘要", StockDesignTokens.primaryText)
        Text {
            attr {
                text(analysis.factSummary)
                fontSize(13f)
                color(StockDesignTokens.secondaryText)
                marginTop(8f)
            }
        }
    }
}

private fun ViewContainer<*, *>.AiObservationPlanCard(analysis: AiAnalysis, width: Float) {
    View {
        attr {
            width(width)
            backgroundColor(StockDesignTokens.aiPlanBackground)
            borderRadius(StockDesignTokens.sectionRadius)
            padding(StockDesignTokens.cardPadding)
            marginTop(12f)
        }
        AiCardTitle("条件式观察计划", StockDesignTokens.brand)
        val plan = analysis.observationPlan
        if (plan == null) {
            Text {
                attr {
                    text("暂无明确观察计划")
                    fontSize(14f)
                    color(StockDesignTokens.secondaryText)
                    marginTop(10f)
                }
            }
            Text {
                attr {
                    text(analysis.focusPoint)
                    fontSize(13f)
                    color(StockDesignTokens.secondaryText)
                    marginTop(6f)
                }
            }
        } else {
            AiPlanItem("关注区间", "${formatStockPrice(plan.focusRangeLow)} — ${formatStockPrice(plan.focusRangeHigh)}", true)
            AiPlanItem("确认条件", plan.confirmationCondition)
            plan.confirmationPrice?.let { AiPlanItem("确认价位", formatStockPrice(it), true) }
            plan.referenceTarget?.let { AiPlanItem("参考目标", formatStockPrice(it), true) }
            AiPlanItem("风险边界", plan.riskBoundary)
        }
    }
}

private fun ViewContainer<*, *>.AiPlanItem(title: String, content: String, emphasize: Boolean = false) {
    View {
        attr { marginTop(10f) }
        Text {
            attr {
                text(title)
                fontSize(11f)
                fontWeightBold()
                color(StockDesignTokens.secondaryText)
            }
        }
        Text {
            attr {
                text(content)
                fontSize(if (emphasize) 16f else 13f)
                if (emphasize) fontWeightBold()
                color(if (emphasize) StockDesignTokens.primaryText else StockDesignTokens.secondaryText)
                marginTop(4f)
            }
        }
    }
}

private fun ViewContainer<*, *>.AiSignalSection(
    analysis: AiAnalysis,
    width: Float,
    expandedSignalIndex: () -> Int,
    onSignalToggle: (Int) -> Unit,
) {
    View {
        attr {
            width(width)
            backgroundColor(StockDesignTokens.surface)
            borderRadius(StockDesignTokens.sectionRadius)
            padding(StockDesignTokens.cardPadding)
            marginTop(12f)
        }
        AiCardTitle("信号解读", StockDesignTokens.primaryText)
        if (analysis.signals.isEmpty()) {
            Text {
                attr {
                    text("暂无可用信号依据")
                    fontSize(14f)
                    color(StockDesignTokens.secondaryText)
                    marginTop(10f)
                }
            }
        } else {
            analysis.signals.forEachIndexed { index, signal ->
                AiSignalItem(
                    signal = signal,
                    expanded = { expandedSignalIndex() == index },
                    onClick = { onSignalToggle(index) },
                )
            }
        }
    }
}

private fun ViewContainer<*, *>.AiSignalItem(
    signal: AiSignal,
    expanded: () -> Boolean,
    onClick: () -> Unit,
) {
    val colors = signalColors(signal.status)
    View {
        attr {
            backgroundColor(StockDesignTokens.pageBackground)
            borderRadius(10f)
            padding(left = 12f, right = 12f, top = 12f, bottom = 12f)
            marginTop(10f)
        }
        event { click { onClick() } }
        View {
            attr { flexDirectionRow(); alignItemsCenter() }
            View {
                attr {
                    backgroundColor(colors.second)
                    borderRadius(9f)
                    padding(left = 8f, right = 8f, top = 3f, bottom = 3f)
                    marginRight(8f)
                }
                Text {
                    attr {
                        text(signal.status)
                        fontSize(11f)
                        fontWeightBold()
                        color(colors.first)
                    }
                }
            }
            Text {
                attr {
                    text(signal.title)
                    fontSize(14f)
                    fontWeightBold()
                    color(StockDesignTokens.primaryText)
                    flex(1f)
                }
            }
            Text {
                attr {
                    text(if (expanded()) "收起" else "看依据")
                    fontSize(12f)
                    color(StockDesignTokens.brand)
                }
            }
        }
        Text {
            attr {
                text(signal.explanation)
                fontSize(13f)
                color(StockDesignTokens.secondaryText)
                marginTop(8f)
            }
        }
        vif({ expanded() }) {
            View {
                attr {
                    height(1f)
                    backgroundColor(StockDesignTokens.divider)
                    marginTop(10f)
                }
            }
            Text {
                attr {
                    text("对应依据：${signal.evidence}")
                    fontSize(12f)
                    color(StockDesignTokens.primaryText)
                    marginTop(10f)
                }
            }
        }
    }
}

private fun ViewContainer<*, *>.AiRiskCard(analysis: AiAnalysis, width: Float) {
    val colors = riskColors(analysis.riskLevel)
    View {
        attr {
            width(width)
            backgroundColor(colors.second)
            borderRadius(StockDesignTokens.sectionRadius)
            padding(StockDesignTokens.cardPadding)
            marginTop(12f)
        }
        View {
            attr { flexDirectionRow(); alignItemsCenter() }
            AiCardTitle("风险提醒", colors.first, true)
            View {
                attr {
                    backgroundColor(StockDesignTokens.surface)
                    borderRadius(10f)
                    padding(left = 8f, right = 8f, top = 3f, bottom = 3f)
                }
                Text {
                    attr {
                        text(analysis.riskLevel.label)
                        fontSize(11f)
                        fontWeightBold()
                        color(colors.first)
                    }
                }
            }
        }
        if (analysis.primaryRisks.isEmpty()) {
            Text {
                attr {
                    text(analysis.riskReminder)
                    fontSize(13f)
                    color(StockDesignTokens.primaryText)
                    marginTop(10f)
                }
            }
        } else {
            analysis.primaryRisks.forEach { risk ->
                Text {
                    attr {
                        text("• $risk")
                        fontSize(13f)
                        color(StockDesignTokens.primaryText)
                        marginTop(8f)
                    }
                }
            }
        }
        Text {
            attr {
                text("判断失效条件")
                fontSize(11f)
                fontWeightBold()
                color(colors.first)
                marginTop(12f)
            }
        }
        Text {
            attr {
                text(if (analysis.invalidationCondition.isNotEmpty()) analysis.invalidationCondition else analysis.riskReminder)
                fontSize(13f)
                color(StockDesignTokens.primaryText)
                marginTop(4f)
            }
        }
    }
}

private fun ViewContainer<*, *>.AiQuickQuestionSection(
    analysis: AiAnalysis,
    width: Float,
    selectedQuickQuestion: () -> String,
    onQuickQuestion: (String) -> Unit,
) {
    View {
        attr {
            width(width)
            backgroundColor(StockDesignTokens.surface)
            borderRadius(StockDesignTokens.sectionRadius)
            padding(StockDesignTokens.cardPadding)
            marginTop(12f)
        }
        AiCardTitle("快捷追问", StockDesignTokens.primaryText)
        Text {
            attr {
                text("点击查看基于当前结构化数据的预设解读")
                fontSize(12f)
                color(StockDesignTokens.tertiaryText)
                marginTop(4f)
            }
        }
        AiQuickQuestions.ALL.forEach { question ->
            val selected = selectedQuickQuestion() == question
            View {
                attr {
                    height(StockDesignTokens.minimumTouchTarget)
                    backgroundColor(if (selected) StockDesignTokens.brandBackground else StockDesignTokens.aiControlBackground)
                    borderRadius(8f)
                    padding(left = 12f, right = 12f)
                    justifyContentCenter()
                    marginTop(8f)
                }
                event { click { onQuickQuestion(question) } }
                Text {
                    attr {
                        text(question)
                        fontSize(13f)
                        fontWeightBold()
                        color(if (selected) StockDesignTokens.brand else StockDesignTokens.primaryText)
                    }
                }
            }
        }
        vif({ selectedQuickQuestion().isNotEmpty() }) {
            View {
                attr {
                    backgroundColor(StockDesignTokens.brandBackground)
                    borderRadius(8f)
                    padding(12f)
                    marginTop(10f)
                }
                Text {
                    attr {
                        text(if (analysis.source == AiAnalysisSource.REMOTE) "基于本次分析" else "预设解读")
                        fontSize(11f)
                        fontWeightBold()
                        color(StockDesignTokens.brand)
                    }
                }
                Text {
                    attr {
                        text(quickAnswer(analysis, selectedQuickQuestion()))
                        fontSize(13f)
                        color(StockDesignTokens.primaryText)
                        marginTop(6f)
                    }
                }
            }
        }
    }
}

private fun ViewContainer<*, *>.AiAnalysisFooter(analysis: AiAnalysis, width: Float) {
    View {
        attr {
            width(width)
            padding(top = 12f, bottom = 4f)
        }
        Text {
            attr {
                text(if (analysis.isDemo) "分析使用 Mock 行情与走势样本" else "分析仅使用真实行情快照，未包含分时与日 K 数据")
                fontSize(11f)
                color(StockDesignTokens.tertiaryText)
            }
        }
        Text {
            attr {
                text("行情时间：${analysis.updatedAt}")
                fontSize(11f)
                color(StockDesignTokens.tertiaryText)
                marginTop(4f)
            }
        }
        if (analysis.generatedAt.isNotEmpty()) {
            Text {
                attr {
                    text("AI 生成时间：${analysis.generatedAt}")
                    fontSize(11f)
                    color(StockDesignTokens.tertiaryText)
                    marginTop(4f)
                }
            }
        }
        Text {
            attr {
                text("以上内容仅用于产品功能演示，不构成投资建议或收益承诺。")
                fontSize(11f)
                color(StockDesignTokens.tertiaryText)
                marginTop(4f)
            }
        }
    }
}

private fun ViewContainer<*, *>.AiCardTitle(
    title: String,
    color: Color,
    fillRemaining: Boolean = false,
) {
    Text {
        attr {
            text(title)
            fontSize(14f)
            fontWeightBold()
            color(color)
            if (fillRemaining) flex(1f)
        }
    }
}

private fun quickAnswer(analysis: AiAnalysis, question: String): String = when (question) {
    AiQuickQuestions.WHY -> "${analysis.trendJudgement}\n${analysis.evidenceSummary}"
    AiQuickQuestions.SIGNALS -> if (analysis.signals.isEmpty()) {
        "当前没有可展开的结构化信号。"
    } else {
        analysis.signals.joinToString("\n") { "${it.title}（${it.status}）：${it.explanation}" }
    }
    AiQuickQuestions.RISKS -> buildString {
        if (analysis.primaryRisks.isEmpty()) append(analysis.riskReminder)
        else append(analysis.primaryRisks.joinToString("；"))
        if (analysis.invalidationCondition.isNotEmpty()) append("\n失效条件：${analysis.invalidationCondition}")
    }
    else -> ""
}

private fun trendColors(type: AiTrendType): Pair<Color, Color> = when (type) {
    AiTrendType.STRONG -> StockDesignTokens.aiStrongText to StockDesignTokens.aiStrongBackground
    AiTrendType.SIDEWAYS -> StockDesignTokens.aiSidewaysText to StockDesignTokens.aiSidewaysBackground
    AiTrendType.WEAK -> StockDesignTokens.aiWeakText to StockDesignTokens.aiWeakBackground
}

private fun riskColors(level: AiRiskLevel): Pair<Color, Color> = when (level) {
    AiRiskLevel.LOW -> StockDesignTokens.aiRiskLowText to StockDesignTokens.aiRiskLowBackground
    AiRiskLevel.MEDIUM -> StockDesignTokens.aiRiskMediumText to StockDesignTokens.aiRiskMediumBackground
    AiRiskLevel.HIGH -> StockDesignTokens.aiRiskHighText to StockDesignTokens.aiRiskHighBackground
}

private fun signalColors(status: String): Pair<Color, Color> = when (status) {
    "积极", "回升", "向上" -> StockDesignTokens.aiStrongText to StockDesignTokens.aiStrongBackground
    "谨慎", "回落", "向下" -> StockDesignTokens.aiWeakText to StockDesignTokens.aiWeakBackground
    else -> StockDesignTokens.aiSidewaysText to StockDesignTokens.aiSidewaysBackground
}

private fun aiSourceLabel(source: AiAnalysisSource): String = when (source) {
    AiAnalysisSource.MOCK -> "演示分析"
    AiAnalysisSource.REMOTE -> "AI 生成"
    AiAnalysisSource.CACHE -> "AI 缓存"
}
