package com.bos.sphere.ui.viz

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
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
fun VizStat(caption: String, figure: String, tone: VizTone, modifier: Modifier = Modifier, figureSp: Int = 24) {
    Row(
        modifier.semantics(mergeDescendants = true) { contentDescription = "$caption, $figure" },
        horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Bottom,
    ) {
        Text(caption, color = tone.ink.copy(alpha = 0.72f), fontSize = 13.sp, maxLines = 1, modifier = Modifier.padding(bottom = 4.dp))
        Text(figure, color = tone.ink, fontSize = figureSp.sp, fontWeight = FontWeight.Light, maxLines = 1, letterSpacing = (-0.01).sp)
    }
}
