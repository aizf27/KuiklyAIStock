package com.example.kuiklyaistock.repository

import com.example.kuiklyaistock.db.StockDatabase

// 数据库单例工厂
internal object DatabaseFactory {
    private var instance: StockDatabase? = null

    fun getDatabase(driverFactory: DatabaseDriverFactory): StockDatabase {
        return instance ?: synchronized(this) {
            instance ?: StockDatabase(driverFactory.createDriver()).also { instance = it }
        }
    }
}
