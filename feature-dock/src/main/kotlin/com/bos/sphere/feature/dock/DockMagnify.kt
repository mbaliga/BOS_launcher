package com.bos.sphere.feature.dock

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.invisibleToUser
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Wave 56 (dockMagnify): how far the icons grow. The ids are the stored spelling; never rename one. Stored in the dock settings next to the style
 * (`dock.magnifyStrength` of the workspace file), so backup and export carry it.
 */
enum class MagnifyStrength(val id: String, val label: String, val factor: Float) {
    Subtle("subtle", "Subtle", 1.3f),
    Medium("medium", "Medium", 1.6f),
    Large("large", "Large", 2.0f);

    companion object {
        fun forId(id: String?): MagnifyStrength? = entries.firstOrNull { it.id == id }
        fun orDefault(id: String?): MagnifyStrength = forId(id) ?: Medium
    }
}

/**
 * Wave 56: the maths of the magnifying dock, pure so a JVM test can check it. All lengths are pixels along the dock's main axis (x for the bottom dock,
 * y for a side dock), in the coordinates of the dock's own viewport; the arrays are sorted by centre, ascending (left to right in physical pixels, so
 * right-to-left layouts need nothing special).
 *
 * The pointer's neighbourhood is a raised-cosine bump `w(d) = 0.5 (1 + cos(pi d / R))` over `R = 2.5` icon pitches. An icon at distance d grows by
 * `1 + (factor - 1) w(d)`, and every icon is pushed away from the pointer by the integral of the extra width between the pointer and it, which is what
 * makes room: the stretch is continuous, never closer than the resting pitch, and the point under the pointer stays under the pointer. The whole row is
 * then shifted back inside [lo, hi] (the room the dock has) when it would leave it.
 */
object DockMagnify {
    const val RANGE_ICONS = 2.5f
    /** How close together the row may be drawn when it lacks room, as a fraction of the resting pitch (the icons are 40 of 60 dp, so 0.72 still leaves them apart). */
    const val MIN_PITCH_FRACTION = 0.72f
    const val SLOT_DP = 52f
    const val ICON_DP = 40f
    const val PITCH_DP = SLOT_DP + 8f
    /** How far a fully magnified icon lifts off the shelf, in dp. */
    const val RISE_DP = 6f
    /** Where the icon's outer edge is inside a slot, as a fraction of the slot from its start: 6 dp margin plus the 40 dp icon. */
    const val OUTER_FRAC = (6f + ICON_DP) / SLOT_DP

    /** The room a dock asks for at each end of the row while it is being touched, so the spread has somewhere to go (dp). */
    fun slackDp(s: MagnifyStrength): Float = (s.factor - 1f) * 100f
    /** The room above (or beside) the icons for the grown ones, in dp. */
    fun headDp(s: MagnifyStrength): Float = (s.factor - 1f) * ICON_DP + RISE_DP

    fun radius(pitch: Float): Float = RANGE_ICONS * pitch

    /** The raised-cosine bump: 1 at the pointer, 0 at and beyond [radius]. */
    fun weight(distance: Float, radius: Float): Float {
        if (abs(distance) >= radius) return 0f
        return 0.5f * (1f + cos(PI.toFloat() * distance / radius))
    }

    /** The integral of [weight] from 0 to [x]: how far the stretch has pushed a point at distance x (odd, saturating at radius/2). */
    fun displacement(x: Float, radius: Float): Float {
        if (x >= radius) return 0.5f * radius
        if (x <= -radius) return -0.5f * radius
        return 0.5f * x + radius / (2f * PI.toFloat()) * sin(PI.toFloat() * x / radius)
    }

