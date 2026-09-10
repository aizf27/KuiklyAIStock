package com.example.kuiklyaistock

import com.example.kuiklyaistock.base.BasePager
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.module.RouterModule
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View
import kotlin.math.max

internal fun BasePager.stockTopInset(): Float =
    max(pagerData.statusBarHeight, pagerData.safeAreaInsets.top)

internal fun BasePager.stockBottomInset(): Float =
    max(pagerData.androidBottomBavBarHeight, pagerData.safeAreaInsets.bottom)

internal fun BasePager.stockContentWidth(): Float =
    (pagerData.pageViewWidth - StockDesignTokens.pageHorizontalPadding * 2f).coerceAtLeast(0f)

// 股票页面统一顶部栏，标题和返回按钮始终位于系统安全区下方。
internal fun ViewContainer<*, *>.StockTopBar(
    pager: BasePager,
    title: () -> String,
    showBack: Boolean,
) {
    val topInset = pager.stockTopInset()
    View {
        attr {
            height(topInset + StockDesignTokens.topBarContentHeight)
            backgroundColor(StockDesignTokens.surface)
        }
        View { attr { height(topInset) } }
        View {
            attr {
                height(StockDesignTokens.topBarContentHeight - 1f)
                flexDirectionRow()
                alignItemsCenter()
            }
            View {
                attr {
                    width(StockDesignTokens.minimumTouchTarget)
                    height(StockDesignTokens.minimumTouchTarget)
                    allCenter()
                }
                if (showBack) {
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
            View {
                attr {
                    width(StockDesignTokens.minimumTouchTarget)
                    height(StockDesignTokens.minimumTouchTarget)
                }
            }
        }
        View { attr { height(1f); backgroundColor(StockDesignTokens.divider) } }
    }
}
