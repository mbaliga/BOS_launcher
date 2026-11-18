package com.bos.sphere.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertTouchHeightIsEqualTo
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.concurrent.CountDownLatch

/**
 * Wave 29 (usageSimplify, owner: "The usage block is a mess and hard to read"): the block is three parts (the day line, the week columns, the
 * ranked list of apps) and a hairline; it never overlaps or clips at any text size or Comms size, holds its height while loading, and its rows
 * select. Screenshots: `build/screenshots/w29-simplify-*.png` (read by eye against the owner's complaint).
 */
@OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
abstract class UsageSimplifyW29Cases(private val orientation: String) : LaunchpadShots() {
    private fun host(dark: Boolean = true, fontScale: Float = 1f, percent: Int = 100, blockWidth: Int = 0, rtl: Boolean = false, repo: UsageWeekRepo = UsageFixtures.repo(), wait: String? = "usage.stat") {
        rule.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, fontScale), LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                CommsScaleHost(percent) {
                    Themed(dark) {
                        Column((if (blockWidth > 0) Modifier.width(blockWidth.dp) else Modifier.fillMaxSize()).background(LocalLp.current.bg).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
                            UsageTimelineBlock(repo, UsageFixtures.labelOf, now = { UsageFixtures.now }, zone = UsageFixtures.zone)
                            Text("first timeline row", color = LocalLp.current.ink, modifier = Modifier.testTag("timeline.row.0"))
                        }
                    }
                }
            }
        }
        if (wait != null) rule.waitUntil(10_000) { rule.onAllNodesWithTag(wait).fetchSemanticsNodes().isNotEmpty() }
    }

    /** Inner parts of a row sit under its merged node: look them up unmerged. */
    private fun bounds(tag: String) = rule.onNodeWithTag(tag, useUnmergedTree = true).getUnclippedBoundsInRoot()
    /** The node's whole rectangle in px, not clipped to the (landscape, short) root like `boundsInRoot`. */
    private fun unclipped(n: androidx.compose.ui.semantics.SemanticsNode) = Rect(n.positionInRoot, androidx.compose.ui.geometry.Size(n.size.width.toFloat(), n.size.height.toFloat()))
    private fun px(tag: String) = unclipped(rule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode())
    private fun has(tag: String) = rule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
    private fun count(prefix: String) = (0..9).count { has("$prefix.$it") }

    private fun texts() = rule.onAllNodesWithTag("usage.block", useUnmergedTree = true).fetchSemanticsNodes().single().let { block ->
        fun walk(n: androidx.compose.ui.semantics.SemanticsNode, out: MutableList<Pair<String, Rect>>) {
            if (n.config.getOrNull(SemanticsProperties.Text) != null) out += n.config[SemanticsProperties.Text].joinToString("") to unclipped(n)
            n.children.forEach { walk(it, out) }
        }
        mutableListOf<Pair<String, Rect>>().also { walk(block, it) }
    }

    private fun capture(name: String): Bitmap {
        rule.mainClock.advanceTimeBy(300); rule.waitForIdle()
        val view = rule.activity.findViewById<android.view.View>(android.R.id.content)
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread { view.draw(Canvas(bmp)) }
        File(outDir, "w29-simplify-$name-$orientation.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return bmp
    }

    // ---- exactly three parts ----

    @Test fun USG_41_theBlockHasExactlyThreePartsInOrderAndNoStackOrLegend() {
        host()
        val stat = bounds("usage.stat"); val chart = bounds("usage.chart"); val rows = bounds("usage.rows"); val hair = bounds("usage.hairline")
        assertTrue("day line above the week", stat.bottom.value <= chart.top.value + 0.5f)
        assertTrue("week above the list", chart.bottom.value <= rows.top.value + 0.5f)
        assertTrue("hairline under the list", rows.bottom.value <= hair.top.value + 0.5f)
        assertEquals("12 dp between the day line and the week", 12f, chart.top.value - stat.bottom.value, 0.6f)
        assertEquals("12 dp between the week and the list", 12f, rows.top.value - chart.bottom.value, 0.6f)
        assertEquals("a hairline under the block", 1f, hair.bottom.value - hair.top.value, 0.1f)
        assertEquals(0, count("usage.stack.part") + count("usage.legend.key") + count("usage.legend.name"))
        val block = bounds("usage.block"); val first = bounds("timeline.row.0")
        assertTrue("the block ends above the first list row", block.bottom.value <= first.top.value + 0.5f)
        assertEquals("16 dp... the same side margin as the list (the host's 20 dp)", 20f, block.left.value, 0.5f)
    }

    @Test fun USG_41_noPercentageIsShownOrSpokenAnywhere() {
        host()
        assertTrue("no % text node: ${texts().map { it.first }}", texts().none { "%" in it.first })
        val summary = rule.onNodeWithTag("usage.chart").fetchSemanticsNode().config[SemanticsProperties.ContentDescription].joinToString()
        assertFalse(summary, "percent" in summary)
        (0..4).forEach { assertFalse("percent" in rule.onNodeWithTag("usage.row.$it").fetchSemanticsNode().config[SemanticsProperties.ContentDescription].joinToString()) }
    }

    @Test fun USG_42_theDayLineIsTheDayNameInMutedInkAndTheTotalAsTheOneBigFigureOnTheLeft() {
        host()
        val caption = texts().first { it.first == "Today" }; val figure = texts().first { it.first == "2 h 34 min" }
        assertTrue("left aligned: ${figure.second.left} vs the block's left edge", figure.second.left <= caption.second.right + 24f * rule.density.density && caption.second.left <= 22f * rule.density.density)
        assertTrue("the figure is the biggest text of the block", figure.second.height > texts().filter { it.first != "2 h 34 min" }.maxOf { it.second.height })
        assertTrue("the figure is at least 28 dp tall type: ${figure.second.height / rule.density.density} dp", figure.second.height / rule.density.density >= 28f)
    }

    @Test fun USG_43_theListHasFiveRowsOfAtLeast36dpWithA20dpIconAnA4dpBarAndTheNameEllipsizedBeforeTheDuration() {
        host()
        assertEquals(5, count("usage.row"))
        (0..4).forEach { i ->
            val r = bounds("usage.row.$i"); val ic = bounds("usage.row.icon.$i"); val bar = bounds("usage.row.bar.$i")
            assertTrue("row $i is ${r.bottom.value - r.top.value} dp high", r.bottom.value - r.top.value >= 35.5f)
            assertEquals("icon $i", 20f, ic.right.value - ic.left.value, 0.5f); assertEquals(20f, ic.bottom.value - ic.top.value, 0.5f)
            assertEquals("bar $i is 4 dp", 4f, bar.bottom.value - bar.top.value, 0.3f)
            val name = bounds("usage.row.name.$i"); val value = bounds("usage.row.value.$i")
            assertTrue("name $i ends before its duration: ${name.right} ${value.left}", name.right.value <= value.left.value + 0.5f)
            assertTrue("the bar is under the text", bar.top.value >= name.bottom.value - 0.5f)
            assertTrue("the duration is right-aligned with the bar's end", Math.abs(value.right.value - bar.right.value) < 1f)
        }
        (0..3).forEach { assertTrue("rows are stacked without overlap", bounds("usage.row.$it").bottom.value <= bounds("usage.row.${it + 1}").top.value + 0.5f) }
        assertEquals("Other", rule.onNodeWithTag("usage.row.name.4", useUnmergedTree = true).fetchSemanticsNode().config[SemanticsProperties.Text].joinToString(""))
    }

    @Test fun USG_43_aLongAppNameEllipsizesAtNarrowWidthAndNeverPushesTheDurationOut() {
        val long = UsageFixtures.repo(UsageFixtures.FakeSource(data = mapOf(0 to mapOf("demo.long" to 90, "demo.chat" to 10))))
        rule.setContent {
            Themed(true) { Column(Modifier.width(240.dp).background(LocalLp.current.bg)) {
                UsageTimelineBlock(long, { if (it == "demo.long") "An extraordinarily long launcher label that cannot fit" else UsageFixtures.labelOf(it) }, now = { UsageFixtures.now }, zone = UsageFixtures.zone)
            } }
        }
        rule.waitUntil(10_000) { has("usage.stat") }
        val name = bounds("usage.row.name.0"); val value = bounds("usage.row.value.0"); val row = bounds("usage.row.0")
        assertTrue("the duration stays inside the row: ${value.right} <= ${row.right}", value.right.value <= row.right.value + 0.5f)
        assertTrue("the name stops before it", name.right.value <= value.left.value + 0.5f)
        assertTrue("the name still has room (${name.right.value - name.left.value} dp)", name.right.value - name.left.value >= 40f)
    }

    // ---- no overlap at any size ----

    private fun assertNoOverlap(label: String, columnsInside: Boolean = true) {
        val t = texts()
        assertTrue("$label: text nodes found: ${t.size}", t.size >= 12)
        for (i in t.indices) for (j in i + 1 until t.size) {
            val a = t[i].second; val b = t[j].second
            val x = minOf(a.right, b.right) - maxOf(a.left, b.left); val y = minOf(a.bottom, b.bottom) - maxOf(a.top, b.top)
            assertFalse("$label: '${t[i].first}' $a overlaps '${t[j].first}' $b", x > 0.5f && y > 0.5f)
        }
        // The three parts never intersect, and each text lies inside the block and the screen.
        val parts = listOf("usage.stat", "usage.chart", "usage.rows").map { it to bounds(it) }
        for (i in parts.indices) for (j in i + 1 until parts.size) {
            val a = parts[i].second; val b = parts[j].second
            assertTrue("$label: ${parts[i].first} $a intersects ${parts[j].first} $b", a.bottom.value <= b.top.value + 0.5f)
        }
        val block = bounds("usage.block"); val row = bounds("timeline.row.0"); val blockPx = px("usage.block")
        assertTrue("$label: block above the list", block.bottom.value <= row.top.value + 0.5f)
        t.forEach { (s, r) -> assertTrue("$label: '$s' $r is inside the block $blockPx", r.left >= blockPx.left - 1f && r.right <= blockPx.right + 1f && r.bottom <= blockPx.bottom + 1f) }
        (0..4).filter { has("usage.row.$it") }.forEach { i -> val c = bounds("usage.row.$i"); assertTrue("$label: row $i inside the rows part", c.top.value >= bounds("usage.rows").top.value - 0.5f && c.bottom.value <= bounds("usage.rows").bottom.value + 0.5f) }
        if (columnsInside && has("usage.steps.col.6")) { val last = px("usage.steps.col.6"); assertTrue("$label: the last day column ends at ${last.right} but the block at ${blockPx.right}", last.right <= blockPx.right + 1f) }
    }

    private fun overlapAt(fs: Float, pct: Int) { host(fontScale = fs, percent = pct); assertNoOverlap("$orientation font $fs comms $pct"); capture("font$fs-comms$pct") }
    @Test fun USG_44_noTwoTextNodesOverlapAtFont1Comms75() = overlapAt(1f, 75)
    @Test fun USG_44_noTwoTextNodesOverlapAtFont1Comms100() = overlapAt(1f, 100)
    @Test fun USG_44_noTwoTextNodesOverlapAtFont1Comms125() = overlapAt(1f, 125)
    @Test fun USG_44_noTwoTextNodesOverlapAtFont2Comms75() = overlapAt(2f, 75)
    @Test fun USG_44_noTwoTextNodesOverlapAtFont2Comms100() = overlapAt(2f, 100)
    @Test fun USG_44_noTwoTextNodesOverlapAtFont2Comms125() = overlapAt(2f, 125)

    @Test fun USG_44_narrowBlockAtFont2Comms125HasNoOverlapEither() {
        host(fontScale = 2f, percent = 125, blockWidth = 300); assertNoOverlap("narrow", columnsInside = false)
        (0..4).forEach { i -> val b = bounds("usage.row.name.$i"); assertTrue("name $i is only ${b.right.value - b.left.value} dp wide", b.right.value - b.left.value >= 30f) }
    }

    @Test fun USG_44_rowsGrowWithTheFontAndKeepAllFiveAtFont2() {
        host(fontScale = 2f)
        assertEquals(5, count("usage.row"))
        (0..4).forEach { assertTrue("row $it grew to ${bounds("usage.row.$it").let { b -> b.bottom.value - b.top.value }} dp", bounds("usage.row.$it").let { b -> b.bottom.value - b.top.value } > 36f) }
    }

    @Test fun USG_45_theBlockAboutFillsTheHeightTheOwnerAskedForAt100Percent() {
        host()
        val h = bounds("usage.block").let { it.bottom.value - it.top.value }
        assertTrue("block is $h dp high (about 260 to 300 dp with five rows; content-sized, never fixed)", h in 250f..320f)
    }

    // ---- selecting ----

    @Test fun USG_46_selectingAColumnUpdatesTheDayLineAndTheRows() {
        host()
        assertEquals("Browser, 1 h 10 min", rule.onNodeWithTag("usage.row.0").fetchSemanticsNode().config[SemanticsProperties.ContentDescription].joinToString())
        rule.onNodeWithTag("usage.steps.col.5").performClick(); rule.waitForIdle()
        assertEquals("Yesterday, 2 h 5 min", rule.onNodeWithTag("usage.stat").fetchSemanticsNode().config[SemanticsProperties.ContentDescription].joinToString())
        assertEquals(listOf("Chat, 1 h 20 min", "Browser, 30 min", "Mail, 15 min"), (0..2).map { rule.onNodeWithTag("usage.row.$it").fetchSemanticsNode().config[SemanticsProperties.ContentDescription].joinToString() })
        assertEquals(3, count("usage.row"))
    }

    private fun maxBright(bmp: Bitmap, tag: String): Int {
        val d = rule.density.density; val b = bounds(tag); var m = 0
        for (y in (b.top.value * d).toInt() until (b.bottom.value * d).toInt()) for (x in (b.left.value * d).toInt() until (b.right.value * d).toInt()) {
            val p = bmp.getPixel(x, y); m = maxOf(m, ((p shr 16) and 255) + ((p shr 8) and 255) + (p and 255))
        }
        return m
    }

    @Test fun USG_47_selectingARowHighlightsItAndDimsTheOthersAndASecondTapClears() {
        host()
        fun sel(i: Int) = rule.onNodeWithTag("usage.row.$i").fetchSemanticsNode().config.getOrNull(SemanticsProperties.Selected)
        val before = maxBright(capture("rows-before"), "usage.row.bar.1")
        rule.onNodeWithTag("usage.row.0").performClick(); rule.waitForIdle()
        assertEquals(true, sel(0)); assertEquals(false, sel(1))
        val dimmed = maxBright(capture("rows-picked"), "usage.row.bar.1")
        val picked = maxBright(capture("rows-picked"), "usage.row.bar.0")
        assertTrue("the other rows are dimmed ($dimmed vs $before)", dimmed < before * 0.7f)
        assertTrue("the picked row keeps full ink ($picked)", picked >= before * 0.9f)
        rule.onNodeWithTag("usage.row.0").performClick(); rule.waitForIdle()
        assertEquals(false, sel(0)); assertEquals("cleared", before, maxBright(capture("rows-cleared"), "usage.row.bar.1"))
    }

    @Test fun USG_47_aPickBelongsToItsDay() {
        host()
        rule.onNodeWithTag("usage.row.1").performClick(); rule.waitForIdle()
        rule.onNodeWithTag("usage.steps.col.5").performClick(); rule.waitForIdle()
        assertTrue("another day starts with nothing picked", (0..2).none { rule.onNodeWithTag("usage.row.$it").fetchSemanticsNode().config.getOrNull(SemanticsProperties.Selected) == true })
    }

    @Test fun USG_47_aPickThatNoLongerExistsHighlightsNothing() {
        assertEquals(-1, com.bos.sphere.ui.viz.clampPick(4, 3)); assertEquals(2, com.bos.sphere.ui.viz.clampPick(2, 3)); assertEquals(-1, com.bos.sphere.ui.viz.clampPick(-1, 3))
    }

    // ---- bars ----

    @Test fun USG_48_theBarsAreAsLongAsTheirShareOfTheLongestAndStartAtTheReadingStart() {
        host()
        val bmp = capture("bars"); val d = rule.density.density
        fun lit(tag: String, from: Float, to: Float): Boolean {
            val b = bounds(tag); val y0 = (b.top.value * d).toInt(); val y1 = (b.bottom.value * d).toInt()
            val x0 = ((b.left.value + (b.right.value - b.left.value) * from) * d).toInt(); val x1 = ((b.left.value + (b.right.value - b.left.value) * to) * d).toInt()
            for (y in y0 until y1) for (x in x0..x1) { val p = bmp.getPixel(x, y); if (((p shr 16) and 255) + ((p shr 8) and 255) + (p and 255) > 300) return true }
            return false
        }
        // Row 0 is the longest (solid, full): lit along its whole length. Row 1 is 45 of 70: lit at 30 percent, dark at 90.
        assertTrue(lit("usage.row.bar.0", 0.01f, 0.03f)); assertTrue(lit("usage.row.bar.0", 0.95f, 0.97f))
        assertTrue(lit("usage.row.bar.1", 0.02f, 0.2f)); assertTrue(lit("usage.row.bar.1", 0.5f, 0.6f))
        assertFalse("the shorter bar (20 of 70) ends before the end of the track", lit("usage.row.bar.2", 0.5f, 0.97f)); assertFalse(lit("usage.row.bar.1", 0.8f, 0.97f))
    }

    @Test fun USG_48_inARightToLeftLayoutTheListAndItsBarsMirror() {
        host(rtl = true)
        val bmp = capture("rtl"); val d = rule.density.density
        val b = bounds("usage.row.bar.0"); val y = (((b.top.value + b.bottom.value) / 2f) * d).toInt()
        fun at(x: Float) = bmp.getPixel((x * d).toInt(), y).let { ((it shr 16) and 255) + ((it shr 8) and 255) + (it and 255) }
        assertTrue("the full bar is lit at its right (reading start) end", at(b.right.value - 3) > 300)
        val icon = bounds("usage.row.icon.0"); val value = bounds("usage.row.value.0")
        assertTrue("the icon is on the right, the duration on the left", icon.left.value > value.right.value)
        val col = (0..6).map { bounds("usage.steps.col.$it").left.value }
        assertTrue("the week runs right to left: ${col.map { it.toInt() }}", col.zipWithNext().all { (a, c) -> a >= c } || col.zipWithNext().all { (a, c) -> a <= c })
    }

    // ---- states ----

    @Test fun USG_49_withoutPermissionTheBlockShowsAClearAccentTextButtonOf48dp() {
        var asked: com.bos.sphere.core.design.GrantKind? = null
        com.bos.sphere.core.design.GrantHooks.interceptor = { k, _ -> asked = k }
        try {
            host(repo = UsageFixtures.repo(UsageFixtures.FakeSource(granted = false)), wait = "usage.noAccess")
            val b = bounds("usage.allow")
            assertTrue("button is ${b.bottom.value - b.top.value} dp high", b.bottom.value - b.top.value >= 47.5f)
            rule.onNodeWithTag("usage.allow").assertTouchHeightIsEqualTo(48.dp)
            val bmp = capture("missing-dark"); val d = rule.density.density
            var accent = 0
            for (y in (b.top.value * d).toInt() until (b.bottom.value * d).toInt()) for (x in (b.left.value * d).toInt() until (b.right.value * d).toInt()) {
                val p = bmp.getPixel(x, y); if (((p shr 16) and 255) > 200 && ((p shr 8) and 255) > 200 && (p and 255) < 120) accent++
            }
            assertTrue("the label is drawn in the accent yellow ($accent px)", accent > 40)
            rule.onNodeWithTag("usage.allow").performClick(); rule.waitForIdle()
            assertEquals(com.bos.sphere.core.design.GrantKind.UsageAccess, asked)
        } finally { com.bos.sphere.core.design.GrantHooks.interceptor = null }
    }

    @Test fun USG_50_whileLoadingAQuietPlaceholderHoldsTheHeightSoTheListDoesNotJump() {
        val gate = CountDownLatch(1)
        val slow = UsageFixtures.repo(object : UsageWeekSource {
            val inner = UsageFixtures.FakeSource()
            override fun granted(): Boolean { gate.await(); return true }
            override fun samples(fromMs: Long, toMs: Long) = inner.samples(fromMs, toMs)
        })
        host(repo = slow, wait = "usage.loading")
        val loading = bounds("usage.block"); val rowLoading = bounds("timeline.row.0")
        assertTrue("the block is ${loading.bottom.value - loading.top.value} dp high while loading, not 1 dp", loading.bottom.value - loading.top.value >= 200f)
        assertEquals("quiet: no figures, no text of its own", 0, texts().size)
        assertEquals("a screen reader hears one short phrase", "Loading screen time", rule.onNodeWithTag("usage.loading").fetchSemanticsNode().config[SemanticsProperties.ContentDescription].joinToString())
        capture("loading-dark")
        gate.countDown()
        rule.waitUntil(10_000) { has("usage.stat") }
        val ready = bounds("usage.block"); val rowReady = bounds("timeline.row.0")
        assertEquals("same top", loading.top.value, ready.top.value, 0.5f)
        assertTrue("the list moves by ${rowReady.top.value - rowLoading.top.value} dp when the numbers arrive (at most 24 dp)", Math.abs(rowReady.top.value - rowLoading.top.value) <= 24f)
    }

    @Test fun USG_50_reopeningWithTheSameRepositoryShowsTheLastAnswerAtOnceInsteadOfAPlaceholder() {
        val repo = UsageFixtures.repo()
        var show by androidx.compose.runtime.mutableStateOf(true)
        rule.setContent { Themed(true) { Column(Modifier.fillMaxSize().background(LocalLp.current.bg)) { if (show) UsageTimelineBlock(repo, UsageFixtures.labelOf, now = { UsageFixtures.now }, zone = UsageFixtures.zone) } } }
        rule.waitUntil(10_000) { has("usage.stat") }
        val first = bounds("usage.block").let { it.bottom.value - it.top.value }
        show = false; rule.waitForIdle(); assertFalse(has("usage.block"))
        show = true; rule.waitForIdle()
        assertFalse("no placeholder frame the second time", has("usage.loading"))
        assertTrue("the block is back with its numbers at once", has("usage.stat"))
        assertEquals(first, bounds("usage.block").let { it.bottom.value - it.top.value }, 1f)
    }

    @Test fun USG_51_theEmptyStateIsOneQuietLine() {
        host(repo = UsageFixtures.repo(UsageFixtures.FakeSource(data = emptyMap())), wait = "usage.empty")
        assertEquals(0, count("usage.steps.col") + count("usage.row"))
        assertTrue(bounds("usage.block").let { it.bottom.value - it.top.value } < 80f)
        capture("empty-dark")
    }

    @Test fun USG_52_theTableViewStillReplacesTheWeekColumnsAndKeepsTheList() {
        host()
        rule.runOnUiThread { rule.onNodeWithTag("usage.chart").fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsActions.CustomActions].single().action() }
        rule.waitForIdle()
        assertTrue(has("usage.table")); assertEquals(0, count("usage.steps.col")); assertEquals(5, count("usage.row"))
        assertNoOverlap("table view")
    }

    // ---- screenshots ----

    @Test fun USG_53_screenshotDarkGranted() { host(dark = true); capture("granted-dark") }
    @Test fun USG_53_screenshotLightGranted() { host(dark = false); capture("granted-light") }
    @Test fun USG_53_screenshotDarkRowPicked() { host(dark = true); rule.onNodeWithTag("usage.row.1").performClick(); capture("picked-dark") }
    @Test fun USG_53_screenshotLightMissing() { host(dark = false, repo = UsageFixtures.repo(UsageFixtures.FakeSource(granted = false)), wait = "usage.noAccess"); capture("missing-light") }
    @Test fun USG_53_screenshotLightEmpty() { host(dark = false, repo = UsageFixtures.repo(UsageFixtures.FakeSource(data = emptyMap())), wait = "usage.empty"); capture("empty-light") }
    @Test fun USG_53_screenshotLightLoading() {
        val gate = CountDownLatch(1)
        host(dark = false, repo = UsageFixtures.repo(object : UsageWeekSource { override fun granted(): Boolean { gate.await(); return true }; override fun samples(fromMs: Long, toMs: Long) = emptyList<AppTime>() }), wait = "usage.loading")
        capture("loading-light"); gate.countDown()
    }
    @Test fun USG_53_screenshotDarkFont2Comms125() { host(dark = true, fontScale = 2f, percent = 125); capture("granted-font2-comms125-dark") }
}