    class Result(
        val ids: IntArray, val centers: FloatArray, val halves: FloatArray, val magnifiable: BooleanArray,
        val scale: FloatArray, val offset: FloatArray, val lift: FloatArray,
        /** The row's extent before and after (the slots' outer edges). */
        val baseStart: Float, val baseEnd: Float, val start: Float, val end: Float,
        private val reach: FloatArray,
    ) {
        val size: Int get() = ids.size
        fun indexOfId(id: Int): Int { for (i in ids.indices) if (ids[i] == id) return i; return -1 }
        /** Where member [i]'s centre is drawn. */
        fun center(i: Int): Float = centers[i] + offset[i]

        /**
         * The hit-test mapping: the member under [p] with the magnified geometry, or -1. A member owns the part of the axis within its reach (its half
         * width plus half the gap, grown by its scale); where two reaches meet the nearer centre wins. Members that do not magnify (a widget) count too.
         */
        fun indexAt(p: Float): Int {
            var best = -1; var bestDist = Float.MAX_VALUE
            for (i in ids.indices) {
                val d = abs(p - center(i))
                if (d <= reach[i] && d < bestDist) { best = i; bestDist = d }
            }
            return best
        }
    }

    /**
     * Lays the row out for a pointer at [pointer]. [amount] is the animated 0..1 strength of the effect (0 = at rest: every scale is 1, every offset 0),
     * [factor] the largest scale, [pitch] the resting distance between neighbouring centres, [gapHalf] half the gap between two slots. [lo]..[hi] is the room
     * the row may use; a row that already was outside it at rest (a scrolling dock) is allowed to stay where it is.
     */
    fun compute(
        ids: IntArray, centers: FloatArray, halves: FloatArray, magnifiable: BooleanArray,
        pointer: Float, factor: Float, amount: Float, pitch: Float, gapHalf: Float, lo: Float, hi: Float,
    ): Result {
        val n = ids.size
        val r = radius(pitch)
        val a = (factor - 1f) * amount
        val scale = FloatArray(n); val offset = FloatArray(n); val lift = FloatArray(n); val reach = FloatArray(n)
        var mnB = Float.MAX_VALUE; var mxB = -Float.MAX_VALUE
        for (i in 0 until n) { mnB = min(mnB, centers[i] - halves[i]); mxB = max(mxB, centers[i] + halves[i]) }
        var mn = Float.MAX_VALUE; var mx = -Float.MAX_VALUE
        /** The row for a contraction [lam] about the pointer and a share [k] of the growth. */
        fun place(lam: Float, k: Float) {
            mn = Float.MAX_VALUE; mx = -Float.MAX_VALUE
            for (i in 0 until n) {
                val x = centers[i] - pointer
                val w = if (magnifiable[i]) weight(x, r) else 0f
                scale[i] = 1f + a * k * w
                lift[i] = w * amount * k
                offset[i] = a * k * displacement(x, r) + (lam - 1f) * x
                mn = min(mn, centers[i] + offset[i] - halves[i] * scale[i]); mx = max(mx, centers[i] + offset[i] + halves[i] * scale[i])
            }
        }
        place(1f, 1f)
        // A row that does not fit even at rest (a scrolling dock) just spreads: the viewport clips what leaves it. Otherwise it must stay inside [lo, hi].
        val overflow = mnB < lo - 1f || mxB > hi + 1f
        if (n > 1 && amount != 0f && !overflow) {
            // Each end must stay inside [lo, hi] WITHOUT moving the stretch off the pointer (the icon under it must stay under it): the row is drawn a
            // little closer around the pointer where an end lacks room (never closer than the icons themselves), and grows less where that is not enough.
            fun fits(k: Float): Boolean {
                place(1f, k)
                var lam = 1f
                val needR = mx - hi; val needL = lo - mn
                val spanR = centers[n - 1] - pointer; val spanL = pointer - centers[0]
                if (needR > 0f && spanR > 1f) lam = min(lam, 1f - needR / spanR)
                if (needL > 0f && spanL > 1f) lam = min(lam, 1f - needL / spanL)
                if (lam < 1f) place(max(MIN_PITCH_FRACTION, lam), k)
                return mn >= lo - 0.02f && mx <= hi + 0.02f
            }
            if (!fits(1f)) {
                var good = 0f; var bad = 1f
                repeat(16) { val mid = (good + bad) / 2f; if (fits(mid)) good = mid else bad = mid }
                fits(good)
            }
        }
        for (i in 0 until n) reach[i] = (halves[i] + gapHalf) * scale[i]
        if (n == 0) { mnB = 0f; mxB = 0f; mn = 0f; mx = 0f }
        return Result(ids, centers, halves, magnifiable, scale, offset, lift, mnB, mxB, mn, mx, reach)
    }

