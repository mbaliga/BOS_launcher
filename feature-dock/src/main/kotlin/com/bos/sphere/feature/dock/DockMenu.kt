package com.bos.sphere.feature.dock

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.bos.sphere.core.design.HyleColors
import com.bos.sphere.core.design.L10n

/** One row of the dock's long-press menu; [label] is already translated. */
class DockMenuEntry(val label: String, val run: () -> Unit)

/**
 * W25 (owner item 4): the dock draws icons only, so everything the old "i" and "..." buttons held lives behind a long press. The
 * entry lists are pure so a test can check them; the rail draws them as a compact popup and also offers each as a TalkBack action.
 */
object DockMenuModel {
    const val TAG_DOCK_MENU = "dock.menu"
    const val TAG_ITEM_MENU = "dock.itemMenu"

    /** Long press on the dock background (or the menu key on the home chip). */
    fun dock(actions: DockActions, edge: DockEdge, onEdit: () -> Unit, onAbout: () -> Unit): List<DockMenuEntry> = buildList {
        add(DockMenuEntry(L10n.tr("Edit dock"), onEdit))
        add(DockMenuEntry(L10n.tr("Workspaces and groups"), actions.onOpenPanel))
        add(DockMenuEntry(L10n.tr("Dock settings"), actions.onOpenPanel))
        actions.onSetEdge?.let { set ->
            if (edge != DockEdge.Bottom) add(DockMenuEntry(L10n.tr("Dock at the bottom")) { set(DockEdge.Bottom) })
            if (edge != DockEdge.Left) add(DockMenuEntry(L10n.tr("Dock on the left")) { set(DockEdge.Left) })
            if (edge != DockEdge.Right) add(DockMenuEntry(L10n.tr("Dock on the right")) { set(DockEdge.Right) })
        }
        actions.onHide?.let { hide -> add(DockMenuEntry(L10n.tr("Hide dock"), hide)) }
        add(DockMenuEntry(L10n.tr("About the dock"), onAbout))
    }

    /** Long press on an icon: that icon's options. [extra] are the host's app entries (shortcuts and the open choices). */
    fun item(item: DockItem, index: Int, count: Int, reorderable: Boolean, actions: DockActions, extra: List<DockMenuEntry>): List<DockMenuEntry> = buildList {
        addAll(extra)
        if (item.ref.kind == ItemKind.App) add(DockMenuEntry(L10n.tr("App info")) { actions.onAppInfo(item) })
        if (reorderable) {
            if (index > 0) add(DockMenuEntry(L10n.tr("Move earlier")) { actions.onReorder(index, index - 1) })
            if (index < count - 1) add(DockMenuEntry(L10n.tr("Move later")) { actions.onReorder(index, index + 1) })
            add(DockMenuEntry(L10n.tr("Replace with another app")) { actions.onReplace(item) })
            add(DockMenuEntry(L10n.tr("Remove from dock")) { actions.onUnpin(item) })
        } else add(DockMenuEntry(L10n.tr("Pin to dock")) { actions.onPin(item) })
    }
}

/** Puts the popup next to the press: below it when there is room, otherwise above, and always inside the window. */
internal class PressPositionProvider(private val press: IntOffset, private val gap: Int) : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
        val x = press.x.coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0))
        val below = press.y + gap
        val y = if (below + popupContentSize.height <= windowSize.height) below else (press.y - gap - popupContentSize.height).coerceAtLeast(0)
        return IntOffset(x, y)
    }
}

@Composable
internal fun DockMenuPopup(press: IntOffset, tag: String, entries: List<DockMenuEntry>, onDismiss: () -> Unit) {
    val first = androidx.compose.runtime.remember { FocusRequester() }
    val shape = RoundedCornerShape(14.dp)
    Popup(popupPositionProvider = PressPositionProvider(press, 12), onDismissRequest = onDismiss, properties = PopupProperties(focusable = true)) {
        Column(
            Modifier.testTag(tag).widthIn(min = 180.dp, max = 260.dp).heightIn(max = 360.dp).clip(shape).background(HyleColors.Surface).border(1.dp, HyleColors.Hairline, shape)
                .verticalScroll(rememberScrollState()).padding(vertical = 6.dp),
        ) {
            entries.forEachIndexed { i, e ->
                Text(
                    e.label, color = HyleColors.InkBright, fontSize = 14.sp,
                    modifier = Modifier.fillMaxWidth().then(if (i == 0) Modifier.focusRequester(first) else Modifier)
                        .clickable { onDismiss(); e.run() }.padding(horizontal = 16.dp, vertical = 12.dp).semantics { role = Role.Button },
                )
            }
        }
    }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
}

/** "Edit dock": the pinned items with move and remove, and a way to add apps; the old panel's pin editing without a drag. */
@Composable
internal fun EditDockDialog(content: DockContent, actions: DockActions, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        DockDialogInput()
        val shape = RoundedCornerShape(20.dp)
        Column(
            Modifier.fillMaxWidth().heightIn(max = 560.dp).clip(shape).background(HyleColors.Surface).border(1.dp, HyleColors.Hairline, shape).verticalScroll(rememberScrollState()).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Heading("Edit dock")
            if (content.pinned.isEmpty()) Hint(L10n.tr("Nothing is pinned to the dock yet."))
            content.pinned.forEachIndexed { i, item ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(item.label, color = HyleColors.Ink, fontSize = 14.sp, maxLines = 1, modifier = Modifier.weight(1f))
                    if (i > 0) Btn("Move earlier") { actions.onReorder(i, i - 1) }
                    Spacer(Modifier.width(8.dp))
                    if (i < content.pinned.lastIndex) Btn("Move later") { actions.onReorder(i, i + 1) }
                    Spacer(Modifier.width(8.dp))
                    Btn("Unpin") { actions.onUnpin(item) }
                }
            }
            Btn("Add apps") { actions.onAddApps() }
            Box(Modifier.align(Alignment.End)) { Btn("Done", onDismiss) }
        }
    }
}
