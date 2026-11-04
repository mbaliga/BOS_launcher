package com.bos.sphere.feature.grid

import android.content.Context
import android.content.pm.ApplicationInfo
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bos.sphere.core.data.AppEntry
import com.bos.sphere.core.data.Corner
import com.bos.sphere.core.data.WheelGrouping
import com.bos.sphere.core.design.HyleColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.Normalizer
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

// =============================================================================================
// Pure geometry (JVM-testable; see WheelGeometryTest)
// =============================================================================================

internal data class WheelPoint(val x: Float, val y: Float)

/** [angleDeg] is a screen angle in [0, 360): 0 = +x (right), increasing clockwise (y is down). */
internal data class WheelPolar(val radius: Float, val angleDeg: Float)

/**
 * One group's arc of the wheel, in *wheel degrees* (0 = the wheel's leading edge; the screen
 * angle is `quadrantStart + wheelAngle + rotation`). [spokeAngles] are its sub-spokes' angles.
 */
internal data class WheelSegment(val start: Float, val end: Float, val spokeAngles: List<Float>) {
    val center: Float get() = (start + end) / 2f
    val width: Float get() = end - start
}

internal data class WheelLayout(val segments: List<WheelSegment>) {
    val span: Float get() = segments.lastOrNull()?.end ?: 0f
    val firstSpoke: Float? get() = segments.firstOrNull()?.spokeAngles?.firstOrNull()
    val lastSpoke: Float? get() = segments.lastOrNull()?.spokeAngles?.lastOrNull()
}

/**
 * Polar geometry of a wheel centred on a screen corner. Screen angles follow Compose's `drawArc`
 * convention (0° = right, clockwise positive). Each corner's on-screen quadrant is
 * `[quadrantStart, quadrantStart + 90]`; wheel content runs clockwise through it.
 */
internal object WheelGeometry {
    const val QUADRANT = 90f
    const val MID = QUADRANT / 2f

    /** Screen angle where [anchor]'s visible quadrant begins (TopLeft 0, TopRight 90, BottomRight 180, BottomLeft 270). */
    fun quadrantStart(anchor: Corner): Float = anchor.ordinal * QUADRANT

    fun cornerX(anchor: Corner, width: Float): Float =
        if (anchor == Corner.TopRight || anchor == Corner.BottomRight) width else 0f

    fun cornerY(anchor: Corner, height: Float): Float =
        if (anchor == Corner.BottomRight || anchor == Corner.BottomLeft) height else 0f

    fun polarToScreen(anchor: Corner, width: Float, height: Float, radius: Float, angleDeg: Float): WheelPoint {
        val rad = Math.toRadians(angleDeg.toDouble())
        return WheelPoint(
            cornerX(anchor, width) + radius * cos(rad).toFloat(),
            cornerY(anchor, height) + radius * sin(rad).toFloat(),
        )
    }

    fun screenToPolar(anchor: Corner, width: Float, height: Float, x: Float, y: Float): WheelPolar {
        val dx = x - cornerX(anchor, width)
        val dy = y - cornerY(anchor, height)
        val a = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
        return WheelPolar(hypot(dx, dy), normalize360(a))
    }

    /** [0, 360). */
    fun normalize360(deg: Float): Float = ((deg % 360f) + 360f) % 360f

    /** [-180, 180). */
    fun normalize180(deg: Float): Float = normalize360(deg + 180f) - 180f

    fun screenAngle(anchor: Corner, wheelAngle: Float, rotation: Float): Float =
        quadrantStart(anchor) + wheelAngle + rotation

    /** Inverse of [screenAngle] for a point on screen (resolves to the winding nearest the quadrant). */
    fun wheelAngleAt(anchor: Corner, screenAngleDeg: Float, rotation: Float): Float =
        normalize180(screenAngleDeg - quadrantStart(anchor)) - rotation

    /** Icons that fit on one spoke between [firstRadius] and [maxRadius] (always ≥ 1). */
    fun spokeCapacity(firstRadius: Float, maxRadius: Float, radialStep: Float): Int =
        if (radialStep <= 0f || maxRadius <= firstRadius) 1
        else floor((maxRadius - firstRadius) / radialStep).toInt() + 1

