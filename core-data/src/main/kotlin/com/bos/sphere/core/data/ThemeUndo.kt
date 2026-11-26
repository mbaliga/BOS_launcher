package com.bos.sphere.core.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The one undo: the look before the last theme was applied or the look reset. Wave 58 (defects58, CFG-62): it is kept in the device-local pref file
 * [StateSchema.THEME_UNDO_FILE] once [attach] has run (LauncherStores does it), so "Undo last theme change" survives process death; never exported.
 */
object ThemeUndo {
    private const val KEY = "snapshot"
    private val state = MutableStateFlow<LookSnapshot?>(null)
    val snapshot: StateFlow<LookSnapshot?> = state.asStateFlow()
    @Volatile private var sp: android.content.SharedPreferences? = null

    /** Reads the kept snapshot (a damaged one is dropped) and keeps every later change in [prefs]. */
    fun attach(prefs: android.content.SharedPreferences) {
        sp = prefs
        state.value = runCatching { prefs.getString(KEY, null)?.let(LookSnapshotCodec::decode) }.getOrNull()
    }

    fun remember(s: LookSnapshot) { state.value = s; runCatching { sp?.edit()?.putString(KEY, LookSnapshotCodec.encode(s))?.apply() } }
    fun clear() { state.value = null; runCatching { sp?.edit()?.remove(KEY)?.apply() } }
}

/**
 * Wave 58: a [LookSnapshot] as text. The look is written through the settings store's own codec (so every wallpaper field, picture included, keeps its
 * stored form) and read back the same way; the dock style rides beside it.
 */
object LookSnapshotCodec {
    fun encode(s: LookSnapshot): String {
        val sp = MapSharedPreferences()
        LauncherPrefsStore(sp).writeAll(ThemeLook.restore(LauncherPrefs(), s))
        val prefs = org.json.JSONObject()
        sp.all.forEach { (k, v) ->
            val t = when (v) { is String -> "s"; is Boolean -> "b"; is Int -> "i"; is Long -> "l"; is Float -> "f"; else -> null } ?: return@forEach
            prefs.put(k, org.json.JSONObject().put("t", t).put("v", if (v is Float) v.toDouble() else v))
        }
        return org.json.JSONObject().put("dock", s.dockStyle.name).put("prefs", prefs).toString()
    }

    fun decode(text: String): LookSnapshot? = runCatching {
        val o = org.json.JSONObject(text)
        val src = o.getJSONObject("prefs")
        val raw = LinkedHashMap<String, Any?>()
        src.keys().forEach { k ->
            val e = src.getJSONObject(k)
            raw[k] = when (e.optString("t")) {
                "s" -> e.getString("v"); "b" -> e.getBoolean("v"); "i" -> e.getInt("v"); "l" -> e.getLong("v"); "f" -> e.getDouble("v").toFloat(); else -> return@forEach
            }
        }
        val dock = DockStyle.entries.firstOrNull { it.name == o.optString("dock") } ?: DockStyle.Standard
        ThemeLook.snapshot(LauncherPrefsStore(MapSharedPreferences(raw)).prefs.value, dock)
    }.getOrNull()
}
