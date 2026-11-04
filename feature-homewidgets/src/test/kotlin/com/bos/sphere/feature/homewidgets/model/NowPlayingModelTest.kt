package com.bos.sphere.feature.homewidgets.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NowPlayingModelTest {

    private val all = Controls(previous = true, playPause = true, next = true)
    private fun session(pkg: String, playback: Playback, title: String? = "Awake", artist: String? = "Tycho") =
        MediaFacts(pkg, pkg.substringAfterLast('.'), playback, title, artist, all)

    @Test
    fun withoutNotificationAccessItIsNotConnected() {
        assertEquals(NowPlayingView.NotConnected, NowPlayingModel.view(false, listOf(session("a.music", Playback.PLAYING)), true))
        assertEquals(NowPlayingView.NotConnected, NowPlayingModel.view(true, null, true))
    }

    @Test
    fun noSessionsOrOnlyStoppedOnesIsNothingPlaying() {
        assertEquals(NowPlayingView.Nothing, NowPlayingModel.view(true, emptyList(), true))
        assertEquals(NowPlayingView.Nothing, NowPlayingModel.view(true, listOf(session("a.music", Playback.STOPPED)), true))
    }

    @Test
    fun playingBeatsLoadingBeatsPausedInPlatformOrder() {
        val paused = session("a.podcasts", Playback.PAUSED)
        val loading = session("b.radio", Playback.LOADING)
        val playing1 = session("c.music", Playback.PLAYING)
        val playing2 = session("d.video", Playback.PLAYING)
        assertEquals(playing1, NowPlayingModel.choose(listOf(paused, loading, playing1, playing2)))
        assertEquals(loading, NowPlayingModel.choose(listOf(paused, loading)))
        assertEquals(paused, NowPlayingModel.choose(listOf(session("x.y", Playback.STOPPED), paused)))
    }

    @Test
    fun trackAndArtistAreShownOnlyWhenTheSettingAllows() {
        val shown = NowPlayingModel.view(true, listOf(session("a.music", Playback.PLAYING)), showDetails = true) as NowPlayingView.Track
        assertEquals("Awake", shown.title)
        assertEquals("Tycho", shown.artist)
        assertFalse(shown.withheld)
        val withheld = NowPlayingModel.view(true, listOf(session("a.music", Playback.PLAYING)), showDetails = false) as NowPlayingView.Track
        assertNull(withheld.title)
        assertNull(withheld.artist)
        assertTrue(withheld.withheld)
        assertEquals("music", withheld.appLabel)
        assertEquals(Playback.PLAYING, withheld.playback)
    }

    @Test
    fun aWidgetWithNoStoredOptionShowsTheAppNameOnly() {
        // Titles can name people (voice messages); a home screen is public.
        assertFalse(NowPlayingModel.SHOW_TRACK_DEFAULT)
        val v = NowPlayingModel.view(true, listOf(session("a.voice", Playback.PLAYING, title = "Voice message from Asha")), NowPlayingModel.SHOW_TRACK_DEFAULT)
            as NowPlayingView.Track
        assertTrue(v.withheld)
        assertNull(v.title)
        assertNull(v.artist)
        assertEquals("voice", v.appLabel)
    }

    @Test
    fun anAppHiddenInSphereIsNeverNamedAndItsTrackNeverShown() {
        val hidden = setOf("a.secret")
        val v = NowPlayingModel.view(true, listOf(session("a.secret", Playback.PLAYING)), showDetails = true, hiddenPackages = hidden)
            as NowPlayingView.Track
        assertTrue(v.appHidden)
        assertNull(v.appLabel)
        assertNull(v.title)
        assertNull(v.artist)
        assertTrue(v.withheld)
        assertEquals(Playback.PLAYING, v.playback) // the state and controls still show
        assertEquals(all, v.controls)
        // Another app playing is unaffected.
        val other = NowPlayingModel.view(true, listOf(session("b.music", Playback.PLAYING)), showDetails = true, hiddenPackages = hidden)
            as NowPlayingView.Track
        assertFalse(other.appHidden)
        assertEquals("Awake", other.title)
    }

    @Test
    fun aControlTapPicksThePackagesSessionTheWidgetShows() {
        // One app, two sessions (two browser tabs): the paused one first in platform order.
        val pausedTab = session("org.browser", Playback.PAUSED, title = "Tab 1")
        val playingTab = session("org.browser", Playback.PLAYING, title = "Tab 2")
        val shown = NowPlayingModel.choose(listOf(pausedTab, playingTab))
        val mine = listOf(pausedTab, playingTab).filter { it.packageName == "org.browser" }
        assertEquals(shown, NowPlayingModel.chooseBy(mine) { it.playback })
        assertEquals(playingTab, NowPlayingModel.chooseBy(mine) { it.playback })
        assertNull(NowPlayingModel.chooseBy(listOf(session("x", Playback.STOPPED))) { it.playback })
    }

    @Test
    fun playbackStates() {
        assertEquals(Playback.PLAYING, NowPlayingModel.playback(NowPlayingModel.STATE_PLAYING))
        assertEquals(Playback.PLAYING, NowPlayingModel.playback(NowPlayingModel.STATE_FAST_FORWARDING))
        assertEquals(Playback.PAUSED, NowPlayingModel.playback(NowPlayingModel.STATE_PAUSED))
        assertEquals(Playback.LOADING, NowPlayingModel.playback(NowPlayingModel.STATE_BUFFERING))
        assertEquals(Playback.LOADING, NowPlayingModel.playback(NowPlayingModel.STATE_SKIPPING_TO_NEXT))
        assertEquals(Playback.STOPPED, NowPlayingModel.playback(NowPlayingModel.STATE_ERROR))
        assertEquals(Playback.STOPPED, NowPlayingModel.playback(NowPlayingModel.STATE_NONE))
        assertEquals(Playback.STOPPED, NowPlayingModel.playback(99))
    }

    @Test
    fun controlsFollowTheSessionsActions() {
        val m = NowPlayingModel
        assertEquals(Controls(previous = false, playPause = true, next = true), m.controls(m.ACTION_PLAY_PAUSE or m.ACTION_SKIP_TO_NEXT, Playback.PAUSED))
        // Separate play / pause actions: the one that applies now decides.
        assertTrue(m.controls(m.ACTION_PAUSE, Playback.PLAYING).playPause)
        assertFalse(m.controls(m.ACTION_PAUSE, Playback.PAUSED).playPause)
        assertTrue(m.controls(m.ACTION_PLAY, Playback.PAUSED).playPause)
        assertEquals(Controls(previous = false, playPause = false, next = false), m.controls(0L, Playback.PLAYING))
    }

    @Test
    fun metadataIsOneBoundedLine() {
        assertNull(NowPlayingModel.clean(null))
        assertNull(NowPlayingModel.clean("   "))
        assertEquals("Says (live)", NowPlayingModel.clean("  Says\n(live) "))
        val long = NowPlayingModel.clean("x".repeat(500))!!
        assertEquals(120, long.length)
        assertTrue(long.endsWith("…"))
    }
}
