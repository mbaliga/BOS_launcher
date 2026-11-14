package com.bos.sphere.feature.grid

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalLayoutDirection
import com.bos.sphere.core.data.AppEntry
import com.bos.sphere.core.design.HyleColors
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private sealed interface LRow {
    data class Head(val letter: Char) : LRow
    data class App(val app: AppEntry) : LRow
}

/** Letter an app files under: its first letter, or '#'. */
internal fun listLetter(label: String): Char = label.firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()?.let { if (it.isLetter()) it else '#' } ?: '#'

/**
 * A plain A–Z list home: icon and name per row under letter headers, with a letter rail on the
 * trailing edge you can drag to speed-scrub the whole list (a big letter shows while you do).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ListHome(
    apps: List<AppEntry>,
    onLaunch: (AppEntry) -> Unit,
    onLongPress: (AppEntry) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    iconScale: Float = 0.8f,
    showLabels: Boolean = true,
    /** Pinch / horizontal swipes / double tap / long press on empty space (see [homeGestures]). */
    gestures: HomeGestureSpec? = null,
) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        val rows = remember(apps) {
            buildList<LRow> {
                var last: Char? = null
                apps.sortedBy { it.label.lowercase() }.forEach { a ->
                    val l = listLetter(a.label)
                    if (l != last) { add(LRow.Head(l)); last = l }
                    add(LRow.App(a))
                }
            }
        }
        val letters = remember(rows) { rows.filterIsInstance<LRow.Head>().map { it.letter } }
        val firstIndexOf = remember(rows) { letters.associateWith { l -> rows.indexOfFirst { it is LRow.Head && it.letter == l } } }
        val state = rememberLazyListState()
        val scope = rememberCoroutineScope()
        val haptic = LocalHapticFeedback.current
        var scrubLetter by remember { mutableStateOf<Char?>(null) }
        val icon = (46f * iconScale / 0.8f).coerceIn(32f, 60f).dp
        val density = LocalDensity.current
        // Empty = no app row under the finger (letter headers and the side margins count as empty).
        val isEmptyAt: (Offset) -> Boolean = { pos ->
            val left = with(density) { contentPadding.calculateLeftPadding(LayoutDirection.Ltr).toPx() }
            val right = state.layoutInfo.viewportSize.width - with(density) { (contentPadding.calculateRightPadding(LayoutDirection.Ltr) + 28.dp).toPx() }
            pos.x < left || pos.x >= right || state.layoutInfo.visibleItemsInfo.none { item ->
                item.contentType != LRow.Head::class && pos.y >= item.offset && pos.y < item.offset + item.size
            }
        }

        Box(modifier.fillMaxSize().homeGestures(gestures, isEmptyAt)) {
            LazyColumn(
                state = state,
                contentPadding = PaddingValues(
                    start = contentPadding.calculateLeftPadding(LayoutDirection.Ltr),
                    end = contentPadding.calculateRightPadding(LayoutDirection.Ltr) + 28.dp,
                    top = contentPadding.calculateTopPadding(),
                    bottom = contentPadding.calculateBottomPadding(),
                ),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(rows, key = { if (it is LRow.Head) "h${it.letter}" else (it as LRow.App).app.key }, contentType = { it::class }) { r ->
                    when (r) {
                        is LRow.Head -> Text(
                            r.letter.toString(), color = HyleColors.Violet, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(start = 6.dp, top = 14.dp, bottom = 4.dp),
                        )
                        is LRow.App -> Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                                .launcherItem("list.${r.app.key}", focusable = false, onMenu = { onLongPress(r.app) })
                                .combinedClickable(onClick = { onLaunch(r.app) }, onLongClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress); onLongPress(r.app)
                                })
                                .padding(vertical = 6.dp, horizontal = 4.dp),
                        ) {
                            AppIcon(r.app, icon)
                            if (showLabels) {
                                Spacer(Modifier.width(16.dp))
                                Text(r.app.label, color = HyleColors.InkBright, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
            // Letter rail: drag anywhere along it.
            if (letters.size > 1) {
                Column(
                    Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(30.dp)
                        .padding(top = contentPadding.calculateTopPadding(), bottom = contentPadding.calculateBottomPadding())
                        .pointerInput(letters) {
                            fun at(y: Float) {
                                val i = ((y / size.height.coerceAtLeast(1)) * letters.size).toInt().coerceIn(0, letters.size - 1)
                                val l = letters[i]
                                if (l != scrubLetter) {
                                    scrubLetter = l
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    scope.launch { state.scrollToItem(firstIndexOf[l] ?: 0) }
                                }
                            }
                            detectDragGestures(
                                onDragStart = { at(it.y) },
                                onDragEnd = { scrubLetter = null },
                                onDragCancel = { scrubLetter = null },
                            ) { change, _ -> change.consume(); at(change.position.y) }
                        },
                    verticalArrangement = Arrangement.SpaceEvenly,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    letters.forEach { l ->
                        Text(l.toString(), color = if (l == scrubLetter) HyleColors.InkBright else HyleColors.InkDim, fontSize = 10.sp)
                    }
                }
            }
            scrubLetter?.let { l ->
                Box(
                    Modifier.align(Alignment.Center).size(84.dp).background(HyleColors.Surface.copy(alpha = 0.92f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) { Text(l.toString(), color = HyleColors.InkBright, fontSize = 40.sp, fontWeight = FontWeight.Light) }
            }
        }
    }
}
