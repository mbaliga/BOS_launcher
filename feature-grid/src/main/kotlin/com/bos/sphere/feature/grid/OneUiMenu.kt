@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.bos.sphere.feature.grid

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bos.sphere.core.design.HyleColors
import com.bos.sphere.core.design.L10n

/** One row of a One UI popup menu. [label] is already translated. */
class OneUiMenuEntry(val label: String, val destructive: Boolean = false, val enabled: Boolean = true, val tag: String? = null, val run: () -> Unit)

object OneUiMenuTags {
    const val MENU = "oneui.menu"
    fun entry(i: Int) = "oneui.menu.$i"
    const val SHEET = "oneui.sheet"
    const val GRABBER = "oneui.sheet.grabber"
}

private val Danger = Color(0xFFE5484D)

/** The rows of a One UI menu: 48 dp tall, 16 sp, no dividers; a destructive row is red. Shared by the anchored menu and the dock's press menu. */
@Composable
fun OneUiMenuRows(entries: List<OneUiMenuEntry>, onDismiss: () -> Unit, firstModifier: Modifier = Modifier) {
    entries.forEachIndexed { i, e ->
        Text(
            e.label, color = if (!e.enabled) HyleColors.InkDim.copy(alpha = 0.5f) else if (e.destructive) Danger else HyleColors.InkBright, fontSize = 16.sp,
            modifier = Modifier.fillMaxWidth().heightIn(min = OneUiDialogSpec.MENU_ROW_DP.dp).then(if (i == 0) firstModifier else Modifier)
                .testTag(e.tag ?: OneUiMenuTags.entry(i)).clickable(enabled = e.enabled, role = Role.Button) { onDismiss(); e.run() }
                .semantics { contentDescription = e.label }
                .padding(horizontal = 20.dp, vertical = 13.dp),
        )
    }
}

/**
 * A One UI popup menu anchored to the composable it is placed beside (put it in the same Box as the anchor): rounded 16 dp, raised,
 * 48 dp rows, no dividers.
 */
@Composable
fun OneUiMenu(expanded: Boolean, onDismiss: () -> Unit, entries: List<OneUiMenuEntry>, modifier: Modifier = Modifier) {
    DropdownMenu(
        expanded = expanded, onDismissRequest = onDismiss,
        modifier = modifier.testTag(OneUiMenuTags.MENU).widthIn(min = 180.dp, max = 280.dp).background(HyleColors.Surface),
        shape = RoundedCornerShape(OneUiDialogSpec.MENU_CORNER_DP.dp),
        containerColor = HyleColors.Surface, tonalElevation = 0.dp, shadowElevation = 8.dp,
    ) { OneUiMenuRows(entries, onDismiss) }
}

/** The three-dot button of a card row (48 dp target) with its menu. */
@Composable
fun OneUiMoreButton(label: String, expanded: Boolean, onToggle: (Boolean) -> Unit, entries: List<OneUiMenuEntry>, tag: String = "oneui.more") {
    Box {
        Box(
            Modifier.size(48.dp).clip(CircleShape).testTag(tag).clickable(role = Role.Button) { onToggle(true) }.semantics { contentDescription = label },
            contentAlignment = Alignment.Center,
        ) { Icon(Glyphs.More, null, tint = HyleColors.InkDim, modifier = Modifier.size(24.dp)) }
        OneUiMenu(expanded, { onToggle(false) }, entries)
    }
}

/** A modal bottom sheet in One UI's shape: top corners 28 dp, a grabber handle, an optional centred title, then [content] (use One UI rows). */
@Composable
fun OneUiSheet(
    onDismiss: () -> Unit,
    title: String? = null,
    containerColor: Color = HyleColors.Surface,
    scrimColor: Color = androidx.compose.material3.BottomSheetDefaults.ScrimColor,
    content: @Composable () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(),
        shape = RoundedCornerShape(topStart = OneUiDialogSpec.SHEET_CORNER_DP.dp, topEnd = OneUiDialogSpec.SHEET_CORNER_DP.dp),
        containerColor = containerColor, scrimColor = scrimColor,
        dragHandle = {
            Box(Modifier.padding(top = 12.dp, bottom = 12.dp).size(width = 36.dp, height = 4.dp).clip(CircleShape).background(OneUi.Track).testTag(OneUiMenuTags.GRABBER))
        },
    ) {
        Column(Modifier.testTag(OneUiMenuTags.SHEET).padding(bottom = 16.dp)) {
            if (title != null) Text(
                title, color = HyleColors.InkBright, fontSize = 20.sp, fontWeight = FontWeight.Medium, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp).semantics { heading() },
            )
            content()
        }
    }
}
