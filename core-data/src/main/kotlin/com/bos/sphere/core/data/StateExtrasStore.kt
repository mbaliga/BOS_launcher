package com.bos.sphere.core.data

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Persists [StateExtras] (workspaces, sessions, palette aliases, unknown fields, pending widgets) in one JSON file in
 * app storage. Other work streams read and write their section through [section] / [setSection]; the state file
 * export and import carry it verbatim. Written atomically (temp file, then rename).
 */
class StateExtrasStore(private val file: File) {
    @Synchronized
    fun read(): StateExtras {
        val o = runCatching { JSONObject(file.readText()) }.getOrNull() ?: return StateExtras()
        val opaque = StateSchema.OPAQUE_SECTIONS.mapNotNull { s -> o.optJSONArray(s)?.let { s to it } }.toMap()
        val widgets = o.optJSONArray(StateSchema.K_WIDGETS)?.let { a ->
            (0 until a.length()).mapNotNull { i -> a.optJSONObject(i)?.let { PendingWidget(it.optString("slot"), it.optString("label"), it.optString("provider")) } }
        } ?: emptyList()
        return StateExtras(
            opaque = opaque,
            unknownTop = o.optJSONObject("unknownTop") ?: JSONObject(),
            unknownPrefs = o.optJSONObject("unknownPrefs") ?: JSONObject(),
            keyboardShortcuts = o.optString("keyboard").takeIf { it.isNotEmpty() },
            widgets = widgets,
            nested = o.optJSONObject("nested") ?: JSONObject(),
            objects = o.optJSONObject("objects")?.let { ob -> StateSchema.OBJECT_SECTIONS.mapNotNull { k -> ob.optJSONObject(k)?.let { k to it } }.toMap() } ?: emptyMap(),
        )
    }

    @Synchronized
    fun write(e: StateExtras) {
        val o = JSONObject()
        e.opaque.forEach { (k, v) -> o.put(k, v) }
        o.put("unknownTop", e.unknownTop)
        o.put("unknownPrefs", e.unknownPrefs)
        e.keyboardShortcuts?.let { o.put("keyboard", it) }
        o.put("nested", e.nested)
        o.put("objects", JSONObject().also { ob -> e.objects.forEach { (k, v) -> ob.put(k, v) } })
        o.put(StateSchema.K_WIDGETS, JSONArray().also { a -> e.widgets.forEach { a.put(JSONObject().put("slot", it.slot).put("label", it.label).put("provider", it.provider)) } })
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(o.toString())
        check(tmp.renameTo(file)) { "Could not write ${file.name}" }
    }

    /** One of [StateSchema.OPAQUE_SECTIONS] (for the workspaces, sessions and aliases streams). */
    fun section(name: String): JSONArray = read().opaque[name] ?: JSONArray()

    fun setSection(name: String, value: JSONArray) {
        require(name in StateSchema.OPAQUE_SECTIONS) { "Unknown state section $name" }
        val cur = read()
        write(cur.copy(opaque = cur.opaque + (name to value)))
    }
}
