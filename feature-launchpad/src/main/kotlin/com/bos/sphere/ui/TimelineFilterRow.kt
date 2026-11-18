package com.bos.sphere.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.Text
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** One text option of a [CommsFilterRow]: the visible [label], the spoken [description], whether it is the chosen one, and its test tag. */
class FilterOpt(val label: String, val description: String, val selected: Boolean, val tag: String, val onSelect: () -> Unit)

/**
 * Wave 29 (timelineClean): the Timeline's filters as one flat, scrolling row of letter-spaced capitals like the tab row but smaller (12 sp from
 * the Comms scale): muted ink, the chosen option in the primary ink with a 2 dp accent underline. [groups] are separated by a thin vertical
 * hairline. An option is 32 dp high and Compose widens its touch target to the 48 dp minimum without drawing anything larger; each is a radio
 * (selected, role) in its group. The row scrolls sideways when it does not fit (large text, 125 percent) and never wraps.
 */
@Composable
fun CommsFilterRow(groups: List<List<FilterOpt>>, modifier: Modifier = Modifier, tag: String = "timeline.filter") {
    val lp = LocalLp.current
    Row(modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).testTag(tag), verticalAlignment = Alignment.CenterVertically) {
        groups.forEachIndexed { gi, g ->
            if (gi > 0) Box(Modifier.padding(end = 14.dp).width(CommsStyle.HAIR_DP.dp).height(16.dp).background(lp.line))
            g.forEach { o ->
                Box(
                    Modifier.heightIn(min = 32.dp)
                        .selectable(selected = o.selected, role = Role.RadioButton, onClick = o.onSelect)
                        .semantics { contentDescription = o.description }
                        .testTag(o.tag)
                        .drawBehind {
                            if (o.selected) drawLine(BlueLight, Offset(0f, size.height - 1.dp.toPx()), Offset(size.width, size.height - 1.dp.toPx()), 2.dp.toPx())
                        },
                    Alignment.Center,
                ) {
                    Text(
                        o.label.uppercase(), color = if (o.selected) lp.ink else lp.tabDim, fontSize = CommsStyle.CHIP_SP.sp, letterSpacing = 1.sp,
                        fontWeight = if (o.selected) FontWeight.Medium else FontWeight.Normal, maxLines = 1, softWrap = false,
                        modifier = Modifier.padding(vertical = 6.dp),
                    )
                }
                androidx.compose.foundation.layout.Spacer(Modifier.width(14.dp))
            }
        }
    }
}
