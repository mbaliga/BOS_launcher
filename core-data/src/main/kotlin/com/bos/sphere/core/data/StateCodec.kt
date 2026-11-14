package com.bos.sphere.core.data

import org.json.JSONArray
import org.json.JSONObject

/** A widget the export could not carry (ids are per install). It is re-bound later, with one consent step each (CFG-02). */
data class PendingWidget(
    val slot: String,
    val label: String,
    /** Flattened provider component (`pkg/cls`) so the widget can be re-added on this or another install; empty when unknown. */
    val provider: String = "",
)

/**
 * The sections other work streams fill in later (workspaces, sessions, palette aliases), plus everything this build
 * does not understand. All of it is kept verbatim so nothing is lost on the way through the store.
 */
data class StateExtras(
    val opaque: Map<String, JSONArray> = emptyMap(),
    val unknownTop: JSONObject = JSONObject(),
    val unknownPrefs: JSONObject = JSONObject(),
    val keyboardShortcuts: String? = null,
    val widgets: List<PendingWidget> = emptyList(),
    /** Unknown fields nested inside known sections (see [StateNested]); kept so a round trip through the live stores loses nothing. */
    val nested: JSONObject = JSONObject(),
    /** Object sections ([StateSchema.OBJECT_SECTIONS]): the dock / workspace shell and the allow-listed preference files. */
    val objects: Map<String, JSONObject> = emptyMap(),
)

/** Everything one state file holds, as the app's own types. */
data class StateParts(
    val prefs: LauncherPrefs = LauncherPrefs(),
    val layout: HomeLayout = HomeLayout(),
    val corners: Map<Corner, CornerSlot> = DefaultCorners,
    /** Corner slots used in landscape when the prefs ask for a separate set (INP-15). Empty when none were set. */
    val landscapeCorners: Map<Corner, CornerSlot> = emptyMap(),
    /** Apps hidden from the home and drawer. */
    val hidden: Set<String> = emptySet(),
    /** A gesture mapped to null is bound to "nothing" on purpose. */
    val gestures: Map<HomeGesture, CornerItem?> = HomeGesture.entries.associateWith { DefaultGestures[it] },
    val launchpadRules: List<Rule> = emptyList(),
    val launchpadPrefs: LaunchpadPrefs = LaunchpadPrefs(),
    val launchpadGestures: Map<LaunchpadGesture, LaunchpadAction> = LaunchpadGestures.defaults,
    /** STR-04: per-app delivery policies. */
    val launchpadPolicies: List<AppPolicy> = emptyList(),
    val extras: StateExtras = StateExtras(),
)

/** Settings <-> the separable settings document (CFG-11). */
object SettingsCodec {
    class Decoded(val prefs: LauncherPrefs, val unknown: JSONObject)

    fun encode(p: LauncherPrefs, unknown: JSONObject = JSONObject()): JSONObject {
        val sp = MapSharedPreferences()
        LauncherPrefsStore(sp).update { p }
        val map = JSONObject()
        sp.all.forEach { (k, v) ->
            if (k in StateSchema.DEVICE_BOUND_PREF_KEYS) return@forEach
            when (v) {
                is Float -> map.put(k, v.toString().toDouble())
                is Set<*> -> map.put(k, JSONArray(v.map { it.toString() }))
                else -> map.put(k, v)
            }
        }
        unknown.keys().forEach { k -> if (!map.has(k)) map.put(k, unknown.get(k)) }
        return JSONObject()
            .put(StateSchema.K_FORMAT, StateSchema.SETTINGS_FORMAT)
            .put("settingsVersion", StateSchema.SETTINGS_CURRENT)
            .put("prefs", map)
    }

    fun decode(settings: JSONObject?): Decoded {
        val src = settings?.optJSONObject("prefs") ?: JSONObject()
        val raw = LinkedHashMap<String, Any?>()
        src.keys().forEach { k ->
            if (k in StateSchema.DEVICE_BOUND_PREF_KEYS) return@forEach
            raw[k] = when (val v = src.get(k)) {
                is JSONArray -> List(v.length()) { v.getString(it) }.toSet()
                else -> v
            }
        }
        val sp = MapSharedPreferences(raw)
        val read = LauncherPrefsStore(sp).prefs.value
        val unknown = JSONObject()
        src.keys().forEach { k -> if (k !in sp.accessed && k !in StateSchema.DEVICE_BOUND_PREF_KEYS) unknown.put(k, src.get(k)) }
        // This is an install that already has a setup, and device-bound values restart empty.
        val d = LauncherPrefs()
        val safe = read.copy(
            onboardingDone = true,
            leftWidgetId = d.leftWidgetId,
            backgroundPicturePath = d.backgroundPicturePath,
            backgroundKind = if (read.backgroundKind == BackgroundKind.Picture) d.backgroundKind else read.backgroundKind,
        )
        return Decoded(safe, unknown)
    }
}

