package com.bos.sphere.core.data

import android.content.ComponentName
import android.content.Context
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.view.KeyEvent
import android.app.PendingIntent
import android.os.Looper
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
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
    /** STR-06: album art or the player's display icon, scaled to [MediaLogic.ART_PX] at most; null when the player gives none. */
    val artwork: android.graphics.Bitmap? = null,
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

    const val STATE_ERROR = 7
    /** Fast-forward, rewind and the skipping states: the player is mid-playback, so the icon must not flip to Play while it changes track. */
    private val TRANSITIONAL = setOf(4, 5, 9, 10, 11)

    fun isPlaying(state: Int): Boolean = state == STATE_PLAYING || state == STATE_BUFFERING || state == STATE_CONNECTING || state in TRANSITIONAL

    fun canPrev(actions: Long): Boolean = actions and ACTION_SKIP_TO_PREVIOUS != 0L
    fun canNext(actions: Long): Boolean = actions and ACTION_SKIP_TO_NEXT != 0L

    /** A session with no state or that is stopped has nothing to control. */
    fun isActive(state: Int): Boolean = state != STATE_NONE && state != STATE_STOPPED

    /**
     * Index of the session to show: the first playing one, else the first active (paused) one, else null.
     * [states] is in the system's order (most recently active first).
     */
    fun choose(states: List<Int>): Int? = chooseSession(states.map { Candidate(it, "") }, null)

    /** One session as the picker sees it: its playback state and its package. */
    data class Candidate(val state: Int, val packageName: String)

    /** 2 = playing (or buffering / connecting / skipping), 1 = paused or errored (still controllable), 0 = stopped or no state (nothing to control). */
    fun rank(state: Int): Int = if (isPlaying(state)) 2 else if (isActive(state)) 1 else 0

    /**
     * W42: the session to show and control. Playing beats paused beats stopped (never picked); among sessions of the same rank the one from
     * [preferPackage] (the one already shown: no flip-flopping between two players) wins, else the first in [candidates], which is the
     * system's order (most recently active first). Re-run on every session-list change, every playback-state change and every tap.
     */
    fun chooseSession(candidates: List<Candidate>, preferPackage: String?): Int? {
        val best = candidates.maxOfOrNull { rank(it.state) } ?: return null
        if (best == 0) return null
        val tied = candidates.indices.filter { rank(candidates[it].state) == best }
        return tied.firstOrNull { preferPackage != null && candidates[it].packageName == preferPackage } ?: tied.first()
    }

    const val ART_PX = 160

    /** Size to scale artwork to so its longest side is at most [ART_PX]; unchanged when already small. */
    fun artSize(w: Int, h: Int): Pair<Int, Int> {
        if (w <= 0 || h <= 0) return 0 to 0
        val longest = maxOf(w, h)
        if (longest <= ART_PX) return w to h
        val f = ART_PX.toFloat() / longest
        return maxOf(1, (w * f).toInt()) to maxOf(1, (h * f).toInt())
    }

    /**
     * STR-05: a watcher that started before notification access was granted stays blind until it re-attaches.
     * True when someone is watching and the last attach was refused, so the grant coming through must re-attach.
     */
    fun needsReattach(watchers: Int, needsAccess: Boolean): Boolean = watchers > 0 && needsAccess

    /** Title shown when the player gives none. */
    fun displayTitle(title: String?, fallback: String): String = title?.takeIf { it.isNotBlank() } ?: fallback
}

/**
 * W42: one media session as [MediaNow] needs it. The real one wraps a [MediaController]; tests use fakes (Robolectric has no media sessions).
 * [transport], [controllerKey] and [systemKey] are the three routes of [MediaCommands].
 */
interface MediaPort : MediaCommands.Target {
    /** Stable identity of the session (its token): equal for the same session across calls that return fresh controller objects. */
    val id: Any
    val packageName: String
    val state: Int
    val actions: Long
    val title: String?
    val artist: String?
    val artwork: android.graphics.Bitmap?
    val sessionActivity: PendingIntent?
    /** Calls [onChange] (main thread) on every playback-state or metadata change, and when the session is destroyed. */
    fun register(onChange: () -> Unit)
    fun unregister()
}

