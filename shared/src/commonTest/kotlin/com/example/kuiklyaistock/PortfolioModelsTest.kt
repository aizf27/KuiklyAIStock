package com.example.kuiklyaistock

import com.example.kuiklyaistock.model.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertNotNull

/**
 * 投资组合模型单元测试
 * 验证持仓和自选股数据结构
 */
class PortfolioModelsTest {

    @Test
    fun `StockPosition 应该正确存储持仓信息`() {
        val position = StockPosition(
            code = "sh601318",
            quantity = 500,
            averageCost = 48.5
        )

        assertEquals("sh601318", position.code)
        assertEquals(500, position.quantity)
        assertEquals(48.5, position.averageCost)
    }

    @Test
    fun `PortfolioState 应该支持空状态初始化`() {
        val emptyState = PortfolioState()

        assertEquals(1, emptyState.version)
        assertTrue(emptyState.favoriteCodes.isEmpty(), "初始自选股列表应该为空")
        assertTrue(emptyState.positions.isEmpty(), "初始持仓列表应该为空")
    }

    @Test
    fun `PortfolioState 应该支持带数据初始化`() {
        val state = PortfolioState(
            version = 1,
            favoriteCodes = listOf("sh601318", "sh600519", "sh600036"),
            positions = listOf(
                StockPosition("sh601318", 500, 48.5),
                StockPosition("sh600519", 10, 1600.0)
            )
        )

        assertEquals(1, state.version)
        assertEquals(3, state.favoriteCodes.size)
        assertEquals(2, state.positions.size)
        assertTrue(state.favoriteCodes.contains("sh601318"))
        assertEquals(500, state.positions[0].quantity)
    }

    @Test
    fun `PortfolioLoadResult Success 应该返回状态`() {
        val state = PortfolioState(
            favoriteCodes = listOf("sh601318")
        )
        val result = PortfolioLoadResult.Success(state)

        assertTrue(result is PortfolioLoadResult.Success)
        assertEquals(1, result.state.favoriteCodes.size)
    }

    @Test
    fun `PortfolioLoadResult Failure 应该包含错误信息`() {
        val result = PortfolioLoadResult.Failure("数据解析失败")

        assertTrue(result is PortfolioLoadResult.Failure)
        assertEquals("数据解析失败", result.message)
    }

    @Test
    fun `TradeResult Success 应该返回持仓信息`() {
        val position = StockPosition("sh601318", 500, 48.5)
        val result = TradeResult.Success(position)

        assertTrue(result is TradeResult.Success)
        assertNotNull(result.position)
        assertEquals("sh601318", result.position?.code)
    }

    @Test
    fun `TradeResult Success 清仓时持仓应该为null`() {
        val result = TradeResult.Success(null)

        assertTrue(result is TradeResult.Success)
        assertEquals(null, result.position, "清仓后持仓应该为null")
    }

    @Test
    fun `TradeResult Failure 应该包含错误信息`() {
        val result = TradeResult.Failure("余额不足")

        assertTrue(result is TradeResult.Failure)
        assertEquals("余额不足", result.message)
    }

    @Test
    fun `持仓计算盈亏逻辑验证`() {
        val position = StockPosition(
            code = "sh601318",
            quantity = 500,
            averageCost = 48.5
        )
        val currentPrice = 50.5

        // 计算持仓市值
        val marketValue = position.quantity * 100 * currentPrice
        assertEquals(2525000.0, marketValue)

        // 计算成本
        val cost = position.quantity * 100 * position.averageCost
        assertEquals(2425000.0, cost)

        // 计算浮动盈亏
        val profit = marketValue - cost
        assertEquals(100000.0, profit)

        // 计算盈亏比例
        val profitPercent = (profit / cost) * 100
        assertTrue(profitPercent > 4.0 && profitPercent < 4.2, "盈亏比例应该约为 4.12%")
    }
}
