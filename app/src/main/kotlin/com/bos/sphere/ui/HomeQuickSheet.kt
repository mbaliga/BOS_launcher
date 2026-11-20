package com.bos.sphere.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bos.sphere.core.input.AppMenuEntry
import com.bos.sphere.core.input.ContextMenus
import com.bos.sphere.core.input.HomeSheetAction
import com.bos.sphere.core.input.HomeSheetContext
import com.bos.sphere.core.input.SheetToggle
import com.bos.sphere.core.data.LauncherPrefs
import com.bos.sphere.core.data.PageTransition
import com.bos.sphere.core.design.HyleColors
import com.bos.sphere.core.design.L10n
import com.bos.sphere.feature.grid.Glyphs

/**
 * What a long-press on the home screen opens. Wave 34 (homeSheet): styled like the Sound and Vibration sheet (docs/decisions/homeSheet.md): pure black,
 * large top radius, centred title, hairlines, 48 dp targets. It carries only the quick choices (Pages, Order, Glance content, Hotspots, this page's
 * tools) and two plain rows, Wallpaper and Ground Control. Glance on/off, Add page, Edit home, Files and the item picker live in Ground Control, Comms
 * and the edit-home mode. [page] is null on homes without pages.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeQuickSheet(
    prefs: LauncherPrefs,
    update: ((LauncherPrefs) -> LauncherPrefs) -> Unit,
    page: Int?,
    pageCount: Int,
    onDeletePage: () -> Unit,
    onWallpaper: () -> Unit,
    onGroundControl: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss, containerColor = SheetBlack, dragHandle = null,
        shape = RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp), scrimColor = Color.Black.copy(alpha = 0.62f),
    ) {
        HomeQuickSheetBody(prefs, update, page, pageCount, onDeletePage, onWallpaper, onGroundControl)
    }
}

private val SheetBlack = Color.Black
private val InkWhite = Color.White
private val HairlineWhite = Color(0x33FFFFFF)
private val OutlineWhite = Color(0x66FFFFFF)

/** The sheet's content (separate from the window so screenshots can draw it directly). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun HomeQuickSheetBody(
    prefs: LauncherPrefs,
    update: ((LauncherPrefs) -> LauncherPrefs) -> Unit,
    page: Int?,
    pageCount: Int,
    onDeletePage: () -> Unit,
    onWallpaper: () -> Unit,
    onGroundControl: () -> Unit,
) {
    // HOM-21: the content scrolls, so nothing falls off a short window or a large font.
    Column(Modifier.navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 28.dp).padding(bottom = 16.dp)) {
        Text(
            L10n.tr("Home"), color = InkWhite, fontSize = com.bos.sphere.core.design.PopupTokens.TitleSize, fontWeight = com.bos.sphere.core.design.PopupTokens.TitleWeight, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 28.dp, bottom = 8.dp).testTag("quick.sheet").semantics { heading() },
        )
        // Wave 28: the Pages setting, exactly three values, and the order.
        Heading(L10n.tr("Pages"))
        ChipRow { for (m in com.bos.sphere.core.data.HomePages.entries) Chip(null, L10n.tr(m.label), prefs.homePages == m, tag = "quick.pages.${m.name}") { update { it.copy(homePages = m) } } }
        Heading(L10n.tr("Order"))
        ChipRow { for (o in com.bos.sphere.core.data.HomeOrder.entries) Chip(null, L10n.tr(o.label), prefs.homeOrder == o) { update { it.copy(homeOrder = o) } } }

        if (prefs.glanceEnabled) {
            // HOM-23: the Glance quick settings: content and hotspots.
            Heading(L10n.tr("Glance content"))
            val cur = com.bos.sphere.core.data.GlanceContent.presetOf(prefs.glanceContent)
            ChipRow {
                for (pr in com.bos.sphere.core.data.GlancePreset.entries.filter { it != com.bos.sphere.core.data.GlancePreset.Custom }) {
                    Chip(null, L10n.tr(pr.label), cur == pr) { update { it.copy(glanceContent = com.bos.sphere.core.data.GlanceContent.applyPreset(it.glanceContent, pr)) } }
                }
            }
            Heading(L10n.tr("Hotspots"))
            val hc = com.bos.sphere.core.data.GlanceHotspots.presetOf(prefs.glanceHotspots)
            ChipRow {
                for (pr in com.bos.sphere.core.data.HotspotPreset.entries.filter { it != com.bos.sphere.core.data.HotspotPreset.Custom }) {
                    Chip(null, L10n.tr(pr.label), hc == pr) { update { it.copy(glanceHotspots = com.bos.sphere.core.data.GlanceHotspots.preset(pr) ?: it.glanceHotspots) } }
                }
            }
        }

        if (page != null) {
            // HOM-21: this page's rows come from ContextMenus.forHomeSheet, the model touch, right-click and the Menu key share.
            val rows = ContextMenus.forHomeSheet(
                HomeSheetContext(
                    page = page, pageCount = pageCount, alignTop = prefs.gridBias <= 0.05f, alignBottom = prefs.gridBias >= 0.95f,
                    isHomePage = prefs.homePage == page,
                    transitions = PageTransition.entries.map { it.label to (prefs.pageTransition == it) },
                ),
            )
            fun glyphOf(k: SheetToggle) = when (k) {
                SheetToggle.Time -> Glyphs.Clock; SheetToggle.Weather -> Glyphs.Sun; SheetToggle.Battery -> Glyphs.Battery
                SheetToggle.AlignTop -> Glyphs.Up; SheetToggle.AlignBottom -> Glyphs.Down; SheetToggle.MakeHome -> Glyphs.Home
            }
            fun flip(k: SheetToggle) = when (k) {
                SheetToggle.AlignTop -> update { it.copy(gridBias = 0f) }
                SheetToggle.AlignBottom -> update { it.copy(gridBias = 1f) }
                SheetToggle.MakeHome -> update { it.copy(homePage = page) }
                else -> Unit
            }
            Heading(L10n.tr("This page"))
            ChipRow { rows.filterIsInstance<AppMenuEntry.Toggle>().filter { it.kind !in GONE }.forEach { t -> Chip(glyphOf(t.kind), t.label, t.on) { flip(t.kind) } } }
            Heading(L10n.tr("Transition"))
            ChipRow {
                rows.filterIsInstance<AppMenuEntry.Choice>().forEach { c ->
                    Chip(null, c.option, c.selected) { PageTransition.entries.firstOrNull { it.label == c.option }?.let { t -> update { it.copy(pageTransition = t) } } }
                }
            }
            rows.filterIsInstance<AppMenuEntry.Sheet>().firstOrNull { it.action == HomeSheetAction.DeletePage }?.let { r ->
                Spacer(Modifier.height(10.dp))
                ChipRow { Chip(Glyphs.Close, r.label, false, danger = true, onClick = onDeletePage) }
            }
        }

        // Two plain rows with glyphs, a hairline between (like Share Wifi / Done in Connectivity).
        Spacer(Modifier.height(22.dp))
        Hairline()
        TextRow(Glyphs.Image, HomeSheetAction.Wallpaper.label, "quick.wallpaper", onWallpaper)
        Hairline()
        TextRow(Glyphs.Gear, HomeSheetAction.GroundControl.label, "quick.groundcontrol", onGroundControl)
        Spacer(Modifier.height(8.dp))
    }
}

/** The time, weather and battery toggles left the sheet (wave 34); they stay in Ground Control, Home screen. */
private val GONE = setOf(SheetToggle.Time, SheetToggle.Weather, SheetToggle.Battery)

