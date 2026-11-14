package com.bos.sphere.core.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * CFG-03: unknown fields nested inside known sections (a layout page, a corner slot, the shortcuts or Launchpad
 * section) must survive file -> live stores -> file, not only file -> model -> file. The live stores hold typed values
 * and would drop them, so [extract] lifts them out on import into [StateExtras.nested] and [inject] puts them back on
 * export.
 *
 * Fields tied to one thing are dropped with that thing: a page's extras go when the page is deleted (keyed by page id),
 * and a corner's extras are only re-attached while the corner still holds the slot they were read with.
 */
object StateNested {
    private const val LAYOUT = "layout"
    private const val PAGES = "layoutPages"
    private const val SHORTCUTS = "shortcuts"
    private const val CORNERS = "corners"
    private const val CORNERS_UNKNOWN = "cornersUnknown"
    private const val GESTURES_UNKNOWN = "gesturesUnknown"
    private const val LAUNCHPAD = "launchpad"
    private const val SETTINGS = "settings"

    private val LAYOUT_KNOWN = setOf("pages", "removed", "seeded", "hidden")
    private val PAGE_KNOWN = setOf("k", "s", "r", "id", "p", "l")
    private val SHORTCUT_KNOWN = setOf("corners", "landscapeCorners", "gestures", "keyboard")
    private val SLOT_KNOWN = setOf("t", "i", "n", "k", "id")
    private val LAUNCHPAD_KNOWN = setOf("rules", "prefs", "gestures")
    private val SETTINGS_KNOWN = setOf(StateSchema.K_FORMAT, "settingsVersion", "prefs")

    /** Which keys of the nested object belong to which top-level section, so an import of one section replaces only its own. */
    val KEYS_BY_SECTION: Map<String, Set<String>> = mapOf(
        StateSchema.K_LAYOUT to setOf(LAYOUT, PAGES),
        StateSchema.K_SHORTCUTS to setOf(SHORTCUTS, CORNERS, CORNERS_UNKNOWN, GESTURES_UNKNOWN),
        StateSchema.K_LAUNCHPAD to setOf(LAUNCHPAD),
        StateSchema.K_SETTINGS to setOf(SETTINGS),
    )

    private fun rest(o: JSONObject?, known: Set<String>): JSONObject = JSONObject().also { out ->
        o?.keys()?.forEach { k -> if (k !in known) out.put(k, o.get(k)) }
    }

    private fun JSONObject.putIfAny(k: String, v: JSONObject) { if (v.length() > 0) put(k, v) }

    /** Reads the nested unknowns out of a (migrated, current-schema) document. [pageIds] are the ids the pages got on decode, in file order. */
    fun extract(doc: JSONObject, pageIds: List<Long>, corners: Map<Corner, CornerSlot>): JSONObject {
        val out = JSONObject()
        val lay = doc.optJSONObject(StateSchema.K_LAYOUT)
        out.putIfAny(LAYOUT, rest(lay, LAYOUT_KNOWN))
        val rawPages = lay?.optJSONArray("pages")
        if (rawPages != null && rawPages.length() == pageIds.size) {
            val m = JSONObject()
            for (i in 0 until rawPages.length()) {
                val extra = rest(rawPages.optJSONObject(i), PAGE_KNOWN)
                if (extra.length() > 0) m.put(pageIds[i].toString(), extra)
            }
            out.putIfAny(PAGES, m)
        }
        val sc = doc.optJSONObject(StateSchema.K_SHORTCUTS)
        out.putIfAny(SHORTCUTS, rest(sc, SHORTCUT_KNOWN))
        sc?.optJSONObject("corners")?.let { c ->
            val per = JSONObject()
            val unknown = JSONObject()
            c.keys().forEach { name ->
                val corner = Corner.entries.firstOrNull { it.name == name }
                val obj = c.optJSONObject(name)
                if (corner == null || obj == null) { unknown.put(name, c.get(name)); return@forEach }
                val extra = rest(obj, SLOT_KNOWN)
                if (extra.length() > 0) per.put(name, JSONObject().put("canon", ShortcutCodec.encodeSlot(corners[corner] ?: CornerSlot.Empty)).put("extra", extra))
            }
            out.putIfAny(CORNERS, per)
            out.putIfAny(CORNERS_UNKNOWN, unknown)
        }
        sc?.optJSONObject("gestures")?.let { g ->
            val unknown = JSONObject()
            g.keys().forEach { name -> if (HomeGesture.entries.none { it.name == name }) unknown.put(name, g.get(name)) }
            out.putIfAny(GESTURES_UNKNOWN, unknown)
        }
        out.putIfAny(LAUNCHPAD, rest(doc.optJSONObject(StateSchema.K_LAUNCHPAD), LAUNCHPAD_KNOWN))
        out.putIfAny(SETTINGS, rest(doc.optJSONObject(StateSchema.K_SETTINGS), SETTINGS_KNOWN))
        return out
    }

