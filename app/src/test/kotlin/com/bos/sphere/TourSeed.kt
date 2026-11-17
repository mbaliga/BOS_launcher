package com.bos.sphere

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import com.bos.sphere.core.data.AssignKind
import com.bos.sphere.core.data.GroupsSettings
import com.bos.sphere.core.data.HubNotification
import com.bos.sphere.core.data.LauncherStores
import com.bos.sphere.core.data.MediaInfo
import com.bos.sphere.core.data.MediaNow
import com.bos.sphere.core.data.NotificationProvider
import com.bos.sphere.core.data.PendingIntentLauncher
import com.bos.sphere.core.data.PendingIntentSender

/** The seeded busy state of the tour (wave 22): many apps, history, a media session, notifications with real PendingIntents, a group, a work-profile app. */
internal object TourSeed {
    val sent = mutableListOf<PendingIntent>()

    /** 500 numbered apps plus the named ones the tests look for. */
    fun apps(count: Int = 500) {
        AppSmokeHarness.seedApps("Alpha", "Bravo", "Camera", "Delta", *Array(count) { "Tour %03d".format(it + 1) })
    }

    /** A launchable activity of another profile (user 10), what a work profile app is to the launcher. Best effort: the platform constructors are hidden. */
    fun workApp(label: String = "Workchat") {
        val shadow = org.robolectric.Shadows.shadowOf(AppSmokeHarness.app.getSystemService(android.content.Context.LAUNCHER_APPS_SERVICE) as android.content.pm.LauncherApps)
        val work = android.os.UserHandle::class.java.getDeclaredMethod("of", Int::class.javaPrimitiveType).invoke(null, 10) as android.os.UserHandle
        val pkg = AppSmokeHarness.packageOf(label)
        val ai = android.content.pm.ApplicationInfo().apply { packageName = pkg; name = label; sourceDir = "/data/app/$pkg.apk"; nonLocalizedLabel = label }
        val info = android.content.pm.ActivityInfo().apply { packageName = pkg; name = "$pkg.Main"; applicationInfo = ai; nonLocalizedLabel = label; exported = true }
        val states = Class.forName("android.content.pm.IncrementalStatesInfo").getDeclaredConstructor().newInstance()
        val internalCls = Class.forName("android.content.pm.LauncherActivityInfoInternal")
        val internal = internalCls.getDeclaredConstructor(android.content.pm.ActivityInfo::class.java, states.javaClass, android.os.UserHandle::class.java).newInstance(info, states, work)
        val ctor = android.content.pm.LauncherActivityInfo::class.java.getDeclaredConstructor(android.content.Context::class.java, internalCls)
        ctor.isAccessible = true
        shadow.addActivity(work, ctor.newInstance(AppSmokeHarness.app, internal))
    }

    fun history(vararg labels: String) {
        val h = LauncherStores.get(AppSmokeHarness.app).history
        labels.forEachIndexed { i, l -> h.record(AppSmokeHarness.appKey(l), System.currentTimeMillis() - (labels.size - i) * 60_000L) }
    }

    val track = MediaInfo("Someday Out of The Blue", "Artist", "pkg.music", playing = true, canPrev = true, canNext = true)
    fun media() = MediaNow.publishForTest(track)

    fun notifications(vararg pkgLabels: Pair<String, String>) {
        val app = AppSmokeHarness.app
        val list = pkgLabels.mapIndexed { i, (label, title) ->
            HubNotification(
                id = "n$i", key = "k$i", source = label, title = title, summary = "body of $title", timestamp = System.currentTimeMillis() - i * 1000L,
                packageName = AppSmokeHarness.packageOf(label), appLabel = label,
                contentIntent = PendingIntent.getActivity(app, i, Intent("tour.open.$i").setPackage(app.packageName), PendingIntent.FLAG_IMMUTABLE),
            )
        }
        // the notification-access grant the system keeps in Settings.Secure (the Launchpad shows the grant card instead of rows without it)
        android.provider.Settings.Secure.putString(app.contentResolver, "enabled_notification_listeners", android.content.ComponentName(app.packageName, com.bos.sphere.core.data.SphereNotificationListener::class.java.name).flattenToString())
        NotificationProvider.publishForTest(list)
    }

    /** Replaces the real sender with one that records and sends nothing. */
    fun recordSends() {
        sent.clear()
        PendingIntentLauncher.sender = PendingIntentSender { pi, _, _, _ -> sent += pi }
    }

    /** One group holding every notification of [label]'s package. */
    fun group(name: String, label: String) {
        GroupsSettings.init(AppSmokeHarness.app)
        GroupsSettings.addGroup(name)
        val id = GroupsSettings.snapshot().groups.first { it.name == name }.id
        GroupsSettings.assign(AssignKind.App, AppSmokeHarness.packageOf(label), id)
    }
}
