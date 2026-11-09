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
    val iconColors = IconColorStore(app.getSharedPreferences("icon_colors", Context.MODE_PRIVATE))

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
                clockSize = it.clockSize.coerceIn(LauncherPrefs.MIN_CLOCK_SIZE, LauncherPrefs.MAX_CLOCK_SIZE),
                iconSpacing = it.iconSpacing.coerceIn(0f, 1f),
                cornerIconSize = it.cornerIconSize.coerceIn(LauncherPrefs.MIN_CORNER_ICON, LauncherPrefs.MAX_CORNER_ICON),
                indicatorIntensity = it.indicatorIntensity.coerceIn(0f, 1f),
                indicatorSpeed = it.indicatorSpeed.coerceIn(0f, 1f),
            )
        }
        if (next == _prefs.value) return
        _prefs.value = next
        sp.edit()
            .putString("style", next.homeStyle.name).putString("homeScroll", next.homeScroll.name).putInt("contCols", next.continuousColumns)
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
            .putString("strip", next.strip.entries.joinToString(",") { "${it.key.name}:${it.value.name}" })
            .putFloat("iconSpacing", next.iconSpacing)
            .putString("healthSource", next.healthSource.name)
            .putString("bgKind", next.backgroundKind.name).putString("bgPreset", next.backgroundPreset)
            .putLong("bgA", next.backgroundColorA).putLong("bgB", next.backgroundColorB)
            .putString("webEngine", next.webEngine.name)
            .putBoolean("searchWeb", next.searchWeb).putBoolean("searchContacts", next.searchContacts)
            .putString("bgPicture", next.backgroundPicturePath).putString("leftPage", next.leftPage.name).putInt("leftWidgetId", next.leftWidgetId).putBoolean("launchpadRail", next.launchpadRail)
            .putString("settingsBackdrop", next.settingsBackdrop.name).putFloat("settingsDim", next.settingsDim)
            .putFloat("spacingV", next.spacingV).putFloat("gridBias", next.gridBias).putFloat("gridInsetX", next.gridInsetX).putFloat("gridInsetTop", next.gridInsetTop)
            .putFloat("gridInsetBottom", next.gridInsetBottom).putFloat("labelScale", next.labelScale)
            .putString("searchLayout", next.searchLayout.name).putBoolean("searchBackdrop", next.searchBackdrop)
            .putBoolean("searchMotion", next.searchMotion).putBoolean("searchFiles", next.searchFiles)
            .putBoolean("searchActions", next.searchActions).putBoolean("searchDeep", next.searchDeep)
            .putBoolean("onboardingDone", next.onboardingDone)
            .putBoolean("alwaysKeyboard", next.alwaysKeyboard)
            .putFloat("clockSize", next.clockSize)
            .putString("clockFormat", next.clockFormat.name)
            .putBoolean("clockDate", next.clockShowDate)
            .putBoolean("clockDay", next.clockShowDay)
            .putBoolean("clockWeather", next.clockShowWeather)
            .putBoolean("weatherF", next.weatherFahrenheit)
            .putFloat("cornerIconSize", next.cornerIconSize)
            .putString("cornerIconMode", next.cornerIconMode.name)
            .putBoolean("hideStatusBar", next.hideStatusBar)
            .putString("appsOnHome", next.appsOnHome.name)
            .putString("pageMode", next.pageMode.name)
            .putString("drawerView", next.drawerView.name)
            .putString("drawerSort", next.drawerSort.name)
            .putString("drawerRail", next.drawerRail.name)
            .putString("indMetric", next.indicatorMetric.name)
            .putString("indEffects", next.indicatorEffects.joinToString(",") { it.name })
            .putLong("indLow", next.indicatorLow).putLong("indMid", next.indicatorMid)
            .putLong("indHigh", next.indicatorHigh).putLong("indIdle", next.indicatorIdle)
            .putBoolean("indInvert", next.indicatorInvert)
            .putFloat("indIntensity", next.indicatorIntensity)
            .putFloat("indSpeed", next.indicatorSpeed)
            .putString("themeMode", next.themeMode.name)
            .putLong("accent", next.accentColor)
            .putString("iconShape", next.iconShape.name)
            .putString("iconTint", next.iconTint.name)
            .putBoolean("iconPlate", next.iconPlate)
            .putString("language", next.language.name)
            .putString("wheelGrouping", next.wheelGrouping.name)
            .apply()
    }

    private fun read(): LauncherPrefs {
        val d = LauncherPrefs()
        return LauncherPrefs(
            homeStyle = enumOr(sp.getString("style", null), d.homeStyle),
            homeScroll = enumOr(sp.getString("homeScroll", null), d.homeScroll),
            continuousColumns = sp.getInt("contCols", d.continuousColumns),
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
            strip = sp.getString("strip", null)?.let { raw ->
                raw.split(',').filter { it.isNotEmpty() }.mapNotNull { pair ->
                    val (a, b) = pair.split(':').takeIf { it.size == 2 } ?: return@mapNotNull null
                    val item = StripItem.entries.firstOrNull { it.name == a } ?: return@mapNotNull null
                    val zone = StripZone.entries.firstOrNull { it.name == b } ?: return@mapNotNull null
                    item to zone
                }.toMap()
            } ?: d.strip,
            iconSpacing = sp.getFloat("iconSpacing", d.iconSpacing).coerceIn(0f, 1f),
            healthSource = enumOr(sp.getString("healthSource", null), d.healthSource),
            // Older installs stored "show wallpaper" as a switch; it is now a background kind.
            backgroundKind = sp.getString("bgKind", null)?.let { n -> BackgroundKind.entries.firstOrNull { it.name == n } }
                ?: if (sp.getBoolean("wallpaper", false)) BackgroundKind.System else d.backgroundKind,
            backgroundPreset = sp.getString("bgPreset", d.backgroundPreset) ?: d.backgroundPreset,
            backgroundColorA = sp.getLong("bgA", d.backgroundColorA),
            backgroundColorB = sp.getLong("bgB", d.backgroundColorB),
            webEngine = enumOr(sp.getString("webEngine", null), d.webEngine),
            searchWeb = sp.getBoolean("searchWeb", d.searchWeb),
            searchContacts = sp.getBoolean("searchContacts", d.searchContacts),
            backgroundPicturePath = sp.getString("bgPicture", "") ?: "",
            leftPage = runCatching { LeftPage.valueOf(sp.getString("leftPage", null) ?: "") }.getOrDefault(d.leftPage),
            leftWidgetId = sp.getInt("leftWidgetId", d.leftWidgetId),
            launchpadRail = sp.getBoolean("launchpadRail", d.launchpadRail),
            settingsBackdrop = runCatching { SettingsBackdrop.valueOf(sp.getString("settingsBackdrop", null) ?: "") }.getOrDefault(d.settingsBackdrop),
            settingsDim = sp.getFloat("settingsDim", d.settingsDim),
            spacingV = sp.getFloat("spacingV", d.spacingV),
            gridBias = sp.getFloat("gridBias", d.gridBias),
            gridInsetX = sp.getFloat("gridInsetX", d.gridInsetX),
            gridInsetTop = sp.getFloat("gridInsetTop", d.gridInsetTop),
            gridInsetBottom = sp.getFloat("gridInsetBottom", d.gridInsetBottom),
            labelScale = sp.getFloat("labelScale", d.labelScale),
            searchLayout = runCatching { SearchLayout.valueOf(sp.getString("searchLayout", null) ?: "") }.getOrDefault(d.searchLayout),
            searchBackdrop = sp.getBoolean("searchBackdrop", d.searchBackdrop),
            searchMotion = sp.getBoolean("searchMotion", d.searchMotion),
            searchFiles = sp.getBoolean("searchFiles", d.searchFiles),
            searchActions = sp.getBoolean("searchActions", d.searchActions),
            searchDeep = sp.getBoolean("searchDeep", d.searchDeep),
            // An install that already has settings saved is not a first run.
            onboardingDone = sp.getBoolean("onboardingDone", sp.contains("style") || sp.contains("pCols")),
            alwaysKeyboard = sp.getBoolean("alwaysKeyboard", d.alwaysKeyboard),
            clockSize = sp.getFloat("clockSize", d.clockSize).coerceIn(LauncherPrefs.MIN_CLOCK_SIZE, LauncherPrefs.MAX_CLOCK_SIZE),
            clockFormat = enumOr(sp.getString("clockFormat", null), d.clockFormat),
            clockShowDate = sp.getBoolean("clockDate", d.clockShowDate),
            clockShowDay = sp.getBoolean("clockDay", d.clockShowDay),
            clockShowWeather = sp.getBoolean("clockWeather", d.clockShowWeather),
            weatherFahrenheit = sp.getBoolean("weatherF", d.weatherFahrenheit),
            cornerIconSize = sp.getFloat("cornerIconSize", d.cornerIconSize).coerceIn(LauncherPrefs.MIN_CORNER_ICON, LauncherPrefs.MAX_CORNER_ICON),
            cornerIconMode = enumOr(sp.getString("cornerIconMode", null), d.cornerIconMode),
            hideStatusBar = sp.getBoolean("hideStatusBar", d.hideStatusBar),
            appsOnHome = enumOr(sp.getString("appsOnHome", null), d.appsOnHome),
            pageMode = enumOr(sp.getString("pageMode", null), d.pageMode),
            drawerView = enumOr(sp.getString("drawerView", null), d.drawerView),
            drawerSort = enumOr(sp.getString("drawerSort", null), d.drawerSort),
            drawerRail = enumOr(sp.getString("drawerRail", null), d.drawerRail),
            indicatorMetric = enumOr(sp.getString("indMetric", null), d.indicatorMetric),
            indicatorEffects = sp.getString("indEffects", null)
                ?.split(',')?.filter { it.isNotEmpty() }
                ?.mapNotNull { n -> IndicatorEffect.entries.firstOrNull { it.name == n } }?.toSet()
                ?: d.indicatorEffects,
            indicatorLow = sp.getLong("indLow", d.indicatorLow), indicatorMid = sp.getLong("indMid", d.indicatorMid),
            indicatorHigh = sp.getLong("indHigh", d.indicatorHigh), indicatorIdle = sp.getLong("indIdle", d.indicatorIdle),
            indicatorInvert = sp.getBoolean("indInvert", d.indicatorInvert),
            indicatorIntensity = sp.getFloat("indIntensity", d.indicatorIntensity).coerceIn(0f, 1f),
            indicatorSpeed = sp.getFloat("indSpeed", d.indicatorSpeed).coerceIn(0f, 1f),
            themeMode = enumOr(sp.getString("themeMode", null), d.themeMode),
            accentColor = sp.getLong("accent", d.accentColor),
            iconShape = enumOr(sp.getString("iconShape", null), d.iconShape),
            iconTint = enumOr(sp.getString("iconTint", null), d.iconTint),
            iconPlate = sp.getBoolean("iconPlate", d.iconPlate),
            language = enumOr(sp.getString("language", null), d.language),
            wheelGrouping = enumOr(sp.getString("wheelGrouping", null), d.wheelGrouping),
        )
    }
}

