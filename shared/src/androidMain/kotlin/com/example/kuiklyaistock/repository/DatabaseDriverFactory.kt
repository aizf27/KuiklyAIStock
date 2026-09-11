package com.example.kuiklyaistock.repository

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.example.kuiklyaistock.db.StockDatabase

// Android 平台的数据库驱动工厂
actual class DatabaseDriverFactory(private val context: Context) {
    actual fun createDriver(): SqlDriver {
        return AndroidSqliteDriver(
            schema = StockDatabase.Schema,
            context = context,
            name = "stock.db"
        )
    }
}
