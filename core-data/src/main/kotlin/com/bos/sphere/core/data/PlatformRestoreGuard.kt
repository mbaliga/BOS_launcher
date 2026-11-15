package com.bos.sphere.core.data

import java.io.File
import java.util.UUID

/**
 * NFR-18: widget ids and the picked background picture belong to one device, but they live in files that also hold
 * setup (`launcher_prefs`, `shortcuts`), so the platform's backup and device transfer carry them. A file-level exclusion
 * cannot separate them, so a restore is detected and they are removed afterwards.
 *
 * Detection uses two copies of one random id: [bound] sits in the backed-up files directory, [local] in the no-backup
 * directory (never restored). After a platform restore [bound] comes back and [local] does not, so they differ. Where
 * [bound] does not exist (a fresh install, or an install updated from a build before this guard) nothing is removed: the
 * ids are adopted, so an ordinary update never loses a widget. A backup taken by a build before this guard cannot be
 * recognised as a restore (stated limit; the state-file import path never carried these values).
 */
object PlatformRestoreGuard {
    data class Result(val restored: Boolean, val widgetsCleared: List<PendingWidget>, val backgroundCleared: Boolean)

    fun check(
        bound: File,
        local: File,
        shortcuts: ShortcutStore,
        prefs: LauncherPrefsStore,
        extras: StateExtrasStore,
        deleteFile: (String) -> Unit = { runCatching { File(it).delete() } },
    ): Result {
        val boundId = runCatching { bound.readText().trim() }.getOrNull()?.takeIf { it.isNotEmpty() }
        val localId = runCatching { local.readText().trim() }.getOrNull()?.takeIf { it.isNotEmpty() }
        val restored = boundId != null && boundId != localId
        var result = Result(false, emptyList(), false)
        if (restored) result = clear(shortcuts, prefs, extras, deleteFile).copy(restored = true)
        if (boundId == null || restored || localId == null) {
            val fresh = UUID.randomUUID().toString()
            runCatching { bound.parentFile?.mkdirs(); bound.writeText(fresh) }
            runCatching { local.parentFile?.mkdirs(); local.writeText(fresh) }
        }
        return result
    }

    private fun clear(shortcuts: ShortcutStore, prefs: LauncherPrefsStore, extras: StateExtrasStore, deleteFile: (String) -> Unit): Result {
        val cleared = ArrayList<PendingWidget>()
        shortcuts.corners.value.forEach { (c, s) ->
            if (s is CornerSlot.Widget) { cleared += PendingWidget("corner.${c.name}", s.label, ""); shortcuts.setCorner(c, CornerSlot.Empty) }
        }
        shortcuts.explicitLandscapeCorners().forEach { (c, s) ->
            if (s is CornerSlot.Widget) { cleared += PendingWidget("lcorner.${c.name}", s.label, ""); shortcuts.setLandscapeCorner(c, CornerSlot.Empty) }
        }
        val cur = prefs.prefs.value
        val d = LauncherPrefs()
        if (cur.leftWidgetId != d.leftWidgetId) {
            if (cur.leftPage == LeftPage.Widget) cleared += PendingWidget("leftPage", "", "")
            prefs.update { it.copy(leftWidgetId = d.leftWidgetId) }
        }
        val hadPicture = cur.backgroundPicturePath.isNotEmpty()
        if (hadPicture) {
            deleteFile(cur.backgroundPicturePath)
            prefs.update {
                it.copy(
                    backgroundPicturePath = "",
                    backgroundKind = if (it.backgroundKind == BackgroundKind.Picture) d.backgroundKind else it.backgroundKind,
                    backgroundPreset = if (it.backgroundPreset == "photo") d.backgroundPreset else it.backgroundPreset,
                )
            }
        }
        if (cleared.isNotEmpty()) {
            val e = extras.read()
            extras.write(e.copy(widgets = e.widgets.filterNot { w -> cleared.any { it.slot == w.slot } } + cleared))
        }
        return Result(true, cleared, hadPicture)
    }
}
