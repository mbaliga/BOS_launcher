package com.bos.sphere.feature.grid

/**
 * CFG-05: "every setting is also a Radar result" includes landing on the setting, not only on its category. Radar's
 * settings source calls [request] with the row's index before it opens Ground Control; the settings screen takes it once
 * on opening, scrolls to the row and puts the cursor on it.
 */
object SettingsFocus {
    @Volatile private var pending: Int = NONE
    const val NONE = -1

    /** Remembers the row to land on in the category that opens next. */
    fun request(itemIndex: Int) { pending = itemIndex }

    /** The requested row, once; [NONE] when nothing was asked or it was already taken. */
    fun take(): Int = pending.also { pending = NONE }

    /** Drops a request that never opened Ground Control (the user closed Radar). */
    fun clear() { pending = NONE }
}