private class ControllerPort(val c: MediaController, private val handler: Handler, private val audio: AudioManager?) : MediaPort {
    override val id: Any get() = c.sessionToken ?: c.packageName
    override val packageName: String get() = c.packageName
    override val state: Int get() = c.playbackState?.state ?: MediaLogic.STATE_NONE
    override val actions: Long get() = c.playbackState?.actions ?: 0L
    override val title: String? get() = c.metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
    override val artist: String? get() = c.metadata?.let { it.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: it.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST) }
    override val artwork: android.graphics.Bitmap? get() = c.metadata?.let {
        it.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART) ?: it.getBitmap(MediaMetadata.METADATA_KEY_ART) ?: it.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
    }
    override val sessionActivity: PendingIntent? get() = c.sessionActivity
    private var cb: MediaController.Callback? = null

    override fun register(onChange: () -> Unit) {
        val k = object : MediaController.Callback() {
            override fun onPlaybackStateChanged(state: PlaybackState?) = onChange()
            override fun onMetadataChanged(metadata: MediaMetadata?) = onChange()
            override fun onSessionDestroyed() = onChange()
        }
        cb = k
        c.registerCallback(k, handler)
    }
    override fun unregister() { cb?.let { runCatching { c.unregisterCallback(it) } }; cb = null }

    override fun transport(cmd: MediaCommands.Cmd) {
        val t = c.transportControls
        when (cmd) {
            MediaCommands.Cmd.Play -> t.play()
            MediaCommands.Cmd.Pause -> t.pause()
            MediaCommands.Cmd.Next -> t.skipToNext()
            MediaCommands.Cmd.Previous -> t.skipToPrevious()
        }
    }
    override fun controllerKey(keyCode: Int): Boolean =
        c.dispatchMediaButtonEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode)) && c.dispatchMediaButtonEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
    override fun systemKey(keyCode: Int): Boolean {
        val a = audio ?: return false
        a.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
        a.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
        return true
    }
}

/**
 * The current media session as a flow. Needs notification access (the app's
 * [SphereNotificationListener] is the component the system checks); without it [needsAccess] is true.
 *
 * W42 ("the music controls at the top don't always work"): every active session is watched (not only the shown one), so a second player that
 * starts playing takes over the line at once; the session is re-picked on a session-list change, on every playback-state change and again
 * on every tap before the command goes out; commands follow [MediaCommands] (transport, then media-button event, then the system key).
 *
 * W60 ("still flaky on a real device", a follow-up to W42): W42's ladder tried a route and, for [MediaCommands.Route.Transport], counted it
 * as success the instant the one-way binder call was accepted — which a real player can silently ignore (not focused, a stale remote stub,
 * a race with another command). So a dropped Transport command never fell through to the working routes below it. [send] now drives the
 * ladder itself: each route is given up to [MediaCommands.CONFIRM_MS] to be confirmed by the session's OWN callback (Play/Pause: the
 * reported state actually flipped; Next/Previous: any callback from that session at all, since track identity can't be checked generically)
 * before moving to the next route; "Can't control <app>" fires only once every route has been tried AND given its window with no
 * confirmation. A short debounce ([MediaCommands.DEBOUNCE_MS]) drops a same-command repeat that arrives while the first is still pending, so
 * a double-fired tap cannot send two commands that race each other onto the player.
 */
object MediaNow {
    private val _info = MutableStateFlow<MediaInfo?>(null)
    val info: StateFlow<MediaInfo?> = _info.asStateFlow()
    private val _needsAccess = MutableStateFlow(false)
    val needsAccess: StateFlow<Boolean> = _needsAccess.asStateFlow()
    private val _failures = MutableSharedFlow<String>(extraBufferCapacity = 4)
    /** The player's display name each time every route of a command failed ("Can't control <app>"). */
    val failures: SharedFlow<String> = _failures.asSharedFlow()

    private var manager: MediaSessionManager? = null
    private var listenerComponent: ComponentName? = null
    private var appContext: Context? = null
    private var provider: (() -> List<MediaPort>)? = null
    /** The watched sessions, in the system's order (most recently active first). */
    private var ports: List<MediaPort> = emptyList()
    private var current: MediaPort? = null
    private var refCount = 0
    private val handler = Handler(Looper.getMainLooper())
    private var scheduler: MediaScheduler = HandlerMediaScheduler(handler)

    /** One command working its way down the ladder: which route is next, since when, and whose session it targets. */
    private class Escalation(
        val cmd: MediaCommands.Cmd,
        val portId: Any,
        val portPackage: String,
        var routesLeft: List<MediaCommands.Route>,
        val startedAt: Long,
        var cancelTimer: (() -> Unit)? = null,
    )
    private var escalation: Escalation? = null

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { list -> adopt(list.orEmpty().map { wrap(it) }) }

    private fun wrap(c: MediaController): MediaPort =
        ControllerPort(c, handler, appContext?.getSystemService(Context.AUDIO_SERVICE) as? AudioManager)

