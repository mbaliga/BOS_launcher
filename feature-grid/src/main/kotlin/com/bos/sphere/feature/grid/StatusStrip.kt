package com.bos.sphere.feature.grid

import android.app.ActivityManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bos.sphere.core.data.ComplicationKind
import com.bos.sphere.core.data.LauncherPrefs
import com.bos.sphere.core.data.NetworkKind
import com.bos.sphere.core.data.NetworkSignal
import com.bos.sphere.core.data.StepsSource
import com.bos.sphere.core.data.StripItem
import com.bos.sphere.core.data.StripZone
import com.bos.sphere.core.data.WeatherInfo
import com.bos.sphere.core.design.HyleColors
import kotlinx.coroutines.delay

/** Height the strip needs: tall enough for the clock when it is shown, else a single line. */
fun stripHeight(prefs: LauncherPrefs): Dp =
    if (StripItem.Time in prefs.strip) clockBlockHeight(prefs) else 26.dp

/**
 * The status strip — a Nintendo-3DS-style bar of small readouts. Items are assigned to the left,
 * centre or right zone in settings; the clock is one of them (at the configured size), the rest are
 * compact glyph + value chips (battery, signal, steps, alarm, ring mode, storage, memory,
 * notifications). Zones keep clear of the corner icons via [sidePadding].
 */
@Composable
fun StatusStrip(
    prefs: LauncherPrefs,
    weather: WeatherInfo?,
    sidePadding: Dp,
    onClockTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier.fillMaxWidth().padding(horizontal = sidePadding), verticalAlignment = Alignment.CenterVertically) {
        for (zone in StripZone.entries) {
            val items = StripItem.entries.filter { prefs.strip[it] == zone }
            Box(
                Modifier.weight(1f),
                contentAlignment = when (zone) {
                    StripZone.Left -> Alignment.CenterStart
                    StripZone.Centre -> Alignment.Center
                    StripZone.Right -> Alignment.CenterEnd
                },
            ) {
                if (items.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        items.forEach { item ->
                            if (item == StripItem.Time) ClockBlock(prefs, weather, onClockTap) else StripChip(item)
                        }
                    }
                }
            }
        }
    }
}

/** A small glyph + value readout. */
@Composable
private fun StripChip(item: StripItem) {
    val v = rememberStripValue(item)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.semantics { contentDescription = "${item.label} ${v.text}" },
    ) {
        Icon(v.glyph, null, tint = HyleColors.Ink, modifier = Modifier.size(14.dp))
        Text(v.text, color = HyleColors.Ink, fontSize = 12.sp, maxLines = 1, modifier = Modifier.padding(start = 4.dp))
    }
}

internal data class StripValue(val glyph: ImageVector, val text: String)

@Composable
private fun rememberStripValue(item: StripItem): StripValue = when (item) {
    StripItem.Battery -> StripValue(Glyphs.Battery, rememberComplicationValue(ComplicationKind.Battery).text)
    StripItem.NextAlarm -> StripValue(Glyphs.Clock, rememberComplicationValue(ComplicationKind.NextAlarm).text)
    StripItem.Ringer -> StripValue(Glyphs.Ring, rememberComplicationValue(ComplicationKind.Ringer).text)
    StripItem.Storage -> StripValue(Glyphs.Storage, rememberComplicationValue(ComplicationKind.Storage).text)
    StripItem.Notifications -> StripValue(Glyphs.Bell, rememberComplicationValue(ComplicationKind.Notifications).text)
    StripItem.Signal -> StripValue(Glyphs.Signal, rememberSignalText())
    StripItem.Steps -> StripValue(Glyphs.Steps, rememberStepsText())
    StripItem.Memory -> StripValue(Glyphs.Memory, rememberMemoryText())
    StripItem.Time -> StripValue(Glyphs.Clock, "")
}

@Composable
private fun rememberSignalText(): String {
    val context = LocalContext.current
    val source = remember { NetworkSignal(context) }
    DisposableEffect(source) { source.start(); onDispose { source.stop() } }
    val st by source.status.collectAsStateWithLifecycle()
    return when (st.kind) {
        NetworkKind.Airplane -> "Airplane"
        NetworkKind.None -> "Offline"
        else -> "${(st.strength * 100).toInt()}%"
    }
}

@Composable
private fun rememberStepsText(): String {
    val context = LocalContext.current
    val source = remember { StepsSource(context) }
    val owner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    // Re-try on resume so the counter starts right after the permission dialog closes.
    DisposableEffect(source, owner) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, e -> if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME) source.start() }
        owner.lifecycle.addObserver(obs)
        source.start()
        onDispose { owner.lifecycle.removeObserver(obs); source.stop() }
    }
    val steps by source.steps.collectAsStateWithLifecycle()
    return when {
        !source.available -> "—"
        !source.hasPermission() -> "Allow"
        else -> steps?.toString() ?: "…"
    }
}

@Composable
private fun rememberMemoryText(): String {
    val context = LocalContext.current
    var text by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        while (true) {
            val info = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
            text = "${(100 - info.availMem * 100 / info.totalMem.coerceAtLeast(1)).toInt()}%"
            delay(5_000)
        }
    }
    return text
}
