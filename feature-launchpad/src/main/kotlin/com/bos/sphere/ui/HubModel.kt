package com.bos.sphere.ui

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import com.bos.sphere.core.data.HistoryEntry
import com.bos.sphere.core.data.NotificationHistoryStore
import com.bos.sphere.core.data.TolerantPrefs
import kotlinx.coroutines.flow.MutableStateFlow
import java.time.Instant
import java.time.ZoneId

/**
 * Wave 25 (hubData, owner item 2): the Launchpad hub. One place with six views: the notification history (All, Unread, Read) and the
 * Files and Timeline panels (Usage is part of Timeline since wave 28) that used to open in their own sheet.
 */
enum class HubView(val id: String, val label: String, val history: Boolean) {
    All("all", "All", true), Unread("unread", "Unread", true), Read("read", "Read", true),
    Files("files", "Files", false), Timeline("timeline", "Timeline", false),
    /** Wave 52: the Widgets section, the fifth tab; only present when the owner turns it on ([PanelSections]). */
    Widgets("widgets", "Widgets", false);

    companion object {
        /** Wave 28: the Usage view was merged into Timeline; a stored or linked "usage" lands on Timeline. */
        const val LEGACY_USAGE = "usage"
        fun fromId(id: String?): HubView? = if (id == LEGACY_USAGE) Timeline else entries.firstOrNull { it.id == id }
        /** A home panel link (`liftoff-panel://files`) lands on the matching hub view; `usage` opens Timeline, scrolled to the top where its usage block is. */
        fun forPanel(uriName: String): HubView = fromId(uriName)?.takeIf { !it.history } ?: Files
    }
}

/**
 * Grouping granularity, coarse to fine: no groups at all (the Comms default: a flat list, no bands), then one group per app, per day, per sender.
 * Wave 27: the Comms menu's GROUP BY grid drives it on the history views ([of] and [toMode]).
 */
enum class HubGrouping(val label: String) {
    Flat("Flat"), App("App"), Day("Day"), Sender("Sender");
    val coarser: HubGrouping? get() = entries.getOrNull(ordinal - 1)
    val finer: HubGrouping? get() = entries.getOrNull(ordinal + 1)
    /** The grid cell that shows this grouping as selected. */
    fun toMode(): com.bos.sphere.core.data.GroupMode = when (this) {
        Flat -> com.bos.sphere.core.data.GroupMode.Flat; App -> com.bos.sphere.core.data.GroupMode.App
        Day -> com.bos.sphere.core.data.GroupMode.Time; Sender -> com.bos.sphere.core.data.GroupMode.Sender
    }
    companion object {
        /** History has no conversations of its own: a conversation groups by sender, app and conversation by app. */
        fun of(m: com.bos.sphere.core.data.GroupMode): HubGrouping = when (m) {
            com.bos.sphere.core.data.GroupMode.Flat -> Flat
            com.bos.sphere.core.data.GroupMode.App, com.bos.sphere.core.data.GroupMode.AppConversation -> App
            com.bos.sphere.core.data.GroupMode.Time -> Day
            com.bos.sphere.core.data.GroupMode.Sender, com.bos.sphere.core.data.GroupMode.Conversation -> Sender
        }
    }
}

enum class HubRange(val label: String, val days: Int) { Any("All time", 0), Today("Today", 1), Week("7 days", 7) }

/** The quick filters of one view. */
data class HubFilters(val app: String? = null, val sender: String? = null, val range: HubRange = HubRange.Any, val unreadOnly: Boolean = false) {
    val active: Boolean get() = this != HubFilters()
}

enum class HubSwipe { Left, Right, Down }

object HubLogic {
    /** Wave 26: day bands always show; an app or sender header only when it groups two or more rows (otherwise it repeats the row). */
    fun showsGroupHeader(g: HubGrouping, count: Int) = g != HubGrouping.Flat && (g == HubGrouping.Day || count >= 2)

    /** The history rows of [view], newest first, with its quick filters applied. */
    fun query(entries: List<HistoryEntry>, view: HubView, f: HubFilters, now: Long, zone: ZoneId): List<HistoryEntry> {
        if (!view.history) return emptyList()
        val since = if (f.range == HubRange.Any) Long.MIN_VALUE
        else Instant.ofEpochMilli(now).atZone(zone).toLocalDate().minusDays((f.range.days - 1).toLong()).atStartOfDay(zone).toInstant().toEpochMilli()
        return entries.filter { e ->
            (view != HubView.Unread || !e.read) && (view != HubView.Read || e.read) &&
                (!f.unreadOnly || !e.read) && e.timeMillis >= since &&
                (f.app == null || e.packageName == f.app) && (f.sender == null || e.who == f.sender)
        }.sortedByDescending { it.timeMillis }
    }

