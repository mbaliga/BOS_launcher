package com.bos.sphere.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bos.sphere.core.data.ClockPosition
import com.bos.sphere.core.input.AppMenuEntry
import com.bos.sphere.core.input.ContextMenus
import com.bos.sphere.core.input.HomeSheetAction
import com.bos.sphere.core.input.HomeSheetContext
import com.bos.sphere.core.input.SheetToggle
import com.bos.sphere.core.data.LauncherPrefs
import com.bos.sphere.core.data.PageTransition
import com.bos.sphere.core.data.StripItem
import com.bos.sphere.core.data.StripZone
import com.bos.sphere.core.design.HyleColors
import com.bos.sphere.core.design.L10n
import com.bos.sphere.feature.grid.Glyphs

/**
 * What a long-press on the home screen opens: the things you reach for most, up front — time,
 * weather, battery, media controls — then this page's tools, then a way into Ground Control (all
 * settings). [page] is null on homes without pages.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeQuickSheet(
    prefs: LauncherPrefs,
    update: ((LauncherPrefs) -> LauncherPrefs) -> Unit,
    page: Int?,
    pageCount: Int,
    onAddPage: () -> Unit,
    onDeletePage: () -> Unit,
    onEdit: (() -> Unit)? = null,
    onPanels: (() -> Unit)? = null,
    onWallpaper: () -> Unit,
    onGroundControl: () -> Unit,
    onDismiss: () -> Unit,
    /** Widgets, folders, links, icon pack and hidden apps (HOM-02 to HOM-07). */
    onHomeItems: (() -> Unit)? = null,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = HyleColors.Surface) {
        // HOM-21: the content scrolls, so nothing falls off a short window or a large font.
        Column(Modifier.navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp).padding(bottom = 16.dp)) {
            Text(L10n.tr("Home"), color = HyleColors.InkBright, fontSize = 20.sp, fontWeight = FontWeight.Light, modifier = Modifier.padding(bottom = 12.dp))

            // HOM-21: the rows come from ContextMenus.forHomeSheet, the model touch, right-click and the Menu key share.
            val timeOn = prefs.clockPosition != ClockPosition.Hidden
            val batteryOn = StripItem.Battery in prefs.strip
            val rows = ContextMenus.forHomeSheet(
                HomeSheetContext(
                    timeOn = timeOn, weatherOn = prefs.clockShowWeather, batteryOn = batteryOn, mediaOn = prefs.mediaControls,
                    page = page, pageCount = pageCount, alignTop = prefs.gridBias <= 0.05f, alignBottom = prefs.gridBias >= 0.95f,
                    isHomePage = page != null && prefs.homePage == page,
                    transitions = PageTransition.entries.map { it.label to (prefs.pageTransition == it) },
                    canEdit = onEdit != null, hasPanels = onPanels != null, hasHomeItems = onHomeItems != null,
                ),
            )
            fun glyphOf(k: SheetToggle) = when (k) {
                SheetToggle.Time -> Glyphs.Clock; SheetToggle.Weather -> Glyphs.Sun; SheetToggle.Battery -> Glyphs.Battery; SheetToggle.Media -> Glyphs.Music
                SheetToggle.AlignTop -> Glyphs.Up; SheetToggle.AlignBottom -> Glyphs.Down; SheetToggle.MakeHome -> Glyphs.Home
            }
            fun flip(k: SheetToggle) = when (k) {
                SheetToggle.Time -> update { it.copy(clockPosition = if (timeOn) ClockPosition.Hidden else ClockPosition.Top) }
                SheetToggle.Weather -> update { it.copy(clockShowWeather = !it.clockShowWeather) }
                SheetToggle.Battery -> update { it.copy(strip = if (batteryOn) it.strip - StripItem.Battery else it.strip + (StripItem.Battery to StripZone.Right)) }
                SheetToggle.Media -> update { it.copy(mediaControls = !it.mediaControls) }
                SheetToggle.AlignTop -> update { it.copy(gridBias = 0f) }
                SheetToggle.AlignBottom -> update { it.copy(gridBias = 1f) }
                SheetToggle.MakeHome -> update { it.copy(homePage = page ?: it.homePage) }
            }
            val switches = rows.filterIsInstance<AppMenuEntry.Toggle>().filter { it.kind in SWITCHES }
            val tiles: List<@Composable (Modifier) -> Unit> = switches.map { t -> { m -> Tile(m, glyphOf(t.kind), t.label, t.on) { flip(t.kind) } } }
            val cfg = androidx.compose.ui.platform.LocalConfiguration.current
            val columns = com.bos.sphere.feature.grid.QuickSheetLayout.tileColumns(cfg.screenWidthDp.toFloat(), cfg.fontScale, tiles.size)
            com.bos.sphere.feature.grid.QuickSheetLayout.rows(tiles, columns).forEachIndexed { r, rowTiles ->
                if (r > 0) Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    rowTiles.forEach { t -> t(Modifier.weight(1f)) }
                }
            }

            if (page != null) {
                Heading(L10n.tr("This page"))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    rows.filterIsInstance<AppMenuEntry.Toggle>().filter { it.kind !in SWITCHES }.forEach { t -> Chip(glyphOf(t.kind), t.label, t.on) { flip(t.kind) } }
                }
                Heading(L10n.tr("Transition"))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    rows.filterIsInstance<AppMenuEntry.Choice>().forEach { c ->
                        Chip(null, c.option, c.selected) { PageTransition.entries.firstOrNull { it.label == c.option }?.let { t -> update { it.copy(pageTransition = t) } } }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    rows.filterIsInstance<AppMenuEntry.Sheet>().filter { it.action == HomeSheetAction.AddPage || it.action == HomeSheetAction.DeletePage }.forEach { r ->
                        if (r.action == HomeSheetAction.AddPage) Chip(Glyphs.Plus, r.label, false, onClick = onAddPage)
                        else Chip(Glyphs.Close, r.label, false, danger = true, onClick = onDeletePage)
                    }
                }
            }

            Spacer(Modifier.height(18.dp))
            val actions = rows.filterIsInstance<AppMenuEntry.Sheet>().map { it.action }
            if (HomeSheetAction.EditHome in actions) { BigButton(Modifier.fillMaxWidth(), Glyphs.Apps, HomeSheetAction.EditHome.label) { onEdit?.invoke() }; Spacer(Modifier.height(10.dp)) }
            if (HomeSheetAction.Panels in actions) { BigButton(Modifier.fillMaxWidth(), Glyphs.Clock, HomeSheetAction.Panels.label) { onPanels?.invoke() }; Spacer(Modifier.height(10.dp)) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BigButton(Modifier.weight(1f), Glyphs.Image, HomeSheetAction.Wallpaper.label, onWallpaper)
                BigButton(Modifier.weight(1f), Glyphs.Gear, HomeSheetAction.GroundControl.label, onGroundControl)
            }
            if (HomeSheetAction.HomeItems in actions) {
                Spacer(Modifier.height(10.dp))
                BigButton(Modifier.fillMaxWidth(), Glyphs.Plus, HomeSheetAction.HomeItems.label) { onHomeItems?.invoke() }
            }
        }
    }
}

