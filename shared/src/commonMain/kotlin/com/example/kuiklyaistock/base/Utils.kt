package com.example.kuiklyaistock.base

import com.tencent.kuikly.core.base.BaseObject
import com.tencent.kuikly.core.manager.BridgeManager
import com.tencent.kuikly.core.manager.PagerManager

/**
 * 工具类，提供页面间通用的辅助方法
 */
internal object Utils : BaseObject() {

    // 获取指定页面的 BridgeModule 实例
    fun bridgeModule(pager: String): BridgeModule {
        return PagerManager.getPager(pager).acquireModule<BridgeModule>(BridgeModule.MODULE_NAME)
    }

    // 向原生端输出日志，用于调试
    fun logToNative(pagerId: String, content: String) {
        bridgeModule(pagerId).log(content)
    }

    // 获取当前页面的 BridgeModule 实例
    fun currentBridgeModule(): BridgeModule {
        return PagerManager.getPager(BridgeManager.currentPageId).acquireModule<BridgeModule>(
            BridgeModule.MODULE_NAME
        )
    }

    // 向原生端输出当前页面的日志
    fun logToNative(content: String) {
        bridgeModule(BridgeManager.currentPageId).log(content)
    }

    // 将价格从分转换为元的字符串表示
    fun convertToPriceStr(price: Long): String {
        return (price / 100f).toString()
    }

}