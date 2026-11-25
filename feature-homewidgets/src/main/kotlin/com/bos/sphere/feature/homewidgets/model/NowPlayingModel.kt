package com.bos.sphere.feature.homewidgets.model

/** A media session's playback, reduced to what the widget names (glyph + word). */
enum class Playback { PLAYING, PAUSED, LOADING, STOPPED }

/** Which transport controls the session accepts. */
data class Controls(val previous: Boolean, val playPause: Boolean, val next: Boolean)

/** One active media session, from `MediaSessionManager.getActiveSessions` (needs notification access). */
data class MediaFacts(
    val packageName: String,
    val appLabel: String,
    val playback: Playback,
    val title: String?,
    val artist: String?,
    val controls: Controls,
)

sealed interface NowPlayingView {
    /** Notification access is off, so Sphere can't see media sessions. */
    data object NotConnected : NowPlayingView

    /** Nothing is playing or paused. */
    data object Nothing : NowPlayingView

    /**
     * The session the widget shows. [title] and [artist] are null when they are withheld
     * ([withheld]: this widget's setting, or the app is hidden) or the app didn't give them.
     * [appLabel] is null when the playing app is hidden in Sphere: the widget then names no app
     * and no track, and its tap opens Sphere rather than the app.
     */
    data class Track(
        val packageName: String,
        val appLabel: String?,
        val playback: Playback,
        val title: String?,
        val artist: String?,
        val withheld: Boolean,
        val controls: Controls,
    ) : NowPlayingView {
        val appHidden: Boolean get() = appLabel == null
    }
}

object NowPlayingModel {
    // PlaybackState.STATE_* and ACTION_* values (stable platform constants), repeated here so this
    // model stays plain JVM code.
    const val STATE_NONE = 0
    const val STATE_STOPPED = 1
    const val STATE_PAUSED = 2
    const val STATE_PLAYING = 3
    const val STATE_FAST_FORWARDING = 4
    const val STATE_REWINDING = 5
    const val STATE_BUFFERING = 6
    const val STATE_ERROR = 7
    const val STATE_CONNECTING = 8
    const val STATE_SKIPPING_TO_PREVIOUS = 9
    const val STATE_SKIPPING_TO_NEXT = 10
    const val STATE_SKIPPING_TO_QUEUE_ITEM = 11
    const val ACTION_PAUSE = 2L
    const val ACTION_PLAY = 4L
    const val ACTION_SKIP_TO_PREVIOUS = 16L
    const val ACTION_SKIP_TO_NEXT = 32L
    const val ACTION_PLAY_PAUSE = 512L

    private const val MAX_TEXT = 120

    /**
     * A widget with no stored option shows the app's name only, not the track: a home screen is
     * public and titles can name people (voice-message players put the sender in them).
     */
    const val SHOW_TRACK_DEFAULT = false

    fun playback(state: Int): Playback = when (state) {
        STATE_PLAYING, STATE_FAST_FORWARDING, STATE_REWINDING -> Playback.PLAYING
        STATE_PAUSED -> Playback.PAUSED
        STATE_BUFFERING, STATE_CONNECTING,
        STATE_SKIPPING_TO_PREVIOUS, STATE_SKIPPING_TO_NEXT, STATE_SKIPPING_TO_QUEUE_ITEM -> Playback.LOADING
        else -> Playback.STOPPED // NONE, STOPPED, ERROR, unknown
    }

    fun controls(actions: Long, playback: Playback): Controls = Controls(
        previous = actions and ACTION_SKIP_TO_PREVIOUS != 0L,
        playPause = actions and ACTION_PLAY_PAUSE != 0L ||
            (if (playback == Playback.PLAYING) actions and ACTION_PAUSE != 0L else actions and ACTION_PLAY != 0L),
        next = actions and ACTION_SKIP_TO_NEXT != 0L,
    )

    /** Metadata text made safe for one widget line: trimmed, one line, bounded; blank = absent. */
    fun clean(text: CharSequence?): String? {
        val s = text?.toString()?.replace(Regex("\\s+"), " ")?.trim().orEmpty()
        return if (s.isEmpty()) null else if (s.length > MAX_TEXT) s.take(MAX_TEXT - 1) + "…" else s
    }

    /**
     * The session to show: the first playing one, else the first loading, else the first paused, in
     * the platform's order (most recently active first). Stopped sessions are nothing playing.
     */
    fun choose(sessions: List<MediaFacts>): MediaFacts? = chooseBy(sessions) { it.playback }

    /**
     * [choose]'s rule for anything with a playback state. A control tap uses it on the tapped
     * package's own sessions, so it reaches the session the widget shows even when one app has
     * several (two browser tabs, one paused).
     */
    fun <T> chooseBy(sessions: List<T>, playback: (T) -> Playback): T? =
        sessions.firstOrNull { playback(it) == Playback.PLAYING }
            ?: sessions.firstOrNull { playback(it) == Playback.LOADING }
            ?: sessions.firstOrNull { playback(it) == Playback.PAUSED }

    /**
     * [access]: notification access granted; [sessions]: null when they couldn't be read.
     * [showDetails]: this widget's setting (track and artist; the default is the app's name only,
     * since titles can name people). [hiddenPackages]: apps hidden in Sphere, which are never
     * named and whose track is never shown.
     */
    fun view(access: Boolean, sessions: List<MediaFacts>?, showDetails: Boolean, hiddenPackages: Set<String> = emptySet()): NowPlayingView {
        if (!access || sessions == null) return NowPlayingView.NotConnected
        val s = choose(sessions) ?: return NowPlayingView.Nothing
        val hidden = s.packageName in hiddenPackages
        val details = showDetails && !hidden
        return NowPlayingView.Track(
            packageName = s.packageName,
            appLabel = if (hidden) null else s.appLabel,
            playback = s.playback,
            title = if (details) s.title else null,
            artist = if (details) s.artist else null,
            withheld = !details,
            controls = s.controls,
        )
    }
}
