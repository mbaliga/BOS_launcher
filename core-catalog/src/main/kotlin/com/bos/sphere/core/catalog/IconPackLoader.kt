package com.bos.sphere.core.catalog

import android.content.Context
import android.content.Intent
import android.content.res.XmlResourceParser
import android.graphics.drawable.Drawable

/** HOM-03: finds installed icon packs (ADW / Nova style) and loads their appfilter mapping and drawables. */
class IconPackLoader(context: Context) {
    private val app = context.applicationContext
    private val pm = app.packageManager

    /** Installed packs: package name to label. */
    fun installed(): List<Pair<String, String>> {
        val seen = LinkedHashMap<String, String>()
        for (action in ACTIONS) {
            pm.queryIntentActivities(Intent(action), 0).forEach {
                val p = it.activityInfo.packageName
                seen.putIfAbsent(p, it.loadLabel(pm).toString())
            }
        }
        return seen.map { it.key to it.value }.sortedBy { it.second.lowercase() }
    }

    fun load(packageName: String): IconPackMap? = runCatching {
        val res = pm.getResourcesForApplication(packageName)
        val id = res.getIdentifier("appfilter", "xml", packageName)
        if (id != 0) {
            val parser = res.getXml(id)
            val out = LinkedHashMap<String, String>()
            parseInto(parser, out)
            IconPackMap(packageName, out)
        } else {
            val text = res.assets.open("appfilter.xml").bufferedReader().use { it.readText() }
            IconPackMap.parse(packageName, text)
        }
    }.getOrNull()

    fun drawable(packageName: String, name: String): Drawable? = runCatching {
        val res = pm.getResourcesForApplication(packageName)
        val id = res.getIdentifier(name, "drawable", packageName)
        if (id == 0) null else res.getDrawable(id, null)
    }.getOrNull()

    private fun parseInto(p: XmlResourceParser, out: MutableMap<String, String>) {
        var ev = p.eventType
        while (ev != XmlResourceParser.END_DOCUMENT) {
            if (ev == XmlResourceParser.START_TAG && p.name == "item") {
                val comp = p.getAttributeValue(null, "component")
                val d = p.getAttributeValue(null, "drawable")
                val m = comp?.let { Regex("ComponentInfo\\{([^/}]+)/([^}]+)}").find(it) }
                if (m != null && d != null) {
                    val pkg = m.groupValues[1]
                    var cls = m.groupValues[2]
                    if (cls.startsWith(".")) cls = pkg + cls
                    out.putIfAbsent("$pkg/$cls", d)
                }
            }
            ev = p.next()
        }
    }

    private companion object {
        val ACTIONS = listOf("org.adw.launcher.THEMES", "com.novalauncher.THEME", "com.gau.go.launcherex.theme")
    }
}
