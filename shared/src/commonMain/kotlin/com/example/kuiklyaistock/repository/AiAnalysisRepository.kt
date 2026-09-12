package com.example.kuiklyaistock.repository

import com.example.kuiklyaistock.base.BridgeModule
import com.example.kuiklyaistock.model.AiAnalysis
import com.example.kuiklyaistock.model.AiAnalysisSource
import com.example.kuiklyaistock.model.AiObservationPlan
import com.example.kuiklyaistock.model.AiRiskLevel
import com.example.kuiklyaistock.model.AiSignal
import com.example.kuiklyaistock.model.AiTrendType
import com.example.kuiklyaistock.model.StockDetail
import com.tencent.kuikly.core.nvi.serialization.json.JSONArray
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject

/**
 * AI分析错误类型
 */
internal enum class AiAnalysisErrorType {
    CONFIG,          // 未配置AI服务
    AUTH,            // 鉴权失败
    BALANCE,         // 额度不足
    RATE_LIMIT,      // 请求频率超限
    INVALID_REQUEST, // 请求参数无效
    SERVER,          // 服务端错误
    NETWORK,         // 网络连接失败
    FORMAT,          // 返回格式异常
}

/**
 * AI分析加载结果
 */
internal sealed class AiAnalysisLoadResult {
    data class Success(val analysis: AiAnalysis) : AiAnalysisLoadResult()
    data class Failure(val type: AiAnalysisErrorType, val message: String) : AiAnalysisLoadResult()
    data object Unsupported : AiAnalysisLoadResult() // 当前环境不支持AI服务
}

/**
 * AI分析请求参数
 * @param stockCode 股票代码
 * @param modelName AI模型名称，如"deepseek-flash"
 * @param systemPrompt 系统提示词，定义输出格式和约束
 * @param userPrompt 用户提示词，包含行情数据
 */
internal data class AiAnalysisRequest(
    val stockCode: String,
    val modelName: String,
    val systemPrompt: String,
    val userPrompt: String,
)

/**
 * AI服务传输层返回结果
 */
internal sealed class AiTransportResult {
    data class Success(
        val content: String,      // AI返回的JSON内容
        val modelName: String,    // 实际使用的模型名
        val requestId: String,    // 请求ID，用于追踪
        val generatedAt: String,  // 生成时间
    ) : AiTransportResult()

    data class Failure(
        val statusCode: Int,      // HTTP状态码
        val errorType: String,    // 错误类型标识
        val message: String,      // 错误描述
    ) : AiTransportResult()
}

/**
 * AI服务传输层接口，支持不同的传输实现
 */
internal interface AiAnalysisTransport {
    // 判断当前环境是否支持AI服务
    fun isSupported(): Boolean

    // 发起AI分析请求
    suspend fun request(request: AiAnalysisRequest): AiTransportResult
}

/**
 * AI分析仓储接口
 */
internal interface AiAnalysisRepository {
    // 加载指定股票的AI分析，优先使用缓存，缓存过期则请求远程
    suspend fun loadAnalysis(detail: StockDetail): AiAnalysisLoadResult
}

/**
 * Mock实现，返回本地模拟的AI分析数据
 */
internal class MockAiAnalysisRepository : AiAnalysisRepository {
    override suspend fun loadAnalysis(detail: StockDetail): AiAnalysisLoadResult =
        AiAnalysisLoadResult.Success(createMockAiAnalysis(detail))
}

/**
 * 通过Kuikly Bridge调用原生端AI服务的传输层实现
 */
internal class BridgeAiAnalysisTransport(
    private val bridgeModule: BridgeModule,
) : AiAnalysisTransport {
    override fun isSupported(): Boolean = bridgeModule.supportsAiAnalysis()

    override suspend fun request(request: AiAnalysisRequest): AiTransportResult {
        val response = bridgeModule.requestAiAnalysis(
            JSONObject()
                .put("stockCode", request.stockCode)
                .put("modelName", request.modelName)
                .put("systemPrompt", request.systemPrompt)
                .put("userPrompt", request.userPrompt)
        ) ?: return AiTransportResult.Failure(0, "NETWORK", "AI 服务未返回结果")
        return if (response.optBoolean("ok")) {
            AiTransportResult.Success(
                content = response.optString("content"),
                modelName = response.optString("modelName"),
                requestId = response.optString("requestId"),
                generatedAt = response.optString("generatedAt"),
            )
        } else {
            AiTransportResult.Failure(
                statusCode = response.optInt("statusCode"),
                errorType = response.optString("errorType"),
                message = response.optString("message"),
            )
        }
    }
}

