package com.bos.sphere.core.quality

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Wave 12 (HOM-22): the recents source keeps the launcher's rules (public APIs only, off the main thread, private, never exported). */
class HomeLandingW12AuditTest {
    private fun text(path: String) = Repo.file(path).readText()
    private val recents = "core-data/src/main/kotlin/com/bos/sphere/core/data/HomeRecents.kt"
    private val landing = "core-data/src/main/kotlin/com/bos/sphere/core/data/HomeShown.kt"
    private val usage = "core-signals/src/main/kotlin/com/bos/sphere/core/signals/Recents.kt"

    @Test fun HOM_22_noHiddenRecentTasksApiOrReflectionAnywhereInTheRecentsCode() {
        for (f in listOf(recents, landing, usage)) {
            val s = text(f)
            assertFalse("$f getRecentTasks", s.contains("getRecentTasks") || s.contains("getRunningTasks") || s.contains("ActivityManager"))
            assertFalse("$f reflection", s.contains("Class.forName") || s.contains(".getDeclaredMethod") || s.contains("java.lang.reflect") || s.contains("setAccessible"))
            assertFalse("$f hidden api", s.contains("@hide"))
            assertFalse("$f accessibility service", s.contains("AccessibilityService"))
        }
        assertTrue("usage events are the public UsageStatsManager query", text(usage).contains("queryEvents"))
    }

    @Test fun HOM_22_theRecentsListIsComputedOffTheMainThreadAndTheFileIsAccessedOnlyThroughItsExecutor() {
        val s = text(recents)
        assertTrue(s.contains("flowOn(Dispatchers.Default)"))
        assertFalse(s.contains("Dispatchers.Main") || s.contains("Looper.getMainLooper"))
        assertTrue(s.contains("io.execute {"))
        assertTrue(s.contains("Executors.newSingleThreadExecutor"))
        // Reads and writes of the removals file sit inside functions the executor runs: init block, change(), write().
        assertFalse(s.contains("runBlocking"))
    }

    @Test fun HOM_22_theRemovalsFileIsClassifiedNeverExportedAndExcludedFromBackup() {
        val schema = text("core-data/src/main/kotlin/com/bos/sphere/core/data/StateSchema.kt")
        assertTrue(schema.contains("RecentsStateStore.RELATIVE_PATH"))
        assertTrue(text(recents).contains("\"usage/recents-state.txt\""))
        for (f in listOf("app/src/main/res/xml/backup_rules.xml", "app/src/main/res/xml/data_extraction_rules.xml"))
            assertTrue("$f excludes the usage directory", text(f).contains("""domain="file" path="usage""""))
        val exporter = text("core-data/src/main/kotlin/com/bos/sphere/core/data/StateService.kt")
        assertFalse(exporter.contains("RecentsState") || exporter.contains("recents-state"))
    }

    @Test fun HOM_22_theHomeSettingsArePlainExportedPrefsInLauncherPrefsNotDeviceBoundKeysAndTheLandingKeyIsRetired() {
        val stores = text("core-data/src/main/kotlin/com/bos/sphere/core/data/LauncherStores.kt")
        for (k in listOf("glanceEnabled", "homeOrder", "homePages")) assertTrue(k, stores.contains("put") && stores.contains("\"$k\""))
        assertFalse("the landing is not written any more", stores.contains("putString(\"homeLanding\""))
        val schema = text("core-data/src/main/kotlin/com/bos/sphere/core/data/StateSchema.kt")
        for (k in listOf("glanceEnabled", "homeOrder", "homePages")) assertFalse(schema.substringAfter("DEVICE_BOUND_PREF_KEYS").substringBefore("\n").contains(k))
    }

    @Test fun HOM_22_noNewAnimationOrNetworkInTheLandingLogic() {
        for (f in listOf(recents, landing)) {
            val s = text(f)
            assertFalse(s.contains("rememberInfiniteTransition") || s.contains("InfiniteRepeatable"))
            assertFalse(s.contains("java.net.") || s.contains("HttpURLConnection") || s.contains("okhttp"))
        }
    }
}
