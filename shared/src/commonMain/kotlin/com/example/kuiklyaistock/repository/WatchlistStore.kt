package com.example.kuiklyaistock.repository

// 跨页面共享的本地自选状态，后续可替换为持久化实现。
object WatchlistStore {
    private val favoriteCodes = linkedSetOf<String>()

    fun isFavorite(code: String): Boolean = code in favoriteCodes

    fun toggle(code: String): Boolean {
        if (favoriteCodes.contains(code)) {
            favoriteCodes.remove(code)
        } else {
            favoriteCodes.add(code)
        }
        return isFavorite(code)
    }

    fun filterFavorite(quotes: List<com.example.kuiklyaistock.model.StockQuote>) =
        quotes.filter { isFavorite(it.code) }
}
