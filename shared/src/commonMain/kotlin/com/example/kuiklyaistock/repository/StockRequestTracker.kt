package com.example.kuiklyaistock.repository

// 页面只接收最后一次请求结果，避免旧请求覆盖新状态。
internal class StockRequestTracker {
    private var latestRequestId = 0

    fun next(): Int = ++latestRequestId

    fun invalidate() {
        latestRequestId++
    }

    fun isLatest(requestId: Int): Boolean = requestId == latestRequestId
}