    private fun mergeInto(target: JSONObject?, extra: JSONObject?) {
        if (target == null || extra == null) return
        extra.keys().forEach { k -> if (!target.has(k)) target.put(k, extra.get(k)) }
    }

    /** Puts the nested unknowns back into an exported document. [corners] are the live slots, to check a corner still holds the slot its extras came with. */
    fun inject(doc: JSONObject, nested: JSONObject, corners: Map<Corner, CornerSlot>) {
        if (nested.length() == 0) return
        val lay = doc.optJSONObject(StateSchema.K_LAYOUT)
        mergeInto(lay, nested.optJSONObject(LAYOUT))
        nested.optJSONObject(PAGES)?.let { m ->
            val pages: JSONArray? = lay?.optJSONArray("pages")
            if (pages != null) for (i in 0 until pages.length()) {
                val p = pages.optJSONObject(i) ?: continue
                mergeInto(p, m.optJSONObject(p.optLong("id").toString()))
            }
        }
        val sc = doc.optJSONObject(StateSchema.K_SHORTCUTS)
        mergeInto(sc, nested.optJSONObject(SHORTCUTS))
        val cj = sc?.optJSONObject("corners")
        nested.optJSONObject(CORNERS)?.let { per ->
            per.keys().forEach { name ->
                val corner = Corner.entries.firstOrNull { it.name == name } ?: return@forEach
                val e = per.optJSONObject(name) ?: return@forEach
                val slotNow = ShortcutCodec.encodeSlot(corners[corner].let { if (it is CornerSlot.Widget) CornerSlot.Empty else it ?: CornerSlot.Empty })
                if (e.optString("canon") == slotNow) mergeInto(cj?.optJSONObject(name), e.optJSONObject("extra"))
            }
        }
        mergeInto(cj, nested.optJSONObject(CORNERS_UNKNOWN))
        mergeInto(sc?.optJSONObject("gestures"), nested.optJSONObject(GESTURES_UNKNOWN))
        mergeInto(doc.optJSONObject(StateSchema.K_LAUNCHPAD), nested.optJSONObject(LAUNCHPAD))
        mergeInto(doc.optJSONObject(StateSchema.K_SETTINGS), nested.optJSONObject(SETTINGS))
    }

    /** The nested object after importing the sections in [present]: those sections' keys come from [incoming], the rest stay as they were in [current]. */
    fun merge(current: JSONObject, incoming: JSONObject, present: Set<String>): JSONObject {
        val out = JSONObject()
        val replaced = present.flatMapTo(HashSet()) { KEYS_BY_SECTION[it].orEmpty() }
        current.keys().forEach { k -> if (k !in replaced) out.put(k, current.get(k)) }
        incoming.keys().forEach { k -> if (k in replaced) out.put(k, incoming.get(k)) }
        return out
    }
}
