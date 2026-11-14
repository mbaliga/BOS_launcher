package com.bos.sphere.feature.desktop

/** One display as the desktop feature sees it. Plain data so the state machine runs on the JVM (EXT-01). */
data class TrackedDisplay(
    val id: Int,
    val name: String,
    val widthPx: Int,
    val heightPx: Int,
    val densityDpi: Int,
    val isDefault: Boolean,
    /** Null when the platform does not say. Only an explicit true makes a display eligible. */
    val canHostTasks: Boolean?,
    /** Stable key for per-display profiles (EXT-09), see [DisplayIdentity]. */
    val identity: String,
    /** True when the display only mirrors the phone (EXT-11). */
    val mirrored: Boolean = false,
) {
    /** A display the desktop surface may be offered on. */
    val eligible: Boolean get() = !isDefault && canHostTasks == true && !mirrored
}

sealed interface DisplayEvent {
    data class Added(val display: TrackedDisplay) : DisplayEvent
    data class Changed(val display: TrackedDisplay) : DisplayEvent
    data class Removed(val id: Int) : DisplayEvent
}

sealed interface DisplayEffect {
    data class Connected(val display: TrackedDisplay) : DisplayEffect
    data class Updated(val display: TrackedDisplay) : DisplayEffect
    data class Disconnected(val display: TrackedDisplay) : DisplayEffect
}

data class TrackerState(val displays: Map<Int, TrackedDisplay> = emptyMap()) {
    val external: List<TrackedDisplay> get() = displays.values.filter { !it.isDefault }.sortedBy { it.id }
    val eligible: List<TrackedDisplay> get() = external.filter { it.eligible }
    val primary: TrackedDisplay? get() = displays.values.firstOrNull { it.isDefault }
}

/**
 * Pure display state machine (EXT-01). Every callback from `DisplayManager` becomes one synchronous
 * [reduce] or [sync] call, so connect and disconnect take effect in the same message-loop turn
 * (well inside the 1 second limit). Duplicate and unknown events are harmless, which is what keeps a
 * flapping cable from crashing or losing state.
 */
object DisplayTracker {
    fun reduce(state: TrackerState, event: DisplayEvent): Pair<TrackerState, List<DisplayEffect>> = when (event) {
        is DisplayEvent.Added -> upsert(state, event.display)
        is DisplayEvent.Changed -> upsert(state, event.display)
        is DisplayEvent.Removed -> {
            val gone = state.displays[event.id]
            if (gone == null) state to emptyList()
            else TrackerState(state.displays - event.id) to listOf(DisplayEffect.Disconnected(gone))
        }
    }

    /** Brings [state] in line with a full snapshot of the connected displays; robust against missed callbacks. */
    fun sync(state: TrackerState, snapshot: List<TrackedDisplay>): Pair<TrackerState, List<DisplayEffect>> {
        var cur = state
        val effects = mutableListOf<DisplayEffect>()
        val ids = snapshot.map { it.id }.toSet()
        for (id in state.displays.keys.sorted()) if (id !in ids) {
            val (s, e) = reduce(cur, DisplayEvent.Removed(id)); cur = s; effects += e
        }
        for (d in snapshot) {
            val (s, e) = reduce(cur, DisplayEvent.Changed(d)); cur = s; effects += e
        }
        return cur to effects
    }

    private fun upsert(state: TrackerState, d: TrackedDisplay): Pair<TrackerState, List<DisplayEffect>> {
        val old = state.displays[d.id]
        val next = TrackerState(state.displays + (d.id to d))
        return when {
            old == null -> next to listOf(DisplayEffect.Connected(d))
            old == d -> state to emptyList()
            else -> next to listOf(DisplayEffect.Updated(d))
        }
    }

    /**
     * Mirroring cannot be read from a public API, so it is inferred from capability data only (EXT-11):
     * a non-default display with the same pixel size as the default one that cannot host tasks.
     */
    fun looksMirrored(d: TrackedDisplay, primary: TrackedDisplay?): Boolean =
        primary != null && !d.isDefault && d.canHostTasks != true &&
            d.widthPx == primary.widthPx && d.heightPx == primary.heightPx
}