private val SWITCHES = setOf(SheetToggle.Time, SheetToggle.Weather, SheetToggle.Battery, SheetToggle.Media)

@Composable
private fun Heading(text: String) {
    Text(text.uppercase(), color = HyleColors.InkDim, fontSize = 11.sp, letterSpacing = 1.4.sp, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
}

@Composable
private fun Tile(modifier: Modifier, glyph: ImageVector, label: String, on: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier
            .clip(shape)
            .background(if (on) HyleColors.Violet.copy(alpha = 0.22f) else Color(0x12FFFFFF))
            .border(1.dp, if (on) HyleColors.Violet else HyleColors.Hairline, shape)
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(glyph, null, tint = if (on) HyleColors.InkBright else HyleColors.InkDim, modifier = Modifier.size(24.dp))
        Text(L10n.tr(label), color = if (on) HyleColors.InkBright else HyleColors.Ink, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp), maxLines = 2)
        Text(L10n.tr(if (on) "On" else "Off"), color = HyleColors.InkDim, fontSize = 10.sp)
    }
}

@Composable
private fun Chip(glyph: ImageVector?, label: String, selected: Boolean, danger: Boolean = false, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    val tint = if (danger) Color(0xFFFF7A7A) else if (selected) HyleColors.InkBright else HyleColors.Ink
    Row(
        Modifier.clip(shape)
            .background(if (selected) HyleColors.Violet.copy(alpha = 0.25f) else Color(0x12FFFFFF))
            .border(1.dp, if (selected) HyleColors.Violet else HyleColors.Hairline, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (glyph != null) { Icon(glyph, null, tint = tint, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(7.dp)) }
        Text(L10n.tr(label), color = tint, fontSize = 13.sp, maxLines = 1)
    }
}

@Composable
private fun BigButton(modifier: Modifier, glyph: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        modifier.clip(RoundedCornerShape(16.dp)).background(Color(0x1AFFFFFF)).clickable(onClick = onClick).padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
    ) {
        Icon(glyph, null, tint = HyleColors.InkBright, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(L10n.tr(label), color = HyleColors.InkBright, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}
