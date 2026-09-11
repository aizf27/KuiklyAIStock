package com.example.kuiklyaistock.base

import android.content.Context

// 获取 Android Context（在 androidMain 中实现）
fun BridgeModule.getContext(): Context {
    return this.pager.pagerData.pageViewContext as Context
}
