package com.bos.sphere.feature.dock

/**
 * The widget slots a workspace remembers (WSP-07). Each is an opaque id the home understands; this object only shapes the
 * list so that "never saved" (empty list) is told apart from "saved, and there were no widgets" (just the marker).
 */
object WorkspaceWidgets {
    const val MARKER = "v1"
    private const val LEFT = "left:"
    private const val CORNER = "corner:"

    sealed interface Slot {
        /** The widget on the left page. */
        data class Left(val appWidgetId: Int) : Slot
        /** A widget peeking out of the named corner. */
        data class Corner(val corner: String, val appWidgetId: Int, val label: String) : Slot
    }

    fun encode(slots: List<Slot>): List<String> = listOf(MARKER) + slots.map {
        when (it) {
            is Slot.Left -> LEFT + it.appWidgetId
            is Slot.Corner -> CORNER + it.corner + ":" + it.appWidgetId + ":" + it.label
        }
    }

    fun decode(ids: List<String>): List<Slot> = ids.mapNotNull { s ->
        when {
            s.startsWith(LEFT) -> s.removePrefix(LEFT).toIntOrNull()?.let { Slot.Left(it) }
            s.startsWith(CORNER) -> s.removePrefix(CORNER).split(":", limit = 3).takeIf { it.size >= 2 }?.let { p ->
                p[1].toIntOrNull()?.let { id -> Slot.Corner(p[0], id, p.getOrElse(2) { "Widget" }) }
            }
            else -> null
        }
    }

    /** True when [ids] is a snapshot (even an empty one) rather than "nothing saved yet". */
    fun isSnapshot(ids: List<String>) = ids.isNotEmpty() && ids.first() == MARKER
}
