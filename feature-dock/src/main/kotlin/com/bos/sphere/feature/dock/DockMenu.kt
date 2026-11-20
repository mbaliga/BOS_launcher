package com.bos.sphere.feature.dock

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
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
    fun dock(actions: DockActions, edge: DockEdge, onEdit: () -> Unit, onAbout: () -> Unit, onStyle: (() -> Unit)? = null): List<DockMenuEntry> = buildList {
        add(DockMenuEntry(L10n.tr("Edit dock"), onEdit))
        add(DockMenuEntry(L10n.tr("Workspaces and groups"), actions.onOpenPanel))
        add(DockMenuEntry(L10n.tr("Dock settings"), actions.onOpenPanel))
        onStyle?.let { add(DockMenuEntry(L10n.tr("Dock style"), it)) } // W39
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
    val shape = RoundedCornerShape(com.bos.sphere.feature.grid.OneUiDialogSpec.MENU_CORNER_DP.dp)
    Popup(popupPositionProvider = PressPositionProvider(press, 12), onDismissRequest = onDismiss, properties = PopupProperties(focusable = true)) {
        // One UI popup menu (wave 41): rounded 16 dp, raised, 48 dp rows, no dividers.
        Column(
            Modifier.testTag(tag).widthIn(min = 180.dp, max = 280.dp).heightIn(max = 360.dp).shadow(8.dp, shape).clip(shape).background(HyleColors.Surface)
                .verticalScroll(rememberScrollState()).padding(vertical = 8.dp),
        ) {
            com.bos.sphere.feature.grid.OneUiMenuRows(entries.map { e -> com.bos.sphere.feature.grid.OneUiMenuEntry(e.label, run = e.run) }, onDismiss, firstModifier = Modifier.focusRequester(first))
        }
    }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
}

/** "Edit dock": the pinned items, each with a menu (move earlier, move later, remove), and a way to add apps; the old panel's pin editing without a drag. */
@Composable
internal fun EditDockDialog(content: DockContent, actions: DockActions, onDismiss: () -> Unit) {
    var menuFor by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<Int?>(null) }
    androidx.compose.runtime.CompositionLocalProvider(com.bos.sphere.feature.grid.LocalOneUiDialogHook provides DockDialogHook) {
        com.bos.sphere.feature.grid.OneUiDialog(L10n.tr("Edit dock"), onDismiss, cancelLabel = null, confirmLabel = L10n.tr("Done"), onConfirm = onDismiss) {
            if (content.pinned.isEmpty()) Text(L10n.tr("Nothing is pinned to the dock yet."), color = HyleColors.InkDim, fontSize = 15.sp, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
            content.pinned.forEachIndexed { i, item ->
                val entries = buildList {
                    if (i > 0) add(com.bos.sphere.feature.grid.OneUiMenuEntry(L10n.tr("Move earlier")) { actions.onReorder(i, i - 1) })
                    if (i < content.pinned.lastIndex) add(com.bos.sphere.feature.grid.OneUiMenuEntry(L10n.tr("Move later")) { actions.onReorder(i, i + 1) })
                    add(com.bos.sphere.feature.grid.OneUiMenuEntry(L10n.tr("Unpin"), destructive = true) { actions.onUnpin(item) })
                }
                Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(start = 24.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(item.label, color = HyleColors.InkBright, fontSize = 17.sp, maxLines = 1, modifier = Modifier.weight(1f))
                    com.bos.sphere.feature.grid.OneUiMoreButton(L10n.tr("More options for %s", item.label), menuFor == i, { open -> menuFor = if (open) i else null }, entries)
                }
            }
            Text(
                L10n.tr("Add apps"), color = HyleColors.Violet, fontSize = 17.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(role = Role.Button) { actions.onAddApps() }.padding(horizontal = 24.dp, vertical = 16.dp),
            )
        }
    }
}
