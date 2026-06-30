package com.bos.sphere.ui

import android.os.BatteryManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bos.sphere.core.design.HyleColors
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Ambient home chrome: VAIO-gate clock pill (top-centre), weather (top-left), battery + settings
 * gear (top-right), and a now-playing line (bottom-left). Includes voice input button (M4+).
 * Signals are lightweight reads for now; live notification / media / weather sources arrive
 * with the Hub (M3).
 */
@Composable
fun Chrome(
    modifier: Modifier = Modifier,
    onOpenSettings: () -> Unit,
    onVoiceInput: (String) -> Unit = {},
) {
    Box(modifier = modifier.safeDrawingPadding().padding(12.dp)) {
        ClockPill(modifier = Modifier.align(Alignment.TopCenter))
        Weather(modifier = Modifier.align(Alignment.TopStart))

        Row(
            modifier = Modifier.align(Alignment.TopEnd),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            BatteryReadout()
            VoiceAssistantButton(onVoiceInput = onVoiceInput)
            GearButton(onClick = onOpenSettings)
        }

        NowPlaying(modifier = Modifier.align(Alignment.BottomStart))
    }
}

@Composable
private fun rememberClock(): String {
    val fmt = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val time by produceState(initialValue = fmt.format(Date())) {
        while (true) {
            value = fmt.format(Date())
            delay(10_000)
        }
    }
    return time
}

@Composable
private fun ClockPill(modifier: Modifier = Modifier) {
    val time = rememberClock()
    Box(
        modifier = modifier
            .background(HyleColors.Surface.copy(alpha = 0.85f), RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp))
            .padding(horizontal = 24.dp, vertical = 8.dp),
    ) {
        Text(
            text = time,
            color = HyleColors.InkBright,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun Weather(modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("☀", color = HyleColors.VioletSoft, fontSize = 18.sp)
        Text("24°", color = HyleColors.InkBright, fontSize = 14.sp)
        Text("Partly cloudy", color = HyleColors.InkDim, fontSize = 13.sp)
    }
}

@Composable
private fun BatteryReadout() {
    val context = LocalContext.current
    val level = remember {
        val bm = context.getSystemService(BatteryManager::class.java)
        bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
    }
    if (level in 0..100) {
        Text("$level%", color = HyleColors.Ink, fontSize = 13.sp)
    }
}

@Composable
private fun GearButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .background(HyleColors.Surface.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Text("⚙", color = HyleColors.InkDim, fontSize = 16.sp)
    }
}

@Composable
private fun NowPlaying(modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("♪", color = HyleColors.VioletSoft, fontSize = 14.sp)
        Text("Tycho — Awake", color = HyleColors.InkDim, fontSize = 12.sp)
    }
}
