package com.bos.sphere.core.data

import android.content.ComponentName
import android.content.Context
import android.content.pm.LauncherApps
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Source of truth for the launchable-app inventory.
 *
 * Exposes a [apps] `Flow` that emits the current list and re-emits whenever apps are
 * installed / removed / updated or profiles are added — wired through [LauncherApps.Callback]
 * so the surface stays live without polling. No root, no special permission: `LauncherApps`
 * is available to the default home app out of the box.
 */
interface AppRepository {
    val apps: Flow<List<AppEntry>>
    fun launch(entry: AppEntry)
}

class LauncherAppsAppRepository(context: Context) : AppRepository {

    private val appContext = context.applicationContext
    private val launcherApps =
        appContext.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
    private val userManager =
        appContext.getSystemService(Context.USER_SERVICE) as UserManager

    override val apps: Flow<List<AppEntry>> = callbackFlow {
        fun emitSnapshot() {
            trySend(loadAll())
        }

        val callback = object : LauncherApps.Callback() {
            override fun onPackageAdded(packageName: String?, user: UserHandle?) = emitSnapshot()
            override fun onPackageRemoved(packageName: String?, user: UserHandle?) = emitSnapshot()
            override fun onPackageChanged(packageName: String?, user: UserHandle?) = emitSnapshot()
            override fun onPackagesAvailable(
                packageNames: Array<out String>?,
                user: UserHandle?,
                replacing: Boolean,
            ) = emitSnapshot()

            override fun onPackagesUnavailable(
                packageNames: Array<out String>?,
                user: UserHandle?,
                replacing: Boolean,
            ) = emitSnapshot()
        }

        launcherApps.registerCallback(callback)
        emitSnapshot() // initial inventory
        awaitClose { launcherApps.unregisterCallback(callback) }
    }

    private fun loadAll(): List<AppEntry> =
        userManager.userProfiles.flatMap { user ->
            launcherApps.getActivityList(null, user).map { info ->
                AppEntry(
                    packageName = info.applicationInfo.packageName,
                    componentName = info.componentName,
                    label = info.label.toString(),
                    user = user,
                    iconLoader = {
                        // Density 0 → let the system pick; LauncherApps badges for the profile.
                        runCatching { info.getBadgedIcon(0) }.getOrNull()
                    },
                )
            }
        }.sortedBy { it.label.lowercase() }

    override fun launch(entry: AppEntry) {
        launcherApps.startMainActivity(
            entry.componentName,
            entry.user,
            /* sourceBounds = */ null,
            /* opts = */ null,
        )
    }

    companion object {
        /** Convenience for callers that only have a [ComponentName]/package and the current user. */
        fun currentUser(): UserHandle = Process.myUserHandle()
    }
}