    private val none = compute(IntArray(0), FloatArray(0), FloatArray(0), BooleanArray(0), 0f, 1f, 0f, 1f, 0f, 0f, 0f)
    internal fun empty(): Result = none
}

/**
 * Wave 56: the state of one magnifying dock: where the pointer is, how strongly the effect is on, and where each member of the row sits. Everything the
 * icons draw is read from [result] inside `graphicsLayer` blocks, so a pointer move redraws the layers and recomposes nothing.
 */
@Stable
internal class MagnifyField {
    /** The largest scale; 1 means no magnification (the tooltip still follows the pointer). */
    var factor by mutableFloatStateOf(1f)
    /** The animated 0..1 strength; the rail animates it (a spring, or a snap with reduced motion). */
    var amount by mutableFloatStateOf(0f)
    /** The pointer along the main axis, in viewport pixels. It keeps its last value after the pointer left, so the icons spring back where they were. */
    var pointer by mutableFloatStateOf(0f)
    /** A finger is down, or a mouse or stylus hovers, on the dock: the tooltip shows. */
    var pointerDown by mutableStateOf(false)
    /** The finger moved past the touch slop or stayed down: the magnification starts (a plain tap never sees it, so it launches exactly what was pressed). */
    var magnifyTouch by mutableStateOf(false)
    /** The member (id) holding keyboard or D-pad focus, or [NONE]. */
    var focusId by mutableIntStateOf(NONE)
    /** The room the row may use along the axis (viewport pixels). */
    var lo by mutableFloatStateOf(0f)
    var hi by mutableFloatStateOf(0f)
    var horizontal = true
    var edge = DockEdge.Bottom
    var pitch = DockMagnify.PITCH_DP
    var gapHalf = 4f
    var rise = DockMagnify.RISE_DP
    var icon = DockMagnify.ICON_DP
    var viewport: LayoutCoordinates? = null
    /** Wave 58 (CFG-73): a still sample pointer (0..1 along the row) for the Settings Preview: the row is drawn magnified there with no finger; null = off. */
    var demo by mutableStateOf<Float?>(null)

    class Member(val coords: LayoutCoordinates, val center: Float, val half: Float, val crossStart: Float, val crossSize: Float, val magnifiable: Boolean, val outerFrac: Float, val label: String)
    private val members = HashMap<Int, Member>()
    private var version by mutableIntStateOf(0)

    private fun measure(c: LayoutCoordinates, magnifiable: Boolean, outerFrac: Float, label: String): Member? {
        val v = viewport?.takeIf { it.isAttached } ?: return null
        if (!c.isAttached) return null
        val p = v.localPositionOf(c, Offset.Zero)
        val main = if (horizontal) p.x else p.y; val cross = if (horizontal) p.y else p.x
        val len = if (horizontal) c.size.width.toFloat() else c.size.height.toFloat()
        val crossLen = if (horizontal) c.size.height.toFloat() else c.size.width.toFloat()
        return Member(c, main + len / 2f, len / 2f, cross, crossLen, magnifiable, outerFrac, label)
    }

    private fun changed(old: Member?, m: Member) = old == null || abs(old.center - m.center) > 0.5f || abs(old.half - m.half) > 0.5f || abs(old.crossStart - m.crossStart) > 0.5f || old.magnifiable != m.magnifiable || old.label != m.label

