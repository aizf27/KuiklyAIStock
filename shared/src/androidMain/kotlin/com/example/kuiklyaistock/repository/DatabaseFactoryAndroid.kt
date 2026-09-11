package com.example.kuiklyaistock.repository

import android.content.Context
import com.example.kuiklyaistock.db.StockDatabase
import com.tencent.kuikly.core.pager.Pager

// Android 平台实现：从 Pager 创建数据库
internal actual fun createDatabaseFromPager(pager: Pager): StockDatabase {
    // 使用 Application Context，通过反射获取 KRApplication.application
    val appClass = Class.forName("com.example.kuiklyaistock.KRApplication")
    val companionField = appClass.getDeclaredField("Companion")
    companionField.isAccessible = true
    val companion = companionField.get(null)

    val applicationField = companion::class.java.getDeclaredField("application")
    applicationField.isAccessible = true
    val context = applicationField.get(companion) as Context

    // 创建数据库
    return DatabaseFactory.getDatabase(DatabaseDriverFactory(context))
}
