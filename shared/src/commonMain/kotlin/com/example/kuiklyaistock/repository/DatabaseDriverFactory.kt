package com.example.kuiklyaistock.repository

import app.cash.sqldelight.db.SqlDriver

// 跨平台数据库驱动工厂接口
expect class DatabaseDriverFactory {
    fun createDriver(): SqlDriver
}