    fun report(id: Int, c: LayoutCoordinates, magnifiable: Boolean, outerFrac: Float, label: String) {
        val m = measure(c, magnifiable, outerFrac, label) ?: return
        if (changed(members[id], m)) { members[id] = m; version++ }
    }

    /** The viewport moved or resized (the rail took room for the spread): every member's place in it changed even though none moved on the screen. */
    fun refreshAll() {
        var any = false
        for ((id, old) in members.entries.toList()) {
            val m = measure(old.coords, old.magnifiable, old.outerFrac, old.label) ?: continue
            if (changed(old, m)) { members[id] = m; any = true }
        }
        if (any) version++
    }

    fun remove(id: Int) { if (members.remove(id) != null) version++ }
    fun memberOf(id: Int): Member? = members[id]
    fun label(id: Int): String? = members[id]?.label

    fun scaleOf(id: Int): Float { val r = result.value; val i = r.indexOfId(id); return if (i < 0) 1f else r.scale[i] }
    /** How far the member is lifted off its surface (pixels), for parts that must stay put on the shelf. */
    fun liftPx(id: Int): Float { val r = result.value; val i = r.indexOfId(id); return if (i < 0) 0f else rise * r.lift[i] * density }
    var density = 1f

    val result = derivedStateOf {
        version
        val sorted = members.entries.sortedBy { it.value.center }
        val n = sorted.size
        DockMagnify.compute(
            IntArray(n) { sorted[it].key }, FloatArray(n) { sorted[it].value.center }, FloatArray(n) { sorted[it].value.half }, BooleanArray(n) { sorted[it].value.magnifiable },
            demo?.let { lo + it.coerceIn(0f, 1f) * (hi - lo) } ?: pointer, factor, amount, pitch, gapHalf, lo, hi,
        )
    }

    /** The member the tooltip names: under the pointer while one is down or hovering, else the focused one; [NONE] otherwise. */
    val hoverId = derivedStateOf {
        when {
            pointerDown -> { val r = result.value; val i = r.indexAt(pointer); if (i >= 0) r.ids[i] else NONE }
            focusId != NONE -> focusId
            else -> NONE
        }
    }

    /** Whether the magnification wants to be on: the animation's target. */
    val wantsMagnify get() = factor > 1f && (demo != null || (pointerDown && magnifyTouch) || (focusId != NONE && !pointerDown))
    val engaged get() = pointerDown || focusId != NONE || amount > 0.002f

    /** Where the focused member's centre is (the pointer follows it while no finger or mouse is on the dock). */
    fun focusTarget(): Float? = members[focusId]?.center

    companion object { const val NONE = Int.MIN_VALUE }
}

/** Reads the pointer on the dock without consuming anything, so taps, long presses, drags and the scroll work as they did. Positions are viewport pixels. */
internal suspend fun PointerInputScope.trackMagnify(field: MagnifyField) {
    val slop = viewConfiguration.touchSlop
    val holdMs = 120L
    awaitPointerEventScope {
        var down = Offset.Zero
        var pending = false
        while (true) {
            val ev = if (pending) withTimeoutOrNull(holdMs) { awaitPointerEvent(PointerEventPass.Initial) } else awaitPointerEvent(PointerEventPass.Initial)
            if (ev == null) { field.magnifyTouch = true; pending = false; continue }
            val c = ev.changes.firstOrNull() ?: continue
            val main = if (field.horizontal) c.position.x else c.position.y
            if (c.type != PointerType.Touch) {
                // A mouse, trackpad or stylus: hovering is enough, the effect starts at once. Exit ends it.
                if (ev.type == PointerEventType.Exit) { field.pointerDown = false; field.magnifyTouch = false }
                else { field.pointer = main; field.pointerDown = true; field.magnifyTouch = true }
            } else if (c.pressed) {
                if (!field.pointerDown || ev.type == PointerEventType.Press) { down = c.position; field.pointerDown = true; field.magnifyTouch = false; pending = true }
                field.pointer = main
                if (!field.magnifyTouch && (c.position - down).getDistance() > slop) { field.magnifyTouch = true; pending = false }
            } else { field.pointerDown = false; field.magnifyTouch = false; pending = false }
        }
    }
}

