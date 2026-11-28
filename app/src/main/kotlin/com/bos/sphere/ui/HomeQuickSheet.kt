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
import com.bos.sphere.feature.grid.SettingsArtHome

/**
 * What a long-press on the home screen opens. Wave 34 (homeSheet): styled like the Sound and Vibration sheet (docs/decisions/homeSheet.md): pure black,
 * large top radius, centred title, hairlines, 48 dp targets. Wave 51 (homeMenuVisual): every choice is a drawn card (HomeQuickSheetCards.kt) and three entry tiles (Add apps, Add widgets, Edit home) lead the sheet. It carries only the quick choices (Pages, Order, the Hotspots tile (wave 59), this page's
 * tools) and two plain rows, Wallpaper and Ground Control. Glance on/off, Add page, Files and the item picker live in Ground Control, Comms
 * and the edit-home mode; the Add apps, Add widgets and Edit home tiles call the same entry points as the edit tray. [page] is null on homes without pages.
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
    onAddApps: () -> Unit = {},
    onAddWidgets: () -> Unit = {},
    onEditHome: () -> Unit = {},
    onDismiss: () -> Unit,
    /** Wave 59: the Hotspots tile (the host opens the Hotspots editor as a full page). */
    onHotspots: () -> Unit = {},
    /** Wave 57 (CFG-48): the profiles for the chip row at the top; null (or one profile) shows no row. */
    profiles: com.bos.sphere.feature.dock.DockWorkspaceState? = null,
    onProfile: (String) -> Unit = {},
) {
    // Wave 41: One UI's sheet shape (28 dp top corners and a grabber) through the shared sheet; the black look and the scrim stay.
    com.bos.sphere.feature.grid.OneUiSheet(onDismiss, containerColor = SheetBlack, scrimColor = Color.Black.copy(alpha = 0.62f)) {
        HomeQuickSheetBody(prefs, update, page, pageCount, onDeletePage, onWallpaper, onGroundControl, onAddApps, onAddWidgets, onEditHome, profiles, onProfile, onHotspots)
    }
}

private val SheetBlack = Color.Black
private val InkWhite = Color.White
private val HairlineWhite = Color(0x33FFFFFF)

