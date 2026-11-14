package com.bos.sphere.core.data

import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/** Test helpers: read fixtures and parse a real Android SharedPreferences XML file into its `getAll()` map. */
object StateTestSupport {
    fun resource(path: String): String =
        StateTestSupport::class.java.classLoader!!.getResourceAsStream(path)!!.readBytes().toString(Charsets.UTF_8)

    fun prefsXml(path: String): Map<String, Any?> {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(resource(path).byteInputStream())
        val out = LinkedHashMap<String, Any?>()
        val nodes = doc.documentElement.childNodes
        for (i in 0 until nodes.length) {
            val e = nodes.item(i) as? Element ?: continue
            val name = e.getAttribute("name")
            out[name] = when (e.tagName) {
                "string" -> e.textContent
                "boolean" -> e.getAttribute("value").toBoolean()
                "int" -> e.getAttribute("value").toInt()
                "long" -> e.getAttribute("value").toLong()
                "float" -> e.getAttribute("value").toFloat()
                "set" -> {
                    val kids = e.getElementsByTagName("string")
                    (0 until kids.length).map { kids.item(it).textContent }.toSet()
                }
                else -> error("unsupported ${e.tagName}")
            }
        }
        return out
    }

    /** The build-31 files as the device would hold them (including stores that must never be exported). */
    fun build31Files(): Map<String, Map<String, Any?>> = mapOf(
        Build31Bridge.PREFS to prefsXml("build31/launcher_prefs.xml"),
        Build31Bridge.LAYOUT to prefsXml("build31/home_layout.xml"),
        Build31Bridge.SHORTCUTS to prefsXml("build31/shortcuts.xml"),
        Build31Bridge.LAUNCHPAD to prefsXml("build31/launchpad_rules.xml"),
        "launch_history" to prefsXml("build31/launch_history.xml"),
        "weather" to prefsXml("build31/weather.xml"),
    )

    class FakeLaunchpad : LaunchpadSink {
        val rules = ArrayList<Rule>()
        var mode = GroupMode.Time
        val per = HashMap<String, GroupMode>()
        val gestures = HashMap<LaunchpadGesture, LaunchpadAction>()
        override fun rules() = rules.toList()
        override fun removeRule(id: String) { rules.removeAll { it.id == id } }
        override fun addRule(r: Rule) { rules += r }
        override fun setDefaultMode(m: GroupMode) { mode = m }
        override fun perApp() = per.toMap()
        override fun setAppMode(pkg: String, m: GroupMode?) { if (m == null) per.remove(pkg) else per[pkg] = m }
        override fun setGesture(g: LaunchpadGesture, a: LaunchpadAction) { gestures[g] = a }
        var held: List<AppPolicy> = emptyList()
        override fun policies() = held
        override fun setPolicies(list: List<AppPolicy>) { held = list }
    }

    /** A complete set of stores over in-memory preferences, standing in for one app process. */
    class Process(
        val prefsSp: MapSharedPreferences = MapSharedPreferences(),
        val layoutSp: MapSharedPreferences = MapSharedPreferences(),
        val shortcutsSp: MapSharedPreferences = MapSharedPreferences(),
        val launchpad: FakeLaunchpad = FakeLaunchpad(),
        val extrasFile: File,
    ) {
        val prefs = LauncherPrefsStore(prefsSp)
        val layout = HomeLayoutStore(layoutSp)
        val shortcuts = ShortcutStore(shortcutsSp)
        val extras = StateExtrasStore(extrasFile)
        val applier = StateApplier(prefs, layout, shortcuts, launchpad, extras::read, extras::write, { LaunchpadPrefs(launchpad.mode, launchpad.per) }, { LaunchpadGestures.defaults + launchpad.gestures })
    }
}
