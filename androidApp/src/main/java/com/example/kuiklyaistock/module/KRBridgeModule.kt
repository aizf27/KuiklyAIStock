package com.example.kuiklyaistock.module

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import com.tencent.kuikly.core.render.android.export.KuiklyRenderBaseModule
import com.tencent.kuikly.core.render.android.export.KuiklyRenderCallback
import com.example.kuiklyaistock.BuildConfig
import com.example.kuiklyaistock.KRApplication
import com.example.kuiklyaistock.KuiklyRenderActivity
import com.example.kuiklyaistock.adapter.execOnSubThread
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class KRBridgeModule : KuiklyRenderBaseModule() {

    override fun call(method: String, params: String?, callback: KuiklyRenderCallback?): Any? {
        return when (method) {
            "ssoRequest" -> {
                ssoRequest(params, callback)
            }

            "showAlert" -> {
                showAlert(params, callback)
            }

            "closePage" -> {
                closePage(params)
            }

            "openPage" -> {
                openPage(params)
            }

            "copyToPasteboard" -> {
                copyToPasteboard(params)
            }

            "toast" -> {
                toast(params)
            }

            "log" -> {
                log(params)
            }

            "reportDT" -> {
                reportDT(params)
            }

            "reportRealtime" -> {
                reportRealtime(params)
            }

            "qqLiveSSORequest" -> {
                qqLiveSSORequest(params, callback)
            }

            "localServeTime" -> {
                localServeTime(params, callback)
            }

            "currentTimestamp" -> {
                currentTimestamp(params)
            }

            "dateFormatter" -> {
                dateFormatter(params)
            }

            "getCachedFromNative" -> {
                getCachedFromNative(params)
            }

            "setCachedToNative" -> {
                setCachedToNative(params, callback)
            }

            "supportsAiAnalysis" -> {
                if (BuildConfig.DEBUG && BuildConfig.DEEPSEEK_API_KEY.isNotBlank()) "1" else "0"
            }

            "requestAiAnalysis" -> {
                requestAiAnalysis(params, callback)
                null
            }

            else -> callback?.invoke(
                mapOf(
                    "code" to -1,
                    "message" to "方法不存在"
                )
            )
        }
    }


    private fun getCachedFromNative(params: String?): String {
        return try {
            val key = JSONObject(params ?: "{}").optString("key")
            val value = KRApplication.application
                .getSharedPreferences(PORTFOLIO_PREFERENCES, Context.MODE_PRIVATE)
                .getString(key, "")
                .orEmpty()
            Log.i(TAG, "读取本地资产状态: key=$key, hasValue=${value.isNotEmpty()}")
            value
        } catch (error: Throwable) {
            Log.e(TAG, "读取本地资产状态失败", error)
            ""
        }
    }

    private fun setCachedToNative(params: String?, callback: KuiklyRenderCallback?) {
        try {
            val json = JSONObject(params ?: "{}")
            val key = json.optString("key")
            val value = json.optString("value")
            val success = KRApplication.application
                .getSharedPreferences(PORTFOLIO_PREFERENCES, Context.MODE_PRIVATE)
                .edit()
                .putString(key, value)
                .commit()
            if (success) {
                Log.i(TAG, "保存本地资产状态成功: key=$key")
            } else {
                Log.e(TAG, "保存本地资产状态失败: key=$key")
            }
            callback?.invoke(mapOf("success" to success))
        } catch (error: Throwable) {
            Log.e(TAG, "保存本地资产状态异常", error)
            callback?.invoke(mapOf("success" to false))
        }
    }

    private fun requestAiAnalysis(params: String?, callback: KuiklyRenderCallback?) {
        val request = try {
            JSONObject(params ?: "{}")
        } catch (_: Throwable) {
            postAiResult(callback, aiFailure(0, "INVALID_REQUEST", "AI 请求参数无效"))
            return
        }
        execOnSubThread {
            postAiResult(callback, performAiRequest(request))
        }
    }

    private fun performAiRequest(request: JSONObject): Map<String, Any> {
        val stockCode = request.optString("stockCode")
        val modelName = request.optString("modelName").ifBlank { DEFAULT_AI_MODEL }
        if (!BuildConfig.DEBUG || BuildConfig.DEEPSEEK_API_KEY.isBlank()) {
            Log.i(AI_TAG, "AI请求跳过: code=$stockCode, type=CONFIG")
            return aiFailure(0, "CONFIG", "未配置 AI 服务，已展示演示分析", modelName)
        }
        val startedAt = System.currentTimeMillis()
        var attempt = 0
        while (attempt < 2) {
            try {
                val result = executeAiRequest(request, modelName)
                val statusCode = (result["statusCode"] as? Int) ?: 0
                val retryable = statusCode == 500 || statusCode == 503
                if (retryable && attempt == 0) {
                    Log.i(AI_TAG, "AI请求重试: code=$stockCode, status=$statusCode, type=SERVER")
                    attempt++
                    continue
                }
                val elapsed = System.currentTimeMillis() - startedAt
                val type = result["errorType"].toString().ifBlank { "SUCCESS" }
                if (result["ok"] == true) {
                    Log.i(AI_TAG, "AI请求成功: code=$stockCode, status=$statusCode, cost=${elapsed}ms")
                } else {
                    Log.e(AI_TAG, "AI请求失败: code=$stockCode, status=$statusCode, cost=${elapsed}ms, type=$type")
                }
                return result
            } catch (error: IOException) {
                if (attempt == 0) {
                    Log.i(AI_TAG, "AI请求重试: code=$stockCode, status=0, type=NETWORK")
                    attempt++
                    continue
                }
                val elapsed = System.currentTimeMillis() - startedAt
                Log.e(AI_TAG, "AI请求失败: code=$stockCode, status=0, cost=${elapsed}ms, type=NETWORK")
                return aiFailure(0, "NETWORK", "网络连接失败，请检查网络后重试", modelName)
            } catch (_: Throwable) {
                val elapsed = System.currentTimeMillis() - startedAt
                Log.e(AI_TAG, "AI请求失败: code=$stockCode, status=0, cost=${elapsed}ms, type=FORMAT")
                return aiFailure(0, "FORMAT", "AI 返回格式异常，已展示演示分析", modelName)
            }
        }
        return aiFailure(0, "NETWORK", "网络连接失败，请检查网络后重试", modelName)
    }

    private fun executeAiRequest(request: JSONObject, modelName: String): Map<String, Any> {
        val body = JSONObject()
            .put("model", modelName)
            .put(
                "messages",
                JSONArray()
                    .put(JSONObject().put("role", "system").put("content", request.optString("systemPrompt")))
                    .put(JSONObject().put("role", "user").put("content", request.optString("userPrompt")))
            )
            .put("response_format", JSONObject().put("type", "json_object"))
            .put("thinking", JSONObject().put("type", "disabled"))
            .put("stream", false)
            .put("max_tokens", 1200)
            .toString()
        val connection = (URL(DEEPSEEK_CHAT_URL).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 10_000
            readTimeout = 20_000
            doOutput = true
            setRequestProperty("Authorization", "Bearer ${BuildConfig.DEEPSEEK_API_KEY}")
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "application/json")
        }
        return try {
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val statusCode = connection.responseCode
            val responseBody = (if (statusCode in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader(Charsets.UTF_8)
                ?.use { it.readText() }
                .orEmpty()
            if (statusCode !in 200..299) {
                aiFailure(statusCode, httpErrorType(statusCode), httpErrorMessage(statusCode), modelName)
            } else {
                parseAiSuccess(responseBody, modelName, statusCode)
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun parseAiSuccess(responseBody: String, fallbackModel: String, statusCode: Int): Map<String, Any> {
        return try {
            val response = JSONObject(responseBody)
            val content = response.optJSONArray("choices")
                ?.optJSONObject(0)
                ?.optJSONObject("message")
                ?.optString("content")
                .orEmpty()
            if (content.isBlank()) {
                aiFailure(200, "FORMAT", "AI 返回格式异常，已展示演示分析", fallbackModel)
            } else {
                mapOf(
                    "ok" to true,
                    "statusCode" to statusCode,
                    "content" to content,
                    "modelName" to response.optString("model").ifBlank { fallbackModel },
                    "requestId" to response.optString("id"),
                    "generatedAt" to SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()),
                    "errorType" to "",
                    "message" to "",
                )
            }
        } catch (_: Throwable) {
            aiFailure(200, "FORMAT", "AI 返回格式异常，已展示演示分析", fallbackModel)
        }
    }

    private fun aiFailure(
        statusCode: Int,
        errorType: String,
        message: String,
        modelName: String = DEFAULT_AI_MODEL,
    ): Map<String, Any> = mapOf(
        "ok" to false,
        "statusCode" to statusCode,
        "content" to "",
        "modelName" to modelName,
        "requestId" to "",
        "generatedAt" to "",
        "errorType" to errorType,
        "message" to message,
    )

    private fun postAiResult(callback: KuiklyRenderCallback?, result: Map<String, Any>) {
        Handler(Looper.getMainLooper()).post { callback?.invoke(result) }
    }

    private fun httpErrorType(statusCode: Int): String = when (statusCode) {
        400, 422 -> "INVALID_REQUEST"
        401 -> "AUTH"
        402 -> "BALANCE"
        429 -> "RATE_LIMIT"
        500, 503 -> "SERVER"
        else -> if (statusCode >= 500) "SERVER" else "INVALID_REQUEST"
    }

    private fun httpErrorMessage(statusCode: Int): String = when (statusCode) {
        401 -> "AI 服务鉴权失败，已展示演示分析"
        402 -> "AI 服务额度不足，已展示演示分析"
        429 -> "AI 请求过于频繁，请稍后重试"
        500, 503 -> "AI 服务暂时不可用，请稍后重试"
        else -> "AI 请求失败，已展示演示分析"
    }

    private fun reportRealtime(params: String?) {
    }

    private fun reportDT(params: String?) {
    }

    private fun log(params: String?) {
        if (params == null) {
            return
        }

        val paramJSON = JSONObject(params)
        Log.i("KuiklyRender", paramJSON.optString("content"))
    }

    private fun toast(params: String?) {
        if (params == null) {
            return
        }
        val paramJSON = JSONObject(params)
        Toast.makeText(
            KRApplication.application,
            paramJSON.optString("content"),
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun copyToPasteboard(params: String?) {
        if (params == null) {
            return
        }

        val paramJSON = JSONObject(params)
        (context?.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager)?.also {
            it.setPrimaryClip(ClipData.newPlainText(MODULE_NAME, paramJSON.optString("content")))
        }
    }

    private fun openPage(params: String?) {
        if (params == null) {
            return
        }
        val ctx = context ?: return
        val paramJSON = JSONObject(params)
        val url = paramJSON.optString("url")
    }

    private fun closePage(params: String?) {
        activity?.finish()
    }

    private fun showAlert(params: String?, callback: KuiklyRenderCallback?) {
        if (params == null) {
            return
        }
        val paramJSON = JSONObject(params)
        val titleText = paramJSON.optString("title")
        val message = paramJSON.optString("message")
        val buttons = paramJSON.optJSONArray("buttons") ?: JSONArray()
    }

    private fun ssoRequest(params: String?, callback: KuiklyRenderCallback?) {}

    private fun qqLiveSSORequest(params: String?, callback: KuiklyRenderCallback?) {
    }

    private fun localServeTime(params: String?, callback: KuiklyRenderCallback?) {
        val time = (System.currentTimeMillis() / 1000.0)
        callback?.invoke(
            mapOf(
                "time" to time
            )
        )
    }

    private fun currentTimestamp(params: String?): String {
        return (System.currentTimeMillis()).toString()
    }

    private fun dateFormatter(params: String?): String {
        val paramJSONObject = JSONObject(params ?: "{}")
        val data = Date(paramJSONObject.optLong("timeStamp"))
        val format = SimpleDateFormat(paramJSONObject.optString("format"))
        return format.format(data)
    }

    companion object {
        const val MODULE_NAME = "HRBridgeModule"
        private const val TAG = "StockPortfolio"
        private const val PORTFOLIO_PREFERENCES = "stock_portfolio"
        private const val AI_TAG = "StockAiAnalysis"
        private const val DEEPSEEK_CHAT_URL = "https://api.deepseek.com/chat/completions"
        private const val DEFAULT_AI_MODEL = "deepseek-flash"
    }
}

private fun JSONObject.toMap(): Map<Any, Any> {
    val map = mutableMapOf<Any, Any>()
    val keys = keys()
    while (keys.hasNext()) {
        val key = keys.next()
        when (val v = opt(key)) {
            is JSONObject -> {
                map[key] = v.toMap()
            }

            else -> {
                v?.also {
                    map[key] = it
                }
            }
        }
    }
    return map
}
