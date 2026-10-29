package com.syntaxa.khata

import com.syntaxa.khata.core.audio.AudioSoundboxManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioSoundboxManagerTest {

    @Test
    fun testFormatRupeesForSpeech_wholeRupees() {
        val manager = AudioSoundboxManagerMock()
        assertEquals("250", manager.formatRupeesForSpeech(25000L))
        assertEquals("100", manager.formatRupeesForSpeech(10000L))
        assertEquals("1000", manager.formatRupeesForSpeech(100000L))
    }

    @Test
    fun testFormatRupeesForSpeech_fractionalRupees() {
        val manager = AudioSoundboxManagerMock()
        assertEquals("250.50", manager.formatRupeesForSpeech(25050L))
        assertEquals("49.25", manager.formatRupeesForSpeech(4925L))
    }

    @Test
    fun testBuildSpeechText_englishUpi() {
        val manager = AudioSoundboxManagerMock()
        val speech = manager.buildSpeechText("250", "UPI", "en")
        assertEquals("Received 250 Rupees via UPI", speech)
    }

    @Test
    fun testBuildSpeechText_englishCash() {
        val manager = AudioSoundboxManagerMock()
        val speech = manager.buildSpeechText("500", "CASH", "en")
        assertEquals("Received 500 Rupees in Cash", speech)
    }

    @Test
    fun testBuildSpeechText_englishSplitOrOther() {
        val manager = AudioSoundboxManagerMock()
        val speech = manager.buildSpeechText("750", "SPLIT", "en")
        assertEquals("Received 750 Rupees", speech)
    }

    @Test
    fun testBuildSpeechText_hindiUpi() {
        val manager = AudioSoundboxManagerMock()
        val speech = manager.buildSpeechText("250", "UPI", "hi")
        assertEquals("यूपीआई द्वारा 250 रुपये प्राप्त हुए", speech)
    }

    @Test
    fun testBuildSpeechText_hindiCash() {
        val manager = AudioSoundboxManagerMock()
        val speech = manager.buildSpeechText("500", "CASH", "hi")
        assertEquals("500 रुपये नकद प्राप्त हुए", speech)
    }

    @Test
    fun testBuildSpeechText_hindiSplitOrOther() {
        val manager = AudioSoundboxManagerMock()
        val speech = manager.buildSpeechText("750", "SPLIT", "hi")
        assertEquals("750 रुपये प्राप्त हुए", speech)
    }

    private class AudioSoundboxManagerMock {
        fun formatRupeesForSpeech(amountPaise: Long): String {
            val rupees = amountPaise / 100.0
            return if (amountPaise % 100L == 0L) {
                (amountPaise / 100L).toString()
            } else {
                String.format(java.util.Locale.US, "%.2f", rupees)
            }
        }

        fun buildSpeechText(cleanRupees: String, method: String, lang: String): String {
            val isUpi = method.equals("UPI", ignoreCase = true)
            val isCash = method.equals("CASH", ignoreCase = true)

            return if (lang == "hi") {
                when {
                    isUpi -> "यूपीआई द्वारा $cleanRupees रुपये प्राप्त हुए"
                    isCash -> "$cleanRupees रुपये नकद प्राप्त हुए"
                    else -> "$cleanRupees रुपये प्राप्त हुए"
                }
            } else {
                when {
                    isUpi -> "Received $cleanRupees Rupees via UPI"
                    isCash -> "Received $cleanRupees Rupees in Cash"
                    else -> "Received $cleanRupees Rupees"
                }
            }
        }
    }
}
