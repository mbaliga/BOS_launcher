package com.bos.sphere.feature.grid

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.clickable
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import com.bos.sphere.core.input.FocusKind
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bos.sphere.core.data.AppEntry
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/** One column of the cross media bar: a named set of apps. */
internal data class XmbCategory(val name: String, val isRecent: Boolean, val apps: List<AppEntry>)

/** Pure helpers behind [XmbHome] (unit tested). */
internal object XmbMath {
    const val MaxCategories = 9
    const val RecentName = "Recent"
    const val OtherName = "Other"

    /**
     * Recent first (when it has apps), then one category per distinct [categoryOf] name, biggest
     * first (ties alphabetical). Beyond [max] columns the smallest groups fold into "Other".
     */
    fun categories(
        apps: List<AppEntry>,
        recent: List<AppEntry>,
        categoryOf: (AppEntry) -> String,
        max: Int = MaxCategories,
    ): List<XmbCategory> {
        val out = ArrayList<XmbCategory>()
        val rec = recent.distinctBy { it.key }
        if (rec.isNotEmpty()) out += XmbCategory(RecentName, true, rec)
        val groups = apps.groupBy { categoryOf(it).trim().ifEmpty { OtherName } }
            .map { (n, l) -> XmbCategory(n, false, l) }
            .sortedWith(compareByDescending<XmbCategory> { it.apps.size }.thenBy { it.name.lowercase() })
        val budget = (max - out.size).coerceAtLeast(1)
        if (groups.size <= budget) {
            out += groups
        } else {
            val keep = groups.take(budget - 1).toMutableList()
            val rest = groups.drop(budget - 1).flatMap { it.apps }
            val oi = keep.indexOfFirst { it.name.equals(OtherName, true) }
            if (oi >= 0) keep[oi] = keep[oi].copy(apps = keep[oi].apps + rest)
            else keep += XmbCategory(OtherName, false, rest)
            out += keep
        }
        return out
    }

    /** A sensible glyph for a category name; [Glyphs.Apps] when nothing matches. */
    fun glyphFor(name: String, isRecent: Boolean = false): ImageVector {
        if (isRecent) return Glyphs.Clock
        val n = name.lowercase()
        fun has(vararg k: String) = k.any { n.contains(it) }
        return when {
            has("music", "audio", "sound", "podcast", "radio") -> Glyphs.Music
            has("photo", "camera", "video", "movie", "film", "tv", "stream") -> Glyphs.Camera
            has("image", "gallery", "picture", "art") -> Glyphs.Image
            has("social", "message", "chat", "messag", "communic") -> Glyphs.Message
            has("phone", "call", "dial") -> Glyphs.Phone
            has("web", "browser", "internet", "news", "online") -> Glyphs.Globe
            has("mail", "email") -> Glyphs.Mail
            has("map", "navig", "travel", "transport", "weather") -> Glyphs.Map
            has("tool", "util", "system", "setting", "device", "personali") -> Glyphs.Gear
            has("product", "office", "business", "work", "calendar", "organi") -> Glyphs.Calendar
            has("finance", "money", "bank", "shopping", "calculat") -> Glyphs.Calculator
            has("game", "play", "fun", "entertain") -> Glyphs.Bolt
            has("health", "fitness", "people", "contact") -> Glyphs.Person
            has("time", "clock", "alarm") -> Glyphs.Clock
            has("battery", "power", "energy") -> Glyphs.Battery
            else -> Glyphs.Apps
        }
    }

    /** 1 at the anchor, falling to 0 one slot away. */
    fun closeness(d: Float): Float = (1f - abs(d)).coerceIn(0f, 1f)

    /** Slot a fling settles on: the position projected [lookAheadSec] along the velocity. */
    fun settleTarget(pos: Float, velocityPerSec: Float, lastIndex: Int, lookAheadSec: Float = 0.12f): Int =
        (pos + velocityPerSec * lookAheadSec).roundToInt().coerceIn(0, lastIndex.coerceAtLeast(0))

    /** Position after a drag, with a soft stretch (max [slack] slots) past either end. */
    fun clampStretch(pos: Float, lastIndex: Int, slack: Float = 0.35f): Float =
        pos.coerceIn(-slack, lastIndex.coerceAtLeast(0) + slack)
}

