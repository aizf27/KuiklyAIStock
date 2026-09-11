package com.example.kuiklyaistock.repository

internal object TencentSymbolMapper {
    fun stockSymbol(code: String): String? {
        val normalized = normalizeCode(code) ?: return null
        val prefix = if (normalized.startsWith("6")) "sh" else if (normalized.startsWith("0") || normalized.startsWith("3")) "sz" else return null
        return prefix + normalized
    }

    fun indexSymbol(code: String): String? = when (normalizeCode(code)) {
        "000001" -> "sh000001"
        "399001" -> "sz399001"
        "399006" -> "sz399006"
        else -> null
    }

    fun standardCode(symbol: String): String? {
        val normalized = symbol.trim().lowercase()
        if (!normalized.matches(Regex("(?:sh|sz)\\d{6}"))) return null
        return normalized.substring(2)
    }

    private fun normalizeCode(code: String): String? = code.trim().takeIf { it.matches(Regex("\\d{6}")) }
}
