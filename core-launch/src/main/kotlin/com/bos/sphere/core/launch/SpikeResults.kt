package com.bos.sphere.core.launch

import android.content.Context
import com.bos.sphere.core.capabilities.CapabilityInputs

/** What a person saw when a Lab spike ran (SPK-02, SPK-03). */
enum class SpikeOutcome { Untested, Proven, NotProven }

interface SpikeStorage { fun get(key: String): String?; fun put(key: String, value: String) }

/** Spike outcomes recorded from the Lab page. Unproven means unsupported: the matching actions stay hidden (WSP-03). */
class SpikeResults(private val storage: SpikeStorage) {
    fun outcome(spikeId: String): SpikeOutcome =
        runCatching { SpikeOutcome.valueOf(storage.get("spike_$spikeId") ?: "") }.getOrDefault(SpikeOutcome.Untested)

    fun record(spikeId: String, outcome: SpikeOutcome) = storage.put("spike_$spikeId", outcome.name)

    /** SPK-02: null until the Lab has been used, then true or false. */
    fun adjacentSupported(): Boolean? = when (outcome(SPK_ADJACENT)) { SpikeOutcome.Proven -> true; SpikeOutcome.NotProven -> false; SpikeOutcome.Untested -> null }

    /** SPK-03: bounds are honoured when desktop windowing is reported or the spike proved it. */
    fun boundsProven(): Boolean = outcome(SPK_BOUNDS) == SpikeOutcome.Proven

    companion object {
        const val SPK_ADJACENT = "SPK-02"
        const val SPK_BOUNDS = "SPK-03"

        fun from(context: Context): SpikeResults {
            val sp = context.applicationContext.getSharedPreferences("liftoff_spikes", Context.MODE_PRIVATE)
            return SpikeResults(object : SpikeStorage {
                override fun get(key: String) = sp.getString(key, null)
                override fun put(key: String, value: String) { sp.edit().putString(key, value).apply() }
            })
        }
    }
}

/** Launch capabilities from the device snapshot plus the recorded spike results. */
object LaunchCapsFactory {
    fun from(i: CapabilityInputs, spikes: SpikeResults): LaunchCaps = LaunchCaps(
        displayIds = i.displays.map { it.id }.toSet(),
        hostingDisplayIds = i.displays.filter { it.canHostTasks == true }.map { it.id }.toSet(),
        boundsHonoured = i.desktopWindowing == true || spikes.boundsProven(),
        adjacentSupported = spikes.adjacentSupported(),
        defaultDisplayId = i.displays.firstOrNull { it.isDefault }?.id ?: 0,
    )

    fun displayRefs(i: CapabilityInputs): List<DisplayRef> = i.displays.map { DisplayRef(it.id, it.name) }
}
