package com.bos.sphere.ui

import com.bos.sphere.core.data.HistoryEntry
import com.bos.sphere.core.data.HubNotification
import com.bos.sphere.core.data.LaunchpadLogic
import com.bos.sphere.core.data.SourceCount

/**
 * Wave 29 (commsRead): the read filter of the Comms list, a three-stop control Unread - All - Read. The pinch steps it
 * (fingers together toward Unread, fingers apart toward Read), the TalkBack actions and the label set it directly.
 */
enum class ReadFilter {
    Unread, All, Read;

    fun towardUnread(): ReadFilter = entries[(ordinal - 1).coerceAtLeast(0)]
    fun towardRead(): ReadFilter = entries[(ordinal + 1).coerceAtMost(entries.size - 1)]
    fun toward(d: PinchDir): ReadFilter = if (d == PinchDir.In) towardUnread() else towardRead()
    /** True when a row with this read state shows under the filter. */
    fun matches(read: Boolean): Boolean = when (this) { Unread -> !read; All -> true; Read -> read }
}

/** [In]: the fingers moved together; [Out]: apart. */
enum class PinchDir { In, Out }

/**
 * The pure rules behind the read filter and the merged Comms list (the live shade plus the persistent history), kept free of Compose so
 * every transition and every merge case is tested on the JVM.
 */
object CommsRead {
    /** The finger spread must change by more than this (8 percent) before a two-finger move counts as a pinch; one step per gesture. */
    const val DEAD_ZONE = 0.08f

    /** The direction of a pinch whose spread is [ratio] times the start spread, or null inside the dead zone (or for garbage). */
    fun direction(ratio: Float): PinchDir? = when {
        ratio.isNaN() || ratio.isInfinite() || ratio <= 0f -> null
        ratio < 1f - DEAD_ZONE -> PinchDir.In
        ratio > 1f + DEAD_ZONE -> PinchDir.Out
        else -> null
    }

    /**
     * The direction of a two-finger move that has changed the spread to [ratio] times its start, with the spread itself having changed by [spreadDelta] px
     * and the pair's centre having travelled [centerTravel] px. A swipe moves the centre far and lets the spread drift a little; a pinch changes the spread
     * by at least as much as the centre moves, so the drift of a swipe is not read as a pinch.
     */
    fun pinchOf(ratio: Float, spreadDelta: Float, centerTravel: Float): PinchDir? =
        if (kotlin.math.abs(spreadDelta) < centerTravel) null else direction(ratio)

    /** The stop the hub is at: the filter on the All view, Unread on the Unread tab, Read on the Read history; null for Files and Timeline. */
    fun stopOf(view: HubView, filter: ReadFilter): ReadFilter? = when (view) {
        HubView.All -> filter
        HubView.Unread -> ReadFilter.Unread
        HubView.Read -> ReadFilter.Read
        else -> null
    }

    /**
     * One pinch step: the view and filter after it, or null when nothing changes (a pinch on Files or Timeline, or already at the end stop).
     * The Unread tab and the Read history are the end stops; pinching away from them lands on the All view with filter All.
     */
    fun step(view: HubView, filter: ReadFilter, dir: PinchDir): Pair<HubView, ReadFilter>? {
        val cur = stopOf(view, filter) ?: return null
        val next = cur.toward(dir)
        if (next == cur) return null
        return HubView.All to next
    }

    /** The result of merging the live feed with the history. */
    class Merged(
        /** Live rows and history-only rows, newest first, one per notification key. */
        val items: List<HubNotification>,
        /** Keys that count as read. */
        val readKeys: Set<String>,
        /** The history entry behind each row that is no longer in the shade (newest entry of its key). */
        val historyOnly: Map<String, HistoryEntry>,
    )

    /** A row for a history entry whose notification left the shade: no intent, no actions. */
    fun fromEntry(e: HistoryEntry): HubNotification = HubNotification(
        id = e.id, key = e.key, source = e.appLabel, title = e.title, summary = e.text, timestamp = e.timeMillis,
        packageName = e.packageName, appLabel = e.appLabel, unread = !e.read,
        sender = e.sender.ifBlank { null }, conversation = e.conversation.ifBlank { null },
        isMessaging = e.sender.isNotBlank() && e.text.isNotBlank(), // history reads text first, then who, as its own rows do
    )

