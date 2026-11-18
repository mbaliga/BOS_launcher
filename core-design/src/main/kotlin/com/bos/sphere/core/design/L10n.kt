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
    val englishKeys: Set<String> get() = L10nEnglish + L10nMoreEnglish + L10nMore2English + L10nMore3English + L10nLaunchpadEnglish + L10nInputEnglish + L10nStateEnglish + L10nBackupEnglish + L10nStrWave2English + L10nNfrEnglish + L10nWave2FillEnglish + L10nW3homeLayoutEnglish + L10nW3homeInfoEnglish + L10nW3stateEnglish + L10nW4radarInputEnglish + L10nW4homeGapsEnglish + L10nW4stateGapsEnglish + L10nW4motionEnglish + L10nW5rebindInputEnglish + L10nW5homeRestEnglish + L10nW6auditPalInpEnglish + L10nW6auditHomeWspEnglish + L10nW6auditStrExtEnglish + L10nW8launchpadCardEnglish + L10nW8editCardEnglish + L10nW10groupsEnglish + L10nW11notifFixEnglish + L10nW12landingEnglish + L10nW13auditLaunchpadEnglish + L10nW13auditHomeLandingEnglish + L10nW13auditCrossEnglish + L10nW14signalFxEnglish + L10nW15backgroundsEnglish + L10nW15recentsEnglish + L10nW16glanceEnglish + L10nW17glanceEdgesEnglish + L10nW18radarSearchEnglish + L10nW18crashRecoveryEnglish + L10nW20orbitTypingEnglish + L10nW22auditGlassEnglish + L10nW23a11yPolishEnglish + L10nW23deviceKitEnglish + L10nW24launchpadRulesEnglish + L10nW23auditSweepEnglish + L10nW24dockDeskEnglish + L10nW25dockIconsEnglish + L10nW25launchpadHubEnglish + L10nW25shortcutFormEnglish

    /** The translation table for a language code, or null for English / unknown codes. */
    fun tableFor(code: String): Map<String, String>? = merged[code]

    private val merged: Map<String, Map<String, String>> by lazy {
        mapOf(
            "es" to L10nEs, "fr" to L10nFr, "de" to L10nDe, "pt" to L10nPt,
            "hi" to L10nHi, "zh" to L10nZh, "ja" to L10nJa, "ar" to L10nAr,
        ).mapValues { (code, base) -> base + (L10nMore[code] ?: emptyMap()) + (L10nMore2[code] ?: emptyMap()) + (L10nMore3[code] ?: emptyMap()) + (L10nLaunchpad[code] ?: emptyMap()) + (L10nState[code] ?: emptyMap()) + (L10nBackup[code] ?: emptyMap()) + (L10nInput[code] ?: emptyMap()) + (L10nStrWave2[code] ?: emptyMap()) + (L10nNfr[code] ?: emptyMap()) + (L10nWave2Fill[code] ?: emptyMap()) + (L10nW3homeLayout[code] ?: emptyMap()) + (L10nW3homeInfo[code] ?: emptyMap()) + (L10nW3state[code] ?: emptyMap()) + (L10nW4radarInput[code] ?: emptyMap()) + (L10nW4homeGaps[code] ?: emptyMap()) + (L10nW4stateGaps[code] ?: emptyMap()) + (L10nW4motion[code] ?: emptyMap()) + (L10nW5rebindInput[code] ?: emptyMap()) + (L10nW5homeRest[code] ?: emptyMap()) + (L10nW6auditPalInp[code] ?: emptyMap()) + (L10nW6auditHomeWsp[code] ?: emptyMap()) + (L10nW6auditStrExt[code] ?: emptyMap()) + (L10nW8launchpadCard[code] ?: emptyMap()) + (L10nW8editCard[code] ?: emptyMap()) + (L10nW10groups[code] ?: emptyMap()) + (L10nW11notifFix[code] ?: emptyMap()) + (L10nW12landing[code] ?: emptyMap()) + (L10nW13auditLaunchpad[code] ?: emptyMap()) + (L10nW13auditHomeLanding[code] ?: emptyMap()) + (L10nW13auditCross[code] ?: emptyMap()) + (L10nW14signalFx[code] ?: emptyMap()) + (L10nW15backgrounds[code] ?: emptyMap()) + (L10nW15recents[code] ?: emptyMap()) + (L10nW16glance[code] ?: emptyMap()) + (L10nW17glanceEdges[code] ?: emptyMap()) + (L10nW18radarSearch[code] ?: emptyMap()) + (L10nW18crashRecovery[code] ?: emptyMap()) + (L10nW20orbitTyping[code] ?: emptyMap()) + (L10nW22auditGlass[code] ?: emptyMap()) + (L10nW23a11yPolish[code] ?: emptyMap()) + (L10nW23deviceKit[code] ?: emptyMap()) + (L10nW24launchpadRules[code] ?: emptyMap()) + (L10nW23auditSweep[code] ?: emptyMap()) + (L10nW24dockDesk[code] ?: emptyMap()) + (L10nW25dockIcons[code] ?: emptyMap()) + (L10nW25launchpadHub[code] ?: emptyMap()) + (L10nW25shortcutForm[code] ?: emptyMap()) }
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
