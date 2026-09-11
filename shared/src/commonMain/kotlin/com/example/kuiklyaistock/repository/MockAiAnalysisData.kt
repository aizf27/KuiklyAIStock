package com.example.kuiklyaistock.repository

import com.example.kuiklyaistock.model.AiAnalysis
import com.example.kuiklyaistock.model.AiObservationPlan
import com.example.kuiklyaistock.model.AiRiskLevel
import com.example.kuiklyaistock.model.AiSignal
import com.example.kuiklyaistock.model.AiTrendType
import com.example.kuiklyaistock.model.StockDetail
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round

// 结构化分析只依赖同一份详情 Mock，后续可整体替换为真实 AI 服务结果。
internal fun createMockAiAnalysis(detail: StockDetail): AiAnalysis {
    val trendType = when {
        detail.quote.changePercent >= 2.0 -> AiTrendType.STRONG
        detail.quote.changePercent < 0.0 -> AiTrendType.WEAK
        else -> AiTrendType.SIDEWAYS
    }
    val riskLevel = when {
        trendType == AiTrendType.WEAK && detail.quote.changePercent <= -2.0 -> AiRiskLevel.HIGH
        trendType == AiTrendType.SIDEWAYS && abs(detail.quote.changePercent) < 1.0 -> AiRiskLevel.LOW
        else -> AiRiskLevel.MEDIUM
    }
    val range = max(0.01, detail.high - detail.low)
    val observationPlan = createObservationPlan(detail, trendType, range)
    val signals = createSignals(detail, trendType)
    val primaryRisks = when (trendType) {
        AiTrendType.STRONG -> listOf("短线涨幅较大，次日波动可能放大", "若成交活跃度下降，当前强势可能难以延续")
        AiTrendType.SIDEWAYS -> listOf("价格仍在当日波动区间内，方向尚未确认", "缺少历史量能对比，不能判断是否有效放量")
        AiTrendType.WEAK -> listOf("价格尚未收复昨收，短线承压", "若继续跌破当日低点，弱势范围可能扩大")
    }
    val invalidationCondition = when (trendType) {
        AiTrendType.STRONG -> "若价格跌破 ${price(detail.previousClose)} 并持续运行在昨收下方，则偏强判断失效。"
        AiTrendType.SIDEWAYS -> "若价格有效突破 ${price(detail.high)} 或跌破 ${price(detail.low)}，则当前震荡判断需要重新评估。"
        AiTrendType.WEAK -> "若价格重新站稳 ${price(detail.previousClose)} 并保持，则偏弱判断失效。"
    }
    val trendJudgement = when (trendType) {
        AiTrendType.STRONG -> "样本价格运行在昨收上方，分时与日 K 收尾方向偏强，但仍需确认持续性。"
        AiTrendType.SIDEWAYS -> "样本价格围绕昨收窄幅波动，暂未形成明确单边方向。"
        AiTrendType.WEAK -> "样本价格低于昨收，短线动能偏弱，优先等待企稳信号。"
    }
    val focusPoint = when (trendType) {
        AiTrendType.STRONG -> "若价格能守住昨收上方并再次接近当日高点，可继续观察强势延续。"
        AiTrendType.SIDEWAYS -> "关注当日高低点构成的区间，等待价格离开区间后再判断方向。"
        AiTrendType.WEAK -> "先观察是否停止创新低，并尝试收复昨收；条件未满足前不下确定结论。"
    }
    val signalInterpretation = signals.joinToString("；") { "${it.title}：${it.explanation}" }
    val riskReminder = primaryRisks.joinToString("；") + "。本页为 AI 分析演示，不构成投资建议。"

    return AiAnalysis(
        trendJudgement = trendJudgement,
        focusPoint = focusPoint,
        riskReminder = riskReminder,
        signalInterpretation = signalInterpretation,
        factSummary = "${detail.quote.name}样本价 ${price(detail.quote.price)}，较昨收${signed(detail.quote.change)}（${signed(detail.quote.changePercent)}%），当日区间 ${price(detail.low)}—${price(detail.high)}。",
        applicablePeriod = "短线观察（1—5 个交易日）",
        evidenceSummary = "依据同一份 Mock 行情中的昨收、当日高低点、分时和日 K 样本；当前 Mock 未提供资金流向及历史均线对比。",
        updatedAt = detail.quote.updatedAt,
        isDemo = true,
        trendType = trendType,
        riskLevel = riskLevel,
        primaryRisks = primaryRisks,
        invalidationCondition = invalidationCondition,
        observationPlan = observationPlan,
        signals = signals,
    )
}

