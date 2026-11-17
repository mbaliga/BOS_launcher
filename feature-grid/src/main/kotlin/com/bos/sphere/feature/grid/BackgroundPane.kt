package com.bos.sphere.feature.grid

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bos.sphere.core.data.BackgroundChoice
import com.bos.sphere.core.data.BackgroundRefusal
import com.bos.sphere.core.data.BgItem
import com.bos.sphere.core.data.LauncherPrefs
import com.bos.sphere.core.data.RadarIntensity
import com.bos.sphere.core.data.SignalForce
import com.bos.sphere.core.design.HyleColors
import com.bos.sphere.core.design.L10n
import com.bos.sphere.core.design.rememberMotionPrefs
import com.bos.sphere.core.signals.SignalState

/**
 * HOM-16, PAL-24, CFG-17 (wave 15): Ground Control > Background. Two slot pickers (slot 1: any of the nine items or None; slot 2:
 * any other item or None; at most one living wallpaper, with the reason shown on the options the rule disables), a live
 * thumbnail on every option drawn at the preview signal level, a "Preview signal" control that only changes what the pane shows
 * (the saved Lab override is separate), and the Intensity.
 */
@Composable
fun BackgroundPane(prefs: LauncherPrefs, update: ((LauncherPrefs) -> LauncherPrefs) -> Unit) {
    var previewForce by remember { mutableStateOf(SignalForce.Good) }
    val choice = BackgroundChoice(prefs.backgroundSlot1, prefs.backgroundSlot2)
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        BackgroundPreview(choice, previewForce, prefs.radarIntensity)
        Text(L10n.tr("Preview signal"), color = HyleColors.Ink, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp, bottom = 2.dp))
        ChipRow(SignalForce.entries.toList(), previewForce, { L10n.tr(it.label) }) { previewForce = it }
        Text(L10n.tr("Each item glows differently with the signal. With no signal it falls to a dim resting state and thin lines close in from the screen edges."), color = HyleColors.InkDim, fontSize = 11.sp)
        SlotPicker(1, choice, previewForce, prefs.radarIntensity) { item -> update { it.copy(backgroundSlot1 = BackgroundChoice(it.backgroundSlot1, it.backgroundSlot2).with(1, item).slot1) } }
        SlotPicker(2, choice, previewForce, prefs.radarIntensity) { item -> update { it.copy(backgroundSlot2 = BackgroundChoice(it.backgroundSlot1, it.backgroundSlot2).with(2, item).slot2) } }
        Text(L10n.tr("At most one living wallpaper, plus an effect. Two effects work too, and so does one item alone."), color = HyleColors.InkDim, fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp))
        Text(L10n.tr("Intensity"), color = HyleColors.Ink, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp, bottom = 2.dp))
        ChipRow(RadarIntensity.entries.toList(), prefs.radarIntensity, { L10n.tr(it.label) }) { v -> update { it.copy(radarIntensity = v) } }
    }
}

/** The slot's heading and its ten options (None, the five living wallpapers, the four effects). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SlotPicker(slot: Int, choice: BackgroundChoice, force: SignalForce, intensity: RadarIntensity, onPick: (BgItem) -> Unit) {
    val current = if (slot == 1) choice.slot1 else choice.slot2
    Text(L10n.tr(if (slot == 1) "Slot 1" else "Slot 2"), color = HyleColors.Ink, fontSize = 12.sp, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
    FlowRow(maxItemsInEachRow = 3, horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        for (item in BackgroundPickerOrder.items) {
            val refusal = choice.refusal(slot, item)
            BackgroundOptionCard(item, item == current, refusal, force, intensity, Modifier.weight(1f)) { onPick(item) }
        }
    }
}

/** The order of the options: None first, the living wallpapers, then the effects. */
internal object BackgroundPickerOrder {
    val items: List<BgItem> = listOf(BgItem.None) + BgItem.livingWallpapers + BgItem.effects
}

/** The reason a refused option gives, in English (translated where shown). */
internal fun refusalText(r: BackgroundRefusal): String = when (r) {
    BackgroundRefusal.SameItem -> "Already in the other slot"
    BackgroundRefusal.TwoWallpapers -> "Only one living wallpaper"
}

@Composable
internal fun BackgroundOptionCard(item: BgItem, selected: Boolean, refusal: BackgroundRefusal?, force: SignalForce, intensity: RadarIntensity, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    val label = L10n.tr(item.label)
    val reason = refusal?.let { L10n.tr(refusalText(it)) }
    Column(
        modifier.clip(shape).alpha(if (refusal != null) 0.45f else 1f)
            .clickable(enabled = refusal == null, onClick = onClick)
            .semantics { role = Role.RadioButton; this.selected = selected; if (refusal != null) disabled(); contentDescription = if (reason != null) "$label, $reason" else label },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.fillMaxWidth().height(84.dp).clip(shape).background(Color.Black)
                .border(if (selected) 2.dp else 1.dp, if (selected) HyleColors.Violet else Color(0x1FFFFFFF), shape),
        ) { BackgroundThumbnail(item, force, intensity) }
        Text(label, color = if (selected) HyleColors.VioletSoft else HyleColors.InkDim, fontSize = 12.sp, maxLines = 1, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp))
        if (reason != null) Text(reason, color = HyleColors.InkDim, fontSize = 9.sp, textAlign = TextAlign.Center, maxLines = 2)
        else Spacer(Modifier.height(0.dp))
    }
}

/** One item drawn still, at the level the preview control shows (Live shows a good signal), at thumbnail size. */
@Composable
internal fun BackgroundThumbnail(item: BgItem, force: SignalForce, intensity: RadarIntensity, modifier: Modifier = Modifier) {
    val cfg = remember(item, intensity) { BackgroundConfig(BackgroundChoice(item, BgItem.None), intensity, dark = true, baseFill = true) }
    val signal = remember(force) { mutableStateOf<SignalState?>(SignalState.forced(if (force == SignalForce.Live) 3 else force.level)) }
    val clock = remember { RadarClock() }
    SignalBackgroundCanvas(cfg, signal, clock, still = true, modifier = modifier)
}

/** The larger strip: the current pair, animated like the real thing (still under reduced motion), at the preview level. */
@Composable
internal fun BackgroundPreview(choice: BackgroundChoice, force: SignalForce, intensity: RadarIntensity, modifier: Modifier = Modifier) {
    val reduced = rememberMotionPrefs().reduced
    val clock = rememberRadarClock(rememberRadarActive(), reduced)
    val signal = remember(force) { mutableStateOf<SignalState?>(SignalState.forced(if (force == SignalForce.Live) 3 else force.level)) }
    val cfg = remember(choice, intensity) { BackgroundConfig(choice, intensity, dark = true, baseFill = true) }
    Box(modifier.fillMaxWidth().height(170.dp).clip(RoundedCornerShape(14.dp)).background(Color.Black)) {
        SignalBackgroundCanvas(cfg, signal, clock, reduced)
    }
}
