package com.bos.sphere.core.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

/**
 * Process-wide persisted launcher state. One instance per process so the home activity and the
 * motion-gesture service observe the same live [StateFlow]s. SharedPreferences-backed (like
 * [HiddenAppsStore]) — no extra dependencies.
 */
class LauncherStores private constructor(context: Context) {
    private val app = context.applicationContext

    val prefs = LauncherPrefsStore(app.getSharedPreferences("launcher_prefs", Context.MODE_PRIVATE))
    val layout = HomeLayoutStore(app.getSharedPreferences("home_layout", Context.MODE_PRIVATE))
    val shortcuts = ShortcutStore(app.getSharedPreferences("shortcuts", Context.MODE_PRIVATE))
    val history = LaunchHistoryStore(app.getSharedPreferences("launch_history", Context.MODE_PRIVATE))

    companion object {
        @Volatile private var instance: LauncherStores? = null

        fun get(context: Context): LauncherStores =
            instance ?: synchronized(this) { instance ?: LauncherStores(context).also { instance = it } }
    }
}

class LauncherPrefsStore internal constructor(private val sp: SharedPreferences) {
    private val _prefs = MutableStateFlow(read())
    val prefs: StateFlow<LauncherPrefs> = _prefs.asStateFlow()

    fun update(transform: (LauncherPrefs) -> LauncherPrefs) {
        val next = transform(_prefs.value).let {
            it.copy(
                portraitGrid = it.portraitGrid.coerced(),
                landscapeGrid = it.landscapeGrid.coerced(),
                iconScale = it.iconScale.coerceIn(LauncherPrefs.MIN_ICON_SCALE, LauncherPrefs.MAX_ICON_SCALE),
                motionSensitivity = it.motionSensitivity.coerceIn(0f, 1f),
                uiSoundVolume = it.uiSoundVolume.coerceIn(0f, 1f),
            )
        }
        if (next == _prefs.value) return
        _prefs.value = next
        sp.edit()
            .putString("style", next.homeStyle.name)
            .putInt("pCols", next.portraitGrid.columns).putInt("pRows", next.portraitGrid.rows)
            .putInt("lCols", next.landscapeGrid.columns).putInt("lRows", next.landscapeGrid.rows)
            .putFloat("iconScale", next.iconScale)
            .putBoolean("labels", next.showLabels)
            .putBoolean("suggestion", next.showSuggestion)
            .putBoolean("wallpaper", next.showWallpaper)
            .putBoolean("cornersFollow", next.cornersFollowDevice)
            .putString("pPaging", next.portraitPaging.name)
            .putString("lPaging", next.landscapePaging.name)
            .putBoolean("motion", next.motionEnabled)
            .putBoolean("motionBg", next.motionInBackground)
            .putFloat("motionSens", next.motionSensitivity)
            .putString("cornerStyle", next.cornerStyle.name)
            .putString("drawerStyle", next.drawerStyle.name)
            .putString("clockPosition", next.clockPosition.name)
            .putBoolean("networkDots", next.networkDots)
            .putBoolean("uiSounds", next.uiSounds)
            .putFloat("uiSoundVolume", next.uiSoundVolume)
            .putString("wheelGrouping", next.wheelGrouping.name)
            .apply()
    }

    private fun read(): LauncherPrefs {
        val d = LauncherPrefs()
        return LauncherPrefs(
            homeStyle = enumOr(sp.getString("style", null), d.homeStyle),
            portraitGrid = GridSpec(sp.getInt("pCols", d.portraitGrid.columns), sp.getInt("pRows", d.portraitGrid.rows)).coerced(),
            landscapeGrid = GridSpec(sp.getInt("lCols", d.landscapeGrid.columns), sp.getInt("lRows", d.landscapeGrid.rows)).coerced(),
            iconScale = sp.getFloat("iconScale", d.iconScale)
                .coerceIn(LauncherPrefs.MIN_ICON_SCALE, LauncherPrefs.MAX_ICON_SCALE),
            showLabels = sp.getBoolean("labels", d.showLabels),
            showSuggestion = sp.getBoolean("suggestion", d.showSuggestion),
            showWallpaper = sp.getBoolean("wallpaper", d.showWallpaper),
            cornersFollowDevice = sp.getBoolean("cornersFollow", d.cornersFollowDevice),
            portraitPaging = enumOr(sp.getString("pPaging", null), d.portraitPaging),
            landscapePaging = enumOr(sp.getString("lPaging", null), d.landscapePaging),
            motionEnabled = sp.getBoolean("motion", d.motionEnabled),
            motionInBackground = sp.getBoolean("motionBg", d.motionInBackground),
            motionSensitivity = sp.getFloat("motionSens", d.motionSensitivity).coerceIn(0f, 1f),
            cornerStyle = enumOr(sp.getString("cornerStyle", null), d.cornerStyle),
            drawerStyle = enumOr(sp.getString("drawerStyle", null), d.drawerStyle),
            clockPosition = enumOr(sp.getString("clockPosition", null), d.clockPosition),
            networkDots = sp.getBoolean("networkDots", d.networkDots),
            uiSounds = sp.getBoolean("uiSounds", d.uiSounds),
            uiSoundVolume = sp.getFloat("uiSoundVolume", d.uiSoundVolume).coerceIn(0f, 1f),
            wheelGrouping = enumOr(sp.getString("wheelGrouping", null), d.wheelGrouping),
        )
    }
}

