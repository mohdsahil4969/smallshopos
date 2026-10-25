package com.syntaxa.khata

import com.syntaxa.khata.core.model.CurrencyFormatter
import org.junit.Assert.assertEquals
import org.junit.Test

class CurrencyFormatterTest {
    @Test
    fun testPaiseFormatting() {
        assertEquals("₹500", CurrencyFormatter.formatPaise(50000L))
        assertEquals("₹100.50", CurrencyFormatter.formatPaise(10050L))
        assertEquals("₹0", CurrencyFormatter.formatPaise(0L))
        assertEquals("-₹50.25", CurrencyFormatter.formatPaise(-5025L))
    }

    @Test
    fun testInputParsingToPaise() {
        assertEquals(50000L, CurrencyFormatter.parseInputToPaise("500"))
        assertEquals(50050L, CurrencyFormatter.parseInputToPaise("500.50"))
        assertEquals(50050L, CurrencyFormatter.parseInputToPaise("500.5"))
        assertEquals(1275L, CurrencyFormatter.parseInputToPaise("12.75"))
        assertEquals(0L, CurrencyFormatter.parseInputToPaise(""))
        assertEquals(10000000L, CurrencyFormatter.parseInputToPaise("1,00,000"))
    }
}
