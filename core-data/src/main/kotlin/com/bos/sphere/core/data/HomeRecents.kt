package com.bos.sphere.core.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import java.io.File
import java.util.concurrent.Executor
import java.util.concurrent.Executors

/**
 * One sighting of an app from the system's usage log (needs usage access); plain data. [background] marks an event of the app going to
 * the background (ACTIVITY_PAUSED, MOVE_TO_BACKGROUND), as opposed to coming to the foreground (STR-14); both place the app in time.
 */
data class UsageSighting(val packageName: String, val timeMillis: Long, val background: Boolean = false)

/** STR-14: one row of the Recents panel: the app key and when it was last seen (launched by Liftoff, or last seen going to the background). */
data class RecentEntry(val key: String, val timeMillis: Long)

/** The apps the Recent landing may show, as plain data: [key] is [AppEntry.key]. */
data class RecentCandidate(val key: String, val packageName: String, val workProfile: Boolean)

/** Which profile the Recent landing lists. */
enum class RecentsProfile { Both, PersonalOnly, WorkOnly }

/**
 * HOM-22: what the user removed from the Recent landing. Both rules compare times, so an app that is launched again after it
 * was removed or cleared comes back by itself: [clearedAt] hides everything at or before it, [removedAt] hides one app.
 */
data class RecentsState(val clearedAt: Long = 0L, val removedAt: Map<String, Long> = emptyMap()) {
    fun hides(key: String, time: Long): Boolean = time <= clearedAt || time <= (removedAt[key] ?: Long.MIN_VALUE)
}

/** HOM-22: the pure rules of the Recent landing. */
object HomeRecents {
    const val MIN_LIMIT = 8
    const val MAX_LIMIT = 48
    const val DEFAULT_LIMIT = 24

    fun clampLimit(n: Int): Int = n.coerceIn(MIN_LIMIT, MAX_LIMIT)

    /**
     * The recent app keys, newest first, one per app, at most [limit].
     *
     * Sources: Liftoff's own launches ([own], by app key) and, when the user granted usage access, the foreground sightings
     * ([usage], by package). A sighting is resolved to the installed app of that package that Liftoff launched most
     * recently, else the first one in the personal profile, else the first one. Apps that are not installed, are in
     * [hidden], are Liftoff itself, or fall outside [profile] never appear. [state] holds removals and clears.
     * [runningFirst] (a Liftoff-launched app still in a session) moves to the front only if it is already in the list: it
     * never adds an app and never reorders the others.
     */
    fun compute(
        own: List<LaunchEvent>,
        usage: List<UsageSighting>,
        apps: List<RecentCandidate>,
        hidden: Set<String>,
        state: RecentsState = RecentsState(),
        limit: Int = DEFAULT_LIMIT,
        profile: RecentsProfile = RecentsProfile.Both,
        runningFirst: String? = null,
        selfPackage: String = "",
    ): List<String> {
        val ordered = entries(own, usage, apps, hidden, state, limit, profile, selfPackage).map { it.key }
        return if (runningFirst != null && runningFirst in ordered) listOf(runningFirst) + (ordered - runningFirst) else ordered
    }

    /**
     * STR-14: the same list as [compute] with the time each app was last seen, newest first, one entry per app. This is the ONE source
     * of the Recent apps landing (HOM-22) and the Recents panel (STR-14). An app's time is the latest of its Liftoff launches and its
     * usage events (foreground or background), so an app opened from a notification and then left sorts by when it went to the background.
     */
    fun entries(
        own: List<LaunchEvent>,
        usage: List<UsageSighting>,
        apps: List<RecentCandidate>,
        hidden: Set<String>,
        state: RecentsState = RecentsState(),
        limit: Int = DEFAULT_LIMIT,
        profile: RecentsProfile = RecentsProfile.Both,
        selfPackage: String = "",
    ): List<RecentEntry> {
        val byKey = apps.associateBy { it.key }
        val byPackage = apps.groupBy { it.packageName }
        val lastOwn = HashMap<String, Long>()
        for (e in own) if (e.timeMillis >= (lastOwn[e.key] ?: Long.MIN_VALUE)) lastOwn[e.key] = e.timeMillis

        fun resolve(pkg: String): RecentCandidate? {
            val all = byPackage[pkg] ?: return null
            return all.filter { it.key in lastOwn }.maxByOrNull { lastOwn.getValue(it.key) }
                ?: all.firstOrNull { !it.workProfile } ?: all.first()
        }

        val seen = ArrayList<Pair<String, Long>>(own.size + usage.size)
        for (e in own) seen += e.key to e.timeMillis
        for (u in usage) resolve(u.packageName)?.let { seen += it.key to u.timeMillis }

        val best = HashMap<String, Long>()
        for ((k, t) in seen) {
            val app = byKey[k] ?: continue
            if (k in hidden || app.packageName == selfPackage && selfPackage.isNotEmpty()) continue
            if (profile == RecentsProfile.PersonalOnly && app.workProfile) continue
            if (profile == RecentsProfile.WorkOnly && !app.workProfile) continue
            if (state.hides(k, t)) continue
            if (t >= (best[k] ?: Long.MIN_VALUE)) best[k] = t
        }
        return best.entries.sortedWith(compareByDescending<Map.Entry<String, Long>> { it.value }.thenBy { it.key })
            .take(clampLimit(limit)).map { RecentEntry(it.key, it.value) }
    }

