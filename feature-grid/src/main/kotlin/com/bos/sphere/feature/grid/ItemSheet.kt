package com.bos.sphere.feature.grid

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.focusable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bos.sphere.core.catalog.CellLimits
import com.bos.sphere.core.catalog.PresetClamp
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import com.bos.sphere.core.data.ClockFormat
import com.bos.sphere.core.design.L10n

/** What the host does for each row of the item sheet; a row whose action is not offered is left out by [SheetCaps]. */
class ItemSheetActions(
    val onClock: (ClockTileOptions) -> Unit = {},
    val onOpen: () -> Unit = {},
    val onAppInfo: () -> Unit = {},
    val onChangeIcon: () -> Unit = {},
    val hidden: Boolean = false,
    val onToggleHidden: () -> Unit = {},
    val onUninstall: () -> Unit = {},
    val onFolderEdit: () -> Unit = {},
    val onDissolve: () -> Unit = {},
    val onReconfigure: () -> Unit = {},
    val widgetSpan: Pair<Int, Int> = 1 to 1,
    val gridCols: Int = 4,
    val gridRows: Int = 6,
    val onWidgetSize: (cols: Int, rows: Int) -> Unit = { _, _ -> },
    /** W47: the provider's resize limits in cells (null: free within the grid). A preset outside them is clamped, buzzes and shows a note. */
    val widgetLimits: CellLimits? = null,
    /** W48: true when a widget of this size would cover another item ("widgets block", like a handle drag): the preset is refused with a buzz and a note. */
    val widgetBlocked: (cols: Int, rows: Int) -> Boolean = { _, _ -> false },
)

object ItemSheetTags {
    const val SHEET = "item-sheet"
    const val PRESET_NOTE = "item-sheet.presetNote"
    fun row(o: SheetOption) = "item-sheet.${o.name}"
}

/**
 * Wave 45 (editSheet), HOM-12: the item sheet. A partial-height One UI bottom sheet (grabber, drag down or Esc to close, a 20 percent scrim so the
 * page stays visible above it) holding the item's real options as One UI rows; 2 or 3 short choices are a segmented control, never a popup.
 */
@Composable
fun ItemSheet(
    kind: SheetKind, title: String, clock: ClockTileOptions, caps: SheetCaps, actions: ItemSheetActions, onDismiss: () -> Unit,
) {
    OneUiSheet(onDismiss, title = title, scrimColor = Color.Black.copy(alpha = 0.20f)) {
        ItemSheetRows(kind, clock, caps, actions, onDismiss)
    }
}

@Composable
fun ItemSheetRows(kind: SheetKind, clock: ClockTileOptions, caps: SheetCaps, actions: ItemSheetActions, onDismiss: () -> Unit) {
    val options = ItemSheetModel.options(kind, clock, caps)
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } } // the sheet takes the keyboard focus (Esc, Tab stay inside it)
    Column(Modifier.testTag(ItemSheetTags.SHEET).focusRequester(focus).focusable().onPreviewKeyEvent { e -> if (e.type == KeyEventType.KeyDown && e.key == Key.Escape) { onDismiss(); true } else false }) {
        options.forEachIndexed { i, o ->
            if (i > 0) OneUiRowDivider()
            Row(o, clock, actions, onDismiss)
        }
    }
}