    /** Apps by number of entries (name and label), for the app chips. */
    fun topApps(entries: List<HistoryEntry>, n: Int = 6): List<Pair<String, String>> =
        entries.groupBy { it.packageName }.entries.sortedByDescending { it.value.size }.take(n).map { (pkg, l) -> pkg to l.first().appLabel.ifBlank { pkg } }

    fun topSenders(entries: List<HistoryEntry>, n: Int = 6): List<String> =
        entries.groupBy { it.who }.entries.sortedByDescending { it.value.size }.take(n).map { it.key }

    /** The view [delta] steps from [cur] among [views], stopping at the ends (a swipe at the end does nothing). */
    fun step(views: List<HubView>, cur: HubView, delta: Int): HubView? =
        views.getOrNull(views.indexOf(cur) + delta)

    /** Section key and title (title is a function of the entry) for [g]. */
    fun groupKey(e: HistoryEntry, g: HubGrouping, now: Long, zone: ZoneId): String = when (g) {
        HubGrouping.App -> "a:" + e.packageName
        HubGrouping.Sender -> "s:" + e.who
        HubGrouping.Flat -> "flat"
        HubGrouping.Day -> "d:" + Instant.ofEpochMilli(e.timeMillis).atZone(zone).toLocalDate()
    }

    fun stripe(pkg: String): Color {
        val h = ((pkg.hashCode() and 0x7fffffff) % 360).toFloat()
        return Color.hsv(h, 0.62f, 0.86f)
    }
}

/** The persisted hub settings: the default view and the grouping (wave 28: the row density is gone, the Comms size replaces it). */
class HubPrefs(private val sp: SharedPreferences?) {
    var defaultView: HubView
        get() = (sp?.getString("defaultView", null)).let { raw ->
            // Wave 28: a stored default of the removed Usage view becomes Timeline, and is rewritten so the migration runs once.
            HubView.fromId(raw)?.also { if (raw == HubView.LEGACY_USAGE) sp?.edit()?.putString("defaultView", it.id)?.apply() } ?: HubView.All
        }
        set(v) { sp?.edit()?.putString("defaultView", v.id)?.apply(); CommsOptions.mirror(v) }
    var grouping: HubGrouping
        get() = HubGrouping.entries.firstOrNull { it.name == sp?.getString("grouping", null) } ?: HubGrouping.Flat
        set(v) { sp?.edit()?.putString("grouping", v.name)?.apply() }

    /** W42: Dismiss in Comms also clears the notification from the system shade (default on, as before). Off: it is only marked read and the shade keeps it. */
    var clearFromShade: Boolean
        get() = sp?.getBoolean("clearFromShade", true) ?: true
        set(v) { sp?.edit()?.putBoolean("clearFromShade", v)?.apply(); CommsOptions.mirrorClear(v) }

    /** Wave 52 (panelWidgets): the Widgets section exists only when this is on; default off, so nothing changes until the owner chooses. */
    var widgetsSection: Boolean
        get() = sp?.getBoolean("widgetsSection", false) ?: false
        set(v) { sp?.edit()?.putBoolean("widgetsSection", v)?.apply(); CommsOptions.mirrorWidgets(v, widgetsSlot) }
    /** Where the Widgets tab sits in the row. */
    var widgetsSlot: WidgetsSlot
        get() = WidgetsSlot.fromId(sp?.getString("widgetsSlot", null))
        set(v) { sp?.edit()?.putString("widgetsSlot", v.id)?.apply(); CommsOptions.mirrorWidgets(widgetsSection, v) }
    private var memoryWidgets: List<PanelWidget> = emptyList()
    /** The widgets of the section, in order (see [PanelWidgetList.encode]). */
    var widgetList: List<PanelWidget>
        get() = if (sp == null) memoryWidgets else PanelWidgetList.decode(sp.getString("widgets", null))
        set(v) { memoryWidgets = v; sp?.edit()?.putString("widgets", PanelWidgetList.encode(v))?.apply() }

    companion object { fun of(context: Context) = HubPrefs(TolerantPrefs.of(context.applicationContext, "launchpad_hub")) }
}

/**
 * A request from elsewhere (a home panel tile, the quick sheet, Ground Control's Comms rows) to show the Launchpad on a given hub view, and
 * (wave 29) to open its Groups and rules manager. [openSignal] tells the host to bring Comms up; the hub consumes [requests] and [groups].
 */
