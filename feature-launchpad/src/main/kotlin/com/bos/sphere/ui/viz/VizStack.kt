package com.bos.sphere.ui.viz

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** One part of a [VizStack]: [weight] (any positive measure), its texture, the label printed under it where it fits, and the spoken description. */
data class VizPart(val weight: Double, val texture: VizTexture, val label: String, val description: String, val key: String = "")

/**
 * `stack`: one pill split into parts by texture (2 px gaps, the toolkit's 3 px part corners inside a fully rounded pill), the label under
 * a part only where it fits (`est(label) <= partWidth - 4`). Every part is its own focus and accessibility node (the toolkit gives each mark
 * `tabindex=0 role=img aria-label="name: value"`).
 */
@Composable
fun VizStack(
    parts: List<VizPart>,
    tone: VizTone,
    modifier: Modifier = Modifier,
    thickness: Dp = STACK_THICKNESS,
    tagPrefix: String = "viz.stack",
) {
    val measurer = rememberTextMeasurer()
    val shown = parts.filter { VizFormat.safe(it.weight) > 0.0 }
    val total = shown.sumOf { VizFormat.safe(it.weight) }
    Box(modifier.fillMaxWidth().height(thickness + LABEL_ZONE)) {
        Canvas(Modifier.fillMaxWidth().fillMaxHeight()) {
            val d = this.density
            val th = thickness.toPx()
            val widths = VizStackGeometry.widths(shown.map { it.weight }, size.width / d)
            val pill = Path().apply { addRoundRect(androidx.compose.ui.geometry.RoundRect(0f, 0f, size.width, th, CornerRadius(th / 2))) }
            clipPath(pill) {
                var x = 0f
                shown.forEachIndexed { i, p ->
                    val w = widths[i] * d
                    drawRoundRect(VizTextures.brush(p.texture, tone.ink, scale = TEXTURE_SCALE, density = d, fadeTop = 0f, fadeBottom = th), Offset(x, 0f), Size(w, th), CornerRadius(3f * d))
                    x += w + VizStackGeometry.GAP * d
                }
            }
            var x = 0f
            shown.forEachIndexed { i, p ->
                val w = widths[i] * d
                if (p.label.isNotEmpty()) {
                    val t = measurer.measure(p.label, TextStyle(fontSize = 11.sp, color = tone.ink.copy(alpha = 0.72f)), maxLines = 1)
                    // `est(label) <= sw - 4`: the label stays only where it fits its part.
                    if (t.size.width <= w - 4f * d) drawText(t, topLeft = Offset(x + 1f * d, th + 3f * d))
                }
                x += w + VizStackGeometry.GAP * d
            }
        }
        Row(Modifier.fillMaxWidth().height(thickness + LABEL_ZONE)) {
            val widths = VizStackGeometry.widths(shown.map { it.weight }, 1000f)
            shown.forEachIndexed { i, p ->
                // Each part answers with its own weight of the row (the gap goes with the part), so focus order and width match the pill.
                val share = if (total <= 0.0) 0f else (widths[i] + VizStackGeometry.GAP) / 1000f
                Box(
                    Modifier.weight(share.coerceAtLeast(0.0001f)).fillMaxHeight()
                        .semantics(mergeDescendants = true) { contentDescription = p.description; role = Role.Image }
                        .focusable()
                        .testTag("$tagPrefix.part.$i"),
                )
            }
        }
    }
}

val STACK_THICKNESS: Dp = 12.dp
private val LABEL_ZONE: Dp = 15.dp
private const val TEXTURE_SCALE = 0.7f

/** The stack geometry in dp, pure. */
object VizStackGeometry {
    const val GAP = 2f

    /** The width of each part: [weights] of the room left after the gaps; non-finite or non-positive weights count as nothing. */
    fun widths(weights: List<Double>, total: Float): List<Float> {
        if (weights.isEmpty()) return emptyList()
        val w = weights.map { VizFormat.safe(it) }
        val sum = w.sum()
        val room = (total - GAP * (weights.size - 1)).coerceAtLeast(0f)
        return if (sum <= 0.0) weights.map { 0f } else w.map { (it / sum * room).toFloat() }
    }
}