class HomeLayoutStore internal constructor(private val sp: SharedPreferences) {
    private val _layout = MutableStateFlow(read())
    val layout: StateFlow<HomeLayout> = _layout.asStateFlow()

    /**
     * Persists an edited *visual* page list: split pages fold back together ([HomeLayoutMath.toStored])
     * and [preserve]d keys (stored but not drawable now — hidden apps, a paused work profile) keep
     * their places.
     */
    fun setPages(visual: List<HomePage>, preserve: Set<String> = emptySet()) {
        val cur = _layout.value
        write(cur.copy(pages = HomeLayoutMath.toStored(visual, cur.pages, preserve), seeded = true))
    }

    /** Takes [key] off the home pages (it stays in the drawer). */
    fun removeFromHome(key: String, visual: List<HomePage>, preserve: Set<String> = emptySet()) {
        val cur = _layout.value
        write(
            cur.copy(
                pages = HomeLayoutMath.toStored(HomeLayoutMath.remove(visual, key), cur.pages, preserve - key),
                removed = cur.removed + key,
                seeded = true,
            ),
        )
    }

    /** Puts [key] back on the home pages. */
    fun addToHome(key: String, visual: List<HomePage>, preferredPage: Int, capacityOf: (PageLayout) -> Int, preserve: Set<String> = emptySet()) {
        val cur = _layout.value
        write(
            cur.copy(
                pages = HomeLayoutMath.toStored(HomeLayoutMath.add(visual, key, preferredPage, capacityOf), cur.pages, preserve),
                removed = cur.removed - key,
                seeded = true,
            ),
        )
    }

