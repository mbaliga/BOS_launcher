package com.bos.sphere.feature.desktop

/** What sits on the desktop. [kind] is "app" (id = component key) or "widget" (id = built-in widget name). */
data class PlacedItem(val kind: String, val id: String, val x: Float, val y: Float, val w: Float, val h: Float) {
    val isWidget get() = kind == KIND_WIDGET
    fun rect() = FRect(x, y, x + w, y + h)
    companion object { const val KIND_APP = "app"; const val KIND_WIDGET = "widget" }
}

/** Arrangement, density and dock mode of one display, kept per [DisplayIdentity] key (EXT-03, EXT-09). */
data class DesktopProfile(
    val items: List<PlacedItem> = emptyList(),
    val densityOverrideDpi: Int? = null,
    val dockMode: DockMode = DockMode.Bottom,
)

/** Text codec: no JSON dependency, so it round-trips in plain JVM tests. */
object ProfileCodec {
    private const val V = "v1"

    fun encode(p: DesktopProfile): String =
        listOf(V, "dock=${p.dockMode.name}", "density=${p.densityOverrideDpi ?: 0}") .joinToString("|") + "|" +
            p.items.joinToString(";") { listOf(it.kind, esc(it.id), it.x, it.y, it.w, it.h).joinToString(",") }

    fun decode(s: String?): DesktopProfile? {
        if (s.isNullOrBlank()) return null
        return runCatching {
            val parts = s.split("|", limit = 4)
            if (parts[0] != V) return null
            val dock = DockMode.valueOf(parts[1].removePrefix("dock="))
            val density = parts[2].removePrefix("density=").toInt().takeIf { it > 0 }
            val items = parts[3].split(";").filter { it.isNotEmpty() }.map {
                val f = splitEsc(it)
                PlacedItem(f[0], unesc(f[1]), f[2].toFloat(), f[3].toFloat(), f[4].toFloat(), f[5].toFloat())
            }
            DesktopProfile(items, density, dock)
        }.getOrNull()
    }

    // Ids hold '/', '.', '#' and maybe separators; escape the four separators we use.
    private fun esc(s: String) = s.replace("\\", "\\\\").replace("|", "\\p").replace(";", "\\s").replace(",", "\\c")
    private fun unesc(s: String): String {
        val sb = StringBuilder(); var i = 0
        while (i < s.length) {
            val ch = s[i]
            if (ch == '\\' && i + 1 < s.length) {
                sb.append(when (s[i + 1]) { 'p' -> '|'; 's' -> ';'; 'c' -> ','; else -> '\\' }); i += 2
            } else { sb.append(ch); i++ }
        }
        return sb.toString()
    }
    private fun splitEsc(s: String): List<String> = s.split(",")
}

/** Minimal key-value seam so the repository is testable without Android. */
interface KeyValue {
    fun get(key: String): String?
    fun put(key: String, value: String)
}

class MapKeyValue(val map: MutableMap<String, String> = mutableMapOf()) : KeyValue {
    override fun get(key: String) = map[key]
    override fun put(key: String, value: String) { map[key] = value }
}

/**
 * Per-display profiles. Each display identity has its own record, so a disconnect keeps it and a reconnect
 * restores it (EXT-06), and the phone's home store is never touched (EXT-03).
 */
class DesktopProfileRepository(private val kv: KeyValue) {
    fun load(identity: String): DesktopProfile = ProfileCodec.decode(kv.get(PREFIX + identity)) ?: DesktopProfile()
    fun save(identity: String, profile: DesktopProfile) = kv.put(PREFIX + identity, ProfileCodec.encode(profile))
    fun update(identity: String, f: (DesktopProfile) -> DesktopProfile): DesktopProfile = f(load(identity)).also { save(identity, it) }
    companion object { const val PREFIX = "profile:" }
}
