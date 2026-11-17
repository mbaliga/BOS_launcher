package com.bos.sphere.core.quality

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Wave 18 (CFG-09, NFR-09, NFR-17): the shared :crash-recovery utility is wired the way the library documents, as source audits. */
class CrashRecoveryWiringTest {
    private fun text(path: String) = Repo.file(path).readText()

    /** The first statement of a function body: the text right after `fun name(...) {` up to the first newline of code. */
    private fun firstStatement(src: String, signature: String): String {
        val start = src.indexOf(signature)
        assertTrue("$signature exists", start >= 0)
        val body = src.substring(src.indexOf('{', start) + 1).lines().map { it.trim() }.first { it.isNotEmpty() && !it.startsWith("//") }
        return body
    }

    @Test fun CFG_09_theCrashHandlerIsInstalledAsTheVeryFirstStatementOfApplicationOnCreate() {
        val app = text("app/src/main/kotlin/com/bos/sphere/LiftoffApplication.kt")
        assertTrue(firstStatement(app, "override fun onCreate()").startsWith("CrashRecovery.install(this, appLabel = \"Liftoff\")"))
        // and nothing that could throw runs before it
        assertTrue(app.indexOf("CrashRecovery.install(") < app.indexOf("super.onCreate()"))
        assertTrue(app.indexOf("super.onCreate()") < app.indexOf("CrashRecoveryHost.onProcessStart(this)"))
        assertTrue(app.indexOf("CrashRecoveryHost.onProcessStart(this)") < app.indexOf("PrefsWarmup.start"))
    }

    @Test fun CFG_09_mainActivityShowsTheRecoveryScreenBeforeAnyOtherInitialisation() {
        val main = text("app/src/main/kotlin/com/bos/sphere/MainActivity.kt")
        assertTrue(firstStatement(main, "override fun onCreate(savedInstanceState: Bundle?)").contains("CrashRecovery.maybeShowRecovery(this, \"Liftoff\")"))
        assertTrue("it returns at once (super.onCreate is called first so the platform is satisfied)", main.contains("maybeShowRecovery(this, \"Liftoff\")) { super.onCreate(savedInstanceState); return }"))
        assertTrue(main.indexOf("maybeShowRecovery") < main.indexOf("enableEdgeToEdge()"))
        assertTrue(main.indexOf("maybeShowRecovery") < main.indexOf("CrashLoopGuard.onStart"))
    }

    @Test fun NFR_09_nativeCrashesAndAnrsAreReadFromTheExitHistoryAtStart() {
        assertTrue(text("app/src/main/kotlin/com/bos/sphere/CrashRecoveryHost.kt").contains("CrashRecovery.captureExitDeath("))
    }

    @Test fun CFG_09_theLibraryIsVendoredWithItsSourcesAndTestsAndAHeaderNamingTheCommit() {
        val gradle = text("crash-recovery/build.gradle.kts")
        assertTrue(gradle.contains("mbaliga/Shared-Libraries-asoc") && gradle.contains("crash-recovery 1.4.0") && gradle.contains("d4ade55"))
        for (f in listOf("CrashRecovery", "CrashRecoveryActivity", "CrashReport", "CrashRecoveryLook", "CrashRecoveryStyle"))
            assertTrue(f, Repo.file("crash-recovery/src/main/java/dev/aarso/crashrecovery/$f.kt").exists())
        assertTrue(Repo.file("crash-recovery/src/test/java/dev/aarso/crashrecovery/CrashReportTest.kt").exists())
        assertTrue(text("settings.gradle.kts").contains("include(\":crash-recovery\")"))
        assertTrue(text("app/build.gradle.kts").contains("implementation(project(\":crash-recovery\"))"))
        assertFalse("pure android.widget, zero dependencies", gradle.contains("implementation(") || gradle.contains("compose"))
    }

    @Test fun NFR_17_theLibraryAddsNoPermissionAndNothingUploads() {
        assertFalse(text("crash-recovery/src/main/AndroidManifest.xml").contains("uses-permission"))
        for (f in Repo.file("crash-recovery/src/main").walkTopDown().filter { it.isFile && it.extension == "kt" })
            listOf("java.net", "okhttp", "HttpURLConnection", "openConnection", "URL(").forEach { assertFalse("${f.name} $it", f.readText().contains(it)) }
        // the manifest's permission set is unchanged by this wave: INTERNET was already there for the opt-in web search, nothing was added
        val perms = Regex("uses-permission android:name=\"([^\"]+)\"").findAll(text("app/src/main/AndroidManifest.xml")).map { it.groupValues[1] }.toSet()
        assertFalse(perms.any { it.contains("WRITE_EXTERNAL") || it.contains("READ_LOGS") })
    }

    @Test fun CFG_09_theLocalDuplicateIsGone() {
        for (name in listOf("CrashReports", "CrashShare")) assertFalse(name, Repo.file("core-quality/src/main/kotlin/com/bos/sphere/core/quality/$name.kt").exists())
        val all = Repo.mainSources().filter { !it.path.contains("crash-recovery") }.joinToString("\n") { it.readText() }
        assertFalse(all.contains("CrashShare."))
        assertFalse(all.contains("CrashReports("))
        assertTrue(Repo.file("docs/decisions/crashRecovery.md").exists())
    }

    @Test fun CFG_09_theGroundControlPaneIsThinAndOnlyPreviewsInLabBuilds() {
        val pane = text("app/src/main/kotlin/com/bos/sphere/ui/CrashReportsPane.kt")
        assertTrue(pane.contains("previewIntent"))
        assertTrue("the preview row is behind the Lab gate", pane.indexOf("LabGate.visible()") in 0 until pane.indexOf("previewIntent"))
        assertFalse(pane.contains("ACTION_SEND"))
    }

    @Test fun CFG_09_safeModeKeepsItsButtonsAndAddsOnlyViewCrashReport() {
        val view = text("feature-grid/src/main/kotlin/com/bos/sphere/feature/grid/StateScreens.kt")
        for (s in listOf("Restore last snapshot (%s)", "Carry on", "View crash report")) assertTrue(s, view.contains(s))
        assertTrue(text("app/src/main/kotlin/com/bos/sphere/ui/SafeModeScreen.kt").contains("CrashRecoveryHost.reopen"))
    }
}
