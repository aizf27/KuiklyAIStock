package com.example.kuiklyaistock

import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.ComposeAttr
import com.tencent.kuikly.core.base.ComposeEvent
import com.tencent.kuikly.core.base.ComposeView
import com.tencent.kuikly.core.base.ViewBuilder
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.directives.vif
import com.tencent.kuikly.core.module.RouterModule
import com.tencent.kuikly.core.reactive.handler.observable
import com.tencent.kuikly.core.views.Image
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View

// 仅供模板基准测试页使用，股票业务页面使用 StockTopBar。
internal class TemplateNavigationBar : ComposeView<TemplateNavigationBarAttr, ComposeEvent>() {
    override fun createEvent() = ComposeEvent()

    override fun createAttr() = TemplateNavigationBarAttr()

    override fun body(): ViewBuilder {
        val ctx = this
        return {
            View {
                attr {
                    paddingTop(ctx.pagerData.statusBarHeight)
                    backgroundColor(Color.WHITE)
                }
                View {
                    attr {
                        height(44f)
                        allCenter()
                    }
                    Text {
                        attr {
                            text(ctx.attr.title)
                            fontSize(17f)
                            fontWeightSemiBold()
                            color(Color.BLACK)
                        }
                    }
                }
                vif({ !ctx.attr.backDisable }) {
                    Image {
                        attr {
                            absolutePosition(
                                top = 12f + getPager().pageData.statusBarHeight,
                                left = 12f,
                                bottom = 12f,
                                right = 12f,
                            )
                            size(10f, 17f)
                            src("data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAsAAAASBAMAAAB/WzlGAAAAElBMVEUAAAAAAAAAAAAAAAAAAAAAAADgKxmiAAAABXRSTlMAIN/PELVZAGcAAAAkSURBVAjXYwABQTDJqCQAooSCHUAcVROCHBiFECTMhVoEtRYA6UMHzQlOjQIAAAAASUVORK5CYII=")
                        }
                        event {
                            click {
                                getPager().acquireModule<RouterModule>(RouterModule.MODULE_NAME).closePage()
                            }
                        }
                    }
                }
            }
        }
    }
}

internal class TemplateNavigationBarAttr : ComposeAttr() {
    var title: String by observable("")
    var backDisable = false
}

internal fun ViewContainer<*, *>.TemplateNavBar(init: TemplateNavigationBar.() -> Unit) {
    addChild(TemplateNavigationBar(), init)
}