    /** Starts watching (reference counted: pair every call with [stop]). Call on the main thread. */
    fun start(context: Context) {
        if (refCount++ > 0) { if (MediaLogic.needsReattach(refCount, _needsAccess.value)) attach(context.applicationContext); return }
        attach(context.applicationContext)
    }

    /**
     * Called by the notification listener when it connects, which is what the system does right after the user turns
     * notification access on (STR-05, STR-06): a strip that was watching without access starts seeing sessions at once.
     */
    fun onAccessMayHaveChanged() {
        val ctx = appContext ?: return
        if (MediaLogic.needsReattach(refCount, _needsAccess.value)) attach(ctx)
    }

    private fun attach(ctx: Context) {
        appContext = ctx
        val mgr = ctx.getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager ?: return
        manager = mgr
        val comp = ComponentName(ctx, SphereNotificationListener::class.java)
        listenerComponent = comp
        provider = { mgr.getActiveSessions(comp).map { wrap(it) } }
        try {
            mgr.addOnActiveSessionsChangedListener(sessionsListener, comp, handler)
            _needsAccess.value = false
            adopt(provider!!())
        } catch (_: SecurityException) {
            _needsAccess.value = true
            dropPorts()
            _info.value = null
        }
    }

    fun stop() {
        if (refCount == 0) return
        if (--refCount > 0) return
        runCatching { manager?.removeOnActiveSessionsChangedListener(sessionsListener) }
        dropPorts()
        manager = null
        provider = null
        _info.value = null
    }

    /** Tests only: publishes [info] as if a session had reported it (Robolectric has no media sessions). */
    fun publishForTest(info: MediaInfo?) { _info.value = info }

    /** Tests only: the session list as the system would report it, and where a tap's refresh reads it from. */
    fun sessionsForTest(list: List<MediaPort>, source: (() -> List<MediaPort>)? = { list }) { provider = source; adopt(list) }
    fun resetForTest() {
        dropPorts(); provider = null; refCount = 0; _info.value = null; _needsAccess.value = false
        escalation?.cancelTimer?.invoke(); escalation = null
        scheduler = HandlerMediaScheduler(handler)
    }

    /** Tests only: a deterministic scheduler so "no confirmation arrived within the window" can be simulated without sleeping a thread. */
    fun schedulerForTest(s: MediaScheduler) { scheduler = s }

    /** Play when paused, pause when playing, after re-reading the session list (a stale controller is the classic "does nothing"). */
    fun playPause() {
        refresh()
        val p = current ?: return
        send(p, if (MediaLogic.isPlaying(p.state)) MediaCommands.Cmd.Pause else MediaCommands.Cmd.Play)
    }

    fun next() { refresh(); current?.let { send(it, MediaCommands.Cmd.Next) } }
    fun previous() { refresh(); current?.let { send(it, MediaCommands.Cmd.Previous) } }

    /**
     * W60: sends [cmd] to [p], escalating through [MediaCommands.routes] until one route is confirmed by the session's own callback or
     * every route has been tried and given [MediaCommands.CONFIRM_MS] with no confirmation (then "Can't control <app>" fires). A repeat of
     * the same command on the same session within [MediaCommands.DEBOUNCE_MS] of one still pending is dropped (the small-target double-fire).
     */
    private fun send(p: MediaPort, cmd: MediaCommands.Cmd) {
        val now = scheduler.now()
        val pending = escalation
        if (pending != null && pending.cmd == cmd && pending.portId == p.id && now - pending.startedAt < MediaCommands.DEBOUNCE_MS) return
        pending?.cancelTimer?.invoke()
        val e = Escalation(cmd, p.id, p.packageName, MediaCommands.routes(cmd, p.actions), now)
        escalation = e
        dispatchRoute(e)
        reselect() // the player's own state callback follows; this only picks up a change that already happened
    }

    /** Sends [e]'s current (first of [Escalation.routesLeft]) route. A route the framework refused outright escalates at once, with no
     * wait; one it accepted gets up to [MediaCommands.CONFIRM_MS] for the session's callback to confirm it (see [onPortSignal]). */
    private fun dispatchRoute(e: Escalation) {
        val p = ports.firstOrNull { it.id == e.portId } ?: run { resolveEscalation(e, confirmed = false); return }
        val route = e.routesLeft.first()
        val accepted = MediaCommands.attempt(p, route, e.cmd)
        if (!accepted) { advanceEscalation(e); return }
        e.cancelTimer = scheduler.postDelayed(MediaCommands.CONFIRM_MS) { if (escalation === e) advanceEscalation(e) }
    }

