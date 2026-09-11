package com.example.kuiklyaistock.model

data class StockPosition(
    val code: String,
    val quantity: Int,
    val averageCost: Double,
)

data class PortfolioState(
    val version: Int = 1,
    val favoriteCodes: List<String> = emptyList(),
    val positions: List<StockPosition> = emptyList(),
)

sealed class PortfolioLoadResult {
    data class Success(val state: PortfolioState) : PortfolioLoadResult()
    data class Failure(val message: String) : PortfolioLoadResult()
}

sealed class TradeResult {
    data class Success(val position: StockPosition?) : TradeResult()
    data class Failure(val message: String) : TradeResult()
}
