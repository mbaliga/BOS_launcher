package com.bos.sphere.core.launch

/** How a group's windows are arranged (WSP-05). Tile splits the display, Full opens each full size, Cascade centres each. */
enum class GroupLayout(val label: String) {
    Tile("Tiled"), Full("Full size"), Cascade("Centred");

    /** One rectangle per member on a [width] x [height] display. */
    fun rects(count: Int, width: Int, height: Int): List<Bounds> = when (this) {
        Full -> List(count) { BoundsPreset.Full.on(width, height) }
        Cascade -> List(count) { BoundsPreset.Centred.on(width, height) }
        Tile -> when (count) {
            1 -> listOf(BoundsPreset.Full.on(width, height))
            2 -> listOf(BoundsPreset.LeftHalf.on(width, height), BoundsPreset.RightHalf.on(width, height))
            3 -> listOf(
                BoundsPreset.LeftHalf.on(width, height),
                Bounds(width / 2, 0, width, height / 2),
                Bounds(width / 2, height / 2, width, height),
            )
            else -> listOf(
                Bounds(0, 0, width / 2, height / 2), Bounds(width / 2, 0, width, height / 2),
                Bounds(0, height / 2, width / 2, height), Bounds(width / 2, height / 2, width, height),
            ).take(count)
        }
    }
}

/** How a group will be opened on this device (WSP-05): bounds where honoured, split where proven, else first app plus a switcher. */
enum class GroupMode { Bounds, Split, FirstOnly }

/** [steps] open in order; [switcher] lists the members offered as a quick switcher instead of opened (T1 with no split). */
data class GroupPlan(val mode: GroupMode, val steps: List<LaunchRequest>, val switcher: List<LaunchRequest>)

object GroupPlanner {
    fun plan(group: AppGroup, displayWidth: Int, displayHeight: Int, caps: LaunchCaps, targetDisplay: Int? = null): GroupPlan {
        val members = group.members.map { m -> if (targetDisplay != null) m.copy(displayId = targetDisplay) else m }
        return when {
            caps.boundsHonoured -> {
                val rects = group.layout.rects(members.size, displayWidth, displayHeight)
                GroupPlan(GroupMode.Bounds, members.mapIndexed { i, m -> m.copy(bounds = rects[i], placement = Placement.Default) }, emptyList())
            }
            caps.adjacentSupported == true ->
                GroupPlan(GroupMode.Split, members.mapIndexed { i, m -> m.copy(bounds = null, placement = if (i == 0) Placement.Default else Placement.Adjacent) }, emptyList())
            else -> GroupPlan(GroupMode.FirstOnly, listOf(members.first().copy(bounds = null, placement = Placement.Default)), members.drop(1))
        }
    }
}

/** Outcome of running a plan: one result per attempted step, the indexes that failed even after one retry, and the switcher list. */
data class GroupOutcome(val results: List<LaunchResult>, val failed: List<Int>, val switcher: List<LaunchRequest>)

object GroupRunner {
    /**
     * Opens each step in sequence with a short pause and re-checks every result: a step that did not start is retried once,
     * then recorded as failed while the rest still open (WSP-05).
     */
    suspend fun run(
        plan: GroupPlan,
        gapMs: Long = 350,
        pause: suspend (Long) -> Unit = { kotlinx.coroutines.delay(it) },
        start: (LaunchRequest) -> LaunchResult,
    ): GroupOutcome {
        val results = mutableListOf<LaunchResult>()
        val failed = mutableListOf<Int>()
        plan.steps.forEachIndexed { i, req ->
            var r = start(req)
            if (!r.started) { pause(gapMs); r = start(req) }
            results += r
            if (!r.started) failed += i
            if (i < plan.steps.lastIndex) pause(gapMs)
        }
        return GroupOutcome(results, failed, plan.switcher)
    }
}

object SourceRects {
    /** Screen rectangle of the source item expressed relative to the window's origin on screen. */
    fun relativeTo(source: Bounds, originX: Int, originY: Int): Bounds =
        Bounds(source.left - originX, source.top - originY, source.right - originX, source.bottom - originY)
}
