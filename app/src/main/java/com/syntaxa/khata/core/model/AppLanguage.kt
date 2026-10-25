package com.syntaxa.khata.core.model

data class AppLanguage(
    val code: String,
    val nativeName: String,
    val englishName: String
)

object SupportedLanguages {
    val all = listOf(
        AppLanguage("en", "English", "English"),
        AppLanguage("hi", "हिन्दी", "Hindi"),
        AppLanguage("te", "తెలుగు", "Telugu"),
        AppLanguage("kn", "ಕನ್ನಡ", "Kannada"),
        AppLanguage("ml", "മലയാളം", "Malayalam"),
        AppLanguage("pa", "ਪੰਜਾਬੀ", "Punjabi"),
        AppLanguage("ta", "தமிழ்", "Tamil"),
        AppLanguage("mr", "मराठी", "Marathi"),
        AppLanguage("gu", "ગુજરાતી", "Gujarati"),
        AppLanguage("bn", "বাংলা", "Bengali")
    )

    fun find(code: String): AppLanguage = all.find { it.code.equals(code, ignoreCase = true) } ?: all.first()
}