    fun subSpokes(count: Int, perSpoke: Int): Int {
        val per = perSpoke.coerceAtLeast(1)
        return ((count + per - 1) / per).coerceAtLeast(1)
    }

    /**
     * Lays groups end to end. Each segment is `gap + subSpokes * spokeStep` wide (so band widths
     * are proportional to the spokes), with sub-spokes evenly spaced inside it.
     */
    fun layout(groupSizes: List<Int>, perSpoke: Int, spokeStep: Float, gap: Float): WheelLayout {
        var at = 0f
        val segments = groupSizes.map { n ->
            val k = subSpokes(n, perSpoke)
            val start = at
            val end = start + gap + k * spokeStep
            at = end
            WheelSegment(start, end, List(k) { start + gap / 2f + spokeStep * (it + 0.5f) })
        }
        return WheelLayout(segments)
    }

    /** Rotation range: the first spoke can come to the middle of the quadrant, as can the last. */
    fun rotationBounds(layout: WheelLayout): ClosedFloatingPointRange<Float> {
        val first = layout.firstSpoke ?: return 0f..0f
        val last = layout.lastSpoke ?: first
        return (MID - last)..(MID - first)
    }

    fun clampRotation(rotation: Float, layout: WheelLayout): Float = rotation.coerceIn(rotationBounds(layout))

    /** Centres a wheel that fits in the quadrant; otherwise starts it [insetDeg] past the leading edge. */
    fun initialRotation(layout: WheelLayout, insetDeg: Float): Float {
        val r = if (layout.span <= QUADRANT - 2f * insetDeg) MID - layout.span / 2f else insetDeg
        return clampRotation(r, layout)
    }

    /** Rotation that brings segment [index]'s centre to the middle of the quadrant (clamped). */
    fun rotationToCenter(layout: WheelLayout, index: Int): Float =
        clampRotation(MID - layout.segments[index].center, layout)

    /** Segment containing [wheelAngle], or -1. */
    fun segmentAt(layout: WheelLayout, wheelAngle: Float): Int =
        layout.segments.indexOfFirst { wheelAngle >= it.start && wheelAngle < it.end }

    /** Segment under the middle of the quadrant at [rotation], or -1. */
    fun focusedSegment(layout: WheelLayout, rotation: Float): Int = segmentAt(layout, MID - rotation)

    fun inView(wheelAngle: Float, rotation: Float, marginDeg: Float): Boolean =
        (wheelAngle + rotation) in -marginDeg..(QUADRANT + marginDeg)

    /** Indices of ascending [angles] within the quadrant (± [marginDeg]); empty range if none. */
    fun visibleRange(angles: List<Float>, rotation: Float, marginDeg: Float): IntRange {
        var first = -1
        var last = -2
        for (i in angles.indices) {
            if (inView(angles[i], rotation, marginDeg)) {
                if (first < 0) first = i
                last = i
            }
        }
        return if (first < 0) IntRange.EMPTY else first..last
    }

    /** Angular velocity (deg/s, clockwise positive) of a point at ([rx], [ry]) from the centre moving at ([vx], [vy]). */
    fun angularVelocityDeg(rx: Float, ry: Float, vx: Float, vy: Float): Float {
        val r2 = rx * rx + ry * ry
        if (r2 < 1e-3f) return 0f
        return Math.toDegrees(((rx * vy - ry * vx) / r2).toDouble()).toFloat()
    }

    /** rotationZ (deg) for text laid tangentially at [screenAngleDeg], flipped so it never reads upside down. */
    fun tangentTextRotation(screenAngleDeg: Float): Float {
        val r = normalize180(screenAngleDeg + 90f)
        return when {
            r > 90f -> r - 180f
            r < -90f -> r + 180f
            else -> r
        }
    }
}

// =============================================================================================
// Grouping
// =============================================================================================

internal data class WheelGroup(val label: String, val apps: List<AppEntry>)

/** Letter bucket for a label: the base (accent-stripped) first letter, upper-cased; "#" for non-letters. */
internal fun wheelLetterOf(label: String): String {
    val c = label.trimStart().firstOrNull() ?: return "#"
    val base = Normalizer.normalize(c.toString(), Normalizer.Form.NFD).firstOrNull() ?: c
    return if (base.isLetter()) base.uppercaseChar().toString() else "#"
}

