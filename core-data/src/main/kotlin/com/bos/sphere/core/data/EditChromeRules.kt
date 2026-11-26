package com.bos.sphere.core.data

import android.content.SharedPreferences

/**
 * W45 (editChrome, HOM-12): the one-line hint under the edit bar is for the first [MAX_SHOWS] entries into edit mode only; after that it never returns.
 * [shownOn] is the pure rule: [entriesBefore] is how many times edit mode was entered before this entry.
 */
object EditChromeHint {
    const val MAX_SHOWS = 3
    /** Fade-out of the hint when the user does not touch the screen (milliseconds). */
    const val FADE_AFTER_MS = 4000L

    fun shownOn(entriesBefore: Int): Boolean = entriesBefore in 0 until MAX_SHOWS

    /** The counter after an entry. Capped so the file never grows a meaningless number. */
    fun next(entriesBefore: Int): Int = (entriesBefore.coerceAtLeast(0) + 1).coerceAtMost(MAX_SHOWS + 1)
}

/**
 * Wave 58 (defects58, CFG-63): first-run hints where the user can act on them. Glance (the home a fresh install lands on, Pages Off) shows
 * "Long press for options" once, after onboarding, while nothing is being edited; the pages keep "Long press to add apps" on an empty first page.
 */
object FirstRunHints {
    const val GLANCE_HINT_MS = 6000L
    fun showsGlanceHint(landing: HomeShown, shownBefore: Boolean, onboardingDone: Boolean, editing: Boolean): Boolean =
        landing == HomeShown.Glance && !shownBefore && onboardingDone && !editing
}

/**
 * The counter of the hint, in its own preference file (device-local UI state: it describes what this person has already been told on this device, so the
 * file is excluded from backup and transfer, see [StateSchema.BACKUP_EXCLUDED_PREF_FILES]). Opened with [TolerantPrefs]. Written when edit mode is entered
 * (an input handler), never during composition.
 */
class EditChromeStore(private val sp: SharedPreferences) {
    fun entries(): Int = runCatching { sp.getInt(KEY, 0) }.getOrDefault(0)

    /** Records one entry into edit mode and says whether the hint shows for it. */
    fun registerEntry(): Boolean {
        val before = entries()
        runCatching { sp.edit().putInt(KEY, EditChromeHint.next(before)).apply() }
        return EditChromeHint.shownOn(before)
    }

    /** Wave 58 (CFG-63): whether Glance has shown its one "Long press for options" hint on this device. */
    fun glanceHintShown(): Boolean = runCatching { sp.getBoolean(GLANCE_HINT, false) }.getOrDefault(false)
    fun markGlanceHintShown() { runCatching { sp.edit().putBoolean(GLANCE_HINT, true).apply() } }

    companion object {
        const val FILE = "edit_chrome"
        private const val KEY = "entries"
        private const val GLANCE_HINT = "glanceHint"
    }
}
