package com.example.kuiklyaistock

import com.example.kuiklyaistock.base.BasePager
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.module.RouterModule
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View

// 股票页面统一顶部栏，避免复用模板渐变导航栏。
internal fun ViewContainer<*, *>.StockTopBar(
    pager: BasePager,
    title: () -> String,
    showBack: Boolean,
) {
    View {
        attr {
            height(52f)
            backgroundColor(StockDesignTokens.surface)
        }
        View {
            attr {
                flex(1f)
                flexDirectionRow()
                alignItemsCenter()
                padding(left = 4f, right = 16f)
            }
            if (showBack) {
                View {
                    attr {
                        width(44f)
                        height(44f)
                        allCenter()
                    }
                    event { click { pager.acquireModule<RouterModule>(RouterModule.MODULE_NAME).closePage() } }
                    Text {
                        attr {
                            text("‹")
                            fontSize(28f)
                            color(StockDesignTokens.brand)
                        }
                    }
                }
            }
            Text {
                attr {
                    text(title())
                    fontSize(17f)
                    fontWeightBold()
                    color(StockDesignTokens.primaryText)
                    flex(1f)
                    textAlignCenter()
                }
            }
            if (showBack) {
                View { attr { width(44f) } }
            } else {
                View { attr { width(4f) } }
            }
        }
        View { attr { height(1f); backgroundColor(StockDesignTokens.divider) } }
    }
}