private class XmbGeo(
    val w: Float,
    val h: Float,
    val anchorX: Float,
    val barY: Float,
    val catSpacing: Float,
    val catBox: Float,
    val rowH: Float,
    val extra: Float,
    val iconBox: Float,
    val listY0: Float,
    val bottomY: Float,
    val rowW: Float,
    val leftX: Float,
) {
    /** Vertical offset of a row [d] slots from the focus (the focused row gets breathing room). */
    fun rowOffset(d: Float) = d * rowH + d.coerceIn(-1f, 1f) * extra
}

/**
 * A PlayStation-Portable style cross media bar. A horizontal bar of category glyphs (Recent,
 * then one per [categoryOf] name); the selected category sits at a fixed anchor and its apps
 * run in a vertical list beneath it, the focused app just under the bar. Swipe sideways for
 * categories and up/down for apps; tap launches (a first tap on an unfocused app focuses it).
 * No opaque background is drawn; soft wave ribbons float behind.
 */
@Composable
fun XmbHome(
    apps: List<AppEntry>,
    recent: List<AppEntry>,
    categoryOf: (AppEntry) -> String,
    onLaunch: (AppEntry) -> Unit,
    onLongPress: (AppEntry) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    showLabels: Boolean = true,
    sounds: UiSounds? = null,
    /**
     * Pinch and double tap (see [homeGestures]); swipes are the bar's own here, both ways. Rows and
     * category glyphs consume their taps, so "empty" is wherever nothing claimed the release.
     */
    gestures: HomeGestureSpec? = null,
) {
    // Physical coordinates: layout and swipes must not mirror under RTL.
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        XmbHomeImpl(apps, recent, categoryOf, onLaunch, onLongPress, modifier.homeGestures(gestures), contentPadding, showLabels, sounds)
    }
}

