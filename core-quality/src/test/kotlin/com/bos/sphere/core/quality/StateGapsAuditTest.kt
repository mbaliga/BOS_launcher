package com.bos.sphere.core.quality

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Wave 4 state-gaps audits: backup classification (NFR-18), the grants list (CFG-07), opt-in surface restore (NFR-11), live shortcut export (CFG-01). */
class StateGapsAuditTest {
    private fun text(p: String) = Repo.file(p).readText()

    @Test fun NFR_18_deviceBoundValuesAreOnlyWrittenThroughTheExcludedDeviceFile() {
        // The two keys may be written to the main preference file only by DeviceBound (the no-device-file path used by the settings codec and tests).
        val offenders = Repo.mainSources().filter { it.name != "DeviceBound.kt" && it.name != "StateSchema.kt" }.filter { f ->
            f.readLines().any { l -> !l.trimStart().startsWith("//") && !l.trimStart().startsWith("*") && (l.contains("putString(\"bgPicture\"") || l.contains("putInt(\"leftWidgetId\"")) }
        }.map { it.name }
        assertEquals("route device-bound values through DeviceBound: $offenders", emptyList<String>(), offenders)
    }

    @Test fun NFR_18_theLiveStoresAreBuiltOverTheExcludedDeviceFileAndAllThreeRuleFilesExcludeIt() {
        val stores = text("core-data/src/main/kotlin/com/bos/sphere/core/data/LauncherStores.kt")
        assertTrue(stores.contains("LauncherPrefsStore(app.getSharedPreferences(\"launcher_prefs\", Context.MODE_PRIVATE), deviceBound)"))
        assertTrue(stores.contains("ShortcutStore(app.getSharedPreferences(\"shortcuts\", Context.MODE_PRIVATE), deviceBound)"))
        val dx = text("app/src/main/res/xml/data_extraction_rules.xml")
        val cloud = dx.substringAfter("<cloud-backup").substringBefore("</cloud-backup>")
        val transfer = dx.substringAfter("<device-transfer").substringBefore("</device-transfer>")
        for (body in listOf(cloud, transfer, text("app/src/main/res/xml/backup_rules.xml"))) assertTrue(body.contains("""domain="sharedpref" path="device_bound.xml""""))
    }

    @Test fun NFR_18_thePictureIsWrittenToTheNoBackupDirectoryAndOlderOnesAreMovedAtStart() {
        assertTrue(text("core-data/src/main/kotlin/com/bos/sphere/core/data/StateService.kt").contains("BackgroundPictures.migrate(app.filesDir, app.noBackupFilesDir"))
        assertFalse(text("app/src/main/kotlin/com/bos/sphere/HomeScreen.kt").contains("filesDir, \"background_"))
    }

    @Test fun CFG_07_stepCountingAndWeatherStayOutsideTheGrantScreenBecauseTheSpecListsFourGrants() {
        val spec = text("docs/Spec.md")
        val row = spec.lines().first { it.startsWith("| CFG-07 ") }
        assertTrue("the spec's grants are notification access, usage access, contacts and folder access", row.contains("Notification access, usage access, contacts and folder access"))
        assertFalse("activity recognition and location are not on that list", row.contains("activity", ignoreCase = true) || row.contains("location", ignoreCase = true))
        // They remain direct runtime prompts, requested only when the user first uses the feature (the home's step and weather effects).
        val home = text("app/src/main/kotlin/com/bos/sphere/HomeScreen.kt")
        assertTrue(home.contains("stepsPermission.launch(android.Manifest.permission.ACTIVITY_RECOGNITION)"))
        assertTrue(home.contains("locationPermission.launch(android.Manifest.permission.ACCESS_COARSE_LOCATION)"))
    }

    @Test fun NFR_11_openSurfaceRestoreIsOptInOffByDefaultAndSkippedInSafeMode() {
        val home = text("app/src/main/kotlin/com/bos/sphere/HomeScreen.kt")
        assertTrue(home.contains("takeRestore(prefs.restoreOpenSurfaces, com.bos.sphere.core.data.CrashLoopGuard.safeMode)"))
        assertTrue(text("core-data/src/main/kotlin/com/bos/sphere/core/data/HomeModel.kt").contains("val restoreOpenSurfaces: Boolean = false"))
        assertTrue(text("app/src/main/kotlin/com/bos/sphere/ui/SettingsCategories.kt").contains("restoreOpenSurfaces = v"))
        // Settings and menus hold edits in progress and are never restored.
        assertFalse(text("core-data/src/main/kotlin/com/bos/sphere/core/data/OpenSurfaceStore.kt").contains("Settings,"))
    }

    @Test fun CFG_01_theExportReadsTheLiveShortcutRegistryAndTheAppInstallsTheHook() {
        assertTrue(text("app/src/main/kotlin/com/bos/sphere/MainActivity.kt").contains("StateService.keyboardLiveSource = { LauncherInput.registry.export() }"))
        assertTrue(text("core-data/src/main/kotlin/com/bos/sphere/core/data/StateService.kt").contains("keyboardLive = { keyboardLiveSource?.let { LiveText(it()) } }"))
    }

    @Test fun CFG_04_everyDestructiveEditAnnouncesItselfByKindAndTheLimiterIsPerKind() {
        assertTrue(text("core-data/src/main/kotlin/com/bos/sphere/core/data/StateService.kt").contains("limiter.allow(reason, System.currentTimeMillis())"))
        val kinds = Repo.mainSources().flatMap { f -> Regex("""DestructiveEdits\.before\("([^"]+)"\)""").findAll(f.readText()).map { it.groupValues[1] }.toList() }.toSet()
        assertTrue("Launchpad rule removal is hooked: $kinds", "remove Launchpad rule" in kinds)
        assertTrue(kinds.size >= 6)
    }
}