/** Letters in order, "#" last. */
internal val WheelLetterOrder: Comparator<String> = Comparator { a, b ->
    when {
        a == b -> 0
        a == "#" -> 1
        b == "#" -> -1
        else -> a.compareTo(b)
    }
}

/** Display order of category spokes. */
internal val WheelCategoryOrder = listOf(
    "Games", "Audio", "Video", "Images", "Social", "News", "Maps", "Productivity", "Accessibility", "Other",
)

internal fun wheelCategoryName(category: Int): String = when (category) {
    ApplicationInfo.CATEGORY_GAME -> "Games"
    ApplicationInfo.CATEGORY_AUDIO -> "Audio"
    ApplicationInfo.CATEGORY_VIDEO -> "Video"
    ApplicationInfo.CATEGORY_IMAGE -> "Images"
    ApplicationInfo.CATEGORY_SOCIAL -> "Social"
    ApplicationInfo.CATEGORY_NEWS -> "News"
    ApplicationInfo.CATEGORY_MAPS -> "Maps"
    ApplicationInfo.CATEGORY_PRODUCTIVITY -> "Productivity"
    ApplicationInfo.CATEGORY_ACCESSIBILITY -> "Accessibility"
    else -> "Other"
}

/**
 * Process-wide cache of `ApplicationInfo.category` per package, filled off the main thread the
 * first time the wheel is grouped by category. Call [invalidate] when a package is updated.
 */
object WheelCategoryCache {
    private val cache = ConcurrentHashMap<String, Int>()

    fun invalidate(packageName: String? = null) {
        if (packageName == null) cache.clear() else cache.remove(packageName)
    }

    /** All of [packages] from the cache, or null if any is missing. */
    internal fun cachedFor(packages: Collection<String>): Map<String, Int>? {
        val out = HashMap<String, Int>(packages.size)
        for (p in packages) out[p] = cache[p] ?: return null
        return out
    }

    internal suspend fun resolve(context: Context, packages: Collection<String>): Map<String, Int> =
        withContext(Dispatchers.IO) {
            val pm = context.packageManager
            packages.distinct().associateWith { p ->
                cache.getOrPut(p) {
                    runCatching { pm.getApplicationInfo(p, 0).category }
                        .getOrDefault(ApplicationInfo.CATEGORY_UNDEFINED)
                }
            }
        }
}

private const val MAX_RECENT = 24
private val ByLabel: Comparator<AppEntry> = compareBy(String.CASE_INSENSITIVE_ORDER) { it.label }

private fun buildWheelGroups(
    apps: List<AppEntry>,
    grouping: WheelGrouping,
    recentKeys: List<String>,
    categories: Map<String, Int>?,
): List<WheelGroup> = when (grouping) {
    WheelGrouping.Letter -> apps.groupBy { wheelLetterOf(it.label) }
        .toSortedMap(WheelLetterOrder)
        .map { (letter, list) -> WheelGroup(letter, list.sortedWith(ByLabel)) }

    WheelGrouping.Category -> if (categories == null) {
        emptyList()
    } else {
        val byName = apps.groupBy { wheelCategoryName(categories[it.packageName] ?: ApplicationInfo.CATEGORY_UNDEFINED) }
        WheelCategoryOrder.mapNotNull { name -> byName[name]?.let { WheelGroup(name, it.sortedWith(ByLabel)) } }
    }

    WheelGrouping.Recent -> {
        val byKey = apps.associateBy { it.key }
        val recent = recentKeys.asSequence().distinct().mapNotNull { byKey[it] }.take(MAX_RECENT).toList()
        if (recent.isEmpty()) emptyList() else listOf(WheelGroup("Recent", recent))
    }
}

// =============================================================================================
// Metrics
// =============================================================================================

/** Pixel metrics for a wheel on a [width]×[height] px screen. */
private class WheelMetrics(width: Float, height: Float, density: Density) {
    val minSide = min(width, height)
    val iconDp: Dp = with(density) { (minSide.toDp() * 0.11f).coerceIn(40.dp, 52.dp) }
    val iconPx: Float
    val hubRadius: Float
    val bandInner: Float
    val bandOuter: Float
    val firstRadius: Float
    val radialStep: Float
    val perSpoke: Int
    val spokeStepDeg: Float
    val gapDeg: Float
    val outerRadius: Float
    val chipRadius: Float
    val searchRadius: Float
    val labelPad: Float

