package com.bos.sphere.core.quality

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Wave 5, widgets, stacks, files tile and reconcile: the new paths keep the launcher's rules. */
class HomeRestW5AuditTest {
    private fun text(path: String) = Repo.file(path).readText()
    private val catalog = "core-catalog/src/main/kotlin/com/bos/sphere/core/catalog"
    private val newFiles = listOf(
        "$catalog/FileThumbs.kt",
        "$catalog/MemberReconcile.kt",
        "feature-desktop/src/main/kotlin/com/bos/sphere/feature/desktop/WidgetResizeHandle.kt",
        "feature-desktop/src/main/kotlin/com/bos/sphere/feature/desktop/DesktopWidgets.kt",
        "feature-grid/src/main/kotlin/com/bos/sphere/feature/grid/WidgetStackView.kt",
        "feature-dock/src/main/kotlin/com/bos/sphere/feature/dock/DockRail.kt",
    )

    @Test fun HOM_15_thumbnailsAreAskedOnlyThroughTheGrantedDocumentUriAndOnlyOffTheMainThread() {
        val t = text("$catalog/FileThumbs.kt")
        assertTrue(t.contains("loadThumbnail(Uri.parse(f.uri)"))
        assertFalse(t.contains("MediaStore") || t.contains("READ_EXTERNAL_STORAGE") || t.contains("MANAGE_EXTERNAL_STORAGE"))
        val host = text("app/src/main/kotlin/com/bos/sphere/HomeItemsHost.kt")
        val refresh = host.substring(host.indexOf("internal suspend fun refreshFiles()"), host.indexOf("internal suspend fun refreshFiles()") + 900)
        assertTrue("refreshFiles hops to the IO dispatcher before reading or loading", refresh.contains("withContext(Dispatchers.IO)"))
        // The Files tile reaches the platform thumbnail API only here (the search data source has its own older use).
        val callers = Repo.mainSources().filter { it.readText().contains("loadThumbnail(") }.map { it.name }
        assertTrue(callers.toString(), "FileThumbs.kt" in callers && callers.all { it == "FileThumbs.kt" || it == "SearchData.kt" })
        val manifest = text("app/src/main/AndroidManifest.xml")
        assertFalse(manifest.contains("READ_EXTERNAL_STORAGE") || manifest.contains("MANAGE_EXTERNAL_STORAGE"))
    }

    @Test fun HOM_10_theMissingSinceTimesLiveInTheExcludedMiscFileNotInANewPreferenceFile() {
        val host = text("app/src/main/kotlin/com/bos/sphere/HomeItemsHost.kt")
        assertTrue(host.contains("getSharedPreferences(\"liftoff_misc\""))
        assertTrue(text("core-data/src/main/kotlin/com/bos/sphere/core/data/StateSchema.kt").contains("\"liftoff_misc\""))
        assertTrue(text("app/src/main/kotlin/com/bos/sphere/HomeScreen.kt").contains("homeItems.reconcileMembers("))
    }

    @Test fun HOM_13_HOM_14_HOM_02_noHiddenApiReflectionAccessibilityServiceOrGestureExclusion() {
        newFiles.forEach { f ->
            val s = text(f)
            assertFalse("$f reflection", s.contains("Class.forName") || s.contains(".getDeclaredMethod") || s.contains("java.lang.reflect"))
            assertFalse("$f hidden api", s.contains("@hide") || s.contains("setAccessible"))
            assertFalse("$f accessibility service", s.contains("AccessibilityService"))
            assertFalse("$f gesture exclusion", s.contains("systemGestureExclusion"))
            assertFalse("$f infinite animation", s.contains("rememberInfiniteTransition"))
        }
    }

    @Test fun HOM_14_theStackKeepsItsOverlaySwipeStripsAndHOM_02_theRailKeepsItsWidgetGrip() {
        val stack = text("feature-grid/src/main/kotlin/com/bos/sphere/feature/grid/WidgetStackView.kt")
        assertTrue(stack.contains("STRIP_BOTTOM_TAG") && stack.contains("STRIP_END_TAG"))
        val rail = text("feature-dock/src/main/kotlin/com/bos/sphere/feature/dock/DockRail.kt")
        assertTrue(rail.contains("widgetGripTag(") && rail.contains("reorderable && !wide"))
    }

    @Test fun HOM_13_theDesktopOffersTheHandleAndTheSizeMenuAndTheProviderReportsItsLimits() {
        val surface = text("feature-desktop/src/main/kotlin/com/bos/sphere/feature/desktop/DesktopSurface.kt")
        assertTrue(surface.contains("WidgetResizeHandle(") && surface.contains("DesktopWidgets.resizeByCells("))
        val provider = text("app/src/main/kotlin/com/bos/sphere/ui/DesktopWidgetProvider.kt")
        assertTrue(provider.contains("minResizeWidth") && provider.contains("RESIZE_HORIZONTAL") && provider.contains("RESIZE_VERTICAL"))
    }
}
