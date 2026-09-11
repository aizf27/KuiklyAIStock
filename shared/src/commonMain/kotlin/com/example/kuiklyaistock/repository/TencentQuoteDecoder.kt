package com.example.kuiklyaistock.repository

internal expect fun decodeGbkPlatform(bytes: ByteArray): String

internal object TencentQuoteDecoder {
    fun decode(bytes: ByteArray): String {
        require(bytes.isNotEmpty()) { "行情响应为空" }
        val text = decodeGbkPlatform(bytes).trimStart('\uFEFF', '\u0000', ' ', '\t', '\r', '\n')
        require(text.isNotBlank()) { "行情正文为空" }
        require(!text.contains('\uFFFD')) { "行情响应乱码" }
        return text.trim()
    }
}
