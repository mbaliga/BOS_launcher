package com.bos.sphere.feature.grid

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bos.sphere.core.data.AppEntry
import com.bos.sphere.core.data.Corner
import com.bos.sphere.core.data.CornerItem
import com.bos.sphere.core.data.HomeLanding
import com.bos.sphere.core.design.HyleColors
import com.bos.sphere.core.design.L10n
import com.bos.sphere.core.input.FocusKind

/** HOM-22: the one-line description of each landing, shared by Ground Control, the quick sheet's tooltips and the screenshots. */
object HomeLandingCopy {
    fun description(l: HomeLanding): String = when (l) {
        HomeLanding.Selected -> L10n.tr("Your own pages, folders and widgets")
        HomeLanding.Recents -> L10n.tr("The apps you opened most recently, newest first")
        HomeLanding.AllApps -> L10n.tr("Every app from A to Z, with an index and type to filter")
        HomeLanding.Minimal -> L10n.tr("Only the wallpaper and four corner hotspots")
    }

    fun label(l: HomeLanding): String = L10n.tr(l.label)
}

/** HOM-22: a tiny drawn picture of a landing (the preview glyph of the choice rows). */
@Composable
fun HomeLandingGlyph(landing: HomeLanding, modifier: Modifier = Modifier.size(40.dp), tint: Color = HyleColors.InkBright) {
    Canvas(modifier) {
        val w = size.width; val h = size.height
        val soft = tint.copy(alpha = 0.45f)
        val r = CornerRadius(w * 0.08f)
        when (landing) {
            HomeLanding.Selected -> {
                val cell = w * 0.26f; val gap = w * 0.08f; val x0 = (w - cell * 2 - gap) / 2f; val y0 = h * 0.1f
                for (row in 0..1) for (col in 0..1) drawRoundRect(tint, Offset(x0 + col * (cell + gap), y0 + row * (cell + gap)), Size(cell, cell), r)
                for (i in 0..2) drawCircle(if (i == 0) tint else soft, w * 0.035f, Offset(w * (0.38f + i * 0.12f), h * 0.88f))
            }
            HomeLanding.Recents -> {
                // newest first: three rows fading out, with a small clock hand on the first icon
                for (i in 0..2) {
                    val y = h * (0.12f + i * 0.28f)
                    val c = tint.copy(alpha = 1f - i * 0.28f)
                    drawRoundRect(c, Offset(w * 0.1f, y), Size(h * 0.2f, h * 0.2f), r)
                    drawRoundRect(c, Offset(w * 0.4f, y + h * 0.07f), Size(w * 0.5f, h * 0.06f), CornerRadius(h * 0.03f))
                }
            }
            HomeLanding.AllApps -> {
                for (i in 0..3) {
                    val y = h * (0.1f + i * 0.22f)
                    drawRoundRect(tint, Offset(w * 0.08f, y), Size(h * 0.16f, h * 0.16f), r)
                    drawRoundRect(soft, Offset(w * 0.34f, y + h * 0.05f), Size(w * 0.42f, h * 0.06f), CornerRadius(h * 0.03f))
                }
                for (i in 0..4) drawRoundRect(soft, Offset(w * 0.88f, h * (0.1f + i * 0.18f)), Size(w * 0.05f, h * 0.06f), CornerRadius(1f))
            }
            HomeLanding.Minimal -> {
                val d = w * 0.075f
                drawRoundRect(soft, Offset(0f, 0f), Size(w, h), r, style = androidx.compose.ui.graphics.drawscope.Stroke(w * 0.03f))
                for (p in listOf(Offset(w * 0.16f, h * 0.16f), Offset(w * 0.84f, h * 0.16f), Offset(w * 0.16f, h * 0.84f), Offset(w * 0.84f, h * 0.84f))) drawCircle(tint, d, p)
            }
        }
    }
}

/** HOM-22: the Ground Control choice: one row per landing, a preview glyph, its name and its one-line description. */
@Composable
fun LandingChoiceList(selected: HomeLanding, onSelect: (HomeLanding) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().selectableGroup().padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (l in HomeLanding.entries) {
            val on = l == selected
            val name = HomeLandingCopy.label(l)
            val desc = HomeLandingCopy.description(l)
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                    .background(if (on) HyleColors.Violet.copy(alpha = 0.22f) else Color(0x12FFFFFF))
                    .selectable(selected = on, role = Role.RadioButton, onClick = { onSelect(l) })
                    .launcherItem("landing.choice.${l.name}", focusable = false, onActivate = { onSelect(l) })
                    .defaultMinSize(minHeight = 56.dp).padding(horizontal = 12.dp, vertical = 10.dp)
                    .semantics(mergeDescendants = true) { contentDescription = "$name. $desc" },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HomeLandingGlyph(l, Modifier.size(40.dp))
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(name, color = HyleColors.InkBright, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    Text(desc, color = HyleColors.InkDim, fontSize = 12.sp)
                }
            }
        }
    }
}