    /**
     * ALL = the live list plus the history, one row per notification key, newest first. A key in the shade is shown as the live row (with all its
     * actions); a key only in history shows its newest entry. A live row is read when the shade list marked it read, or when the history
     * entry of the very same post is read (the shade marks do not survive a restart, the history does).
     */
    fun merge(live: List<HubNotification>, history: Collection<HistoryEntry>, liveRead: Set<String>): Merged {
        val liveKeys = live.mapTo(HashSet()) { it.key }
        val newest = HashMap<String, HistoryEntry>()
        for (e in history) { val c = newest[e.key]; if (c == null || e.timeMillis > c.timeMillis) newest[e.key] = e }
        val only = newest.filterKeys { it !in liveKeys }
        // A key whose older posts were read but whose newest post is unread was updated after the read (a new message in a chat): the live read mark
        // belongs to the old post, so the row is unread again.
        val updatedSinceRead = HashSet<String>()
        for (e in history) { val c = newest[e.key]; if (c != null && e.read && e.timeMillis < c.timeMillis && !c.read) updatedSinceRead += e.key }
        val read = HashSet<String>()
        for (n in live) {
            val h = newest[n.key]
            val updated = h != null && !h.read && h.timeMillis == n.timestamp && n.key in updatedSinceRead
            if ((n.key in liveRead && !updated) || h?.let { it.read && it.timeMillis == n.timestamp } == true) read += n.key
        }
        for (e in only.values) if (e.read) read += e.key
        val items = dropTwins(live, only.values.map(::fromEntry)).sortedByDescending { it.timestamp }
        val kept = items.mapTo(HashSet()) { it.key }
        return Merged(items, read, only.filterKeys { it in kept })
    }

    /** Two posts at most this far apart with the same app, title and text are one notification shown twice (W32). */
    const val TWIN_WINDOW_MS = 60_000L

    /**
     * W32: some apps post the same message under two keys (another profile, a second channel, a re-post), so it showed twice. Rows with the same
     * app, title, text and a post time within [TWIN_WINDOW_MS] collapse into one; a live row wins over a history row, then the newer one.
     */
    internal fun dropTwins(live: List<HubNotification>, hist: List<HubNotification>): List<HubNotification> {
        val liveKeys = live.mapTo(HashSet()) { it.key }
        val out = ArrayList<HubNotification?>()
        val byText = HashMap<Triple<String, String, String>, MutableList<Int>>() // same app, title and text: only these are compared
        for (n in live + hist.sortedByDescending { it.timestamp }) {
            val slots = byText.getOrPut(Triple(n.packageName, n.title, n.summary)) { ArrayList() }
            val twin = slots.firstOrNull { val o = out[it]!!; o.key != n.key && kotlin.math.abs(o.timestamp - n.timestamp) <= TWIN_WINDOW_MS }
            if (twin == null) { slots += out.size; out += n; continue }
            if (n.key in liveKeys && out[twin]!!.key !in liveKeys) out[twin] = n
        }
        return out.filterNotNull()
    }

    fun filter(items: List<HubNotification>, readKeys: Set<String>, f: ReadFilter): List<HubNotification> =
        if (f == ReadFilter.All) items else items.filter { f.matches(it.key in readKeys) }

    /**
     * The Show list of the menu: every app that has a row stays listed. Under a filter the numbers are that filter's counts, so an app whose rows are
     * all read shows 0 under Unread (dimmed by the menu) instead of vanishing. Order is by the unfiltered count, so the list does not jump.
     */
    fun counts(visible: List<HubNotification>, readKeys: Set<String>, f: ReadFilter): List<SourceCount> {
        val all = LaunchpadLogic.countsBySource(visible)
        if (f == ReadFilter.All) return all
        val m = LaunchpadLogic.countsBySource(filter(visible, readKeys, f)).associate { it.packageName to it.count }
        return all.map { it.copy(count = m[it.packageName] ?: 0) }
    }
}
