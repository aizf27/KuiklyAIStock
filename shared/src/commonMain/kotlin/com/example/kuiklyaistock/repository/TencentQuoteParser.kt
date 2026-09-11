package com.example.kuiklyaistock.repository

import kotlin.math.roundToLong

internal data class TencentParsedQuote(
    val symbol: String,
    val name: String,
    val code: String,
    val price: Double,
    val previousClose: Double,
    val open: Double,
    val high: Double,
    val low: Double,
    val volume: Long,
    val turnover: Double,
    val change: Double,
    val changePercent: Double,
    val updatedAt: String,
)

internal data class TencentQuoteParseError(
    val symbol: String,
    val category: TencentQuoteParseErrorCategory,
)

internal enum class TencentQuoteParseErrorCategory {
    EMPTY,
    PREFIX,
    FIELD_COUNT,
    CODE_MISMATCH,
    INVALID_NUMBER,
    INVALID_PRICE,
    INVALID_RANGE,
    MISSING_TIME,
}

internal data class TencentQuoteParseResult(
    val quotes: List<TencentParsedQuote>,
    val missingSymbols: List<String>,
    val errors: List<TencentQuoteParseError>,
)

internal object TencentQuoteParser {
    private const val NAME_INDEX = 1
    private const val CODE_INDEX = 2
    private const val PRICE_INDEX = 3
    private const val PREVIOUS_CLOSE_INDEX = 4
    private const val OPEN_INDEX = 5
    private const val VOLUME_INDEX = 6
    private const val TIME_INDEX = 30
    private const val CHANGE_INDEX = 31
    private const val CHANGE_PERCENT_INDEX = 32
    private const val HIGH_INDEX = 33
    private const val LOW_INDEX = 34
    private const val TURNOVER_INDEX = 37
    private const val MIN_FIELDS = TURNOVER_INDEX + 1
    private val recordPattern = Regex("""v_([a-z]{2}\d{6})\s*=\s*"([^"]*)"\s*;?""", RegexOption.IGNORE_CASE)

    fun parse(text: String, requestedSymbols: List<String>): TencentQuoteParseResult {
        val requested = requestedSymbols.map { it.trim().lowercase() }.distinct()
        if (text.isBlank()) {
            return TencentQuoteParseResult(
                emptyList(),
                requested,
                requested.map { TencentQuoteParseError(it, TencentQuoteParseErrorCategory.EMPTY) },
            )
        }
        val cleaned = text.trim().removePrefix("\uFEFF")
        if (!cleaned.startsWith("v_")) {
            return TencentQuoteParseResult(
                emptyList(),
                requested,
                requested.map { TencentQuoteParseError(it, TencentQuoteParseErrorCategory.PREFIX) },
            )
        }
        val records = recordPattern.findAll(cleaned).associateBy { it.groupValues[1].lowercase() }
        val quotes = mutableListOf<TencentParsedQuote>()
        val errors = mutableListOf<TencentQuoteParseError>()
        requested.forEach { symbol ->
            val record = records[symbol]
            if (record == null) {
                errors += TencentQuoteParseError(symbol, TencentQuoteParseErrorCategory.CODE_MISMATCH)
                return@forEach
            }
            when (val parsed = parseRecord(symbol, record.groupValues[2])) {
                is ParsedQuote.Valid -> quotes += parsed.value
                is ParsedQuote.Invalid -> errors += TencentQuoteParseError(symbol, parsed.category)
            }
        }
        val found = quotes.map { it.symbol }.toSet()
        return TencentQuoteParseResult(quotes, requested.filterNot(found::contains), errors)
    }

