package com.bos.sphere.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.bos.sphere.core.data.AppEntry
import com.bos.sphere.core.design.HyleColors

/**
 * Long-press app menu (build brief §2.A "App management"). App info + uninstall route to the
 * system; hide is persisted locally. Rename / custom icon arrive with the icon-pack work later.
 */
@Composable
fun AppContextMenu(
    app: AppEntry,
    isHidden: Boolean,
    onDismiss: () -> Unit,
    onAppInfo: () -> Unit,
    onUninstall: () -> Unit,
    onToggleHidden: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .width(240.dp)
                .background(HyleColors.SurfaceTranslucent, RoundedCornerShape(16.dp))
                .border(1.dp, HyleColors.Hairline, RoundedCornerShape(16.dp))
                .padding(vertical = 8.dp),
        ) {
            Text(
                app.label,
                color = HyleColors.InkBright,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            )
            MenuItem("App info", onAppInfo)
            MenuItem(if (isHidden) "Unhide" else "Hide", onToggleHidden)
            MenuItem("Uninstall", onUninstall, destructive = true)
        }
    }
}

@Composable
private fun MenuItem(label: String, onClick: () -> Unit, destructive: Boolean = false) {
    Text(
        text = label,
        color = if (destructive) HyleColors.Violet else HyleColors.Ink,
        fontSize = 14.sp,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    )
}