private fun createObservationPlan(
    detail: StockDetail,
    trendType: AiTrendType,
    range: Double,
): AiObservationPlan? = when (trendType) {
    AiTrendType.STRONG -> {
        val lower = round2(max(detail.previousClose, detail.low))
        val upper = round2(max(lower, detail.quote.price))
        AiObservationPlan(
            focusRangeLow = lower,
            focusRangeHigh = upper,
            confirmationCondition = "若价格保持在关注区间上方，并再次接近当日高点，则关注强势能否延续。",
            confirmationPrice = detail.high,
            referenceTarget = null,
            riskBoundary = "若跌回昨收 ${price(detail.previousClose)} 下方，则停止沿用当前偏强观察计划。",
        )
    }
    AiTrendType.SIDEWAYS -> {
        val lower = round2(max(detail.low, detail.quote.price - range * 0.25))
        val upper = round2(max(lower, min(detail.high, detail.quote.price + range * 0.25)))
        AiObservationPlan(
            focusRangeLow = lower,
            focusRangeHigh = upper,
            confirmationCondition = "若价格脱离关注区间并站稳当日高点一侧，再关注方向是否确认。",
            confirmationPrice = detail.high,
            referenceTarget = null,
            riskBoundary = "若价格跌破当日低点 ${price(detail.low)}，则震荡观察计划失效。",
        )
    }
    AiTrendType.WEAK -> {
        if (abs(detail.quote.changePercent) < 0.5) {
            null
        } else {
            val lower = round2(detail.low)
            val upper = round2(max(lower, detail.previousClose))
            AiObservationPlan(
                focusRangeLow = lower,
                focusRangeHigh = upper,
                confirmationCondition = "若价格不再创当日新低，并重新站上昨收，则关注是否出现企稳。",
                confirmationPrice = detail.previousClose,
                referenceTarget = null,
                riskBoundary = "若继续跌破当日低点 ${price(detail.low)}，则不再沿用当前企稳观察计划。",
            )
        }
    }
}

private fun createSignals(detail: StockDetail, trendType: AiTrendType): List<AiSignal> {
    val intradayFirst = detail.intradayTrend.firstOrNull()?.price ?: detail.previousClose
    val intradayLast = detail.intradayTrend.lastOrNull()?.price ?: detail.quote.price
    val dailyFirst = detail.dailyTrend.firstOrNull()?.price ?: detail.previousClose
    val dailyLast = detail.dailyTrend.lastOrNull()?.price ?: detail.quote.price
    val priceStatus = when (trendType) {
        AiTrendType.STRONG -> "积极"
        AiTrendType.SIDEWAYS -> "中性"
        AiTrendType.WEAK -> "谨慎"
    }
    val priceExplanation = when (trendType) {
        AiTrendType.STRONG -> "当前价高于昨收，价格位置对短线判断偏积极。"
        AiTrendType.SIDEWAYS -> "当前价与昨收距离有限，暂不足以确认单边方向。"
        AiTrendType.WEAK -> "当前价低于昨收，短线价格位置偏弱。"
    }
    val intradayStatus = when {
        intradayLast > intradayFirst -> "回升"
        intradayLast < intradayFirst -> "回落"
        else -> "持平"
    }
    val dailyStatus = when {
        dailyLast > dailyFirst -> "向上"
        dailyLast < dailyFirst -> "向下"
        else -> "横向"
    }
    return listOf(
        AiSignal(
            title = "价格位置",
            status = priceStatus,
            explanation = priceExplanation,
            evidence = "当前价 ${price(detail.quote.price)}，昨收 ${price(detail.previousClose)}，差额 ${signed(detail.quote.change)}。",
        ),
        AiSignal(
            title = "分时收尾",
            status = intradayStatus,
            explanation = "分时样本从 ${price(intradayFirst)} 运行至 ${price(intradayLast)}，用于观察当日收尾方向。",
            evidence = "分时样本共 ${detail.intradayTrend.size} 个点，最后一个点与详情当前价使用同一份 Mock 数据。",
        ),
        AiSignal(
            title = "日 K 样本",
            status = dailyStatus,
            explanation = "日 K 样本首尾方向为${dailyStatus}，只作为当前演示周期的辅助依据。",
            evidence = "日 K 样本从 ${price(dailyFirst)} 变化至 ${price(dailyLast)}；未提供真实均线和历史量能对比。",
        ),
    )
}

private fun price(value: Double): String = fixed2(value)

private fun signed(value: Double): String = if (value >= 0.0) "+${fixed2(value)}" else fixed2(value)

private fun fixed2(value: Double): String {
    val rounded = round2(value)
    val text = rounded.toString()
    val decimalLength = text.substringAfter('.', "").length
    return when (decimalLength) {
        0 -> "$text.00"
        1 -> "${text}0"
        else -> text
    }
}

private fun round2(value: Double): Double = round(value * 100.0) / 100.0