/** [StateParts] <-> [StateDocument]. Pure apart from org.json. */
object StateCodec {
    fun export(parts: StateParts, now: Long, app: String = "", widgetProvider: (Int) -> String? = { null }): StateDocument {
        val o = JSONObject()
        parts.extras.unknownTop.keys().forEach { k -> if (k !in StateSchema.KNOWN_TOP) o.put(k, parts.extras.unknownTop.get(k)) }
        o.put(StateSchema.K_FORMAT, StateSchema.FORMAT)
        o.put(StateSchema.K_VERSION, StateSchema.CURRENT)
        o.put(StateSchema.K_EXPORTED_AT, now)
        if (app.isNotEmpty()) o.put(StateSchema.K_APP, app)

        o.put(
            StateSchema.K_LAYOUT,
            JSONObject()
                .put("pages", JSONArray(HomeLayoutCodec.encode(parts.layout.pages)))
                .put("removed", JSONArray(parts.layout.removed.sorted()))
                .put("seeded", parts.layout.seeded)
                .put("hidden", JSONArray(parts.hidden.sorted())),
        )
        o.put(StateSchema.K_SETTINGS, SettingsCodec.encode(parts.prefs, parts.extras.unknownPrefs))

        val widgets = ArrayList(parts.extras.widgets)
        val corners = JSONObject()
        parts.corners.forEach { (c, slot) ->
            val kept = if (slot is CornerSlot.Widget) {
                widgets += PendingWidget("corner.${c.name}", slot.label, widgetProvider(slot.appWidgetId).orEmpty())
                CornerSlot.Empty
            } else slot
            corners.put(c.name, JSONObject(ShortcutCodec.encodeSlot(kept)))
        }
        val landscape = JSONObject()
        parts.landscapeCorners.forEach { (c, slot) ->
            val kept = if (slot is CornerSlot.Widget) {
                widgets += PendingWidget("lcorner.${c.name}", slot.label, widgetProvider(slot.appWidgetId).orEmpty())
                CornerSlot.Empty
            } else slot
            landscape.put(c.name, JSONObject(ShortcutCodec.encodeSlot(kept)))
        }
        val gestures = JSONObject()
        HomeGesture.entries.forEach { g ->
            if (parts.gestures.containsKey(g)) gestures.put(g.name, parts.gestures[g]?.let(ShortcutCodec::encodeItem) ?: "")
        }
        o.put(
            StateSchema.K_SHORTCUTS,
            JSONObject().put("corners", corners).put("landscapeCorners", landscape).put("gestures", gestures).apply {
                parts.extras.keyboardShortcuts?.let { put("keyboard", it) }
            },
        )
        if (parts.prefs.leftPage == LeftPage.Widget && parts.prefs.leftWidgetId >= 0) {
            widgets += PendingWidget("leftPage", "Left page widget", widgetProvider(parts.prefs.leftWidgetId).orEmpty())
        }
        o.put(
            StateSchema.K_LAUNCHPAD,
            JSONObject()
                .put("rules", JSONArray(LaunchpadJson.encodeRules(parts.launchpadRules)))
                .put("prefs", JSONObject(LaunchpadJson.encodePrefs(parts.launchpadPrefs)))
                .put("gestures", LaunchpadGestures.encode(parts.launchpadGestures))
                .put("policies", PolicyJson.encode(parts.launchpadPolicies)),
        )
        for (s in StateSchema.OPAQUE_SECTIONS) o.put(s, parts.extras.opaque[s] ?: JSONArray())
        o.put(
            StateSchema.K_WIDGETS,
            JSONArray().also { a -> widgets.distinct().forEach { a.put(JSONObject().put("slot", it.slot).put("label", it.label).apply { if (it.provider.isNotEmpty()) put("provider", it.provider) }) } },
        )
        parts.extras.objects.forEach { (k, v) -> if (k in StateSchema.OBJECT_SECTIONS) o.put(k, JSONObject(v.toString())) }
        StateNested.inject(o, parts.extras.nested, parts.corners)
        return StateDocument(o)
    }

