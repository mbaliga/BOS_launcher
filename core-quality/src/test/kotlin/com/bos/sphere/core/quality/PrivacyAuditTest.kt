package com.bos.sphere.core.quality

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrivacyAuditTest {
    private val sdkMarkers = listOf(
        "com.google.firebase", "com.crashlytics", "io.sentry", "com.bugsnag", "org.acra", "com.google.android.gms",
        "com.appsflyer", "com.amplitude", "com.mixpanel", "com.flurry", "com.segment", "com.datadog", "com.newrelic",
        "com.facebook.appevents", "com.adjust.sdk", "io.branch", "com.microsoft.appcenter", "com.posthog",
    )

    @Test fun NFR_17_noAnalyticsOrCrashUploadSdkInSources() {
        val hits = Repo.mainSources().flatMap { f -> f.readLines().filter { it.trimStart().startsWith("import ") || it.contains("package ") }
            .filter { l -> sdkMarkers.any { l.contains(it) } }.map { "${f.name}: $it" } }
        assertTrue("analytics SDK imports: $hits", hits.isEmpty())
    }

    @Test fun NFR_17_noAnalyticsDependencyInBuildFiles() {
        val builds = Repo.root.walkTopDown().maxDepth(3).filter { it.name == "build.gradle.kts" || it.name == "libs.versions.toml" }
            .filter { !it.path.contains("/benchmark/") }.joinToString("\n") { it.readText() }
        sdkMarkers.forEach { assertFalse(it, builds.contains(it)) }
        assertFalse(builds.contains("crashlytics"))
    }

    @Test fun NFR_17_manifestHasNoAnalyticsOrCrashServices() {
        val m = Repo.file("app/src/main/AndroidManifest.xml").readText()
        sdkMarkers.forEach { assertFalse(it, m.contains(it)) }
        assertFalse(m.contains("ACCESSIBILITY_SERVICE"))
        assertFalse(m.contains("SYSTEM_ALERT_WINDOW"))
    }

    @Test fun NFR_17_noSourceFileOpensAnHttpConnection() {
        // Wave 38: the weather client went with the clock strip; nothing used the network. Wave 43 (radarWeb): exactly ONE file may, the Radar suggestion client
        // (HTTPS GET of the typed text to the chosen engine's suggestion endpoint, docs/decisions/radarWebSuggest.md). A second user, or the client in another file, fails here on purpose.
        val users = Repo.mainSources().filter { f -> f.readText().let { it.contains("HttpURLConnection") || it.contains("openConnection") || it.contains("okhttp") } }
        assertEquals(listOf("WebSuggestHttp.kt"), users.map { it.name })
        assertTrue("HTTPS only", Repo.mainSources().first { it.name == "WebSuggestHttp.kt" }.readText().contains("startsWith(\"https://\")"))
    }

    @Test fun NFR_16_internetIsDeclaredOnlyForRadarWebSuggestionsAndTheDecisionIsDocumented() {
        // Wave 43 (radarWeb): INTERNET is back, for the one client above and nothing else; location stays out.
        val m = Repo.file("app/src/main/AndroidManifest.xml").readText()
        assertEquals(1, Regex("""<uses-permission android:name="android.permission.INTERNET"""").findAll(m).count())
        assertFalse(m.contains("ACCESS_COARSE_LOCATION"))
        assertTrue(Repo.file("docs/decisions/radarWebSuggest.md").exists())
        assertTrue(Repo.file("docs/decisions/NFR-16.md").exists())
    }

    @Test fun NFR_17_noAdditionalNetworkLikePermissionsDeclared() {
        val m = Repo.file("app/src/main/AndroidManifest.xml").readText()
        val perms = Regex("uses-permission android:name=\"([^\"]+)\"").findAll(m).map { it.groupValues[1] }.toSet()
        val networkLike = perms.filter { it.contains("INTERNET") || it.contains("CHANGE_NETWORK") || it.contains("CHANGE_WIFI") || it.contains("BLUETOOTH") }
        assertEquals(setOf("android.permission.INTERNET"), networkLike.toSet()) // wave 43: INTERNET only; nothing that changes the network state or radios
    }
}