    /** Forgets "removed from home" for apps no longer installed, so a reinstall lands on home again. */
    fun pruneRemoved(installed: Set<String>) {
        val cur = _layout.value
        val kept = cur.removed.filterTo(HashSet()) { it in installed }
        if (kept.size != cur.removed.size) write(cur.copy(removed = kept))
    }

    private fun write(next: HomeLayout) {
        _layout.value = next
        sp.edit()
            .putString("pages", HomeLayoutCodec.encode(next.pages))
            .putStringSet("removed", next.removed)
            .putBoolean("seeded", next.seeded)
            .apply()
    }

    private fun read(): HomeLayout = HomeLayout(
        pages = HomeLayoutMath.normalizeIds(HomeLayoutCodec.decode(sp.getString("pages", null))),
        removed = sp.getStringSet("removed", emptySet())!!.toSet(),
        seeded = sp.getBoolean("seeded", false),
    )
}

/** JSON codec for the page list: `[{"k":[keys],"s":"Grid","p":[c,r],"l":[c,r],"r":2,"id":1}, …]`. */
object HomeLayoutCodec {
    fun encode(pages: List<HomePage>): String = JSONArray().apply {
        pages.forEach { page ->
            put(
                JSONObject()
                    .put("k", JSONArray(page.keys))
                    .put("s", page.layout.shape.name)
                    .put("r", page.layout.rings)
                    .put("id", page.id)
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
                id = o.optLong("id", 0L),
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


/** Per-app icon tint overrides (ARGB), keyed by [AppEntry.key]. */
class IconColorStore internal constructor(private val sp: SharedPreferences) {
    private val _colors = MutableStateFlow(sp.all.mapNotNull { (k, v) -> (v as? Long)?.let { k to it } }.toMap())
    val colors: StateFlow<Map<String, Long>> = _colors.asStateFlow()

    fun set(key: String, argb: Long?) {
        _colors.value = if (argb == null) _colors.value - key else _colors.value + (key to argb)
        sp.edit().apply { if (argb == null) remove(key) else putLong(key, argb) }.apply()
    }
}

/** Launch statistics derived from the history log, for drawer sorting. */
object UsageStats {
    data class Usage(val count: Int, val lastMillis: Long)

    fun of(events: List<LaunchEvent>): Map<String, Usage> {
        val out = HashMap<String, Usage>()
        for (e in events) {
            val cur = out[e.key]
            out[e.key] = Usage((cur?.count ?: 0) + 1, maxOf(cur?.lastMillis ?: 0L, e.timeMillis))
        }
        return out
    }
}
