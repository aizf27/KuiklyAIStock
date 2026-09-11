package com.example.kuiklyaistock.repository

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class TencentQuoteParserTest {
    @Test
    fun mapsStocksAndIndices() {
        assertEquals("sh600519", TencentSymbolMapper.stockSymbol("600519"))
        assertEquals("sz000858", TencentSymbolMapper.stockSymbol("000858"))
        assertEquals("sz300750", TencentSymbolMapper.stockSymbol("300750"))
        assertEquals("sh000001", TencentSymbolMapper.indexSymbol("000001"))
        assertEquals("sz399001", TencentSymbolMapper.indexSymbol("399001"))
        assertEquals("sz399006", TencentSymbolMapper.indexSymbol("399006"))
        assertEquals(null, TencentSymbolMapper.stockSymbol("800000"))
    }

    @Test
    fun decodesGbkChineseName() {
        val bytes = byteArrayOf(
            185.toByte(), 243.toByte(), 214.toByte(), 221.toByte(),
            195.toByte(), 169.toByte(), 204.toByte(), 168.toByte(),
        )

        assertEquals("贵州茅台", TencentQuoteDecoder.decode(bytes))
    }

    @Test
    fun rejectsEmptyAndMalformedGbkBodies() {
        assertFailsWith<IllegalArgumentException> { TencentQuoteDecoder.decode(byteArrayOf()) }
        assertFailsWith<IllegalArgumentException> { TencentQuoteDecoder.decode(byteArrayOf(0x81.toByte())) }
    }

    @Test
    fun parsesSingleAndBatchResponsesWithUnitsAndTime() {
        val response = listOf(
            record("sh600519", name = "Moutai"),
            record("sz300750", name = "CATL", price = "220.50", low = "210.00", high = "225.00"),
        ).joinToString("\n")

        val result = TencentQuoteParser.parse(response, listOf("sh600519", "sz300750"))

        assertEquals(2, result.quotes.size)
        assertTrue(result.missingSymbols.isEmpty())
        assertEquals(1_234_500L, result.quotes.first().volume)
        assertEquals(193_525_000.0, result.quotes.first().turnover)
        assertEquals("2026-09-11 10:30:45", result.quotes.first().updatedAt)
    }

    @Test
    fun reportsEmptyPrefixMissingAndCodeMismatch() {
        val empty = TencentQuoteParser.parse("", listOf("sh600519"))
        val prefix = TencentQuoteParser.parse("bad", listOf("sh600519"))
        val missing = TencentQuoteParser.parse(record("sh600519"), listOf("sh600519", "sz300750"))
        val mismatch = TencentQuoteParser.parse(record("sh600519", code = "600000"), listOf("sh600519"))

        assertEquals(TencentQuoteParseErrorCategory.EMPTY, empty.errors.single().category)
        assertEquals(TencentQuoteParseErrorCategory.PREFIX, prefix.errors.single().category)
        assertEquals(listOf("sz300750"), missing.missingSymbols)
        assertEquals(TencentQuoteParseErrorCategory.CODE_MISMATCH, mismatch.errors.single().category)
    }

    @Test
    fun rejectsInsufficientFieldsInvalidNumbersPricesRangesAndTime() {
        val cases = listOf(
            "v_sh600519=\"x~Moutai~600519\";" to TencentQuoteParseErrorCategory.FIELD_COUNT,
            record("sh600519", price = "bad") to TencentQuoteParseErrorCategory.INVALID_NUMBER,
            record("sh600519", price = "0") to TencentQuoteParseErrorCategory.INVALID_PRICE,
            record("sh600519", high = "1500", low = "1600") to TencentQuoteParseErrorCategory.INVALID_RANGE,
            record("sh600519", volumeLots = "-1") to TencentQuoteParseErrorCategory.INVALID_RANGE,
            record("sh600519", time = "") to TencentQuoteParseErrorCategory.MISSING_TIME,
            record("sh600519", time = "not-time") to TencentQuoteParseErrorCategory.MISSING_TIME,
        )

        cases.forEach { (body, expected) ->
            val result = TencentQuoteParser.parse(body, listOf("sh600519"))
            assertEquals(expected, result.errors.single().category)
            assertEquals(listOf("sh600519"), result.missingSymbols)
        }
    }

    private fun record(
        symbol: String,
        name: String = "Moutai",
        code: String = symbol.drop(2),
        price: String = "1568.20",
        previousClose: String = "1581.80",
        open: String = "1575.00",
        volumeLots: String = "12345",
        time: String = "20260911103045",
        change: String = "-13.60",
        percent: String = "-0.86",
        high: String = "1580.00",
        low: String = "1560.00",
        turnoverWan: String = "19352.50",
    ): String {
        val fields = MutableList(38) { "" }
        fields[1] = name
        fields[2] = code
        fields[3] = price
        fields[4] = previousClose
        fields[5] = open
        fields[6] = volumeLots
        fields[30] = time
        fields[31] = change
        fields[32] = percent
        fields[33] = high
        fields[34] = low
        fields[37] = turnoverWan
        return """v_$symbol="${fields.joinToString("~")}";"""
    }
}