    init {
        with(density) {
            iconPx = iconDp.toPx()
            hubRadius = (minSide * 0.34f).coerceAtMost(190.dp.toPx())
            bandInner = hubRadius + 3.dp.toPx()
            bandOuter = bandInner + 26.dp.toPx()
            firstRadius = bandOuter + 14.dp.toPx() + iconPx / 2f
            radialStep = iconPx + 14.dp.toPx()
            val maxRadius = minSide - 10.dp.toPx() - iconPx / 2f
            perSpoke = WheelGeometry.spokeCapacity(firstRadius, maxRadius, radialStep)
            spokeStepDeg = Math.toDegrees(((iconPx + 10.dp.toPx()) / firstRadius).toDouble()).toFloat()
            gapDeg = spokeStepDeg * 0.35f
            outerRadius = firstRadius + (perSpoke - 1) * radialStep + iconPx / 2f + 16.dp.toPx()
            chipRadius = hubRadius * 0.8f
            searchRadius = hubRadius * 0.34f
            labelPad = 8.dp.toPx()
        }
    }

    val bandMid: Float get() = (bandInner + bandOuter) / 2f
    val bandThickness: Float get() = bandOuter - bandInner
}

private class Spoke(val angle: Float, val apps: List<AppEntry>)

private const val INSET_DEG = 4f
private const val CULL_MARGIN_DEG = 30f
// Spread across the hub; chips stay upright (readable) and inset from the screen edges.
private val ChipAngles = floatArrayOf(13f, 45f, 77f)

/** Unfurl: the wheel grows from 35% and sweeps 60° clockwise into place as [p] goes 0 → 1. */
private fun openScale(p: Float) = 0.35f + 0.65f * p
private fun openRotation(p: Float) = -60f * (1f - p)

// =============================================================================================
// Composable
// =============================================================================================

/**
 * The "All apps" drawer as a Concepts-style colour wheel centred on screen corner [anchor].
 *
 * Layout from the corner outward: a hub disc holding the [grouping] chips and a search button
 * ([onSearch]); a ring band with one segment per group (tap to bring that group to the middle);
 * then one spoke of upright icons per group (large groups split into adjacent sub-spokes). Only
 * the on-screen quadrant is visible; drag around the corner to rotate (with fling). Tapping the
 * scrim outside the wheel, or Back, calls [onDismiss]. Only icons near the visible quadrant are
 * composed. Category grouping reads `ApplicationInfo.category` off the main thread, cached in
 * [WheelCategoryCache].
 */