class HomeLayoutStore internal constructor(private val sp: SharedPreferences) {
    private val _layout = MutableStateFlow(read())
    val layout: StateFlow<HomeLayout> = _layout.asStateFlow()

    /** Persists a new page arrangement (marks the layout as seeded). */
    fun setPages(pages: List<HomePage>) = write(_layout.value.copy(pages = pages, seeded = true))

    /** Takes [key] off the home pages (it stays in the drawer). */
    fun removeFromHome(key: String, currentPages: List<HomePage>) = write(
        _layout.value.copy(
            pages = HomeLayoutMath.remove(currentPages, key),
            removed = _layout.value.removed + key,
            seeded = true,
        ),
    )

    /** Puts [key] back on the home pages. */
    fun addToHome(key: String, currentPages: List<HomePage>, preferredPage: Int, capacityOf: (PageLayout) -> Int) = write(
        _layout.value.copy(
            pages = HomeLayoutMath.add(currentPages, key, preferredPage, capacityOf),
            removed = _layout.value.removed - key,
            seeded = true,
        ),
    )

    private fun write(next: HomeLayout) {
        _layout.value = next
        sp.edit()
            .putString("pages", HomeLayoutCodec.encode(next.pages))
            .putStringSet("removed", next.removed)
            .putBoolean("seeded", next.seeded)
            .apply()
    }

    private fun read(): HomeLayout = HomeLayout(
        pages = HomeLayoutCodec.decode(sp.getString("pages", null)),
        removed = sp.getStringSet("removed", emptySet())!!.toSet(),
        seeded = sp.getBoolean("seeded", false),
    )
}

/** JSON codec for the page list: `[{"k":[keys],"s":"Grid","p":[c,r],"l":[c,r],"r":2}, …]`. */
object HomeLayoutCodec {
    fun encode(pages: List<HomePage>): String = JSONArray().apply {
        pages.forEach { page ->
            put(
                JSONObject()
                    .put("k", JSONArray(page.keys))
                    .put("s", page.layout.shape.name)
                    .put("r", page.layout.rings)
                    .apply {
                        page.layout.portrait?.let { put("p", JSONArray(listOf(it.columns, it.rows))) }
                        page.layout.landscape?.let { put("l", JSONArray(listOf(it.columns, it.rows))) }
                    },
            )
        }
    }.toString()

    fun decode(raw: String?): List<HomePage> = runCatching {
        val arr = JSONArray(raw ?: return emptyList())
        List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            val k = o.getJSONArray("k")
            fun spec(name: String) = o.optJSONArray(name)?.let { GridSpec(it.getInt(0), it.getInt(1)).coerced() }
            HomePage(
                keys = List(k.length()) { k.getString(it) },
                layout = PageLayout(
                    shape = enumOr(o.optString("s"), PageShape.Grid),
                    portrait = spec("p"),
                    landscape = spec("l"),
                    rings = o.optInt("r", 2).coerceIn(PageLayout.MIN_RINGS, PageLayout.MAX_RINGS),
                ),
            )
        }
    }.getOrDefault(emptyList())
}

/** Corner slots (by physical corner) and gesture bindings. */
class ShortcutStore internal constructor(private val sp: SharedPreferences) {
    private val _corners = MutableStateFlow(readCorners())
    val corners: StateFlow<Map<Corner, CornerSlot>> = _corners.asStateFlow()

    private val _gestures = MutableStateFlow(readGestures())
    val gestures: StateFlow<Map<HomeGesture, CornerItem>> = _gestures.asStateFlow()

    fun setCorner(corner: Corner, slot: CornerSlot) {
        val next = _corners.value + (corner to slot)
        _corners.value = next
        sp.edit().putString("corner.${corner.name}", ShortcutCodec.encodeSlot(slot)).apply()
    }

    fun setGesture(gesture: HomeGesture, item: CornerItem?) {
        val next = if (item == null) _gestures.value - gesture else _gestures.value + (gesture to item)
        _gestures.value = next
        // An explicit "none" is stored as an empty string so it survives over the defaults.
        sp.edit().putString("gesture.${gesture.name}", item?.let(ShortcutCodec::encodeItem) ?: "").apply()
    }

