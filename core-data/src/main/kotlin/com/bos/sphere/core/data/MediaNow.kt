package com.bos.sphere.core.data

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** What the home media strip shows. */
data class MediaInfo(
    val title: String,
    val artist: String,
    val packageName: String,
    val playing: Boolean,
    val canPrev: Boolean,
    val canNext: Boolean,
)

/** Android-free rules for mapping a playback state and choosing among sessions. */
object MediaLogic {
    // Mirrors PlaybackState.STATE_* / ACTION_* so this stays unit-testable without the framework.
    const val STATE_NONE = 0
    const val STATE_STOPPED = 1
    const val STATE_PAUSED = 2
    const val STATE_PLAYING = 3
    const val STATE_BUFFERING = 6
    const val STATE_CONNECTING = 8
    const val ACTION_SKIP_TO_PREVIOUS = 16L
    const val ACTION_SKIP_TO_NEXT = 32L

    fun isPlaying(state: Int): Boolean = state == STATE_PLAYING || state == STATE_BUFFERING || state == STATE_CONNECTING

    fun canPrev(actions: Long): Boolean = actions and ACTION_SKIP_TO_PREVIOUS != 0L
    fun canNext(actions: Long): Boolean = actions and ACTION_SKIP_TO_NEXT != 0L

    /** A session with no state or that is stopped has nothing to control. */
    fun isActive(state: Int): Boolean = state != STATE_NONE && state != STATE_STOPPED

    /**
     * Index of the session to show: the first playing one, else the first active (paused) one, else null.
     * [states] is in the system's order (most recently active first).
     */
    fun choose(states: List<Int>): Int? =
        states.indexOfFirst { isPlaying(it) }.takeIf { it >= 0 }
            ?: states.indexOfFirst { isActive(it) }.takeIf { it >= 0 }

    /** Title shown when the player gives none. */
    fun displayTitle(title: String?, fallback: String): String = title?.takeIf { it.isNotBlank() } ?: fallback
}

/**
 * The current media session as a flow. Needs notification access (the app's
 * [SphereNotificationListener] is the component the system checks); without it [needsAccess] is true.
 */
object MediaNow {
    private val _info = MutableStateFlow<MediaInfo?>(null)
    val info: StateFlow<MediaInfo?> = _info.asStateFlow()
    private val _needsAccess = MutableStateFlow(false)
    val needsAccess: StateFlow<Boolean> = _needsAccess.asStateFlow()

    private var manager: MediaSessionManager? = null
    private var listenerComponent: ComponentName? = null
    private var appContext: Context? = null
    private var current: MediaController? = null
    private var refCount = 0
    private val handler = Handler(Looper.getMainLooper())

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { list -> pick(list.orEmpty()) }

    private val callback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) = publish()
        override fun onMetadataChanged(metadata: MediaMetadata?) = publish()
        override fun onSessionDestroyed() = refresh()
    }

    /** Starts watching (reference counted: pair every call with [stop]). Call on the main thread. */
    fun start(context: Context) {
        if (refCount++ > 0) return
        val ctx = context.applicationContext
        appContext = ctx
        val mgr = ctx.getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager ?: return
        manager = mgr
        val comp = ComponentName(ctx, SphereNotificationListener::class.java)
        listenerComponent = comp
        try {
            mgr.addOnActiveSessionsChangedListener(sessionsListener, comp, handler)
            _needsAccess.value = false
            pick(mgr.getActiveSessions(comp))
        } catch (_: SecurityException) {
            _needsAccess.value = true
            _info.value = null
        }
    }

    fun stop() {
        if (refCount == 0) return
        if (--refCount > 0) return
        runCatching { manager?.removeOnActiveSessionsChangedListener(sessionsListener) }
        detach()
        manager = null
        _info.value = null
    }

    fun playPause() {
        val c = current ?: return
        if (c.playbackState?.state?.let(MediaLogic::isPlaying) == true) c.transportControls.pause() else c.transportControls.play()
    }

    fun next() { current?.transportControls?.skipToNext() }
    fun previous() { current?.transportControls?.skipToPrevious() }

    /** Opens the player: the session's own activity if it has one, else the package's launch intent. */
    fun openPlayer(context: Context) {
        val c = current ?: return
        val pi = c.sessionActivity
        if (pi != null && runCatching { pi.send() }.isSuccess) return
        context.packageManager.getLaunchIntentForPackage(c.packageName)?.let {
            runCatching { context.startActivity(it.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }
    }

    private fun refresh() {
        val mgr = manager ?: return
        val comp = listenerComponent ?: return
        try { pick(mgr.getActiveSessions(comp)) } catch (_: SecurityException) { _needsAccess.value = true; detach(); _info.value = null }
    }

    private fun pick(sessions: List<MediaController>) {
        val idx = MediaLogic.choose(sessions.map { it.playbackState?.state ?: MediaLogic.STATE_NONE })
        val chosen = idx?.let { sessions[it] }
        if (chosen?.sessionToken != current?.sessionToken) {
            detach()
            current = chosen
            chosen?.registerCallback(callback, handler)
        }
        publish()
    }

    private fun detach() {
        runCatching { current?.unregisterCallback(callback) }
        current = null
    }

    private fun publish() {
        val c = current
        if (c == null) { _info.value = null; return }
        val st = c.playbackState
        val md = c.metadata
        val ctx = appContext
        val fallback = ctx?.let { runCatching { it.packageManager.getApplicationLabel(it.packageManager.getApplicationInfo(c.packageName, 0)).toString() }.getOrNull() } ?: c.packageName
        _info.value = MediaInfo(
            title = MediaLogic.displayTitle(md?.getString(MediaMetadata.METADATA_KEY_TITLE), fallback),
            artist = md?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: md?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST) ?: "",
            packageName = c.packageName,
            playing = MediaLogic.isPlaying(st?.state ?: MediaLogic.STATE_NONE),
            canPrev = MediaLogic.canPrev(st?.actions ?: 0L),
            canNext = MediaLogic.canNext(st?.actions ?: 0L),
        )
    }
}
