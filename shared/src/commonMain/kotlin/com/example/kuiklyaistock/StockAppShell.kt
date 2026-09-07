package com.example.kuiklyaistock

import com.example.kuiklyaistock.base.BasePager
import com.tencent.kuikly.core.base.Color
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
    selectedTab: String,
    onTabSelected: (String) -> Unit,
) {
    View {
        attr {
            height(64f)
            backgroundColor(Color.WHITE)
            flexDirectionRow()
            borderTopWidth(1f)
            borderTopColor(Color(0xFFE5E7EB))
        }
        StockTabItem(StockTabs.MARKET, selectedTab == StockTabs.MARKET, onTabSelected)
        StockTabItem(StockTabs.WATCHLIST, selectedTab == StockTabs.WATCHLIST, onTabSelected)
        StockTabItem(StockTabs.AI, selectedTab == StockTabs.AI, onTabSelected)
    }
}

private fun ViewContainer<*, *>.StockTabItem(
    title: String,
    selected: Boolean,
    onTabSelected: (String) -> Unit,
) {
    View {
        attr {
            flex(1f)
            allCenter()
            backgroundColor(if (selected) Color(0xFFEFF6FF) else Color.WHITE)
        }
        event {
            click { onTabSelected(title) }
        }
        Text {
            attr {
                text(title)
                fontSize(13f)
                fontWeightBold()
                color(if (selected) Color(0xFF2563EB) else Color(0xFF6B7280))
            }
        }
    }
}

internal fun BasePager.openStockPage(pageName: String, code: String? = null) {
    val pageData = JSONObject()
    code?.let {
        pageData.put("code", it)
        pageData.put("symbol", it)
    }
    acquireModule<RouterModule>(RouterModule.MODULE_NAME).openPage(pageName, pageData)
}