/** The sheet's content (separate from the window so screenshots can draw it directly). */
@Composable
internal fun HomeQuickSheetBody(
    prefs: LauncherPrefs,
    update: ((LauncherPrefs) -> LauncherPrefs) -> Unit,
    page: Int?,
    pageCount: Int,
    onDeletePage: () -> Unit,
    onWallpaper: () -> Unit,
    onGroundControl: () -> Unit,
    onAddApps: () -> Unit = {},
    onAddWidgets: () -> Unit = {},
    onEditHome: () -> Unit = {},
    profiles: com.bos.sphere.feature.dock.DockWorkspaceState? = null,
    onProfile: (String) -> Unit = {},
    onHotspots: () -> Unit = {},
) {
    // HOM-21: the content scrolls, so nothing falls off a short window or a large font.
    Column(Modifier.navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 28.dp).padding(bottom = 16.dp)) {
        Text(
            L10n.tr("Home"), color = InkWhite, fontSize = com.bos.sphere.core.design.PopupTokens.TitleSize, fontWeight = com.bos.sphere.core.design.PopupTokens.TitleWeight, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 28.dp, bottom = 8.dp).testTag("quick.sheet").semantics { heading() },
        )
        // Wave 57 (CFG-48): the profiles as one row of chips at the top (Work, Calm, Desk ...); one tap switches. Not shown with a single profile.
        if (profiles != null && profiles.workspaces.size > 1) {
            Heading(L10n.tr("Profile"))
            com.bos.sphere.feature.dock.ProfileChipRow(profiles, onProfile, dark = true)
        }
        // Wave 51: three entry tiles first, so adding and editing is discoverable on any page, empty or not.
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            EntryTile(Glyphs.Apps, "Add apps", "quick.add.apps", Modifier.weight(1f), onAddApps)
            EntryTile(Glyphs.Widget, "Add widgets", "quick.add.widgets", Modifier.weight(1f), onAddWidgets)
            EntryTile(Glyphs.Sliders, "Edit home", "quick.edit", Modifier.weight(1f), onEditHome)
        }
        // Wave 28 / 51: Pages and Order, every value a drawn card.
        Heading(L10n.tr("Pages"))
        CardRow { for (m in com.bos.sphere.core.data.HomePages.entries) OptionCardArt(m.label, prefs.homePages == m, "quick.pages.${m.name}", { update { it.copy(homePages = m) } }, SettingsArtHome.pagesArt(m)) }
        Heading(L10n.tr("Order"))
        CardRow { for (o in com.bos.sphere.core.data.HomeOrder.entries) OptionCardArt(o.label, prefs.homeOrder == o, "quick.order.${o.name}", { update { it.copy(homeOrder = o) } }, SettingsArtHome.orderArt(o.rtl)) }

        // Wave 59 (glanceTop): the Hotspots cards (Four, Two, One, None) are gone; one tile opens the Hotspots editor as a full page.
        Spacer(Modifier.height(18.dp))
        EntryTile(Glyphs.Dial, "Hotspots", "quick.hotspots", Modifier.fillMaxWidth(), onHotspots)

        if (page != null) {
            // HOM-21: this page's rows come from ContextMenus.forHomeSheet, the model touch, right-click and the Menu key share.
            val rows = ContextMenus.forHomeSheet(
                HomeSheetContext(
                    page = page, pageCount = pageCount, alignTop = prefs.gridBias <= 0.05f, alignBottom = prefs.gridBias >= 0.95f,
                    isHomePage = prefs.homePage == page,
                    transitions = PageTransition.entries.map { it.label to (prefs.pageTransition == it) },
                ),
            )
            fun artOf(k: SheetToggle): androidx.compose.ui.graphics.drawscope.DrawScope.(androidx.compose.ui.geometry.Size) -> Unit = when (k) {
                SheetToggle.AlignTop -> alignArt(true)
                SheetToggle.AlignBottom -> alignArt(false)
                else -> makeHomeArt()
            }
            fun tagOf(k: SheetToggle) = when (k) { SheetToggle.AlignTop -> "quick.align.top"; SheetToggle.AlignBottom -> "quick.align.bottom"; else -> "quick.makehome" }
            fun flip(k: SheetToggle) = when (k) {
                SheetToggle.AlignTop -> update { it.copy(gridBias = 0f) }
                SheetToggle.AlignBottom -> update { it.copy(gridBias = 1f) }
                SheetToggle.MakeHome -> update { it.copy(homePage = page) }
                else -> Unit
            }
            Heading(L10n.tr("This page"))
            CardRow { rows.filterIsInstance<AppMenuEntry.Toggle>().filter { it.kind !in GONE }.forEach { t -> OptionCard(t.label, t.on, tagOf(t.kind), { flip(t.kind) }, artOf(t.kind)) } }
            Heading(L10n.tr("Transition"))
            CardRow {
                rows.filterIsInstance<AppMenuEntry.Choice>().forEach { c ->
                    val t = PageTransition.entries.firstOrNull { it.label == c.option }
                    if (t != null) OptionCard(c.option, c.selected, "quick.transition.${t.name}", { update { it.copy(pageTransition = t) } }, transitionArt(t))
                }
            }
            rows.filterIsInstance<AppMenuEntry.Sheet>().firstOrNull { it.action == HomeSheetAction.DeletePage }?.let { r ->
                Spacer(Modifier.height(14.dp))
                TextRow(Glyphs.Close, r.label, "quick.deletepage", onDeletePage, danger = true)
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

@Composable
private fun TextRow(glyph: ImageVector, label: String, tag: String, onClick: () -> Unit, danger: Boolean = false) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag(tag).clickable(role = Role.Button, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val ink = if (danger) Color(0xFFFF7A7A) else InkWhite
        Icon(glyph, null, tint = ink, modifier = Modifier.size(26.dp))
        Spacer(Modifier.width(14.dp))
        Text(L10n.tr(label), color = ink, fontSize = 18.sp)
    }
}
