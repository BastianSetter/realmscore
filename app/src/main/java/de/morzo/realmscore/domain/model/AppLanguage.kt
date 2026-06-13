package de.morzo.realmscore.domain.model

/**
 * User-selected app language (Phase 19). [SYSTEM] follows the Android system locale, the others
 * force a specific language regardless of the device setting.
 */
enum class AppLanguage(val flagEmoji: String) {
    SYSTEM("🌐"), // 🌐 globe — follow device locale
    GERMAN("🇩🇪"), // 🇩🇪
    ENGLISH("🇬🇧"); // 🇬🇧

    companion object {
        fun fromName(name: String?): AppLanguage =
            entries.firstOrNull { it.name == name } ?: SYSTEM
    }
}
