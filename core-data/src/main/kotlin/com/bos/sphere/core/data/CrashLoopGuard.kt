package com.bos.sphere.core.data

import android.content.Context

/**
 * CFG-09: safe mode is for a launcher that CRASHED twice, not for one that was started twice (docs/decisions/startupCrashes.md,
 * wave 19, the owner's decision). Two counted crashes, the second within 30 seconds of the first run's start, put Liftoff into safe mode.
 *
 * What counts as a crash: the previous start left its marker set ("starting", never cleared by 30 s of stability or a clean finish) AND the
 * previous process's exit record is crash-shaped ([endedInCrash]). A user-requested stop, a package replacement (an install over the
 * running launcher), a low-memory kill or the system removing the task never count: they leave the marker set but not a crash reason.
 *
 * The decision is pure ([decide], [endedInCrash]); [CrashLoopGuard] stores the marker in SharedPreferences and reads the exit record.
 */
object CrashLoopMath {
    const val WINDOW_MS = 30_000L

    /** [lastWasStable] false is the "starting" marker: the previous start has not been marked stable. [crashes] is the counted crash streak. */
    data class State(val lastStart: Long, val lastWasStable: Boolean, val crashes: Int)

    /** The newest exit record of the previous process of this package: ApplicationExitInfo.reason and its timestamp. */
    data class Exit(val reason: Int, val timestamp: Long)

    /** ApplicationExitInfo.REASON_CRASH, REASON_CRASH_NATIVE, REASON_ANR, REASON_INITIALIZATION_FAILURE. Everything else is not a crash. */
    val CRASH_REASONS = setOf(
        android.app.ApplicationExitInfo.REASON_CRASH, android.app.ApplicationExitInfo.REASON_CRASH_NATIVE,
        android.app.ApplicationExitInfo.REASON_ANR, android.app.ApplicationExitInfo.REASON_INITIALIZATION_FAILURE,
    )

    /**
     * Did the previous start end in a crash? Needs the marker still set. From API 30 the newest exit record at or after the marker time must
     * be crash-shaped; below API 30 there is no exit history, so only a pending JVM crash report counts.
     */
    fun endedInCrash(sdk: Int, prev: State?, exit: Exit?, jvmReportPending: Boolean): Boolean {
        if (prev == null || prev.lastWasStable) return false
        if (sdk < 30) return jvmReportPending
        return exit != null && exit.timestamp >= prev.lastStart && exit.reason in CRASH_REASONS
    }

    /**
     * The next state and whether this start should boot into safe mode. [previousEndedInCrash] comes from [endedInCrash]. A counted crash
     * extends the streak only when that run died within [WINDOW_MS] of its start; a slower crash starts the streak at one; any start that
     * does not follow a crash breaks it.
     */
    fun decide(prev: State?, now: Long, previousEndedInCrash: Boolean): Pair<State, Boolean> {
        if (prev == null) return State(now, false, 0) to false
        val crashes = when {
            !previousEndedInCrash -> 0
            now - prev.lastStart in 0 until WINDOW_MS -> prev.crashes + 1 // a clock set back (negative age) is not 'within 30 seconds'
            else -> 1
        }
        return State(now, false, crashes) to (crashes >= 2)
    }

    fun markStable(s: State) = s.copy(lastWasStable = true, crashes = 0)

    /**
     * Is this activity creation a start to record? A recreate in the SAME process (rotation, theme) is not; but the system also hands a saved
     * state to the first activity of a NEW process after a crash or a low-memory kill, and that start must be recorded or the loop is never seen.
     */
    fun countsAsStart(hasSavedState: Boolean, alreadyCountedInThisProcess: Boolean): Boolean = !hasSavedState || !alreadyCountedInThisProcess
}

object CrashLoopGuard {
    private const val FILE = "crash_loop"

    @Volatile var safeMode: Boolean = false
        private set

    /** True once [onStart] ran in this process (see [CrashLoopMath.countsAsStart]). */
    @Volatile var countedInThisProcess: Boolean = false
        private set

    /** Where the previous process's exit record comes from; replaced in tests. Never throws. */
    @Volatile var exitProbe: (Context) -> CrashLoopMath.Exit? = ::newestExit

    private fun newestExit(context: Context): CrashLoopMath.Exit? = runCatching {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        am.getHistoricalProcessExitReasons(context.packageName, 0, 8)
            .filter { it.processName == context.packageName }
            .maxByOrNull { it.timestamp }?.let { CrashLoopMath.Exit(it.reason, it.timestamp) }
    }.getOrNull()

    /** Call once from the launcher activity's onCreate. Writes the "starting" marker. */
    fun onStart(
        context: Context, now: Long = System.currentTimeMillis(),
        sdk: Int = android.os.Build.VERSION.SDK_INT, jvmReportSince: (Long) -> Boolean = { false },
    ) {
        countedInThisProcess = true
        val sp = TolerantPrefs.of(context, FILE)
        // "rapid" was the old start counter (waves before 19): it is ignored, so an old marker cannot count as a crash.
        val prev = if (sp.contains("last") && sp.contains("crashes")) CrashLoopMath.State(sp.getLong("last", 0), sp.getBoolean("stable", false), sp.getInt("crashes", 0)) else null
        val exit = if (prev != null && !prev.lastWasStable && sdk >= 30) exitProbe(context) else null
        val (next, safe) = CrashLoopMath.decide(prev, now, CrashLoopMath.endedInCrash(sdk, prev, exit, prev != null && sdk < 30 && jvmReportSince(prev.lastStart)))
        safeMode = safe
        save(sp, next)
    }

    /** Wave 18: the shared crash-recovery streak says the app crashed again after Continue, so this start is safe mode too. */
    fun enterSafeMode() { safeMode = true }

    /** Call after the launcher has stayed up for [CrashLoopMath.WINDOW_MS], or at a clean finish: clears the marker. */
    fun onStable(context: Context) {
        val sp = TolerantPrefs.of(context, FILE)
        save(sp, CrashLoopMath.markStable(CrashLoopMath.State(sp.getLong("last", 0), sp.getBoolean("stable", false), sp.getInt("crashes", 0))))
    }

    /** Leave safe mode (the user chose to carry on). */
    fun clear(context: Context) { safeMode = false; onStable(context) }

    private fun save(sp: android.content.SharedPreferences, s: CrashLoopMath.State) {
        sp.edit().remove("rapid").putLong("last", s.lastStart).putBoolean("stable", s.lastWasStable).putInt("crashes", s.crashes).apply()
    }
}

/**
 * Wave 18 (CFG-09, docs/decisions/crashRecovery.md): which screen a start shows once the shared crash-recovery utility is in
 * front of the launcher. Pure, so the sequence is testable without Android.
 *
 *  1. A pending crash report: the library's recovery screen (splash, readable report, Share). The activity returns at once.
 *  2. Otherwise, the crash streak is at least 2 (the user continued past the recovery screen and the app crashed again within
 *     the streak window) or [CrashLoopGuard] says the last two starts ended in a counted crash (wave 19: crash evidence, not rapid starts): the safe mode screen, the SECOND layer.
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
