package com.bos.sphere.core.quality

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Wave 58 (defects58, CFG-64): every permission a manifest declares has a live consumer in the production sources, named here with a string that only
 * the consuming code holds. A declaration without one (the Health Connect reads, ACTIVITY_RECOGNITION and the never-requested READ_MEDIA_* before wave 58)
 * fails this test; adding a permission means adding its consumer to this table, deliberately.
 */
class PermissionConsumersW58AuditTest {
    private class Consumer(val file: String, val needle: String)

    private val consumers: Map<String, Consumer> = mapOf(
        "android.permission.INTERNET" to Consumer("core-data/src/main/kotlin/com/bos/sphere/core/data/WebSuggestHttp.kt", "openConnection()"),
        "android.permission.QUERY_ALL_PACKAGES" to Consumer("core-data/src/main/kotlin/com/bos/sphere/core/data/ActionRunner.kt", "queryIntentActivities"),
        "android.permission.REQUEST_DELETE_PACKAGES" to Consumer("core-data/src/main/kotlin/com/bos/sphere/core/data/AppUninstall.kt", "ACTION_DELETE"),
        "android.permission.RECORD_AUDIO" to Consumer("feature-grid/src/main/kotlin/com/bos/sphere/feature/grid/VoiceEntry.kt", "RECORD_AUDIO"),
        "android.permission.PACKAGE_USAGE_STATS" to Consumer("core-signals/src/main/kotlin/com/bos/sphere/core/signals/Recents.kt", "UsageStatsManager"),
        "android.permission.BIND_NOTIFICATION_LISTENER_SERVICE" to Consumer("core-data/src/main/kotlin/com/bos/sphere/core/data/NotificationManager.kt", "NotificationListenerService"),
        "com.bos.sphere.permission.READ_SEARCH" to Consumer("feature-search/src/main/kotlin/com/bos/sphere/feature/search/palette/ThirdPartyProviders.kt", "READ_SEARCH"),
        "android.permission.READ_CONTACTS" to Consumer("core-data/src/main/kotlin/com/bos/sphere/core/data/Contacts.kt", "READ_CONTACTS"),
        "android.permission.ACCESS_NETWORK_STATE" to Consumer("core-signals/src/main/kotlin/com/bos/sphere/core/signals/RadarSignal.kt", "getNetworkCapabilities"),
        "android.permission.ACCESS_WIFI_STATE" to Consumer("feature-launchpad/src/main/kotlin/com/bos/sphere/ui/ConnectivityState.kt", "WifiManager"),
        "android.permission.VIBRATE" to Consumer("core-data/src/main/kotlin/com/bos/sphere/core/data/MotionGestureSensors.kt", "Vibrator"),
        "android.permission.ACCESS_NOTIFICATION_POLICY" to Consumer("core-data/src/main/kotlin/com/bos/sphere/core/data/ActionRunner.kt", "isNotificationPolicyAccessGranted"),
        "android.permission.FOREGROUND_SERVICE" to Consumer("app/src/main/kotlin/com/bos/sphere/MotionGestureService.kt", "startForeground"),
        "android.permission.FOREGROUND_SERVICE_SPECIAL_USE" to Consumer("app/src/main/kotlin/com/bos/sphere/MotionGestureService.kt", "FOREGROUND_SERVICE_TYPE_SPECIAL_USE"),
        "android.permission.POST_NOTIFICATIONS" to Consumer("app/src/main/kotlin/com/bos/sphere/HomeScreen.kt", "POST_NOTIFICATIONS"),
        "android.permission.RECEIVE_BOOT_COMPLETED" to Consumer("feature-dock/src/main/kotlin/com/bos/sphere/feature/dock/Automation.kt", "BOOT_COMPLETED"),
    )

    private fun declared(): Set<String> = Repo.root.listFiles { f -> f.isDirectory }.orEmpty()
        .map { java.io.File(it, "src/main/AndroidManifest.xml") }.filter { it.isFile }
        .flatMap { m -> Regex("""<uses-permission android:name="([^"]+)"""").findAll(m.readText()).map { it.groupValues[1] }.toList() }.toSortedSet()

    @Test fun CFG_64_everyDeclaredPermissionHasALiveConsumer() {
        val d = declared()
        assertEquals("declared without a consumer in this table: ${d - consumers.keys}", emptySet<String>(), d - consumers.keys)
        for (p in d) {
            val c = consumers.getValue(p)
            val f = Repo.file(c.file)
            assertTrue("$p: ${c.file} exists", f.isFile)
            assertTrue("$p: ${c.file} still uses it (${c.needle})", f.readText().contains(c.needle))
        }
    }

    @Test fun CFG_64_theUnusedHealthActivityAndMediaPermissionsAreGone() {
        val d = declared()
        for (p in listOf("android.permission.ACTIVITY_RECOGNITION", "android.permission.READ_MEDIA_IMAGES", "android.permission.READ_MEDIA_VIDEO", "android.permission.READ_MEDIA_AUDIO"))
            assertFalse("$p is declared but nothing asks for it", p in d)
        assertTrue("no Health Connect read is declared", d.none { it.startsWith("android.permission.health.") })
        val manifest = Repo.file("app/src/main/AndroidManifest.xml").readText()
        assertFalse("no Health Connect rationale entry", manifest.contains("ACTION_SHOW_PERMISSIONS_RATIONALE"))
        assertTrue("no source reads media permissions any more", Repo.mainSources().none { it.readText().contains("READ_MEDIA_") })
    }
}
