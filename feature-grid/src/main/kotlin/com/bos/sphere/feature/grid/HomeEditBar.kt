package com.bos.sphere.feature.grid

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bos.sphere.core.design.HyleColors
import com.bos.sphere.core.design.L10n

/**
 * HOM-12: the explicit edit-mode chrome. A banner at the top says what edit mode is for; the bar
 * at the bottom carries Undo, Redo (with the depth of the 20-change history), Add page and Done.
 * An outline around the screen shows that edit mode is on. Nothing here changes the arrangement
 * itself: the drag, remove and page actions of the home already do that and the session records
 * them.
 */
@Composable
fun HomeEditBar(
    undoDepth: Int,
    canUndo: Boolean,
    canRedo: Boolean,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onAddPage: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize().border(BorderStroke(2.dp, HyleColors.Violet.copy(alpha = 0.7f)))) {
        Text(
            L10n.tr("Editing home: drag items to rearrange, long-press for more"),
            color = HyleColors.InkBright, fontSize = 12.sp,
            modifier = Modifier.align(Alignment.TopCenter).windowInsetsPadding(WindowInsets.safeDrawing).padding(top = 6.dp)
                .background(Color(0xCC101426), RoundedCornerShape(50)).padding(horizontal = 14.dp, vertical = 6.dp)
                .semantics { contentDescription = "Edit mode" },
        )
        Row(
            Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.safeDrawing).padding(bottom = 10.dp, start = 12.dp, end = 12.dp)
                .background(Color(0xE6101426), RoundedCornerShape(24.dp)).padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically,
        ) {
            EditButton(if (undoDepth > 0) L10n.tr("Undo") + " ($undoDepth)" else L10n.tr("Undo"), canUndo, onUndo)
            EditButton(L10n.tr("Redo"), canRedo, onRedo)
            EditButton(L10n.tr("Add page"), true, onAddPage)
            EditButton(L10n.tr("Done"), true, onDone, primary = true)
        }
    }
}

@Composable
private fun EditButton(label: String, enabled: Boolean, onClick: () -> Unit, primary: Boolean = false) {
    val shape = RoundedCornerShape(50)
    Text(
        label,
        color = if (!enabled) HyleColors.InkDim.copy(alpha = 0.5f) else HyleColors.InkBright,
        fontSize = 13.sp, fontWeight = if (primary) FontWeight.SemiBold else FontWeight.Normal,
        modifier = Modifier
            .background(if (primary) HyleColors.Violet.copy(alpha = 0.45f) else Color(0x14FFFFFF), shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .semantics { role = Role.Button; if (!enabled) disabled() },
    )
}
