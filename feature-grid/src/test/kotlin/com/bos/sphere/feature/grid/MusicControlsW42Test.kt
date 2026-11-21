package com.bos.sphere.feature.grid

import android.app.PendingIntent
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import com.bos.sphere.core.data.MediaCommands
import com.bos.sphere.core.data.MediaInfo
import com.bos.sphere.core.data.MediaLogic
import com.bos.sphere.core.data.MediaNow
import com.bos.sphere.core.data.MediaPort
import com.bos.sphere.core.design.HyleTheme
import com.bos.sphere.core.design.ReducedMotionTesting
import com.bos.sphere.core.design.ThemeVariant
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.CoroutineScope
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** A session whose state the test drives; records which route every command took. */
class FakePort(
    override val id: Any, override val packageName: String, override var state: Int, override var actions: Long = 0L,
    override var title: String? = "Song", var controllerOk: Boolean = true, var systemOk: Boolean = true,
) : MediaPort {
    val calls = mutableListOf<String>()
    private var cb: (() -> Unit)? = null
    var registered = false
    override val artist: String? = "Artist"
    override val artwork: android.graphics.Bitmap? = null
    override val sessionActivity: PendingIntent? = null
    override fun register(onChange: () -> Unit) { cb = onChange; registered = true }
    override fun unregister() { cb = null; registered = false }
    /** The player changes state: the framework calls the callback. */
    fun change(state: Int? = null, actions: Long? = null) { state?.let { this.state = it }; actions?.let { this.actions = it }; cb?.invoke() }
    override fun transport(cmd: MediaCommands.Cmd) { calls += "transport:$cmd" }
    override fun controllerKey(keyCode: Int): Boolean { calls += "controller:$keyCode"; return controllerOk }
    override fun systemKey(keyCode: Int): Boolean { calls += "system:$keyCode"; return systemOk }
}

