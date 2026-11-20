package com.bos.sphere.feature.grid

import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.PointerInputModifierNode
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.unit.IntSize
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Wave 44 (ringLabels): the words on the wheel's label band (the group names A-Z, Category, Recent and the section labels: letters, #, category
 * names) are set ON THE ARC, each glyph on the circle and turned tangent to it, instead of one straight run rotated about its middle. This file
 * holds the pure geometry (testable on the JVM) and the cached drawing object; the wheel only calls [ArcLabel.draw].
 *
 * Direction rule (never upside down): screen angles run clockwise (y down). A run set clockwise has its up side pointing OUT of the circle and is
 * upright wherever the label is on the upper half of the circle; on the lower half ([flipped]) the run goes counter-clockwise, up points IN, and
 * the baseline moves to the other side of the ink so the ink stays in the same band. Both corners use the same rule, so the text reads left to
 * right on either.
 */
internal object RingLabelGeometry {
    /** True when a label centred on screen angle [angleDeg] is set counter-clockwise (it lies on the lower half of the circle). */
    fun flipped(angleDeg: Float): Boolean = sin(Math.toRadians(angleDeg.toDouble())) > 1e-4

    /** Rotation (deg, clockwise) of a glyph's baseline whose centre is at screen angle [angleDeg]: tangent to the circle, never past +-90. */
    fun glyphRotation(angleDeg: Float, flipped: Boolean = flipped(angleDeg)): Float =
        WheelGeometry.normalize180(angleDeg + if (flipped) -90f else 90f)

    /** Degrees of arc that [lengthPx] covers at [radiusPx]. */
    fun degOf(lengthPx: Float, radiusPx: Float): Float = if (radiusPx <= 0f) 0f else Math.toDegrees((lengthPx / radiusPx).toDouble()).toFloat()

    /**
     * Screen angle of the centre of every glyph of a run whose advances are [advances] (px), whose middle sits at [centreDeg], set on a circle of
     * [radiusPx] (the baseline radius). A normal run advances with growing angle, a flipped one with falling angle.
     */
    fun glyphAngles(centreDeg: Float, advances: List<Float>, radiusPx: Float, flipped: Boolean): List<Float> {
        val total = advances.sum()
        var x = 0f
        return advances.map { a ->
            val off = degOf(x + a / 2f - total / 2f, radiusPx)
            x += a
            centreDeg + if (flipped) -off else off
        }
    }

    /** Radius of the baseline for ink centred on [inkCentreRadius] and [inkHeight] tall: ink grows outward from a normal baseline, inward from a flipped one. */
    fun baselineRadius(inkCentreRadius: Float, inkHeight: Float, flipped: Boolean): Float =
        if (flipped) inkCentreRadius + inkHeight / 2f else inkCentreRadius - inkHeight / 2f

    /** The whole slot of one label: the angles it spans (screen degrees, [startDeg] < [endDeg]) and its radial extent. */
    data class Slot(val startDeg: Float, val endDeg: Float, val innerPx: Float, val outerPx: Float) {
        val centreDeg: Float get() = (startDeg + endDeg) / 2f
        val spanDeg: Float get() = endDeg - startDeg
        fun contains(radiusPx: Float, angleDeg: Float): Boolean {
            if (radiusPx < innerPx || radiusPx > outerPx) return false
            val rel = WheelGeometry.normalize180(angleDeg - centreDeg)
            return abs(rel) <= spanDeg / 2f
        }
    }

