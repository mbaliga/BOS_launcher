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
 * Files, Usage and Timeline panels that used to open in their own sheet.
 */
enum class HubView(val id: String, val label: String, val history: Boolean) {
    All("all", "All", true), Unread("unread", "Unread", true), Read("read", "Read", true),
    Files("files", "Files", false), Usage("usage", "Usage", false), Timeline("timeline", "Timeline", false);

    companion object {
        fun fromId(id: String?): HubView? = entries.firstOrNull { it.id == id }
        /** A home panel link (`liftoff-panel://files`) lands on the matching hub view. */
        fun forPanel(uriName: String): HubView = fromId(uriName)?.takeIf { !it.history } ?: Files
    }
}

/** Row density: what a pinch changes first. */
enum class HubDensity(val label: String) {
    Compact("Compact"), Comfortable("Comfortable"), Expanded("Expanded");
    val denser: HubDensity? get() = entries.getOrNull(ordinal - 1)
    val looser: HubDensity? get() = entries.getOrNull(ordinal + 1)
}

/** Grouping granularity, coarse to fine: one group per app, per day, per sender. */
enum class HubGrouping(val label: String) {
    App("App"), Day("Day"), Sender("Sender");
    val coarser: HubGrouping? get() = entries.getOrNull(ordinal - 1)
    val finer: HubGrouping? get() = entries.getOrNull(ordinal + 1)
}

enum class HubRange(val label: String, val days: Int) { Any("All time", 0), Today("Today", 1), Week("7 days", 7) }

/** The quick filters of one view. */
data class HubFilters(val app: String? = null, val sender: String? = null, val range: HubRange = HubRange.Any, val unreadOnly: Boolean = false) {
    val active: Boolean get() = this != HubFilters()
}

enum class HubSwipe { Left, Right, Down }

object HubLogic {
    /** Wave 26: day bands always show; an app or sender header only when it groups two or more rows (otherwise it repeats the row). */
    fun showsGroupHeader(g: HubGrouping, count: Int) = g == HubGrouping.Day || count >= 2

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

    /**
     * Pinch rules. Fingers apart ([zoomIn]) makes rows looser (Compact, Comfortable, Expanded); once Expanded, a further pinch out makes
     * the grouping finer (App, Day, Sender). Fingers together does the reverse: denser rows, then coarser grouping.
     */
    fun pinch(d: HubDensity, g: HubGrouping, zoomIn: Boolean): Pair<HubDensity, HubGrouping> =
        if (zoomIn) d.looser?.let { it to g } ?: (d to (g.finer ?: g))
        else d.denser?.let { it to g } ?: (d to (g.coarser ?: g))

    /** Section key and title (title is a function of the entry) for [g]. */
    fun groupKey(e: HistoryEntry, g: HubGrouping, now: Long, zone: ZoneId): String = when (g) {
        HubGrouping.App -> "a:" + e.packageName
        HubGrouping.Sender -> "s:" + e.who
        HubGrouping.Day -> "d:" + Instant.ofEpochMilli(e.timeMillis).atZone(zone).toLocalDate()
    }

    fun stripe(pkg: String): Color {
        val h = ((pkg.hashCode() and 0x7fffffff) % 360).toFloat()
        return Color.hsv(h, 0.62f, 0.86f)
    }
}

/** The persisted hub settings: the default view, the row density and the grouping. */
class HubPrefs(private val sp: SharedPreferences?) {
    var defaultView: HubView
        get() = HubView.fromId(sp?.getString("defaultView", null)) ?: HubView.All
        set(v) { sp?.edit()?.putString("defaultView", v.id)?.apply() }
    var density: HubDensity
        get() = HubDensity.entries.firstOrNull { it.name == sp?.getString("density", null) } ?: HubDensity.Comfortable
        set(v) { sp?.edit()?.putString("density", v.name)?.apply() }
    var grouping: HubGrouping
        get() = HubGrouping.entries.firstOrNull { it.name == sp?.getString("grouping", null) } ?: HubGrouping.Day
        set(v) { sp?.edit()?.putString("grouping", v.name)?.apply() }

    companion object { fun of(context: Context) = HubPrefs(TolerantPrefs.of(context.applicationContext, "launchpad_hub")) }
}

/** A request from elsewhere (a home panel tile, the quick sheet) to show the Launchpad on a given hub view. */
object LaunchpadHubNav {
    val requests = MutableStateFlow<HubView?>(null)
    fun request(view: HubView) { requests.value = view }
    /** Takes the pending request (null when none). */
    fun take(): HubView? = requests.value.also { if (it != null) requests.value = null }
}

/** What an undo puts back. */
class HubUndo(val message: String, val ids: List<String>)

/**
 * The hub's state: the view, the quick filters of each view (kept while the user moves between views), density and grouping
 * (persisted), and the undo of the last mark-read. [panels] says whether Files, Usage and Timeline can be shown (the host supplies them).
 * With [liveAll] the All view is the Launchpad's live grouped list (everything in the shade now, with groups, digests and row actions) and
 * Unread and Read are the persistent history; without it All is the history too.
 */
class HubState(val prefs: HubPrefs, val store: NotificationHistoryStore?, val panels: Boolean, val liveAll: Boolean = false) {
    val views: List<HubView> = HubView.entries.filter { panels || it.history }
    var view by mutableStateOf(prefs.defaultView.takeIf { it in views } ?: HubView.All)
        private set
    var density by mutableStateOf(prefs.density)
        private set
    var grouping by mutableStateOf(prefs.grouping)
        private set
    var defaultView by mutableStateOf(prefs.defaultView)
        private set
    private var optionsOpenState by mutableStateOf(false)
    /** Wave 26 (audit): closing or opening the options always drops a half-made "Delete history" confirmation, so it is never found pending on the next visit. */
    var optionsOpen: Boolean
        get() = optionsOpenState
        set(v) { optionsOpenState = v; confirmDelete = false }
    var confirmDelete by mutableStateOf(false)
    var undo by mutableStateOf<HubUndo?>(null)
    private var filterMap by mutableStateOf(emptyMap<HubView, HubFilters>())

    /** True when the list on screen is the Launchpad's own live grouped list: the All view of a hub whose [liveAll] is set. */
    val listIsLive: Boolean get() = liveAll && view == HubView.All

    fun filters(v: HubView = view): HubFilters = filterMap[v] ?: HubFilters()
    fun setFilters(f: HubFilters, v: HubView = view) { filterMap = filterMap + (v to f) }
    fun clearFilters() = setFilters(HubFilters())

    fun select(v: HubView) { if (v in views) { view = v; undo = null } }
    fun swipe(s: HubSwipe, markVisibleRead: () -> Unit) {
        when (s) {
            HubSwipe.Left -> HubLogic.step(views, view, +1)?.let(::select)
            HubSwipe.Right -> HubLogic.step(views, view, -1)?.let(::select)
            HubSwipe.Down -> if (view.history) markVisibleRead()
        }
    }

    fun setDefault(v: HubView = view) { defaultView = v; prefs.defaultView = v }
    fun chooseDensity(d: HubDensity) { density = d; prefs.density = d }
    fun chooseGrouping(g: HubGrouping) { grouping = g; prefs.grouping = g }
    fun pinch(zoomIn: Boolean) {
        if (!view.history || listIsLive) return
        val (d, g) = HubLogic.pinch(density, grouping, zoomIn)
        if (d != density) chooseDensity(d)
        if (g != grouping) chooseGrouping(g)
    }
}
