package com.bos.sphere.ui.viz

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * `table()`: the chart's data as a plain table, the toolkit's accessible view of every chart. The first column is the row header (`th scope=row`),
 * the others are cells; each row reads as one sentence to TalkBack ("Tue, 2 h 5 min").
 */
@Composable
fun VizTable(headers: List<String>, rows: List<List<String>>, tone: VizTone, modifier: Modifier = Modifier, tagPrefix: String = "viz.table") {
    Column(modifier.fillMaxWidth().testTag(tagPrefix)) {
        if (headers.isNotEmpty()) Row(Modifier.fillMaxWidth().padding(bottom = 2.dp).semantics(mergeDescendants = true) { heading() }) {
            headers.forEachIndexed { i, h -> cell(h, i, tone, head = true) }
        }
        Box(Modifier.fillMaxWidth().height(0.5.dp).background(tone.ink.copy(alpha = 0.25f)))
        rows.forEachIndexed { r, row ->
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp).testTag("$tagPrefix.row.$r").semantics(mergeDescendants = true) { contentDescription = row.joinToString(", ") }) {
                row.forEachIndexed { i, v -> cell(v, i, tone, head = false) }
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.cell(text: String, col: Int, tone: VizTone, head: Boolean) {
    Text(
        text, color = tone.ink.copy(alpha = if (head) 0.72f else if (col == 0) 0.85f else 1f), fontSize = if (head) 11.sp else 13.sp,
        fontWeight = if (!head && col > 0) FontWeight.Medium else FontWeight.Normal, maxLines = 1,
        textAlign = if (col == 0) TextAlign.Start else TextAlign.End,
        modifier = Modifier.weight(if (col == 0) 1.2f else 1f).align(Alignment.CenterVertically),
    )
}
