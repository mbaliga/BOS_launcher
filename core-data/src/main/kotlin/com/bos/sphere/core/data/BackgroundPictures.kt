package com.bos.sphere.core.data

import java.io.File

/**
 * NFR-18: the private background picture is written under a fixed name pattern to the no-backup directory, which the
 * platform never backs up or transfers. Builds before that wrote the picture to the backed-up files directory (and an
 * exclusion rule cannot match a name with a timestamp in it), so [migrate] moves such a file across at start and points the
 * preference at the new path. A picture already in place is left alone.
 */
object BackgroundPictures {
    const val PREFIX = "background_"
    const val SUFFIX = ".img"

    fun fileName(timeMillis: Long) = "$PREFIX$timeMillis$SUFFIX"

    fun isPictureName(name: String) = name.startsWith(PREFIX) && name.endsWith(SUFFIX)

    /** Returns the number of files moved. */
    fun migrate(filesDir: File, noBackupDir: File, prefs: LauncherPrefsStore): Int {
        val legacy = filesDir.listFiles { f -> f.isFile && isPictureName(f.name) }.orEmpty()
        if (legacy.isEmpty()) return 0
        noBackupDir.mkdirs()
        val current = prefs.prefs.value.backgroundPicturePath
        var moved = 0
        for (f in legacy) {
            val dest = File(noBackupDir, f.name)
            val ok = runCatching { f.copyTo(dest, overwrite = true); f.delete() }.getOrDefault(false)
            if (!ok) continue
            moved++
            if (current == f.absolutePath) prefs.update { it.copy(backgroundPicturePath = dest.absolutePath) }
        }
        return moved
    }
}
