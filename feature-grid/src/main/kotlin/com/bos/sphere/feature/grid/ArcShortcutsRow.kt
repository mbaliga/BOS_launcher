package com.bos.sphere.feature.grid

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.bos.sphere.core.data.AppEntry
import com.bos.sphere.core.data.Corner
import com.bos.sphere.core.input.FocusKind

/** W28 (arcShortcuts): sizes of the shortcut group on the home, in dp. */
internal object ShortcutsAtRest {
    const val ICON = 48f
    const val GAP = 8f
    /** The four-squares hotspot's square. */
    const val GLYPH = 64f
    /** Half the width kept clear in the middle of the bottom edge for the page indicator. */
    const val KEEP_MID = 56f

    /** The icon centres, in dp, of [count] shortcuts at rest on a [wDp] x [hDp] safe area, next to the glyph in [anchor]'s corner. */
    fun centres(anchor: Corner, wDp: Float, hDp: Float, count: Int, liftDp: Float): List<Pair<Float, Float>> =
        ShortcutPlacement.atRest(anchor, wDp, hDp, count, ICON, GAP, GLYPH, KEEP_MID, liftDp)
}

/**
 * The shortcuts at rest on the home (Always): up to four plain launcher icons along the bottom edge next to the four-squares glyph of
 * [anchor]'s corner, or a column up that edge when the row would reach the page indicator. [liftDp] raises them above the dock. Laid out
 * left to right whatever the language (the corner is physical).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ArcShortcutsRow(
    anchor: Corner,
    shortcuts: List<AppEntry>,
    onLaunch: (AppEntry) -> Unit,
    onLongPress: (AppEntry) -> Unit,
    modifier: Modifier = Modifier,
    liftDp: Float = 0f,
) {
    if (shortcuts.isEmpty()) return
    val haptics = rememberLauncherHaptics()
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        BoxWithConstraints(modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            val centres = ShortcutsAtRest.centres(anchor, maxWidth.value, maxHeight.value, shortcuts.size, liftDp)
            shortcuts.forEachIndexed { i, a ->
                val (cx, cy) = centres[i]
                key(a.key) {
                    Box(
                        Modifier
                            .offset((cx - ShortcutsAtRest.ICON / 2f).dp, (cy - ShortcutsAtRest.ICON / 2f).dp)
                            .size(ShortcutsAtRest.ICON.dp)
                            .semantics { contentDescription = a.label }
                            .launcherItem("home.shortcut.${a.key}", FocusKind.Item, onActivate = { onLaunch(a) }, onMenu = { onLongPress(a) }, ringRadiusDp = 26f)
                            .appDragSource(a.key)
                            .combinedClickable(
                                role = Role.Button,
                                onLongClick = { haptics.performHapticFeedback(HapticFeedbackType.LongPress); onLongPress(a) },
                                onClick = { onLaunch(a) },
                            ),
                    ) { AppIcon(app = a, size = ShortcutsAtRest.ICON.dp) }
                }
            }
        }
    }
}

/**
 * W28 (arcShortcuts): the alphabet wheel with its shortcuts, as the home hosts it. With shortcuts On and "Always" the chosen apps stay on
 * the home next to the four squares ([restCorner], null when that hotspot is not on the screen) and the wheel shows them too as its bottom
 * row; with "Only with the app wheel" they show only while the wheel is open; with shortcuts Off there is no shortcut anywhere.
 */
@Composable
fun ArcWheelHost(
    wheelOpen: Boolean,
    wheelAnchor: Corner,
    restCorner: Corner?,
    restHidden: Boolean,
    apps: List<AppEntry>,
    appsByKey: Map<String, AppEntry>,
    shortcutsOn: Boolean,
    shortcutKeys: List<String>,
    shortcutsShown: com.bos.sphere.core.data.ShortcutsShown,
    liftDp: Float,
    onLaunch: (AppEntry) -> Unit,
    onLongPress: (AppEntry) -> Unit,
    onSearch: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val chosen = androidx.compose.runtime.remember(shortcutsOn, shortcutKeys, appsByKey) {
        if (shortcutsOn) com.bos.sphere.core.data.ArcShortcuts.clamp(shortcutKeys).mapNotNull { appsByKey[it] } else emptyList()
    }
    if (restCorner != null && !wheelOpen && !restHidden && com.bos.sphere.core.data.ArcShortcuts.visibleAtRest(shortcutsOn, shortcutsShown, chosen.size)) {
        ArcShortcutsRow(restCorner, chosen, onLaunch, onLongPress, liftDp = liftDp)
    }
    WheelDrawer(
        visible = wheelOpen, anchor = wheelAnchor, apps = apps,
        shortcuts = if (com.bos.sphere.core.data.ArcShortcuts.visibleWithWheel(shortcutsOn, chosen.size)) chosen else emptyList(),
        onLaunch = onLaunch, onLongPressApp = onLongPress, onSearch = onSearch, onDismiss = onDismiss, modifier = modifier.fillMaxSize(),
    )
}
