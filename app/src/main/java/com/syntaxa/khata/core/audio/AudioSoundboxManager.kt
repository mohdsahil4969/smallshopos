package com.syntaxa.khata.core.audio

import android.content.Context
import android.content.SharedPreferences
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.util.Log
import com.syntaxa.khata.core.model.CurrencyFormatter
import java.util.Locale

/**
 * AudioSoundboxManager delivers instant audio payment confirmations ("Virtual Soundbox").
 * Replicates the Paytm / PhonePe hardware soundbox experience directly from the phone:
 * 1. Plays a high-frequency acknowledgment chime tone.
 * 2. Speaks the received amount and payment mode aloud (e.g., "Received 250 Rupees via UPI"
 *    or "यूपीआई पर 250 रुपये प्राप्त हुए") using Android's native TextToSpeech engine.
 */
class AudioSoundboxManager(
    private val context: Context,
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
) : TextToSpeech.OnInitListener {

    companion object {
        private const val TAG = "AudioSoundboxManager"
        private const val PREFS_NAME = "syntaxa_soundbox_prefs"
        private const val KEY_ENABLED = "is_soundbox_voice_alerts_enabled"
        private const val KEY_LANGUAGE = "soundbox_speech_language"

        const val LANG_AUTO = "auto"
        const val LANG_EN = "en"
        const val LANG_HI = "hi"
    }

    private var tts: TextToSpeech? = null
    private var isTtsReady = false
    private var pendingSpeech: String? = null
    private var pendingLocale: Locale? = null

    private val mainHandler = Handler(Looper.getMainLooper())

    private var toneGenerator: ToneGenerator? = try {
        ToneGenerator(AudioManager.STREAM_MUSIC, 85)
    } catch (_: Throwable) {
        null
    }

    init {
        try {
            tts = TextToSpeech(context.applicationContext, this)
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to instantiate TextToSpeech", e)
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            isTtsReady = true
            tts?.setSpeechRate(0.95f) // Slightly relaxed pace for maximum intelligibility in noisy retail shops
            tts?.setPitch(1.05f)

            // If a speech request arrived before TTS finished binding, speak it now
            pendingSpeech?.let { text ->
                val loc = pendingLocale ?: Locale.getDefault()
                speakInternal(text, loc)
                pendingSpeech = null
                pendingLocale = null
            }
        } else {
            Log.w(TAG, "TextToSpeech init returned non-success status: $status")
        }
    }

    /**
     * Checks whether the audio payment voice announcement is enabled.
     */
    fun isEnabled(): Boolean {
        return prefs.getBoolean(KEY_ENABLED, true)
    }

    /**
     * Enables or disables audio payment voice announcements.
     */
    fun setEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    /**
     * Gets preferred soundbox speech language ("auto", "hi", "en").
     */
    fun getSpeechLanguage(): String {
        return prefs.getString(KEY_LANGUAGE, LANG_AUTO) ?: LANG_AUTO
    }

    /**
     * Sets preferred soundbox speech language.
     */
    fun setSpeechLanguage(lang: String) {
        prefs.edit().putString(KEY_LANGUAGE, lang).apply()
    }

    /**
     * Announces a received payment aloud with a preceding POS chime.
     *
     * @param amountPaise The amount received in paise (e.g. 25000L = ₹250.00).
     * @param method Payment method ("UPI", "CASH", "SPLIT", etc.).
     * @param storeLanguage Language code of current store profile ("hi" or "en").
     */
    fun announcePayment(
        amountPaise: Long,
        method: String = "UPI",
        storeLanguage: String? = null
    ) {
        if (!isEnabled() || amountPaise <= 0L) return

        val cleanRupees = formatRupeesForSpeech(amountPaise)
        val selectedLang = resolveTargetLanguage(storeLanguage)
        val announcement = buildSpeechText(cleanRupees, method, selectedLang)

        playChimeAndSpeak(announcement, if (selectedLang == LANG_HI) Locale("hi", "IN") else Locale.ENGLISH)
    }

    /**
     * Plays a sample test announcement for settings preview.
     */
    fun playTestAnnouncement(storeLanguage: String? = null) {
        val selectedLang = resolveTargetLanguage(storeLanguage)
        val announcement = if (selectedLang == LANG_HI) {
            "सिंटैक्सा पर ₹100 प्राप्त हुए"
        } else {
            "Received 100 Rupees on Syntaxa"
        }
        playChimeAndSpeak(announcement, if (selectedLang == LANG_HI) Locale("hi", "IN") else Locale.ENGLISH)
    }

    private fun playChimeAndSpeak(text: String, locale: Locale) {
        // 1. Play POS hardware chime tone
        try {
            toneGenerator?.startTone(ToneGenerator.TONE_PROP_ACK, 120)
        } catch (_: Throwable) {}

        // 2. Delay slightly so the chime completes before speech begins
        mainHandler.postDelayed({
            if (isTtsReady && tts != null) {
                speakInternal(text, locale)
            } else {
                pendingSpeech = text
                pendingLocale = locale
            }
        }, 180)
    }

    private fun speakInternal(text: String, locale: Locale) {
        try {
            val supported = tts?.isLanguageAvailable(locale)
            if (supported != null && supported >= TextToSpeech.LANG_AVAILABLE) {
                tts?.language = locale
            } else {
                // Fallback to default device locale
                tts?.language = Locale.getDefault()
            }
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "PAYMENT_CONFIRMATION_${System.currentTimeMillis()}")
        } catch (e: Throwable) {
            Log.e(TAG, "Error executing TextToSpeech speak", e)
        }
    }

    /**
     * Determines whether speech should be in Hindi or English based on settings or store language.
     */
    private fun resolveTargetLanguage(storeLanguage: String?): String {
        return when (val pref = getSpeechLanguage()) {
            LANG_HI -> LANG_HI
            LANG_EN -> LANG_EN
            else -> {
                // AUTO: Inherit store language or device language
                if (storeLanguage?.equals("hi", ignoreCase = true) == true) {
                    LANG_HI
                } else {
                    val defaultLang = Locale.getDefault().language
                    if (defaultLang.equals("hi", ignoreCase = true)) LANG_HI else LANG_EN
                }
            }
        }
    }

    /**
     * Formats rupee amount to natural number speech text (e.g. 250, 49.50).
     */
    fun formatRupeesForSpeech(amountPaise: Long): String {
        val rupees = amountPaise / 100.0
        return if (amountPaise % 100L == 0L) {
            (amountPaise / 100L).toString()
        } else {
            String.format(Locale.US, "%.2f", rupees)
        }
    }

    /**
     * Constructs clean, natural announcement strings for Indian retail scenarios.
     */
    fun buildSpeechText(cleanRupees: String, method: String, lang: String): String {
        val isUpi = method.equals("UPI", ignoreCase = true)
        val isCash = method.equals("CASH", ignoreCase = true)

        return if (lang == LANG_HI) {
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

    /**
     * Releases resources when application terminates.
     */
    fun shutdown() {
        try {
            toneGenerator?.release()
            toneGenerator = null
            tts?.stop()
            tts?.shutdown()
            tts = null
            isTtsReady = false
        } catch (_: Throwable) {}
    }
}