object LaunchpadHubNav {
    val requests = MutableStateFlow<HubView?>(null)
    val groups = MutableStateFlow(false)
    /** Set by every "open Comms" from outside; the host opens the surface and clears it, the hub then picks up the pending view or manager. */
    val openSignal = MutableStateFlow(false)
    fun request(view: HubView) { requests.value = view }
    /** Takes the pending request (null when none). */
    fun take(): HubView? = requests.value.also { if (it != null) requests.value = null }
    /** Ground Control: open Comms on [view] and/or with the Groups and rules manager open. */
    fun openComms(view: HubView? = null, groupsManager: Boolean = false) {
        if (view != null) requests.value = view
        if (groupsManager) groups.value = true
        openSignal.value = true
    }
    fun takeGroups(): Boolean = groups.value.also { if (it) groups.value = false }
}

/**
 * Wave 29: the Comms options that used to live in the Comms menu panel, as plain operations for Ground Control. One source of truth: the same
 * stores as before (the hub's own pref file, the history store, the notification feed); the hub reads them live when Comms opens.
 */
object CommsOptions {
    private val _defaultView = MutableStateFlow(HubView.All)
    val defaultView: kotlinx.coroutines.flow.StateFlow<HubView> = _defaultView
    @Volatile private var prefs: HubPrefs? = null
    @Volatile private var appContext: Context? = null
    /** True once [init] has published the stored values; before that the flows hold defaults a freshly built hub must not be overwritten with. */
    val isInitialised: Boolean get() = prefs != null

    /** Opens the hub's pref file once (idempotent) and publishes the stored default view. */
    fun init(context: Context) {
        appContext = context.applicationContext
        prefs = HubPrefs.of(context) // rebound on every init: a fresh process or test context owns its own file handle
        _defaultView.value = prefs!!.defaultView
        _clearFromShade.value = prefs!!.clearFromShade
        _widgetsSection.value = prefs!!.widgetsSection
        _widgetsSlot.value = prefs!!.widgetsSlot
    }
    private val _clearFromShade = MutableStateFlow(true)
    /** W42: whether Dismiss in Comms also removes the notification from the shade. */
    val clearFromShade: kotlinx.coroutines.flow.StateFlow<Boolean> = _clearFromShade
    internal fun mirrorClear(v: Boolean) { _clearFromShade.value = v }
    fun setClearFromShade(v: Boolean) { val p = prefs; if (p != null) p.clearFromShade = v else _clearFromShade.value = v }
    internal fun mirror(v: HubView) { _defaultView.value = v }

    private val _widgetsSection = MutableStateFlow(false)
    private val _widgetsSlot = MutableStateFlow(WidgetsSlot.Last)
    /** Wave 52: whether the Widgets section exists, and where; the Comms settings show and set them, an open hub follows. */
    val widgetsSection: kotlinx.coroutines.flow.StateFlow<Boolean> = _widgetsSection
    val widgetsSlot: kotlinx.coroutines.flow.StateFlow<WidgetsSlot> = _widgetsSlot
    internal fun mirrorWidgets(on: Boolean, slot: WidgetsSlot) { _widgetsSection.value = on; _widgetsSlot.value = slot }
    fun setWidgetsSection(v: Boolean) { val p = prefs; if (p != null) p.widgetsSection = v else _widgetsSection.value = v }
    fun setWidgetsSlot(v: WidgetsSlot) { val p = prefs; if (p != null) p.widgetsSlot = v else _widgetsSlot.value = v }
    /** The views the Default view choice offers: the Widgets section only while it exists. */
    fun selectableViews(): List<HubView> = HubView.entries.filter { it != HubView.Widgets || _widgetsSection.value }
    fun setDefaultView(v: HubView) { val p = prefs; if (p != null) p.defaultView = v else _defaultView.value = v }

    /** Every live notification and every history entry becomes read. */
    fun markAllRead() {
        com.bos.sphere.core.data.NotificationProvider.snapshot.forEach { com.bos.sphere.core.data.LaunchpadSettings.markRead(it.key) }
        com.bos.sphere.core.data.NotificationHistory.get()?.markAllRead()
    }
    /** What the menu's "Clear all" did: dismiss everything in the live list. */
    fun clearAll() = clearer()
    /** The dismissal behind [clearAll]; a seam for tests (the real listener is not bound under Robolectric). */
    @Volatile var clearer: () -> Unit = { com.bos.sphere.core.data.NotificationProvider.clearAll() }
    /** "Delete history", after its confirmation: the stored history goes, the notifications in the shade stay. */
    fun deleteHistory() { com.bos.sphere.core.data.NotificationHistory.get()?.deleteAll() }
}

/** What an undo puts back. */
class HubUndo(val message: String, val ids: List<String>)

