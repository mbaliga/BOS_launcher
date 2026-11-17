package com.bos.sphere.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** NFR-18 / CFG-01: what stays out of system backup, and a guard that no preference file slips in unclassified. */
class StateBackupExclusionTest {
    private fun root(): File {
        var d: File? = File("").absoluteFile
        while (d != null && !File(d, "app/src/main").isDirectory) d = d.parentFile
        return d!!
    }

    private fun text(rel: String) = File(root(), rel).readText()

    @Test
    fun NFR_18_everyDeviceBoundOrPersonalStoreIsExcludedFromCloudBackupAndDeviceTransferAndFullBackup() {
        val dx = text("app/src/main/res/xml/data_extraction_rules.xml")
        val cloud = dx.substringAfter("<cloud-backup").substringBefore("</cloud-backup>")
        val transfer = dx.substringAfter("<device-transfer").substringBefore("</device-transfer>")
        val full = text("app/src/main/res/xml/backup_rules.xml")
        for ((name, body) in listOf("cloud-backup" to cloud, "device-transfer" to transfer, "fullBackupContent" to full)) {
            for (p in StateSchema.BACKUP_EXCLUDED_PREF_FILES) assertTrue("$name must exclude $p.xml", body.contains("""domain="sharedpref" path="$p.xml""""))
            for (f in StateSchema.BACKUP_EXCLUDED_FILES) assertTrue("$name must exclude $f", body.contains("""domain="file" path="$f""""))
        }
    }

    @Test
    fun CFG_01_everyPreferenceFileInTheSourceIsEitherExportedOrExcludedFromBackup() {
        val exported = setOf("launcher_prefs", "home_layout", "shortcuts", "launchpad_rules", "hidden_apps", "state_meta") + PrefFilesSource.ALLOWED
        val excluded = StateSchema.NEVER_EXPORTED_STORES + StateSchema.BACKUP_EXCLUDED_PREF_FILES + "search_habits"
        // Wave 18: stores open their files through TolerantPrefs.of(context, "name") as well as getSharedPreferences("name"); both count.
        val rx = Regex("""(?:getSharedPreferences\(\s*|TolerantPrefs\.of\([^,()]+(?:\([^()]*\))?[^,()]*,\s*)"([A-Za-z0-9_]+)"""")
        val found = HashSet<String>()
        root().walkTopDown()
            .onEnter { it.name != "build" && it.name != ".git" && it.name != "test" }
            .filter { it.isFile && it.extension == "kt" }
            .forEach { f -> rx.findAll(f.readText()).forEach { found += it.groupValues[1] } }
        assertTrue("source scan found preference files", found.size > 10)
        val unclassified = found - exported - excluded
        assertEquals("classify these in StateSchema / PrefFilesSource.ALLOWED and the backup rules: $unclassified", emptySet<String>(), unclassified)
    }

    @Test
    fun CFG_01_noExportedPrefFileIsAlsoAHistoryOrIndexStore() {
        assertTrue((PrefFilesSource.ALLOWED.toSet() intersect (StateSchema.NEVER_EXPORTED_STORES + StateSchema.BACKUP_EXCLUDED_PREF_FILES)).isEmpty())
    }
}

/** NFR-18: device-bound widget ids and the background picture are removed after a platform restore. */
class PlatformRestoreGuardTest {
    @org.junit.Rule @JvmField val tmp = org.junit.rules.TemporaryFolder()

    private class Env(root: File) {
        val shortcuts = ShortcutStore(MapSharedPreferences())
        val prefs = LauncherPrefsStore(MapSharedPreferences())
        val extras = StateExtrasStore(File(root, "extras.json"))
        val bound = File(root, "files/state/device-binding")
        val local = File(root, "nobackup/device-binding")
        init {
            shortcuts.setCorner(Corner.TopLeft, CornerSlot.Widget(41, "Clock"))
            shortcuts.setLandscapeCorner(Corner.TopRight, CornerSlot.Widget(42, "Weather"))
            prefs.update { it.copy(leftPage = LeftPage.Widget, leftWidgetId = 43) }
        }
        fun check() = PlatformRestoreGuard.check(bound, local, shortcuts, prefs, extras)
    }

    @Test
    fun NFR_18_aFirstRunOrAnUpdateFromABuildWithoutTheGuardKeepsEveryWidget() {
        val e = Env(tmp.root)
        val r = e.check()
        assertFalse(r.restored)
        assertTrue(e.shortcuts.corners.value[Corner.TopLeft] is CornerSlot.Widget)
        assertEquals(43, e.prefs.prefs.value.leftWidgetId)
        assertEquals(e.bound.readText(), e.local.readText())
        assertFalse(e.check().restored) // second start: ids agree
    }

    @Test
    fun NFR_18_aPlatformRestoreBringsBackTheBoundIdButNotTheLocalOneAndTheWidgetsAreRemoved() {
        val e = Env(tmp.root)
        e.check()
        e.local.delete() // the no-backup copy is not restored
        val r = e.check()
        assertTrue(r.restored)
        assertEquals(setOf("corner.TopLeft", "lcorner.TopRight", "leftPage"), r.widgetsCleared.map { it.slot }.toSet())
        assertEquals(CornerSlot.Empty, e.shortcuts.corners.value[Corner.TopLeft])
        assertEquals(CornerSlot.Empty, e.shortcuts.explicitLandscapeCorners()[Corner.TopRight] ?: CornerSlot.Empty)
        assertEquals(LauncherPrefs().leftWidgetId, e.prefs.prefs.value.leftWidgetId)
        assertEquals(3, e.extras.read().widgets.size) // listed so the user can add them again
        assertFalse(e.check().restored) // the guard settles after one run
    }

    @Test
    fun NFR_18_aDifferentLocalIdAlsoCountsAsARestoreAndSetupThatIsNotDeviceBoundIsUntouched() {
        val e = Env(tmp.root)
        e.prefs.update { it.copy(accentColor = 0xFF123456) }
        e.check()
        e.local.writeText("some-other-device")
        assertTrue(e.check().restored)
        assertEquals(0xFF123456, e.prefs.prefs.value.accentColor)
    }
}
