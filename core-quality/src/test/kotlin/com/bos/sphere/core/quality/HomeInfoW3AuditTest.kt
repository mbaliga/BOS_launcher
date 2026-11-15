package com.bos.sphere.core.quality

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Wave 3, home behaviour: the new code paths keep the launcher's rules (off the main thread, no hidden APIs, no accessibility service, no new permissions). */
class HomeInfoW3AuditTest {
    private fun text(path: String) = Repo.file(path).readText()
    private val state = "core-catalog/src/main/kotlin/com/bos/sphere/core/catalog/WorkProfileState.kt"
    private val panels = "app/src/main/kotlin/com/bos/sphere/HomePanels.kt"
    private val newFiles = listOf(
        state,
        "core-catalog/src/main/kotlin/com/bos/sphere/core/catalog/AppPresentation.kt",
        "core-catalog/src/main/kotlin/com/bos/sphere/core/catalog/HomePanelModel.kt",
        "feature-grid/src/main/kotlin/com/bos/sphere/feature/grid/HomeEditHandle.kt",
        panels,
    )

    @Test fun HOM_06_quietModeQueriesAndRequestsRunOffTheMainThread() {
        val s = text(state)
        assertTrue(s.contains("Dispatchers.IO"))
        // every call into AppCatalog (UserManager) sits inside scope.launch on the IO scope
        assertTrue(Regex("""scope\.launch \{[^}]*AppCatalog""", RegexOption.DOT_MATCHES_ALL).containsMatchIn(s))
        assertFalse("no main-thread blocking", s.contains("runBlocking") || s.contains("Dispatchers.Main"))
    }

    @Test fun HOM_06_drawerChipUsesTheSharedStateNotAMainThreadQuery() {
        // the pause chip on home and the one in the drawer read the same state
        assertTrue(text("app/src/main/kotlin/com/bos/sphere/HomeScreen.kt").contains("WorkProfileState"))
    }

    @Test fun HOM_10_HOM_06_noHiddenApiOrReflectionOrAccessibilityInTheNewFiles() {
        newFiles.forEach { f ->
            val s = text(f)
            assertFalse("$f reflection", s.contains("Class.forName") || s.contains(".getDeclaredMethod") || s.contains("java.lang.reflect"))
            assertFalse("$f hidden api", s.contains("@hide") || s.contains("setAccessible"))
            assertFalse("$f accessibility service", s.contains("AccessibilityService"))
        }
    }

    @Test fun HOM_12_editModeUsesNoSystemGestureExclusionAndNoLegacyBack() {
        val s = text("feature-grid/src/main/kotlin/com/bos/sphere/feature/grid/GridHome.kt") + text("feature-grid/src/main/kotlin/com/bos/sphere/feature/grid/HomeEditHandle.kt")
        assertFalse(s.contains("systemGestureExclusion"))
        assertFalse(s.contains("onBackPressed"))
    }

    @Test fun HOM_15_filesPanelReadsOnlyGrantedTreesAndNoBroadStoragePermission() {
        val manifest = text("app/src/main/AndroidManifest.xml")
        assertFalse(manifest.contains("MANAGE_EXTERNAL_STORAGE"))
        assertFalse(manifest.contains("READ_EXTERNAL_STORAGE"))
        assertTrue(text("core-catalog/src/main/kotlin/com/bos/sphere/core/catalog/RecentFilesSource.kt").contains("persistedUriPermissions"))
        // releasing a folder gives the grant back instead of only forgetting it
        assertTrue(text("core-catalog/src/main/kotlin/com/bos/sphere/core/catalog/RecentFilesSource.kt").contains("releasePersistableUriPermission"))
    }

    @Test fun HOM_17_usageAccessIsAReadOnlySpecialAccessGrantedInSystemSettings() {
        assertTrue(text(panels).contains("ACTION_USAGE_ACCESS_SETTINGS"))
        assertFalse("no network call in the panels", text(panels).contains("HttpURLConnection") || text(panels).contains("java.net.URL("))
    }
}

/** EXT-10: the phone's trackpad drives only the launcher's own surface. */
class Ext10AuditTest {
    private fun allDesktopSources() = Repo.file("feature-desktop/src/main").walkTopDown().filter { it.isFile && (it.extension == "kt" || it.name.endsWith(".xml")) }.toList()

    @Test fun EXT_10_noAccessibilityServiceNoInputInjectionAndNoSystemPointer() {
        val banned = listOf("AccessibilityService", "INJECT_EVENTS", "injectInputEvent", "Instrumentation", "BIND_ACCESSIBILITY_SERVICE", "setPointerIcon", "InputManager")
        allDesktopSources().forEach { f ->
            val s = f.readText()
            banned.forEach { b -> assertFalse("${f.name} must not use $b", s.contains(b)) }
        }
        val manifests = listOf("app/src/main/AndroidManifest.xml", "feature-desktop/src/main/AndroidManifest.xml").map { Repo.file(it) }.filter { it.exists() }
        manifests.forEach { m -> assertFalse(m.readText().contains("BIND_ACCESSIBILITY_SERVICE")); assertFalse(m.readText().contains("INJECT_EVENTS")) }
    }

    @Test fun EXT_10_clicksAreHandedToTheLaunchersOwnWindowOnly() {
        val s = Repo.file("feature-desktop/src/main/kotlin/com/bos/sphere/feature/desktop/SyntheticTouch.kt").readText()
        assertTrue("dispatches into the launcher's own view tree", s.contains("dispatchTouchEvent"))
        assertFalse(s.contains("startActivity"))
    }
}
