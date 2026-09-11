package com.example.kuiklyaistock.repository

import android.content.Context
import com.example.kuiklyaistock.db.StockDatabase
import com.tencent.kuikly.core.pager.Pager

// Android 平台实现：从 Pager 创建数据库
internal actual fun createDatabaseFromPager(pager: Pager): StockDatabase {
    // 通过反射获取 Context，需要向上查找父类的 pagerData 字段
    val pagerDataField = findFieldInHierarchy(pager::class.java, "pagerData")
        ?: throw NoSuchFieldException("Cannot find pagerData field in Pager hierarchy")

    pagerDataField.isAccessible = true
    val pagerData = pagerDataField.get(pager)

    val contextField = pagerData::class.java.getDeclaredField("pageViewContext")
    contextField.isAccessible = true
    val context = contextField.get(pagerData) as Context

    // 创建数据库
    return DatabaseFactory.getDatabase(DatabaseDriverFactory(context))
}

// 在类层次结构中查找字段
private fun findFieldInHierarchy(clazz: Class<*>, fieldName: String): java.lang.reflect.Field? {
    var currentClass: Class<*>? = clazz
    while (currentClass != null) {
        try {
            return currentClass.getDeclaredField(fieldName)
        } catch (e: NoSuchFieldException) {
            currentClass = currentClass.superclass
        }
    }
    return null
}