@Composable
private fun Heading(text: String) {
    Text(text.uppercase(), color = HyleColors.InkDim, fontSize = 12.sp, letterSpacing = 1.6.sp, modifier = Modifier.padding(top = 22.dp, bottom = 10.dp).semantics { heading() })
}

@Composable
private fun Hairline() { Box(Modifier.fillMaxWidth().height(1.dp).background(HairlineWhite)) }

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipRow(content: @Composable () -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) { content() }
}

/** A pill with a thin outline; selected is filled white with black text (the Sound sheet is white on black, it has no accent colour). 48 dp tall. */
@Composable
private fun Chip(glyph: ImageVector?, label: String, selected: Boolean, danger: Boolean = false, tag: String? = null, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    val tint = if (selected) Color.Black else if (danger) Color(0xFFFF7A7A) else InkWhite
    Row(
        Modifier.then(if (tag != null) Modifier.testTag(tag) else Modifier).heightIn(min = 48.dp).clip(shape)
            .background(if (selected) InkWhite else Color.Transparent)
            .border(1.dp, if (selected) InkWhite else OutlineWhite, shape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (glyph != null) { Icon(glyph, null, tint = tint, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)) }
        Text(L10n.tr(label), color = tint, fontSize = 16.sp)
    }
}

@Composable
private fun TextRow(glyph: ImageVector, label: String, tag: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag(tag).clickable(role = Role.Button, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(glyph, null, tint = InkWhite, modifier = Modifier.size(26.dp))
        Spacer(Modifier.width(14.dp))
        Text(L10n.tr(label), color = InkWhite, fontSize = 18.sp)
    }
}
