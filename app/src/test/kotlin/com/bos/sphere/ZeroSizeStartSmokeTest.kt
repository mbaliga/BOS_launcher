package com.bos.sphere

import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import com.bos.sphere.core.data.HomeLanding
import com.bos.sphere.core.data.LauncherStores
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Wave 18 PART 2 (NFR-09, NFR-13, HOM-23): geometry at a start with a window that is as small as the platform lets Robolectric make one
 * (a split-screen sliver, a window that is still being laid out) must not divide by zero or throw out of a first composition, on every landing.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w2dp-h2dp-mdpi")
class ZeroSizeStartSmokeTest {
    @get:Rule val rule = createEmptyComposeRule()
    private var scenario: ActivityScenario<MainActivity>? = null
    private val uncaught = java.util.concurrent.CopyOnWriteArrayList<Throwable>()
    private var old: Thread.UncaughtExceptionHandler? = null

    @Before fun setUp() { AppSmokeHarness.resetProcess(); old = Thread.getDefaultUncaughtExceptionHandler(); Thread.setDefaultUncaughtExceptionHandler { _, e -> uncaught += e } }
    @After fun tearDown() {
        if (scenario != null) { rule.waitForIdle(); assertFalse(Snapshot.current.hasPendingChanges()) }
        scenario?.close(); scenario = null; AppSmokeHarness.resetProcess(); Thread.setDefaultUncaughtExceptionHandler(old)
    }

    @Test fun NFR_09_everyLandingStartsInATinyWindow() {
        AppSmokeHarness.seedApps("Alpha", "Bravo")
        for (landing in HomeLanding.entries) {
            AppSmokeHarness.resetProcess()
            AppSmokeHarness.configure { it.copy(homeLanding = landing) }
            scenario = AppSmokeHarness.launch(); rule.waitForIdle()
            assertTrue("$landing: $uncaught", uncaught.isEmpty())
            scenario!!.close(); scenario = null
            AppSmokeHarness.restartProcess()
        }
        assertEquals(HomeLanding.entries.last(), LauncherStores.get(AppSmokeHarness.app).prefs.prefs.value.homeLanding)
    }
}