/**
 * One member of the row (an icon slot, the home glyph, the enable button, the divider): reports its place to [field] and takes the scale and the shift the
 * field computes. The scale grows from the icon's outer edge (the shelf for the bottom dock; the screen edge for a side dock, so it grows towards the
 * centre of the screen), and the icon rises a little off its surface. The layer is the outermost one, so touches hit the magnified geometry.
 */
@Composable
internal fun Modifier.magnifyMember(field: MagnifyField?, id: Int, label: String, magnifiable: Boolean = true, outerFrac: Float = DockMagnify.OUTER_FRAC): Modifier {
    if (field == null) return this
    DisposableEffect(field, id) { onDispose { field.remove(id) } }
    val lbl = label
    return this
        .onGloballyPositioned { c -> field.report(id, c, magnifiable, outerFrac, lbl) }
        .graphicsLayer {
            val r = field.result.value
            val i = r.indexOfId(id)
            if (i < 0) return@graphicsLayer
            val s = r.scale[i]
            val h = field.horizontal
            val dir = if (field.edge == DockEdge.Left) 1f else -1f // which way "up" (away from the screen edge) points on the cross axis
            val frac = when (field.edge) { DockEdge.Left -> 1f - outerFrac; else -> outerFrac }
            scaleX = s; scaleY = s
            transformOrigin = if (h) TransformOrigin(0.5f, frac) else TransformOrigin(frac, 0.5f)
            val lift = field.rise * r.lift[i] * density
            if (h) { translationX = r.offset[i]; translationY = -lift } else { translationY = r.offset[i]; translationX = dir * lift }
        }
}

/** The geometry of the tooltip's bubble in rail pixels: its left and top, and where its pointer points. */
class TooltipGeo(val left: Float, val top: Float, val tipX: Float, val tipY: Float)

internal fun tooltipGeo(field: MagnifyField, id: Int, w: Float, h: Float, density: Float, railX: Float, railY: Float, winW: Float, winH: Float): TooltipGeo? {
    val r = field.result.value
    val i = r.indexOfId(id)
    val m = field.memberOf(id) ?: return null
    val s = if (i >= 0) r.scale[i] else 1f
    val cm = if (i >= 0) r.center(i) else m.center
    val lift = if (i >= 0) r.lift[i] else 0f
    val extent = field.icon * density * s + field.rise * density * lift
    val pivot = if (field.edge == DockEdge.Left) m.crossStart + m.crossSize * (1f - m.outerFrac) else m.crossStart + m.crossSize * m.outerFrac
    return DockTooltip.place(field.edge, cm, pivot, extent, w, h, density, railX, railY, winW, winH)
}

/** Wave 56: where the bubble goes, pure. [centre] is the icon's centre along the dock, [pivot] the cross coordinate of its foot, [extent] how far it reaches from the foot. */
object DockTooltip {
    internal fun place(edge: DockEdge, centre: Float, pivot: Float, extent: Float, w: Float, h: Float, density: Float, railX: Float, railY: Float, winW: Float, winH: Float): TooltipGeo {
        val gap = 3f * density; val ptr = 6f * density; val margin = 6f * density
        return when (edge) {
            DockEdge.Bottom -> {
                val tipY = pivot - extent - gap
                val left = (centre - w / 2f).coerceIn(-railX + margin, max(-railX + margin, winW - railX - margin - w))
                TooltipGeo(left, tipY - ptr - h, centre, tipY)
            }
            DockEdge.Left -> {
                val tipX = pivot + extent + gap
                val top = (centre - h / 2f).coerceIn(-railY + margin, max(-railY + margin, winH - railY - margin - h))
                TooltipGeo(tipX + ptr, top, tipX, centre)
            }
            DockEdge.Right -> {
                val tipX = pivot - extent - gap
                val top = (centre - h / 2f).coerceIn(-railY + margin, max(-railY + margin, winH - railY - margin - h))
                TooltipGeo(tipX - ptr - w, top, tipX, centre)
            }
        }
    }
}