    /**
     * The hit slots of labels centred on [centresDeg] (ascending angles) whose text covers [textSpansDeg]: the text span plus [marginDeg] a side,
     * at least [minArcPx] along the arc at [midRadiusPx] where the neighbours allow, never past the midpoint to a neighbour, so slots never overlap.
     * Radially the slot runs from [innerPx] to [outerPx].
     */
    fun slots(
        centresDeg: List<Float>, textSpansDeg: List<Float>, midRadiusPx: Float, innerPx: Float, outerPx: Float, minArcPx: Float, marginDeg: Float,
    ): List<Slot> = centresDeg.mapIndexed { i, c ->
        val want = max(textSpansDeg[i] / 2f + marginDeg, degOf(minArcPx, midRadiusPx) / 2f)
        val before = if (i > 0) (c - centresDeg[i - 1]) / 2f else Float.MAX_VALUE
        val after = if (i < centresDeg.lastIndex) (centresDeg[i + 1] - c) / 2f else Float.MAX_VALUE
        Slot(c - min(want, before), c + min(want, after), innerPx, outerPx)
    }

    /** Index of the slot a touch at [radiusPx], [angleDeg] lies in, or -1. */
    fun hit(slots: List<Slot>, radiusPx: Float, angleDeg: Float): Int = slots.indexOfFirst { it.contains(radiusPx, angleDeg) }
}

/** The cached paints of one kind of ring label (a size and a letter spacing). Created once per density, reused every frame. */
internal class ArcPaints(textSizePx: Float, letterSpacingEm: Float, val underlineWidthPx: Float) {
    val regular = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        textSize = textSizePx; letterSpacing = letterSpacingEm; typeface = android.graphics.Typeface.DEFAULT
    }
    val bold = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        textSize = textSizePx; letterSpacing = letterSpacingEm; typeface = android.graphics.Typeface.DEFAULT_BOLD
    }
    val underline = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        style = android.graphics.Paint.Style.STROKE; strokeWidth = underlineWidthPx; strokeCap = android.graphics.Paint.Cap.ROUND
    }
    /** Height of capital ink above the baseline. */
    val inkHeight: Float get() = regular.textSize * 0.72f
    /** Text that fits in [maxPx] (the widest weight), ellipsised with an ellipsis when it does not. */
    fun fit(text: String, maxPx: Float): String {
        if (bold.measureText(text) <= maxPx) return text
        val ell = "…"
        val room = maxPx - bold.measureText(ell)
        if (room <= 0f) return ell
        val n = bold.breakText(text, true, room, null)
        return if (n <= 0) ell else text.substring(0, n).trimEnd() + ell
    }
}

/**
 * One label set on the arc whose ink is centred on [inkRadiusPx] from the wheel's centre. The two arc paths (clockwise for the upper half, counter-
 * clockwise for the lower) and the underline arcs are built once, centred on angle 0 about the origin; [draw] only turns the canvas to the label's
 * angle, so drawing allocates nothing. `Canvas.drawTextOnPath` shapes the whole string, so Arabic, Hebrew and other joined scripts keep their forms.
 */
internal class ArcLabel(val text: String, private val paints: ArcPaints, val inkRadiusPx: Float) {
    val widthPx: Float = paints.bold.measureText(text)
    private val padPx = 2f
    private val ink = paints.inkHeight
    private val rNormal = RingLabelGeometry.baselineRadius(inkRadiusPx, ink, false)
    private val rFlipped = RingLabelGeometry.baselineRadius(inkRadiusPx, ink, true)
    private val gap = max(2.5f, paints.underlineWidthPx * 1.5f)
    /** Angular span of the text on the circle it is set on (the baseline radius of the normal run). */
    val spanDeg: Float = RingLabelGeometry.degOf(widthPx, rNormal)

    private val textCw = arc(rNormal, RingLabelGeometry.degOf(widthPx + 2 * padPx, rNormal), false)
    private val textCcw = arc(rFlipped, RingLabelGeometry.degOf(widthPx + 2 * padPx, rFlipped), true)
    private val underCw = arc(rNormal - gap, RingLabelGeometry.degOf(widthPx, rNormal - gap), false)
    private val underCcw = arc(rFlipped + gap, RingLabelGeometry.degOf(widthPx, rFlipped + gap), false)