/**
 * 远程AI分析仓储实现，支持缓存和网络请求
 */
internal class RemoteAiAnalysisRepository(
    private val transport: AiAnalysisTransport,
    private val databaseRepo: StockDatabaseRepository,
    private val modelName: String = DEFAULT_MODEL,
    private val schemaVersion: String = SCHEMA_VERSION,
    private val promptVersion: String = PROMPT_VERSION,
    private val getCurrentTime: () -> String = { "" }, // 获取当前时间的回调
) : AiAnalysisRepository {
    override suspend fun loadAnalysis(detail: StockDetail): AiAnalysisLoadResult {
        if (!transport.isSupported()) return AiAnalysisLoadResult.Unsupported

        // 1. 优先从数据库读取缓存
        val cached = databaseRepo.getAiAnalysis(detail.quote.code)
        if (cached != null && cached.quoteTime == detail.quote.updatedAt) {
            return AiAnalysisLoadResult.Success(cached.analysis.copy(source = AiAnalysisSource.CACHE))
        }

        // 2. 缓存失效，发起网络请求
        val request = buildRequest(detail)
        return when (val result = transport.request(request)) {
            is AiTransportResult.Success -> {
                val parsed = parseRemoteAnalysis(detail, result)
                if (parsed is AiAnalysisLoadResult.Success) {
                    // 写入数据库缓存
                    databaseRepo.insertAiAnalysis(
                        detail.quote.code,
                        parsed.analysis,
                        detail.quote.updatedAt
                    )
                }
                parsed
            }
            is AiTransportResult.Failure -> AiAnalysisLoadResult.Failure(
                type = mapFailure(result),
                message = result.message.ifBlank { defaultErrorMessage(mapFailure(result)) }
            )
        }
    }

    // 构建AI请求，包含系统提示词和用户提示词
    private fun buildRequest(detail: StockDetail): AiAnalysisRequest {
        val systemPrompt = """
            你是股票行情产品的结构化分析助手。必须只输出合法 JSON 对象，不要 Markdown、代码块或额外说明。
            只能依据输入的行情快照，不得补写不存在的时序走势，不得给出买卖指令、收益承诺或确定性预测。
            schema=$schemaVersion。枚举：trendType=STRONG|SIDEWAYS|WEAK，riskLevel=LOW|MEDIUM|HIGH。
            signals 最多 3 条，primaryRisks 最多 2 条。observationPlan 无合理计划时返回 null。
            JSON 结构必须为：
            {"trendType":"STRONG|SIDEWAYS|WEAK","trendJudgement":"文本","factSummary":"文本",
            "applicablePeriod":"文本","observationPlan":null或{"focusRangeLow":正数,"focusRangeHigh":正数,
            "confirmationCondition":"文本","confirmationPrice":正数或null,"referenceTarget":正数或null,"riskBoundary":"文本"},
            "signals":[{"title":"文本","status":"文本","explanation":"文本","evidence":"文本"}],
            "riskLevel":"LOW|MEDIUM|HIGH","primaryRisks":["文本"],"invalidationCondition":"文本"}。
        """.trimIndent()

        // 优化：只发送核心数据，减少 token 消耗
        val userPrompt = buildString {
            appendLine("请基于以下行情快照生成 JSON 分析：")
            appendLine("名称=${detail.quote.name}，代码=${detail.quote.code}")
            appendLine("当前价=${detail.quote.price}，涨跌=${detail.quote.change}，涨跌幅=${detail.quote.changePercent}%")
            appendLine("今开=${detail.open}，昨收=${detail.previousClose}，最高=${detail.high}，最低=${detail.low}")
            append("成交量=${detail.volume}，成交额=${detail.turnover}")

            // 只在有真实走势数据时才发送（避免发送大量 Mock 数据）
            if (detail.intradayTrend.isNotEmpty() && detail.intradayTrend.size < 100) {
                appendLine()
                // 采样：只取首、中、尾 10 个点，减少 token
                val samples = if (detail.intradayTrend.size > 30) {
                    detail.intradayTrend.take(10) +
                    detail.intradayTrend.drop(detail.intradayTrend.size / 2 - 5).take(10) +
                    detail.intradayTrend.takeLast(10)
                } else {
                    detail.intradayTrend
                }
                append("分时样本=${samples.joinToString("；") { "${it.label}:${it.price}" }}")
            }

            if (detail.dailyTrend.isNotEmpty() && detail.dailyTrend.size < 50) {
                appendLine()
                // 日 K 只取最近 10 条
                val samples = detail.dailyTrend.takeLast(10)
                append("日K样本=${samples.joinToString("；") { "${it.label}:${it.price}" }}")
            }
        }
        return AiAnalysisRequest(detail.quote.code, modelName, systemPrompt, userPrompt)
    }

    private fun parseRemoteAnalysis(detail: StockDetail, result: AiTransportResult.Success): AiAnalysisLoadResult {
        if (result.content.isBlank()) return formatFailure("AI 返回内容为空")
        return try {
            val root = JSONObject(result.content)
            val trendType = enumValue<AiTrendType>(root.optString("trendType"))
                ?: return formatFailure("趋势枚举无效")
            val riskLevel = enumValue<AiRiskLevel>(root.optString("riskLevel"))
                ?: return formatFailure("风险枚举无效")
            val trendJudgement = requiredText(root, "trendJudgement", 160) ?: return formatFailure("综合判断无效")
            val factSummary = requiredText(root, "factSummary", 240) ?: return formatFailure("事实摘要无效")
            val applicablePeriod = requiredText(root, "applicablePeriod", 24) ?: return formatFailure("适用周期无效")
            val invalidationCondition = requiredText(root, "invalidationCondition", 180)
                ?: return formatFailure("失效条件无效")
            val observationPlan = parseObservationPlan(root.optJSONObject("observationPlan"))
            if (observationPlan is Parsed.Invalid) return formatFailure(observationPlan.message)
            val signals = parseSignals(root.optJSONArray("signals")) ?: return formatFailure("信号字段无效")
            val risks = parseRisks(root.optJSONArray("primaryRisks")) ?: return formatFailure("风险字段无效")
            val plan = (observationPlan as Parsed.Valid).value
            val focusPoint = plan?.confirmationCondition ?: "暂无明确观察计划"
            val riskReminder = risks.firstOrNull() ?: invalidationCondition
            val signalInterpretation = signals.joinToString("；") { it.explanation }.ifBlank { "暂无结构化信号" }
            val evidenceSummary = signals.joinToString("；") { it.evidence }.ifBlank { factSummary }
            AiAnalysisLoadResult.Success(
                AiAnalysis(
                    trendJudgement = trendJudgement,
                    focusPoint = focusPoint,
                    riskReminder = riskReminder,
                    signalInterpretation = signalInterpretation,
                    factSummary = factSummary,
                    applicablePeriod = applicablePeriod,
                    evidenceSummary = evidenceSummary,
                    updatedAt = getCurrentTime().ifBlank { detail.quote.updatedAt }, // 使用实时时间
                    isDemo = false,
                    trendType = trendType,
                    riskLevel = riskLevel,
                    primaryRisks = risks,
                    invalidationCondition = invalidationCondition,
                    observationPlan = plan,
                    signals = signals,
                    source = AiAnalysisSource.REMOTE,
                    modelName = result.modelName.ifBlank { modelName },
                    generatedAt = result.generatedAt,
                    requestId = result.requestId.ifBlank { null },
                )
            )
        } catch (_: Throwable) {
            formatFailure("AI 返回内容不是合法 JSON")
        }
    }

    private fun parseObservationPlan(value: JSONObject?): Parsed<AiObservationPlan?> {
        if (value == null) return Parsed.Valid(null)
        val low = value.optDouble("focusRangeLow", Double.NaN)
        val high = value.optDouble("focusRangeHigh", Double.NaN)
        val condition = requiredText(value, "confirmationCondition", 180)
            ?: return Parsed.Invalid("观察条件无效")
        val boundary = requiredText(value, "riskBoundary", 180)
            ?: return Parsed.Invalid("风险边界无效")
        val confirmationPrice = optionalPositive(value, "confirmationPrice")
            ?: if (hasNonNullValue(value, "confirmationPrice")) return Parsed.Invalid("确认价格无效") else null
        val referenceTarget = optionalPositive(value, "referenceTarget")
            ?: if (hasNonNullValue(value, "referenceTarget")) return Parsed.Invalid("参考目标无效") else null
        if (!low.isFinite() || !high.isFinite() || low <= 0.0 || high <= 0.0 || low > high) {
            return Parsed.Invalid("观察价格区间无效")
        }
        return Parsed.Valid(AiObservationPlan(low, high, condition, confirmationPrice, referenceTarget, boundary))
    }

    private fun parseSignals(array: JSONArray?): List<AiSignal>? {
        if (array == null || array.length() > 3) return null
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: return null
                add(
                    AiSignal(
                        title = requiredText(item, "title", 24) ?: return null,
                        status = requiredText(item, "status", 24) ?: return null,
                        explanation = requiredText(item, "explanation", 180) ?: return null,
                        evidence = requiredText(item, "evidence", 240) ?: return null,
                    )
                )
            }
        }
    }

    private fun parseRisks(array: JSONArray?): List<String>? {
        if (array == null || array.length() > 2) return null
        return buildList {
            for (index in 0 until array.length()) {
                val value = array.optString(index)?.trim().orEmpty()
                if (value.isEmpty() || value.length > 180) return null
                add(value)
            }
        }
    }

    private fun requiredText(json: JSONObject, key: String, maxLength: Int): String? =
        json.optString(key).trim().takeIf { it.isNotEmpty() && it.length <= maxLength }

    private fun optionalPositive(json: JSONObject, key: String): Double? {
        if (!hasNonNullValue(json, key)) return null
        val value = json.optDouble(key, Double.NaN)
        return value.takeIf { it.isFinite() && it > 0.0 }
    }

    private fun hasNonNullValue(json: JSONObject, key: String): Boolean {
        val value = json.opt(key) ?: return false
        return value.toString() != "null"
    }

    private inline fun <reified T : Enum<T>> enumValue(value: String): T? =
        enumValues<T>().firstOrNull { it.name == value.trim() }

    private fun mapFailure(result: AiTransportResult.Failure): AiAnalysisErrorType = when {
        result.errorType.equals("CONFIG", true) -> AiAnalysisErrorType.CONFIG
        result.statusCode == 401 || result.errorType.equals("AUTH", true) -> AiAnalysisErrorType.AUTH
        result.statusCode == 402 || result.errorType.equals("BALANCE", true) -> AiAnalysisErrorType.BALANCE
        result.statusCode == 429 || result.errorType.equals("RATE_LIMIT", true) -> AiAnalysisErrorType.RATE_LIMIT
        result.statusCode in listOf(400, 422) || result.errorType.equals("INVALID_REQUEST", true) -> AiAnalysisErrorType.INVALID_REQUEST
        result.statusCode in listOf(500, 503) || result.errorType.equals("SERVER", true) -> AiAnalysisErrorType.SERVER
        result.errorType.equals("FORMAT", true) -> AiAnalysisErrorType.FORMAT
        else -> AiAnalysisErrorType.NETWORK
    }

    private fun formatFailure(message: String) = AiAnalysisLoadResult.Failure(AiAnalysisErrorType.FORMAT, message)

    private fun defaultErrorMessage(type: AiAnalysisErrorType): String = when (type) {
        AiAnalysisErrorType.CONFIG -> "未配置 AI 服务，已展示演示分析"
        AiAnalysisErrorType.AUTH -> "AI 服务鉴权失败，已展示演示分析"
        AiAnalysisErrorType.BALANCE -> "AI 服务额度不足，已展示演示分析"
        AiAnalysisErrorType.RATE_LIMIT -> "AI 请求过于频繁，请稍后重试"
        AiAnalysisErrorType.INVALID_REQUEST -> "AI 请求参数无效，已展示演示分析"
        AiAnalysisErrorType.SERVER -> "AI 服务暂时不可用，请稍后重试"
        AiAnalysisErrorType.NETWORK -> "网络连接失败，请检查网络后重试"
        AiAnalysisErrorType.FORMAT -> "AI 返回格式异常，已展示演示分析"
    }

    private sealed class Parsed<out T> {
        data class Valid<T>(val value: T) : Parsed<T>()
        data class Invalid(val message: String) : Parsed<Nothing>()
    }

    companion object {
        const val DEFAULT_MODEL = "deepseek-flash"
        const val SCHEMA_VERSION = "ai-analysis-v1"
        const val PROMPT_VERSION = "stock-detail-v1"
    }
}

internal object AiAnalysisMemoryCache {
    private val values = mutableMapOf<String, AiAnalysis>()

    fun get(key: String): AiAnalysis? = values[key]

    fun put(key: String, analysis: AiAnalysis) {
        values[key] = analysis
    }

    fun clearForTest() {
        values.clear()
    }
}
