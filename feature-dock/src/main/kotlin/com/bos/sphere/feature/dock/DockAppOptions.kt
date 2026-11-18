package com.bos.sphere.feature.dock

import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.net.Uri
import android.provider.Settings
import com.bos.sphere.core.data.AppEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** W25: what an app icon's long-press menu offers beyond the dock's own entries: the app's shortcuts, then the open choices. */
object DockAppOptions {
    fun infoIntent(packageName: String): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** Off the main thread: the shortcut query is a binder call. Empty for a group, a shortcut item or when Liftoff is not the home app. */
    suspend fun entries(context: Context, launcher: AppLauncher, manager: WorkspaceManager, app: AppEntry?): List<DockMenuEntry> {
        if (app == null) return emptyList()
        val shortcuts = withContext(Dispatchers.IO) {
            runCatching {
                val la = context.getSystemService(LauncherApps::class.java)
                val q = LauncherApps.ShortcutQuery().setPackage(app.packageName).setQueryFlags(
                    LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED,
                )
                la.getShortcuts(q, app.user).orEmpty().take(4).map { s ->
                    DockMenuEntry((s.shortLabel ?: s.id).toString()) { runCatching { la.startShortcut(s, null, null) } }
                }
            }.getOrDefault(emptyList())
        }
        // The open choices (open on a display, beside); the pin entry is the menu's own, so it is dropped here.
        val pin = com.bos.sphere.core.design.L10n.tr("Remove from dock")
        val pinAdd = com.bos.sphere.core.design.L10n.tr("Add to dock")
        val open = runCatching { AppActionList.build(launcher, manager, app).filter { it.label != pin && it.label != pinAdd }.map { DockMenuEntry(it.label, it.run) } }.getOrDefault(emptyList())
        return shortcuts + open
    }
}
