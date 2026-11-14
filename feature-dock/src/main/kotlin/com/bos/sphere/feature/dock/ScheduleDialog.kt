package com.bos.sphere.feature.dock

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
    Dialog(onDismissRequest = onDismiss) {
        val shape = RoundedCornerShape(24.dp)
        Column(
            Modifier.fillMaxWidth().clip(shape).background(HyleColors.Surface).border(1.dp, HyleColors.Hairline, shape).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Heading(L10n.tr("Remind me to open %s", label))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                Stepper("hour", hour, { hour = ScheduleTime.wrap(hour, it, 24) })
                Text(":", color = HyleColors.InkBright, fontSize = 28.sp, modifier = Modifier.padding(horizontal = 8.dp))
                Stepper("minute", minute, { minute = ScheduleTime.wrap(minute, it * 5, 60) })
            }
            Hint(L10n.tr("At %s a notification with an Open action appears. Nothing opens on its own.", ScheduleTime.clock(hour, minute)))
            Row {
                Btn("Cancel", onDismiss)
                Spacer(Modifier.width(8.dp))
                Btn("In 1 h") { onPick(ScheduleTime.inMinutes(now, 60)) }
                Btn("Set") { onPick(ScheduleTime.nextOccurrence(now, hour, minute)) }
            }
        }
    }
}

@Composable
private fun Stepper(what: String, value: Int, step: (Int) -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Btn("+") { step(1) }
        Text("%02d".format(value), color = HyleColors.InkBright, fontSize = 28.sp, fontWeight = FontWeight.Medium)
        Btn("-") { step(-1) }
    }
}
