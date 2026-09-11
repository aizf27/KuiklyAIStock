package com.example.kuiklyaistock.repository

import com.tencent.kuikly.core.module.NetworkModule
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject
import com.tencent.kuikly.core.pager.Pager
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

internal sealed class QuoteTransportResult {
    data class Success(val body: ByteArray, val statusCode: Int?) : QuoteTransportResult()
    data class Failure(val category: QuoteTransportErrorCategory, val statusCode: Int? = null) : QuoteTransportResult()
}

internal enum class QuoteTransportErrorCategory {
    EMPTY,
    NETWORK,
    TIMEOUT,
    HTTP,
    UNSUPPORTED,
}

internal interface QuoteTransport {
    suspend fun fetch(symbols: List<String>): QuoteTransportResult
}

internal class TencentQuoteTransport(
    private val pager: Pager,
    private val nowMillis: () -> Long = { 0L },
    private val logger: (String) -> Unit = {},
) : QuoteTransport {
    private val networkModule: NetworkModule by lazy {
        pager.acquireModule<NetworkModule>(NetworkModule.MODULE_NAME)
    }

    override suspend fun fetch(symbols: List<String>): QuoteTransportResult {
        val normalized = symbols.map { it.trim().lowercase() }.distinct()
        if (normalized.isEmpty()) return QuoteTransportResult.Failure(QuoteTransportErrorCategory.EMPTY)
        val startedAt = nowMillis()
        logger("行情请求开始 type=腾讯快照 count=${normalized.size}")
        val result = suspendCoroutine<QuoteTransportResult> { continuation ->
            val url = "http://qt.gtimg.cn/q=${normalized.joinToString(",")}"
            networkModule.requestGetBinary(url, JSONObject()) { data, success, errorMessage, response ->
                val statusCode = response.statusCode
                val category = when {
                    !success && errorMessage.contains("timeout", ignoreCase = true) -> QuoteTransportErrorCategory.TIMEOUT
                    !success && statusCode != null && statusCode >= 400 -> QuoteTransportErrorCategory.HTTP
                    !success -> QuoteTransportErrorCategory.NETWORK
                    statusCode != null && statusCode >= 400 -> QuoteTransportErrorCategory.HTTP
                    data.isEmpty() -> QuoteTransportErrorCategory.EMPTY
                    else -> null
                }
                continuation.resume(
                    if (category == null) QuoteTransportResult.Success(data, statusCode)
                    else QuoteTransportResult.Failure(category, statusCode)
                )
            }
        }
        val elapsed = (nowMillis() - startedAt).coerceAtLeast(0L)
        when (result) {
            is QuoteTransportResult.Success -> logger(
                "行情请求完成 type=腾讯快照 count=${normalized.size} status=${result.statusCode ?: "unknown"} elapsedMs=$elapsed"
            )
            is QuoteTransportResult.Failure -> logger(
                "行情请求失败 type=腾讯快照 count=${normalized.size} status=${result.statusCode ?: "unknown"} elapsedMs=$elapsed error=${result.category}"
            )
        }
        return result
    }
}
