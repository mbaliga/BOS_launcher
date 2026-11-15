package com.bos.sphere.core.quality

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Wave 4, home surface code gaps: the new paths keep the launcher's rules (off the main thread, no hidden APIs, no new permissions, motion prefs). */
class HomeGapsW4AuditTest {
    private fun text(path: String) = Repo.file(path).readText()
    private val log = "core-data/src/main/kotlin/com/bos/sphere/core/data/TimelineLog.kt"
    private val newFiles = listOf(
        log,
        "core-catalog/src/main/kotlin/com/bos/sphere/core/catalog/DropRule.kt",
        "core-catalog/src/main/kotlin/com/bos/sphere/core/catalog/ProfileBadge.kt",
        "core-catalog/src/main/kotlin/com/bos/sphere/core/catalog/GroupTiles.kt",
        "core-catalog/src/main/kotlin/com/bos/sphere/core/catalog/UsageTileBar.kt",
        "core-catalog/src/main/kotlin/com/bos/sphere/core/catalog/HomeIntentRouting.kt",
        "feature-desktop/src/main/kotlin/com/bos/sphere/feature/desktop/DesktopWidgets.kt",
        "app/src/main/kotlin/com/bos/sphere/ui/DesktopWidgetProvider.kt",
        "feature-dock/src/main/kotlin/com/bos/sphere/feature/dock/DockFootprint.kt",
    )

    @Test fun HOM_18_theTimelineLogReadsAndWritesOnlyThroughItsIoExecutor() {
        val s = text(log)
        // Every file access sits in a function the executor runs (load, the append lambda, writeAll under it).
        assertTrue(s.contains("io.execute { load() }"))
        assertTrue(s.contains("io.execute {"))
        assertFalse("no blocking on the caller", s.contains("runBlocking") || s.contains("Dispatchers.Main"))
        // The default executor is a background thread, never the main looper.
        assertTrue(s.contains("Executors.newSingleThreadExecutor"))
        assertFalse(s.contains("Looper.getMainLooper"))
    }

    @Test fun HOM_18_theTimelineLogIsClassifiedAsNeverExportedAndExcludedFromBackup() {
        val schema = text("core-data/src/main/kotlin/com/bos/sphere/core/data/StateSchema.kt")
        assertTrue(schema.contains("NEVER_EXPORTED_FILES"))
        assertTrue(schema.contains("\"usage\""))
        for (f in listOf("app/src/main/res/xml/backup_rules.xml", "app/src/main/res/xml/data_extraction_rules.xml"))
            assertTrue("$f excludes the usage directory", text(f).contains("""domain="file" path="usage""""))
        // The exporter never reads the log.
        assertFalse(text("core-data/src/main/kotlin/com/bos/sphere/core/data/StateService.kt").contains("TimelineLog"))
    }

    @Test fun HOM_13_HOM_06_HOM_04_noHiddenApiOrReflectionOrAccessibilityInTheNewFiles() {
        newFiles.forEach { f ->
            val s = text(f)
            assertFalse("$f reflection", s.contains("Class.forName") || s.contains(".getDeclaredMethod") || s.contains("java.lang.reflect"))
            assertFalse("$f hidden api", s.contains("@hide") || s.contains("setAccessible"))
            assertFalse("$f accessibility service", s.contains("AccessibilityService"))
            assertFalse("$f gesture exclusion", s.contains("systemGestureExclusion"))
            assertFalse("$f legacy back", s.contains("onBackPressed"))
        }
    }

    @Test fun HOM_04_theDwellTickRunsOnlyWhileADragIsInProgressAndIsNotAnAnimation() {
        val s = text("feature-grid/src/main/kotlin/com/bos/sphere/feature/grid/GridHome.kt")
        assertTrue(s.contains("while (dragKey != null)"))
        assertTrue(s.contains("dropTracker.tick"))
        assertFalse(s.contains("systemGestureExclusion"))
    }

    @Test fun HOM_06_privateSpaceIsUnlockedOnlyThroughTheSystemsOwnRequest() {
        val s = text("core-catalog/src/main/kotlin/com/bos/sphere/core/catalog/WorkProfileState.kt")
        assertTrue(s.contains("setPrivateLocked"))
        // The unlock is the public quiet-mode request (the system shows its own authentication); nothing here handles a credential.
        assertTrue(text("core-catalog/src/main/kotlin/com/bos/sphere/core/catalog/AppCatalog.kt").contains("requestQuietModeEnabled"))
        assertFalse(s.contains("BiometricPrompt") || s.contains("KeyguardManager"))
    }

    @Test fun HOM_13_theDesktopAddsNoPermissionAndTheWidgetHostIsOneSharedInstance() {
        val manifest = text("app/src/main/AndroidManifest.xml")
        // Binding goes through the system consent screen, not through a held permission.
        assertFalse(manifest.contains("uses-permission android:name=\"android.permission.BIND_APPWIDGET\""))
        val host = text("feature-grid/src/main/kotlin/com/bos/sphere/feature/grid/WidgetHosting.kt")
        assertTrue(host.contains("fun shared(context: Context)"))
        assertTrue(host.contains("LauncherWidgetHost.shared(context)"))
    }

    @Test fun HOM_02_theDockPinsAreBoundToTheHomeStateFromOnePlaceAndNeverOnTheMainThreadIo() {
        val s = text("app/src/main/kotlin/com/bos/sphere/ui/DockLayer.kt")
        assertTrue(s.contains("object DockPinBinding"))
        assertTrue(s.contains("manager.pinSink"))
        assertFalse(s.contains("runBlocking"))
    }
}
