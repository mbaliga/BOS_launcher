package com.bos.sphere.feature.grid

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.bos.sphere.core.design.HyleColors
import com.bos.sphere.core.design.L10n
import com.bos.sphere.core.input.ContextMenus
import com.bos.sphere.core.input.FocusKind
import com.bos.sphere.core.input.MenuAction
import com.bos.sphere.core.input.MenuContext
import com.bos.sphere.core.input.MenuEntry

/** The three ways into one menu (INP-03, HOM-21). */
enum class MenuTrigger { LongPress, SecondaryClick, MenuKey }

/** Every trigger asks the same model, so the entries cannot differ. [InputMenuTest] asserts it. */
object InputMenuModel {
    fun entries(trigger: MenuTrigger, context: MenuContext): List<MenuEntry> = when (trigger) {
        MenuTrigger.LongPress -> ContextMenus.onLongPress(context)
        MenuTrigger.SecondaryClick -> ContextMenus.onSecondaryClick(context)
        MenuTrigger.MenuKey -> ContextMenus.onMenuKey(context)
    }
}

/**
 * A pointer-and-keyboard friendly popup of the entries for [context]: arrows move between rows, Enter chooses,
 * hover and focus show, Esc (Back) closes. [onChoose] receives the action; the host performs it.
 */
@Composable
fun InputMenu(
    context: MenuContext,
    trigger: MenuTrigger,
    onChoose: (MenuAction) -> Unit,
    onDismiss: () -> Unit,
    offset: IntOffset = IntOffset.Zero,
) {
    val entries = InputMenuModel.entries(trigger, context)
    Popup(offset = offset, onDismissRequest = onDismiss, properties = PopupProperties(focusable = true)) {
        FocusLayer(layer = 8) {
            Column(
                Modifier
                    .widthIn(min = 200.dp, max = 280.dp)
                    .background(HyleColors.SurfaceTranslucent.copy(alpha = 0.98f), RoundedCornerShape(14.dp))
                    .border(1.dp, HyleColors.Hairline, RoundedCornerShape(14.dp))
                    .padding(vertical = 6.dp)
                    .linearTraversal(),
            ) {
                entries.forEachIndexed { i, e ->
                    Text(
                        L10n.tr(e.action.label),
                        color = if (e.enabled) HyleColors.InkBright else HyleColors.InkDim,
                        fontSize = 14.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .launcherItem("menu.${e.action.name}", FocusKind.Control, order = i, layer = 8, focusable = false, onActivate = { if (e.enabled) onChoose(e.action) }, ringRadiusDp = 8f)
                            .clickable(enabled = e.enabled) { onChoose(e.action) }
                            .semantics { role = Role.Button }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                    )
                }
            }
        }
    }
}
