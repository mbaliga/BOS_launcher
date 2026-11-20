package com.bos.sphere.feature.grid

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.focusable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
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
)

object ItemSheetTags {
    const val SHEET = "item-sheet"
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
        SheetOption.ClockFormat -> Box(tag) { OneUiSegmentedRow(L10n.tr("Hours"), listOf(L10n.tr("System"), L10n.tr("12-hour"), L10n.tr("24-hour")), clock.format.ordinal, { a.onClock(clock.copy(format = ClockFormat.entries[it])) }) }
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
            Box(tag) { OneUiSegmentedRow(L10n.tr("Size"), presets.map { WidgetSizePresets.label(it) }, presets.indexOf(a.widgetSpan), { i -> presets[i].let { a.onWidgetSize(it.first, it.second) } }) }
        }
        SheetOption.OpenItem -> item("Open", Glyphs.Apps, run = a.onOpen)()
    }
}
