package com.bos.sphere.feature.dock

import com.bos.sphere.core.capabilities.Tier
import com.bos.sphere.core.data.MediaInfo
import com.bos.sphere.core.input.LauncherCommand

/** A pinned thing, by kind and id. The folder and shortcut kinds are defined by the home stream; the dock only carries the reference. */
enum class ItemKind { App, Shortcut, Folder, Group }

data class DockItemRef(val kind: ItemKind, val id: String) {
    fun encode(): String = kind.name.lowercase() + ":" + id

    companion object {
        fun app(key: String) = DockItemRef(ItemKind.App, key)
        fun group(id: String) = DockItemRef(ItemKind.Group, id)
        fun decode(s: String): DockItemRef? {
            val at = s.indexOf(':')
            if (at <= 0) return null
            val kind = ItemKind.entries.firstOrNull { it.name.equals(s.substring(0, at), ignoreCase = true) } ?: return null
            return DockItemRef(kind, s.substring(at + 1))
        }
    }
}

enum class DockMode(val label: String) { Collapsed("Collapsed band"), PinnedPlusRecent("Pinned and recent") }

enum class DockEdge(val label: String) { Bottom("Bottom"), Left("Left"), Right("Right") }

/** User choices for the dock (DCK-04). [enabled] null means "use the tier default". */
data class DockSettings(
    val enabled: Boolean? = null,
    val mode: DockMode = DockMode.PinnedPlusRecent,
    val edge: DockEdge = DockEdge.Bottom,
) {
    /** Off by default in T1, on by default in T2 and T3 (spec section 9). */
    fun isEnabled(tier: Tier): Boolean = enabled ?: (tier != Tier.T1)

    /** In portrait the dock sits at the bottom; in landscape it may sit on the bottom or either side. */
    fun edgeFor(landscape: Boolean): DockEdge = if (landscape) edge else DockEdge.Bottom
}

/** Pin list edits (DCK-01). Every function returns a new list. */
object DockPins {
    fun add(pins: List<DockItemRef>, ref: DockItemRef): List<DockItemRef> = if (ref in pins) pins else pins + ref
    fun remove(pins: List<DockItemRef>, ref: DockItemRef): List<DockItemRef> = pins - ref
    fun move(pins: List<DockItemRef>, from: Int, to: Int): List<DockItemRef> {
        if (from !in pins.indices) return pins
        val target = to.coerceIn(0, pins.lastIndex)
        if (target == from) return pins
        return pins.toMutableList().also { val x = it.removeAt(from); it.add(target, x) }
    }
    /** Keyboard reorder: one step earlier (-1) or later (+1). */
    fun moveBy(pins: List<DockItemRef>, ref: DockItemRef, delta: Int): List<DockItemRef> {
        val i = pins.indexOf(ref)
        return if (i < 0) pins else move(pins, i, i + delta)
    }
}

/** Drag maths for the rail: which slot a dragged item has reached. */
object DockReorder {
    fun targetIndex(from: Int, dragPx: Float, itemPx: Float, count: Int): Int {
        if (count <= 0 || itemPx <= 0f) return from
        return (from + Math.round(dragPx / itemPx)).coerceIn(0, count - 1)
    }
}

data class DockItem(val ref: DockItemRef, val label: String, val recentlyUsed: Boolean = false)

/** What the rail shows, already decided (DCK-02, DCK-03, DCK-06, DCK-08). */
data class DockContent(
    val pinned: List<DockItem>,
    val recent: List<DockItem>,
    /** True when usage access is missing: the Recent section is replaced by one enable row (DCK-06). */
    val showEnableRow: Boolean,
    val showRecentSection: Boolean,
    val media: MediaInfo?,
    val collapsed: Boolean,
) {
    /** Items that number shortcuts 1 to 9 launch, in the order the rail shows them. */
    val numbered: List<DockItem> get() = (pinned + recent).take(9)
}

object DockText {
    /** The section is called Recent and never Running: Android gives a launcher no list of live tasks (DCK-02). */
    const val RECENT_LABEL = "Recent"
    const val RECENCY_HELP = "A dot marks a pinned app you used in this session. Recent lists apps you used lately. Liftoff cannot see which apps are running."
    const val ENABLE_ROW = "Allow usage access to see recent apps"
}

