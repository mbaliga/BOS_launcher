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

    companion object {
        const val FILE = "edit_chrome"
        private const val KEY = "entries"
    }
}
