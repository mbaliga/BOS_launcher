package com.bos.sphere.core.data

import android.media.session.PlaybackState
import android.view.KeyEvent
import com.bos.sphere.core.data.MediaCommands.Cmd
import com.bos.sphere.core.data.MediaCommands.Route
import com.bos.sphere.core.data.MediaLogic.Candidate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** W42 (STR-06, HOM-23): which session the music line follows, and how a command reaches it. Pure JVM. */
class MediaCommandsW42Test {
    private val P = MediaLogic.STATE_PLAYING
    private val U = MediaLogic.STATE_PAUSED
    private val S = MediaLogic.STATE_STOPPED
    private fun pick(vararg c: Pair<Int, String>, prefer: String? = null) = MediaLogic.chooseSession(c.map { Candidate(it.first, it.second) }, prefer)

    @Test fun constantsMatchTheFramework() {
        assertEquals(PlaybackState.ACTION_STOP, MediaCommands.ACTION_STOP)
        assertEquals(PlaybackState.ACTION_PAUSE, MediaCommands.ACTION_PAUSE)
        assertEquals(PlaybackState.ACTION_PLAY, MediaCommands.ACTION_PLAY)
        assertEquals(PlaybackState.ACTION_PLAY_PAUSE, MediaCommands.ACTION_PLAY_PAUSE)
        assertEquals(PlaybackState.ACTION_SKIP_TO_NEXT, MediaCommands.ACTION_SKIP_TO_NEXT)
        assertEquals(PlaybackState.ACTION_SKIP_TO_PREVIOUS, MediaCommands.ACTION_SKIP_TO_PREVIOUS)
        assertEquals(KeyEvent.KEYCODE_MEDIA_PLAY, MediaCommands.KEYCODE_MEDIA_PLAY)
        assertEquals(KeyEvent.KEYCODE_MEDIA_PAUSE, MediaCommands.KEYCODE_MEDIA_PAUSE)
        assertEquals(KeyEvent.KEYCODE_MEDIA_NEXT, MediaCommands.KEYCODE_MEDIA_NEXT)
        assertEquals(KeyEvent.KEYCODE_MEDIA_PREVIOUS, MediaCommands.KEYCODE_MEDIA_PREVIOUS)
        assertEquals(PlaybackState.STATE_ERROR, MediaLogic.STATE_ERROR)
    }

    @Test fun STR_06_playingBeatsPausedBeatsStopped() {
        assertEquals(1, pick(U to "a", P to "b"))
        assertEquals(1, pick(S to "a", U to "b"))
        assertEquals(2, pick(S to "a", U to "b", MediaLogic.STATE_BUFFERING to "c"))
        assertNull(pick(S to "a", MediaLogic.STATE_NONE to "b"))
        assertNull(pick())
    }

    @Test fun STR_06_tiesGoToTheMostRecentSessionUnlessOneIsAlreadyShown() {
        assertEquals(0, pick(P to "new", P to "old"))
        assertEquals(1, pick(P to "new", P to "old", prefer = "old")) // no flip-flopping between two players
        assertEquals(0, pick(P to "new", U to "old", prefer = "old")) // a preference never beats a better state
    }

    @Test fun STR_06_aPlayerMidSkipStillCountsAsPlaying() {
        for (s in listOf(9, 10, 11, 4, 5)) assertTrue(MediaLogic.isPlaying(s))
        assertEquals(1, pick(U to "a", 10 to "b"))
    }

    @Test fun STR_06_actionsDecideWhetherTheTransportCallLeads() {
        assertEquals(listOf(Route.Transport, Route.ControllerKey, Route.SystemKey), MediaCommands.routes(Cmd.Next, MediaCommands.ACTION_SKIP_TO_NEXT))
        assertEquals(listOf(Route.ControllerKey, Route.SystemKey), MediaCommands.routes(Cmd.Next, MediaCommands.ACTION_PLAY))
        assertEquals(listOf(Route.ControllerKey, Route.SystemKey), MediaCommands.routes(Cmd.Previous, 0L))
        assertEquals(Route.Transport, MediaCommands.routes(Cmd.Pause, MediaCommands.ACTION_PAUSE).first())
        assertEquals(Route.Transport, MediaCommands.routes(Cmd.Play, MediaCommands.ACTION_PLAY_PAUSE).first())
        assertEquals(Route.Transport, MediaCommands.routes(Cmd.Pause, MediaCommands.ACTION_PLAY_PAUSE).first())
        assertEquals(Route.ControllerKey, MediaCommands.routes(Cmd.Play, MediaCommands.ACTION_PAUSE).first())
    }

    @Test fun STR_06_explicitKeysNotTheToggle() {
        assertEquals(KeyEvent.KEYCODE_MEDIA_PLAY, MediaCommands.keyCode(Cmd.Play))
        assertEquals(KeyEvent.KEYCODE_MEDIA_PAUSE, MediaCommands.keyCode(Cmd.Pause))
    }

    @Test fun HOM_23_aSkipControlIsDimmedOnlyWhenTheSessionReportsActionsWithoutIt() {
        assertTrue(MediaCommands.enabled(Cmd.Next, 0L)) // nothing reported yet: unknown, so try
        assertTrue(MediaCommands.enabled(Cmd.Next, MediaCommands.ACTION_SKIP_TO_NEXT))
        assertFalse(MediaCommands.enabled(Cmd.Next, MediaCommands.ACTION_PLAY or MediaCommands.ACTION_PAUSE))
        assertFalse(MediaCommands.enabled(Cmd.Previous, MediaCommands.ACTION_SKIP_TO_NEXT))
        assertTrue(MediaCommands.enabled(Cmd.Play, MediaCommands.ACTION_SKIP_TO_NEXT)); assertTrue(MediaCommands.enabled(Cmd.Pause, 0L))
    }

    private class Fake(val transportThrows: Boolean = false, val controllerOk: Boolean = true, val systemOk: Boolean = true) : MediaCommands.Target {
        val calls = mutableListOf<String>()
        override fun transport(cmd: Cmd) { calls += "transport:$cmd"; if (transportThrows) throw SecurityException() }
        override fun controllerKey(keyCode: Int): Boolean { calls += "controller:$keyCode"; return controllerOk }
        override fun systemKey(keyCode: Int): Boolean { calls += "system:$keyCode"; return systemOk }
    }

    @Test fun STR_06_executeUsesTheTransportWhenAdvertised() {
        val f = Fake()
        assertEquals(Route.Transport, MediaCommands.execute(f, Cmd.Next, MediaCommands.ACTION_SKIP_TO_NEXT))
        assertEquals(listOf("transport:Next"), f.calls)
    }

    @Test fun STR_06_executeFallsBackToTheMediaButtonThenTheSystemKey() {
        val f = Fake()
        assertEquals(Route.ControllerKey, MediaCommands.execute(f, Cmd.Previous, 0L))
        assertEquals(listOf("controller:88"), f.calls)
        val g = Fake(controllerOk = false)
        assertEquals(Route.SystemKey, MediaCommands.execute(g, Cmd.Play, MediaCommands.ACTION_PAUSE))
        assertEquals(listOf("controller:126", "system:126"), g.calls)
        val h = Fake(transportThrows = true)
        assertEquals(Route.ControllerKey, MediaCommands.execute(h, Cmd.Pause, MediaCommands.ACTION_PAUSE))
        assertEquals(listOf("transport:Pause", "controller:127"), h.calls)
    }

    @Test fun STR_06_executeReportsNullWhenEveryRouteFailed() {
        assertNull(MediaCommands.execute(Fake(controllerOk = false, systemOk = false), Cmd.Next, 0L))
    }
}
