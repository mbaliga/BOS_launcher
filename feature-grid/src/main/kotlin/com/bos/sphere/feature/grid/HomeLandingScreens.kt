package com.bos.sphere.feature.grid

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bos.sphere.core.data.AppEntry
import com.bos.sphere.core.data.HomeStyle
import com.bos.sphere.core.design.HyleColors
import com.bos.sphere.core.design.L10n

/** What the Recent apps and All apps landings need from the home to draw in the chosen home style. */
class LandingLook(
    val style: HomeStyle,
    /** Columns of the grid and DSi styles (the DSi style is always three across). */
    val columns: Int,
    val scrollHorizontal: Boolean,
    val padding: PaddingValues,
    val iconScale: Float,
    val spacing: Float,
    val showLabels: Boolean,
    val sounds: UiSounds?,
    val gestures: HomeGestureSpec?,
    val categoryOf: (AppEntry) -> String,
    val wallpaperScroll: Boolean,
)

/**
 * HOM-22: the apps of [list] in the chosen home style: a grid, a list, the cross media bar or the DSi tiles. No pages, no folders
 * and no widgets: the Recent apps and All apps landings are lists of apps. [ordered] keeps the given order (Recent apps, newest
 * first); the list style sorts A to Z itself, so Recent apps uses its own newest-first list there.
 */
@Composable
internal fun LandingList(list: List<AppEntry>, look: LandingLook, ordered: Boolean, onLaunch: (AppEntry) -> Unit, onLongPress: (AppEntry) -> Unit, jump: ScrollJump? = null) {
    when (look.style) {
        HomeStyle.Grid -> ContinuousHome(
            apps = list, crossCount = look.columns, landscape = look.scrollHorizontal, style = ContinuousStyle.Plain, onLaunch = onLaunch, onLongPress = onLongPress,
            modifier = Modifier.fillMaxSize(), contentPadding = look.padding, iconScale = look.iconScale, spacing = look.spacing, showLabels = look.showLabels,
            sounds = look.sounds, gestures = look.gestures, wallpaperScroll = look.wallpaperScroll, jump = jump,
        )
        HomeStyle.Dsi -> ContinuousHome(
            apps = list, crossCount = 3, landscape = look.scrollHorizontal, style = ContinuousStyle.Dsi, onLaunch = onLaunch, onLongPress = onLongPress,
            modifier = Modifier.fillMaxSize(), contentPadding = look.padding, iconScale = look.iconScale, spacing = look.spacing, showLabels = look.showLabels,
            sounds = look.sounds, gestures = look.gestures, wallpaperScroll = look.wallpaperScroll, jump = jump,
        )
        HomeStyle.List -> if (ordered) RecentsListHome(list, onLaunch, onLongPress, Modifier.fillMaxSize(), look.padding, look.iconScale, look.showLabels, look.gestures)
        else ListHome(list, onLaunch, onLongPress, Modifier.fillMaxSize(), look.padding, look.iconScale, look.showLabels, look.gestures)
        HomeStyle.Psp -> XmbHome(
            apps = list, recent = if (ordered) list else emptyList(), categoryOf = look.categoryOf, onLaunch = onLaunch, onLongPress = onLongPress,
            modifier = Modifier.fillMaxSize(), contentPadding = look.padding, showLabels = look.showLabels, sounds = look.sounds, gestures = look.gestures,
            wallpaperScroll = look.wallpaperScroll,
        )
    }
}

/**
 * HOM-22: the Recent apps landing. [recents] is newest first and already bounded, de-duplicated and filtered by the rules in core-data.
 * Empty: a quiet one-line explanation instead of a blank screen (and how to include apps opened from elsewhere).
 */
@Composable
fun RecentsLanding(
    recents: List<AppEntry>,
    look: LandingLook,
    usageAccessOn: Boolean,
    onGrantUsage: () -> Unit,
    onLaunch: (AppEntry) -> Unit,
    onLongPress: (AppEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize()) {
        if (recents.isEmpty()) {
            // The gesture layer still covers the empty home (long press opens the quick sheet).
            Box(Modifier.fillMaxSize().homeGestures(look.gestures) { true })
            RecentsEmptyState(usageAccessOn, onGrantUsage, Modifier.align(Alignment.Center).safeDrawingPadding())
        } else {
            LandingList(recents, look, ordered = true, onLaunch = onLaunch, onLongPress = onLongPress)
        }
    }
}

/**
 * HOM-22: the All apps landing: the drawer's content as home. Tabs (personal, work, private), tags and type-to-filter come from the
 * drawer's own filter bar and rules, hidden apps are already out of [apps], and the alphabet index jumps in the grid and DSi styles
 * (the list style has its own rail; the cross media bar groups by category and has no alphabet to jump along).
 */
@Composable
fun AllAppsLanding(
    apps: List<AppEntry>,
    look: LandingLook,
    topInset: androidx.compose.ui.unit.Dp,
    onLaunch: (AppEntry) -> Unit,
    onLongPress: (AppEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    val filter = rememberDrawerFilter()
    val filtered = rememberFilteredApps(apps, filter)
    val sorted = remember(filtered) { filtered.sortedWith(compareBy<AppEntry, String>(String.CASE_INSENSITIVE_ORDER) { it.label }.thenBy { it.key }) }
    val letters = remember(sorted) { sorted.map { listLetter(it.label) }.distinct() }
    var jump by remember { mutableStateOf<ScrollJump?>(null) }
    val padL = look.padding.calculateLeftPadding(LayoutDirection.Ltr)
    val padR = look.padding.calculateRightPadding(LayoutDirection.Ltr)
    val listLook = remember(look, padL, padR) {
        LandingLook(look.style, look.columns, look.scrollHorizontal, PaddingValues(start = padL, end = padR, top = 4.dp, bottom = look.padding.calculateBottomPadding()),
            look.iconScale, look.spacing, look.showLabels, look.sounds, look.gestures, look.categoryOf, look.wallpaperScroll)
    }
    Column(modifier.fillMaxSize().safeDrawingPadding().padding(top = topInset)) {
        DrawerFilterBar(filter, Modifier.padding(horizontal = padL.coerceAtMost(24.dp)))
        Box(Modifier.fillMaxWidth().weight(1f)) {
            if (sorted.isEmpty()) {
                Box(Modifier.fillMaxSize().homeGestures(look.gestures) { true })
                if (apps.isNotEmpty()) Text(L10n.tr("No apps match"), color = HyleColors.InkDim, fontSize = 14.sp, modifier = Modifier.align(Alignment.Center))
            } else {
                LandingList(sorted, listLook, ordered = false, onLaunch = onLaunch, onLongPress = onLongPress, jump = jump)
                val gridLike = look.style == HomeStyle.Grid || look.style == HomeStyle.Dsi
                if (gridLike) LetterIndexRail(letters, onJump = { l -> jump = ScrollJump(sorted.indexOfFirst { listLetter(it.label) == l }.coerceAtLeast(0)) })
            }
        }
    }
}
