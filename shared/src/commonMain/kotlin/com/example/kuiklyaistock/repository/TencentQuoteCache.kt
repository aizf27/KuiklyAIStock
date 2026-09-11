package com.example.kuiklyaistock.repository

internal data class CachedTencentQuote(
    val quote: TencentParsedQuote,
    val cachedAtMillis: Long,
)

// 进程内共享真实行情缓存，首页和详情共用。
internal object TencentQuoteCache {
    private val values = mutableMapOf<String, CachedTencentQuote>()

    fun get(symbol: String): CachedTencentQuote? = values[normalize(symbol)]

    fun putIfNewer(quote: TencentParsedQuote, cachedAtMillis: Long): Boolean {
        val key = normalize(quote.symbol)
        val old = values[key]
        if (old != null && isOlder(quote.updatedAt, old.quote.updatedAt)) return false
        values[key] = CachedTencentQuote(quote.copy(symbol = key), cachedAtMillis)
        return true
    }

    fun isExpired(cached: CachedTencentQuote, nowMillis: Long, ttlMillis: Long): Boolean {
        if (nowMillis <= 0L || cached.cachedAtMillis <= 0L) return false
        return nowMillis - cached.cachedAtMillis > ttlMillis
    }

    fun clear() {
        values.clear()
    }

    private fun normalize(symbol: String): String = symbol.trim().lowercase()

    private fun isOlder(incoming: String, cached: String): Boolean =
        incoming.isNotBlank() && cached.isNotBlank() && incoming < cached
}