    /** The current route's window ran out (or it was refused outright) with no confirmation: move to the next route, or fail. */
    private fun advanceEscalation(e: Escalation) {
        if (escalation !== e) return
        val rest = e.routesLeft.drop(1)
        if (rest.isEmpty()) { resolveEscalation(e, confirmed = false); return }
        e.routesLeft = rest
        dispatchRoute(e)
    }

    /** Called with every port's own callback (whether or not a command is pending on it): confirms an in-flight escalation targeting that
     * session, per [MediaCommands.confirmsPlayPause] for Play/Pause, or on ANY callback for Next/Previous (can't verify track identity
     * generically; total silence from the session is the actual sign of a dropped command, so any sign of life counts as landed). */
    private fun onPortSignal(p: MediaPort) {
        reselect()
        val e = escalation ?: return
        if (e.portId != p.id) return
        val confirmed = when (e.cmd) {
            MediaCommands.Cmd.Play, MediaCommands.Cmd.Pause -> MediaCommands.confirmsPlayPause(e.cmd, MediaLogic.isPlaying(p.state))
            MediaCommands.Cmd.Next, MediaCommands.Cmd.Previous -> true
        }
        if (confirmed) resolveEscalation(e, confirmed = true)
    }

    private fun resolveEscalation(e: Escalation, confirmed: Boolean) {
        if (escalation !== e) return
        e.cancelTimer?.invoke()
        escalation = null
        if (!confirmed) _failures.tryEmit(labelOf(e.portPackage))
    }

    /** Opens the player: the session's own activity if it has one, else the package's launch intent. */
    fun openPlayer(context: Context) {
        refresh()
        val c = current ?: return
        if (PendingIntentLauncher.fire(c.sessionActivity, context)) return
        context.packageManager.getLaunchIntentForPackage(c.packageName)?.let {
            runCatching { context.startActivity(it.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }
    }

    /** Re-reads the session list from the system (cheap: one binder call). */
    private fun refresh() {
        val src = provider ?: return
        try { adopt(src()) } catch (_: SecurityException) { _needsAccess.value = true; dropPorts(); _info.value = null }
    }

    /** Takes the new session list: keeps the watched port of a session that is still there, watches new ones, forgets the gone ones. */
    private fun adopt(fresh: List<MediaPort>) {
        val old = ports.associateBy { it.id }
        val next = fresh.map { f -> old[f.id] ?: f.also { p -> p.register { onPortSignal(p) } } }
        val keep = next.map { it.id }.toSet()
        ports.filter { it.id !in keep }.forEach { it.unregister() }
        ports = next
        escalation?.let { if (it.portId !in keep) resolveEscalation(it, confirmed = false) } // its session just disappeared from the list
        reselect()
    }

    private fun reselect() {
        val idx = MediaLogic.chooseSession(ports.map { MediaLogic.Candidate(it.state, it.packageName) }, current?.packageName)
        current = idx?.let { ports[it] }
        publish()
    }

    private fun dropPorts() {
        ports.forEach { it.unregister() }
        ports = emptyList()
        current = null
        escalation?.let { resolveEscalation(it, confirmed = false) } // its session is gone: fail now, don't wait out the timer
    }

    private fun labelOf(pkg: String): String {
        val ctx = appContext ?: return pkg
        return runCatching { ctx.packageManager.getApplicationLabel(ctx.packageManager.getApplicationInfo(pkg, 0)).toString() }.getOrNull() ?: pkg
    }

    private fun publish() {
        val c = current
        if (c == null) { _info.value = null; return }
        val actions = c.actions
        _info.value = MediaInfo(
            title = MediaLogic.displayTitle(c.title, labelOf(c.packageName)),
            artist = c.artist ?: "",
            packageName = c.packageName,
            playing = MediaLogic.isPlaying(c.state),
            canPrev = MediaCommands.enabled(MediaCommands.Cmd.Previous, actions),
            canNext = MediaCommands.enabled(MediaCommands.Cmd.Next, actions),
            artwork = artworkOf(c.artwork),
        )
    }

    private var lastArtSource: android.graphics.Bitmap? = null
    private var lastArt: android.graphics.Bitmap? = null

    /** Album art, else art, else the display icon; scaled down once per source bitmap. */
    private fun artworkOf(src: android.graphics.Bitmap?): android.graphics.Bitmap? {
        if (src == null) return null
        if (src === lastArtSource) return lastArt
        val (w, h) = MediaLogic.artSize(src.width, src.height)
        val out = if (w == src.width && h == src.height) src else runCatching { android.graphics.Bitmap.createScaledBitmap(src, w, h, true) }.getOrDefault(src)
        lastArtSource = src; lastArt = out
        return out
    }
}