    /**
     * The live list. Everything is combined and computed on [Dispatchers.Default], so collecting it from the main thread
     * never does the work there; it emits only when the list changes. [usage] is read off the main thread whenever
     * [usageTick] emits (the caller re-emits when the home resumes or access is granted).
     */
    fun flow(
        own: Flow<List<LaunchEvent>>,
        usageTick: Flow<Unit>,
        usageReader: () -> List<UsageSighting>,
        apps: Flow<List<RecentCandidate>>,
        hidden: Flow<Set<String>>,
        state: Flow<RecentsState>,
        limit: Flow<Int>,
        profile: Flow<RecentsProfile>,
        useUsage: Flow<Boolean>,
        runningFirst: () -> String? = { null },
        selfPackage: String = "",
    ): Flow<List<String>> {
        val sightings: Flow<List<UsageSighting>> = combine(usageTick, useUsage) { _, on -> if (on) runCatching { usageReader() }.getOrDefault(emptyList()) else emptyList() }
        return combine(own, sightings, apps, hidden, state) { o, u, a, h, s -> Inputs(o, u, a, h, s) }
            .combine(combine(limit, profile) { l, p -> l to p }) { i, (l, p) ->
                compute(i.own, i.usage, i.apps, i.hidden, i.state, l, p, runningFirst(), selfPackage)
            }
            .distinctUntilChanged()
            .catch { emit(emptyList()) } // wave 18: a failing source is an empty list, never an exception out of the home's first composition
            .flowOn(Dispatchers.Default)
    }

    /** STR-14: [flow] with the time of each entry (the Recents panel shows "how long ago"). Same inputs, same rules, same thread. */
    fun entriesFlow(
        own: Flow<List<LaunchEvent>>,
        usageTick: Flow<Unit>,
        usageReader: () -> List<UsageSighting>,
        apps: Flow<List<RecentCandidate>>,
        hidden: Flow<Set<String>>,
        state: Flow<RecentsState>,
        limit: Flow<Int>,
        profile: Flow<RecentsProfile>,
        useUsage: Flow<Boolean>,
        selfPackage: String = "",
    ): Flow<List<RecentEntry>> {
        val sightings: Flow<List<UsageSighting>> = combine(usageTick, useUsage) { _, on -> if (on) runCatching { usageReader() }.getOrDefault(emptyList()) else emptyList() }
        return combine(own, sightings, apps, hidden, state) { o, u, a, h, s -> Inputs(o, u, a, h, s) }
            .combine(combine(limit, profile) { l, p -> l to p }) { i, (l, p) -> entries(i.own, i.usage, i.apps, i.hidden, i.state, l, p, selfPackage) }
            .distinctUntilChanged()
            .catch { emit(emptyList()) } // wave 18: as above
            .flowOn(Dispatchers.Default)
    }

    private class Inputs(val own: List<LaunchEvent>, val usage: List<UsageSighting>, val apps: List<RecentCandidate>, val hidden: Set<String>, val state: RecentsState)
}

/** HOM-22: the codec of the removals file: `cleared <ms>` then one `removed <ms> <key>` per line. Bounded, tolerant of damaged lines. */
object RecentsStateCodec {
    const val MAX_REMOVED = 200

