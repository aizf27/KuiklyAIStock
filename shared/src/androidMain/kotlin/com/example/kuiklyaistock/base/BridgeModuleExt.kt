package com.example.kuiklyaistock.base

import android.content.Context

// 获取 Android Context（在 androidMain 中实现）
internal fun BridgeModule.getContext(): Context {
    // 通过反射获取 pager 字段和 context
    val pagerField = this::class.java.getDeclaredField("pager")
    pagerField.isAccessible = true
    val pager = pagerField.get(this)

    // 获取 pagerData
    val pagerDataField = pager::class.java.getDeclaredField("pagerData")
    pagerDataField.isAccessible = true
    val pagerData = pagerDataField.get(pager)

    // 获取 pageViewContext
    val contextField = pagerData::class.java.getDeclaredField("pageViewContext")
    contextField.isAccessible = true
    return contextField.get(pagerData) as Context
}
