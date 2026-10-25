package com.syntaxa.khata.core.model

import java.text.DecimalFormat
import java.text.NumberFormat
import java.util.Locale

/**
 * Exact integer paise currency model.
 * 100 paise = 1 INR (₹).
 * Floating-point types are strictly forbidden for currency values.
 */
object CurrencyFormatter {
    private val inrLocale: Locale = Locale("en", "IN")

    private val inrFormat: ThreadLocal<NumberFormat> = ThreadLocal.withInitial {
        NumberFormat.getCurrencyInstance(inrLocale).apply {
            maximumFractionDigits = 2
            minimumFractionDigits = 0
        }
    }

    private val plainDecimalFormat: ThreadLocal<DecimalFormat> = ThreadLocal.withInitial {
        DecimalFormat("#,##,##0.##")
    }

    /**
     * Formats integer paise to Indian Rupee string, e.g. 10050L -> "₹100.50", 50000L -> "₹500"
     */
    fun formatPaise(paise: Long, showSymbol: Boolean = true): String {
        val isNegative = paise < 0
        val absPaise = if (isNegative) -paise else paise
        val wholeRupees = absPaise / 100
        val remainingPaise = absPaise % 100

        val formattedNumber = if (remainingPaise == 0L) {
            plainDecimalFormat.get()?.format(wholeRupees) ?: wholeRupees.toString()
        } else {
            String.format(inrLocale, "%,d.%02d", wholeRupees, remainingPaise)
        }

        val prefix = if (isNegative) "-₹" else "₹"
        return if (showSymbol) "$prefix$formattedNumber" else (if (isNegative) "-$formattedNumber" else formattedNumber)
    }

    /**
     * Parses user rupee text input (e.g. "500", "500.50", "12.5") into exact integer Paise.
     * Throws NumberFormatException or returns 0L if invalid.
     */
    fun parseInputToPaise(input: String): Long {
        val clean = input.trim().replace(",", "").replace("₹", "")
        if (clean.isBlank()) return 0L
        val parts = clean.split(".")
        if (parts.size > 2) throw NumberFormatException("Invalid amount format")

        val wholeRupees = parts[0].toLongOrNull() ?: throw NumberFormatException("Invalid amount")
        if (wholeRupees < 0) throw NumberFormatException("Amount cannot be negative")

        val paisePart = if (parts.size == 2) {
            val decimalStr = parts[1]
            when {
                decimalStr.isEmpty() -> 0L
                decimalStr.length == 1 -> (decimalStr + "0").toLong()
                decimalStr.length == 2 -> decimalStr.toLong()
                else -> decimalStr.substring(0, 2).toLong() // round/truncate beyond 2 decimals
            }
        } else {
            0L
        }

        return Math.addExact(Math.multiplyExact(wholeRupees, 100L), paisePart)
    }
}
