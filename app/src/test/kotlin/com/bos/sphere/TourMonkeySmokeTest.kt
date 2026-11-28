package com.bos.sphere

import android.view.KeyEvent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.click
import androidx.compose.ui.test.down
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.moveBy
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.up
import com.bos.sphere.core.data.LauncherStores
import com.bos.sphere.feature.grid.LauncherInput
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.random.Random

/**
 * TOUR, part 4 (wave 22): a seeded random walk. Three seeds, 300 steps each, over taps, long presses, swipes from the four edges, letters, arrows, Enter,
 * Esc, Back, every Ctrl combo of the shortcut registry and rotations. It asserts only that nothing throws, that the activity stays alive and that no
 * single step hangs the test clock (a wall-clock budget per step). Esc and Back are not sent to a plain home: on a device the system handles that
 * (Android 12+ moves a root launcher to the back), while Robolectric emulates the old "finish", which is not a launcher defect.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h720dp-xhdpi")
class TourMonkeySmokeTest : TourBase() {
    private val letters = (KeyEvent.KEYCODE_A..KeyEvent.KEYCODE_Z).toList() + KeyEvent.KEYCODE_SPACE + (KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9)
    private val arrows = listOf(KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT)
    private var landscape = false
    private var last = ""
    private val visited = mutableSetOf<Surface>()
    private fun act(what: String, f: () -> Unit) { last = what; f() }

    private fun seedBusyish(busy: Boolean) {
        if (busy) { TourSeed.apps(60); TourSeed.history("Alpha", "Bravo", "Tour 007"); TourSeed.media(); TourSeed.recordSends(); TourSeed.notifications("Alpha" to "One", "Bravo" to "Two"); TourSeed.group("Work", "Alpha") }
        else AppSmokeHarness.seedApps("Alpha", "Bravo", "Camera", "Delta")
        AppSmokeHarness.configureFresh()
    }

    private fun topRoot() = rule.onAllNodes(isRoot()).onLast()
    private fun homeRoot() = rule.onAllNodes(isRoot()).onFirst()

    /** Nothing but the home, or a modal sheet (a dialog window: on a device it takes Esc itself, here the key goes to the activity, which would only be the old "finish" fallback). */
    private fun plainHome() = open().all { it == Surface.Home || it == Surface.QuickSheet }

    private fun step(rnd: Random) {
        // EditMode (Ctrl+E) raises a Toast from the input hub's collector; under the Compose test dispatcher that runs off a Looper thread (a harness limit, docs/decisions/tourSmoke.md)
        val chords = LauncherInput.registry.all().filter { it.first != com.bos.sphere.core.input.LauncherCommand.EditMode }.mapNotNull { it.second }
        when (rnd.nextInt(100)) {
            in 0..29 -> { val fx = rnd.nextFloat(); val fy = rnd.nextFloat(); act("tap ($fx, $fy) over ${open()}") { topRoot().performTouchInput { click(Offset(fx * width, fy * height)) } } }
            in 30..33 -> if (open().contains(Surface.Radar)) Unit /* a long press on Radar's field raises the platform text magnifier, which needs a real surface */ else { val fx = rnd.nextFloat(); val fy = rnd.nextFloat(); act("long press ($fx, $fy) over ${open()}") { topRoot().performTouchInput { longClick(Offset(fx * width, fy * height)) } } }
            in 34..47 -> { // a swipe from one of the four edges (or a random drag)
                val edge = rnd.nextInt(5); val f = 0.15f + rnd.nextFloat() * 0.7f
                homeRoot().performTouchInput {
                    val (a, d) = when (edge) {
                        0 -> Offset(6f, height * f) to Offset(1f, 0f); 1 -> Offset(width - 6f, height * f) to Offset(-1f, 0f)
                        2 -> Offset(width * f, 6f) to Offset(0f, 1f); 3 -> Offset(width * f, height - 6f) to Offset(0f, -1f)
                        else -> Offset(width * f, height * f) to Offset(rnd.nextFloat() - 0.5f, rnd.nextFloat() - 0.5f)
                    }
                    down(a); moveBy(d * 60f); moveBy(d * 200f); moveBy(d * 300f); up()
                }
            }
            in 48..62 -> letters.random(rnd).let { k -> act("letter $k") { AppSmokeHarness.press(sc, k) } }
            in 63..72 -> arrows.random(rnd).let { k -> act("arrow $k") { AppSmokeHarness.press(sc, k) } }
            in 73..76 -> act("enter") { AppSmokeHarness.press(sc, KeyEvent.KEYCODE_ENTER) }
            in 77..80 -> if (!plainHome()) act("esc over ${open()}") { AppSmokeHarness.press(sc, KeyEvent.KEYCODE_ESCAPE) }
            in 81..85 -> if (AppSmokeHarness.hasEnabledBackCallback(sc)) act("back over ${open()}") { AppSmokeHarness.back(sc) }
            in 86..95 -> chords.random(rnd).let { c -> if (c.keyCode != KeyEvent.KEYCODE_ESCAPE || !plainHome()) act("chord $c over ${open()}") { AppSmokeHarness.press(sc, c.keyCode, ctrl = c.ctrl, shift = c.shift) } }
            else -> { landscape = !landscape; RuntimeEnvironment.setQualifiers(if (landscape) "+w720dp-h360dp-land-xhdpi" else "+w360dp-h720dp-port-xhdpi") }
        }
        // one short advance per step: every frame of the Compose test clock walks the global snapshot, which gets slower with every live state object
        org.robolectric.shadows.ShadowLooper.idleMainLooper(); androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications(); rule.mainClock.advanceTimeBy(120)
    }

    private fun walk(seed: Int, busy: Boolean, steps: Int = 300) {
        visited.clear(); seedBusyish(busy); launch()
        val rnd = Random(seed)
        for (i in 1..steps) {
            val t0 = System.nanoTime()
            try { step(rnd); visited += open() } catch (e: Throwable) { throw AssertionError("seed $seed step $i threw after [$last] (open ${runCatching { open() }.getOrNull()}): $e", e) }
            val ms = (System.nanoTime() - t0) / 1_000_000
            assertTrue("seed $seed step $i took $ms ms: the test clock hung", ms < 30_000)
            var state: androidx.lifecycle.Lifecycle.State? = null; var finishing = false
            sc.onActivity { state = it.lifecycle.currentState; finishing = it.isFinishing }
            assertTrue("seed $seed step $i: the activity died after [$last] (finishing=$finishing state=$state)", !finishing && state!!.isAtLeast(androidx.lifecycle.Lifecycle.State.CREATED))
        }
        assertTrue("seed $seed: the walk reached several surfaces, not just the home (reached $visited)", visited.size >= 4)
        // and the app still answers: back to a plain state with Back and Esc, then a key opens something
        repeat(4) { if (!plainHome() && AppSmokeHarness.hasEnabledBackCallback(sc)) back() }
        check("after the walk")
    }

    @Test fun NFR_19_monkeySeed1OnAFreshState() = walk(1, busy = false)
    @Test fun INP_05_monkeySeed2OnABusyState() = walk(2, busy = true)
    @Test fun INP_08_monkeySeed3OnAFreshState() = walk(3, busy = false)
}
