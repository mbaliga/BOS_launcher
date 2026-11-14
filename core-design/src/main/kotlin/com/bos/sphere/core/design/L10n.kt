package com.bos.sphere.core.design

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale

/**
 * Tiny in-app localisation. The English string is the key; unknown keys (or languages without a
 * table) fall back to English. [language] is snapshot state so composables that call [t]
 * update live when it changes. Code "" follows the system locale.
 *
 * Format strings use `%s` / `%d` placeholders: `L10n.tr("Page %d", n)`.
 */
object L10n {
    private var state: String by mutableStateOf("")

    /** The selected language code ("" = follow the system). Snapshot state. */
    val language: String get() = state

    fun setLanguage(code: String) {
        state = code.trim().lowercase()
    }

    /** The effective two-letter code after resolving "" to the system locale. */
    fun effectiveLanguage(): String = language.ifEmpty { Locale.getDefault().language }

    /** Every English key that has a translation. */
    val englishKeys: Set<String> get() = L10nEnglish + L10nMoreEnglish + L10nMore2English + L10nMore3English + L10nLaunchpadEnglish + L10nInputEnglish + L10nStateEnglish + L10nBackupEnglish

    /** The translation table for a language code, or null for English / unknown codes. */
    fun tableFor(code: String): Map<String, String>? = merged[code]

    private val merged: Map<String, Map<String, String>> by lazy {
        mapOf(
            "es" to L10nEs, "fr" to L10nFr, "de" to L10nDe, "pt" to L10nPt,
            "hi" to L10nHi, "zh" to L10nZh, "ja" to L10nJa, "ar" to L10nAr,
        ).mapValues { (code, base) -> base + (L10nMore[code] ?: emptyMap()) + (L10nMore2[code] ?: emptyMap()) + (L10nMore3[code] ?: emptyMap()) + (L10nLaunchpad[code] ?: emptyMap()) + (L10nState[code] ?: emptyMap()) + (L10nBackup[code] ?: emptyMap()) + (L10nInput[code] ?: emptyMap()) }
    }

    /** Translate [english] (reads [language], so it is snapshot-aware). */
    fun tr(english: String): String = tableFor(effectiveLanguage())?.get(english) ?: english

    /** Translate, then apply [String.format] arguments. */
    fun tr(english: String, vararg args: Any?): String =
        runCatching { tr(english).format(*args) }.getOrElse { english.format(*args) }
}

/** Composable shorthand for [L10n.tr]; recomposes when the language changes. */
@Composable
fun t(english: String): String = L10n.tr(english)