    private fun readCorners(): Map<Corner, CornerSlot> = Corner.entries.associateWith { c ->
        sp.getString("corner.${c.name}", null)?.let(ShortcutCodec::decodeSlot)
            ?: DefaultCorners[c] ?: CornerSlot.Empty
    }

    private fun readGestures(): Map<HomeGesture, CornerItem> = buildMap {
        HomeGesture.entries.forEach { g ->
            val raw = sp.getString("gesture.${g.name}", null)
            val item = if (raw == null) DefaultGestures[g] else ShortcutCodec.decodeItem(raw)
            if (item != null) put(g, item)
        }
    }
}

/** String codec for corner items / slots (JSON via the platform's org.json). */
object ShortcutCodec {
    fun encodeItem(item: CornerItem): String = when (item) {
        is CornerItem.Action -> "action:${item.id.name}"
        is CornerItem.App -> "app:${item.key}"
    }

    fun decodeItem(raw: String): CornerItem? = when {
        raw.startsWith("action:") ->
            runCatching { CornerActionId.valueOf(raw.removePrefix("action:")) }.getOrNull()?.let(CornerItem::Action)
        raw.startsWith("app:") -> raw.removePrefix("app:").takeIf { it.isNotEmpty() }?.let(CornerItem::App)
        else -> null
    }

    fun encodeSlot(slot: CornerSlot): String = when (slot) {
        CornerSlot.Empty -> JSONObject().put("t", "empty").toString()
        is CornerSlot.Single -> JSONObject().put("t", "single").put("i", encodeItem(slot.item)).toString()
        is CornerSlot.Palette -> JSONObject()
            .put("t", "palette")
            .put("n", slot.name)
            .put("i", JSONArray(slot.items.map(::encodeItem)))
            .toString()
        is CornerSlot.Complication -> JSONObject().put("t", "complication").put("k", slot.kind.name).toString()
        is CornerSlot.ComplicationCluster -> JSONObject()
            .put("t", "cluster")
            .put("k", JSONArray(slot.kinds.map { it.name }))
            .toString()
        is CornerSlot.Widget -> JSONObject().put("t", "widget").put("id", slot.appWidgetId).put("n", slot.label).toString()
    }

    fun decodeSlot(raw: String): CornerSlot? = runCatching {
        val o = JSONObject(raw)
        when (o.getString("t")) {
            "empty" -> CornerSlot.Empty
            "single" -> decodeItem(o.getString("i"))?.let(CornerSlot::Single)
            "palette" -> {
                val arr = o.getJSONArray("i")
                CornerSlot.Palette(
                    name = o.optString("n", "Palette"),
                    items = List(arr.length()) { arr.getString(it) }.mapNotNull(::decodeItem)
                        .take(CornerSlot.MAX_PALETTE_ITEMS),
                )
            }
            "complication" -> kindOf(o.getString("k"))?.let(CornerSlot::Complication)
            "cluster" -> {
                val arr = o.getJSONArray("k")
                CornerSlot.ComplicationCluster(
                    List(arr.length()) { arr.getString(it) }.mapNotNull(::kindOf).take(CornerSlot.MAX_CLUSTER),
                )
            }
            "widget" -> CornerSlot.Widget(o.getInt("id"), o.optString("n", "Widget"))
            else -> null
        }
    }.getOrNull()

    private fun kindOf(name: String): ComplicationKind? = ComplicationKind.entries.firstOrNull { it.name == name }
}

/** Bounded log of launches feeding [AppSuggester]. */
class LaunchHistoryStore internal constructor(private val sp: SharedPreferences) {
    private val _events = MutableStateFlow(read())
    val events: StateFlow<List<LaunchEvent>> = _events.asStateFlow()

    fun record(key: String, nowMillis: Long = System.currentTimeMillis()) {
        val hour = Calendar.getInstance().apply { timeInMillis = nowMillis }.get(Calendar.HOUR_OF_DAY)
        val next = (_events.value + LaunchEvent(key, nowMillis, hour)).takeLast(MAX_EVENTS)
        _events.value = next
        val arr = JSONArray()
        next.forEach { arr.put(JSONArray().put(it.key).put(it.timeMillis).put(it.hourOfDay)) }
        sp.edit().putString("events", arr.toString()).apply()
    }

    private fun read(): List<LaunchEvent> = runCatching {
        val arr = JSONArray(sp.getString("events", "[]"))
        List(arr.length()) { i ->
            val e = arr.getJSONArray(i)
            LaunchEvent(e.getString(0), e.getLong(1), e.getInt(2))
        }
    }.getOrDefault(emptyList())

    private companion object {
        const val MAX_EVENTS = 400
    }
}

private inline fun <reified E : Enum<E>> enumOr(name: String?, fallback: E): E =
    name?.let { n -> enumValues<E>().firstOrNull { it.name == n } } ?: fallback