/** W42: MediaNow with fake sessions (Robolectric has no media sessions): picking, re-picking and the command routes. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MediaNowW42Test {
    @After fun reset() { MediaNow.resetForTest() }

    private val P = MediaLogic.STATE_PLAYING
    private val U = MediaLogic.STATE_PAUSED

    @Test fun STR_06_theShownSessionIsThePlayingOneNotTheFirstOrThePaused() {
        val spotify = FakePort("a", "spotify", U); val ytm = FakePort("b", "ytmusic", P)
        MediaNow.sessionsForTest(listOf(spotify, ytm))
        assertEquals("ytmusic", MediaNow.info.value?.packageName)
        assertTrue(MediaNow.info.value!!.playing)
    }

    @Test fun STR_06_aPausedSessionIsStillShownAndPlayCanResumeIt() {
        val p = FakePort("a", "spotify", U, MediaCommands.ACTION_PLAY)
        MediaNow.sessionsForTest(listOf(p))
        assertEquals(false, MediaNow.info.value?.playing)
        MediaNow.playPause()
        assertEquals(listOf("transport:Play"), p.calls)
    }

    @Test fun STR_06_aSecondPlayerStartingTakesOverWithoutAnySessionListChange() {
        val a = FakePort("a", "spotify", P); val b = FakePort("b", "ytmusic", U)
        MediaNow.sessionsForTest(listOf(a, b))
        assertEquals("spotify", MediaNow.info.value?.packageName)
        a.change(state = U); b.change(state = P) // the system sends only playback-state callbacks, no list change
        assertEquals("ytmusic", MediaNow.info.value?.packageName)
        MediaNow.playPause() // playing -> pause, sent to the one now shown
        assertTrue(b.calls.isNotEmpty() && a.calls.isEmpty())
    }

    @Test fun STR_06_everyTapRereadsTheSessionListSoACommandNeverGoesToADeadSession() {
        val old = FakePort("old", "spotify", P, MediaCommands.ACTION_SKIP_TO_NEXT)
        var live = listOf<MediaPort>(old)
        MediaNow.sessionsForTest(live) { live }
        val restarted = FakePort("new", "spotify", P, MediaCommands.ACTION_SKIP_TO_NEXT) // the app restarted: a new session, the listener never told us
        live = listOf(restarted)
        MediaNow.next()
        assertEquals(emptyList<String>(), old.calls)
        assertEquals(listOf("transport:Next"), restarted.calls)
        assertTrue(!old.registered && restarted.registered)
    }

    @Test fun STR_06_aSessionThatEndsLeavesNoLineAndNoWatcher() {
        val a = FakePort("a", "spotify", P)
        MediaNow.sessionsForTest(listOf(a))
        a.change(state = MediaLogic.STATE_STOPPED)
        assertNull(MediaNow.info.value)
        MediaNow.sessionsForTest(emptyList())
        assertTrue(!a.registered)
    }

    @Test fun STR_06_whenTheActionIsNotAdvertisedTheMediaButtonEventIsSentInstead() {
        val p = FakePort("a", "browser", P, MediaCommands.ACTION_PAUSE)
        MediaNow.sessionsForTest(listOf(p))
        MediaNow.next(); MediaNow.previous(); MediaNow.playPause()
        assertEquals(listOf("controller:87", "controller:88", "transport:Pause"), p.calls)
    }

    @Test fun STR_06_theSystemKeyIsTheLastResortAndAFullFailureIsReported() {
        val p = FakePort("a", "browser", P, MediaCommands.ACTION_PAUSE, controllerOk = false)
        MediaNow.sessionsForTest(listOf(p))
        val got = mutableListOf<String>()
        val scope = CoroutineScope(Dispatchers.Unconfined)
        MediaNow.failures.onEach { got += it }.launchIn(scope)
        MediaNow.next()
        assertEquals(listOf("controller:87", "system:87"), p.calls)
        assertTrue("the system key was sent, so no failure yet", got.isEmpty())
        p.systemOk = false
        MediaNow.next()
        assertEquals(listOf("browser"), got)
        scope.cancel()
    }

    @Test fun HOM_23_aSkipControlTheSessionDoesNotAdvertiseIsMarkedUnavailable() {
        MediaNow.sessionsForTest(listOf(FakePort("a", "podcast", P, MediaCommands.ACTION_PLAY or MediaCommands.ACTION_PAUSE)))
        val i = MediaNow.info.value!!
        assertEquals(false, i.canNext); assertEquals(false, i.canPrev)
        MediaNow.sessionsForTest(listOf(FakePort("b", "music", P, MediaCommands.ACTION_SKIP_TO_NEXT)))
        assertEquals(true, MediaNow.info.value!!.canNext); assertEquals(false, MediaNow.info.value!!.canPrev)
    }

    @Test fun STR_06_titleFollowsMetadataChanges() {
        val p = FakePort("a", "spotify", P)
        MediaNow.sessionsForTest(listOf(p))
        assertEquals("Song", MediaNow.info.value?.title)
        p.title = "Next song"; p.change()
        assertEquals("Next song", MediaNow.info.value?.title)
    }
}

/** W42: the pop-in controls take taps from their first frame, call the right function, dim what the session cannot do and hold the auto-hide while touched. */
@RunWith(RobolectricTestRunner::class)
class MusicControlsW42UiTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @After fun reset() {
        Snapshot.withMutableSnapshot { HyleTheme.apply(ThemeVariant.Dark, 0xFF8E7BFF) }
        ReducedMotionTesting.force(null)
        runCatching { LauncherInput.focus.nodes().map { it.id }.forEach(LauncherInput.focus::unregister) }
    }

    private val base = MediaInfo("Song", "Artist", "pkg", playing = true, canPrev = true, canNext = true)
    private val clicks = mutableListOf<String>()
    private var media by mutableStateOf<MediaInfo?>(base)

    private fun host(hideSeconds: Int = 4) = rule.setContent {
        Box(Modifier.fillMaxSize()) {
            FixedHomeChrome(media, true, hideSeconds, { clicks += "prev" }, { clicks += "pp" }, { clicks += "next" }, dark = true)
        }
    }
    private fun line() = rule.onNodeWithContentDescription("Now playing", substring = true)
    private fun notchCount() = rule.onAllNodesWithTag("glance.notch", useUnmergedTree = true).fetchSemanticsNodes().size
    private fun ctl(tag: String) = rule.onNodeWithTag(tag, useUnmergedTree = true)

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun HOM_23_aTapOnAControlInTheFirstFrameAfterTheLineIsTappedIsNotDropped() {
        host()
        rule.mainClock.autoAdvance = false
        line().performClick()
        var frames = 0
        while (notchCount() == 0 && frames++ < 5) rule.mainClock.advanceTimeByFrame() // the first frame the notch exists, mid-spring
        assertTrue("the notch appeared within a few frames", notchCount() == 1 && frames <= 3)
        val early = ctl("glance.playpause").fetchSemanticsNode().boundsInRoot
        ctl("glance.playpause").performClick()
        assertEquals(listOf("pp"), clicks)
        rule.mainClock.advanceTimeBy(800)
        val settled = ctl("glance.playpause").fetchSemanticsNode().boundsInRoot
        assertEquals("the hit area is where it will rest, not riding the spring", settled, early)
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun HOM_23_eachControlCallsItsOwnFunctionAndPausedStillShowsAndAllowsPlay() {
        media = base.copy(playing = false)
        host(); line().performClick(); rule.mainClock.advanceTimeBy(600)
        ctl("glance.prev").performClick(); ctl("glance.playpause").performClick(); ctl("glance.next").performClick()
        assertEquals(listOf("prev", "pp", "next"), clicks)
        rule.onNodeWithContentDescription("Pause", useUnmergedTree = true).assertExists() // paused showed Play; the tap flipped it at once
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun HOM_23_aControlTheSessionCannotDoIsDisabledAndDoesNothing() {
        media = base.copy(canNext = false)
        host(); line().performClick(); rule.mainClock.advanceTimeBy(600)
        ctl("glance.next").assertIsNotEnabled(); ctl("glance.prev").assertIsEnabled(); ctl("glance.playpause").assertIsEnabled()
        ctl("glance.next").performClick()
        assertTrue(clicks.isEmpty())
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun HOM_23_thePlayPauseGlyphFlipsAtOnceOnATapAndTheReportedStateConfirmsIt() {
        host(); line().performClick(); rule.mainClock.advanceTimeBy(600)
        rule.onNodeWithContentDescription("Pause", useUnmergedTree = true).assertExists()
        ctl("glance.playpause").performClick() // the tap itself flips the glyph, before the player has said anything
        rule.mainClock.advanceTimeBy(400)
        rule.onNodeWithContentDescription("Play", useUnmergedTree = true).assertExists()
        rule.onAllNodesWithContentDescription("Pause", useUnmergedTree = true).assertCountEquals(0)
        media = base.copy(playing = false); rule.waitForIdle() // the player confirms: it stays
        rule.mainClock.advanceTimeBy(GlanceNotch.PENDING_MS + 500)
        rule.onNodeWithContentDescription("Play", useUnmergedTree = true).assertExists()
        ctl("glance.playpause").performClick() // and back
        rule.mainClock.advanceTimeBy(400)
        rule.onNodeWithContentDescription("Pause", useUnmergedTree = true).assertExists()
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun HOM_23_aTapThePlayerNeverAnswersShowsTheOldGlyphAgainAfterAMoment() {
        host(10); line().performClick(); rule.mainClock.advanceTimeBy(600)
        ctl("glance.playpause").performClick()
        rule.mainClock.advanceTimeBy(300)
        rule.onNodeWithContentDescription("Play", useUnmergedTree = true).assertExists()
        rule.mainClock.advanceTimeBy(GlanceNotch.PENDING_MS + 500) // no state change was reported: the glyph tells the truth again
        rule.onNodeWithContentDescription("Pause", useUnmergedTree = true).assertExists()
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun HOM_23_aTouchOnTheNotchRestartsTheAutoHideAndTheControlsGoWhenItIsLeftAlone() {
        host(4)
        line().performClick(); rule.mainClock.advanceTimeBy(600)
        assertEquals(1, notchCount())
        rule.mainClock.advanceTimeBy(3000)
        ctl("glance.notch").performTouchInput { down(center); up() } // a touch just before it would hide
        rule.mainClock.advanceTimeBy(3000)
        assertEquals("still shown: the touch restarted the 4 s", 1, notchCount())
        rule.mainClock.advanceTimeBy(2000)
        assertEquals("hidden after being left alone", 0, notchCount())
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun HOM_23_theControlsStayWhileAFingerIsHeldOnThemAndTheyAreAtLeast48Dp() {
        host(4)
        line().performClick(); rule.mainClock.advanceTimeBy(600)
        ctl("glance.playpause").performTouchInput { down(center) }
        rule.mainClock.advanceTimeBy(9000)
        assertEquals(1, notchCount())
        ctl("glance.playpause").performTouchInput { up() }
        val d = rule.activity.resources.displayMetrics.density
        for (t in listOf("glance.prev", "glance.playpause", "glance.next")) {
            val b = ctl(t).fetchSemanticsNode().boundsInRoot
            assertTrue("$t >= 48dp", b.width / d >= 47.9f && b.height / d >= 47.9f)
        }
    }

    @Test @Config(sdk = [35]) fun HOM_23_theHotspotCornersNeverOverlapTheThreeControlsOnNarrowPhones() {
        for (w in listOf(320f, 360f, 411f, 600f)) {
            val notchW = GlanceNotch.widthDp(w)
            val left = (w - notchW) / 2f + GlanceNotch.FLARE_DP // first control's left edge region start
            val right = (w + notchW) / 2f - GlanceNotch.FLARE_DP
            assertTrue("width $w: left hotspot (64 dp) clear of the controls", left >= 64f)
            assertTrue("width $w: right hotspot clear of the controls", w - right >= 64f)
        }
    }
}
