package com.bos.sphere.core.data

/**
 * CFG-02: widgets are not carried across installs (their ids are per install), so an import lists them as
 * [PendingWidget]s. Each one is re-added with one consent step: the system's own bind screen (or none, where the launcher is
 * already allowed to bind). This is the pure half: which slot a pending widget belongs to and how to put the new widget there.
 */
object WidgetRebind {
    sealed interface Target {
        data class CornerTarget(val corner: Corner, val landscape: Boolean) : Target
        data object LeftPageTarget : Target
    }

    /** The slot names [StateCodec.export] writes: `corner.<Name>`, `lcorner.<Name>` and `leftPage`. Null for anything else. */
    fun parseSlot(slot: String): Target? = when {
        slot == "leftPage" -> Target.LeftPageTarget
        slot.startsWith("corner.") -> Corner.entries.firstOrNull { it.name == slot.removePrefix("corner.") }?.let { Target.CornerTarget(it, false) }
        slot.startsWith("lcorner.") -> Corner.entries.firstOrNull { it.name == slot.removePrefix("lcorner.") }?.let { Target.CornerTarget(it, true) }
        else -> null
    }

    /** A human line for the list: where the widget will go. */
    fun describe(w: PendingWidget): String = when (val t = parseSlot(w.slot)) {
        is Target.CornerTarget -> "${t.corner.name}${if (t.landscape) " (landscape)" else ""}"
        Target.LeftPageTarget -> "Left page"
        null -> w.slot
    }

    /** Puts the freshly bound widget [appWidgetId] into the slot [w] was exported from. Returns false for a slot this build does not know. */
    fun place(w: PendingWidget, appWidgetId: Int, label: String, shortcuts: ShortcutStore, prefs: LauncherPrefsStore): Boolean {
        val name = label.ifBlank { w.label }
        when (val t = parseSlot(w.slot) ?: return false) {
            is Target.CornerTarget ->
                if (t.landscape) shortcuts.setLandscapeCorner(t.corner, CornerSlot.Widget(appWidgetId, name))
                else shortcuts.setCorner(t.corner, CornerSlot.Widget(appWidgetId, name))
            Target.LeftPageTarget -> prefs.update { it.copy(leftWidgetId = appWidgetId, leftPage = LeftPage.Widget) }
        }
        return true
    }
}
