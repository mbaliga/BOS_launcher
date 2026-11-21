package com.bos.sphere.core.data

/**
 * INP-15: an app shortcut assigned to a corner, stored as a [CornerItem.App] whose key is
 * `shortcut:<package>/<shortcutId>#<userHash>|<label>`. The label travels with the key so a corner can name the
 * shortcut without asking the system, and so it still reads right when the app is gone. Pure, so the format is tested.
 */
object CornerShortcuts {
    data class Parsed(val packageName: String, val shortcutId: String, val userHash: Int, val label: String)

    fun make(packageName: String, shortcutId: String, userHash: Int, label: String): String =
        "${CornerSets.SHORTCUT_PREFIX}$packageName/$shortcutId#$userHash|${label.replace('|', ' ').replace('\n', ' ').trim()}"

    fun isShortcut(key: String) = key.startsWith(CornerSets.SHORTCUT_PREFIX)

    fun parse(key: String): Parsed? {
        if (!isShortcut(key)) return null
        val body = key.removePrefix(CornerSets.SHORTCUT_PREFIX)
        val label = body.substringAfter('|', "")
        val head = body.substringBefore('|')
        val hashAt = head.lastIndexOf('#')
        if (hashAt <= 0) return null
        val userHash = head.substring(hashAt + 1).toIntOrNull() ?: return null
        val pkgAndId = head.substring(0, hashAt)
        val slash = pkgAndId.indexOf('/')
        if (slash <= 0 || slash == pkgAndId.lastIndex) return null
        return Parsed(pkgAndId.substring(0, slash), pkgAndId.substring(slash + 1), userHash, label)
    }
}
