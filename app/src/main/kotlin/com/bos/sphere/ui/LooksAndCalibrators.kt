package com.bos.sphere.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.bos.sphere.core.data.Calibrators
import com.bos.sphere.core.data.DockStyle
import com.bos.sphere.core.data.GlanceControls
import com.bos.sphere.core.data.LauncherPrefs
import com.bos.sphere.core.data.LookPreset
import com.bos.sphere.core.data.LookPresets
import com.bos.sphere.core.data.ThemeUndo
import com.bos.sphere.core.design.L10n
import com.bos.sphere.feature.dock.WorkspaceRepository
import com.bos.sphere.feature.grid.Glyphs
import com.bos.sphere.feature.grid.HoldCalibrator
import com.bos.sphere.feature.grid.IntervalCalibrator
import com.bos.sphere.feature.grid.AccelSource
import com.bos.sphere.feature.grid.LooksRail
import com.bos.sphere.feature.grid.SettingItem
import com.bos.sphere.feature.grid.ShakeCalibrator
import com.bos.sphere.feature.grid.rememberAccelSource
import kotlin.math.roundToInt

/**
 * Wave 56 (looksPresets): the Looks rail at the top of the Look page (CFG-26) and the "Try it" calibrators under the timing settings (CFG-27).
 * Both are rows of the ordinary settings list (a rail row: title, then the control inline), built here so the shared settings builder gets one line each.
 */

/** The rail with the settings behind it: the picked card is the Look the stored settings add up to (a hand edit leaves none picked); a tap applies through the theme path with the one undo. */
@Composable
fun LooksRailSection(prefs: LauncherPrefs, host: ThemeHost, dock: DockStyle) {
    val undo by ThemeUndo.snapshot.collectAsState()
    var applied by remember { mutableStateOf<String?>(null) }
    LooksRail(
        presets = LookPresets.all,
        selectedId = LookPresets.current(prefs, dock)?.id,
        onSelect = { l: LookPreset -> host.applier.applyLook(l); applied = L10n.tr("Applied %s.", L10n.tr(l.name)) },
        name = { L10n.tr(it.name) },
        applied = applied,
        canUndo = undo != null,
        onUndo = { if (host.applier.undo()) applied = L10n.tr("Your previous look is back.") },
    )
}

/** CFG-26: the "Looks" row. Its value is empty (the cards are the control and say which is picked); the dock style is read from the workspace state. */
fun looksRailItem(p: LauncherPrefs): SettingItem = SettingItem.Choice<LookPreset?>(
    L10n.tr("Looks"), Glyphs.Palette, LookPresets.all, null, { "" },
    hint = L10n.tr("One tap sets mode, accent, icons, dock, font and calm style together. A wallpaper picture or live wallpaper of yours is left alone."),
    preview = {
        val ctx = LocalContext.current
        val manager = remember { WorkspaceRepository.get(ctx) }
        val workspace by manager.state.collectAsState()
        val dock = remember(workspace) { manager.current.dock.style }
        LooksRailSection(p, rememberThemeHost(), dock)
    },
) { }

private fun tryRow(title: String, hint: String, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit): SettingItem =
    SettingItem.Choice<Unit>(title, Glyphs.Dial, listOf(Unit), Unit, { "" }, hint = hint, preview = content) { }

/** CFG-27: hold in the area for as long as the pointer should rest in a corner; the held time (to the slider's step) becomes Hot corner delay. */
fun hotCornerTryItem(p: LauncherPrefs, upd: ((LauncherPrefs) -> LauncherPrefs) -> Unit): SettingItem = tryRow(
    L10n.tr("Try it: Hot corner delay"), L10n.tr("Press and hold in the box for as long as the pointer should rest in a corner before it opens."),
) {
    HoldCalibrator(
        "hotCorner", L10n.tr("Press and hold here"), "${p.hotCornerDwellMs} ms", { "$it ms" },
        onHeld = { ms -> upd { it.copy(hotCornerDwellMs = Calibrators.hotCornerDelay(ms)) } },
        onReset = { upd { it.copy(hotCornerDwellMs = LauncherPrefs().hotCornerDwellMs) } },
    )
}

/** CFG-27: tap, wait as long as the music controls should stay, tap again; the wait (whole seconds) becomes Controls hide after. */
fun controlsTryItem(p: LauncherPrefs, upd: ((LauncherPrefs) -> LauncherPrefs) -> Unit): SettingItem = tryRow(
    L10n.tr("Try it: Controls hide after"), L10n.tr("Tap the box, wait as long as the controls should stay, then tap it again."),
) {
    IntervalCalibrator(
        "controls", L10n.tr("Tap here to start"), L10n.tr("Tap again when the controls should hide"), "${p.glanceControlsSeconds} s", { "%.1f s".format(it / 1000.0) },
        onWaited = { ms -> upd { it.copy(glanceControlsSeconds = Calibrators.controlsSeconds(ms)) } },
        onReset = { upd { it.copy(glanceControlsSeconds = GlanceControls.DEFAULT_SECONDS) } },
    )
}

/** CFG-27: shake the phone the way it should react; the strongest push sets Motion sensitivity so that shake just trips the detector. [source] is the accelerometer (injected in tests). */
fun motionTryItem(p: LauncherPrefs, upd: ((LauncherPrefs) -> LauncherPrefs) -> Unit, source: (@Composable () -> AccelSource?)? = null): SettingItem = tryRow(
    L10n.tr("Try it: Motion sensitivity"), L10n.tr("Shake your phone the way you want it to react: a lighter shake sets a higher sensitivity."),
) {
    ShakeCalibrator(
        "motion", L10n.tr("Shake your phone"), L10n.tr("No motion sensor on this device"), "${(p.motionSensitivity * 100).roundToInt()}%", (source ?: { rememberAccelSource() })(), { "%.1f m/s²".format(it) },
        onShake = { peak -> upd { it.copy(motionSensitivity = Calibrators.motionSensitivity(peak)) } },
        onReset = { upd { it.copy(motionSensitivity = LauncherPrefs().motionSensitivity) } },
    )
}
