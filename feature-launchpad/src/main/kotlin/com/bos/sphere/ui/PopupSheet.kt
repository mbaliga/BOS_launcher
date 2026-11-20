package com.bos.sphere.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bos.sphere.core.design.L10n
import com.bos.sphere.core.design.rememberMotionPrefs

/**
 * The scaffolding the Sound and Vibration sheet introduced (wave 27) and the Connectivity sheet shares (wave 34): a dimmed scrim, a pure black
 * bottom sheet with a 36 dp top radius and a centred title, Back, Escape, a tap on the scrim and a drag down to dismiss, a slide in (a fade with
 * reduced motion), at most 640 dp wide (760 dp in landscape), scrolling when the content does not fit. Test tags are `<prefix>.root`,
 * `.scrim`, `.sheet`, `.title`. [content] gets `compact` (a landscape phone: less air).
 */
@Composable
internal fun PopupSheet(
    title: String, closeLabel: String, tagPrefix: String, onDismiss: () -> Unit, modifier: Modifier = Modifier,
    content: @Composable ColumnScope.(compact: Boolean) -> Unit,
) {
    BackHandler(enabled = true, onBack = onDismiss)
    val reduced = rememberMotionPrefs().reduced
    val visible = remember { MutableTransitionState(false).apply { targetState = true } }
    val req = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { req.requestFocus() } }
    val compact = LocalConfiguration.current.screenHeightDp < 480 // landscape phones: less air, the rest scrolls
    var drag by remember { mutableFloatStateOf(0f) }
    val onDismissLatest by rememberUpdatedState(onDismiss)
    val dismissPx = with(LocalDensity.current) { 96.dp.toPx() }
    Box(
        modifier.fillMaxSize().testTag("$tagPrefix.root")
            .onPreviewKeyEvent { if (it.type == KeyEventType.KeyDown && it.key == Key.Escape) { onDismiss(); true } else false }
            .focusRequester(req).focusable(),
    ) {
        Box(
            Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.62f)).testTag("$tagPrefix.scrim")
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClickLabel = closeLabel, onClick = onDismiss),
        )
        AnimatedVisibility(
            visibleState = visible, modifier = Modifier.align(Alignment.BottomCenter),
            enter = if (reduced) fadeIn(tween(150)) else slideInVertically(tween(260)) { it } + fadeIn(tween(200)),
        ) {
            Column(
                Modifier.widthIn(max = if (compact) 760.dp else 640.dp).fillMaxWidth().testTag("$tagPrefix.sheet")
                    .graphicsLayer { translationY = drag.coerceAtLeast(0f) }
                    .nestedScroll(remember(dismissPx) {
                        // A drag down that the scrolling content cannot use (it is at the top, or fits) moves the sheet; letting go far enough closes it.
                        object : NestedScrollConnection {
                            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                                if (drag <= 0f || available.y >= 0f) return Offset.Zero
                                val used = maxOf(available.y, -drag); drag += used; return Offset(0f, used)
                            }
                            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                                if (available.y <= 0f) return Offset.Zero
                                drag += available.y; return Offset(0f, available.y)
                            }
                            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                                if (drag > dismissPx || (drag > 0f && available.y > 1800f)) onDismissLatest() else drag = 0f
                                return available
                            }
                        }
                    })
                    .background(Color.Black, RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp))
                    .navigationBarsPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 32.dp),
            ) {
                Text(
                    title, color = Color.White, fontSize = 22.sp, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = if (compact) 18.dp else 36.dp, bottom = if (compact) 8.dp else 22.dp).testTag("$tagPrefix.title").semantics { heading() },
                )
                content(compact)
            }
        }
    }
}
