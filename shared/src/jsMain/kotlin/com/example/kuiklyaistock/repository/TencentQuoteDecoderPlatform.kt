package com.example.kuiklyaistock.repository

import kotlin.js.unsafeCast

internal actual fun decodeGbkPlatform(bytes: ByteArray): String =
    js("new TextDecoder('gbk').decode(bytes)").unsafeCast<String>()
