package com.bos.sphere.feature.dock

import com.bos.sphere.core.capabilities.Tier
import com.bos.sphere.core.data.MediaInfo
import com.bos.sphere.core.input.LauncherCommand

/** A pinned thing, by kind and id. The folder and shortcut kinds are defined by the home stream; the dock only carries the reference. */
enum class ItemKind { App, Shortcut, Folder, Group, Link, Widget }

/**
 * HOM-02: home items that are not apps (a folder, a launcher shortcut, a web link or file) pinned to the rail.
 * The rail's pin stores a [DockItemRef]; this maps the home item key to one and back, with no catalog dependency.
 */
object HomeItemPins {
    private const val FOLDER = "folder:"
    private const val SHORTCUT = "shortcut:"
    private const val LINK = "link:"
    private const val WIDGET = "widget:"

    fun refOf(homeKey: String): DockItemRef? = when {
        homeKey.startsWith(FOLDER) -> DockItemRef(ItemKind.Folder, homeKey.removePrefix(FOLDER))
        homeKey.startsWith(SHORTCUT) -> DockItemRef(ItemKind.Shortcut, homeKey)
        homeKey.startsWith(LINK) -> DockItemRef(ItemKind.Link, homeKey)
        homeKey.startsWith(WIDGET) -> DockItemRef(ItemKind.Widget, homeKey)
        else -> null
    }

    /** The home item key a pin stands for, or null for apps, groups and other launcher pins (the Desktop item). */
    fun homeKeyOf(ref: DockItemRef): String? = when (ref.kind) {
        ItemKind.Folder -> FOLDER + ref.id
        ItemKind.Shortcut -> ref.id.takeIf { it.startsWith(SHORTCUT) }
        ItemKind.Link -> ref.id.takeIf { it.startsWith(LINK) }
        ItemKind.Widget -> ref.id.takeIf { it.startsWith(WIDGET) }
        else -> null
    }

    fun isPinned(pins: List<DockItemRef>, homeKey: String): Boolean = refOf(homeKey)?.let { it in pins } ?: false

    fun toggle(pins: List<DockItemRef>, homeKey: String): List<DockItemRef> {
        val r = refOf(homeKey) ?: return pins
        return if (r in pins) pins - r else pins + r
    }
}

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
    /** Saved app groups shown as chips on the home (WSP-05). */
    val groupShelf: Boolean = true,
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

/** [span] is how many rail slots the item takes along the rail: 1 for everything but a pinned widget (HOM-02). */
data class DockItem(val ref: DockItemRef, val label: String, val recentlyUsed: Boolean = false, val span: Int = 1)

/**
 * HOM-02: the rail's widget slot. A pinned widget takes its column span along the rail ([MAX_SPAN] slots at most, so one
 * widget cannot push the rest off a phone's rail); across the rail it is one slot thick, the rail's thickness, so the
 * widget's rows beyond the first are not shown there. The home keeps the full-size copy.
 */
object DockWidgetSlot {
    const val MAX_SPAN = 4
    const val SLOT_DP = DockFootprint.SLOT_DP
    const val GAP_DP = 8f

    fun slots(cols: Int): Int = cols.coerceIn(1, MAX_SPAN)

    /** Length along the rail of an item spanning [cols] slots, in dp. */
    fun lengthDp(cols: Int): Float = slots(cols) * SLOT_DP + (slots(cols) - 1) * GAP_DP
}

/**
 * HOM-02: `HomeState.dock` is the one list the rail reads. Each entry is a pin written as `pin:` plus a [DockItemRef]
 * encoding, so a pinned copy never collides with the key of the item it points at (that item stays on its home page).
 */
object HomeDockKeys {
    private const val PIN = "pin:"

    fun encode(ref: DockItemRef): String = PIN + ref.encode()

    /** An entry as a pin: `pin:` entries decode directly; an older plain home key (from "move to dock") becomes a pin of that item. */
    fun decode(entry: String): DockItemRef? =
        if (entry.startsWith(PIN)) DockItemRef.decode(entry.removePrefix(PIN))
        else HomeItemPins.refOf(entry) ?: entry.takeIf { it.isNotEmpty() }?.let(DockItemRef::app)

    fun decodeAll(dock: List<String>): List<DockItemRef> = dock.mapNotNull(::decode).distinct()

    fun encodeAll(pins: List<DockItemRef>): List<String> = pins.distinct().map(::encode)

    fun hasPins(dock: List<String>): Boolean = dock.any { it.startsWith(PIN) }

    /**
     * Migration of the old per-workspace pin store into `HomeState.dock`: the dock list is kept when it already holds pins;
     * otherwise the active workspace's pins seed it. Returns null when nothing changes.
     */
    fun migrate(dock: List<String>, activeWorkspacePins: List<DockItemRef>): List<String>? =
        if (hasPins(dock) || activeWorkspacePins.isEmpty()) null else encodeAll(activeWorkspacePins)

    /**
     * The dock list for a workspace being entered: its saved arrangement's dock when the arrangement has one, else its
     * own legacy pins (a workspace saved before the dock moved into the arrangement keeps its pins).
     */
    fun forWorkspace(arrangementDock: List<String>?, legacyPins: List<DockItemRef>): List<String> =
        arrangementDock ?: encodeAll(legacyPins)

    /** The `dock` array of a saved home arrangement (the JSON the workspace hooks write), or null when it has none. */
    fun dockOf(arrangement: String?): List<String>? {
        if (arrangement == null) return null
        return runCatching {
            org.json.JSONObject(arrangement).optJSONArray("dock")?.let { a -> List(a.length()) { a.getString(it) } }
        }.getOrNull()
    }
}

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
        spanOf: (DockItemRef) -> Int = { 1 },
    ): DockContent {
        val pinned = pins.mapNotNull { ref ->
            labelOf(ref)?.let { DockItem(ref, it, recentlyUsed = ref.kind == ItemKind.App && ref.id in sessionUsed, span = if (ref.kind == ItemKind.Widget) DockWidgetSlot.slots(spanOf(ref)) else 1) }
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
