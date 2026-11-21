package com.bos.sphere.core.catalog

import com.bos.sphere.core.data.GridSpec
import com.bos.sphere.core.data.HomePage
import com.bos.sphere.core.data.PageLayout
import com.bos.sphere.core.data.PageShape

/** Line-based, dependency-free persistence of a [HomeState] (survives process death, HOM-02 / HOM-11). */
object HomeStateCodec {
    private const val SEP = '\t'
    private const val LIST = '\u001e'

    private fun esc(s: String) = s.replace("%", "%25").replace("\t", "%09").replace("\n", "%0A").replace("\u001e", "%1E")
    private fun unesc(s: String) = s.replace("%1E", "\u001e").replace("%0A", "\n").replace("%09", "\t").replace("%25", "%")
    private fun list(l: List<String>) = l.joinToString(LIST.toString()) { esc(it) }
    private fun unlist(s: String) = if (s.isEmpty()) emptyList() else s.split(LIST).map(::unesc)
    private fun spec(g: GridSpec?) = g?.let { "${it.columns}x${it.rows}" } ?: "-"
    private fun unspec(s: String): GridSpec? = s.split('x').takeIf { it.size == 2 }?.let { p -> p[0].toIntOrNull()?.let { c -> p[1].toIntOrNull()?.let { r -> GridSpec(c, r).coerced() } } }

    fun encode(s: HomeState): String = buildString {
        s.pages.forEach { p ->
            append("P").append(SEP).append(p.id).append(SEP).append(p.layout.shape.name).append(SEP).append(p.layout.rings).append(SEP)
                .append(spec(p.layout.portrait)).append(SEP).append(spec(p.layout.landscape)).append(SEP).append(list(p.keys)).append('\n')
        }
        s.folders.values.forEach { f -> append("F").append(SEP).append(esc(f.id)).append(SEP).append(esc(f.name)).append(SEP).append(list(f.keys)).append('\n') }
        s.items.values.forEach { i ->
            append("I").append(SEP).append(esc(i.key)).append(SEP).append(i.kind.name).append(SEP).append(esc(i.label)).append(SEP)
                .append(esc(i.target)).append(SEP).append(if (i.isFile) "1" else "0").append('\n')
        }
        s.widgets.forEach { (k, w) -> append("W").append(SEP).append(esc(k)).append(SEP).append(w.cols).append(SEP).append(w.rows).append('\n') }
        if (s.dock.isNotEmpty()) append("D").append(SEP).append(list(s.dock)).append('\n')
    }

    fun decode(raw: String?): HomeState {
        if (raw.isNullOrEmpty()) return HomeState()
        val pages = ArrayList<HomePage>()
        val folders = LinkedHashMap<String, Folder>()
        val items = LinkedHashMap<String, ItemDef>()
        val widgets = LinkedHashMap<String, WidgetSpan>()
        var dock = emptyList<String>()
        for (line in raw.lineSequence()) {
            val f = line.split(SEP)
            runCatching {
                when (f[0]) {
                    "P" -> pages += HomePage(
                        keys = unlist(f[6]),
                        layout = PageLayout(
                            shape = PageShape.entries.firstOrNull { it.name == f[2] } ?: PageShape.Grid,
                            portrait = unspec(f[4]), landscape = unspec(f[5]),
                            rings = f[3].toIntOrNull()?.coerceIn(PageLayout.MIN_RINGS, PageLayout.MAX_RINGS) ?: 2,
                        ),
                        id = f[1].toLongOrNull() ?: 0L,
                    )
                    "F" -> unesc(f[1]).let { id -> folders[id] = Folder(id, unesc(f[2]), unlist(f[3])) }
                    "I" -> unesc(f[1]).let { k -> items[k] = ItemDef(k, ItemKind.entries.firstOrNull { it.name == f[2] } ?: ItemKind.App, unesc(f[3]), unesc(f[4]), f[5] == "1") }
                    "W" -> widgets[unesc(f[1])] = WidgetSpan(f[2].toInt().coerceAtLeast(1), f[3].toInt().coerceAtLeast(1))
                    "D" -> dock = unlist(f[1])
                }
            }
        }
        return HomeState(pages, folders, items, widgets, dock)
    }
}