object DockMagnifyTags {
    const val TOOLTIP = "dock.tooltip"
}

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
/** The bubble with the hovered icon's name (the 'Finder' bubble of the classic Mac dock): dark, rounded, a small pointer towards the icon. */
@Composable
internal fun BoxScope.DockTooltipLayer(field: MagnifyField, railX: () -> Float, railY: () -> Float) {
    val id by field.hoverId
    val label = if (id == MagnifyField.NONE) null else field.label(id)
    if (label.isNullOrEmpty()) return
    val density = LocalDensity.current.density
    val view = LocalView.current
    val edge = field.edge
    // The layer fills the rail and places the bubble with a graphics layer, so it follows the icon without a relayout or a recomposition.
    Layout(
        content = {
            Text(
                label, color = DockTooltipTokens.TEXT.c(), fontSize = 13.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium, maxLines = 1,
                modifier = Modifier.testTag(DockMagnifyTags.TOOLTIP).semantics { invisibleToUser() } // the icon already says its name to a screen reader
                    .drawBehind {
                        val g = tooltipGeo(field, id, size.width, size.height, density, railX(), railY(), view.width.toFloat(), view.height.toFloat()) ?: return@drawBehind
                        val r = 9.dp.toPx()
                        val body = Path().apply { addRoundRect(androidx.compose.ui.geometry.RoundRect(0f, 0f, size.width, size.height, androidx.compose.ui.geometry.CornerRadius(r))) }
                        val ptr = 6.dp.toPx(); val half = 6.dp.toPx()
                        val tip = Offset(g.tipX - g.left, g.tipY - g.top)
                        val pointer = Path().apply {
                            when (edge) {
                                DockEdge.Bottom -> { val x = tip.x.coerceIn(r, size.width - r); moveTo(x - half, size.height - 0.5f); lineTo(x, size.height + ptr); lineTo(x + half, size.height - 0.5f) }
                                DockEdge.Left -> { val y = tip.y.coerceIn(r, size.height - r); moveTo(0.5f, y - half); lineTo(-ptr, y); lineTo(0.5f, y + half) }
                                DockEdge.Right -> { val y = tip.y.coerceIn(r, size.height - r); moveTo(size.width - 0.5f, y - half); lineTo(size.width + ptr, y); lineTo(size.width - 0.5f, y + half) }
                            }
                            close()
                        }
                        drawPath(body, DockTooltipTokens.FILL.c()); drawPath(pointer, DockTooltipTokens.FILL.c())
                        drawPath(body, DockTooltipTokens.EDGE.c(), style = androidx.compose.ui.graphics.drawscope.Stroke(1f))
                    }
                    .padding(horizontal = 11.dp, vertical = 6.dp),
            )
        },
        modifier = Modifier.matchParentSize(),
    ) { measurables, c ->
        val p = measurables.first().measure(Constraints())
        layout(c.maxWidth, c.maxHeight) {
            p.placeWithLayer(0, 0) {
                val g = tooltipGeo(field, id, p.width.toFloat(), p.height.toFloat(), density, railX(), railY(), view.width.toFloat(), view.height.toFloat())
                if (g != null) { translationX = g.left; translationY = g.top } else alpha = 0f
            }
        }
    }
}

internal object DockTooltipTokens {
    const val FILL = 0xF0262A33.toInt()
    const val EDGE = 0x33FFFFFF
    const val TEXT = 0xFFFFFFFF.toInt()
}

private fun Int.c() = Color(this)
