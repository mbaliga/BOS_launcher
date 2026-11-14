package com.bos.sphere.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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

/**
 * Long-press app menu: the app's icon and name on top, then one row per action with a glyph.
 * App info + uninstall route to the system; hide is persisted locally.
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
) {
    Dialog(onDismissRequest = onDismiss) {
        val shape = RoundedCornerShape(24.dp)
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
            MenuItem(Glyphs.Plus, L10n.tr("Tags"), { tagsOpen.value = true })
            if (isOnHome != null) MenuItem(if (isOnHome) Glyphs.Close else Glyphs.Plus, L10n.tr(if (isOnHome) "Remove from home" else "Add to home"), onToggleHome)
            if (onIconColor != null) MenuItem(Glyphs.Palette, L10n.tr("Icon colour"), onIconColor)
            MenuItem(Glyphs.Info, L10n.tr("App info"), onAppInfo)
            MenuItem(Glyphs.Image, L10n.tr(if (isHidden) "Unhide" else "Hide"), onToggleHidden)
            if (onHomeSettings != null) MenuItem(Glyphs.Gear, L10n.tr("Home settings"), onHomeSettings)
            MenuItem(Glyphs.Close, L10n.tr("Uninstall"), onUninstall, destructive = true)
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
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 18.dp).height(52.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(glyph, null, tint = if (destructive) tint else HyleColors.InkDim, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(16.dp))
        Text(label, color = tint, fontSize = 16.sp)
    }
}
