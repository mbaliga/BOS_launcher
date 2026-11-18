package com.bos.sphere.core.data

/**
 * W28 (arcShortcuts): the app drawer shortcuts, pure rules. Up to [MAX] apps the user picks; they are either always on the home next
 * to the four-squares hotspot or only shown with the alphabet wheel; with the setting off nothing is shown anywhere.
 */
object ArcShortcuts {
    const val MAX = 4
    private const val SEP = "\n"

    /** The list kept to [MAX] distinct non-blank keys, in order. */
    fun clamp(keys: List<String>): List<String> = keys.filter { it.isNotBlank() }.distinct().take(MAX)

    fun add(keys: List<String>, key: String): List<String> = if (key in keys || keys.size >= MAX) clamp(keys) else clamp(keys + key)
    fun remove(keys: List<String>, key: String): List<String> = clamp(keys - key)

    /** Moves [key] by [delta] places (clamped to the ends). */
    fun move(keys: List<String>, key: String, delta: Int): List<String> {
        val i = keys.indexOf(key)
        if (i < 0) return clamp(keys)
        val j = (i + delta).coerceIn(0, keys.lastIndex)
        val out = keys.toMutableList()
        out.removeAt(i); out.add(j, key)
        return clamp(out)
    }

    fun encode(keys: List<String>): String = clamp(keys).joinToString(SEP)
    fun decode(raw: String?): List<String> = clamp(raw?.split(SEP).orEmpty())

    /** The shortcuts drawn at rest on the home (the wheel closed). */
    fun visibleAtRest(on: Boolean, shown: ShortcutsShown, count: Int): Boolean = on && count > 0 && shown == ShortcutsShown.Always

    /** The shortcuts drawn as the bottom row of the open wheel. */
    fun visibleWithWheel(on: Boolean, count: Int): Boolean = on && count > 0
}
