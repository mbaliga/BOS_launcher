package com.bos.sphere.ui.viz

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp

/**
 * `stat`: the number itself. The toolkit stacks a muted caption (0.72 rem, 72 percent) over a light (weight 300) figure; in a one-line
 * header the caption stands on the figure's baseline instead, so a block that is about 150 dp high keeps its room for the chart.
 */
@Composable
fun VizStat(caption: String, figure: String, tone: VizTone, modifier: Modifier = Modifier, captionSp: Int = 13, figureSp: Int = 24) {
    val semantic = modifier.semantics(mergeDescendants = true) { contentDescription = "$caption, $figure" }
    val cap: @Composable (Modifier) -> Unit = { m -> Text(caption, color = tone.ink.copy(alpha = 0.72f), fontSize = captionSp.sp, maxLines = 1, modifier = m) }
    // Large text: the figure steps down to the old 24 sp so "2 h 34 min" still fits one line of a narrow block at 2x.
    val big = LocalDensity.current.fontScale > 1.3f
    val fig: @Composable () -> Unit = { Text(figure, color = tone.ink, fontSize = (if (big) minOf(figureSp, 22) else figureSp).sp, fontWeight = FontWeight.Light, maxLines = 1, letterSpacing = (-0.01).sp, modifier = Modifier.testTag("viz.stat.figure")) }
    // Large text: the caption stacks over the figure, as in the toolkit's own `stat`, instead of sharing a line that no longer fits.
    if (LocalDensity.current.fontScale > 1.3f) Column(semantic) { cap(Modifier); fig() }
    else Row(semantic, horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Bottom) { cap(Modifier.padding(bottom = 4.dp)); fig() }
}
