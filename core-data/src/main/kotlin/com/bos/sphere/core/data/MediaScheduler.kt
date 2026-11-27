package com.bos.sphere.core.data

/**
 * W60: a postDelayed-and-clock abstraction, so [MediaNow]'s confirm-or-escalate ladder is a plain unit test — "no callback arrived within
 * the window" is simulated by never firing the fake's timer, not by sleeping a real thread. Mirrors how [ControlsPop] already keeps its
 * own timer logic as pure data read against a passed-in `now`; this is the same idea for a singleton that lives outside Compose and must
 * actually schedule the timeout itself (there is no recomposition to re-run a LaunchedEffect's delay here).
 */
interface MediaScheduler {
    /** Same clock family as [ControlsPop] / [FixedHomeChrome] (uptime, not wall time: unaffected by the user setting the clock). */
    fun now(): Long

    /** Runs [action] once, after [delayMs] (clamped to at least 0), on the main thread. Returns a canceller; calling it after the action
     * already ran, or more than once, is a no-op. */
    fun postDelayed(delayMs: Long, action: () -> Unit): () -> Unit
}

/** The real scheduler: an [android.os.Handler] posting against [android.os.SystemClock.uptimeMillis]. */
class HandlerMediaScheduler(private val handler: android.os.Handler) : MediaScheduler {
    override fun now(): Long = android.os.SystemClock.uptimeMillis()

    override fun postDelayed(delayMs: Long, action: () -> Unit): () -> Unit {
        val r = Runnable(action)
        handler.postDelayed(r, delayMs.coerceAtLeast(0L))
        return { handler.removeCallbacks(r) }
    }
}
