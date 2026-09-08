package com.example.kuiklyaistock

import com.example.kuiklyaistock.base.BasePager
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.module.RouterModule
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View

internal object StockTabs {
    const val MARKET = "行情"
    const val WATCHLIST = "自选"
    const val AI = "AI解读"

}

// 应用底部主导航，页面只负责处理 Tab 选择后的业务动作。
internal fun ViewContainer<*, *>.StockBottomBar(
    selectedTab: () -> String,
    onTabSelected: (String) -> Unit,
) {
    View {
        attr {
            height(64f)
            backgroundColor(StockDesignTokens.surface)
        }
        View { attr { height(1f); backgroundColor(StockDesignTokens.divider) } }
        View {
            attr { flex(1f); flexDirectionRow() }
            StockTabItem("⌁", StockTabs.MARKET, { selectedTab() == StockTabs.MARKET }, onTabSelected)
            StockTabItem("♡", StockTabs.WATCHLIST, { selectedTab() == StockTabs.WATCHLIST }, onTabSelected)
            StockTabItem("✦", StockTabs.AI, { selectedTab() == StockTabs.AI }, onTabSelected)
        }
    }
}

private fun ViewContainer<*, *>.StockTabItem(
    icon: String,
    title: String,
    selected: () -> Boolean,
    onTabSelected: (String) -> Unit,
) {
    View {
        attr {
            flex(1f)
            allCenter()
            backgroundColor(StockDesignTokens.surface)
        }
        event {
            click { onTabSelected(title) }
        }
        Text {
            attr {
                text(icon)
                fontSize(16f)
                fontWeightBold()
                color(if (selected()) StockDesignTokens.brand else StockDesignTokens.secondaryText)
                marginBottom(2f)
            }
        }
        Text {
            attr {
                text(title)
                fontSize(13f)
                fontWeightBold()
                color(if (selected()) StockDesignTokens.brand else StockDesignTokens.secondaryText)
            }
        }
    }
}

internal fun BasePager.openStockDetail(code: String) {
    val pageData = JSONObject()
    pageData.put("code", code)
    acquireModule<RouterModule>(RouterModule.MODULE_NAME).openPage("stock_detail", pageData)
}
