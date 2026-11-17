package com.bos.sphere.feature.grid

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import com.bos.sphere.core.data.RecentsProfile
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
            sounds = look.sounds, gestures = look.gestures, jump = jump,
        )
        HomeStyle.Dsi -> ContinuousHome(
            apps = list, crossCount = 3, landscape = look.scrollHorizontal, style = ContinuousStyle.Dsi, onLaunch = onLaunch, onLongPress = onLongPress,
            modifier = Modifier.fillMaxSize(), contentPadding = look.padding, iconScale = look.iconScale, spacing = look.spacing, showLabels = look.showLabels,
            sounds = look.sounds, gestures = look.gestures, jump = jump,
        )
        HomeStyle.List -> if (ordered) RecentsListHome(list, onLaunch, onLongPress, Modifier.fillMaxSize(), look.padding, look.iconScale, look.showLabels, look.gestures)
        else ListHome(list, onLaunch, onLongPress, Modifier.fillMaxSize(), look.padding, look.iconScale, look.showLabels, look.gestures)
        HomeStyle.Psp -> XmbHome(
            apps = list, recent = if (ordered) list else emptyList(), categoryOf = look.categoryOf, onLaunch = onLaunch, onLongPress = onLongPress,
            modifier = Modifier.fillMaxSize(), contentPadding = look.padding, showLabels = look.showLabels, sounds = look.sounds, gestures = look.gestures,
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
    /** A work profile exists: the list can be narrowed to Personal or Work apps (work apps are badged either way). */
    hasWork: Boolean = false,
    profile: RecentsProfile = RecentsProfile.Both,
    onProfile: (RecentsProfile) -> Unit = {},
    topInset: androidx.compose.ui.unit.Dp = 0.dp,
) {
    Box(modifier.fillMaxSize()) {
        val body: @Composable (LandingLook) -> Unit = { l ->
            if (recents.isEmpty()) {
                // The gesture layer still covers the empty home (long press opens the quick sheet).
                Box(Modifier.fillMaxSize().homeGestures(l.gestures) { true })
                RecentsEmptyState(usageAccessOn, onGrantUsage, Modifier.align(Alignment.Center).safeDrawingPadding())
            } else {
                LandingList(recents, l, ordered = true, onLaunch = onLaunch, onLongPress = onLongPress)
            }
        }
        if (!hasWork) body(look) else {
            val padL = look.padding.calculateLeftPadding(LayoutDirection.Ltr)
            val padR = look.padding.calculateRightPadding(LayoutDirection.Ltr)
            val listLook = remember(look, padL, padR) {
                LandingLook(look.style, look.columns, look.scrollHorizontal, PaddingValues(start = padL, end = padR, top = 4.dp, bottom = look.padding.calculateBottomPadding()),
                    look.iconScale, look.spacing, look.showLabels, look.sounds, look.gestures, look.categoryOf)
            }
            Column(Modifier.fillMaxSize().safeDrawingPadding().padding(top = topInset)) {
                RecentsProfileChips(profile, onProfile, Modifier.padding(horizontal = padL.coerceAtMost(24.dp), vertical = 4.dp))
                Box(Modifier.fillMaxWidth().weight(1f)) { body(listLook) }
            }
        }
    }
}

/** HOM-22: the Recent apps landing's Personal / Work filter (only drawn when a work profile exists). */
@Composable
internal fun RecentsProfileChips(selected: RecentsProfile, onSelect: (RecentsProfile) -> Unit, modifier: Modifier = Modifier) {
    androidx.compose.foundation.layout.Row(modifier.selectableGroup(), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
        listOf(RecentsProfile.Both to "All", RecentsProfile.PersonalOnly to "Personal", RecentsProfile.WorkOnly to "Work").forEach { (v, label) ->
            val on = selected == v
            Text(
                L10n.tr(label), fontSize = 13.sp, color = if (on) HyleColors.OnAccent else HyleColors.InkDim,
                modifier = Modifier.clip(androidx.compose.foundation.shape.RoundedCornerShape(50))
                    .background(if (on) HyleColors.Violet else HyleColors.InkDim.copy(alpha = 0.14f))
                    .selectable(selected = on, role = androidx.compose.ui.semantics.Role.RadioButton, onClick = { onSelect(v) })
                    .defaultMinSize(minHeight = 48.dp).padding(horizontal = 14.dp, vertical = 14.dp),
            )
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
            look.iconScale, look.spacing, look.showLabels, look.sounds, look.gestures, look.categoryOf)
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
