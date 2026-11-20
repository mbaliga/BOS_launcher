package com.bos.sphere.core.quality

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Wave 23 (NFR-05, NFR-12, CFG-17, SPK-01; docs/decisions/deviceKit.md): the Device checks page uses public APIs only, adds no permission and no network. */
class DeviceKitAuditTest {
    private val files = listOf(
        "app/src/main/kotlin/com/bos/sphere/ui/DeviceChecksPane.kt", "app/src/main/kotlin/com/bos/sphere/ui/DeviceChecksStore.kt",
        "app/src/main/kotlin/com/bos/sphere/ui/DeviceProbe.kt", "app/src/main/kotlin/com/bos/sphere/ColdStart.kt",
        "core-quality/src/main/kotlin/com/bos/sphere/core/quality/DeviceKit.kt",
    )
    private fun code(p: String) = Repo.file(p).readText().lines().filterNot { it.trimStart().startsWith("*") || it.trimStart().startsWith("/*") || it.trimStart().startsWith("//") }.joinToString("\n")

    @Test fun NFR_05_theDeviceChecksSourcesUseNoHiddenApiReflectionAccessibilityServiceOrNetwork() {
        val banned = listOf("Class.forName", "getDeclaredMethod", "getDeclaredField", "setAccessible", "::class.java.getMethod", "dalvik.system", "@hide", "AccessibilityService",
            "java.net", "HttpURLConnection", "okhttp", "URL(", "Socket", "WebView", "ConnectivityManager", "INTERNET", "uses-permission", "checkSelfPermission", "requestPermissions", "getRecentTasks", "Runtime.getRuntime().exec")
        for (f in files) { val t = code(f); for (b in banned) assertFalse("$f uses $b", t.contains(b)) }
    }

    @Test fun NFR_05_theProbeUsesThePublicFrameMetricsListenerAndNothingFromTheMainThreadHandler() {
        val t = code("app/src/main/kotlin/com/bos/sphere/ui/DeviceProbe.kt")
        assertTrue(t.contains("Window.OnFrameMetricsAvailableListener") && t.contains("FrameMetrics.TOTAL_DURATION"))
        assertFalse("must pass its own handler", Regex("""addOnFrameMetricsAvailableListener\([^,]+\)""").containsMatchIn(t))
        assertFalse(t.contains("Choreographer"))
    }

    @Test fun NFR_12_thePageReadsTheProcessStartClockAndNotTheApi35StartInfo() {
        val t = code("app/src/main/kotlin/com/bos/sphere/ColdStart.kt")
        assertTrue(t.contains("Process.getStartElapsedRealtime()"))
        assertFalse(t.contains("ApplicationStartInfo")) // API 35; minSdk is 31
    }

    @Test fun NFR_20_theStateIsInATolerantPrefFileClassifiedAsExcludedAndTheReportGoesToTheClipboardOnly() {
        val store = code("app/src/main/kotlin/com/bos/sphere/ui/DeviceChecksStore.kt")
        assertTrue(store.contains("TolerantPrefs.of(") && !store.contains("getSharedPreferences("))
        val xmls = listOf("app/src/main/res/xml/backup_rules.xml", "app/src/main/res/xml/data_extraction_rules.xml").map { Repo.file(it).readText() }
        xmls.forEach { assertTrue(it.contains("""path="device_checks.xml"""")) }
        val pane = code("app/src/main/kotlin/com/bos/sphere/ui/DeviceChecksPane.kt")
        assertTrue(pane.contains("setPrimaryClip") && !pane.contains("Intent("))
    }

    @Test fun NFR_20_noPermissionWasAddedByTheDeviceKit() {
        // The permission set of the manifest at wave 23; a change here has to be a deliberate decision, not a side effect of this page.
        val manifest = Repo.file("app/src/main/AndroidManifest.xml").readText()
        val declared = Regex("""<uses-permission android:name="([^"]+)"""").findAll(manifest).map { it.groupValues[1] }.toSortedSet()
        val expected = setOf(
            "android.permission.QUERY_ALL_PACKAGES", "android.permission.REQUEST_DELETE_PACKAGES", "android.permission.RECORD_AUDIO", "android.permission.PACKAGE_USAGE_STATS", "android.permission.BIND_NOTIFICATION_LISTENER_SERVICE",
            "android.permission.health.READ_STEPS", "android.permission.health.READ_FLOORS_CLIMBED", "android.permission.health.READ_ELEVATION_GAINED", "android.permission.health.READ_DISTANCE",
            "android.permission.health.READ_ACTIVE_CALORIES_BURNED", "android.permission.ACTIVITY_RECOGNITION", "com.bos.sphere.permission.READ_SEARCH",
            "android.permission.READ_CONTACTS", "android.permission.READ_MEDIA_IMAGES", "android.permission.READ_MEDIA_VIDEO", "android.permission.READ_MEDIA_AUDIO",
            "android.permission.ACCESS_NETWORK_STATE", "android.permission.ACCESS_WIFI_STATE", "android.permission.VIBRATE", "android.permission.ACCESS_NOTIFICATION_POLICY",
            "android.permission.FOREGROUND_SERVICE", "android.permission.FOREGROUND_SERVICE_SPECIAL_USE", "android.permission.POST_NOTIFICATIONS",
        ).toSortedSet()
        assertEquals(expected, declared)
    }

    @Test fun CFG_17_theChecklistDocumentNamesEveryCheckAndSurface() {
        val doc = Repo.file("docs/device-checklist.md").readText()
        assertTrue(doc.contains("Device checks page (wave 23, deviceKit)"))
        for (c in DeviceCheck.entries) assertTrue(c.english, doc.contains(c.english))
        for (s in ProbeSurface.entries) assertTrue(s.english, doc.contains(s.english))
    }
}
