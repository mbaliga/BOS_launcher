package com.bos.sphere.feature.grid

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.bos.sphere.core.design.HyleColors
import com.bos.sphere.core.design.L10n
import com.bos.sphere.core.input.MenuAction

/** One entry of Radar's small chooser: the label as shown (already translated) and what picking it does. */
class RadarChoice(val label: String, val run: () -> Unit)

internal class RadarPickerState(val title: String, val choices: List<RadarChoice>)

internal object RadarPicker {
    /** PAL-08: the question the chooser asks for the action that needed a pick. */
    fun titleFor(a: MenuAction?): String = when (a) {
        MenuAction.OpenOnDisplay -> L10n.tr("Open on which display?")
        MenuAction.AddToWorkspace -> L10n.tr("Add to which profile?")
        else -> L10n.tr("Choose")
    }
}

/**
 * PAL-08: the small chooser over Radar for an action that needs a pick (which display, which workspace), so those two
 * run from Radar's list without a detour through the home's app sheet. It is a dialog window like the other launcher
 * dialogs, so a controller's B button and Esc close it (INP-20), and Enter or a tap on a row picks it.
 */
@Composable
internal fun RadarChoicePicker(title: String, choices: List<RadarChoice>, onDismiss: () -> Unit, onPick: (RadarChoice) -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        DialogControllerKeys()
        val shape = RoundedCornerShape(20.dp)
        Column(
            Modifier.testTag("radar.picker").fillMaxWidth().clip(shape).background(Color(0xF2101018)).border(1.dp, Color(0x2EFFFFFF), shape).padding(vertical = 14.dp),
        ) {
            Text(
                title, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp).semantics { heading() },
            )
            choices.forEachIndexed { i, c ->
                Text(
                    c.label, color = Color.White, fontSize = 16.sp, maxLines = 2,
                    modifier = Modifier.testTag("radar.picker.$i").fillMaxWidth().clickable { onPick(c) }.semantics { role = Role.Button }.padding(horizontal = 20.dp, vertical = 12.dp),
                )
            }
            Text(
                L10n.tr("Cancel"), color = HyleColors.InkDim, fontSize = 15.sp,
                modifier = Modifier.testTag("radar.picker.cancel").fillMaxWidth().clickable(onClick = onDismiss).semantics { role = Role.Button }.padding(horizontal = 20.dp, vertical = 12.dp),
            )
        }
    }
}
