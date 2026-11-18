package com.bos.sphere.ui.viz

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.draw.alpha
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

/**
 * One key of a [VizLegend]: its texture, an optional lead (the app's own coloured icon), the name and the value. [percent] is set only for the
 * keys whose part the stack could not label: the percentage lives in the stack or in the legend line, never both.
 */
data class VizKey(val texture: VizTexture, val label: String, val value: String? = null, val tag: String = "", val percent: String? = null)

/**
 * `legend` (wave 29, one entry per line): swatch (14 dp), the lead (the app's coloured icon, 16 dp), the name (ellipsizes, takes the middle of the
 * line), the percentage where the stack has none, and the duration right-aligned in muted ink. A line is 24 dp high or more (it grows with large
 * text, so text never overlaps), lines are 8 dp apart, and the whole line is the tap target for [onSelect] (Compose widens a smaller target to the
 * view configuration's 48 dp minimum without drawing anything larger). [lead] is the only place a hue may appear.
 */
@Composable
fun VizLegend(
    keys: List<VizKey>,
    tone: VizTone,
    modifier: Modifier = Modifier,
    lead: @Composable (VizKey) -> Unit = {},
    tagPrefix: String = "viz.legend",
    textSp: Float = 12f,
    selected: Int = -1,
    onSelect: ((Int) -> Unit)? = null,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        keys.forEachIndexed { i, k ->
            val dim = selected >= 0 && selected != i
            Row(
                Modifier.fillMaxWidth().heightIn(min = 24.dp).testTag("$tagPrefix.key.$i")
                    .then(if (onSelect != null) Modifier.clickable { onSelect(i) } else Modifier)
                    .semantics(mergeDescendants = true) {
                        contentDescription = listOfNotNull(k.label, k.value).joinToString(", ")
                        if (onSelect != null) { role = Role.Button; this.selected = selected == i }
                    }
                    .alpha(if (dim) 0.55f else 1f),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                VizSwatch(k.texture, tone, size = 14.dp)
                Spacer(Modifier.width(8.dp))
                Box(Modifier.size(16.dp)) { lead(k) }
                Spacer(Modifier.width(8.dp))
                Text(k.label, color = tone.ink.copy(alpha = 0.92f), fontSize = textSp.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).testTag("$tagPrefix.name.$i"))
                if (k.percent != null) Text(k.percent, color = tone.ink.copy(alpha = 0.72f), fontSize = textSp.sp, maxLines = 1, modifier = Modifier.padding(start = 8.dp).testTag("$tagPrefix.percent.$i"))
                if (k.value != null) Text(k.value, color = tone.ink.copy(alpha = 0.72f), fontSize = textSp.sp, fontWeight = FontWeight.Medium, maxLines = 1, softWrap = false, modifier = Modifier.padding(start = 10.dp).testTag("$tagPrefix.value.$i"))
            }
        }
    }
}

/** The picked legend line when it still exists among [count] lines, else -1 (data changed under a saved pick: nothing is dimmed). */
fun clampPick(picked: Int, count: Int): Int = if (picked in 0 until count) picked else -1
