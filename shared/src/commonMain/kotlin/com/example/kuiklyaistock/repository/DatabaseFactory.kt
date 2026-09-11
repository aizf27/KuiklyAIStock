package com.example.kuiklyaistock.repository

import com.example.kuiklyaistock.db.StockDatabase
import com.tencent.kuikly.core.pager.Pager

// 数据库单例工厂
internal object DatabaseFactory {
    private var instance: StockDatabase? = null

    fun getDatabase(driverFactory: DatabaseDriverFactory): StockDatabase {
        return instance ?: synchronized(this) {
            instance ?: StockDatabase(driverFactory.createDriver()).also { instance = it }
        }
    }

    // 从 Pager 创建数据库（平台相关实现）
    fun getDatabaseFromPager(pager: Pager): StockDatabase {
        return instance ?: synchronized(this) {
            instance ?: createDatabaseFromPager(pager).also { instance = it }
        }
    }
}

// 平台相关的数据库创建函数
internal expect fun createDatabaseFromPager(pager: Pager): StockDatabase