    private fun parseRecord(symbol: String, body: String): ParsedQuote {
        val fields = body.split('~')
        if (fields.size < MIN_FIELDS) return ParsedQuote.Invalid(TencentQuoteParseErrorCategory.FIELD_COUNT)
        val name = fields[NAME_INDEX].trim()
        val returnedCode = fields[CODE_INDEX].trim()
        if (name.isEmpty() || returnedCode != symbol.substring(2)) {
            return ParsedQuote.Invalid(TencentQuoteParseErrorCategory.CODE_MISMATCH)
        }
        val price = fields.number(PRICE_INDEX) ?: return ParsedQuote.Invalid(TencentQuoteParseErrorCategory.INVALID_NUMBER)
        val previousClose = fields.number(PREVIOUS_CLOSE_INDEX) ?: return ParsedQuote.Invalid(TencentQuoteParseErrorCategory.INVALID_NUMBER)
        val open = fields.number(OPEN_INDEX) ?: return ParsedQuote.Invalid(TencentQuoteParseErrorCategory.INVALID_NUMBER)
        val volumeLots = fields.number(VOLUME_INDEX) ?: return ParsedQuote.Invalid(TencentQuoteParseErrorCategory.INVALID_NUMBER)
        val change = fields.number(CHANGE_INDEX) ?: return ParsedQuote.Invalid(TencentQuoteParseErrorCategory.INVALID_NUMBER)
        val changePercent = fields.number(CHANGE_PERCENT_INDEX) ?: return ParsedQuote.Invalid(TencentQuoteParseErrorCategory.INVALID_NUMBER)
        val high = fields.number(HIGH_INDEX) ?: return ParsedQuote.Invalid(TencentQuoteParseErrorCategory.INVALID_NUMBER)
        val low = fields.number(LOW_INDEX) ?: return ParsedQuote.Invalid(TencentQuoteParseErrorCategory.INVALID_NUMBER)
        val turnoverWan = fields.number(TURNOVER_INDEX) ?: return ParsedQuote.Invalid(TencentQuoteParseErrorCategory.INVALID_NUMBER)
        val updatedAt = normalizeTime(fields[TIME_INDEX]) ?: return ParsedQuote.Invalid(TencentQuoteParseErrorCategory.MISSING_TIME)
        if (price <= 0.0 || previousClose <= 0.0 || open <= 0.0 || high <= 0.0 || low <= 0.0) {
            return ParsedQuote.Invalid(TencentQuoteParseErrorCategory.INVALID_PRICE)
        }
        if (volumeLots < 0.0 || turnoverWan < 0.0 || high < low || price !in low..high) {
            return ParsedQuote.Invalid(TencentQuoteParseErrorCategory.INVALID_RANGE)
        }
        return ParsedQuote.Valid(
            TencentParsedQuote(
                symbol = symbol,
                name = name,
                code = returnedCode,
                price = price,
                previousClose = previousClose,
                open = open,
                high = high,
                low = low,
                volume = (volumeLots * 100.0).roundToLong(),
                turnover = turnoverWan * 10_000.0,
                change = change,
                changePercent = changePercent,
                updatedAt = updatedAt,
            )
        )
    }

    private fun List<String>.number(index: Int): Double? =
        getOrNull(index)?.trim()?.takeIf { it.isNotEmpty() }?.toDoubleOrNull()?.takeIf { it.isFinite() }

    private fun normalizeTime(raw: String): String? {
        val value = raw.trim()
        return when {
            value.matches(Regex("\\d{14}")) ->
                "${value.substring(0, 4)}-${value.substring(4, 6)}-${value.substring(6, 8)} " +
                    "${value.substring(8, 10)}:${value.substring(10, 12)}:${value.substring(12, 14)}"
            value.matches(Regex("\\d{8}")) ->
                "${value.substring(0, 4)}-${value.substring(4, 6)}-${value.substring(6, 8)}"
            value.matches(Regex("\\d{4}-\\d{2}-\\d{2}[ T]\\d{2}:\\d{2}:\\d{2}")) -> value.replace('T', ' ')
            else -> null
        }
    }

    private sealed class ParsedQuote {
        data class Valid(val value: TencentParsedQuote) : ParsedQuote()
        data class Invalid(val category: TencentQuoteParseErrorCategory) : ParsedQuote()
    }
}
