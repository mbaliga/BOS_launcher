package com.bos.sphere.feature.homewidgets.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Checks the widget layouts as files, because a RemoteViews mistake only shows inside the launcher
 * ("Can't load widget"): only views RemoteViews can inflate, every id the renderer sets exists in
 * the layout for that size, text is at least 12sp and ellipsizes, images are labelled or hidden
 * from TalkBack, tappable pads and rows are 48 dp, and every card is the system background view.
 */
class WidgetLayoutsTest {

    private val dir = listOf(File("src/main/res/layout"), File("feature-homewidgets/src/main/res/layout")).first { it.isDirectory }
    private val a = "http://schemas.android.com/apk/res/android"

    private fun root(name: String): Element =
        DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }.newDocumentBuilder()
            .parse(File(dir, "$name.xml")).documentElement

    private fun Element.all(): List<Element> {
        val out = mutableListOf(this)
        val kids = childNodes
        for (i in 0 until kids.length) (kids.item(i) as? Element)?.let { out += it.all() }
        return out
    }

    private fun Element.attr(name: String): String? = getAttributeNS(a, name).takeIf { it.isNotEmpty() }
    private fun ids(name: String) = root(name).all().mapNotNull { it.attr("id")?.removePrefix("@+id/") }.toSet()

    private val widgetLayouts = dir.listFiles()!!.map { it.nameWithoutExtension }.filter { it.startsWith("hw_") }.sorted()

    private val remotable = setOf(
        "FrameLayout", "LinearLayout", "RelativeLayout", "GridLayout", "TextView", "ImageView", "ImageButton",
        "Button", "ProgressBar", "TextClock", "Chronometer", "ViewFlipper", "ListView", "GridView", "StackView",
        "AdapterViewFlipper", "AnalogClock", "CheckBox", "Switch", "RadioButton", "RadioGroup",
    )

    private val s = Tier.SMALL
    private val m = Tier.MEDIUM
    private val l = Tier.LARGE
    private fun rows(n: Int) = (1..n).flatMap { listOf("hw_ny_row$it", "hw_ny_row${it}_label", "hw_ny_row${it}_count") }
    private fun pads(n: Int, labels: Boolean) = (1..n).flatMap { listOfNotNull("hw_pad_$it", "hw_pad_${it}_icon", if (labels) "hw_pad_${it}_label" else null) }

    /** The ids Renderers sets in each layout. */
    private val needed: Map<String, List<String>> = mapOf(
        "hw_state" to listOf("hw_state_glyph", "hw_state_title", "hw_state_body", "hw_state_asof"),
        "hw_state_s" to listOf("hw_state_glyph", "hw_state_title", "hw_state_body"),
        "hw_clock_s" to listOf("hw_clock_time"),
        "hw_clock_m" to listOf("hw_clock_time", "hw_clock_date"),
        "hw_clock_l" to listOf("hw_clock_time", "hw_clock_date", "hw_clock_alarm", "hw_clock_alarm_glyph"),
        "hw_battery_s" to listOf("hw_battery_glyph", "hw_battery_percent", "hw_battery_state", "hw_battery_asof"),
        "hw_battery_m" to listOf("hw_battery_glyph", "hw_battery_percent", "hw_battery_state", "hw_battery_asof", "hw_battery_bar"),
        "hw_battery_l" to listOf("hw_battery_glyph", "hw_battery_percent", "hw_battery_state", "hw_battery_asof", "hw_battery_bar", "hw_battery_detail", "hw_battery_temp"),
        "hw_np_s" to listOf("hw_np_state_glyph", "hw_np_title", "hw_np_sub"),
        "hw_np_m" to listOf("hw_np_state_glyph", "hw_np_eyebrow", "hw_np_title", "hw_np_artist", "hw_np_state"),
        "hw_np_l" to listOf("hw_np_state_glyph", "hw_np_eyebrow", "hw_np_title", "hw_np_artist", "hw_np_state", "hw_np_prev", "hw_np_playpause", "hw_np_next"),
        "hw_ny_s" to listOf("hw_ny_message", "hw_ny_message_sub"),
        "hw_ny_m" to listOf("hw_ny_total", "hw_ny_message", "hw_ny_message_sub", "hw_ny_footer") + rows(NeedsYouModel.rowsFor(m)),
        "hw_ny_l" to listOf("hw_ny_total", "hw_ny_message", "hw_ny_message_sub", "hw_ny_footer") + rows(NeedsYouModel.rowsFor(l)),
        "hw_pads_s" to pads(PadsModel.padsFor(s), labels = false),
        "hw_pads_m" to pads(PadsModel.padsFor(m), labels = false),
        "hw_pads_l" to pads(PadsModel.padsFor(l), labels = true),
    )

    @Test
    fun everyKindHasASmallMediumAndLargeLayoutAndAPreview() {
        for (prefix in listOf("hw_clock", "hw_battery", "hw_np", "hw_ny", "hw_pads")) {
            for (suffix in listOf("s", "m", "l", "preview")) assertTrue("${prefix}_$suffix", "${prefix}_$suffix" in widgetLayouts)
        }
    }

    @Test
    fun theRendererFindsEveryViewItSets() {
        for ((layout, wanted) in needed) {
            val have = ids(layout)
            val missing = wanted.filter { it !in have }
            assertTrue("$layout is missing $missing", missing.isEmpty())
        }
    }

    @Test
    fun onlyViewsRemoteViewsCanInflate() {
        for (name in widgetLayouts) {
            for (e in root(name).all()) assertTrue("$name: <${e.tagName}>", e.tagName in remotable)
        }
    }

    @Test
    fun everyCardIsTheSystemBackgroundView() {
        for (name in widgetLayouts) {
            val r = root(name)
            assertEquals(name, "@android:id/background", r.attr("id"))
            assertEquals(name, "@drawable/hw_bg", r.attr("background"))
            assertEquals(name, "true", r.attr("clipToOutline"))
        }
    }

    @Test
    fun textIsAtLeastTwelveSpAndEllipsizes() {
        for (name in widgetLayouts) {
            for (e in root(name).all().filter { it.tagName == "TextView" || it.tagName == "TextClock" }) {
                val size = e.attr("textSize")
                val min = e.attr("autoSizeMinTextSize")
                val sp = (size ?: min ?: fail("$name ${e.attr("id")}: no text size").toString()).removeSuffix("sp").toFloat()
                assertTrue("$name ${e.attr("id")}: ${sp}sp", sp >= 12f)
                assertTrue("$name ${e.attr("id")}: text in sp", (size ?: min)!!.endsWith("sp"))
                assertTrue("$name ${e.attr("id")}: maxLines", e.attr("maxLines") != null)
            }
        }
    }

    @Test
    fun imagesAreLabelledOrHiddenFromTalkBack() {
        for (name in widgetLayouts) {
            for (e in root(name).all().filter { it.tagName == "ImageView" || it.tagName == "ImageButton" }) {
                val labelled = e.attr("contentDescription") != null || e.attr("importantForAccessibility") == "no"
                assertTrue("$name ${e.attr("id")}", labelled)
            }
        }
    }

    /** Pad layouts whose pads fill the height they are given: tier and number of pad rows. */
    private val fullHeightPads = mapOf("hw_pads_s" to (s to 1), "hw_pads_m" to (m to 1), "hw_pads_l" to (l to 2))

    private fun dp(value: String?): Float = when {
        value == null -> 0f
        value == "@dimen/hw_padding" -> 12f
        value == "@dimen/hw_touch" -> 48f
        value.endsWith("dp") -> value.removeSuffix("dp").toFloat()
        else -> throw AssertionError("unknown dimension $value")
    }

    private fun Element.verticalPadding(): Float =
        (attr("padding") ?: attr("paddingVertical"))?.let { 2 * dp(it) } ?: (dp(attr("paddingTop")) + dp(attr("paddingBottom")))

    @Test
    fun padsRowsAndControlsAreFortyEightDp() {
        for (name in widgetLayouts) {
            val r = root(name)
            fun check(e: Element, parent: Element?) {
                if (e.attr("background") == "@drawable/hw_pad_bg") {
                    // A pad that fills its parent: minHeight is ignored under an exact height, so it
                    // counts only where fullHeightPadsFitAtTheirSmallestKey checks the size key (or
                    // in a picker preview, which takes no taps).
                    val filling = e.attr("layout_height") == "match_parent" && e.attr("minHeight") == "@dimen/hw_touch" &&
                        (name in fullHeightPads || name.endsWith("_preview"))
                    val ok = filling || e.attr("layout_height") == "@dimen/hw_touch" ||
                        (e.attr("layout_height") == "match_parent" && parent?.attr("layout_height") == "@dimen/hw_touch")
                    assertTrue("$name ${e.attr("id")} is shorter than 48 dp", ok)
                }
                val kids = e.childNodes
                for (i in 0 until kids.length) (kids.item(i) as? Element)?.let { check(it, e) }
            }
            check(r, null)
        }
    }

    @Test
    fun fullHeightPadsFitAtTheirSmallestKey() {
        // At the layout's own size key (the smallest size it is shown at), each row of pads gets
        // the key height less the root's vertical padding and the gaps between rows: ≥ 48 dp.
        for ((name, tierRows) in fullHeightPads) {
            val (tier, padRows) = tierRows
            val r = root(name)
            val gaps = r.childNodes.let { kids -> (0 until kids.length).mapNotNull { kids.item(it) as? Element } }
                .sumOf { dp(it.attr("layout_marginTop")).toDouble() }.toFloat()
            val perRow = (WidgetKind.PADS.key(tier).minHeight - r.verticalPadding() - gaps) / padRows
            assertTrue("$name: $perRow dp per row of pads", perRow >= 48f)
        }
    }
}
