package com.example.kuiklyaistock.repository

import java.nio.charset.Charset

internal actual fun decodeGbkPlatform(bytes: ByteArray): String = Charset.forName("GBK").decode(java.nio.ByteBuffer.wrap(bytes)).toString()
