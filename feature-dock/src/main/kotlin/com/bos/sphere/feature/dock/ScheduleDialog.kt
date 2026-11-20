package com.bos.sphere.feature.dock

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import com.bos.sphere.core.design.HyleColors
import com.bos.sphere.core.design.L10n

/**
 * Picks a clock time for a scheduled launch (WSP-11). The result is the next time the clock reads that, which is when the
 * Open notification is posted; nothing opens on its own.
 */
@Composable
fun ScheduleDialog(label: String, now: Long, onDismiss: () -> Unit, onPick: (Long) -> Unit) {
    val start = remember { java.util.Calendar.getInstance().apply { timeInMillis = now + 3_600_000L } }
    var hour by remember { mutableIntStateOf(start.get(java.util.Calendar.HOUR_OF_DAY)) }
    var minute by remember { mutableIntStateOf((start.get(java.util.Calendar.MINUTE) / 5) * 5) }
    androidx.compose.runtime.CompositionLocalProvider(com.bos.sphere.feature.grid.LocalOneUiDialogHook provides DockDialogHook) {
        com.bos.sphere.feature.grid.OneUiDialog(
            L10n.tr("Remind me to open %s", label), onDismiss, confirmLabel = L10n.tr("Set"),
            onConfirm = { onPick(ScheduleTime.nextOccurrence(now, hour, minute)) },
            body = L10n.tr("At %s a notification with an Open action appears. Nothing opens on its own.", ScheduleTime.clock(hour, minute)),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                Stepper(hour, { hour = ScheduleTime.wrap(hour, it, 24) }, L10n.tr("hour"))
                Text(":", color = HyleColors.InkBright, fontSize = 28.sp, modifier = Modifier.padding(horizontal = 8.dp))
                Stepper(minute, { minute = ScheduleTime.wrap(minute, it * 5, 60) }, L10n.tr("minute"))
            }
            Text(
                L10n.tr("In 1 h"), color = HyleColors.Violet, fontSize = 16.sp, fontWeight = FontWeight.Medium, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(role = androidx.compose.ui.semantics.Role.Button) { onPick(ScheduleTime.inMinutes(now, 60)) }.padding(16.dp),
            )
        }
    }
}

@Composable
private fun Stepper(value: Int, step: (Int) -> Unit, what: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        StepButton("+", L10n.tr("Next %s", what)) { step(1) }
        Text("%02d".format(value), color = HyleColors.InkBright, fontSize = 28.sp, fontWeight = FontWeight.Medium)
        StepButton("−", L10n.tr("Previous %s", what)) { step(-1) }
    }
}

@Composable
private fun StepButton(glyph: String, label: String, onClick: () -> Unit) {
    Box(
        Modifier.size(48.dp).clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onClick).semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { Text(glyph, color = HyleColors.Violet, fontSize = 24.sp, fontWeight = FontWeight.Medium) }
}