@Composable
fun WheelDrawer(
    visible: Boolean,
    anchor: Corner,
    apps: List<AppEntry>,
    grouping: WheelGrouping,
    onGroupingChange: (WheelGrouping) -> Unit,
    recentKeys: List<String>,
    onLaunch: (AppEntry) -> Unit,
    onLongPressApp: (AppEntry) -> Unit,
    onSearch: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val progress = animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow),
        label = "wheelOpen",
    )
    BackHandler(enabled = visible) { onDismiss() }
    val visibleNow by rememberUpdatedState(visible)
    val shown by remember { derivedStateOf { visibleNow || progress.value > 0.002f } }
    if (!shown) return

    // The wheel is physical geometry around a screen corner: lay it out LTR so RTL locales don't
    // shift every positioned child away from where its hit-test expects it.
    androidx.compose.runtime.CompositionLocalProvider(
        androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Ltr,
    ) {
    WheelContent(
        anchor = anchor,
        apps = apps,
        grouping = grouping,
        onGroupingChange = onGroupingChange,
        recentKeys = recentKeys,
        onLaunch = onLaunch,
        onLongPressApp = onLongPressApp,
        onSearch = onSearch,
        onDismiss = onDismiss,
        interactive = visible,
        progress = progress,
        modifier = modifier,
    )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WheelContent(
    anchor: Corner,
    apps: List<AppEntry>,
    grouping: WheelGrouping,
    onGroupingChange: (WheelGrouping) -> Unit,
    recentKeys: List<String>,
    onLaunch: (AppEntry) -> Unit,
    onLongPressApp: (AppEntry) -> Unit,
    onSearch: () -> Unit,
    onDismiss: () -> Unit,
    interactive: Boolean,
    progress: State<Float>,
    modifier: Modifier,
) {
    BoxWithConstraints(modifier) {
        val density = LocalDensity.current
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        val metrics = remember(w, h, density) { WheelMetrics(w, h, density) }
        val qs = WheelGeometry.quadrantStart(anchor)
        val context = LocalContext.current.applicationContext
        val scope = rememberCoroutineScope()
        val haptics = LocalHapticFeedback.current

        val categories by produceState(
            initialValue = WheelCategoryCache.cachedFor(apps.map { it.packageName }),
            apps, grouping,
        ) {
            if (grouping == WheelGrouping.Category && value == null) {
                value = WheelCategoryCache.resolve(context, apps.map { it.packageName })
            }
        }
        val groups = remember(apps, grouping, recentKeys, categories) {
            buildWheelGroups(apps, grouping, recentKeys, categories)
        }
        val layout = remember(groups, metrics) {
            WheelGeometry.layout(groups.map { it.apps.size }, metrics.perSpoke, metrics.spokeStepDeg, metrics.gapDeg)
        }
        val spokes = remember(groups, layout) {
            groups.flatMapIndexed { gi, g ->
                g.apps.chunked(metrics.perSpoke).mapIndexed { k, chunk -> Spoke(layout.segments[gi].spokeAngles[k], chunk) }
            }
        }
        val spokeAngles = remember(spokes) { spokes.map { it.angle } }

        val rotation = remember(grouping, anchor, groups.size) {
            Animatable(WheelGeometry.initialRotation(layout, INSET_DEG))
        }
        LaunchedEffect(rotation, layout) {
            val b = WheelGeometry.rotationBounds(layout)
            rotation.updateBounds(b.start, b.endInclusive)
            rotation.snapTo(rotation.value) // re-clamp
        }
        if (interactive) {
            LaunchedEffect(rotation, layout) {
                snapshotFlow { WheelGeometry.focusedSegment(layout, rotation.value) }
                    .distinctUntilChanged()
                    .drop(1)
                    .collect { if (it >= 0) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
            }
        }

        val currentLayout by rememberUpdatedState(layout)
        val currentDismiss by rememberUpdatedState(onDismiss)

        // Band labels, measured once per layout; ellipsised to their segment's arc length.
        val measurer = rememberTextMeasurer()
        val bandLabels: List<TextLayoutResult> = remember(groups, layout, metrics) {
            val style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.6.sp)
            groups.mapIndexed { i, g ->
                val arc = Math.toRadians(layout.segments[i].width.toDouble()).toFloat() * metrics.bandMid
                measurer.measure(
                    text = g.label,
                    style = style,
                    overflow = TextOverflow.Ellipsis,
                    softWrap = false,
                    maxLines = 1,
                    constraints = Constraints(maxWidth = max(1, (arc - metrics.labelPad).toInt())),
                )
            }
        }

        val visibleSpokes by remember(spokeAngles, rotation) {
            derivedStateOf {
                WheelGeometry.visibleRange(
                    spokeAngles,
                    rotation.value + openRotation(progress.value),
                    CULL_MARGIN_DEG,
                )
            }
        }

        val corner = Offset(WheelGeometry.cornerX(anchor, w), WheelGeometry.cornerY(anchor, h))
        val origin = TransformOrigin(if (corner.x > 0f) 1f else 0f, if (corner.y > 0f) 1f else 0f)

        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(anchor, metrics, rotation, interactive) {
                    if (!interactive) {
                        // Closing: swallow touches so nothing underneath fires mid-animation.
                        awaitEachGesture {
                            awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                        }
                        return@pointerInput
                    }
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        scope.launch { rotation.stop() }
                        val tracker = VelocityTracker()
                        tracker.addPosition(down.uptimeMillis, down.position)
                        val bounds = WheelGeometry.rotationBounds(currentLayout)
                        var target = rotation.value
                        var dragging = false
                        var lastAngle = WheelGeometry.screenToPolar(anchor, w, h, down.position.x, down.position.y).angleDeg
                        var up: PointerInputChange? = null
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) {
                                up = change
                                break
                            }
                            tracker.addPosition(change.uptimeMillis, change.position)
                            if (!dragging && (change.position - down.position).getDistance() > viewConfiguration.touchSlop) {
                                dragging = true
                            }
                            if (dragging) {
                                val polar = WheelGeometry.screenToPolar(anchor, w, h, change.position.x, change.position.y)
                                if (polar.radius > metrics.iconPx * 0.5f) {
                                    target = (target + WheelGeometry.normalize180(polar.angleDeg - lastAngle))
                                        .coerceIn(bounds)
                                    val t = target
                                    scope.launch { rotation.snapTo(t) }
                                }
                                lastAngle = polar.angleDeg
                                change.consume()
                            }
                        }
                        val release = up
                        if (dragging && release != null) {
                            val v = tracker.calculateVelocity()
                            val omega = WheelGeometry.angularVelocityDeg(
                                release.position.x - corner.x, release.position.y - corner.y, v.x, v.y,
                            ).coerceIn(-1500f, 1500f)
                            scope.launch {
                                rotation.snapTo(target)
                                if (abs(omega) > 20f) rotation.animateDecay(omega, exponentialDecay(frictionMultiplier = 1.4f))
                            }
                        } else if (!dragging && release != null && !release.isConsumed) {
                            release.consume()
                            val polar = WheelGeometry.screenToPolar(anchor, w, h, release.position.x, release.position.y)
                            when {
                                polar.radius in metrics.bandInner..metrics.bandOuter -> {
                                    val l = currentLayout
                                    val i = WheelGeometry.segmentAt(
                                        l, WheelGeometry.wheelAngleAt(anchor, polar.angleDeg, rotation.value),
                                    )
                                    if (i >= 0) scope.launch {
                                        rotation.animateTo(
                                            WheelGeometry.rotationToCenter(l, i),
                                            spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessLow),
                                        )
                                    }
                                }
                                polar.radius > metrics.outerRadius -> currentDismiss()
                            }
                        }
                    }
                },
        ) {
            // Scrim, hub disc, band.
            Canvas(Modifier.fillMaxSize()) {
                val p = progress.value
                val a = p.coerceIn(0f, 1f)
                val s = openScale(p)
                val rot = rotation.value + openRotation(p)
                drawRect(Color.Black.copy(alpha = 0.55f * a))

                // Faint outer boundary + hub.
                drawCircle(HyleColors.Hairline.copy(alpha = 0.06f * a), radius = metrics.outerRadius * s, center = corner, style = Stroke(1.dp.toPx()))
                val hubR = metrics.hubRadius * s
                drawCircle(
                    brush = Brush.radialGradient(
                        0f to HyleColors.Surface,
                        1f to HyleColors.SurfaceTranslucent,
                        center = corner,
                        radius = hubR.coerceAtLeast(1f),
                    ),
                    radius = hubR,
                    center = corner,
                    alpha = a,
                )
                drawCircle(HyleColors.Hairline, radius = hubR, center = corner, style = Stroke(1.dp.toPx()), alpha = a)

                // Band segments.
                val mid = metrics.bandMid * s
                val thick = metrics.bandThickness * s
                val arcTopLeft = Offset(corner.x - mid, corner.y - mid)
                val arcSize = Size(mid * 2f, mid * 2f)
                val focused = WheelGeometry.focusedSegment(layout, rotation.value)
                val alt = lerp(HyleColors.Surface, HyleColors.Ink, 0.07f)
                layout.segments.forEachIndexed { i, seg ->
                    val rel0 = seg.start + rot
                    val rel1 = seg.end + rot
                    if (rel1 < -CULL_MARGIN_DEG || rel0 > WheelGeometry.QUADRANT + CULL_MARGIN_DEG) return@forEachIndexed
                    val color = when {
                        i == focused -> lerp(HyleColors.Surface, HyleColors.Violet, 0.38f)
                        i % 2 == 0 -> HyleColors.Surface
                        else -> alt
                    }
                    drawArc(
                        color = color,
                        startAngle = qs + rel0,
                        sweepAngle = seg.width,
                        useCenter = false,
                        topLeft = arcTopLeft,
                        size = arcSize,
                        style = Stroke(width = thick, cap = StrokeCap.Butt),
                        alpha = a,
                    )
                    // Hairline divider at the segment's leading edge.
                    val inner = WheelGeometry.polarToScreen(anchor, w, h, mid - thick / 2f, qs + rel0)
                    val outer = WheelGeometry.polarToScreen(anchor, w, h, mid + thick / 2f, qs + rel0)
                    drawLine(HyleColors.Hairline, Offset(inner.x, inner.y), Offset(outer.x, outer.y), 1.dp.toPx(), alpha = a)

                    val label = bandLabels.getOrNull(i) ?: return@forEachIndexed
                    val centerAngle = qs + seg.center + rot
                    val c = WheelGeometry.polarToScreen(anchor, w, h, mid, centerAngle)
                    val pivot = Offset(c.x, c.y)
                    rotate(WheelGeometry.tangentTextRotation(centerAngle), pivot) {
                        drawText(
                            textLayoutResult = label,
                            color = if (i == focused) HyleColors.InkBright else HyleColors.Ink,
                            topLeft = Offset(pivot.x - label.size.width / 2f, pivot.y - label.size.height / 2f),
                            alpha = a,
                        )
                    }
                }
                // Band edges.
                drawCircle(HyleColors.Hairline, radius = metrics.bandOuter * s, center = corner, style = Stroke(1.dp.toPx()), alpha = a)
            }

            // Hub controls: grouping chips along the inside of the quadrant + search.
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val p = progress.value
                        val s = openScale(p)
                        scaleX = s
                        scaleY = s
                        alpha = p.coerceIn(0f, 1f)
                        transformOrigin = origin
                    },
            ) {
                // Labels follow the hub's curve: no enclosing shapes, the active one is lit and underlined.
                val textInk = HyleColors.InkBright
                val dimInk = HyleColors.InkDim
                val accent = HyleColors.Violet
                Canvas(Modifier.fillMaxSize()) {
                    val corner = WheelGeometry.polarToScreen(anchor, w, h, 0f, 0f)
                    val r = metrics.chipRadius
                    val oval = android.graphics.RectF(corner.x - r, corner.y - r, corner.x + r, corner.y + r)
                    WheelGrouping.entries.forEachIndexed { i, g ->
                        val selected = g == grouping
                        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                            textSize = 11.sp.toPx()
                            letterSpacing = 0.06f
                            typeface = if (selected) android.graphics.Typeface.DEFAULT_BOLD else android.graphics.Typeface.DEFAULT
                            color = (if (selected) textInk else dimInk).toArgb()
                        }
                        val textW = paint.measureText(g.label)
                        val sweep = Math.toDegrees((textW / r).toDouble()).toFloat()
                        val start = qs + ChipAngles[i.coerceAtMost(ChipAngles.lastIndex)] - sweep / 2f
                        val path = android.graphics.Path().apply { addArc(oval, start, sweep) }
                        drawContext.canvas.nativeCanvas.drawTextOnPath(g.label, path, 0f, 0f, paint)
                        if (selected) {
                            val ur = r - 4.dp.toPx()
                            drawArc(
                                color = accent,
                                startAngle = start,
                                sweepAngle = sweep,
                                useCenter = false,
                                topLeft = Offset(corner.x - ur, corner.y - ur),
                                size = Size(ur * 2, ur * 2),
                                style = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round),
                            )
                        }
                    }
                }
                // Invisible touch targets over each label.
                WheelGrouping.entries.forEachIndexed { i, g ->
                    val angle = qs + ChipAngles[i.coerceAtMost(ChipAngles.lastIndex)]
                    val pt = WheelGeometry.polarToScreen(anchor, w, h, metrics.chipRadius, angle)
                    Box(
                        Modifier
                            .centeredAt { Offset(pt.x, pt.y) }
                            .size(width = 72.dp, height = 40.dp)
                            .semantics { contentDescription = g.label }
                            .clickable(
                                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                indication = null,
                                role = Role.Tab,
                            ) { if (g != grouping) onGroupingChange(g) },
                    )
                }
                val sp = WheelGeometry.polarToScreen(anchor, w, h, metrics.searchRadius, qs + WheelGeometry.MID)
                SearchButton(onClick = onSearch, modifier = Modifier.centeredAt { Offset(sp.x, sp.y) })
            }

            // Spokes (only those near the visible quadrant are composed).
            val range = visibleSpokes
            for (si in range) {
                val spoke = spokes[si]
                spoke.apps.forEachIndexed { k, app ->
                    key(app.key) {
                        val radius = metrics.firstRadius + k * metrics.radialStep
                        val delay = if (metrics.perSpoke > 1) 0.18f * k / (metrics.perSpoke - 1) else 0f
                        WheelIcon(
                            app = app,
                            size = metrics.iconDp,
                            position = {
                                val p = progress.value
                                val pt = WheelGeometry.polarToScreen(
                                    anchor, w, h, radius * openScale(p),
                                    qs + spoke.angle + rotation.value + openRotation(p),
                                )
                                Offset(pt.x, pt.y)
                            },
                            alpha = { ((progress.value - delay) / (1f - delay)).coerceIn(0f, 1f) },
                            onClick = { onLaunch(app) },
                            onLongClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                onLongPressApp(app)
                            },
                        )
                    }
                }
            }

            if (groups.isEmpty()) {
                val text = when {
                    grouping == WheelGrouping.Recent -> "No recent apps yet"
                    grouping == WheelGrouping.Category && categories == null -> "Sorting…"
                    else -> "No apps"
                }
                val pt = WheelGeometry.polarToScreen(anchor, w, h, metrics.firstRadius, qs + WheelGeometry.MID)
                Text(
                    text = text,
                    color = HyleColors.InkDim,
                    fontSize = 13.sp,
                    modifier = Modifier
                        .centeredAt { Offset(pt.x, pt.y) }
                        .graphicsLayer { alpha = progress.value.coerceIn(0f, 1f) },
                )
            }
        }
    }
}