/**
 * The hub's state: the view, the quick filters of each view (kept while the user moves between views) and grouping
 * (persisted), and the undo of the last mark-read. [panels] says whether Files and Timeline can be shown (the host supplies them).
 * With [liveAll] the All view is the Launchpad's live grouped list (everything in the shade now, with groups, digests and row actions) and
 * Unread and Read are the persistent history; without it All is the history too.
 */
class HubState(val prefs: HubPrefs, val store: NotificationHistoryStore?, val panels: Boolean, val liveAll: Boolean = false) {
    /** Wave 52: the Widgets section's switch and place; [applyWidgets] changes them while the hub is open. */
    var widgetsOn by mutableStateOf(prefs.widgetsSection)
        private set
    var widgetsSlot by mutableStateOf(prefs.widgetsSlot)
        private set
    val views: List<HubView> get() = HubView.entries.filter { (panels || it.history) && (it != HubView.Widgets || (panels && widgetsOn)) }
    var view by mutableStateOf(prefs.defaultView.takeIf { it in views } ?: HubView.All)
        private set
    var grouping by mutableStateOf(prefs.grouping)
        private set
    var defaultView by mutableStateOf(prefs.defaultView)
        private set
    /** Wave 27: the tab row has no Read tab (the tabs are ALL UNREAD FILES TIMELINE); Read history is a row of Ground Control's Comms category. */
    val tabs: List<HubView> get() = PanelSections.order(widgetsOn, widgetsSlot).filter { it in views }
    /** The Widgets section was turned on or off, or moved; leaving it while it is shown falls back to All. */
    fun applyWidgets(on: Boolean, slot: WidgetsSlot) {
        widgetsOn = on; widgetsSlot = slot
        if (view !in views) view = HubView.All
    }
    var undo by mutableStateOf<HubUndo?>(null)
    /** Wave 29: the read filter of the All view (Unread - All - Read); the pinch, the TalkBack actions and the label's tap set it. */
    var readFilter by mutableStateOf(ReadFilter.All)
        private set
    fun chooseReadFilter(f: ReadFilter) { readFilter = f }
    /** One pinch step (see [CommsRead.step]); true when something changed. */
    fun pinch(dir: PinchDir): Boolean {
        val (v, f) = CommsRead.step(view, readFilter, dir) ?: return false
        if (v in views) { view = v; undo = null }
        readFilter = f
        return true
    }
    private var filterMap by mutableStateOf(emptyMap<HubView, HubFilters>())

    /** Wave 33 (commsDebt): what survives the process being killed while Comms is open: the tab and the read filter (the list scroll is saved by its own state). */
    fun savedState(): List<String> = listOf(view.id, readFilter.name)
    fun restoreState(saved: List<String>) {
        HubView.fromId(saved.getOrNull(0))?.takeIf { it in views }?.let { view = it }
        ReadFilter.entries.firstOrNull { it.name == saved.getOrNull(1) }?.let { readFilter = it }
    }

    /** True when the list on screen is the Launchpad's own live grouped list: the All view of a hub whose [liveAll] is set. */
    val listIsLive: Boolean get() = liveAll && view == HubView.All

    fun filters(v: HubView = view): HubFilters = filterMap[v] ?: HubFilters()
    fun setFilters(f: HubFilters, v: HubView = view) { filterMap = filterMap + (v to f) }
    fun clearFilters() = setFilters(HubFilters())

    fun select(v: HubView) { if (v in views) { view = v; undo = null } }
    /** The tab [delta] steps away; from the Read history (which has no tab) next is Files and previous is Unread. */
    fun step(delta: Int): HubView? {
        if (view == HubView.Read) return if (delta > 0) HubLogic.step(tabs, HubView.Unread, 1) else if (HubView.Unread in tabs) HubView.Unread else null
        return HubLogic.step(tabs, view, delta)
    }
    fun swipe(s: HubSwipe, markVisibleRead: () -> Unit) {
        when (s) {
            HubSwipe.Left -> step(+1)?.let(::select)
            HubSwipe.Right -> step(-1)?.let(::select)
            HubSwipe.Down -> if (view.history) markVisibleRead()
        }
    }

    fun setDefault(v: HubView = view) { defaultView = v; prefs.defaultView = v }
    fun chooseGrouping(g: HubGrouping) { grouping = g; prefs.grouping = g }
}

/** Wave 33: the saver of [HubState] for `rememberSaveable`; [make] builds a fresh state, which then takes the saved tab and filter. */
fun hubStateSaver(make: () -> HubState): androidx.compose.runtime.saveable.Saver<HubState, Any> = androidx.compose.runtime.saveable.listSaver(
    save = { it.savedState() },
    restore = { make().also { h -> h.restoreState(it.map { v -> v.toString() }) } },
)
