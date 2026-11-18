package com.bos.sphere.feature.grid

import androidx.compose.ui.draw.drawBehind
import android.content.Context
import android.content.pm.ApplicationInfo
import com.bos.sphere.core.design.predictiveBackLayer
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.traversalIndex
import com.bos.sphere.core.input.FocusKind
import com.bos.sphere.core.input.PuckEffect
import com.bos.sphere.core.input.PuckKey
import com.bos.sphere.core.input.PuckKeys
import com.bos.sphere.core.input.PuckLayout
import com.bos.sphere.core.input.PuckMode
import com.bos.sphere.core.input.PuckState
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

/** The few angle helpers the arc drawer shares with its tests; the polar layout of the arc itself is [ArcMath]. */
internal object WheelGeometry {
    /** [0, 360). */
    fun normalize360(deg: Float): Float = ((deg % 360f) + 360f) % 360f

    /** [-180, 180). */
    fun normalize180(deg: Float): Float = normalize360(deg + 180f) - 180f

    /** Angular velocity (deg/s, clockwise positive) of a point at ([rx], [ry]) from the centre moving at ([vx], [vy]). */
    fun angularVelocityDeg(rx: Float, ry: Float, vx: Float, vy: Float): Float {
        val r2 = rx * rx + ry * ry
        if (r2 < 1e-3f) return 0f
        return Math.toDegrees(((rx * vy - ry * vx) / r2).toDouble()).toFloat()
    }
}

// =============================================================================================
// Grouping
// =============================================================================================

internal data class WheelGroup(val label: String, val apps: List<AppEntry>, val glyph: androidx.compose.ui.graphics.vector.ImageVector? = null)

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

/**
 * Wave 23 second review: the words drawn on a category or Recent band. The grouping keys stay English ([WheelCategoryOrder]); only the
 * shown label is in the app language (the bands read "Games", "Social" on every phone before).
 */
internal fun wheelGroupLabel(name: String): String = com.bos.sphere.core.design.L10n.tr(name)

/** Display order of category spokes. */
internal val WheelCategoryOrder = listOf(
    "Games", "Audio", "Video", "Images", "Social", "News", "Maps", "Productivity", "Accessibility", "Other",
)

/** The icon on a category's wedge. */
internal fun wheelCategoryGlyph(name: String): androidx.compose.ui.graphics.vector.ImageVector = when (name) {
    "Games" -> Glyphs.Bolt
    "Audio" -> Glyphs.Music
    "Video" -> Glyphs.Camera
    "Images" -> Glyphs.Image
    "Social" -> Glyphs.Message
    "News" -> Glyphs.Globe
    "Maps" -> Glyphs.Map
    "Productivity" -> Glyphs.Calendar
    "Accessibility" -> Glyphs.Person
    else -> Glyphs.Apps
}

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

    /** Category name for [packageName] from the cache ("Other" until it has been resolved). */
    fun nameOf(packageName: String): String =
        wheelCategoryName(cache[packageName] ?: ApplicationInfo.CATEGORY_UNDEFINED)

    /** Resolves and caches categories for [packages] off the main thread (so search can use them). */
    suspend fun warm(context: Context, packages: Collection<String>) { resolve(context, packages) }

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

internal fun buildWheelGroups(
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
        WheelCategoryOrder.mapNotNull { name -> byName[name]?.let { WheelGroup(wheelGroupLabel(name), it.sortedWith(ByLabel), wheelCategoryGlyph(name)) } }
    }

    WheelGrouping.Recent -> {
        val byKey = apps.associateBy { it.key }
        val recent = recentKeys.asSequence().distinct().mapNotNull { byKey[it] }.take(MAX_RECENT).toList()
        if (recent.isEmpty()) emptyList() else listOf(WheelGroup(wheelGroupLabel("Recent"), recent, Glyphs.Clock))
    }
}

// =============================================================================================
// Composable
// =============================================================================================