object DockContentBuilder {
    /**
     * @param labelOf label for a pinned reference, or null when it cannot be shown now (an uninstalled app); such pins are kept in storage and skipped here.
     * @param recentApps app ids (the same ids the pins use), newest first.
     * @param sessionUsed app ids used in this session, for the recency marker.
     */
    fun build(
        pins: List<DockItemRef>,
        labelOf: (DockItemRef) -> String?,
        recentApps: List<String>,
        recentLabelOf: (String) -> String?,
        usageGranted: Boolean,
        mode: DockMode,
        sessionUsed: Set<String>,
        media: MediaInfo?,
        recentLimit: Int = 6,
    ): DockContent {
        val pinned = pins.mapNotNull { ref ->
            labelOf(ref)?.let { DockItem(ref, it, recentlyUsed = ref.kind == ItemKind.App && ref.id in sessionUsed) }
        }
        val pinnedIds = pins.filter { it.kind == ItemKind.App }.mapTo(HashSet()) { it.id }
        val showRecent = mode == DockMode.PinnedPlusRecent
        val recent = if (showRecent && usageGranted) {
            recentApps.asSequence().filter { it !in pinnedIds }.distinct()
                .mapNotNull { id -> recentLabelOf(id)?.let { DockItem(DockItemRef.app(id), it) } }
                .take(recentLimit).toList()
        } else emptyList()
        return DockContent(
            pinned = pinned, recent = recent,
            showEnableRow = showRecent && !usageGranted,
            showRecentSection = showRecent && usageGranted,
            media = media, collapsed = mode == DockMode.Collapsed,
        )
    }
}

/** Keys inside the rail (DCK-01, DCK-05): arrows move focus, Alt plus an arrow reorders, Enter opens, Escape leaves. */
enum class DockKeyAction { FocusPrevious, FocusNext, MoveEarlier, MoveLater, Activate, Leave, None }

object DockKeys {
    const val DPAD_UP = 19; const val DPAD_DOWN = 20; const val DPAD_LEFT = 21; const val DPAD_RIGHT = 22
    const val ENTER = 66; const val NUMPAD_ENTER = 160; const val SPACE = 62; const val ESCAPE = 111

    fun interpret(keyCode: Int, alt: Boolean, horizontal: Boolean): DockKeyAction {
        val prev = if (horizontal) DPAD_LEFT else DPAD_UP
        val next = if (horizontal) DPAD_RIGHT else DPAD_DOWN
        return when (keyCode) {
            prev -> if (alt) DockKeyAction.MoveEarlier else DockKeyAction.FocusPrevious
            next -> if (alt) DockKeyAction.MoveLater else DockKeyAction.FocusNext
            ENTER, NUMPAD_ENTER, SPACE -> DockKeyAction.Activate
            ESCAPE -> DockKeyAction.Leave
            else -> DockKeyAction.None
        }
    }

    fun step(index: Int, delta: Int, count: Int): Int = if (count <= 0) 0 else (index + delta).coerceIn(0, count - 1)
}

/** Handlers for the launcher commands that belong to the dock; the input layer calls [handle] with whatever the shortcut registry resolved. */
class DockCommandHandler(private val focusDock: () -> Unit, private val launchNth: (Int) -> Boolean) {
    fun handle(command: LauncherCommand): Boolean {
        if (command == LauncherCommand.FocusDock) { focusDock(); return true }
        val n = numberOf(command) ?: return false
        return launchNth(n - 1)
    }

    companion object {
        fun numberOf(c: LauncherCommand): Int? = if (c.name.startsWith("Dock") && c.name.length == 5) c.name[4].digitToIntOrNull()?.takeIf { it in 1..9 } else null
    }
}

/** One dock per display with a launcher surface; they all read and write the same pins (DCK-07). */
class DockHub(private val pins: () -> List<DockItemRef>, private val setPins: (List<DockItemRef>) -> Unit) {
    inner class Instance(val displayId: Int) {
        var expanded: Boolean = false
        var focusIndex: Int = -1
        val sharedPins: List<DockItemRef> get() = pins()
        fun pin(ref: DockItemRef) = setPins(DockPins.add(pins(), ref))
        fun unpin(ref: DockItemRef) = setPins(DockPins.remove(pins(), ref))
        fun reorder(from: Int, to: Int) = setPins(DockPins.move(pins(), from, to))
    }

    private val instances = LinkedHashMap<Int, Instance>()
    fun forDisplay(displayId: Int): Instance = instances.getOrPut(displayId) { Instance(displayId) }
    fun displays(): Set<Int> = instances.keys
    /** Drops the instance for a display that was disconnected; the pins stay. */
    fun release(displayId: Int) { instances.remove(displayId) }
}
