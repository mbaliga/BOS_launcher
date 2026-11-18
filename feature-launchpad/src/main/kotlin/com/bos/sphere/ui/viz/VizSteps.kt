package com.bos.sphere.ui.viz

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** One column of [VizSteps]: its name below ([label]), its [value], the text shown above it when selected, and what a screen reader says. */
data class VizStepColumn(
    val label: String,
    val value: Double,
    val valueText: String,
    val description: String,
    /** Today (or any mark that earns the accent). */
    val accent: Boolean = false,
    val texture: VizTexture = VizTexture.Solid,
)

/**
 * `steps`: columns from one baseline, thin rounded tops, the name under each column and the figure above the selected one only (the
 * toolkit writes a figure over every column; "no number on every point" is the toolkit's own rule for dense charts, and a week of
 * seven is selected, not read). The selected column is full ink (the accent for today), the others are the same ink at half strength.
 * Each column is a [cell] wide tap target (48 dp at least; the chart may reach into its side margins to give seven of them room).
 */
@Composable
fun VizSteps(
    columns: List<VizStepColumn>,
    selected: Int,
    onSelect: (Int) -> Unit,
    tone: VizTone,
    modifier: Modifier = Modifier,
    height: Dp = STEPS_HEIGHT,
    minCell: Dp = 48.dp,
    tagPrefix: String = "viz.steps",
) {
    val n = columns.size.coerceAtLeast(1)
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current.density
    // Large text: the figure above and the names below are sp, so their rows grow with the font scale (up to 2x) and the plot keeps its height.
    val fs = LocalDensity.current.fontScale.coerceIn(1f, 2f)
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val full = height + (VizStepsGeometry.HEAD + VizStepsGeometry.FOOT).dp * (fs - 1f)
    BoxWithConstraints(modifier.fillMaxWidth().height(full)) {
        val cell = maxOf(minCell, maxWidth / n)
        Box(Modifier.requiredWidth(cell * n).height(full)) {
            Canvas(Modifier.fillMaxWidth().fillMaxHeight()) {
                val g = VizStepsGeometry.of(size.width / this.density, size.height / this.density, n, fs)
                val max = columns.maxOfOrNull { VizFormat.safe(it.value) } ?: 0.0
                columns.forEachIndexed { i, c ->
                    // Right to left the week runs from the right edge, as the tap targets below do.
                    val left = g.left(if (rtl) n - 1 - i else i) * this.density; val w = g.barW * this.density
                    val bh = VizFormat.barHeight(c.value, max, g.plot) * this.density
                    val base = g.baseline * this.density
                    val sel = i == selected
                    val inkOf = if (c.accent) tone.accent else tone.ink
                    val alpha = if (sel) 1f else if (c.accent) 0.75f else 0.5f
                    if (bh > 0f) {
                        val r = minOf(VizStepsGeometry.RADIUS * this.density, w / 2, bh)
                        val p = Path().apply { addRoundRect(RoundRect(left, base - bh, left + w, base, topLeftCornerRadius = CornerRadius(r), topRightCornerRadius = CornerRadius(r))) }
                        val brush = VizTextures.brush(c.texture, inkOf, density = this.density, fadeTop = base - bh, fadeBottom = base)
                        drawPath(p, brush, alpha = alpha)
                    }
                    // The name below the baseline; the selected one in full ink, the others at the toolkit's .72.
                    val name = measurer.measure(c.label, TextStyle(fontSize = 11.sp, color = tone.ink.copy(alpha = if (sel) 1f else 0.72f), fontWeight = if (sel) FontWeight.Medium else FontWeight.Normal), maxLines = 1)
                    drawText(name, topLeft = Offset(left + (w - name.size.width) / 2f, base + 3f * this.density))
                    if (sel) {
                        val num = measurer.measure(c.valueText, TextStyle(fontSize = 15.sp, color = tone.ink, fontWeight = FontWeight.Light), maxLines = 1)
                        val x = (left + (w - num.size.width) / 2f).coerceIn(0f, (size.width - num.size.width).coerceAtLeast(0f))
                        drawText(num, topLeft = Offset(x, (base - bh - num.size.height - 2f * this.density).coerceAtLeast(0f)))
                    }
                }
            }
            Row(Modifier.fillMaxWidth().fillMaxHeight()) {
                columns.forEachIndexed { i, c ->
                    Box(
                        Modifier.width(cell).fillMaxHeight()
                            .selectable(selected = i == selected, role = Role.RadioButton, onClick = { onSelect(i) })
                            .semantics { contentDescription = c.description }
                            .testTag("$tagPrefix.col.$i"),
                    )
                }
            }
        }
    }
}

val STEPS_HEIGHT: Dp = 54.dp

/** The steps geometry in dp, pure so tests can read it: seven cells, the bars centred in them, one baseline. */
class VizStepsGeometry private constructor(val width: Float, val height: Float, val n: Int, val fontScale: Float = 1f) {
    /** Space above the tallest column for the selected column's figure, and below the baseline for the names. */
    val head = HEAD * fontScale
    val foot = FOOT * fontScale
    val plot: Float get() = (height - head - foot).coerceAtLeast(0f)
    val baseline: Float get() = head + plot
    val cell: Float get() = if (n <= 0) 0f else width / n
    val gap: Float get() = maxOf(MIN_GAP, cell * 0.18f).coerceAtMost(cell / 2)
    val barW: Float get() = (cell - gap).coerceAtLeast(1f)
    fun left(i: Int): Float = i * cell + gap / 2

    companion object {
        const val HEAD = 17f
        const val FOOT = 14f
        const val RADIUS = 9f
        const val MIN_GAP = 6f
        fun of(width: Float, height: Float, n: Int, fontScale: Float = 1f) = VizStepsGeometry(if (width.isFinite()) width.coerceAtLeast(0f) else 0f, if (height.isFinite()) height.coerceAtLeast(0f) else 0f, n, if (fontScale.isFinite()) fontScale.coerceIn(1f, 2f) else 1f)
    }
}