/** HOM-22: the quick sheet's four-way segmented control. Each segment is a radio button; arrows and Enter work through the focus graph. */
@Composable
fun LandingSegmented(selected: HomeLanding, onSelect: (HomeLanding) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color(0x12FFFFFF)).padding(3.dp).selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        for (l in HomeLanding.entries) {
            val on = l == selected
            val name = HomeLandingCopy.label(l)
            Column(
                Modifier.weight(1f).clip(RoundedCornerShape(13.dp))
                    .background(if (on) HyleColors.Violet.copy(alpha = 0.30f) else Color.Transparent)
                    .selectable(selected = on, role = Role.RadioButton, onClick = { onSelect(l) })
                    .launcherItem("landing.segment.${l.name}", focusable = false, onActivate = { onSelect(l) })
                    .defaultMinSize(minHeight = 64.dp).padding(horizontal = 4.dp, vertical = 8.dp)
                    .semantics(mergeDescendants = true) { contentDescription = name },
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
            ) {
                HomeLandingGlyph(l, Modifier.size(26.dp), if (on) HyleColors.InkBright else HyleColors.InkDim)
                Text(name, color = if (on) HyleColors.InkBright else HyleColors.InkDim, fontSize = 11.sp, textAlign = TextAlign.Center, maxLines = 2, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

/** HOM-22: where a corner's hotspot sits and what it runs; one entry per usable corner of the Minimal landing. */
class Hotspot(val screen: Corner, val item: CornerItem)

private fun Corner.cornerAlignment(): Alignment = when (this) {
    Corner.TopLeft -> Alignment.TopStart
    Corner.TopRight -> Alignment.TopEnd
    Corner.BottomLeft -> Alignment.BottomStart
    Corner.BottomRight -> Alignment.BottomEnd
}

private fun Corner.spoken(what: String): String = when (this) {
    Corner.TopLeft -> L10n.tr("Top left hotspot: %s", what)
    Corner.TopRight -> L10n.tr("Top right hotspot: %s", what)
    Corner.BottomLeft -> L10n.tr("Bottom left hotspot: %s", what)
    Corner.BottomRight -> L10n.tr("Bottom right hotspot: %s", what)
}

/**
 * HOM-22, INP-16, INP-17: the four hotspots of the Minimal landing. Each is a labelled button in the focus graph (arrows and Tab reach
 * it, Enter runs it, TalkBack reads "Top left hotspot: Radar"), a click runs it, and [showHints] draws one faint dot per corner.
 * The pointer's dwell reveal is the shared hot-corner layer on the root; this layer is what keyboard, touch and TalkBack use.
 * Corners are physical, so the layer lays out left to right whatever the language.
 */
@Composable
fun MinimalHotspotLayer(
    hotspots: List<Hotspot>,
    appsByKey: Map<String, AppEntry>,
    showHints: Boolean,
    onRun: (CornerItem, Corner) -> Unit,
    modifier: Modifier = Modifier,
) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Box(modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            for (h in hotspots) {
                val what = itemLabel(h.item, appsByKey)
                val spoken = h.screen.spoken(what)
                Box(
                    Modifier.align(h.screen.cornerAlignment()).size(64.dp)
                        .launcherItem("minimal.corner.${h.screen.name}", kind = FocusKind.Corner, focusable = true, onActivate = { onRun(h.item, h.screen) }, ringRadiusDp = 32f)
                        .semantics { role = Role.Button; contentDescription = spoken }
                        .clickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null, onClickLabel = what) { onRun(h.item, h.screen) },
                ) {
                    if (showHints) {
                        val inset = 14.dp
                        Box(
                            Modifier.align(h.screen.cornerAlignment()).padding(inset).size(6.dp)
                                .background(Color.White.copy(alpha = 0.28f), CircleShape),
                        )
                    }
                }
            }
        }
    }
}

