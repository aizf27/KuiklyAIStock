package com.example.kuiklyaistock.repository

import com.example.kuiklyaistock.model.PortfolioLoadResult
import com.example.kuiklyaistock.model.PortfolioState
import com.example.kuiklyaistock.model.StockPosition
import com.example.kuiklyaistock.model.TradeResult
import com.tencent.kuikly.core.nvi.serialization.json.JSONArray
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject

interface PortfolioStateStorage {
    fun read(key: String): String
    fun write(key: String, value: String)
}

object PortfolioStore {
    const val STORAGE_KEY = "stock_portfolio_state_v1"
    const val STATE_VERSION = 1

    private var state = PortfolioState()
    private var loaded = false
    private val observers = linkedSetOf<(PortfolioState) -> Unit>()

    fun snapshot(): PortfolioState = state.copy(
        favoriteCodes = state.favoriteCodes.toList(),
        positions = state.positions.toList(),
    )

    fun isLoaded(): Boolean = loaded

    fun load(serialized: String?): PortfolioLoadResult {
        if (serialized.isNullOrBlank()) {
            state = PortfolioState()
            loaded = true
            notifyObservers()
            return PortfolioLoadResult.Success(snapshot())
        }
        return try {
            val root = JSONObject(serialized)
            val version = root.optInt("version", -1)
            if (version != STATE_VERSION) {
                resetAfterLoadFailure("不支持的资产数据版本: $version")
            } else {
                val favorites = root.optJSONArray("favoriteCodes").toStringList().distinct()
                val positions = root.optJSONArray("positions").toPositions()
                state = PortfolioState(
                    version = version,
                    favoriteCodes = favorites,
                    positions = positions,
                )
                loaded = true
                notifyObservers()
                PortfolioLoadResult.Success(snapshot())
            }
        } catch (error: Throwable) {
            resetAfterLoadFailure("资产数据解析失败: ${error.message ?: "未知错误"}")
        }
    }

    fun serialize(): String {
        val favorites = JSONArray()
        state.favoriteCodes.forEach { favorites.put(it) }
        val positions = JSONArray()
        state.positions.forEach { position ->
            positions.put(
                JSONObject()
                    .put("code", position.code)
                    .put("quantity", position.quantity)
                    .put("averageCost", position.averageCost)
            )
        }
        return JSONObject()
            .put("version", STATE_VERSION)
            .put("favoriteCodes", favorites)
            .put("positions", positions)
            .toString()
    }

    fun subscribe(observer: (PortfolioState) -> Unit): () -> Unit {
        observers.add(observer)
        observer(snapshot())
        return { observers.remove(observer) }
    }

    fun toggleFavorite(code: String): Boolean {
        val normalizedCode = code.trim()
        if (normalizedCode.isEmpty()) {
            return false
        }
        val favorites = state.favoriteCodes.toMutableList()
        val existingIndex = favorites.indexOf(normalizedCode)
        val isFavorite = if (existingIndex >= 0) {
            favorites.removeAt(existingIndex)
            false
        } else {
            favorites.add(normalizedCode)
            true
        }
        updateState(state.copy(favoriteCodes = favorites))
        return isFavorite
    }

    fun removeFavorite(code: String): Boolean {
        val favorites = state.favoriteCodes.toMutableList()
        if (!favorites.remove(code.trim())) {
            return false
        }
        updateState(state.copy(favoriteCodes = favorites))
        return true
    }

    fun moveFavorite(fromIndex: Int, toIndex: Int): Boolean {
        val favorites = state.favoriteCodes.toMutableList()
        if (fromIndex !in favorites.indices || toIndex !in favorites.indices || fromIndex == toIndex) {
            return false
        }
        val code = favorites.removeAt(fromIndex)
        favorites.add(toIndex, code)
        updateState(state.copy(favoriteCodes = favorites))
        return true
    }

    fun buy(code: String, quantity: Int, price: Double): TradeResult {
        validateTrade(quantity, price)?.let { return TradeResult.Failure(it) }
        val normalizedCode = code.trim()
        if (normalizedCode.isEmpty()) {
            return TradeResult.Failure("股票代码不能为空")
        }
        val positions = state.positions.toMutableList()
        val index = positions.indexOfFirst { it.code == normalizedCode }
        val updatedPosition = if (index >= 0) {
            val current = positions[index]
            val totalQuantity = current.quantity + quantity
            val averageCost = (current.quantity * current.averageCost + quantity * price) / totalQuantity
            current.copy(quantity = totalQuantity, averageCost = averageCost)
        } else {
            StockPosition(normalizedCode, quantity, price)
        }
        if (index >= 0) {
            positions[index] = updatedPosition
        } else {
            positions.add(updatedPosition)
        }
        updateState(state.copy(positions = positions))
        return TradeResult.Success(updatedPosition)
    }

    fun sell(code: String, quantity: Int): TradeResult {
        validateQuantity(quantity)?.let { return TradeResult.Failure(it) }
        val normalizedCode = code.trim()
        if (normalizedCode.isEmpty()) {
            return TradeResult.Failure("股票代码不能为空")
        }
        val positions = state.positions.toMutableList()
        val index = positions.indexOfFirst { it.code == normalizedCode }
        if (index < 0) {
            return TradeResult.Failure("当前没有该股票持仓")
        }
        val current = positions[index]
        if (quantity > current.quantity) {
            return TradeResult.Failure("卖出数量不能超过可卖持仓 ${current.quantity} 股")
        }
        val updatedPosition = if (quantity == current.quantity) {
            positions.removeAt(index)
            null
        } else {
            current.copy(quantity = current.quantity - quantity).also { positions[index] = it }
        }
        updateState(state.copy(positions = positions))
        return TradeResult.Success(updatedPosition)
    }

    internal fun resetForTest() {
        state = PortfolioState()
        loaded = false
        observers.clear()
    }

    private fun validateTrade(quantity: Int, price: Double): String? {
        validateQuantity(quantity)?.let { return it }
        return if (price <= 0.0) "成交价格必须大于 0" else null
    }

    private fun validateQuantity(quantity: Int): String? = when {
        quantity <= 0 -> "股数必须是正整数"
        quantity % 100 != 0 -> "股数必须是 100 的整数倍"
        else -> null
    }

    private fun updateState(newState: PortfolioState) {
        state = newState.copy(version = STATE_VERSION)
        loaded = true
        notifyObservers()
    }

    private fun notifyObservers() {
        val current = snapshot()
        observers.toList().forEach { it(current) }
    }

    private fun resetAfterLoadFailure(message: String): PortfolioLoadResult.Failure {
        state = PortfolioState()
        loaded = true
        notifyObservers()
        return PortfolioLoadResult.Failure(message)
    }

    private fun JSONArray?.toStringList(): List<String> {
        if (this == null) return emptyList()
        return buildList {
            for (index in 0 until length()) {
                optString(index)?.trim()?.takeIf { it.isNotEmpty() }?.let(::add)
            }
        }
    }

    private fun JSONArray?.toPositions(): List<StockPosition> {
        if (this == null) return emptyList()
        val positions = mutableListOf<StockPosition>()
        val seenCodes = mutableSetOf<String>()
        for (index in 0 until length()) {
            val item = optJSONObject(index) ?: continue
            val code = item.optString("code").trim()
            val quantity = item.optInt("quantity")
            val averageCost = item.optDouble("averageCost")
            if (code.isNotEmpty() && quantity > 0 && quantity % 100 == 0 && averageCost > 0.0 && seenCodes.add(code)) {
                positions.add(StockPosition(code, quantity, averageCost))
            }
        }
        return positions
    }
}
