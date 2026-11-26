package com.bos.sphere.feature.grid

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import com.bos.sphere.core.data.Corner
import com.bos.sphere.core.data.HomeGesture
import com.bos.sphere.core.design.L10n

/**
 * Wave 57 (previewAll), CFG-50: what the floating Preview window shows. Each settings page, section or row declares (or inherits) one topic;
 * the window renders a live picture of that surface. [None] hides the window (a page with nothing visual to configure).
 */
enum class PreviewTopic(val label: String) {
    Home("Home"), Glance("Glance"), Corners("Corners"), Gestures("Gestures"), Dock("Dock"), Radar("Radar"), Disc("App drawer"), SidePanel("Side panel"), Look("Look"), None("None");

    /** Whether the window draws anything for this topic. */
    val visual: Boolean get() = this != None
}

/** A topic plus what inside it is being configured: the corner that pulses, the gesture whose hint plays. */
data class PreviewSubject(
    val topic: PreviewTopic, val corner: Corner? = null, val gesture: HomeGesture? = null,
    /** Wave 59: the music line is what is configured (its width): the Glance topic draws it, with a sample title when nothing plays. */
    val music: Boolean = false,
) {
    companion object {
        val HOME = PreviewSubject(PreviewTopic.Home)
        val NONE = PreviewSubject(PreviewTopic.None)
    }
}

/** Declares what the Preview shows while this row is configured (overrides [PreviewTopics]). */
fun <T : SettingItem> T.previewTopic(topic: PreviewTopic, corner: Corner? = null, gesture: HomeGesture? = null, music: Boolean = false): T { meta.previewTopic = PreviewSubject(topic, corner, gesture, music); return this }

/**
 * CFG-50: the one table that maps settings to Preview topics. Resolution of a row, first match wins:
 *  1. the row's own declaration ([previewTopic] on the row, kept in its [ItemMeta]),
 *  2. [ROWS] by the row's stable id ([rowId]), then by its English title,
 *  3. [CATEGORIES] by the English title of the page it is on,
 *  4. for a page nobody listed (a new tab), the topic most of its rows resolve to; with none, [PreviewTopic.None].
 * A corner row (titled with a corner's name, or "<corner> hotspot") adds its corner; a gesture row adds its gesture.
 *
 * Other streams extend the tables at start-up or in their own files with [category] and [row]; nothing else needs to change.
 */
object PreviewTopics {
    /** English category (page or tab) title to its default topic. */
    val CATEGORIES: MutableMap<String, PreviewTopic> = linkedMapOf(
        "Pages" to PreviewTopic.Home,
        "Home screen" to PreviewTopic.Home,
        "Home" to PreviewTopic.Home,
        "Workspaces" to PreviewTopic.Home,
        "Profiles" to PreviewTopic.Home,
        "Look" to PreviewTopic.Look,
        "Theme" to PreviewTopic.Look,
        "Indicator" to PreviewTopic.Glance,
        "App drawer" to PreviewTopic.Disc,
        "Apps" to PreviewTopic.Disc,
        "Desktop & dock" to PreviewTopic.Dock,
        "Corners" to PreviewTopic.Corners,
        "Gestures" to PreviewTopic.Gestures,
        "Motion" to PreviewTopic.Gestures,
        "Radar" to PreviewTopic.Radar,
        OneUiModel.SIDE_PANEL to PreviewTopic.SidePanel,
        "System & sound" to PreviewTopic.None,
        "System" to PreviewTopic.None,
        "Sound" to PreviewTopic.None,
        "Hidden apps" to PreviewTopic.None,
        "Backup" to PreviewTopic.None,
        "Capabilities" to PreviewTopic.None,
        "Lab" to PreviewTopic.None,
        "About" to PreviewTopic.None,
        OneUiModel.FINISH_SETUP to PreviewTopic.None,
    )

    /** Row id or English row title to its topic, where it differs from its page's default (or the row may move to another page). */
    val ROWS: MutableMap<String, PreviewTopic> = linkedMapOf(
        // Home screen: the Glance rows show the Glance surface (fixed backdrop and chrome, the clock at its edited place)
        "Calm home screen (Glance)" to PreviewTopic.Glance,
        "Glance wallpaper" to PreviewTopic.Glance,
        "Pages use the same wallpaper as Glance" to PreviewTopic.Home,
        "Pages wallpaper" to PreviewTopic.Home,
        "Clock style" to PreviewTopic.Glance,
        "Clock format" to PreviewTopic.Glance,
        "Show date under the clock" to PreviewTopic.Glance,
        "Clock colour" to PreviewTopic.Glance,
        "Clock size" to PreviewTopic.Glance,
        "Show music" to PreviewTopic.Glance,
        "Hotspots" to PreviewTopic.Corners, // wave 59: the Hotspots editor (Home > Corners); its selected corner pulses
        "Music line width" to PreviewTopic.Glance,
        "Controls hide after (seconds)" to PreviewTopic.Glance,
        "Surface indicator" to PreviewTopic.Glance,
        "Right side" to PreviewTopic.Home,
        "Keyboard opens with Radar and drawer · portrait" to PreviewTopic.Radar,
        "Keyboard opens with Radar and drawer · landscape" to PreviewTopic.Radar,
        "Restore open screens" to PreviewTopic.None,
        // Look: the calm styles and the glow are Glance effects
        "Calm style" to PreviewTopic.Glance,
        "Also show regular battery and signal" to PreviewTopic.Glance,
        "Glow: intensity" to PreviewTopic.Glance,
        "Glow: speed" to PreviewTopic.Glance,
        "Glow: colours" to PreviewTopic.Glance,
        "Glow: pattern" to PreviewTopic.Glance,
        // Settings itself is what these change: it is already in view, the Preview steps aside
        "Settings backdrop" to PreviewTopic.None,
        "Backdrop dimming" to PreviewTopic.None,
        // App drawer: what lives on the home pages
        "Pages" to PreviewTopic.Home,
        "Open with" to PreviewTopic.None,
        // Side panel: the left page is the Comms panel; its widget page draws the widget, shown as the home
        "Choose widget" to PreviewTopic.SidePanel,
    )

