package com.bos.sphere.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.bos.sphere.core.data.AppEntry
import com.bos.sphere.core.design.HyleColors
import com.bos.sphere.core.design.L10n
import com.bos.sphere.feature.grid.AppIcon
import com.bos.sphere.feature.grid.Glyphs
import com.bos.sphere.feature.grid.AppMenuModel
import com.bos.sphere.feature.grid.DialogControllerKeys
import com.bos.sphere.core.input.HomeMenuAction
import com.bos.sphere.core.input.MenuAction

/**
 * The app menu: the app's icon and name on top, then one row per entry of the shared menu model (INP-03). The rows
 * come from `AppMenuModel.rows`, which asks `ContextMenus`, so a long-press, a right-click and the Menu key can never
 * show different rows. App info + uninstall route to the system; hide is persisted locally. Arrows, Tab and Enter
 * work through normal focus, hover washes a row, Back or a controller's B closes it.
 */
@Composable
fun AppContextMenu(
    app: AppEntry,
    isHidden: Boolean,
    onDismiss: () -> Unit,
    onAppInfo: () -> Unit,
    onUninstall: () -> Unit,
    onToggleHidden: () -> Unit,
    isOnHome: Boolean? = null,
    onToggleHome: () -> Unit = {},
    onIconColor: (() -> Unit)? = null,
    /** Shown on homes that have no empty space to long-press (PSP, DSi, continuous). */
    onHomeSettings: (() -> Unit)? = null,
    /** Launch-control and dock entries from the dock stream: "Open on <display>", "Open beside", bounds, dock pinning. */
    extraEntries: List<Pair<String, () -> Unit>> = emptyList(),
    /** The "Open" row. */
    onOpen: () -> Unit = onDismiss,
) {
    Dialog(onDismissRequest = onDismiss) {
        DialogControllerKeys()
        val shape = RoundedCornerShape(24.dp)
        val ctx = AppMenuModel.context(isHidden, isOnHome, hasIconColour = onIconColor != null, hasHomeSettings = onHomeSettings != null)
        val rows = AppMenuModel.rows(ctx)
        Column(
            modifier = Modifier
                .width(300.dp)
                .clip(shape)
                .background(HyleColors.Surface.copy(alpha = 0.97f))
                .border(1.dp, HyleColors.Hairline, shape)
                .padding(bottom = 6.dp),
        ) {
            Row(Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                AppIcon(app, 48.dp)
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(app.label, color = HyleColors.InkBright, fontSize = 18.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(app.packageName, color = HyleColors.InkDim, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            // HOM-09: notifications of this app, when notification access is granted.
            com.bos.sphere.feature.grid.NotificationPreview(app)
            Divider()
            // HOM-05: user tags.
            val tagsOpen = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
            if (tagsOpen.value) com.bos.sphere.feature.grid.TagDialog(app) { tagsOpen.value = false }
            for (row in rows) {
                val label = L10n.tr(row.label)
                row.home?.let { h ->
                    when (h) {
                        HomeMenuAction.Tags -> MenuItem(Glyphs.Plus, label, { tagsOpen.value = true })
                        HomeMenuAction.AddToHome -> MenuItem(Glyphs.Plus, label, onToggleHome)
                        HomeMenuAction.IconColour -> onIconColor?.let { MenuItem(Glyphs.Palette, label, it) }
                        HomeMenuAction.Hide, HomeMenuAction.Unhide -> MenuItem(Glyphs.Image, label, onToggleHidden)
                        HomeMenuAction.HomeSettings -> onHomeSettings?.let { MenuItem(Glyphs.Gear, label, it) }
                    }
                }
                when (row.shared) {
                    MenuAction.Open -> {
                        MenuItem(Glyphs.Plus, label, onOpen)
                        // The dock stream's group sits right after Open: display, beside, bounds, dock pinning.
                        extraEntries.forEach { (l, run) -> MenuItem(Glyphs.Plus, l, run) }
                    }
                    MenuAction.Remove -> MenuItem(Glyphs.Close, label, onToggleHome)
                    MenuAction.AppInfo -> MenuItem(Glyphs.Info, label, onAppInfo)
                    MenuAction.Uninstall -> MenuItem(Glyphs.Close, label, onUninstall, destructive = true)
                    // Not offered by this menu's context (see AppMenuModel.context).
                    else -> Unit
                }
            }
        }
    }
}

@Composable
private fun Divider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(HyleColors.Hairline))
}

@Composable
private fun MenuItem(glyph: ImageVector, label: String, onClick: () -> Unit, destructive: Boolean = false) {
    val tint = if (destructive) Color(0xFFFF7A7A) else HyleColors.Ink
    val hover = remember { MutableInteractionSource() }
    val hovered by hover.collectIsHoveredAsState()
    Row(
        Modifier
            .fillMaxWidth()
            .hoverable(hover)
            .pointerHoverIcon(PointerIcon.Hand)
            .background(if (hovered) Color.White.copy(alpha = 0.10f) else Color.Transparent)
            .clickable(onClick = onClick)
            .semantics { role = Role.Button }
            .padding(horizontal = 18.dp).height(52.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(glyph, null, tint = if (destructive) tint else HyleColors.InkDim, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(16.dp))
        Text(label, color = tint, fontSize = 16.sp)
    }
}
