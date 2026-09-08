package com.example.kuiklyaistock

import com.tencent.kuikly.core.base.Color

// 股票页面共享的视觉语义，避免页面内散落相同的样式字面量。
internal object StockDesignTokens {
    val pageBackground = Color(0xFFF6F7F9)
    val surface = Color.WHITE
    val primaryText = Color(0xFF101828)
    val secondaryText = Color(0xFF667085)
    val tertiaryText = Color(0xFF98A2B3)
    val divider = Color(0xFFEAECF0)
    val brand = Color(0xFF246BFD)
    val rise = Color(0xFFE64545)
    val fall = Color(0xFF1A9B62)
    val flat = Color(0xFF667085)
    val risk = Color(0xFFB54708)
    val warningBackground = Color(0xFFFFFAEB)
    val brandBackground = Color(0xFFF0F5FF)

    const val pageHorizontalPadding = 16f
    const val sectionRadius = 8f
    const val quoteRowHeight = 68f
    const val minimumTouchTarget = 44f
}