    /** Registers [title] (English) of a page or tab; the six-tab layout adds its tab titles here. */
    fun category(title: String, topic: PreviewTopic) { CATEGORIES[title] = topic }

    /** Registers a row by its id or English title. */
    fun row(idOrTitle: String, topic: PreviewTopic) { ROWS[idOrTitle] = topic }

    private fun lookup(table: Map<String, PreviewTopic>, title: String): PreviewTopic? {
        table[title]?.let { return it }
        for ((k, v) in table) if (L10n.tr(k) == title) return v
        return null
    }

    private fun cornerOf(title: String): Corner? = Corner.entries.firstOrNull { c -> title == L10n.tr(c.label) || title == c.label } ?: hotspotOf(title)

    private fun hotspotOf(title: String): Corner? = Corner.entries.firstOrNull { c -> title == L10n.tr("%s hotspot", L10n.tr(c.label)) || title == "${c.label} hotspot" }

    private fun gestureOf(title: String): HomeGesture? = HomeGesture.entries.firstOrNull { g -> title == L10n.tr(g.label) || title == g.label }

    /** The topic a page shows before any of its rows is touched. */
    fun forCategory(category: SettingCategory?): PreviewSubject {
        if (category == null) return PreviewSubject.HOME // the root list: the home, as before
        lookup(CATEGORIES, category.title)?.let { return PreviewSubject(it) }
        val votes = category.items.map { forRow(null, it).topic }.filter { it.visual }
        val top = votes.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
        return PreviewSubject(top ?: PreviewTopic.None)
    }

    /** The topic of [item] on the page [category] (null when unknown). */
    fun forRow(category: SettingCategory?, item: SettingItem): PreviewSubject {
        item.meta.previewTopic?.let { return it }
        val title = item.title
        val corner = cornerOf(title)
        val gesture = gestureOf(title)
        val topic = item.meta.id?.let { ROWS[it] } ?: lookup(ROWS, title)
            ?: (if (hotspotOf(title) != null) PreviewTopic.Glance else null) // "Top left hotspot": the Glance hotspots, that corner pulsing
            ?: (if (gesture != null) PreviewTopic.Gestures else null)
            ?: category?.let { lookup(CATEGORIES, it.title) ?: forCategory(it).topic }
            ?: PreviewTopic.None
        return PreviewSubject(
            topic,
            corner = corner.takeIf { topic == PreviewTopic.Corners || topic == PreviewTopic.Glance },
            gesture = gesture.takeIf { topic == PreviewTopic.Gestures },
        )
    }
}

/**
 * CFG-50: what Settings is configuring right now, for the Preview window. The settings host writes it (the page from its navigation, the row when
 * one is touched, focused or changed); only the Preview reads it, so a change never recomposes the settings page.
 */
@Stable
class PreviewFocusState {
    private var pageSubject by mutableStateOf(PreviewSubject.HOME)
    private var rowSubject by mutableStateOf<PreviewSubject?>(null)
    private var pageKey: Any? = Unit
    private var category: SettingCategory? = null

    /** What the window shows: the row last touched on this page, else the page's own topic. */
    val subject: PreviewSubject get() = rowSubject ?: pageSubject

    /** The page shown changed (the root list is a null [category]; [sub] is the row whose own page is open, or null). */
    fun onPage(category: SettingCategory?, sub: SettingItem?) {
        this.category = category
        val key = (category?.title ?: "") to (sub?.title ?: "")
        if (key == pageKey) return
        pageKey = key
        rowSubject = null
        pageSubject = if (sub != null) PreviewTopics.forRow(category, sub) else PreviewTopics.forCategory(category)
    }

    /** A row of the current page was touched, focused or changed. */
    fun onRow(item: SettingItem) { rowSubject = PreviewTopics.forRow(category, item) }

    /** Wave 59: a row that knows exactly what it configures (the Hotspots editor's selected corner) says so. */
    fun onSubject(s: PreviewSubject) { rowSubject = s }
}

/** Provided by [SettingsSpace] around the settings and the Preview window; null outside Settings. */
val LocalPreviewFocus = staticCompositionLocalOf<PreviewFocusState?> { null }

/** On a settings row: a touch (seen first, never consumed) or the keyboard focus makes it what the Preview shows. */
internal fun Modifier.previewRow(item: SettingItem): Modifier = composed {
    val focus = LocalPreviewFocus.current ?: return@composed this
    val latest by rememberUpdatedState(item)
    this
        .pointerInput(focus) { awaitEachGesture { awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial); focus.onRow(latest) } }
        .onFocusChanged { if (it.hasFocus) focus.onRow(latest) }
}
