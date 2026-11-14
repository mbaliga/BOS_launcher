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
) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = HyleColors.Surface) {
        Column(Modifier.navigationBarsPadding().padding(horizontal = 18.dp).padding(bottom = 16.dp)) {
            Text(L10n.tr("Home"), color = HyleColors.InkBright, fontSize = 20.sp, fontWeight = FontWeight.Light, modifier = Modifier.padding(bottom = 12.dp))

            // Up front: the four things people toggle most.
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                val timeOn = prefs.clockPosition != ClockPosition.Hidden
                Tile(Modifier.weight(1f), Glyphs.Clock, "Time", timeOn) {
                    update { it.copy(clockPosition = if (timeOn) ClockPosition.Hidden else ClockPosition.Top) }
                }
                Tile(Modifier.weight(1f), Glyphs.Sun, "Weather", prefs.clockShowWeather) { update { it.copy(clockShowWeather = !it.clockShowWeather) } }
                val batteryOn = StripItem.Battery in prefs.strip
                Tile(Modifier.weight(1f), Glyphs.Battery, "Battery %", batteryOn) {
                    update { it.copy(strip = if (batteryOn) it.strip - StripItem.Battery else it.strip + (StripItem.Battery to StripZone.Right)) }
                }
                Tile(Modifier.weight(1f), Glyphs.Music, "Media", prefs.mediaControls) { update { it.copy(mediaControls = !it.mediaControls) } }
            }

            if (page != null) {
                Heading(L10n.tr("This page"))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    Chip(Glyphs.Up, "Align with top", prefs.gridBias <= 0.05f) { update { it.copy(gridBias = 0f) } }
                    Chip(Glyphs.Down, "Align with bottom", prefs.gridBias >= 0.95f) { update { it.copy(gridBias = 1f) } }
                    Chip(Glyphs.Home, "Make this home", prefs.homePage == page) { update { it.copy(homePage = page) } }
                }
                Heading(L10n.tr("Transition"))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    PageTransition.entries.forEach { t -> Chip(null, t.label, prefs.pageTransition == t) { update { it.copy(pageTransition = t) } } }
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip(Glyphs.Plus, "Add page", false, onClick = onAddPage)
                    if (pageCount > 1) Chip(Glyphs.Close, "Delete page", false, danger = true, onClick = onDeletePage)
                }
            }

            Spacer(Modifier.height(18.dp))
            if (onEdit != null) {
                BigButton(Modifier.fillMaxWidth(), Glyphs.Apps, "Edit home", onEdit)
                Spacer(Modifier.height(10.dp))
            }
            if (onPanels != null) {
                BigButton(Modifier.fillMaxWidth(), Glyphs.Clock, "Files, usage and timeline", onPanels)
                Spacer(Modifier.height(10.dp))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BigButton(Modifier.weight(1f), Glyphs.Image, "Wallpaper", onWallpaper)
                BigButton(Modifier.weight(1f), Glyphs.Gear, "Ground Control", onGroundControl)
            }
        }
    }
}

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
        Text(L10n.tr(label), color = if (on) HyleColors.InkBright else HyleColors.Ink, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp), maxLines = 1)
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
