package com.bos.sphere.core.data

/**
 * STR-14: what the right-hand side of the home holds. The mirror image of Launchpad on the left. The dock stays optional everywhere:
 * choosing [DockRail] only decides that the right-edge swipe reveals the existing dock rail, it never turns the dock on.
 */
enum class RightSide(val label: String) {
    /** The Recents panel: a swipe from the right edge shrinks the home into a card on the left and lists the apps the user left most recently. */
    RecentsPanel("Recents panel"),
    /** The right-edge swipe reveals the existing dock rail instead. */
    DockRail("Dock rail"),
    /** Nothing on the right side. */
    Off("Off");

    companion object {
        /** A stored name, or [RecentsPanel] (the default) for a missing or unknown one. */
        fun parse(name: String?): RightSide = entries.firstOrNull { it.name == name } ?: RecentsPanel
    }
}

/** A place the right-hand side can lead to; the Glance surface indicator (HOM-23) draws one dot for each. */
enum class RightDestination { Recents, Dock }

/** STR-14: the small provider the Glance surface indicator reads: which destinations the right side currently offers, and what an edge swipe does. */
object RightSideDestinations {
    /** The destinations on the right, in order. Off: none. */
    fun of(side: RightSide): List<RightDestination> = when (side) {
        RightSide.RecentsPanel -> listOf(RightDestination.Recents)
        RightSide.DockRail -> listOf(RightDestination.Dock)
        RightSide.Off -> emptyList()
    }

    /** The right-edge swipe, the handle, the shortcut and the "Open recents" action open the panel only on [RightSide.RecentsPanel]. */
    fun opensRecentsPanel(side: RightSide): Boolean = side == RightSide.RecentsPanel

    /** On [RightSide.DockRail] the right-edge swipe reveals the dock rail (only when the dock is on; it is never turned on by this). */
    fun edgeRevealsDock(side: RightSide): Boolean = side == RightSide.DockRail

    /** The labelled name of a destination, for TalkBack and the indicator. */
    fun label(d: RightDestination): String = when (d) { RightDestination.Recents -> "Recent apps"; RightDestination.Dock -> "Dock" }
}

/** STR-14: how long ago, as plain buckets; the UI turns them into words in the user's language. */
sealed interface AgoBucket {
    data object JustNow : AgoBucket
    data class Minutes(val n: Int) : AgoBucket
    data class Hours(val n: Int) : AgoBucket
    data class Days(val n: Int) : AgoBucket
}

object RecentsAgo {
    fun bucket(now: Long, then: Long): AgoBucket {
        val min = ((now - then).coerceAtLeast(0L) / 60_000L).toInt()
        return when {
            min < 1 -> AgoBucket.JustNow
            min < 60 -> AgoBucket.Minutes(min)
            min < 60 * 24 -> AgoBucket.Hours(min / 60)
            else -> AgoBucket.Days(min / (60 * 24))
        }
    }
}
