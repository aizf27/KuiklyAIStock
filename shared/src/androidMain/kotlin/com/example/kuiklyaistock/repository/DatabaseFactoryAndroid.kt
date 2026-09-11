package com.example.kuiklyaistock.repository

import android.content.Context
import com.example.kuiklyaistock.db.StockDatabase
import com.tencent.kuikly.core.pager.Pager

// Android 平台实现：从 Pager 创建数据库
internal actual fun createDatabaseFromPager(pager: Pager): StockDatabase {
    // 通过反射获取 Context
    val pagerDataField = pager::class.java.getDeclaredField("pagerData")
    pagerDataField.isAccessible = true
    val pagerData = pagerDataField.get(pager)

    val contextField = pagerData::class.java.getDeclaredField("pageViewContext")
    contextField.isAccessible = true
    val context = contextField.get(pagerData) as Context

    // 创建数据库
    return DatabaseFactory.getDatabase(DatabaseDriverFactory(context))
}
