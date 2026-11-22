package com.bos.sphere.feature.grid

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.height
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bos.sphere.core.data.ClockFormat
import com.bos.sphere.core.data.ClockSize
import com.bos.sphere.core.data.ClockStyle
import com.bos.sphere.core.data.LauncherPrefs
import com.bos.sphere.core.design.HyleColors
import com.bos.sphere.core.design.L10n

/** Test tags of the Glance clock edit mode (wave 50). */
object GlanceClockEditTags {
    const val LAYER = "glance.clockedit.layer"
    const val PANEL = "glance.clockedit.panel"
    const val DONE = "glance.clockedit.done"
    const val CANCEL = "glance.clockedit.cancel"
    const val RESET = "glance.clockedit.reset"
    const val STYLE = "glance.clockedit.style"
    const val SIZE = "glance.clockedit.size"
    const val FORMAT = "glance.clockedit.format"
    const val DATE = "glance.clockedit.date"
    const val COLOUR = "glance.clockedit.colour"
}

/** Where the edit panel sits so it never covers the clock: the opposite half of the area from the clock (portrait: top or bottom, landscape: start or end side). */
internal object GlanceClockEditRules {
    /** True when the panel goes at the top (portrait) or at the start side (landscape): the clock is in the lower, or the end, half of the area. */
    fun panelAtStartOrTop(landscape: Boolean, centreX: Float, topY: Float, clockHeightFraction: Float): Boolean =
        if (landscape) centreX > 0.5f else topY + clockHeightFraction / 2f > 0.5f

    /** The panel's largest height as a fraction of the area in portrait. */
    const val PORTRAIT_MAX_HEIGHT = 0.5f
    val LANDSCAPE_WIDTH: Dp = 340.dp
}

/**
 * Wave 50 (glanceClockEdit): the compact options panel shown while the Glance clock is being edited. It is NOT modal (the clock stays draggable above or beside it):
 * a rounded One UI card with the clock options, then the actions stacked under them: Done as the coloured box on top, Reset position and Cancel as plain text
 * below (never side by side). Every option applies live through [onChange]. The style list opens as a full page (it is not already outside).
 */
@Composable
internal fun GlanceClockEditPanel(
    prefs: LauncherPrefs,
    onChange: ((LauncherPrefs) -> LauncherPrefs) -> Unit,
    onReset: () -> Unit,
    onDone: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var stylePage by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(24.dp)
    Column(
        modifier.shadow(8.dp, shape).clip(shape).background(HyleColors.Surface)
            // The panel takes the touches it covers: a drag that starts on it never reaches the clock layer under it.
            .pointerInput(Unit) { detectTapGestures { } }
            .semantics { paneTitle = L10n.tr("Edit clock") }
            .testTag(GlanceClockEditTags.PANEL),
    ) {
        Text(
            L10n.tr("Edit clock"), color = HyleColors.InkBright, fontSize = 18.sp, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp, start = 16.dp, end = 16.dp).semantics { heading() },
        )
        Text(
            L10n.tr("Drag the clock to move it"), color = HyleColors.InkDim, fontSize = 13.sp, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
        )
        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
            OneUiValueRow(L10n.tr("Clock style"), L10n.tr(prefs.clockStyle.label), { stylePage = true }, modifier = Modifier.testTag(GlanceClockEditTags.STYLE))
            ClockOptionRow(
                L10n.tr("Clock size"), ClockSize.entries.map { L10n.tr(it.label) }, ClockSize.entries.indexOf(prefs.glanceClockSize),
                { i -> onChange { it.copy(glanceClockSize = ClockSize.entries[i]) } },
            )
            if (prefs.clockStyle.digital) ClockOptionRow(
                L10n.tr("Clock format"), ClockFormat.entries.map { L10n.tr(it.label) }, ClockFormat.entries.indexOf(prefs.clockFormat),
                { i -> onChange { it.copy(clockFormat = ClockFormat.entries[i]) } },
            )
            OneUiSwitchRow(L10n.tr("Show date under the clock"), prefs.glanceClockDate, { on -> onChange { it.copy(glanceClockDate = on) } }, modifier = Modifier.testTag(GlanceClockEditTags.DATE))
            ClockOptionRow(
                L10n.tr("Clock colour"), listOf(L10n.tr("Automatic"), L10n.tr("Accent")), if (prefs.glanceClockAccent) 1 else 0,
                { i -> onChange { it.copy(glanceClockAccent = i == 1) } },
            )
        }
        // Actions, stacked: the primary as the coloured box on top, the secondary ones as plain text below.
        Column(Modifier.padding(horizontal = 16.dp).padding(top = 6.dp, bottom = 8.dp)) {
            ClockEditAction(L10n.tr("Done"), GlanceClockEditTags.DONE, onDone, filled = true)
            ClockEditAction(L10n.tr("Reset position"), GlanceClockEditTags.RESET, onReset)
            ClockEditAction(L10n.tr("Cancel"), GlanceClockEditTags.CANCEL, onCancel)
        }
    }
    if (stylePage) OneUiRadioPage(
        L10n.tr("Clock style"), ClockStyle.entries, prefs.clockStyle, { L10n.tr(it.label) },
        { s -> onChange { it.copy(clockStyle = s) } }, { stylePage = false },
    )
}

/**
 * Wave 50: the fixed chrome while the Glance clock is edited. The hotspots and the music line stay drawn (dimmed by the home to [EditChromeGeometry.FIXED_DIM], like the
 * home editor does) but cannot fire: four squares at the safe corners and a strip over the music line swallow touch. The page dots are blocked by the home itself.
 */
@Composable
fun GlanceClockEditShield(modifier: Modifier = Modifier) {
    androidx.compose.foundation.layout.Box(modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).testTag("glance.clockedit.shield").clearAndSetSemantics { }) {
        val side = FixedHomeChrome.HOTSPOT_TOUCH_DP.dp
        val eat = Modifier.pointerInput(Unit) { detectTapGestures { } }
        for (a in listOf(Alignment.TopStart, Alignment.TopEnd, Alignment.BottomStart, Alignment.BottomEnd)) Box(Modifier.align(a).size(side).then(eat))
        Box(Modifier.align(Alignment.TopCenter).size(280.dp, (GlanceNotch.TITLE_TOP_DP + 40f).dp).then(eat))
    }
}

/** A compact action of the panel: the primary is the filled accent box (48 dp), a secondary one is plain accent text (40 dp). Same look as the One UI dialog buttons. */
@Composable
private fun ClockEditAction(label: String, tag: String, onClick: () -> Unit, filled: Boolean = false) {
    Box(
        Modifier.fillMaxWidth().height(if (filled) 48.dp else 40.dp).testTag(tag).clip(RoundedCornerShape(24.dp))
            .background(if (filled) HyleColors.Violet.copy(alpha = 0.75f) else androidx.compose.ui.graphics.Color.Transparent)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(label, color = if (filled) androidx.compose.ui.graphics.Color.White else HyleColors.Violet, fontSize = 16.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center) }
}

/** A compact segmented option: a small caption over the control (two short lines less than a settings row, so the panel stays clear of the clock). */
@Composable
private fun ClockOptionRow(title: String, options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp)) {
        Text(title, color = HyleColors.InkDim, fontSize = 13.sp, modifier = Modifier.padding(bottom = 4.dp))
        OneUiSegmented(options, selected, onSelect, description = title)
    }
}
