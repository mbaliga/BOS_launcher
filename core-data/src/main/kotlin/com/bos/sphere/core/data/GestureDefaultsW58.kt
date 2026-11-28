package com.bos.sphere.core.data

import android.content.SharedPreferences

/**
 * Wave 58 (defects58, CFG-62): swipe left is no longer bound to Radar by default ([DefaultGestures]). An install from before wave 58 that never chose a
 * swipe-left binding had Radar through the default; once per install:
 *  - when its home lets swipe left fire (a vertical or Continuous-less surface where the swipe is not the scroll), Radar is written down explicitly so
 *    nothing it relies on changes;
 *  - when swipe left was the dead default (the home scrolls sideways), nothing is written and the new default (unbound) applies.
 * A fresh install (no settings stored yet) only gets the marker. An explicit choice (any stored `gesture.SwipeLeft`, "nothing" included) is never touched.
 */
object GestureDefaultsW58 {
    const val MARKER = "gestureDefaultsW58"
    private const val KEY = "gesture.SwipeLeft"

    /** [existing]: the install had settings before this build (launcher_prefs holds `style` or `pCols`). Returns true when Radar was kept. */
    fun migrate(shortcuts: SharedPreferences, prefs: LauncherPrefs, existing: Boolean): Boolean {
        if (shortcuts.getBoolean(MARKER, false)) return false
        val keep = existing && !shortcuts.contains(KEY) && HomeGesture.SwipeLeft.unavailableReason(prefs.surface(landscape = false)) == null
        shortcuts.edit().also { e ->
            if (keep) e.putString(KEY, ShortcutCodec.encodeItem(CornerItem.Action(CornerActionId.Search)))
            e.putBoolean(MARKER, true)
        }.apply()
        return keep
    }
}
