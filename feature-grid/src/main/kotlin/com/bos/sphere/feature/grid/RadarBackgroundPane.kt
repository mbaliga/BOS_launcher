package com.bos.sphere.feature.grid

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bos.sphere.core.data.LauncherPrefs
import com.bos.sphere.core.data.RadarIntensity
import com.bos.sphere.core.data.SignalForce
import com.bos.sphere.core.design.HyleColors
import com.bos.sphere.core.design.L10n
import com.bos.sphere.core.design.rememberMotionPrefs

/**
 * PAL-24, CFG-17: Ground Control's "Radar background" pane: sweep, signal view, comic lines for no signal, intensity,
 * and a live preview strip that can show every signal state without touching the saved Lab override.
 */
@Composable
fun RadarBackgroundPane(prefs: LauncherPrefs, update: ((LauncherPrefs) -> LauncherPrefs) -> Unit) {
    var previewForce by remember { mutableStateOf(SignalForce.Good) }
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        RadarBackgroundPreview(prefs, previewForce)
        Text(L10n.tr("Preview as"), color = HyleColors.Ink, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp, bottom = 2.dp))
        ChipRow(SignalForce.entries.toList(), previewForce, { L10n.tr(it.label) }) { previewForce = it }
        ToggleRow(L10n.tr("Sweep"), prefs.radarSweep, { v -> update { it.copy(radarSweep = v) } }, L10n.tr("A soft wedge turns around the scope and a ring pings every few seconds"))
        Text(L10n.tr("Signal view"), color = HyleColors.Ink, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp, bottom = 2.dp))
        ChipRow(SignalViews.options, SignalViews.options.firstOrNull { it.id == prefs.radarSignalView } ?: SignalViews.options[1], { L10n.tr(it.label) }) { o -> update { it.copy(radarSignalView = o.id) } }
        Text(L10n.tr("Glow lines: pulses of light run along the lines, more, faster and brighter as the signal gets stronger"), color = HyleColors.InkDim, fontSize = 11.sp)
        ToggleRow(L10n.tr("Comic lines for no signal"), prefs.radarComicNoSignal, { v -> update { it.copy(radarComicNoSignal = v) } }, L10n.tr("Thin lines close in from the screen edges while the phone has no connection"))
        Text(L10n.tr("Intensity"), color = HyleColors.Ink, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp, bottom = 2.dp))
        ChipRow(RadarIntensity.entries.toList(), prefs.radarIntensity, { L10n.tr(it.label) }) { v -> update { it.copy(radarIntensity = v) } }
    }
}

/** A short strip of the background as Radar draws it, with the chosen preview signal. */
@Composable
internal fun RadarBackgroundPreview(prefs: LauncherPrefs, force: SignalForce, modifier: Modifier = Modifier) {
    val reduced = rememberMotionPrefs().reduced
    val clock = rememberRadarClock(rememberRadarActive(), reduced)
    val signal = rememberRadarSignal(force, true)
    val cfg = RadarBackdropConfig.from(prefs).copy(baseFill = true)
    Box(modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(14.dp)).background(Color.Black)) {
        RadarBackground(cfg, signal, clock, reduced)
    }
}