    private fun arc(r: Float, sweep: Float, flip: Boolean) = android.graphics.Path().apply {
        val oval = android.graphics.RectF(-r, -r, r, r)
        if (flip) addArc(oval, sweep / 2f, -sweep) else addArc(oval, -sweep / 2f, sweep)
    }

    /**
     * Draws the label centred on screen angle [angleDeg] about ([cx], [cy]) at [scale] (the unfurl). [textColor] and [underlineColor] are ARGB;
     * [bold] picks the heavy weight, [underlined] adds the curved underline.
     */
    fun draw(canvas: android.graphics.Canvas, cx: Float, cy: Float, angleDeg: Float, scale: Float, textColor: Int, bold: Boolean, underlined: Boolean, underlineColor: Int) {
        val flip = RingLabelGeometry.flipped(angleDeg)
        canvas.save()
        canvas.translate(cx, cy)
        canvas.rotate(angleDeg)
        if (scale != 1f) canvas.scale(scale, scale)
        val p = if (bold) paints.bold else paints.regular
        p.color = textColor
        canvas.drawTextOnPath(text, if (flip) textCcw else textCw, padPx, 0f, p)
        if (underlined) {
            paints.underline.color = underlineColor
            canvas.drawPath(if (flip) underCcw else underCw, paints.underline)
        }
        canvas.restore()
    }
}

/**
 * A tap on a ring label's own slot (a curved patch, see [RingLabelGeometry.Slot]) in a layer that covers the wheel yet shares every touch with the
 * siblings below it: the node answers only when the touch goes down inside a slot and comes up inside the same one, and ignores all else (so the
 * shortcut ring and the spokes keep their touches). [slotAt] maps a position (px, in the node's space) to a slot index or -1.
 */
internal fun Modifier.ringLabelTap(slotAt: (Offset) -> Int, onTap: (Int) -> Unit): Modifier = this then RingLabelTapElement(slotAt, onTap)

private class RingLabelTapElement(val slotAt: (Offset) -> Int, val onTap: (Int) -> Unit) : ModifierNodeElement<RingLabelTapNode>() {
    override fun create() = RingLabelTapNode(slotAt, onTap)
    override fun update(node: RingLabelTapNode) { node.slotAt = slotAt; node.onTap = onTap }
    override fun equals(other: Any?) = other is RingLabelTapElement && other.slotAt === slotAt && other.onTap === onTap
    override fun hashCode() = 31 * System.identityHashCode(slotAt) + System.identityHashCode(onTap)
}

private class RingLabelTapNode(var slotAt: (Offset) -> Int, var onTap: (Int) -> Unit) : Modifier.Node(), PointerInputModifierNode, CompositionLocalConsumerModifierNode {
    private var downId: PointerId? = null
    private var downSlot = -1
    private var downPos = Offset.Zero

    override fun sharePointerInputWithSiblings(): Boolean = true
    override fun onCancelPointerInput() { downId = null; downSlot = -1 }

    override fun onPointerEvent(pointerEvent: PointerEvent, pass: PointerEventPass, bounds: IntSize) {
        if (pass != PointerEventPass.Main) return
        for (c in pointerEvent.changes) {
            if (downId == null && c.changedToDown() && !c.isConsumed) {
                val s = slotAt(c.position)
                if (s >= 0) { downId = c.id; downSlot = s; downPos = c.position }
            } else if (c.id == downId) {
                val slop = currentValueOf(LocalViewConfiguration).touchSlop
                if (c.changedToUp()) {
                    val ok = !c.isConsumed && (c.position - downPos).getDistance() <= slop * 2f && slotAt(c.position) == downSlot
                    val s = downSlot
                    downId = null; downSlot = -1
                    if (ok) { c.consume(); onTap(s) }
                } else if (!c.pressed || (c.position - downPos).getDistance() > slop * 2f) {
                    downId = null; downSlot = -1
                }
            }
        }
    }
}
