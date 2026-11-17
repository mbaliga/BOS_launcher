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

    /** Wave 15: the background picture went with the old gallery (docs/decisions/backgroundsReset.md); this replaces the wave 4 test that moved pictures to the no-backup directory. */
    @Test fun NFR_18_leftoverBackgroundPicturesArePurgedAtStartAndNothingWritesThemAnymore() {
        assertTrue(text("core-data/src/main/kotlin/com/bos/sphere/core/data/StateService.kt").contains("BackgroundPictures.purge(app.filesDir, app.noBackupFilesDir)"))
        assertFalse(text("app/src/main/kotlin/com/bos/sphere/HomeScreen.kt").contains("background_"))
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
        val svc = text("core-data/src/main/kotlin/com/bos/sphere/core/data/StateService.kt")
        assertTrue(svc.contains("limiter.decide(reason, System.currentTimeMillis())"))
        assertTrue("the first snapshot of each burst is pinned in the store", svc.contains("pin = d.burstStart"))
        val kinds = Repo.mainSources().flatMap { f -> Regex("""DestructiveEdits\.before\("([^"]+)"\)""").findAll(f.readText()).map { it.groupValues[1] }.toList() }.toSet()
        assertTrue("Launchpad rule removal is hooked: $kinds", "remove Launchpad rule" in kinds)
        assertTrue(kinds.size >= 6)
    }

    @Test fun NFR_18_everyStoreThatPersistsHomeItemKeysWritesThemThroughTheWidgetIdSplit() {
        val stores = listOf(
            "core-data/src/main/kotlin/com/bos/sphere/core/data/LauncherStores.kt",
            "core-catalog/src/main/kotlin/com/bos/sphere/core/catalog/CatalogStores.kt",
            "core-catalog/src/main/kotlin/com/bos/sphere/core/catalog/HomeExtrasStore.kt",
            "core-catalog/src/main/kotlin/com/bos/sphere/core/catalog/SizeClassLayouts.kt",
        )
        for (f in stores) assertTrue("$f must use WidgetIdSplit", text(f).contains("WidgetIdSplit.putString"))
        assertTrue(text("core-data/src/main/kotlin/com/bos/sphere/core/data/LauncherStores.kt").contains("HomeLayoutStore(app.getSharedPreferences(\"home_layout\", Context.MODE_PRIVATE), deviceBound)"))
        // No store writes the raw encoded arrangement to its file any more.
        assertFalse(text("core-catalog/src/main/kotlin/com/bos/sphere/core/catalog/HomeExtrasStore.kt").contains("putString(\"extras\""))
        assertFalse(text("core-catalog/src/main/kotlin/com/bos/sphere/core/catalog/SizeClassLayouts.kt").contains("putString(\"pages:"))
        // The export and import of the arrangement files go through the same rewrite.
        assertTrue(text("core-data/src/main/kotlin/com/bos/sphere/core/data/StateLiveSources.kt").contains("WidgetIdSplit.ARRANGEMENT_FILES"))
        assertTrue(text("core-data/src/main/kotlin/com/bos/sphere/core/data/StateCodec.kt").contains("WidgetIdSplit.sanitize(HomeLayoutCodec.encode"))
    }

    @Test fun NFR_18_theDockStreamFileKeepsItsWidgetListsInTheNoBackupDirectory() {
        val dock = text("feature-dock/src/main/kotlin/com/bos/sphere/feature/dock/FileStore.kt")
        assertTrue(dock.contains("noBackupFilesDir, \"liftoff_workspace_widgets.json\""))
        assertTrue("the export source must not read widget lists back", text("core-data/src/main/kotlin/com/bos/sphere/core/data/StateLiveSources.kt").contains("put(\"widgets\", JSONArray())"))
    }

    @Test fun NFR_11_theHomeRestoresEveryOpenSurfaceTheStoreCanRecordAndNothingElse() {
        val home = text("app/src/main/kotlin/com/bos/sphere/HomeScreen.kt")
        val entries = Regex("""enum class OpenSurface \{([^}]*)}""").find(text("core-data/src/main/kotlin/com/bos/sphere/core/data/OpenSurfaceStore.kt"))!!.groupValues[1].split(",").map { it.trim() }.filter { it.isNotEmpty() }
        assertTrue(entries.isNotEmpty())
        for (e in entries) assertTrue("HomeScreen must open $e on restore and record it", home.contains("restoredSurface == com.bos.sphere.core.data.OpenSurface.$e") && home.contains("com.bos.sphere.core.data.OpenSurface.$e else"))
        assertTrue("safe mode is passed to the restore", home.contains("takeRestore(prefs.restoreOpenSurfaces, com.bos.sphere.core.data.CrashLoopGuard.safeMode)"))
    }

    @Test fun CFG_02_theWorkProfileWidgetReAddUsesTheHostsSystemLaunchedConfigureForOtherProfiles() {
        val r = text("feature-grid/src/main/kotlin/com/bos/sphere/feature/grid/WidgetRebind.kt")
        assertTrue(r.contains("startAppWidgetConfigureActivityForResult"))
        assertTrue(r.contains("WidgetConfigureResults.results.collect"))
        assertTrue(text("app/src/main/kotlin/com/bos/sphere/MainActivity.kt").contains("WidgetConfigureResults.deliver"))
    }
}
