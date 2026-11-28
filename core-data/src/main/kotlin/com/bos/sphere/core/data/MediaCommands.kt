package com.bos.sphere.core.data

/**
 * W42: how a transport command reaches a media session. Android-free so the decision table is a plain JVM test.
 *
 * Why: a session's transportControls only work when the player advertises the matching PlaybackState action (and many players, browsers
 * and older apps advertise none for Next / Previous), so a bare skipToNext() silently does nothing. The order is: the transport call when the
 * action is advertised, else a real media-button key event sent to that session, else the system's media key. Explicit PLAY / PAUSE keys are
 * used (never the toggle) because the state is known.
 */
object MediaCommands {
    enum class Cmd { Play, Pause, Next, Previous }
    enum class Route { Transport, ControllerKey, SystemKey }

    // Mirror PlaybackState.ACTION_* and KeyEvent.KEYCODE_* (checked against the framework in the tests).
    const val ACTION_STOP = 1L
    const val ACTION_PAUSE = 2L
    const val ACTION_PLAY = 4L
    const val ACTION_SKIP_TO_PREVIOUS = 16L
    const val ACTION_SKIP_TO_NEXT = 32L
    const val ACTION_PLAY_PAUSE = 512L
    const val KEYCODE_MEDIA_PLAY_PAUSE = 85
    const val KEYCODE_MEDIA_NEXT = 87
    const val KEYCODE_MEDIA_PREVIOUS = 88
    const val KEYCODE_MEDIA_PLAY = 126
    const val KEYCODE_MEDIA_PAUSE = 127

    /** What the three routes do; the real one wraps a MediaController, tests use a fake. */
    interface Target {
        fun transport(cmd: Cmd)
        /** Key down then up sent to the session itself; true when the session accepted both. */
        fun controllerKey(keyCode: Int): Boolean
        /** Key down then up through the audio service (reaches whichever session the system picks); true when it was sent. */
        fun systemKey(keyCode: Int): Boolean
    }

    /**
     * W60 ("the music controls are flaky", a device report after wave 42): [execute] (below) treated the Transport route's call as success
     * unconditionally, because dispatching a MediaController.transportControls call is a one-way binder call that never confirms the
     * receiving app actually acted on it (not focused, a stale remote stub, a race with another command, the app's own callback no-oping).
     * So the ladder never escalated for the common real failure. [MediaNow] now drives each route with [attempt], then waits up to
     * [CONFIRM_MS] for the session's OWN state/metadata callback to confirm it before moving to the next route, instead of stopping at the
     * first route that the framework merely accepted.
     *
     * How long to wait for that confirmation: a MediaSession callback crosses one binder hop and is delivered well under 200 ms in the
     * normal case (same reasoning as the rest of this file: framework contract, not measured on a device). 700 ms leaves wide margin for a
     * slow player (cold IPC, buffering) while keeping the worst case for Play/Pause (two routes when nothing is advertised, or three when
     * Transport is also tried: see [routes]) at 1400-2100 ms, which [GlanceNotch.PENDING_MS] (the optimistic glyph's own revert window,
     * bumped to 2200 ms alongside this) is sized to outlast — so a command that is slow but eventually confirmed does not flash the glyph
     * back and then forward again.
     */
    const val CONFIRM_MS = 700L

    /**
     * W60: a second identical command (same [Cmd], same session) that arrives while the first is still awaiting confirmation (within
     * [DEBOUNCE_MS] of being sent) is dropped — the known small-target double-fire (two pointer-down events from one tap) is indistinguishable
     * from a real second tap only by timing, and a genuine bounce arrives within a few tens of ms, far inside this window. Once the first
     * command resolves (confirmed or every route exhausted) or [DEBOUNCE_MS] has passed, whichever is first, the next identical command is
     * sent fresh — a deliberate quick double-skip (two separate Next taps) almost always has one of those happen between the two presses
     * (most sessions confirm a skip within tens of ms), so it still fires twice. 280 ms sits in the middle of the owner's suggested
     * 250-300 ms: long enough to catch a bounce, short enough that a human's second deliberate tap rarely lands inside it un-confirmed.
     */
    const val DEBOUNCE_MS = 280L

