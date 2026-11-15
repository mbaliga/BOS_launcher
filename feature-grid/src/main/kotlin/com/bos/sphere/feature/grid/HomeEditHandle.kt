package com.bos.sphere.feature.grid

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bos.sphere.core.design.HyleColors
import com.bos.sphere.core.design.L10n

/**
 * HOM-12: an item in edit mode. The icon is drawn as usual and a remove handle sits on its top
 * start corner; the handle takes its own touch, so a tap on it removes the item from home and a
 * drag anywhere else on the item rearranges it (see the arbiter in [GridHome]).
 */
@Composable
fun EditHandleBox(size: Dp, label: String, onRemove: () -> Unit, content: @Composable BoxScope.() -> Unit) {
    Box(Modifier.size(size)) {
        content()
        Box(
            Modifier.align(Alignment.TopStart).offset((-6).dp, (-6).dp).size(28.dp)
                .clickable(onClick = onRemove)
                .semantics { role = Role.Button; contentDescription = L10n.tr("Remove from home") + ": " + label },
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(20.dp).background(Color(0xFFE5484D), CircleShape), contentAlignment = Alignment.Center) {
                Text("−", color = Color.White, fontSize = 14.sp)
            }
        }
    }
}
