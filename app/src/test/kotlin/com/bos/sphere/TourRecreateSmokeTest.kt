package com.bos.sphere

import android.view.KeyEvent
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.bos.sphere.core.data.CrashLoopGuard
import com.bos.sphere.core.data.HomeLanding
import com.bos.sphere.core.data.LauncherStores
import com.bos.sphere.core.data.OpenSurface
import com.bos.sphere.core.data.OpenSurfaceStore
import dev.aarso.crashrecovery.CrashRecovery
import dev.aarso.crashrecovery.CrashRecoveryActivity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * TOUR, part 3 (wave 22): process recreate (state restored when the open-surface setting is on, home otherwise), an activity recreate with each
 * surface open, the Lab crash row's path to the recovery screen and Continue, and safe mode on a seeded two-crash state with Restore snapshot and Carry on.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h720dp-xhdpi")
class TourRecreateSmokeTest : TourBase() {
    private val app get() = AppSmokeHarness.app
    private fun wipeCrashFiles() {
        File(app.filesDir, "").listFiles { f -> f.name.startsWith("crash_recovery") }?.forEach { it.delete() }
        app.getSharedPreferences("crash_loop", android.content.Context.MODE_PRIVATE).edit().clear().commit()
    }
    @After fun wipe() { wipeCrashFiles() }

    private fun startSelected(restore: Boolean) {
        AppSmokeHarness.seedApps("Alpha", "Bravo", "Camera", "Delta")
        AppSmokeHarness.configure { it.copy(appsOnHome = com.bos.sphere.core.data.AppsOnHome.Everything, restoreOpenSurfaces = restore) }
        launch()
    }
    private fun killAndRestart() { sc.close(); scenario = null; AppSmokeHarness.restartProcess(); launch() }

    @Test fun NFR_11_tourProcessRecreateRestoresTheDrawerWhenTheSettingIsOn() {
        startSelected(restore = true)
        key(KeyEvent.KEYCODE_D, ctrl = true); check("drawer", Surface.Drawer)
        assertEquals(OpenSurface.Drawer, OpenSurfaceStore.get(app).last())
        killAndRestart(); check("after the process restart", Surface.Drawer)
        back(); check("closed", Surface.Home)
        key(KeyEvent.KEYCODE_SLASH, ctrl = true); check("radar", Surface.Radar)
        killAndRestart(); check("radar restored", Surface.Radar)
    }

    @Test fun NFR_11_tourProcessRecreateGoesHomeWhenTheSettingIsOffWhateverWasOpen() {
        startSelected(restore = false)
        for (act in listOf<() -> Unit>({ key(KeyEvent.KEYCODE_D, ctrl = true) }, { key(KeyEvent.KEYCODE_SLASH, ctrl = true) }, { key(KeyEvent.KEYCODE_N, ctrl = true) }, { key(KeyEvent.KEYCODE_COMMA, ctrl = true) })) {
            act(); assertFalse("something is open", open().contains(Surface.Home))
            killAndRestart(); check("home after a restart", Surface.Home)
        }
    }

    @Test fun NFR_11_tourAnActivityRecreateWithEachSurfaceOpenDoesNotCrashAndKeepsTheHome() {
        startSelected(restore = false)
        for ((name, act) in listOf<Pair<String, () -> Unit>>(
            "drawer" to { key(KeyEvent.KEYCODE_D, ctrl = true) }, "radar" to { key(KeyEvent.KEYCODE_SLASH, ctrl = true) },
            "launchpad" to { key(KeyEvent.KEYCODE_N, ctrl = true) }, "recents" to { key(KeyEvent.KEYCODE_R, ctrl = true) },
            "ground control" to { key(KeyEvent.KEYCODE_COMMA, ctrl = true) })) {
            act(); assertFalse("$name opened", open().contains(Surface.Home))
            sc.recreate(); rule.waitForIdle(); settle()
            check("recreate with $name open")
            assertFalse("a recreate is not taken for a crash loop", CrashLoopGuard.safeMode)
            // whatever came back, Back and Esc bring the home back without a crash
            repeat(3) { if (!open().contains(Surface.Home)) { back(); key(ESC) } }
            check("home after $name", Surface.Home)
        }
    }