    fun keyCode(cmd: Cmd): Int = when (cmd) {
        Cmd.Play -> KEYCODE_MEDIA_PLAY
        Cmd.Pause -> KEYCODE_MEDIA_PAUSE
        Cmd.Next -> KEYCODE_MEDIA_NEXT
        Cmd.Previous -> KEYCODE_MEDIA_PREVIOUS
    }

    /** True when the session's advertised [actions] include the one this command needs. */
    fun advertised(cmd: Cmd, actions: Long): Boolean = when (cmd) {
        Cmd.Play -> actions and (ACTION_PLAY or ACTION_PLAY_PAUSE) != 0L
        Cmd.Pause -> actions and (ACTION_PAUSE or ACTION_STOP or ACTION_PLAY_PAUSE) != 0L
        Cmd.Next -> actions and ACTION_SKIP_TO_NEXT != 0L
        Cmd.Previous -> actions and ACTION_SKIP_TO_PREVIOUS != 0L
    }

    /** The routes to try, in order: the transport call only when the action is advertised, then the session's own key event, then the system key. */
    fun routes(cmd: Cmd, actions: Long): List<Route> =
        if (advertised(cmd, actions)) listOf(Route.Transport, Route.ControllerKey, Route.SystemKey)
        else listOf(Route.ControllerKey, Route.SystemKey)

    /**
     * Whether the control is shown usable. Play / Pause always are. Next / Previous are when advertised, or when the session advertises nothing
     * at all (state not reported yet: unknown, so try). A session that reports actions but not this one cannot do it: the control is dimmed.
     */
    fun enabled(cmd: Cmd, actions: Long): Boolean = when (cmd) {
        Cmd.Play, Cmd.Pause -> true
        else -> actions == 0L || advertised(cmd, actions)
    }

    /**
     * Sends [cmd] to [target] by exactly [route] once. True means the framework ACCEPTED the call (for [Route.Transport] this is always
     * true unless it threw: a transport call is fire-and-forget and never confirms the receiving app acted); false means the call itself
     * was refused or threw, so the caller should move to the next route at once, with no wait. [execute] and [MediaNow]'s escalating sender
     * both use this; only [MediaNow] additionally waits for the session's own callback before trusting a true.
     */
    fun attempt(target: Target, route: Route, cmd: Cmd): Boolean = runCatching {
        when (route) {
            Route.Transport -> { target.transport(cmd); true }
            Route.ControllerKey -> target.controllerKey(keyCode(cmd))
            Route.SystemKey -> target.systemKey(keyCode(cmd))
        }
    }.getOrDefault(false)

    /**
     * Runs [cmd] on [target] along [routes], one call per route, stopping at the first the framework accepts. Returns that route, or null
     * when every route refused the call outright. Kept for the decision-table tests and as the non-escalating building block; it does NOT
     * confirm the command actually worked (see [CONFIRM_MS]) — [MediaNow] drives the real, confirming send itself using [attempt].
     */
    fun execute(target: Target, cmd: Cmd, actions: Long): Route? {
        for (r in routes(cmd, actions)) if (attempt(target, r, cmd)) return r
        return null
    }

    /**
     * W60: whether [cmd]'s expected effect is visible in [nowPlaying] (the session's reported state after SOME callback arrived). Only
     * Play/Pause can be judged this way — [nowPlaying] must have flipped to what [cmd] asked for. Next/Previous can't verify track
     * identity generically, so their confirmation is "any callback from the targeted session arrived at all" (judged by [MediaNow] itself,
     * which is the only place that knows whether a callback happened; this function always answers true for them so a caller that does not
     * special-case Next/Previous still treats any observed callback as confirming, matching that rule).
     */
    fun confirmsPlayPause(cmd: Cmd, nowPlaying: Boolean): Boolean = when (cmd) {
        Cmd.Play -> nowPlaying
        Cmd.Pause -> !nowPlaying
        Cmd.Next, Cmd.Previous -> true
    }
}
