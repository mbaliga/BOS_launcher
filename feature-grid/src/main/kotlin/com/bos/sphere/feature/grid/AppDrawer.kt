package com.bos.sphere.feature.grid

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bos.sphere.core.data.AppEntry
import com.bos.sphere.core.design.HyleColors
import com.bos.sphere.core.design.L10n
import com.bos.sphere.feature.search.AppSearch
import kotlinx.coroutines.delay

/**
 * Type-to-search, kept deliberately light so it comes up instantly: a field and a short result
 * list (recent apps until you type), no full app grid. The keyboard is requested after the first
 * frame, not during the fade-in. Enter launches the top result.
 */
@Composable
fun SearchOverlay(
    visible: Boolean,
    apps: List<AppEntry>,
    recent: List<AppEntry>,
    onLaunch: (AppEntry) -> Unit,
    onLongPressApp: (AppEntry) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    LaunchedEffect(visible) {
        if (visible) {
            query = ""
            delay(60) // let the overlay draw its first frame before the IME animates
            runCatching { focus.requestFocus(); keyboard?.show() }
        } else {
            keyboard?.hide()
        }
    }
    if (visible) BackHandler(onBack = onDismiss)

    AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut(), modifier = modifier) {
        val results = remember(apps, recent, query) {
            if (query.isBlank()) recent.take(8) else AppSearch.rank(apps, query, limit = 12)
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(HyleColors.Background.copy(alpha = 0.96f))
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .imePadding(),
        ) {
            androidx.compose.foundation.layout.Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .padding(top = 12.dp, bottom = 8.dp)
                        .fillMaxWidth()
                        .background(HyleColors.Surface, RoundedCornerShape(14.dp))
                        .border(1.dp, HyleColors.Hairline, RoundedCornerShape(14.dp))
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                ) {
                    Icon(Glyphs.Search, null, tint = HyleColors.InkDim, modifier = Modifier.size(18.dp))
                    Box(Modifier.weight(1f).padding(start = 10.dp)) {
                        if (query.isEmpty()) Text(L10n.tr("Search apps"), color = HyleColors.InkDim, fontSize = 15.sp)
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
                        modifier = Modifier.size(32.dp).clickable(onClick = onDismiss).padding(6.dp),
                    )
                }
                if (results.isEmpty() && query.isNotBlank()) {
                    Text(L10n.tr("No apps match “%s”", query), color = HyleColors.InkDim, fontSize = 13.sp, modifier = Modifier.padding(top = 20.dp))
                }
                LazyColumn(Modifier.fillMaxSize()) {
                    items(results, key = { it.key }) { app ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .androidxCombinedClick(onClick = { onLaunch(app) }, onLongClick = { onLongPressApp(app) })
                                .padding(vertical = 8.dp, horizontal = 4.dp),
                        ) {
                            AppIcon(app, 42.dp)
                            Text(app.label, color = HyleColors.InkBright, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 14.dp))
                        }
                    }
                }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
private fun Modifier.androidxCombinedClick(onClick: () -> Unit, onLongClick: () -> Unit): Modifier =
    this.then(Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick))
