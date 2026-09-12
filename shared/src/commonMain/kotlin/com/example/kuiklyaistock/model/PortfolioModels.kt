package com.example.kuiklyaistock.model

/**
 * 股票持仓信息
 * @param code 股票代码
 * @param quantity 持仓数量（手）
 * @param averageCost 平均成本价
 */
data class StockPosition(
    val code: String,
    val quantity: Int,
    val averageCost: Double,
)

/**
 * 用户投资组合状态，包含自选股和持仓股
 * @param version 数据版本号，用于兼容性管理
 * @param favoriteCodes 自选股代码列表
 * @param positions 持仓股列表
 */
data class PortfolioState(
    val version: Int = 1,
    val favoriteCodes: List<String> = emptyList(),
    val positions: List<StockPosition> = emptyList(),
)

/**
 * 投资组合加载结果
 */
sealed class PortfolioLoadResult {
    data class Success(val state: PortfolioState) : PortfolioLoadResult()
    data class Failure(val message: String) : PortfolioLoadResult()
}

/**
 * 交易操作结果（买入/卖出）
 */
sealed class TradeResult {
    // 成功，返回更新后的持仓（卖出清仓时为null）
    data class Success(val position: StockPosition?) : TradeResult()
    data class Failure(val message: String) : TradeResult()
}
