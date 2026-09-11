package com.example.kuiklyaistock.repository

import com.example.kuiklyaistock.base.BridgeModule
import com.example.kuiklyaistock.model.PortfolioLoadResult

internal class NativePortfolioStateStorage(
    private val bridgeModule: BridgeModule,
) : PortfolioStateStorage {
    override fun read(key: String): String = bridgeModule.getCachedFromNative(key)

    override fun write(key: String, value: String) {
        bridgeModule.setCachedToNative(key, value)
    }
}

internal object PortfolioPersistence {
    fun ensureLoaded(bridgeModule: BridgeModule) {
        if (PortfolioStore.isLoaded()) return
        val serialized = try {
            NativePortfolioStateStorage(bridgeModule).read(PortfolioStore.STORAGE_KEY)
        } catch (error: Throwable) {
            PortfolioStore.load(null)
            bridgeModule.log("资产状态读取失败，已回退为空状态: ${error.message ?: "未知错误"}")
            return
        }
        when (val result = PortfolioStore.load(serialized)) {
            is PortfolioLoadResult.Success -> bridgeModule.log("资产状态恢复成功")
            is PortfolioLoadResult.Failure -> bridgeModule.log("资产状态恢复失败，已回退为空状态: ${result.message}")
        }
    }

    fun save(bridgeModule: BridgeModule) {
        try {
            NativePortfolioStateStorage(bridgeModule).write(
                PortfolioStore.STORAGE_KEY,
                PortfolioStore.serialize(),
            )
            bridgeModule.log("资产状态已写入本地")
        } catch (error: Throwable) {
            bridgeModule.log("资产状态写入失败: ${error.message ?: "未知错误"}")
        }
    }
}
