package com.bos.sphere.feature.grid

import com.bos.sphere.core.data.MediaCommands
import com.bos.sphere.core.data.MediaLogic
import com.bos.sphere.core.data.MediaNow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * W60 ("the music controls are flaky", a device report after wave 42; docs/decisions/mediaReliability.md): the confirm-or-escalate ladder.
 * Wave 42 made `MediaCommands.execute()` try Transport, then the controller key, then the system key — but counted the ONE-WAY Transport
 * call as success the instant the binder accepted it, so a player that silently ignored it (not focused, a stale remote stub, a race with
 * another command) never fell through to the routes that actually work. These tests use a [FakeMediaScheduler] so "no confirmation arrived
 * within the window" is simulated deterministically (advancing a fake clock), never by sleeping a real thread.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MediaReliabilityW60Test {
    @After fun reset() { MediaNow.resetForTest() }

    private val P = MediaLogic.STATE_PLAYING
    private val U = MediaLogic.STATE_PAUSED

    private fun failures(): Pair<MutableList<String>, CoroutineScope> {
        val got = mutableListOf<String>()
        val scope = CoroutineScope(Dispatchers.Unconfined)
        MediaNow.failures.onEach { got += it }.launchIn(scope)
        return got to scope
    }

    @Test fun STR_06_aCommandThatSwallowsAtEveryRouteEscalatesThroughAllThreeThenFails() {
        val sched = FakeMediaScheduler()
        MediaNow.schedulerForTest(sched)
        // Every route is "accepted" (true/no exception) but the fake never calls the registered callback: the real-world failure mode.
        val p = FakePort("a", "evilplayer", P, MediaCommands.ACTION_SKIP_TO_NEXT)
        MediaNow.sessionsForTest(listOf(p))
        val (got, scope) = failures()

        MediaNow.next()
        assertEquals("Transport tried first since Next is advertised", listOf("transport:Next"), p.calls)
        assertTrue(got.isEmpty())

        sched.advanceBy(MediaCommands.CONFIRM_MS) // Transport's window: total silence
        assertEquals("escalated to the controller key", listOf("transport:Next", "controller:87"), p.calls)
        assertTrue("still waiting on the controller key's own window", got.isEmpty())

        sched.advanceBy(MediaCommands.CONFIRM_MS) // controller key's window: also silent
        assertEquals("escalated to the system key, the last rung", listOf("transport:Next", "controller:87", "system:87"), p.calls)
        assertTrue(got.isEmpty())

        sched.advanceBy(MediaCommands.CONFIRM_MS) // every route exhausted, none confirmed
        assertEquals(listOf("evilplayer"), got)
        scope.cancel()
    }

    @Test fun STR_06_aConfirmationOnTheSecondRouteStopsTheLadderFromTryingTheThird() {
        val sched = FakeMediaScheduler()
        MediaNow.schedulerForTest(sched)
        val p = FakePort("a", "lazyplayer", P, MediaCommands.ACTION_SKIP_TO_NEXT)
        p.onCall = { route -> if (route == "controller") p.change() } // the session answers only when the controller key reaches it
        MediaNow.sessionsForTest(listOf(p))
        val (got, scope) = failures()

        MediaNow.next()
        assertEquals(listOf("transport:Next"), p.calls)
        sched.advanceBy(MediaCommands.CONFIRM_MS) // Transport's window: silent, escalates
        assertEquals("the controller key confirmed, so the system key is never tried", listOf("transport:Next", "controller:87"), p.calls)
        sched.advanceBy(MediaCommands.CONFIRM_MS * 3) // even waiting well past every remaining window, nothing more is sent
        assertEquals(listOf("transport:Next", "controller:87"), p.calls)
        assertTrue("confirmed, not failed", got.isEmpty())
        scope.cancel()
    }

    @Test fun STR_06_playConfirmsOnlyOnceIsPlayingTrueAndPauseOnlyOnceItIsFalse() {
        val sched = FakeMediaScheduler()
        MediaNow.schedulerForTest(sched)
        val p = FakePort("a", "podcast", U, MediaCommands.ACTION_PLAY)
        MediaNow.sessionsForTest(listOf(p))
        val (got, scope) = failures()

        MediaNow.playPause() // paused -> Play
        p.change(state = MediaLogic.STATE_ERROR) // a callback arrived, but still not playing: NOT a confirmation
        sched.advanceBy(MediaCommands.CONFIRM_MS) // Transport's window ran out with no real confirmation: escalate
        assertEquals("the non-confirming callback did not stop the escalation", listOf("transport:Play", "controller:126"), p.calls)
        p.change(state = MediaLogic.STATE_PLAYING) // now it genuinely flipped
        sched.advanceBy(MediaCommands.CONFIRM_MS * 3)
        assertEquals("confirmed at the controller key; the system key was never tried", listOf("transport:Play", "controller:126"), p.calls)
        assertTrue(got.isEmpty())
        scope.cancel()
    }

    @Test fun STR_06_nextOrPreviousConfirmsOnAnyCallbackAtAllSinceTrackIdentityCannotBeVerified() {
        val sched = FakeMediaScheduler()
        MediaNow.schedulerForTest(sched)
        val p = FakePort("a", "radio", P, MediaCommands.ACTION_SKIP_TO_PREVIOUS)
        MediaNow.sessionsForTest(listOf(p))
        val (got, scope) = failures()

        MediaNow.previous()
        assertEquals(listOf("transport:Previous"), p.calls)
        p.change() // any callback, even with the state unchanged: evidence of life, not silence
        sched.advanceBy(MediaCommands.CONFIRM_MS * 3)
        assertEquals("nothing further was sent: that one callback already confirmed it", listOf("transport:Previous"), p.calls)
        assertTrue(got.isEmpty())
        scope.cancel()
    }

    @Test fun STR_06_aRepeatOfTheSameTapWithinTheDebounceWindowIsDroppedWhileTheFirstIsStillPending() {
        val sched = FakeMediaScheduler()
        MediaNow.schedulerForTest(sched)
        val p = FakePort("a", "playlist", P, MediaCommands.ACTION_SKIP_TO_NEXT)
        MediaNow.sessionsForTest(listOf(p))

        MediaNow.next()
        assertEquals(listOf("transport:Next"), p.calls)
        MediaNow.next() // the same tap bouncing (a known small-target double-fire), well inside the debounce window
        assertEquals("the bounce sent nothing new", listOf("transport:Next"), p.calls)
    }

    @Test fun STR_06_aDeliberateSecondTapStillFiresOnceTheFirstHasBeenConfirmed() {
        val sched = FakeMediaScheduler()
        MediaNow.schedulerForTest(sched)
        val p = FakePort("a", "playlist", P, MediaCommands.ACTION_SKIP_TO_NEXT)
        MediaNow.sessionsForTest(listOf(p))

        MediaNow.next()
        p.change() // the first Next is confirmed right away (any callback, for Next)
        MediaNow.next() // a deliberate second Next, even though the clock has not moved at all
        assertEquals("two real taps, two commands", listOf("transport:Next", "transport:Next"), p.calls)
    }

    @Test fun STR_06_theDebounceWindowItselfExpiresSoATapCanNeverGetPermanentlyStuck() {
        val sched = FakeMediaScheduler()
        MediaNow.schedulerForTest(sched)
        val p = FakePort("a", "playlist", P, MediaCommands.ACTION_SKIP_TO_NEXT)
        MediaNow.sessionsForTest(listOf(p))

        MediaNow.next()
        sched.advanceBy(MediaCommands.DEBOUNCE_MS) // no confirmation yet, but the (short) debounce window alone has elapsed
        MediaNow.next()
        assertEquals("a repeat past the debounce window is sent fresh even with no confirmation", listOf("transport:Next", "transport:Next"), p.calls)
    }

    @Test fun STR_06_aSessionThatDisappearsMidLadderFailsAtOnceInsteadOfWaitingOutTheTimer() {
        val sched = FakeMediaScheduler()
        MediaNow.schedulerForTest(sched)
        val p = FakePort("a", "fragile", P, MediaCommands.ACTION_SKIP_TO_NEXT)
        MediaNow.sessionsForTest(listOf(p))
        val (got, scope) = failures()

        MediaNow.next()
        assertEquals(listOf("transport:Next"), p.calls)
        MediaNow.sessionsForTest(emptyList()) // the session vanished (the app died) before it ever answered
        assertEquals("the ladder gave up the moment the session was gone, not after a further wait", listOf("fragile"), got)
        scope.cancel()
    }

    /** STR-06 item 4: `playPause()`/`next()`/`previous()` already re-read the session list (`refresh()`) before reading `current`, so a
     * stale reference from before an app restart is never the target — checked here rather than assumed. */
    @Test fun STR_06_playPauseRetargetsToTheFreshSessionWhenTheOldOneWasReplacedWithoutAListChangeNotification() {
        val stale = FakePort("old", "spotify", U, MediaCommands.ACTION_PLAY)
        var live = listOf<com.bos.sphere.core.data.MediaPort>(stale)
        MediaNow.sessionsForTest(live) { live }
        val restarted = FakePort("new", "spotify", U, MediaCommands.ACTION_PLAY) // the app restarted with a new session token
        live = listOf(restarted)
        MediaNow.playPause()
        assertEquals(emptyList<String>(), stale.calls)
        assertEquals(listOf("transport:Play"), restarted.calls)
    }
}
