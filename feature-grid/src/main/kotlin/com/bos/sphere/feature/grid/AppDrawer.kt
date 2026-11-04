package com.bos.sphere.feature.grid

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bos.sphere.core.data.AppEntry
import com.bos.sphere.core.design.HyleColors
import com.bos.sphere.feature.search.AppSearch

/**
 * Full-screen app list + search (the "List" drawer style, and where the Search action lands).
 * [focusSearch] raises the keyboard on open. Enter launches the top result.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppDrawer(
    visible: Boolean,
    focusSearch: Boolean,
    apps: List<AppEntry>,
    onLaunch: (AppEntry) -> Unit,
    onLongPressApp: (AppEntry) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    LaunchedEffect(visible, focusSearch) {
        if (visible) {
            query = ""
            if (focusSearch) runCatching { focus.requestFocus(); keyboard?.show() }
        } else {
            keyboard?.hide()
        }
    }
    if (visible) BackHandler(onBack = onDismiss)

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + slideInVertically { it / 8 },
        exit = fadeOut() + slideOutVertically { it / 8 },
        modifier = modifier,
    ) {
        val results = remember(apps, query) { if (query.isBlank()) apps else AppSearch.rank(apps, query, limit = 60) }
        Column(
            Modifier
                .fillMaxSize()
                .background(HyleColors.Background.copy(alpha = 0.97f))
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .imePadding()
                .padding(horizontal = 16.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .padding(top = 12.dp, bottom = 10.dp)
                    .fillMaxWidth()
                    .background(HyleColors.Surface, RoundedCornerShape(14.dp))
                    .border(1.dp, HyleColors.Hairline, RoundedCornerShape(14.dp))
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                Icon(Glyphs.Search, null, tint = HyleColors.InkDim, modifier = Modifier.size(18.dp))
                Box(Modifier.weight(1f).padding(start = 10.dp)) {
                    if (query.isEmpty()) Text("Search apps", color = HyleColors.InkDim, fontSize = 15.sp)
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        textStyle = TextStyle(color = HyleColors.InkBright, fontSize = 15.sp),
                        cursorBrush = SolidColor(HyleColors.Violet),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(onGo = { results.firstOrNull()?.let(onLaunch) }),
                        modifier = Modifier.fillMaxWidth().focusRequester(focus),
                    )
                }
                Icon(
                    Glyphs.Close,
                    contentDescription = "Close",
                    tint = HyleColors.InkDim,
                    modifier = Modifier.size(20.dp).combinedClickable(onClick = onDismiss),
                )
            }
            if (results.isEmpty()) {
                Text("No apps match “$query”", color = HyleColors.InkDim, fontSize = 13.sp, modifier = Modifier.padding(top = 24.dp))
            }
            LazyVerticalGrid(columns = GridCells.Adaptive(minSize = 78.dp), modifier = Modifier.fillMaxSize()) {
                items(results, key = { it.key }) { app ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .padding(vertical = 10.dp)
                            .combinedClickable(onClick = { onLaunch(app) }, onLongClick = { onLongPressApp(app) })
                            .semantics(mergeDescendants = true) { contentDescription = app.label },
                    ) {
                        AppIcon(app, 54.dp, Modifier.size(54.dp))
                        Text(
                            app.label,
                            color = HyleColors.Ink,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 6.dp).width(72.dp),
                        )
                    }
                }
            }
        }
    }
}