@Composable
private fun XmbHomeImpl(
    apps: List<AppEntry>,
    recent: List<AppEntry>,
    categoryOf: (AppEntry) -> String,
    onLaunch: (AppEntry) -> Unit,
    onLongPress: (AppEntry) -> Unit,
    modifier: Modifier,
    contentPadding: PaddingValues,
    showLabels: Boolean,
    sounds: UiSounds?,
) {
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val latestCategoryOf by rememberUpdatedState(categoryOf)
    val cats = remember(apps, recent) { XmbMath.categories(apps, recent, categoryOf = { latestCategoryOf(it) }) }
    val lastCat = cats.lastIndex

    var catPos by remember { mutableFloatStateOf(0f) }
    var listPos by remember { mutableFloatStateOf(0f) }
    val jobs = remember { JobPair() }
    // Keep the selection valid if the categories shrink under us.
    if (catPos > lastCat.coerceAtLeast(0)) catPos = lastCat.coerceAtLeast(0).toFloat()

    val shownCat by remember { derivedStateOf { catPos.roundToInt().coerceIn(0, lastCat.coerceAtLeast(0)) } }
    val shownApps = cats.getOrNull(shownCat)?.apps.orEmpty()
    val lastApp = shownApps.lastIndex
    val focusIdx by remember { derivedStateOf { listPos.roundToInt().coerceIn(0, lastApp.coerceAtLeast(0)) } }

    val lastCatNow by rememberUpdatedState(lastCat)
    val lastAppNow by rememberUpdatedState(lastApp)
    val latestLaunch by rememberUpdatedState(onLaunch)
    val latestLong by rememberUpdatedState(onLongPress)
    val latestSounds by rememberUpdatedState(sounds)

    // Sound + haptic on every slot crossed; a category change also restarts the list at the top.
    LaunchedEffect(Unit) {
        var first = true
        snapshotFlow { catPos.roundToInt().coerceIn(0, lastCat.coerceAtLeast(0)) }.distinctUntilChanged().collect {
            if (first) { first = false; return@collect }
            jobs.list?.cancel(); listPos = 0f
            latestSounds?.play(UiSound.Tick)
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }
    LaunchedEffect(Unit) {
        var first = true
        snapshotFlow { focusIdx }.distinctUntilChanged().collect {
            if (first) { first = false; return@collect }
            latestSounds?.play(UiSound.Hover)
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    BoxWithConstraints(modifier.fillMaxSize()) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        val landscape = w > h
        val pad = contentPadding
        val geo = remember(w, h, pad, density) {
            with(density) {
                val l = pad.calculateLeftPadding(LayoutDirection.Ltr).toPx()
                val r = pad.calculateRightPadding(LayoutDirection.Ltr).toPx()
                val t = pad.calculateTopPadding().toPx()
                val b = pad.calculateBottomPadding().toPx()
                val innerW = (w - l - r).coerceAtLeast(1f)
                val innerH = (h - t - b).coerceAtLeast(1f)
                val anchor = l + innerW * (if (landscape) 0.13f else 0.25f)
                val box = 56.dp.toPx()
                val rowH = (if (landscape) 56.dp else 66.dp).toPx()
                val barY = t + innerH * (if (landscape) 0.17f else 0.2f)
                val spacing = minOf(innerW * (if (landscape) 0.1f else 0.19f), 84.dp.toPx())
                XmbGeo(
                    w = w, h = h, anchorX = anchor, barY = barY, catSpacing = spacing, catBox = box,
                    rowH = rowH, extra = 14.dp.toPx(), iconBox = box,
                    listY0 = barY + (if (landscape) 84.dp else 98.dp).toPx(),
                    bottomY = h - b,
                    rowW = (w - r - (anchor - box / 2)).coerceAtLeast(box),
                    leftX = l,
                )
            }
        }
        val latestGeo by rememberUpdatedState(geo)

        WaveField(Modifier.fillMaxSize().alpha(0.55f))

        fun settleCat(target: Int, velocity: Float = 0f) {
            jobs.cat?.cancel()
            jobs.cat = scope.launch {
                animate(catPos, target.toFloat(), velocity, spring(0.85f, 320f)) { v, _ -> catPos = v }
            }
        }
        fun settleList(target: Int, velocity: Float = 0f) {
            jobs.list?.cancel()
            jobs.list = scope.launch {
                animate(listPos, target.toFloat(), velocity, spring(0.85f, 380f)) { v, _ -> listPos = v }
            }
        }

        // Gesture layer + content. Rows/icons carry their own tap handlers; this drags.
        Box(
            Modifier.fillMaxSize().linearTraversal().pointerInput(Unit) {
                val tracker = VelocityTracker()
                var axis = 0 // 0 undecided, 1 horizontal, 2 vertical
                var acc = Offset.Zero
                fun end() {
                    val g = latestGeo
                    val v = tracker.calculateVelocity()
                    if (axis == 1) settleCat(XmbMath.settleTarget(catPos, -v.x / g.catSpacing, lastCatNow), -v.x / g.catSpacing)
                    else if (axis == 2) settleList(XmbMath.settleTarget(listPos, -v.y / g.rowH, lastAppNow), -v.y / g.rowH)
                    axis = 0
                }
                detectDragGestures(
                    onDragStart = { jobs.cat?.cancel(); jobs.list?.cancel(); tracker.resetTracking(); axis = 0; acc = Offset.Zero },
                    onDragEnd = { end() },
                    onDragCancel = { end() },
                ) { change, amount ->
                    tracker.addPosition(change.uptimeMillis, change.position)
                    change.consume()
                    var d = amount
                    if (axis == 0) {
                        acc += amount
                        if (acc.getDistance() < 3f) return@detectDragGestures
                        axis = if (abs(acc.x) >= abs(acc.y)) 1 else 2
                        d = acc
                    }
                    val g = latestGeo
                    if (axis == 1) catPos = XmbMath.clampStretch(catPos - d.x / g.catSpacing, lastCatNow)
                    else listPos = XmbMath.clampStretch(listPos - d.y / g.rowH, lastAppNow)
                }
            },
        ) {
            // ---- category bar ----
            cats.forEachIndexed { i, cat ->
                val interaction = remember { MutableInteractionSource() }
                Box(
                    Modifier
                        .offset { IntOffset((geo.anchorX + (i - catPos) * geo.catSpacing - geo.catBox / 2).roundToInt(), (geo.barY - geo.catBox / 2).roundToInt()) }
                        .size(with(density) { geo.catBox.toDp() })
                        .graphicsLayer {
                            val c = XmbMath.closeness(i - catPos)
                            val s = 0.66f + 0.34f * c
                            scaleX = s; scaleY = s
                            alpha = (0.38f + 0.62f * c) * (1f - ((geo.leftX + 4f - (geo.anchorX + (i - catPos) * geo.catSpacing)) / geo.catSpacing).coerceIn(0f, 1f))
                        }
                        .drawWithCache {
                            val glow = Brush.radialGradient(listOf(Color.White.copy(alpha = 0.20f), Color.Transparent), center = Offset(size.width / 2, size.height / 2), radius = size.width / 2)
                            onDrawBehind { drawCircle(glow) }
                        }
                        .semantics { contentDescription = cat.name }
                        .launcherItem("xmb.cat.$i", FocusKind.Category, order = i, focusable = false, onFocused = { if (i != shownCat) settleCat(i) }, ringRadiusDp = 26f)
                        .clickable(interaction, null) { if (i != shownCat) settleCat(i) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(XmbMath.glyphFor(cat.name, cat.isRecent), null, tint = Color.White, modifier = Modifier.size(30.dp))
                }
                // Label under the selected one only (fades with closeness).
                Box(
                    Modifier
                        .offset { IntOffset((geo.anchorX + (i - catPos) * geo.catSpacing - 50.dp.toPx()).roundToInt(), (geo.barY + geo.catBox / 2 + 2.dp.toPx()).roundToInt()) }
                        .requiredWidth(100.dp)
                        .graphicsLayer { alpha = XmbMath.closeness((i - catPos) * 1.6f) },
                    contentAlignment = Alignment.TopCenter,
                ) {
                    Text(
                        cat.name, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1,
                        overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                        style = TextStyle(shadow = Shadow(Color.Black.copy(alpha = 0.6f), Offset(0f, 1f), 6f)),
                    )
                }
            }

            // ---- app list of the shown category ----
            key(shownCat) {
                val first = (focusIdx - 2).coerceAtLeast(0)
                val visible = ((geo.bottomY - geo.listY0) / geo.rowH).toInt() + 3
                val last = (focusIdx + visible).coerceAtMost(lastApp)
                for (i in first..last) {
                    val app = shownApps.getOrNull(i) ?: break
                    key(app.key) {
                        XmbRow(
                            app = app, index = i, geo = geo,
                            listPos = { listPos }, catOffset = { catPos - shownCat },
                            showLabels = showLabels,
                            itemModifier = Modifier.launcherItem("xmb.row.${app.key}", order = 1000 + i, focusable = false, onFocused = { if (abs(listPos - i) > 0.5f) settleList(i) }, onMenu = { latestLong(app) }),
                            onTap = {
                                if (abs(listPos - i) > 0.5f) settleList(i)
                                else { latestSounds?.play(UiSound.Select); latestLaunch(app) }
                            },
                            onLong = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                latestLong(app)
                            },
                        )
                    }
                }
            }
        }
    }
}

private class JobPair { var cat: Job? = null; var list: Job? = null }

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun XmbRow(
    app: AppEntry,
    index: Int,
    geo: XmbGeo,
    listPos: () -> Float,
    catOffset: () -> Float,
    showLabels: Boolean,
    itemModifier: Modifier,
    onTap: () -> Unit,
    onLong: () -> Unit,
) {
    val density = LocalDensity.current
    val interaction = remember { MutableInteractionSource() }
    Row(
        Modifier
            .offset {
                val d = index - listPos()
                IntOffset(
                    (geo.anchorX - geo.iconBox / 2 - catOffset() * geo.catSpacing * 0.5f).roundToInt(),
                    (geo.listY0 + geo.rowOffset(d) - geo.iconBox / 2).roundToInt(),
                )
            }
            .width(with(density) { geo.rowW.toDp() })
            .graphicsLayer {
                val d = index - listPos()
                val c = XmbMath.closeness(d)
                val s = 0.74f + 0.26f * c
                scaleX = s; scaleY = s
                transformOrigin = TransformOrigin(geo.iconBox / 2 / geo.rowW, 0.5f)
                // Rows scrolling up under the bar fade out, rows near the bottom edge fade away.
                val top = if (d < 0f) (1f + d).coerceIn(0f, 1f) else 1f
                val y = geo.listY0 + geo.rowOffset(d)
                val bottom = ((geo.bottomY - y) / geo.rowH).coerceIn(0f, 1f)
                val fade = 1f - (abs(catOffset()) * 2f).coerceAtMost(1f)
                alpha = (0.62f + 0.38f * c) * top * bottom * fade
            }
            .semantics { contentDescription = app.label }
            .then(itemModifier)
            .combinedClickable(interaction, null, onClick = onTap, onLongClick = onLong),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(app, 56.dp)
        Spacer(Modifier.width(14.dp))
        // Labels: all rows, or only the focused one when labels are off.
        Box(
            Modifier.graphicsLayer {
                val c = XmbMath.closeness(index - listPos())
                alpha = if (showLabels) 1f else ((c - 0.5f) * 2f).coerceIn(0f, 1f)
            },
        ) {
            Text(
                app.label, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Medium, maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(shadow = Shadow(Color.Black.copy(alpha = 0.65f), Offset(0f, 1f), 8f)),
            )
        }
    }
}
