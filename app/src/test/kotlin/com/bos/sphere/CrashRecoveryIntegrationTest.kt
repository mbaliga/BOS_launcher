package com.bos.sphere

import android.app.Activity
import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import com.bos.sphere.core.data.CrashLoopGuard
import dev.aarso.crashrecovery.CrashRecovery
import dev.aarso.crashrecovery.CrashRecoveryActivity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowActivityManager
import java.io.File

/**
 * CFG-09, NFR-09, NFR-17 (wave 18): the shared crash-recovery utility is what the owner sees after a crash. Real MainActivity and the
 * library's real recovery activity under Robolectric; the report is seeded through the library's public API (captureInitError).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h720dp-xhdpi")
class CrashRecoveryIntegrationTest {
    private val app: Context get() = ApplicationProvider.getApplicationContext()

    @Before fun fresh() { wipe() }
    @After fun clean() { wipe() }

    private fun wipe() {
        AppSmokeHarness.resetProcess()
        File(app.filesDir, "").listFiles { f -> f.name.startsWith("crash_recovery") }?.forEach { it.delete() }
        app.getSharedPreferences("crash_loop", Context.MODE_PRIVATE).edit().clear().commit()
    }

    private fun seedCrash(message: String = "boom from the test") =
        CrashRecovery.captureInitError(app, "Liftoff", IllegalStateException(message))

    private fun texts(root: View, out: MutableList<TextView> = mutableListOf()): List<TextView> {
        if (root is TextView) out += root
        if (root is ViewGroup) for (i in 0 until root.childCount) texts(root.getChildAt(i), out)
        return out
    }

    private fun Activity.textsOnScreen() = texts(window.decorView).map { it.text.toString() }

    private fun Activity.click(label: String) {
        val v = texts(window.decorView).first { it.text.toString().contains(label) }
        var target: View? = v
        while (target != null && !target.hasOnClickListeners()) target = target.parent as? View
        assertNotNull("a clickable view for $label", target)
        target!!.performClick()
    }

    @Test fun CFG_09_withAPendingReportMainActivityStartsTheRecoveryScreenAndFinishes() {
        seedCrash()
        val main = Robolectric.buildActivity(MainActivity::class.java).create().get()
        assertTrue("MainActivity finished without building the home", main.isFinishing)
        val started = shadowOf(main).nextStartedActivity
        assertEquals(CrashRecoveryActivity::class.java.name, started.component!!.className)
    }

    @Test fun CFG_09_theRecoveryScreenAndSafeModeNeverRaceTheLoopGuardIsNotTouchedWhileTheReportIsShown() {
        seedCrash()
        Robolectric.buildActivity(MainActivity::class.java).create()
        assertFalse("CrashLoopGuard.onStart did not run: the recovery start is not counted as a launcher start",
            app.getSharedPreferences("crash_loop", Context.MODE_PRIVATE).contains("last"))
        assertFalse(CrashLoopGuard.safeMode)
    }

    @Test fun NFR_09_theRecoveryScreenShowsTheReadableReportAndEveryWayToKeepOrShareIt() {
        seedCrash("the widget host fell over")
        val screen = Robolectric.buildActivity(CrashRecoveryActivity::class.java, CrashRecoveryActivity.intent(app, "Liftoff")).setup().get()
        val on = screen.textsOnScreen().joinToString("\n")
        assertTrue(on, on.contains("Well, that happened."))
        assertTrue(on.contains("Continue to Liftoff"))
        assertTrue(on.contains("View the full report"))
        assertTrue(on.contains("Discard"))
        screen.click("View the full report")
        val report = screen.textsOnScreen().joinToString("\n")
        assertTrue("the exception is readable", report.contains("IllegalStateException") && report.contains("the widget host fell over"))
        for (tile in listOf("Copy", "Save file", "Email", "WhatsApp")) assertTrue(tile, report.contains(tile))
    }

    @Test fun NFR_17_shareGoesOnlyThroughTheShareSheetOnTheUsersTap() {
        seedCrash("share me")
        val screen = Robolectric.buildActivity(CrashRecoveryActivity::class.java, CrashRecoveryActivity.intent(app, "Liftoff")).setup().get()
        screen.click("View the full report")
        assertNull("nothing is sent before the tap", shadowOf(screen).nextStartedActivity)
        screen.click("Email")
        val chooser = shadowOf(screen).nextStartedActivity
        assertEquals(android.content.Intent.ACTION_CHOOSER, chooser.action)
        val send = chooser.getParcelableExtra<android.content.Intent>(android.content.Intent.EXTRA_INTENT)!!
        assertEquals(android.content.Intent.ACTION_SEND, send.action)
        assertTrue(send.getStringExtra(android.content.Intent.EXTRA_TEXT)!!.contains("share me"))
    }

    @Test fun NFR_17_saveFileAsksTheSystemDocumentPickerOnTheUsersTapAndWritesNothingItself() {
        seedCrash("save me")
        val screen = Robolectric.buildActivity(CrashRecoveryActivity::class.java, CrashRecoveryActivity.intent(app, "Liftoff")).setup().get()
        screen.click("View the full report")
        screen.click("Save file")
        val create = shadowOf(screen).nextStartedActivity
        assertEquals(android.content.Intent.ACTION_CREATE_DOCUMENT, create.action)
    }

    @Test fun NFR_17_copyPutsTheReportOnTheClipboardOnTheUsersTap() {
        seedCrash("copy me")
        val screen = Robolectric.buildActivity(CrashRecoveryActivity::class.java, CrashRecoveryActivity.intent(app, "Liftoff")).setup().get()
        screen.click("View the full report")
        screen.click("Copy")
        val clip = (app.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager).primaryClip!!
        assertTrue(clip.getItemAt(0).text.contains("copy me"))
    }

    @Test fun CFG_09_continueClearsTheReportButKeepsTheStreakSoALoopCanBeSeen() {
        seedCrash(); seedCrash()
        assertEquals(2, CrashRecovery.consecutiveCount(app))
        val screen = Robolectric.buildActivity(CrashRecoveryActivity::class.java, CrashRecoveryActivity.intent(app, "Liftoff")).setup().get()
        assertTrue("the loop-gated reset is offered at streak 2", screen.textsOnScreen().any { it.contains("Reset Liftoff's data") })
        screen.click("Continue to Liftoff")
        assertNull(CrashRecovery.pending(app))
        assertEquals(2, CrashRecovery.consecutiveCount(app))
        assertTrue(screen.isFinishing)
    }

    @Test fun CFG_09_aSingleCrashDoesNotOfferTheDataReset() {
        seedCrash()
        val screen = Robolectric.buildActivity(CrashRecoveryActivity::class.java, CrashRecoveryActivity.intent(app, "Liftoff")).setup().get()
        assertFalse(screen.textsOnScreen().any { it.contains("Reset Liftoff's data") })
    }

    @Test fun NFR_17_thePreviewIntentShowsSampleContentWithoutAnyRealCrash() {
        assertNull(CrashRecovery.pending(app))
        val screen = Robolectric.buildActivity(CrashRecoveryActivity::class.java, CrashRecovery.previewIntent(app, "Liftoff")).setup().get()
        assertTrue(screen.textsOnScreen().any { it.contains("Well, that happened.") })
        screen.click("View the full report")
        assertTrue("the sample is labelled as a preview", screen.textsOnScreen().any { it.contains("PREVIEW") })
        assertNull("a preview writes no report", CrashRecovery.pending(app))
        screen.click("Continue to Liftoff")
        assertTrue("Continue is inert in a preview: it only closes", screen.isFinishing)
        assertNull(shadowOf(screen).nextStartedActivity)
    }

    // ---- the host's own few rules --------------------------------------------------------------------------------

    @Test fun CFG_09_theLibrarysPrivateFileNamesTheHostRelyOnStillMatch() {
        seedCrash()
        assertTrue(File(app.filesDir, CrashRecoveryHost.LIBRARY_REPORT).exists())
        assertTrue("the streak file the host reads", File(app.filesDir, CrashRecoveryHost.LIBRARY_STREAK).exists())
        CrashRecovery.captureExitDeath(app, "Liftoff") // nothing to add; just must not throw
    }

    @Test fun CFG_09_theHostKeepsACopyOfTheShownReportSoSafeModeCanOpenItAfterContinue() {
        seedCrash("kept for safe mode")
        CrashRecoveryHost.onProcessStart(app)
        CrashRecovery.clear(app) // what Continue does
        assertNull(CrashRecovery.pending(app))
        assertTrue(CrashRecoveryHost.hasReport(app))
        val main = Robolectric.buildActivity(MainActivity::class.java)
        // reopen puts the report back and asks the library to show its screen from the calling activity
        val host = Robolectric.buildActivity(androidx.activity.ComponentActivity::class.java).setup().get()
        assertTrue(CrashRecoveryHost.reopen(host))
        assertEquals(CrashRecoveryActivity::class.java.name, shadowOf(host).nextStartedActivity.component!!.className)
        assertNotNull(CrashRecovery.pending(app))
        assertTrue(main != null)
    }

    @Test fun CFG_09_withNoReportAtAllTheHostSaysSoAndReopenDoesNothing() {
        assertFalse(CrashRecoveryHost.hasReport(app))
        val host = Robolectric.buildActivity(androidx.activity.ComponentActivity::class.java).setup().get()
        assertFalse(CrashRecoveryHost.reopen(host))
        assertNull(shadowOf(host).nextStartedActivity)
    }

    private fun exit(reason: Int, ts: Long, description: String = "test") = ShadowActivityManager.ApplicationExitInfoBuilder.newBuilder()
        .setReason(reason).setTimestamp(ts).setDescription(description).setPid(1).setProcessName(app.packageName).setPackageUid(1).setRealUid(1).setDefiningUid(1).build()

    @Test fun NFR_09_aNativeCrashInTheExitHistorySurfacesOnTheRecoveryScreenOnce() {
        val shadow = shadowOf(app.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager)
        shadow.addApplicationExitInfo(exit(ApplicationExitInfo.REASON_CRASH_NATIVE, System.currentTimeMillis() - 5_000, "SIGSEGV in libwidget.so"))
        CrashRecoveryHost.onProcessStart(app)
        val p = CrashRecovery.pending(app)
        assertNotNull("the native death became a report", p)
        assertTrue(p!!.fullReport.contains("Native crash") || p.headline.contains("Native crash"))
        CrashRecovery.clear(app)
        CrashRecoveryHost.onProcessStart(app)
        assertNull("surfaced exactly once", CrashRecovery.pending(app))
    }

    @Test fun NFR_09_aJvmCrashIsNotReportedASecondTimeFromItsOwnExitRecord() {
        val shadow = shadowOf(app.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager)
        seedCrash("jvm crash")
        shadow.addApplicationExitInfo(exit(ApplicationExitInfo.REASON_CRASH, System.currentTimeMillis() - 1_000, "crash"))
        CrashRecoveryHost.onProcessStart(app)       // the next process start: the JVM report is pending
        CrashRecovery.clear(app)                    // Continue
        val before = CrashRecovery.consecutiveCount(app)
        CrashRecoveryHost.onProcessStart(app)       // the start after Continue
        assertNull("no second, synthesised report for the same death", CrashRecovery.pending(app))
        assertEquals("and the streak was not bumped twice", before, CrashRecovery.consecutiveCount(app))
    }

    @Test fun CFG_09_theHostReadsTheStreakOnlyWhileItIsRecent() {
        val now = 10_000_000_000L
        File(app.filesDir, "crash_recovery_streak.txt").writeText("3\t${now - 60_000}")
        assertEquals(3, CrashRecoveryHost.liveStreak(app, now))
        File(app.filesDir, "crash_recovery_streak.txt").writeText("3\t${now - CrashRecoveryHost.LIVE_STREAK_MS - 1}")
        assertEquals("older than the live window", 0, CrashRecoveryHost.liveStreak(app, now))
        File(app.filesDir, "crash_recovery_streak.txt").writeText("3\t${now + 5_000}")
        assertEquals("stamped in the future: the clock moved, it is not evidence", 0, CrashRecoveryHost.liveStreak(app, now))
        File(app.filesDir, "crash_recovery_streak.txt").writeText("garbage")
        assertEquals(0, CrashRecoveryHost.liveStreak(app, now))
        File(app.filesDir, "crash_recovery_streak.txt").delete()
        assertEquals(0, CrashRecoveryHost.liveStreak(app, now))
    }

    @Test fun CFG_09_theLiveStreakAgreesWithTheLibraryForACrashJustNow() {
        seedCrash(); seedCrash()
        assertEquals(CrashRecovery.consecutiveCount(app), CrashRecoveryHost.liveStreak(app))
        assertEquals(2, CrashRecoveryHost.liveStreak(app))
    }
}
