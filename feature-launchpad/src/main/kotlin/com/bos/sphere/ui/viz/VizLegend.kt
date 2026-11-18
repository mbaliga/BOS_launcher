package com.bos.sphere.ui.viz

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import androidx.compose.ui.geometry.RoundRect

/**
 * `swatch`: the key of a texture, a round [size] dp sample of it with a hairline outline (35 percent ink; none for solid). The texture scale
 * is 0.62 under 20 px, as in the toolkit.
 */
@Composable
fun VizSwatch(texture: VizTexture, tone: VizTone, modifier: Modifier = Modifier, size: Dp = 14.dp) {
    val d = LocalDensity.current.density
    val scale = if (size.value < 20f) 0.62f else 1f
    Canvas(modifier.size(size).clearAndSetSemantics { }) {
        val s = this.size.minDimension
        val p = Path().apply { addRoundRect(RoundRect(0.5f, 0.5f, s - 0.5f, s - 0.5f, CornerRadius(s / 2))) }
        clipPath(p) { drawRect(VizTextures.brush(texture, tone.ink, scale, d, 0f, s), size = Size(s, s)) }
        if (texture != VizTexture.Solid) drawPath(p, tone.ink.copy(alpha = 0.35f), style = Stroke(1f))
    }
}

/** One key of a [VizLegend]: its texture, an optional [lead] (the app's own coloured icon), the name and the value. */
data class VizKey(val texture: VizTexture, val label: String, val value: String? = null, val tag: String = "")

/**
 * `legend` (row layout): the keys in the same textures, wrapping where the row ends. [lead] draws the identity beside the swatch; it is the
 * only place a hue may appear (the app's coloured icon), because identity inside the chart is texture alone.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VizLegend(
    keys: List<VizKey>,
    tone: VizTone,
    modifier: Modifier = Modifier,
    lead: @Composable (VizKey) -> Unit = {},
    tagPrefix: String = "viz.legend",
) {
    FlowRow(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        keys.forEachIndexed { i, k ->
            Row(Modifier.testTag("$tagPrefix.key.$i").semantics(mergeDescendants = true) { contentDescription = listOfNotNull(k.label, k.value).joinToString(", ") }, verticalAlignment = Alignment.CenterVertically) {
                VizSwatch(k.texture, tone)
                Spacer(Modifier.width(5.dp))
                lead(k)
                Spacer(Modifier.width(5.dp))
                Text(k.label, color = tone.ink.copy(alpha = 0.85f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 88.dp))
                if (k.value != null) Text(k.value, color = tone.ink, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1, modifier = Modifier.padding(start = 6.dp))
            }
        }
    }
}
