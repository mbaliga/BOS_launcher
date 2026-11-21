package com.bos.sphere.feature.dock

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bos.sphere.core.design.HyleColors
import com.bos.sphere.core.design.L10n
import com.bos.sphere.core.launch.AppGroup

/** What the home shelf shows (WSP-05): saved groups only, up to [MAX] chips; nothing at all when there are none or it is off. */
object GroupShelfModel {
    const val MAX = 6
    fun visible(groups: List<AppGroup>, on: Boolean): List<AppGroup> = if (on) groups.take(MAX) else emptyList()
    fun description(g: AppGroup) = L10n.tr("Open group %s, %d apps", g.name, g.members.size)
}

/**
 * A group is an item of the home: one chip per saved group, one tap to open all its apps (WSP-05). It is a shelf above the
 * dock rather than a slot in the page grid, which belongs to the home stream's item model.
 */
@Composable
fun GroupShelf(groups: List<AppGroup>, onRun: (AppGroup) -> Unit, modifier: Modifier = Modifier) {
    if (groups.isEmpty()) return
    val shape = RoundedCornerShape(16.dp)
    Row(modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        groups.forEach { g ->
            Row(
                Modifier.clip(shape).background(HyleColors.Surface.copy(alpha = 0.92f)).border(1.dp, HyleColors.Hairline, shape)
                    .clickable { onRun(g) }.padding(horizontal = 12.dp, vertical = 8.dp)
                    .semantics { role = Role.Button; contentDescription = GroupShelfModel.description(g) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(g.name, color = HyleColors.InkBright, fontSize = 13.sp)
                Text("  ${g.members.size}", color = HyleColors.InkDim, fontSize = 12.sp)
            }
        }
    }
}
