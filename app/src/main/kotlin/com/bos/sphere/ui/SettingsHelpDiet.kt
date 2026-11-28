package com.bos.sphere.ui

import com.bos.sphere.feature.grid.SettingItem

/**
 * Wave 58 (settingsPolish), CFG-65: the help diet. The grey line under a row's title is its current state ("Dark · Rounded", "4 s", "Off") or a
 * few words ("Sends typing to Google"); longer help lives in the header line of the page the row opens, in a section's note, or nowhere when the
 * drawn cards already say it. A test reads every row of the real settings in several configurations and fails on a grey line of more than
 * [MAX_WORDS] words unless the row is in [ALLOWED] with its reason (at most about 5 percent of the rows).
 */
object SettingsHelpDiet {
    const val MAX_WORDS = 6

    /** The grey line a row draws: the reason while it is off, otherwise what the row renderers show (see OneUiRowFrame and its callers). */
    fun summary(i: SettingItem): String = i.meta.disabledReason ?: when (i) {
        is SettingItem.Toggle -> i.hint
        is SettingItem.Slider -> i.hint
        is SettingItem.Choice<*> -> i.hint
        is SettingItem.Stepper -> i.hint
        is SettingItem.Action -> listOfNotNull(i.value.takeIf { it.isNotEmpty() }, i.hint?.takeIf { it.isNotEmpty() }).joinToString(" · ")
        is SettingItem.Pane -> i.value
    }.orEmpty()

    /** Words of a grey line; the "·" separators of a state line are not words. */
    fun words(s: String): Int = s.split(Regex("\\s+")).count { w -> w.isNotEmpty() && w.any { it.isLetterOrDigit() } }

    /** (tab title, row title) to why its grey line may be longer. Kept short on purpose. */
    val ALLOWED: Map<Pair<String, String>, String> = mapOf()
}