/**
 * HOM-22: the Recent apps landing in the list style: newest first (not alphabetical), icon and name per row, the app menu on long
 * press, right-click and the Menu key like every other home list. The home's gesture layer covers the empty space.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RecentsListHome(
    apps: List<AppEntry>,
    onLaunch: (AppEntry) -> Unit,
    onLongPress: (AppEntry) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    iconScale: Float = 0.8f,
    showLabels: Boolean = true,
    gestures: HomeGestureSpec? = null,
) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        val haptic = LocalHapticFeedback.current
        val icon = (46f * iconScale / 0.8f).coerceIn(32f, 60f).dp
        val state = androidx.compose.foundation.lazy.rememberLazyListState()
        Box(modifier.fillMaxSize().homeGestures(gestures) { pos -> state.layoutInfo.visibleItemsInfo.none { pos.y >= it.offset && pos.y < it.offset + it.size } }) {
            LazyColumn(state = state, contentPadding = contentPadding, modifier = Modifier.fillMaxSize()) {
                items(apps, key = { it.key }) { app ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                            .launcherItem("recents.${app.key}", focusable = false, onMenu = { onLongPress(app) })
                            .combinedClickable(onClick = { onLaunch(app) }, onLongClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onLongPress(app) })
                            .padding(vertical = 6.dp, horizontal = 4.dp),
                    ) {
                        AppIcon(app, icon)
                        if (showLabels) {
                            Spacer(Modifier.width(16.dp))
                            Text(app.label, color = HyleColors.InkBright, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

/** HOM-22: the quiet empty state of the Recent apps landing: one line, plus how to include apps opened from elsewhere. */
@Composable
fun RecentsEmptyState(usageAccessOn: Boolean, onGrantUsage: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(horizontal = 32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(L10n.tr("No recent apps yet"), color = HyleColors.InkBright, fontSize = 17.sp, fontWeight = FontWeight.Light, textAlign = TextAlign.Center)
        Text(
            L10n.tr("Apps you open from Liftoff show up here, newest first."), color = HyleColors.InkDim, fontSize = 13.sp, textAlign = TextAlign.Center,
        )
        if (!usageAccessOn) {
            Text(
                L10n.tr("To include apps you open from elsewhere, allow usage access."), color = HyleColors.InkDim, fontSize = 13.sp, textAlign = TextAlign.Center,
            )
            Text(
                L10n.tr("Allow usage access"), color = HyleColors.Violet, fontSize = 13.sp,
                modifier = Modifier.clip(RoundedCornerShape(50)).clickable(role = Role.Button, onClick = onGrantUsage).defaultMinSize(minHeight = 48.dp).padding(horizontal = 16.dp, vertical = 14.dp),
            )
        }
    }
}

/**
 * HOM-22: the alphabet index of the All apps landing for the grid and DSi styles (the list style has its own rail). Drag along it to
 * jump; each letter is also a button for keyboard and TalkBack. [letters] are the first letters present, in order.
 */
@Composable
fun BoxScope.LetterIndexRail(letters: List<Char>, onJump: (Char) -> Unit, modifier: Modifier = Modifier, topPad: androidx.compose.ui.unit.Dp = 0.dp, bottomPad: androidx.compose.ui.unit.Dp = 0.dp) {
    if (letters.size < 2) return
    val haptic = LocalHapticFeedback.current
    var active by remember { mutableStateOf<Char?>(null) }
    val latest = androidx.compose.runtime.rememberUpdatedState(onJump)
    Column(
        modifier.align(Alignment.CenterEnd).fillMaxHeight().width(30.dp).padding(top = topPad, bottom = bottomPad)
            .pointerInput(letters) {
                fun at(y: Float) {
                    val i = ((y / size.height.coerceAtLeast(1)) * letters.size).toInt().coerceIn(0, letters.size - 1)
                    val l = letters[i]
                    if (l != active) { active = l; haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); latest.value(l) }
                }
                detectDragGestures(onDragStart = { at(it.y) }, onDragEnd = { active = null }, onDragCancel = { active = null }) { c, _ -> c.consume(); at(c.position.y) }
            }
            .semantics { contentDescription = L10n.tr("Alphabet index") },
        verticalArrangement = Arrangement.SpaceEvenly, horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        letters.forEach { l ->
            Text(
                l.toString(), color = if (l == active) HyleColors.InkBright else HyleColors.InkDim, fontSize = 10.sp,
                modifier = Modifier.clickable(role = Role.Button) { latest.value(l) }.semantics { contentDescription = L10n.tr("Jump to %s", l.toString()) },
            )
        }
    }
    active?.let { l ->
        Box(Modifier.align(Alignment.Center).size(84.dp).background(HyleColors.Surface.copy(alpha = 0.92f), CircleShape), contentAlignment = Alignment.Center) {
            Text(l.toString(), color = HyleColors.InkBright, fontSize = 40.sp, fontWeight = FontWeight.Light)
        }
    }
}
