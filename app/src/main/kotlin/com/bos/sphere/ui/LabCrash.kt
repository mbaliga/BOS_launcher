package com.bos.sphere.ui

import androidx.compose.runtime.snapshots.SnapshotStateMap
import com.bos.sphere.core.design.L10n

/**
 * CFG-17, NFR-09 (wave 18): the Lab row "Crash on purpose (test the recovery screen)". The first tap arms it, a second tap within
 * [CONFIRM_WINDOW_MS] throws from a background thread (uncaught, so the shared crash-recovery handler captures it and the next start
 * shows its recovery screen). The Lab category itself is behind [LabGate].
 */
object LabCrash {
    const val ID = "crashOnPurpose"
    const val CONFIRM_WINDOW_MS = 5_000L

    /** True when a tap at [now] confirms an arming at [armedAt] (null when not armed). */
    fun confirmed(armedAt: Long?, now: Long): Boolean = armedAt != null && now - armedAt in 0..CONFIRM_WINDOW_MS

    /** The error the test throws; named so the report is unmistakably a test. */
    fun testError() = IllegalStateException("Crash on purpose from the Lab (testing the recovery screen)")

    /** Throws [testError] uncaught from a background thread. */
    fun fire(): Thread = Thread { throw testError() }.apply { name = "liftoff-lab-crash"; start() }

    fun entry(results: SnapshotStateMap<String, String>, armedAt: () -> Long?, setArmed: (Long?) -> Unit, now: () -> Long = System::currentTimeMillis, thrower: () -> Unit = { fire() }): LabEntry =
        LabEntry(ID, L10n.tr("Crash on purpose (test the recovery screen)"),
            results[ID] ?: L10n.tr("Tap to arm. A second tap within 5 seconds throws an error from a background thread; Liftoff stops and shows the recovery screen when it starts again."),
            {
                if (confirmed(armedAt(), now())) { setArmed(null); thrower() }
                else { setArmed(now()); results[ID] = L10n.tr("Armed. Tap again within 5 seconds to crash Liftoff on purpose.") }
            })
}
