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
        val sp = TolerantPrefs.of(context, FILE)
        val prev = if (sp.contains("last")) CrashLoopMath.State(sp.getLong("last", 0), sp.getBoolean("stable", false), sp.getInt("rapid", 0)) else null
        val (next, safe) = CrashLoopMath.decide(prev, now)
        safeMode = safe
        save(sp, next)
    }

    /** Wave 18: the shared crash-recovery streak says the app crashed again after Continue, so this start is safe mode too. */
    fun enterSafeMode() { safeMode = true }

    /** Call after the launcher has stayed up for [CrashLoopMath.WINDOW_MS]. */
    fun onStable(context: Context) {
        val sp = TolerantPrefs.of(context, FILE)
        save(sp, CrashLoopMath.markStable(CrashLoopMath.State(sp.getLong("last", 0), sp.getBoolean("stable", false), sp.getInt("rapid", 0))))
    }

    /** Leave safe mode (the user chose to carry on). */
    fun clear(context: Context) { safeMode = false; onStable(context) }

    private fun save(sp: android.content.SharedPreferences, s: CrashLoopMath.State) {
        sp.edit().putLong("last", s.lastStart).putBoolean("stable", s.lastWasStable).putInt("rapid", s.rapidStarts).apply()
    }
}

/**
 * Wave 18 (CFG-09, docs/decisions/crashRecovery.md): which screen a start shows once the shared crash-recovery utility is in
 * front of the launcher. Pure, so the sequence is testable without Android.
 *
 *  1. A pending crash report: the library's recovery screen (splash, readable report, Share). The activity returns at once.
 *  2. Otherwise, the crash streak is at least 2 (the user continued past the recovery screen and the app crashed again within
 *     the streak window) or [CrashLoopGuard] says the last two starts were rapid: the safe mode screen, the SECOND layer.
 *  3. Otherwise the normal home.
 */
object RecoveryDecision {
    enum class Screen { Recovery, SafeMode, Home }

    /** The library's streak count at which a crash that already had its recovery screen counts as a loop. */
    const val LOOP_STREAK = 2

    fun screen(pendingReport: Boolean, guardSaysSafe: Boolean, streak: Int): Screen = when {
        pendingReport -> Screen.Recovery
        guardSaysSafe || streak >= LOOP_STREAK -> Screen.SafeMode
        else -> Screen.Home
    }
}
