package com.bos.sphere.core.data

import android.media.session.PlaybackState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaLogicTest {
    @Test fun constantsMatchFramework() {
        assertEquals(PlaybackState.STATE_PLAYING, MediaLogic.STATE_PLAYING)
        assertEquals(PlaybackState.STATE_PAUSED, MediaLogic.STATE_PAUSED)
        assertEquals(PlaybackState.STATE_STOPPED, MediaLogic.STATE_STOPPED)
        assertEquals(PlaybackState.STATE_NONE, MediaLogic.STATE_NONE)
        assertEquals(PlaybackState.STATE_BUFFERING, MediaLogic.STATE_BUFFERING)
        assertEquals(PlaybackState.STATE_CONNECTING, MediaLogic.STATE_CONNECTING)
        assertEquals(PlaybackState.ACTION_SKIP_TO_PREVIOUS, MediaLogic.ACTION_SKIP_TO_PREVIOUS)
        assertEquals(PlaybackState.ACTION_SKIP_TO_NEXT, MediaLogic.ACTION_SKIP_TO_NEXT)
    }

    @Test fun playingFlags() {
        assertTrue(MediaLogic.isPlaying(MediaLogic.STATE_PLAYING))
        assertTrue(MediaLogic.isPlaying(MediaLogic.STATE_BUFFERING))
        assertFalse(MediaLogic.isPlaying(MediaLogic.STATE_PAUSED))
        assertFalse(MediaLogic.isPlaying(MediaLogic.STATE_NONE))
    }

    @Test fun skipFlags() {
        assertTrue(MediaLogic.canNext(MediaLogic.ACTION_SKIP_TO_NEXT or 4L))
        assertFalse(MediaLogic.canNext(MediaLogic.ACTION_SKIP_TO_PREVIOUS))
        assertTrue(MediaLogic.canPrev(MediaLogic.ACTION_SKIP_TO_PREVIOUS))
        assertFalse(MediaLogic.canPrev(0L))
    }

    @Test fun choosePrefersPlayingThenPausedThenNothing() {
        assertEquals(1, MediaLogic.choose(listOf(MediaLogic.STATE_PAUSED, MediaLogic.STATE_PLAYING)))
        assertEquals(0, MediaLogic.choose(listOf(MediaLogic.STATE_PAUSED, MediaLogic.STATE_PAUSED)))
        assertEquals(1, MediaLogic.choose(listOf(MediaLogic.STATE_STOPPED, MediaLogic.STATE_PAUSED)))
        assertNull(MediaLogic.choose(listOf(MediaLogic.STATE_STOPPED, MediaLogic.STATE_NONE)))
        assertNull(MediaLogic.choose(emptyList()))
    }

    @Test fun titleFallback() {
        assertEquals("Spotify", MediaLogic.displayTitle(null, "Spotify"))
        assertEquals("Spotify", MediaLogic.displayTitle("  ", "Spotify"))
        assertEquals("Song", MediaLogic.displayTitle("Song", "Spotify"))
    }
}

class MediaArtworkTest {
    @org.junit.Test fun STR_06_artworkIsScaledToTheLimitKeepingAspect() {
        assertEquals(160 to 80, MediaLogic.artSize(1600, 800))
        assertEquals(40 to 160, MediaLogic.artSize(400, 1600))
        assertEquals(100 to 100, MediaLogic.artSize(100, 100))
        assertEquals(0 to 0, MediaLogic.artSize(0, 10))
    }
}
