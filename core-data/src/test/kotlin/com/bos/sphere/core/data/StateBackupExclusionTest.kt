package com.bos.sphere.core.data

import org.junit.Assert.assertEquals
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
        val rx = Regex("""getSharedPreferences\(\s*"([A-Za-z0-9_]+)"""")
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
