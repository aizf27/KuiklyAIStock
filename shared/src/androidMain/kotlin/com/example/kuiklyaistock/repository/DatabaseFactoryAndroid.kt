package com.example.kuiklyaistock.repository

import android.content.Context
import com.example.kuiklyaistock.db.StockDatabase
import com.tencent.kuikly.core.pager.Pager

// Android 平台实现：从 Pager 创建数据库
internal actual fun createDatabaseFromPager(pager: Pager): StockDatabase {
    // 使用 Application Context，调用 KRApplication 的静态方法
    val appClass = Class.forName("com.example.kuiklyaistock.KRApplication")
    val getContextMethod = appClass.getMethod("getAppContext")
    val context = getContextMethod.invoke(null) as Context

    // 创建数据库
    return DatabaseFactory.getDatabase(DatabaseDriverFactory(context))
}
