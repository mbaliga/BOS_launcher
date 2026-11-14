package com.bos.sphere.core.data

import android.content.Context

/**
 * CFG-09: a crash loop in the default launcher can leave a phone hard to use, so two starts within 30 seconds of
 * each other, with no start having survived that long in between, put Liftoff into safe mode.
 *
 * The decision is pure ([decide]); [CrashLoopGuard] stores the start times in SharedPreferences.
 */
object CrashLoopMath {
    const val WINDOW_MS = 30_000L

    data class State(val lastStart: Long, val lastWasStable: Boolean, val rapidStarts: Int)

    /** The next state and whether this start should boot into safe mode. */
    fun decide(prev: State?, now: Long): Pair<State, Boolean> {
        if (prev == null) return State(now, false, 0) to false
        val rapid = if (!prev.lastWasStable && now - prev.lastStart < WINDOW_MS) prev.rapidStarts + 1 else 0
        return State(now, false, rapid) to (rapid >= 1)
    }

    fun markStable(s: State) = s.copy(lastWasStable = true, rapidStarts = 0)
}

object CrashLoopGuard {
    private const val FILE = "crash_loop"

    @Volatile var safeMode: Boolean = false
        private set

    /** Call once from the launcher activity's onCreate. */
    fun onStart(context: Context, now: Long = System.currentTimeMillis()) {
        val sp = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        val prev = if (sp.contains("last")) CrashLoopMath.State(sp.getLong("last", 0), sp.getBoolean("stable", false), sp.getInt("rapid", 0)) else null
        val (next, safe) = CrashLoopMath.decide(prev, now)
        safeMode = safe
        save(sp, next)
    }

    /** Call after the launcher has stayed up for [CrashLoopMath.WINDOW_MS]. */
    fun onStable(context: Context) {
        val sp = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        save(sp, CrashLoopMath.markStable(CrashLoopMath.State(sp.getLong("last", 0), sp.getBoolean("stable", false), sp.getInt("rapid", 0))))
    }

    /** Leave safe mode (the user chose to carry on). */
    fun clear(context: Context) { safeMode = false; onStable(context) }

    private fun save(sp: android.content.SharedPreferences, s: CrashLoopMath.State) {
        sp.edit().putLong("last", s.lastStart).putBoolean("stable", s.lastWasStable).putInt("rapid", s.rapidStarts).apply()
    }
}