    /** Reads a document of any supported version. [Decoded.newer] is true when it came from a newer build. */
    class Decoded(val parts: StateParts, val migration: StateMigrations.Result, val present: Set<String>) {
        val newer get() = migration.fromNewer
    }

    fun decode(doc: StateDocument): Decoded {
        val mig = StateMigrations.toCurrent(doc)
        val j = mig.doc.json
        val settings = SettingsCodec.decode(j.optJSONObject(StateSchema.K_SETTINGS))

        val lay = j.optJSONObject(StateSchema.K_LAYOUT)
        val layout = HomeLayout(
            pages = HomeLayoutMath.normalizeIds(HomeLayoutCodec.decode(lay?.optJSONArray("pages")?.toString())),
            removed = lay?.optJSONArray("removed")?.let { a -> List(a.length()) { a.getString(it) }.toSet() } ?: emptySet(),
            seeded = lay?.optBoolean("seeded", false) ?: false,
        )

        val sc = j.optJSONObject(StateSchema.K_SHORTCUTS)
        val corners = LinkedHashMap(DefaultCorners)
        sc?.optJSONObject("corners")?.let { c ->
            Corner.entries.forEach { corner ->
                val v = c.opt(corner.name) ?: return@forEach
                ShortcutCodec.decodeSlot(v.toString())?.let { corners[corner] = it }
            }
        }
        val landscape = LinkedHashMap<Corner, CornerSlot>()
        sc?.optJSONObject("landscapeCorners")?.let { c ->
            Corner.entries.forEach { corner ->
                val v = c.opt(corner.name) ?: return@forEach
                ShortcutCodec.decodeSlot(v.toString())?.let { landscape[corner] = it }
            }
        }
        val gestures = LinkedHashMap<HomeGesture, CornerItem?>()
        HomeGesture.entries.forEach { gestures[it] = DefaultGestures[it] }
        sc?.optJSONObject("gestures")?.let { g ->
            HomeGesture.entries.forEach { gesture ->
                if (!g.has(gesture.name)) return@forEach
                val raw = g.optString(gesture.name)
                gestures[gesture] = if (raw.isEmpty()) null else ShortcutCodec.decodeItem(raw) ?: DefaultGestures[gesture]
            }
        }

        val lp = j.optJSONObject(StateSchema.K_LAUNCHPAD)
        val widgets = j.optJSONArray(StateSchema.K_WIDGETS)?.let { a ->
            (0 until a.length()).mapNotNull { i ->
                a.optJSONObject(i)?.let { PendingWidget(it.optString("slot"), it.optString("label"), it.optString("provider")) }?.takeIf { it.slot.isNotEmpty() }
            }
        } ?: emptyList()
        val opaque = StateSchema.OPAQUE_SECTIONS.mapNotNull { s -> j.optJSONArray(s)?.let { s to it } }.toMap()

        return Decoded(
            StateParts(
                prefs = settings.prefs,
                layout = layout,
                corners = corners,
                landscapeCorners = landscape,
                hidden = lay?.optJSONArray("hidden")?.let { a -> List(a.length()) { a.getString(it) }.toSet() } ?: emptySet(),
                gestures = gestures,
                launchpadRules = LaunchpadJson.decodeRules(lp?.optJSONArray("rules")?.toString()),
                launchpadPrefs = LaunchpadJson.decodePrefs(lp?.optJSONObject("prefs")?.toString()),
                launchpadGestures = LaunchpadGestures.decode(lp?.optString("gestures")),
                launchpadPolicies = PolicyJson.decode(lp?.optJSONArray("policies")),
                extras = StateExtras(
                    opaque = opaque,
                    unknownTop = mig.doc.unknownTop(),
                    unknownPrefs = settings.unknown,
                    keyboardShortcuts = sc?.optString("keyboard")?.takeIf { it.isNotEmpty() },
                    widgets = widgets,
                    nested = StateNested.extract(j, layout.pages.map { it.id }, corners),
                    objects = StateSchema.OBJECT_SECTIONS.mapNotNull { s -> j.optJSONObject(s)?.let { s to it } }.toMap(),
                ),
            ),
            mig,
            StateSchema.KNOWN_TOP.filterTo(LinkedHashSet()) { j.has(it) },
        )
    }

    /** Layout keys that name apps not in [installed]: they stay in the layout as placeholders and resolve when installed (CFG-02). */
    fun unresolvedApps(layout: HomeLayout, installed: Set<String>): Set<String> =
        layout.pages.flatMapTo(LinkedHashSet()) { it.keys } .filterTo(LinkedHashSet()) { it !in installed && it !in HomeTiles.ALL }
}
