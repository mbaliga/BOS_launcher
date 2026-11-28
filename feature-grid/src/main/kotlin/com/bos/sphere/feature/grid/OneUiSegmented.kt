package com.bos.sphere.feature.grid

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bos.sphere.core.design.HyleColors

/** Whether a choice of [count] options of the longest label [longest] fits a segmented control (2 or 3 short options), else it is a radio dialog. */
object OneUiSegmentedRule {
    const val MAX_OPTIONS = 3
    const val MAX_LABEL_CHARS = 14
    fun fits(labels: List<String>): Boolean = labels.size in 2..MAX_OPTIONS && labels.all { it.length <= MAX_LABEL_CHARS }
}

fun oneUiSegmentedTag(i: Int) = "oneui.segment.$i"

/**
 * A rounded segmented control for two or three short options on one row: the selected segment is filled with a soft accent, the others
 * stay flat. Each segment is a 48 dp radio button for the screen reader; the labels wrap under a large font.
 */
@Composable
fun OneUiSegmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier, description: String? = null) {
    val shape = RoundedCornerShape(24.dp)
    Row(
        modifier.fillMaxWidth().clip(shape).background(OneUi.Track.copy(alpha = 0.35f)).padding(3.dp).selectableGroup()
            .semantics { if (description != null) contentDescription = description },
    ) {
        options.forEachIndexed { i, label ->
            val on = i == selected
            Box(
                Modifier.weight(1f).heightIn(min = 48.dp).clip(RoundedCornerShape(21.dp))
                    .background(if (on) HyleColors.Violet.copy(alpha = 0.30f) else androidx.compose.ui.graphics.Color.Transparent)
                    .testTag(oneUiSegmentedTag(i))
                    .clickable(role = Role.RadioButton) { if (!on) onSelect(i) }
                    .semantics(mergeDescendants = true) { this.selected = on; contentDescription = label },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label, color = if (on) HyleColors.InkBright else HyleColors.InkDim, fontSize = 15.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                    textAlign = TextAlign.Center, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                )
            }
        }
    }
}

/** A settings row for a segmented choice: the title (and grey line) above, the control under it. */
@Composable
fun OneUiSegmentedRow(title: String, options: List<String>, selected: Int, onSelect: (Int) -> Unit, summary: String? = null) {
    OneUiRowFrame(
        title, summary = summary, role = null,
        below = { OneUiSegmented(options, selected, onSelect, Modifier.padding(top = 8.dp, bottom = 4.dp), description = title) },
    )
}
