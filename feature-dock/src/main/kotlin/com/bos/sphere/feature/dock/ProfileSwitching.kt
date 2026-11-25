package com.bos.sphere.feature.dock

import com.bos.sphere.core.data.HapticStrength
import com.bos.sphere.core.data.HomeGesture
import com.bos.sphere.core.data.LauncherHapticSettings
import com.bos.sphere.core.data.LauncherStores
import com.bos.sphere.core.data.ProfileSettings
import com.bos.sphere.core.data.ShortcutCodec

/**
 * Wave 57 (profiles, CFG-48): where the settings a profile carries are read and written. The dock keys (`dock.X`) are not here:
 * they are the manager's own [DockSettings] ([DockProfileKeys]). Reads and writes are synchronous so a switch is one step.
 */
interface ProfileSettingsPort {
    /** The JSON texts of [keys] as they are now (a key this port does not hold is left out). */
    fun read(keys: Set<String>): Map<String, String>
    /** Writes [values]; all the launcher_prefs keys in one store update. */
    fun write(values: Map<String, String>)
}

/** A port that holds nothing (tests of the workspace contents, hosts without settings). */
object NoProfileSettings : ProfileSettingsPort {
    override fun read(keys: Set<String>): Map<String, String> = emptyMap()
    override fun write(values: Map<String, String>) {}
}

/** A port over a plain map (tests). */
class MapProfileSettings(val values: MutableMap<String, String> = LinkedHashMap()) : ProfileSettingsPort {
    var writes = 0
    override fun read(keys: Set<String>): Map<String, String> = values.filterKeys { it in keys }
    override fun write(values: Map<String, String>) { writes++; this.values.putAll(values) }
}

/** The dock fields a profile can carry, in the encoding of `dock.settings` in the Backup file. */
object DockProfileKeys {
    const val ENABLED = "dock.enabled"
    const val EDGE = "dock.edge"
    const val MODE = "dock.mode"

    fun isDock(key: String) = key.startsWith(ProfileSettings.DOCK)

    fun read(d: DockSettings): Map<String, String> = mapOf(
        ENABLED to ProfileSettings.text(d.enabled),
        EDGE to ProfileSettings.text(d.edge.name),
        MODE to ProfileSettings.text(d.mode.name),
    )

    fun apply(d: DockSettings, values: Map<String, String>): DockSettings {
        var out = d
        values[ENABLED]?.let { t -> out = out.copy(enabled = ProfileSettings.value(t) as? Boolean) }
        values[EDGE]?.let { t -> DockEdge.entries.firstOrNull { it.name == ProfileSettings.value(t) }?.let { out = out.copy(edge = it) } }
        values[MODE]?.let { t -> DockMode.entries.firstOrNull { it.name == ProfileSettings.value(t) }?.let { out = out.copy(mode = it) } }
        return out
    }
}

/** The app's port: launcher_prefs through [LauncherStores], the home gestures through the shortcut store, the haptic strength file. */
class StoresProfileSettings(private val context: android.content.Context) : ProfileSettingsPort {
    private val stores by lazy { LauncherStores.get(context) }

    override fun read(keys: Set<String>): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        if (keys.any { it in ProfileSettings.PREF_KEYS }) out += ProfileSettings.readPrefs(stores.prefs.prefs.value).filterKeys { it in keys }
        val gestures = stores.shortcuts.gestures.value
        for (k in keys) if (k.startsWith(ProfileSettings.GESTURE)) {
            val g = HomeGesture.entries.firstOrNull { it.name == k.removePrefix(ProfileSettings.GESTURE) } ?: continue
            out[k] = ProfileSettings.text(gestures[g]?.let(ShortcutCodec::encodeItem) ?: "")
        }
        if (ProfileSettings.HAPTIC in keys) {
            LauncherHapticSettings.init(context)
            out[ProfileSettings.HAPTIC] = ProfileSettings.text(LauncherHapticSettings.strength.value.id)
        }
        return out
    }

    override fun write(values: Map<String, String>) {
        if (values.isEmpty()) return
        if (values.keys.any { it in ProfileSettings.PREF_KEYS }) stores.prefs.update { ProfileSettings.writePrefs(it, values) }
        for ((k, t) in values) if (k.startsWith(ProfileSettings.GESTURE)) {
            val g = HomeGesture.entries.firstOrNull { it.name == k.removePrefix(ProfileSettings.GESTURE) } ?: continue
            val raw = ProfileSettings.value(t) as? String ?: continue
            stores.shortcuts.setGesture(g, if (raw.isEmpty()) null else ShortcutCodec.decodeItem(raw) ?: continue)
        }
        values[ProfileSettings.HAPTIC]?.let { t ->
            LauncherHapticSettings.init(context)
            (ProfileSettings.value(t) as? String)?.let { LauncherHapticSettings.set(HapticStrength.forId(it)) }
        }
    }
}

/** CFG-48: the last switch, for the "Switch back" row. */
data class ProfileSwitchUndo(val fromId: String, val fromName: String, val toId: String)

/** Edits of a saved home arrangement (the JSON the workspace hooks write: pages, removed, dock). */
object ProfileArrangements {
    /** [arrangement] with [ref] added to its dock list; an arrangement without a dock list is left as it is (its legacy pins hold the dock). */
    fun withPin(arrangement: String?, ref: DockItemRef): String? {
        if (arrangement == null) return null
        return runCatching {
            val o = org.json.JSONObject(arrangement)
            val dock = o.optJSONArray("dock") ?: return arrangement
            val list = List(dock.length()) { dock.getString(it) }
            val pins = HomeDockKeys.decodeAll(list)
            if (ref in pins) arrangement else o.put("dock", org.json.JSONArray(list + HomeDockKeys.encode(ref))).toString()
        }.getOrDefault(arrangement)
    }
}