    fun encode(s: RecentsState): String = buildString {
        append("cleared\t").append(s.clearedAt).append('\n')
        s.removedAt.entries.sortedByDescending { it.value }.take(MAX_REMOVED).forEach {
            append("removed\t").append(it.value).append('\t').append(it.key.replace('\t', ' ').replace('\n', ' ')).append('\n')
        }
    }

    fun decode(text: String): RecentsState {
        var cleared = 0L
        val removed = LinkedHashMap<String, Long>()
        for (line in text.lineSequence()) {
            val p = line.split('\t', limit = 3)
            when (p.firstOrNull()) {
                "cleared" -> p.getOrNull(1)?.toLongOrNull()?.let { cleared = it }
                "removed" -> {
                    val t = p.getOrNull(1)?.toLongOrNull(); val k = p.getOrNull(2)
                    if (t != null && !k.isNullOrEmpty()) removed[k] = maxOf(t, removed[k] ?: Long.MIN_VALUE)
                }
            }
        }
        return RecentsState(cleared, removed)
    }

    /**
     * A removal or clear stamped after [now] was written while the clock was ahead (a wrong date since corrected): it would hide every launch
     * until the clock caught up, so on load it is pulled back to [now]. Launches seen since then are newer than it and show again.
     */
    fun clampTo(s: RecentsState, now: Long): RecentsState =
        RecentsState(minOf(s.clearedAt, now), s.removedAt.mapValues { minOf(it.value, now) })

    /** Removals at or before a clear are redundant; dropping them keeps the file small. */
    fun tidy(s: RecentsState): RecentsState =
        s.copy(removedAt = s.removedAt.filterValues { it > s.clearedAt }.entries.sortedByDescending { it.value }.take(MAX_REMOVED).associate { it.key to it.value })
}

/**
 * HOM-22: the removals of the Recent landing ("Remove from recents", "Clear recents"). A small private file under
 * `files/usage` (listed in [StateSchema.NEVER_EXPORTED_FILES], so it is never exported, never in system backup). Reading and
 * writing run on [io]; [state] changes at once.
 */
class RecentsStateStore(private val file: File, private val io: Executor = SHARED_IO, private val clock: () -> Long = System::currentTimeMillis) {
    private val lock = Any()
    private val _state = MutableStateFlow(RecentsState())
    val state: StateFlow<RecentsState> = _state.asStateFlow()
    private var loaded = false
    private var pendingOps = ArrayList<(RecentsState) -> RecentsState>()

    init {
        io.execute {
            val read = runCatching { if (file.exists()) RecentsStateCodec.decode(file.readText()) else RecentsState() }.getOrDefault(RecentsState())
            synchronized(lock) {
                var s = RecentsStateCodec.clampTo(read, clock())
                for (op in pendingOps) s = op(s)
                val changed = pendingOps.isNotEmpty()
                pendingOps = ArrayList()
                loaded = true
                s = RecentsStateCodec.tidy(s)
                _state.value = s
                if (changed) write(s)
            }
        }
    }

    /** "Remove from recents": [key] stays out until it is launched again after [now]. */
    fun remove(key: String, now: Long = System.currentTimeMillis()) = change { it.copy(removedAt = it.removedAt + (key to now)) }

    /** "Clear recents": everything launched up to [now] is hidden. */
    fun clear(now: Long = System.currentTimeMillis()) = change { RecentsState(clearedAt = now) }

    private fun change(op: (RecentsState) -> RecentsState) {
        synchronized(lock) {
            if (!loaded) pendingOps += op
            _state.value = RecentsStateCodec.tidy(op(_state.value))
        }
        io.execute { synchronized(lock) { if (loaded) write(_state.value) } }
    }

    private fun write(s: RecentsState) {
        runCatching {
            file.parentFile?.mkdirs()
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(RecentsStateCodec.encode(s))
            if (!tmp.renameTo(file)) { file.writeText(tmp.readText()); tmp.delete() }
        }
    }

    companion object {
        const val RELATIVE_PATH = "usage/recents-state.txt"
        private val SHARED_IO: Executor by lazy { Executors.newSingleThreadExecutor { r -> Thread(r, "recents-state").apply { isDaemon = true } } }
    }
}
