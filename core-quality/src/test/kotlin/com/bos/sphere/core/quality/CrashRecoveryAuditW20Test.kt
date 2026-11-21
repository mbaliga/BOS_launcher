package com.bos.sphere.core.quality

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Wave 20 audit (CFG-09, NFR-09, NFR-17): manifest merge, backup, what a shared report can hold, and the Lab crash row, as source audits. */
class CrashRecoveryAuditW20Test {
    private fun text(path: String) = Repo.file(path).readText()
    private val libSources get() = Repo.file("crash-recovery/src/main").walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()

    @Test fun CFG_09_theLibraryActivityIsNotExportedHasNoFiltersAndNoOwnTaskAffinity() {
        val lib = text("crash-recovery/src/main/AndroidManifest.xml")
        assertTrue(lib.contains("android:exported=\"false\""))
        assertFalse("no intent filter: only our own explicit intent starts it", lib.contains("intent-filter"))
        assertFalse("it stays in the launcher's task so Continue returns to a fresh start", lib.contains("taskAffinity"))
        assertFalse(lib.contains("launchMode"))
        val app = text("app/src/main/AndroidManifest.xml")
        assertFalse("the app manifest does not re-declare (and so cannot export) the library activity", app.contains("CrashRecoveryActivity"))
    }

    @Test fun NFR_18_allFourLibraryFilesAndOurCopyAreExcludedFromEveryBackupPath() {
        val cloud = text("app/src/main/res/xml/data_extraction_rules.xml")
        val transfer = cloud.substring(cloud.indexOf("<device-transfer>"))
        val cloudOnly = cloud.substring(0, cloud.indexOf("<device-transfer>"))
        for (f in listOf("crash_recovery_report.txt", "crash_recovery_streak.txt", "crash_recovery_exit_seen.txt", "crash_recovery_last_shown.txt")) {
            for ((name, body) in listOf("cloud" to cloudOnly, "transfer" to transfer, "fullBackupContent" to text("app/src/main/res/xml/backup_rules.xml")))
                assertTrue("$name must exclude $f", body.contains("domain=\"file\" path=\"$f\""))
            assertTrue("$f is a file name the library or host really uses", libSources.any { it.readText().contains(f) } || text("app/src/main/kotlin/com/bos/sphere/CrashRecoveryHost.kt").contains(f))
        }
    }

    /** The library's whole report is these keys. A library update that adds a field (a user id, an account, a path) must be reviewed and this list changed on purpose. */
    @Test fun NFR_17_theReportHoldsExactlyTheKnownDeviceFieldsAndNoIdentifier() {
        val src = text("crash-recovery/src/main/java/dev/aarso/crashrecovery/CrashReport.kt")
        val encode = src.substring(src.indexOf("fun encode()"), src.indexOf("companion object"))
        val keys = Regex("""kv\("([A-Za-z]+)"""").findAll(encode).map { it.groupValues[1] }.toList()
        assertEquals(
            listOf("appLabel", "whenMillis", "threadName", "excType", "excMessage", "plainLanguage", "versionName", "versionCode", "packageName", "installSource",
                "osSdkInt", "manufacturer", "model", "freeMemMb", "totalMemMb"),
            keys,
        )
        val all = libSources.joinToString("\n") { it.readText() }
        for (banned in listOf("ANDROID_ID", "Settings.Secure", "Build.SERIAL", "getSerial(", "getDeviceId", "getImei", "getInstalledPackages", "getInstalledApplications",
            "queryIntentActivities", "AccountManager", "getAccounts", "TelephonyManager", "WifiManager", "LocationManager", "ContactsContract", "getExternalStorage", "getExternalFilesDir", "Environment."))
            assertFalse("the recovery library must not touch $banned", all.contains(banned))
    }

    @Test fun NFR_17_theLauncherPutsNoPersonalTextIntoItsOwnCrashHooks() {
        // The only text a shared report carries beyond the device block is the exception message and trace, so our own throw sites that run at start must not format user content.
        val host = text("app/src/main/kotlin/com/bos/sphere/CrashRecoveryHost.kt") + text("app/src/main/kotlin/com/bos/sphere/LiftoffApplication.kt")
        assertFalse(host.contains("captureInitError("))
        val lab = text("app/src/main/kotlin/com/bos/sphere/ui/LabCrash.kt")
        assertTrue(lab.contains("IllegalStateException(\"Crash on purpose from the Lab (testing the recovery screen)\")"))
    }

    @Test fun CFG_17_theCrashOnPurposeRowIsReachableOnlyThroughTheLabListBehindTheGate() {
        val uses = Repo.mainSources().filter { it.name != "LabCrash.kt" }.filter { it.readText().contains("LabCrash") }.map { it.name }
        assertEquals("LabCrash is built in the Lab list and nowhere else", listOf("Lab.kt"), uses)
        val cats = text("app/src/main/kotlin/com/bos/sphere/ui/SettingsCategories.kt")
        val gate = cats.indexOf("m.lab.isNotEmpty() && LabGate.visible()")
        assertTrue(gate > 0)
        assertEquals("m.lab is only turned into rows inside the gate", 1, Regex("""m\.lab\.map""").findAll(cats).count())
        assertTrue(cats.indexOf("m.lab.map") > gate)
        // Nothing else lists LabEntry rows (the Radar settings index reads the built categories, so a gated-off Lab is not searchable either).
        val others = Repo.mainSources().filter { it.name != "SettingsCategories.kt" && it.name != "Lab.kt" }.filter { Regex("""\bm\.lab\b""").containsMatchIn(it.readText()) }.map { it.name }
        assertEquals(emptyList<String>(), others)
        val gradle = text("app/build.gradle.kts")
        assertTrue("a public build can switch the Lab off", gradle.contains("liftoff.lab") && gradle.contains("LAB_VISIBLE"))
    }

    @Test fun CFG_09_theOnlyLauncherCodeThatThrowsOnPurposeIsTheLabRow() {
        val offenders = Repo.mainSources().filter { !it.path.contains("crash-recovery") && it.name != "LabCrash.kt" }
            .filter { Regex("""Thread\s*\{\s*throw\b""").containsMatchIn(it.readText()) }.map { it.name }
        assertEquals(emptyList<String>(), offenders)
    }
}
