package com.bos.sphere.ui

import android.content.Context
import android.os.Build
import android.os.PowerManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SnapshotMutationPolicy
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.platform.LocalContext
import com.bos.sphere.core.capabilities.CapabilityEvaluator
import com.bos.sphere.core.capabilities.CapabilityInputs
import com.bos.sphere.core.launch.Bounds
import com.bos.sphere.core.launch.BoundsPreset
import com.bos.sphere.core.launch.LaunchCaps
import com.bos.sphere.core.launch.LaunchExecutor
import com.bos.sphere.core.launch.LaunchRequest
import com.bos.sphere.core.launch.LaunchTarget
import com.bos.sphere.core.launch.Placement
import com.bos.sphere.core.launch.SpikeOutcome
import com.bos.sphere.core.launch.SpikeResults

/** Launch capabilities as far as the device and the Lab spikes have shown (an unproven adjacent launch stays "unsupported"). */
fun launchCaps(i: CapabilityInputs, adjacentProven: Boolean?): LaunchCaps = LaunchCaps(
    displayIds = i.displays.map { it.id }.toSet(),
    hostingDisplayIds = i.displays.filter { it.canHostTasks == true }.map { it.id }.toSet(),
    boundsHonoured = i.desktopWindowing == true,
    adjacentSupported = adjacentProven,
)

/**
 * The Lab page (CFG-17): each platform spike from the spec is a button that performs the check on this device and
 * shows a result to copy back into `docs/capabilities.md`. Spikes that need something only a person can see
 * (does it split? does it move?) launch the test and say what to look for.
 */
@Composable
fun rememberLabEntries(inputs: CapabilityInputs, results: SnapshotStateMap<String, String>): List<LabEntry> {
    val context = LocalContext.current
    val app = context.applicationContext
    return remember(inputs, results.toMap()) {
        fun settingsTarget() = LaunchTarget("com.android.settings")
        val exec = LaunchExecutor(app, caps = { launchCaps(inputs, adjacentProven = true) })
        fun put(id: String, text: String) { results[id] = text }
        val spikes = SpikeResults.from(app)
        fun word(o: SpikeOutcome) = when (o) { SpikeOutcome.Proven -> "proven"; SpikeOutcome.NotProven -> "not proven"; SpikeOutcome.Untested -> "not tested yet" }
        fun mark(id: String, spike: String, outcome: SpikeOutcome) { spikes.record(spike, outcome); put(id, "Recorded for $spike: ${word(outcome)}") }
        fun recorded(id: String, spike: String) = results[id] ?: "Recorded for $spike: ${word(spikes.outcome(spike))}"
        listOf(
            LabEntry("env", "Device", "${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) · tier ${CapabilityEvaluator.tier(inputs).name}", null),
            LabEntry("spk01", "SPK-01 Displays", results["spk01"] ?: (inputs.displays.joinToString("\n") { "#${it.id} ${it.name} ${it.widthPx}x${it.heightPx} @${it.densityDpi}dpi" + if (it.isDefault) " (default)" else " hosts-tasks=${it.canHostTasks}" }.ifEmpty { "No displays read" } + "\nConnect an external display, reopen this page, and note whether Liftoff became its home."), null),
            LabEntry("spk02", "SPK-02 Open beside", results["spk02"] ?: "Tap to open Settings beside Liftoff", {
                val r = exec.launch(LaunchRequest(settingsTarget(), placement = Placement.Adjacent))
                put("spk02", "Started=${r.started}. Look at the screen: did it enter split, replace Liftoff, or just open full screen? Note it with the Android version.")
            }),
            LabEntry("spk02_yes", "SPK-02 · It worked", recorded("spk02_rec", SpikeResults.SPK_ADJACENT) + ". Open beside shows in action lists only when proven.", { mark("spk02_rec", SpikeResults.SPK_ADJACENT, SpikeOutcome.Proven) }),
            LabEntry("spk02_no", "SPK-02 · It did not", "Records that opening beside did not split the screen; the action stays hidden.", { mark("spk02_rec", SpikeResults.SPK_ADJACENT, SpikeOutcome.NotProven) }),
            LabEntry("spk03", "SPK-03 Launch bounds", results["spk03"] ?: "Tap to open Settings in the left half", {
                val d = inputs.displays.firstOrNull { it.isDefault }
                val b = d?.let { BoundsPreset.LeftHalf.on(it.widthPx, it.heightPx) } ?: Bounds(0, 0, 600, 800)
                // The resolver would drop bounds where desktop windowing is absent; the spike asks the platform regardless.
                val caps = launchCaps(inputs, true).copy(boundsHonoured = true)
                val r = LaunchExecutor(app, caps = { caps }).launch(LaunchRequest(settingsTarget(), bounds = b))
                put("spk03", "Started=${r.started}, bounds=$b. Did the window take the left half, or open normally?")
            }),
            LabEntry("spk03_yes", "SPK-03 · It worked", recorded("spk03_rec", SpikeResults.SPK_BOUNDS) + ". Bounds presets show in action lists only when proven or when desktop windowing is reported.", { mark("spk03_rec", SpikeResults.SPK_BOUNDS, SpikeOutcome.Proven) }),
            LabEntry("spk03_no", "SPK-03 · It did not", "Records that the window did not take the requested bounds.", { mark("spk03_rec", SpikeResults.SPK_BOUNDS, SpikeOutcome.NotProven) }),
            LabEntry("spk04", "SPK-04 Notification shade", "No stable public route is probed in the core build; the curtain renders listener data instead (STR-12 stays elevated-tier).", null),
            LabEntry("spk05", "SPK-05 Drops onto home", "Not built yet: needs a drop target on home (INP-11).", null),
            LabEntry("spk06", "SPK-06 OEM limits", results["spk06"] ?: "Tap to read battery-optimisation state", {
                val pm = app.getSystemService(PowerManager::class.java)
                put("spk06", "${Build.MANUFACTURER} · ignoring battery optimisation=${pm.isIgnoringBatteryOptimizations(app.packageName)}. Note whether gesture navigation worked with Liftoff as home and whether it was killed in the background.")
            }),
            LabEntry("spk07", "SPK-07 On-device speech", "present=${inputs.speechOnDevice ?: "unknown"}", null),
            LabEntry("input", "Input devices", "keyboard=${inputs.hasKeyboard} pointer=${inputs.hasPointer} controller=${inputs.hasController}", null),
        )
    }
}
