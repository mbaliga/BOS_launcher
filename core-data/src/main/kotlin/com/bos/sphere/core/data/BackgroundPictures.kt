package com.bos.sphere.core.data

import java.io.File

/**
 * Wave 15: the custom background picture was removed with the old background gallery (docs/decisions/backgroundsReset.md).
 * Pictures an earlier build copied into the app's private storage are deleted once at start so nothing of the removed
 * option is left on the device. Run it off the main thread.
 */
object BackgroundPictures {
    const val PREFIX = "background_"
    const val SUFFIX = ".img"

    fun isPictureName(name: String) = name.startsWith(PREFIX) && name.endsWith(SUFFIX)

    /** Deletes every leftover picture in [dirs]; returns how many files were removed. */
    fun purge(vararg dirs: File): Int = dirs.sumOf { d -> d.listFiles { f -> f.isFile && isPictureName(f.name) }.orEmpty().count { runCatching { it.delete() }.getOrDefault(false) } }
}