/** Places the content centred on [position] (read at placement time, so moving it only re-places). */
private fun Modifier.centeredAt(position: () -> Offset): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
    layout(placeable.width, placeable.height) {
        val o = position()
        placeable.place((o.x - placeable.width / 2f).roundToInt(), (o.y - placeable.height / 2f).roundToInt())
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WheelIcon(
    app: AppEntry,
    size: Dp,
    position: () -> Offset,
    alpha: () -> Float,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.88f else 1f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMedium),
        label = "iconPress",
    )
    AppIcon(
        app = app,
        size = size,
        modifier = Modifier
            .centeredAt(position)
            .size(size)
            .graphicsLayer {
                this.alpha = alpha()
                scaleX = scale
                scaleY = scale
            }
            .semantics { contentDescription = app.label }
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onLongClick = onLongClick,
                onClick = onClick,
            ),
    )
}

@Composable
private fun GroupingChip(
    label: String,
    selected: Boolean,
    rotationDeg: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bg by animateColorAsState(if (selected) HyleColors.Violet.copy(alpha = 0.22f) else Color.Transparent, label = "chipBg")
    val edge by animateColorAsState(if (selected) HyleColors.Violet.copy(alpha = 0.75f) else HyleColors.Hairline, label = "chipEdge")
    val ink by animateColorAsState(if (selected) HyleColors.InkBright else HyleColors.InkDim, label = "chipInk")
    val shape = RoundedCornerShape(50)
    Box(
        modifier = modifier
            .graphicsLayer { rotationZ = rotationDeg }
            .clip(shape)
            .background(bg, shape)
            .border(1.dp, edge, shape)
            .clickable(role = Role.Tab, onClick = onClick)
            .padding(horizontal = 7.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = label, color = ink, fontSize = 10.5.sp, letterSpacing = 0.6.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

/** The hub's search glyph (the Concepts eyedropper analogue): a bare magnifier, no enclosing shape. */
@Composable
private fun SearchButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(48.dp)
            .semantics { contentDescription = "Search apps" }
            .clickable(role = Role.Button, onClick = onClick),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 1.8.dp.toPx()
            val r = size.minDimension * 0.24f
            val c = Offset(size.width * 0.44f, size.height * 0.44f)
            drawCircle(HyleColors.InkBright, radius = r, center = c, style = Stroke(stroke))
            val d = r / 1.4142135f
            drawLine(
                HyleColors.InkBright,
                Offset(c.x + d, c.y + d),
                Offset(c.x + d + r * 0.85f, c.y + d + r * 0.85f),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
        }
    }
}
