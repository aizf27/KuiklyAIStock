package com.example.kuiklyaistock.repository

import com.example.kuiklyaistock.model.StockQuote

// 兼容旧调用，新业务统一使用 PortfolioStore。
object WatchlistStore {
    fun snapshot(): Set<String> = PortfolioStore.snapshot().favoriteCodes.toSet()

    fun subscribe(observer: (Set<String>) -> Unit): () -> Unit =
        PortfolioStore.subscribe { observer(it.favoriteCodes.toSet()) }

    fun toggle(code: String): Boolean = PortfolioStore.toggleFavorite(code)

    fun filterFavorite(
        quotes: List<StockQuote>,
        codes: Set<String> = snapshot(),
    ): List<StockQuote> = quotes.filter { it.code in codes }

    internal fun resetForTest() {
        PortfolioStore.resetForTest()
    }
}