/**
 * The "All apps" drawer as a corner arc (W26): first a quarter-ring SWITCHER of section wedges round a hub (looped, so the first
 * section is shown again after the last; [switcherStyle] decides icon only or icon with a count), and, when it expands, a Concepts
 * colour-picker style fan of rings of rotated app cells, one ring per section, all in [ArcDrawerContent] and [ArcMath].
 * Tapping the scrim outside collapses, then dismisses ([onDismiss]); Back does the same. Category grouping reads
 * `ApplicationInfo.category` off the main thread, cached in [WheelCategoryCache].
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
    /** More buttons for the hub, like the tool puck in Concepts. */
    actions: List<PuckAction> = emptyList(),
    switcherStyle: com.bos.sphere.core.data.SwitcherStyle = com.bos.sphere.core.data.SwitcherStyle.Expanded,
    /** Start with the fan open (tests and screenshots). */
    startExpanded: Boolean = false,
) {
    // NFR-14: with animations off the drawer does not unfold; it cross-fades in place.
    val motion = com.bos.sphere.core.design.rememberMotionPrefs()
    val progress = animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = if (motion.reduced) androidx.compose.animation.core.tween(com.bos.sphere.core.quality.MorphPolicy.progressMs(motion, 120))
        else spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow),
        label = "wheelOpen",
    )
    val backProgress = com.bos.sphere.core.design.rememberPredictiveBackProgress(visible, onDismiss)
    var expanded by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(startExpanded) }
    // Closing resets to the switcher, so the next open starts there.
    LaunchedEffect(visible) { if (!visible) expanded = false }
    // Back collapses the fan first; the handler registered last wins over the predictive one above while the fan is open.
    androidx.activity.compose.BackHandler(enabled = visible && expanded) { expanded = false }
    val visibleNow by rememberUpdatedState(visible)
    val shown by remember { derivedStateOf { visibleNow || progress.value > 0.002f } }
    if (!shown) return
    val geometry = remember(motion) { derivedStateOf { com.bos.sphere.core.quality.MorphPolicy.geometry(motion, progress.value, visibleNow) } }

    // The arc is physical geometry around a screen corner: lay it out LTR so RTL locales mirror nothing here; the corner
    // the user chose decides the side (the left-handed corner is the mirror image).
    androidx.compose.runtime.CompositionLocalProvider(
        androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Ltr,
    ) {
        ArcDrawerContent(
            anchor = anchor,
            apps = apps,
            grouping = grouping,
            onGroupingChange = onGroupingChange,
            recentKeys = recentKeys,
            onLaunch = onLaunch,
            onLongPressApp = onLongPressApp,
            onSearch = onSearch,
            actions = actions,
            onDismiss = onDismiss,
            interactive = visible,
            expanded = expanded,
            onExpandedChange = { expanded = it },
            switcherStyle = switcherStyle,
            progress = if (motion.reduced) geometry else progress,
            modifier = (if (motion.reduced) modifier.graphicsLayer { alpha = com.bos.sphere.core.quality.MorphPolicy.alpha(motion, progress.value) } else modifier).predictiveBackLayer(backProgress),
        )
    }
}

/** Places the content centred on [position] (read at placement time, so moving it only re-places). */
internal fun Modifier.centeredAt(position: () -> Offset): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
    layout(placeable.width, placeable.height) {
        val o = position()
        placeable.place((o.x - placeable.width / 2f).roundToInt(), (o.y - placeable.height / 2f).roundToInt())
    }
}

/** The hub's search glyph (the Concepts eyedropper analogue): a bare magnifier, no enclosing shape. */
@Composable
internal fun SearchButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(48.dp)
            .semantics { contentDescription = com.bos.sphere.core.design.L10n.tr("Search apps") }
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

/** One extra button on the all-apps puck. */
class PuckAction(val label: String, val glyph: androidx.compose.ui.graphics.vector.ImageVector, val onClick: () -> Unit)

/** A tactile "crater" button: a dark concave dish with a lit lower lip (the Hyle kit's crater), optionally ringed in the accent. */
@Composable
internal fun Crater(size: androidx.compose.ui.unit.Dp, modifier: Modifier = Modifier, accent: Boolean = false, content: @Composable () -> Unit) {
    Box(
        modifier
            .size(size)
            .drawBehind {
                val r = this.size.minDimension / 2f
                drawCircle(Brush.radialGradient(listOf(Color(0xFF06060A), Color(0xFF14141C)), center = Offset(this.size.width / 2f, this.size.height * 0.42f), radius = r), radius = r)
                drawCircle(
                    Brush.verticalGradient(listOf(Color(0x66000000), Color.Transparent, Color(0x40FFFFFF))),
                    radius = r - 0.5.dp.toPx(), style = Stroke(1.5.dp.toPx()),
                )
                if (accent) drawCircle(HyleColors.Violet.copy(alpha = 0.55f), radius = r, style = Stroke(1.5.dp.toPx()))
            },
        contentAlignment = Alignment.Center,
    ) { content() }
}