@Composable
private fun Row(o: SheetOption, clock: ClockTileOptions, a: ItemSheetActions, close: () -> Unit) {
    val tag = Modifier.testTag(ItemSheetTags.row(o))
    val danger = Color(0xFFE5484D)
    fun item(title: String, glyph: ImageVector, summary: String? = null, chevron: Boolean = false, run: () -> Unit) =
        @Composable { OneUiListRow(L10n.tr(title), tag, summary = summary?.let { L10n.tr(it) }, tile = glyph, tileColor = Color(0xFF5B7FE8), onClick = { run(); close() }, chevron = chevron) }
    when (o) {
        SheetOption.ClockStyle -> Box(tag) { OneUiSegmentedRow(L10n.tr("Style"), listOf(L10n.tr("Digital"), L10n.tr("Analog")), if (clock.analog) 1 else 0, { a.onClock(clock.copy(analog = it == 1)) }) }
        SheetOption.ClockFormat -> Box(tag) { OneUiSegmentedRow(L10n.tr("Hours"), ClockFormat.entries.map { L10n.tr(it.label) }, clock.format.ordinal, { a.onClock(clock.copy(format = ClockFormat.entries[it])) }) }
        SheetOption.ClockDate -> OneUiSwitchRow(L10n.tr("Show date"), clock.date, { a.onClock(clock.copy(date = it)) }, summary = L10n.tr("The day and date under the time"), modifier = tag)
        SheetOption.OpenApp -> item("Open app", Glyphs.Apps, run = a.onOpen)()
        SheetOption.AppInfo -> item("App info", Glyphs.Info, run = a.onAppInfo)()
        SheetOption.ChangeIcon -> item("Change icon", Glyphs.Palette, chevron = true, run = a.onChangeIcon)()
        SheetOption.HideApp -> OneUiSwitchRow(L10n.tr("Hide from launcher"), a.hidden, { a.onToggleHidden(); close() }, summary = L10n.tr("Hidden apps stay searchable in Hidden apps"), modifier = tag)
        SheetOption.Uninstall -> OneUiListRow(L10n.tr("Uninstall"), tag, summary = L10n.tr("Asks the system to remove the app"), tile = Glyphs.Close, tileColor = danger, onClick = { a.onUninstall(); close() })
        SheetOption.FolderOpen -> item("Open folder", Glyphs.Apps, run = a.onFolderEdit)()
        SheetOption.FolderRename -> item("Rename", Glyphs.ViewList, summary = "Opens the folder with its name field", chevron = true, run = a.onFolderEdit)()
        SheetOption.FolderDissolve -> OneUiListRow(L10n.tr("Remove folder"), tag, summary = L10n.tr("The apps in it go back to the page"), tile = Glyphs.Close, tileColor = danger, onClick = { a.onDissolve(); close() })
        SheetOption.WidgetReconfigure -> item("Reconfigure", Glyphs.Sliders, summary = "Opens the widget's own settings", chevron = true, run = a.onReconfigure)()
        SheetOption.WidgetSize -> {
            val presets = WidgetSizePresets.forGrid(a.gridCols, a.gridRows)
            val haptics = rememberLauncherHaptics()
            var note by remember { mutableStateOf<String?>(null) }
            Column(tag) {
                OneUiSegmentedRow(L10n.tr("Size"), presets.map { WidgetSizePresets.label(it) }, presets.indexOf(a.widgetSpan), { i ->
                    val (c, r) = presets[i]
                    val limits = a.widgetLimits
                    val res = if (limits == null) null else PresetClamp.clamp(limits, c, r)
                    val tc = res?.cols ?: c; val tr = res?.rows ?: r
                    if ((tc to tr) != a.widgetSpan && a.widgetBlocked(tc, tr)) {
                        // W48: widgets block: a preset that would cover a neighbour is refused, the same buzz and reason as a handle drag.
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        note = "Can't resize: another item is in the way"
                    } else if (res != null && res.clamped) {
                        // W47: the provider's limits are the handles' limits too; outside them the preset clamps, buzzes and says so.
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        note = "Not available for this widget"
                        if (res.cols to res.rows != a.widgetSpan) a.onWidgetSize(res.cols, res.rows)
                    } else { note = null; a.onWidgetSize(c, r) }
                })
                note?.let { Text(L10n.tr(it), color = Color(0xFFE5484D), fontSize = 12.sp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp).testTag(ItemSheetTags.PRESET_NOTE)) }
            }
        }
        SheetOption.OpenItem -> item("Open", Glyphs.Apps, run = a.onOpen)()
    }
}
