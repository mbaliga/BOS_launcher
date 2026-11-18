package com.bos.sphere.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.test.assertTouchHeightIsEqualTo
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Wave 29 (timelineClean): the usage block's legend (one entry per line, no overlap at any text or Comms size) and the Timeline's flat filter row.
 * Screenshots: `build/screenshots/w29-*.png`.
 */
@OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
abstract class UsageLegendW29Cases(private val orientation: String) : LaunchpadShots() {
    private fun host(dark: Boolean = true, fontScale: Float = 1f, percent: Int = 100, filters: Boolean = false, filterWidth: Int = 0, blockWidth: Int = 0, repo: UsageWeekRepo = UsageFixtures.repo()) {
        rule.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, fontScale)) {
                CommsScaleHost(percent) {
                    Themed(dark) {
                        Column((if (blockWidth > 0) Modifier.width(blockWidth.dp) else Modifier.fillMaxSize()).background(LocalLp.current.bg).padding(horizontal = 20.dp)) {
                            UsageTimelineBlock(repo, UsageFixtures.labelOf, now = { UsageFixtures.now }, zone = UsageFixtures.zone)
                            if (filters) Box(if (filterWidth > 0) Modifier.width(filterWidth.dp) else Modifier) { sampleFilters() }
                            Text("first timeline row", color = LocalLp.current.ink, modifier = Modifier.testTag("timeline.row.0"))
                        }
                    }
                }
            }
        }
        rule.waitUntil(10_000) { rule.onAllNodesWithTag("usage.stat").fetchSemanticsNodes().isNotEmpty() }
    }

    private var depth by mutableStateOf("30")
    private var kind by mutableStateOf(true)

    @androidx.compose.runtime.Composable
    private fun sampleFilters() {
        CommsFilterRow(listOf(
            listOf("30" to "Last 30 opens", "100" to "Last 100 opens", "All" to "Everything recorded, up to 5000").map { (l, d) -> FilterOpt(l, d, depth == l, "timeline.filter.range.$l") { depth = l } },
            listOf(true, false).map { s -> FilterOpt(if (s) "Scheduled" else "Opens only", if (s) "With scheduled launches" else "Opens only", kind == s, "timeline.filter.kind.$s") { kind = s } },
        ))
    }

    private fun texts(prefix: String = "usage.") = rule.onAllNodesWithTag("usage.block", useUnmergedTree = true).fetchSemanticsNodes().single().let { block ->
        fun walk(n: androidx.compose.ui.semantics.SemanticsNode, out: MutableList<Pair<String, Rect>>) {
            if (n.config.getOrNull(SemanticsProperties.Text) != null) out += n.config[SemanticsProperties.Text].joinToString("") to n.boundsInRoot
            n.children.forEach { walk(it, out) }
        }
        mutableListOf<Pair<String, Rect>>().also { walk(block, it) }
    }

    private fun assertNoOverlap(label: String) {
        val t = texts()
        assertTrue("$label: text nodes found: ${t.size}", t.size >= 10)
        for (i in t.indices) for (j in i + 1 until t.size) {
            val a = t[i].second; val b = t[j].second
            val x = minOf(a.right, b.right) - maxOf(a.left, b.left); val y = minOf(a.bottom, b.bottom) - maxOf(a.top, b.top)
            assertFalse("$label: '${t[i].first}' $a overlaps '${t[j].first}' $b", x > 0.5f && y > 0.5f)
        }
        // The legend starts below the stack's label row, and the block ends above the next row.
        val stack = rule.onNodeWithTag("usage.stack.part.0").getUnclippedBoundsInRoot(); val first = rule.onNodeWithTag("usage.legend.key.0").getUnclippedBoundsInRoot()
        assertTrue("$label: legend ${first.top} under the stack ${stack.bottom}", first.top.value >= stack.bottom.value - 0.5f)
        val block = rule.onNodeWithTag("usage.block").getUnclippedBoundsInRoot(); val row = rule.onNodeWithTag("timeline.row.0").getUnclippedBoundsInRoot()
        assertTrue("$label: block above the list", block.bottom.value <= row.top.value + 0.5f)
    }

    private fun overlapAt(fs: Float, pct: Int) { host(fontScale = fs, percent = pct); assertNoOverlap("$orientation font $fs comms $pct"); capture("w29-legend-font$fs-comms$pct") }
    @Test fun USG_30_noTwoTextNodesOverlapAtFont1Comms75() = overlapAt(1f, 75)
    @Test fun USG_30_noTwoTextNodesOverlapAtFont1Comms100() = overlapAt(1f, 100)
    @Test fun USG_30_noTwoTextNodesOverlapAtFont1Comms125() = overlapAt(1f, 125)
    @Test fun USG_30_noTwoTextNodesOverlapAtFont2Comms75() = overlapAt(2f, 75)
    @Test fun USG_30_noTwoTextNodesOverlapAtFont2Comms100() = overlapAt(2f, 100)
    @Test fun USG_30_noTwoTextNodesOverlapAtFont2Comms125() = overlapAt(2f, 125)

    @Test fun USG_31_theLegendIsOneEntryPerLineEachAtLeast24dpWith8dpGapsAndTheBlockGrowsWithThem() {
        host()
        val keys = (0..4).map { rule.onNodeWithTag("usage.legend.key.$it").getUnclippedBoundsInRoot() }
        keys.forEachIndexed { i, b -> assertTrue("line $i is ${b.bottom.value - b.top.value} dp", b.bottom.value - b.top.value >= 23.5f) }
        (0..3).forEach { i -> assertEquals("8 dp between line $i and the next", 8f, keys[i + 1].top.value - keys[i].bottom.value, 0.6f) }
        val block = rule.onNodeWithTag("usage.block").getUnclippedBoundsInRoot()
        assertTrue("the block holds all five lines: ${block.bottom} vs ${keys[4].bottom}", block.bottom.value >= keys[4].bottom.value)
        assertTrue("block height ${block.bottom.value - block.top.value} dp at 100 percent with five lines", (block.bottom.value - block.top.value) in 230f..300f)
        (0..4).forEach { rule.onNodeWithTag("usage.legend.key.$it").assertTouchHeightIsEqualTo(48.dp) }
    }

    private fun legendPercents() = (0..4).filter { rule.onAllNodesWithTag("usage.legend.percent.$it", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }

    @Test fun USG_32_thePercentageIsPrintedInOnePlaceOnlyTheStackLabelsAreCanvasSoNoTextNodeRepeatsThem() {
        host(fontScale = 2f, percent = 125, blockWidth = 300)
        val inLegend = legendPercents()
        val widths = com.bos.sphere.ui.viz.VizStackGeometry.widths(UsageFixtures.week().parts(6).map { it.ms.toDouble() }, 300f)
        assertTrue("the narrow parts (${widths.map { it.toInt() }}) go to the legend at large text: $inLegend", inLegend.isNotEmpty() && inLegend.size < 5)
        assertTrue("the legend ones are the narrowest: $inLegend", inLegend == (5 - inLegend.size..4).toList())
        val pct = texts().map { it.first }.filter { it.endsWith("%") }
        assertEquals("only the legend's percentages are text nodes: $pct", inLegend.size, pct.size)
    }

    @Test fun USG_32_atNormalSizeTheLegendCarriesAPercentageOnlyForTheNarrowestParts() {
        host(blockWidth = 300)
        assertTrue("legend percentages ${legendPercents()}", legendPercents().let { l -> l == (5 - l.size..4).toList() })
    }

    @Test fun USG_33_tappingALegendLineHighlightsItsPartAndTappingAgainClears() {
        host()
        fun sel(i: Int) = rule.onNodeWithTag("usage.legend.key.$i").fetchSemanticsNode().config.getOrNull(SemanticsProperties.Selected)
        assertEquals(false, sel(1))
        rule.onNodeWithTag("usage.legend.key.1").performClick(); rule.waitForIdle()
        assertEquals(true, sel(1)); assertEquals(false, sel(2))
        rule.onNodeWithTag("usage.legend.key.1").performClick(); rule.waitForIdle()
        assertEquals(false, sel(1))
    }

    @Test fun TLF_01_theFilterRowIsOneFlatRowOfTextOptionsWithOneSelectedPerGroupAndNoWrap() {
        host(filters = true)
        val ids = listOf("timeline.filter.range.30", "timeline.filter.range.100", "timeline.filter.range.All", "timeline.filter.kind.true", "timeline.filter.kind.false")
        val b = ids.map { rule.onNodeWithTag(it).getUnclippedBoundsInRoot() }
        assertTrue("one row: tops ${b.map { it.top }}", b.all { it.top == b[0].top })
        assertTrue("height of an option is about 32 dp: ${b[0].bottom.value - b[0].top.value}", (b[0].bottom.value - b[0].top.value) in 30f..36f)
        ids.forEach { rule.onNodeWithTag(it).assertTouchHeightIsEqualTo(48.dp) }
        fun sel(id: String) = rule.onNodeWithTag(id).fetchSemanticsNode().config.getOrNull(SemanticsProperties.Selected)
        assertEquals(listOf(true, false, false, true, false), ids.map { sel(it) })
        assertEquals("Everything recorded, up to 5000", rule.onNodeWithTag("timeline.filter.range.All").fetchSemanticsNode().config[SemanticsProperties.ContentDescription].joinToString())
        assertEquals("ALL", rule.onNodeWithTag("timeline.filter.range.All", useUnmergedTree = true).fetchSemanticsNode().children.first().config[SemanticsProperties.Text].joinToString())
        rule.onNodeWithTag("timeline.filter.range.All").performClick(); rule.waitForIdle()
        assertEquals(listOf(false, false, true, true, false), ids.map { sel(it) })
        rule.onNodeWithTag("timeline.filter.kind.false").performClick(); rule.waitForIdle()
        assertEquals(listOf(false, false, true, false, true), ids.map { sel(it) })
    }

    @Test fun TLF_02_atFontScale2AndCommsScale125TheFilterRowScrollsInsteadOfWrapping() {
        host(fontScale = 2f, percent = 125, filters = true, filterWidth = 280)
        val ids = listOf("timeline.filter.range.30", "timeline.filter.range.100", "timeline.filter.range.All", "timeline.filter.kind.true", "timeline.filter.kind.false")
        val b = ids.map { rule.onNodeWithTag(it).getUnclippedBoundsInRoot() }
        assertTrue("still one row: tops ${b.map { it.top }}", b.all { it.top == b[0].top })
        val rowW = rule.onNodeWithTag("timeline.filter").getUnclippedBoundsInRoot().let { it.right.value - it.left.value }
        assertEquals(280f, rowW, 1f)
        assertTrue("the options are wider than the row (${b.last().right}), so it scrolls ($rowW)", b.last().right.value > rowW)
    }

    @Test fun USG_34_screenshotDarkWithDataAndFilters() { host(dark = true, filters = true); capture("w29-timeline-top-dark") }
    @Test fun USG_34_screenshotLightWithDataAndFilters() { host(dark = false, filters = true); capture("w29-timeline-top-light") }

    private fun capture(name: String): Bitmap {
        rule.mainClock.advanceTimeBy(300); rule.waitForIdle()
        val view = rule.activity.findViewById<android.view.View>(android.R.id.content)
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread { view.draw(Canvas(bmp)) }
        File(outDir, "$name-$orientation.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return bmp
    }
}
