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
    fun clearIcons() = icons.evictAll()

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
            val rec = AppRecord(
                key = "${info.componentName.flattenToShortString()}#${user.hashCode()}",
                packageName = info.applicationInfo.packageName,
                label = info.label.toString(),
                profileId = user.hashCode(),
                suspended = flags and ApplicationInfo.FLAG_SUSPENDED != 0,
                versionStamp = info.firstInstallTime,
            )
            CatalogApp(rec, info.componentName, user, kind)
        }
    }.sortedBy { it.record.label.lowercase() }

    /** Emits Unit whenever the inventory may have changed; the collector re-reads [apps]. Bursts collapse to one emission. */
    val changes: Flow<Unit> = callbackFlow {
        val cb = object : LauncherApps.Callback() {
            override fun onPackageAdded(packageName: String?, user: UserHandle?) { trySend(Unit) }
            override fun onPackageRemoved(packageName: String?, user: UserHandle?) { trySend(Unit) }
            override fun onPackageChanged(packageName: String?, user: UserHandle?) { trySend(Unit) }
            override fun onPackagesAvailable(packageNames: Array<out String>?, user: UserHandle?, replacing: Boolean) { trySend(Unit) }
            override fun onPackagesUnavailable(packageNames: Array<out String>?, user: UserHandle?, replacing: Boolean) { trySend(Unit) }
            override fun onPackagesSuspended(packageNames: Array<out String>?, user: UserHandle?) { trySend(Unit) }
            override fun onPackagesUnsuspended(packageNames: Array<out String>?, user: UserHandle?) { trySend(Unit) }
        }
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

    // ---- widget providers (HOM-07) -------------------------------------------------------

    fun widgetProviders(): List<WidgetChoiceRef> {
        val wm = AppWidgetManager.getInstance(app)
        return wm.installedProviders.mapNotNull { p ->
            runCatching {
                val appLabel = pm.getApplicationLabel(pm.getApplicationInfo(p.provider.packageName, 0)).toString()
                WidgetChoiceRef(p.provider.flattenToString(), appLabel, p.loadLabel(pm), p.minWidth, p.minHeight)
            }.getOrNull()
        }
    }

    fun installedProviderIds(): Set<String> = AppWidgetManager.getInstance(app).installedProviders.map { it.provider.flattenToString() }.toSet()
}