    @Test fun CFG_09_tourTheLabCrashRowLeadsToTheRecoveryScreenAndContinueGoesHome() {
        AppSmokeHarness.seedApps("Alpha", "Bravo"); AppSmokeHarness.configure()
        // what the Lab row does after its second tap: an uncaught error whose report the shared handler stores (the JVM itself must not die here)
        CrashRecovery.captureInitError(app, "Liftoff", com.bos.sphere.ui.LabCrash.testError())
        val main = Robolectric.buildActivity(MainActivity::class.java).create().get()
        assertTrue("MainActivity hands over to the recovery screen and finishes", main.isFinishing)
        val started = shadowOf(main).nextStartedActivity
        assertEquals(CrashRecoveryActivity::class.java.name, started.component!!.className)
        val screen = Robolectric.buildActivity(CrashRecoveryActivity::class.java, CrashRecoveryActivity.intent(app, "Liftoff")).setup().get()
        val on = texts(screen.window.decorView).joinToString("\n")
        assertTrue(on, on.contains("Continue to Liftoff"))
        click(screen, "View the full report")
        assertTrue("the Lab crash is named in the report", texts(screen.window.decorView).any { it.contains("Crash on purpose") })
        click(screen, "Continue to Liftoff")
        assertNull("Continue clears the pending report", CrashRecovery.pending(app))
        launch(); check("home after Continue", Surface.Home)
    }

    @Test fun CFG_09_tourThePreviewIntentShowsTheRecoveryScreenWithoutACrash() {
        val screen = Robolectric.buildActivity(CrashRecoveryActivity::class.java, CrashRecovery.previewIntent(app, "Liftoff")).setup().get()
        assertTrue(texts(screen.window.decorView).any { it.contains("Continue to Liftoff") })
        click(screen, "Continue to Liftoff"); assertTrue(screen.isFinishing)
        assertNull(CrashRecovery.pending(app))
    }

    private fun texts(root: android.view.View, out: MutableList<String> = mutableListOf()): List<String> {
        if (root is android.widget.TextView) out += root.text.toString()
        if (root is android.view.ViewGroup) for (i in 0 until root.childCount) texts(root.getChildAt(i), out)
        return out
    }
    private fun click(a: android.app.Activity, label: String) {
        fun find(v: android.view.View): android.widget.TextView? = if (v is android.widget.TextView && v.text.toString().contains(label)) v else (v as? android.view.ViewGroup)?.let { g -> (0 until g.childCount).firstNotNullOfOrNull { find(g.getChildAt(it)) } }
        var t: android.view.View? = find(a.window.decorView)
        while (t != null && !t.hasOnClickListeners()) t = t.parent as? android.view.View
        assertNotNull("a clickable $label", t); t!!.performClick()
    }

    private fun enterSafeMode() {
        AppSmokeHarness.seedApps("Alpha", "Bravo", "Camera")
        AppSmokeHarness.configure()
        com.bos.sphere.core.data.StateService.get(app).snapshot("tour", force = true)
        launch(); AppSmokeHarness.closeLikeAKill(sc); scenario = null
        AppSmokeHarness.resetProcess(); AppSmokeHarness.previousProcessExit(android.app.ApplicationExitInfo.REASON_CRASH)
        launch(); AppSmokeHarness.closeLikeAKill(sc); scenario = null
        AppSmokeHarness.resetProcess(); AppSmokeHarness.previousProcessExit(android.app.ApplicationExitInfo.REASON_CRASH)
        launch()
        assertTrue("two counted crashes put this start in safe mode", CrashLoopGuard.safeMode)
    }

    @Test fun CFG_09_tourSafeModeOffersTheAppsRestoreSnapshotAndCarryOn() {
        enterSafeMode()
        assertTrue("the safe mode screen", textShown("Safe mode")); rotateAndCheckSafe()
        rule.onNodeWithText("Carry on").performClick(); settle(); waitFor("the normal home after Carry on") { !textShown("Safe mode") }
        assertFalse("Carry on clears the guard for the next start", textShown("Safe mode"))
    }

    @Test fun CFG_09_tourSafeModeRestoreSnapshotReturnsToTheHome() {
        enterSafeMode()
        rule.onNodeWithText("Restore last snapshot", substring = true).performClick(); settle(); waitFor("the home after Restore") { !textShown("Safe mode") }
        assertFalse(CrashLoopGuard.safeMode && textShown("Safe mode"))
    }

    private fun rotateAndCheckSafe() {
        org.robolectric.RuntimeEnvironment.setQualifiers("+w720dp-h360dp-land-xhdpi"); rule.waitForIdle(); settle(400)
        assertTrue("safe mode survives a rotation", textShown("Safe mode"))
        org.robolectric.RuntimeEnvironment.setQualifiers("+w360dp-h720dp-port-xhdpi"); rule.waitForIdle(); settle(400)
        assertTrue(textShown("Safe mode"))
    }
}
