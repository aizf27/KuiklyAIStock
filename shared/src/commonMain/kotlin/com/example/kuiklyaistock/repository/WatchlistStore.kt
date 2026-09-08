package com.example.kuiklyaistock.repository

// 跨页面共享的本地自选状态，后续可替换为持久化实现。
object WatchlistStore {
    private val favoriteCodes = linkedSetOf<String>()
    private val observers = linkedSetOf<(Set<String>) -> Unit>()

    fun snapshot(): Set<String> = favoriteCodes.toSet()

    fun subscribe(observer: (Set<String>) -> Unit): () -> Unit {
        observers.add(observer)
        observer(snapshot())
        return { observers.remove(observer) }
    }

    fun toggle(code: String): Boolean {
        if (favoriteCodes.contains(code)) {
            favoriteCodes.remove(code)
        } else {
            favoriteCodes.add(code)
        }
        val current = snapshot()
        observers.toList().forEach { it(current) }
        return code in current
    }

    fun filterFavorite(
        quotes: List<com.example.kuiklyaistock.model.StockQuote>,
        codes: Set<String> = favoriteCodes,
    ) = quotes.filter { it.code in codes }

    internal fun resetForTest() {
        favoriteCodes.clear()
        observers.clear()
    }
}
