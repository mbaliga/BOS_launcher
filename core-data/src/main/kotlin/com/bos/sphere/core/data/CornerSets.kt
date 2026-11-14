package com.bos.sphere.core.data

/** What a corner is assigned to, as the settings list names it (INP-15). */
enum class CornerAssignKind(val label: String) {
    App("App"), Shortcut("Shortcut"), LauncherAction("Launcher action"), AppGroup("App group"), Nothing("Nothing"), Other("Other");

    companion object {
        /** The kinds a user can pick. [Shortcut] is an app shortcut key (`pkg/id` style key, see [shortcutKey]). */
        val Pickable = listOf(App, Shortcut, LauncherAction, AppGroup, Nothing)
    }
}

/** Pure helpers for corner assignments, orientation sets and the keyboard bindings that reach them. */
object CornerSets {
    /** Prefix that marks an app-shortcut key stored in a [CornerItem.App]. */
    const val SHORTCUT_PREFIX = "shortcut:"

    fun shortcutKey(id: String) = SHORTCUT_PREFIX + id

    fun kindOf(slot: CornerSlot): CornerAssignKind = when (slot) {
        CornerSlot.Empty -> CornerAssignKind.Nothing
        is CornerSlot.Single -> when (val i = slot.item) {
            is CornerItem.Action -> CornerAssignKind.LauncherAction
            is CornerItem.App -> if (i.key.startsWith(SHORTCUT_PREFIX)) CornerAssignKind.Shortcut else CornerAssignKind.App
        }
        is CornerSlot.Palette -> CornerAssignKind.AppGroup
        else -> CornerAssignKind.Other
    }

    /** Builds a slot for a chosen kind, so every style gets the same assignment shape. */
    fun slotFor(kind: CornerAssignKind, action: CornerActionId? = null, appKey: String? = null, group: String? = null, members: List<CornerItem> = emptyList()): CornerSlot = when (kind) {
        CornerAssignKind.App -> appKey?.let { CornerSlot.Single(CornerItem.App(it)) } ?: CornerSlot.Empty
        CornerAssignKind.Shortcut -> appKey?.let { CornerSlot.Single(CornerItem.App(if (it.startsWith(SHORTCUT_PREFIX)) it else shortcutKey(it))) } ?: CornerSlot.Empty
        CornerAssignKind.LauncherAction -> action?.let { CornerSlot.Single(CornerItem.Action(it)) } ?: CornerSlot.Empty
        CornerAssignKind.AppGroup -> CornerSlot.Palette(group ?: "Group", members.take(CornerSlot.MAX_PALETTE_ITEMS))
        CornerAssignKind.Nothing, CornerAssignKind.Other -> CornerSlot.Empty
    }

    /** The set a style draws: landscape's own set when the preference is on and the window is landscape. */
    fun select(portrait: Map<Corner, CornerSlot>, landscape: Map<Corner, CornerSlot>, perOrientation: Boolean, isLandscape: Boolean): Map<Corner, CornerSlot> =
        if (perOrientation && isLandscape) landscape else portrait

    /** Which set a write should go to, mirroring [select]. True = landscape. */
    fun writesLandscape(perOrientation: Boolean, isLandscape: Boolean) = perOrientation && isLandscape
}
