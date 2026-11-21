package com.bos.sphere.core.quality

import android.content.pm.ApplicationInfo
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RenderPathAuditTest {
    /** Files on the home render path: process start, activity and the home host. */
    private val renderPath = listOf(
        "app/src/main/kotlin/com/bos/sphere/MainActivity.kt",
        "app/src/main/kotlin/com/bos/sphere/LiftoffApplication.kt",
    )

    /** Calls that block the main thread on disk or binder; none may appear in the render-path files. */
    private val blocking = listOf("runBlocking", "Thread.sleep", ".commit()", "getInstalledPackages", "queryIntentActivities", "contentResolver.query(", "getInstalledApplications")

    @Test fun NFR_12_noBlockingCallsInTheRenderPathFiles() {
        val hits = renderPath.flatMap { p -> Repo.file(p).readLines().withIndex().filter { (_, l) -> !l.trimStart().startsWith("//") && blocking.any { l.contains(it) } }.map { "$p:${it.index + 1}" } }
        assertTrue("blocking calls: $hits", hits.isEmpty())
    }

    @Test fun NFR_12_homeHostHasNoBlockingCalls() {
        val src = Repo.file("app/src/main/kotlin/com/bos/sphere/HomeScreen.kt").readLines()
        val hits = src.withIndex().filter { (_, l) -> !l.trimStart().startsWith("//") && blocking.any { l.contains(it) } }.map { it.index + 1 }
        assertTrue("HomeScreen.kt blocking calls at lines $hits", hits.isEmpty())
    }

    @Test fun NFR_12_strictModeIsOnlyEnabledForDebuggableBuilds() {
        assertTrue(DebugStrictMode.shouldEnable(ApplicationInfo.FLAG_DEBUGGABLE))
        assertTrue(DebugStrictMode.shouldEnable(ApplicationInfo.FLAG_DEBUGGABLE or ApplicationInfo.FLAG_HAS_CODE))
        assertFalse(DebugStrictMode.shouldEnable(0))
        assertFalse(DebugStrictMode.shouldEnable(ApplicationInfo.FLAG_HAS_CODE))
    }

    @Test fun NFR_12_applicationInstallsStrictModeOnlyThroughTheDebuggableGate() {
        val app = Repo.file("app/src/main/kotlin/com/bos/sphere/LiftoffApplication.kt").readText()
        assertTrue(app.contains("installIfDebuggable"))
        assertFalse(app.contains("DebugStrictMode.install()"))
        val policy = Repo.file("core-quality/src/main/kotlin/com/bos/sphere/core/quality/DebugStrictMode.kt").readText()
        listOf("detectDiskReads", "detectDiskWrites", "detectNetwork", "setThreadPolicy", "setVmPolicy").forEach { assertTrue(it, policy.contains(it)) }
        assertTrue(Repo.file("app/src/main/AndroidManifest.xml").readText().contains("android:name=\".LiftoffApplication\""))
    }
}
