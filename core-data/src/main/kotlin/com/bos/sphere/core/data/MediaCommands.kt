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

    /** Runs [cmd] on [target] along [routes]; returns the route that worked, or null when every route failed (the caller tells the user). */
    fun execute(target: Target, cmd: Cmd, actions: Long): Route? {
        for (r in routes(cmd, actions)) {
            val ok = runCatching {
                when (r) {
                    Route.Transport -> { target.transport(cmd); true }
                    Route.ControllerKey -> target.controllerKey(keyCode(cmd))
                    Route.SystemKey -> target.systemKey(keyCode(cmd))
                }
            }.getOrDefault(false)
            if (ok) return r
        }
        return null
    }
}
