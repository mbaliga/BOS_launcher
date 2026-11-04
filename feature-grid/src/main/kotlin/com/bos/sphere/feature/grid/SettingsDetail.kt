package com.bos.sphere.feature.grid

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bos.sphere.core.design.HyleColors
import androidx.compose.material3.Text
import androidx.compose.material3.Icon

/**
 * Full-screen option page. Breadcrumbs ("Settings › Category › Item") run along the top and
 * each is tappable; the body is a scrolling column of visual (WYSIWYG) choices.
 */
@Composable
fun DetailScreen(
    crumbs: List<String>,
    onCrumb: (Int) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(modifier.fillMaxSize().background(HyleColors.Background.copy(alpha = 0.97f)).pointerInput(Unit) { detectTapGestures { } }) {
        WaveField(Modifier.fillMaxSize().alpha(0.5f))
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal = 20.dp)) {
            Row(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                crumbs.forEachIndexed { i, c ->
                    val last = i == crumbs.lastIndex
                    if (i > 0) Text("›", color = HyleColors.InkDim, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 8.dp))
                    Text(
                        c.uppercase(),
                        color = if (last) HyleColors.InkBright else HyleColors.InkDim,
                        fontSize = 12.sp, letterSpacing = 1.6.sp,
                        fontWeight = if (last) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1,
                        modifier = Modifier.pointerInput(i) { detectTapGestures { onCrumb(i) } }.padding(vertical = 8.dp),
                    )
                }
            }
            Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(bottom = 28.dp)) { content() }
        }
    }
}
