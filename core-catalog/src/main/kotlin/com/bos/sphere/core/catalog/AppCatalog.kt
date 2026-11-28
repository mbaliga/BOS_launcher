package com.bos.sphere.core.catalog

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import android.util.LruCache
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.channels.BufferOverflow

/** One launchable as the catalog sees it: the pure [record] plus what is needed to launch and badge it. */
data class CatalogApp(val record: AppRecord, val component: ComponentName, val user: UserHandle, val profile: ProfileKind)

/**
 * Apps, shortcuts, profiles and widget providers from LauncherApps / UserManager / AppWidgetManager
 * (HOM-02, HOM-05, HOM-06, HOM-07, HOM-10), with a small icon cache. The existing
 * LauncherAppsAppRepository keeps working unchanged; this is the richer view that the new home
 * features use. Nothing here uses hidden APIs or reflection.
 */
class AppCatalog(context: Context) {

    private val app = context.applicationContext
    private val launcherApps = app.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
    private val userManager = app.getSystemService(Context.USER_SERVICE) as UserManager
    private val pm = app.packageManager

    // ---- icons ---------------------------------------------------------------------------

    private val icons = object : LruCache<String, Drawable>(256) {}

    /** Icon of [a], cached by key; call [evict] when [CatalogDiff.iconKeysToEvict] says so. */
    fun icon(a: CatalogApp, activityIcon: () -> Drawable?): Drawable? =
        icons.get(a.record.key) ?: activityIcon()?.also { icons.put(a.record.key, it) }

    fun evict(keys: Set<String>) = keys.forEach { icons.remove(it) }
    // ---- profiles (HOM-06) ---------------------------------------------------------------

    fun kindOf(user: UserHandle): ProfileKind {
        if (user == Process.myUserHandle()) return ProfileKind.Personal
        if (Build.VERSION.SDK_INT >= 35) {
            val type = runCatching { launcherApps.getLauncherUserInfo(user)?.userType }.getOrNull()
            return when (type) {
                UserManager.USER_TYPE_PROFILE_PRIVATE -> ProfileKind.Private
                UserManager.USER_TYPE_PROFILE_MANAGED -> ProfileKind.Work
                else -> ProfileKind.Other
            }
        }
        // Before Android 15 the public API does not say which kind a secondary profile is; the
        // work profile is by far the common one, so it is treated as such.
        return ProfileKind.Work
    }

    fun profiles(): List<Pair<UserHandle, ProfileInfo>> = userManager.userProfiles.mapIndexed { i, u ->
        u to ProfileInfo(i, kindOf(u), quiet = runCatching { userManager.isQuietModeEnabled(u) }.getOrDefault(false))
    }

    /** Pauses or resumes a work profile (needs to be the default home app); false if the system refused. */
    fun setPaused(user: UserHandle, paused: Boolean): Boolean =
        runCatching { userManager.requestQuietModeEnabled(paused, user) }.getOrDefault(false)

    // ---- apps (HOM-05, HOM-10) -----------------------------------------------------------

    fun apps(): List<CatalogApp> = userManager.userProfiles.flatMap { user ->
        val kind = kindOf(user)
        runCatching { launcherApps.getActivityList(null, user) }.getOrDefault(emptyList()).map { info ->
            val flags = info.applicationInfo.flags
            knownLabels[info.applicationInfo.packageName] = info.label.toString()
            val rec = AppRecord(
                key = "${info.componentName.flattenToShortString()}#${user.hashCode()}",
                packageName = info.applicationInfo.packageName,
                label = info.label.toString(),
                profileId = user.hashCode(),
                suspended = flags and ApplicationInfo.FLAG_SUSPENDED != 0,
                archived = com.bos.sphere.core.data.AppFlags.archived(flags),
                versionStamp = info.firstInstallTime,
            )
            CatalogApp(rec, info.componentName, user, kind)
        }
    }.sortedBy { it.record.label.lowercase() }

    /** Wave 41: the last label seen per package, so the Timeline can name an app that has just been removed. */
    private val knownLabels = java.util.concurrent.ConcurrentHashMap<String, String>()

    /** Wave 41: install / update / removal as Timeline events. A package callback also fires for component changes, so an update counts only when the package was just updated. */
    private fun recordPackage(kind: com.bos.sphere.core.data.TimelineKind, pkg: String?) {
        if (pkg == null || pkg == app.packageName) return
        val label = runCatching { pm.getApplicationInfo(pkg, 0).loadLabel(pm).toString() }.getOrNull() ?: knownLabels[pkg].orEmpty()
        if (kind == com.bos.sphere.core.data.TimelineKind.PackageUpdated) {
            val updated = runCatching { pm.getPackageInfo(pkg, 0).lastUpdateTime }.getOrDefault(0L)
            if (System.currentTimeMillis() - updated > 60_000L) return
        }
        com.bos.sphere.core.data.TimelineEvents.record(kind, pkg, label.ifBlank { pkg })
    }

    /** Emits Unit whenever the inventory may have changed; the collector re-reads [apps]. Bursts collapse to one emission. */
    val changes: Flow<Unit> = callbackFlow {
        val cb = object : LauncherApps.Callback() {
            override fun onPackageAdded(packageName: String?, user: UserHandle?) { recordPackage(com.bos.sphere.core.data.TimelineKind.PackageInstalled, packageName); trySend(Unit) }
            override fun onPackageRemoved(packageName: String?, user: UserHandle?) {
                if (packageName != null && packageName != app.packageName) com.bos.sphere.core.data.TimelineEvents.record(com.bos.sphere.core.data.TimelineKind.PackageRemoved, packageName, knownLabels[packageName] ?: packageName)
                trySend(Unit)
            }
            override fun onPackageChanged(packageName: String?, user: UserHandle?) { recordPackage(com.bos.sphere.core.data.TimelineKind.PackageUpdated, packageName); trySend(Unit) }
            override fun onPackagesAvailable(packageNames: Array<out String>?, user: UserHandle?, replacing: Boolean) { trySend(Unit) }
            override fun onPackagesUnavailable(packageNames: Array<out String>?, user: UserHandle?, replacing: Boolean) { trySend(Unit) }
            override fun onPackagesSuspended(packageNames: Array<out String>?, user: UserHandle?) { trySend(Unit) }
            override fun onPackagesUnsuspended(packageNames: Array<out String>?, user: UserHandle?) { trySend(Unit) }
        }
        com.bos.sphere.core.data.TimelineEvents.init(app)
        launcherApps.registerCallback(cb)
        trySend(Unit)
        awaitClose { launcherApps.unregisterCallback(cb) }
    }.buffer(1, BufferOverflow.DROP_OLDEST)

    // ---- shortcuts (HOM-02) --------------------------------------------------------------

    /** Dynamic, manifest and pinned shortcuts of a package; empty when Liftoff is not the home app. */
    fun shortcuts(packageName: String, user: UserHandle): List<ShortcutInfo> = runCatching {
        val q = LauncherApps.ShortcutQuery().setPackage(packageName).setQueryFlags(
            LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED,
        )
        launcherApps.getShortcuts(q, user).orEmpty()
    }.getOrDefault(emptyList())

    fun startShortcut(s: ShortcutInfo): Boolean = runCatching { launcherApps.startShortcut(s, null, null); true }.getOrDefault(false)

}
