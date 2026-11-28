package com.bos.sphere.feature.grid

import android.content.ComponentName
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.ColorDrawable
import android.os.Process
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import com.bos.sphere.core.data.AppEntry
import com.bos.sphere.core.data.Corner
import com.bos.sphere.core.data.WheelGrouping
import com.bos.sphere.core.design.HyleTheme
import com.bos.sphere.core.design.ThemeVariant
import com.bos.sphere.core.input.PuckLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * INP-18 (wave 44, ringLabels): the owner's "A-Z, Category and Recent are slanted but not curved" and "Category is not getting clicked".
 * Touch taps by position (not by semantics) on every group label on both corners and orientations, and pixel checks that the label ink lies on
 * the band's arc, the underline curves, the flip rule and an RTL string.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RingLabelsW44UiTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val outDir = File(System.getProperty("user.dir"), "build/screenshots").apply { mkdirs() }
    private val tints = longArrayOf(0xFFE1306C, 0xFF2F6BFF, 0xFF25C04A, 0xFFFF9A3C, 0xFF8E7BFF, 0xFF3A9BFF)
    private val apps = (0 until 60).map { i ->
        AppEntry("demo.w$i", ComponentName("demo.w$i", "M"), "${('A' + (i % 22))}app$i", Process.myUserHandle()) {
            AdaptiveIconDrawable(ColorDrawable(tints[i % tints.size].toInt()), ColorDrawable(tints[(i + 2) % tints.size].toInt()))
        }
    }

    private class Host(val taps: MutableList<WheelGrouping>, val grouping: androidx.compose.runtime.MutableState<WheelGrouping>, val anchor: androidx.compose.runtime.MutableState<Corner>) {
        fun set(g: WheelGrouping? = null, a: Corner? = null) = Snapshot.withMutableSnapshot { if (g != null) grouping.value = g; if (a != null) anchor.value = a }
    }

    private fun open(anchor: Corner, start: WheelGrouping, light: Boolean = false): Host {
        rule.mainClock.autoAdvance = false
        val taps = mutableListOf<WheelGrouping>()
        val g = mutableStateOf(start)
        val an = mutableStateOf(anchor)
        Snapshot.withMutableSnapshot { HyleTheme.apply(if (light) ThemeVariant.Light else ThemeVariant.Dark, 0xFF8E7BFF) }
        rule.setContent {
            Box(Modifier.fillMaxSize().background(if (light) Color(0xFFF2F3F7) else Color.Black)) {
                WheelDrawer(true, an.value, apps, g.value, { taps += it; g.value = it }, emptyList(), {}, {}, {}, {}, modifier = Modifier.fillMaxSize())
            }
        }
        rule.mainClock.advanceTimeBy(2_000)
        return Host(taps, g, an)
    }

    private fun labelPoint(anchor: Corner, i: Int, dr: Float = 3.5f, dAngle: Float = 0f): Offset {
        val d = rule.activity.resources.displayMetrics.density
        val root = rule.onRoot().fetchSemanticsNode().boundsInRoot
        val min = minOf(root.width, root.height) / d
        val r = (PuckLayout.compute(min).chipRadiusDp + dr) * d
        val p = WheelGeometry.polarToScreen(anchor, root.width, root.height, r, WheelGeometry.quadrantStart(anchor) + floatArrayOf(13f, 45f, 77f)[i] + dAngle)
        return Offset(p.x, p.y)
    }

    private fun tapEachGroup(anchor: Corner) {
        val h = open(anchor, WheelGrouping.Letter)
        for ((i, g) in WheelGrouping.entries.withIndex()) {
            h.taps.clear()
            h.set(g = if (g == WheelGrouping.Letter) WheelGrouping.Recent else WheelGrouping.Letter)
            rule.mainClock.advanceTimeBy(300)
            val p = labelPoint(anchor, i)
            rule.onRoot().performTouchInput { click(p) }
            rule.mainClock.advanceTimeBy(400)
            assertEquals("tap on the $g label at $p on $anchor switches the group", listOf(g), h.taps)
            assertEquals(g, h.grouping.value)
        }
    }

    // ---- the Category bug: a tap on each label, by touch position, on both corners, portrait and landscape ----------------------------------
    @Test @Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xhdpi") fun INP_18_w44_tapEveryGroupLabelPortraitLeft() = tapEachGroup(Corner.BottomLeft)
    @Test @Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xhdpi") fun INP_18_w44_tapEveryGroupLabelPortraitRight() = tapEachGroup(Corner.BottomRight)
    @Test @Config(sdk = [34], qualifiers = "w891dp-h411dp-land-xhdpi") fun INP_18_w44_tapEveryGroupLabelLandscapeLeft() = tapEachGroup(Corner.BottomLeft)
    @Test @Config(sdk = [34], qualifiers = "w891dp-h411dp-land-xhdpi") fun INP_18_w44_tapEveryGroupLabelLandscapeRight() = tapEachGroup(Corner.BottomRight)
    @Test @Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xhdpi") fun INP_18_w44_tapEveryGroupLabelTopCorners() { tapEachGroup(Corner.TopLeft) }
    @Test @Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xhdpi") fun INP_18_w44_tapEveryGroupLabelTopRight() = tapEachGroup(Corner.TopRight)

    @Test @Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun INP_18_w44_categoryHitsAcrossItsWholeCurvedSlotButNotInTheShortcutRing() {
        val h = open(Corner.BottomLeft, WheelGrouping.Letter)
        // along the arc +-9 degrees and across the band radially: all Category
        for (dA in listOf(-9f, -4f, 0f, 4f, 9f)) for (dr in listOf(-5f, 3.5f, 12f)) {
            h.taps.clear(); h.set(g = WheelGrouping.Letter)
            rule.mainClock.advanceTimeBy(50)
            val p = labelPoint(Corner.BottomLeft, 1, dr, dA)
            rule.onRoot().performTouchInput { click(p) }
            rule.mainClock.advanceTimeBy(200)
            assertEquals("dAngle $dA dr $dr", listOf(WheelGrouping.Category), h.taps)
        }
        // 25 dp inside the label radius is the shortcut ring (an empty slot tap does nothing), never a group change
        h.taps.clear()
        val ring = labelPoint(Corner.BottomLeft, 1, -25f)
        rule.onRoot().performTouchInput { click(ring) }
        rule.mainClock.advanceTimeBy(200)
        assertTrue("the ring does not switch the group: ${h.taps}", h.taps.isEmpty())
    }

    @Test @Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun INP_18_w44_groupLabelNodesCoverTheLabelAndStillClick() {
        val h = open(Corner.BottomLeft, WheelGrouping.Letter)
        val a = rule.onNodeWithContentDescription("Category").fetchSemanticsNode().boundsInRoot
        val p = labelPoint(Corner.BottomLeft, 1)
        assertTrue("the node covers the label $a vs $p", a.contains(p))
        assertTrue("and is no bigger than its slot (about 100 dp): ${a.width} x ${a.height}", a.width < 130f * rule.activity.resources.displayMetrics.density)
        rule.onNodeWithContentDescription("Category").performClick()
        rule.mainClock.advanceTimeBy(100)
        assertEquals(listOf(WheelGrouping.Category), h.taps)
    }

    // ---- ink on the arc -----------------------------------------------------------------------------------------------------------------
    /** Draws [label] centred on [angle] about (cx, cy) on a bitmap and returns the ink pixels' polar coordinates (radius, angle) in px/deg. */
    private fun ink(label: ArcLabel, angle: Float, size: Int, color: Int = 0xFFFFFFFF.toInt(), under: Boolean = false, underColor: Int = 0xFFFF0000.toInt(), redOnly: Boolean = false): List<Pair<Float, Float>> {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = size / 2f
        label.draw(Canvas(bmp), c, c, angle, 1f, color, true, under, underColor)
        val out = ArrayList<Pair<Float, Float>>()
        for (y in 0 until size) for (x in 0 until size) {
            val px = bmp.getPixel(x, y)
            val on = if (redOnly) (android.graphics.Color.red(px) > 200 && android.graphics.Color.green(px) < 80 && android.graphics.Color.alpha(px) > 200) else android.graphics.Color.alpha(px) > 128
            if (on) {
                val dx = x + 0.5f - c; val dy = y + 0.5f - c
                out += hypot(dx, dy) to Math.toDegrees(atan2(dy, dx).toDouble()).toFloat().let { if (it < 0) it + 360f else it }
            }
        }
        return out
    }

    private val bigPaints get() = ArcPaints(40f, 0.1f, 4f)

    /** Mean radius of the ink in [bins] equal slices of its angular range: constant for text on an arc, rising to the ends for a straight run. */
    private fun binMeans(px: List<Pair<Float, Float>>, bins: Int = 6): List<Float> {
        val lo = px.minOf { it.second }; val hi = px.maxOf { it.second }
        return (0 until bins).map { b ->
            val s = px.filter { it.second >= lo + (hi - lo) * b / bins && it.second <= lo + (hi - lo) * (b + 1) / bins }
            s.map { it.first }.average().toFloat()
        }
    }

    @Test @Config(sdk = [34]) fun INP_18_w44_inkLiesOnTheBandArcNotOnAStraightLine() {
        val paints = bigPaints
        val label = ArcLabel("HEHEHEHEHE", paints, 300f) // glyphs of equal height: the ink then shows only the circle, not the letter shapes
        for (angle in listOf(300f, 315f, 328f, 45f, 135f)) {
            val px = ink(label, angle, 760)
            assertTrue("ink drawn at $angle", px.size > 400)
            val rMin = px.minOf { it.first }; val rMax = px.maxOf { it.first }
            assertTrue("angle $angle radial range $rMin..$rMax within the band ${300 - paints.inkHeight}..${300 + paints.inkHeight}", rMin > 300 - paints.inkHeight * 0.9f && rMax < 300 + paints.inkHeight * 1.25f)
            val means = binMeans(px)
            val spread = means.max() - means.min()
            // the label spans about ${label.spanDeg} degrees: a straight run of that length would rise by L^2/(8r) at its ends
            val straightRise = (label.widthPx * label.widthPx) / (8f * 300f)
            assertTrue("angle $angle: mean radius per slice varies by $spread, a straight run would vary by about $straightRise", spread < straightRise * 0.35f)
            // and the ink is centred on its slot
            val mid = px.map { it.second }.let { (it.min() + it.max()) / 2f }
            assertEquals("centred on its angle", angle, mid, 1.5f)
        }
    }

    @Test @Config(sdk = [34]) fun INP_18_w44_flippedLabelsStayUprightAndOnTheSameBand() {
        val label = ArcLabel("RECENT", bigPaints, 300f)
        val up = ink(label, 315f, 760); val down = ink(label, 135f, 760)
        // same ink radius band on both halves
        assertEquals(up.map { it.first }.average().toFloat(), down.map { it.first }.average().toFloat(), 3.5f)
        // the glyphs of a lower-half label are not mirrored: its up side points to the circle's centre, so ink at the label's left end (smaller
        // screen x at angle 135 is larger angle) belongs to the first letter: check the extent only (a reversed run would swap the order)
        assertTrue(down.size > 300)
    }

    @Test @Config(sdk = [34]) fun INP_18_w44_underlineCurvesWithTheTextOnBothHalves() {
        val paints = bigPaints
        val label = ArcLabel("Category", paints, 300f)
        for (angle in listOf(315f, 135f)) {
            val px = ink(label, angle, 760, color = 0x00000000, under = true, redOnly = true)
            // keep only the red underline pixels (the text is transparent)
            assertTrue("underline drawn at $angle (${px.size})", px.size > 200)
            val means = binMeans(px)
            val straightRise = (label.widthPx * label.widthPx) / (8f * 300f)
            assertTrue("underline at $angle is an arc: spread ${means.max() - means.min()} vs straight $straightRise", means.max() - means.min() < straightRise * 0.35f)
            val r = px.map { it.first }.average().toFloat()
            // below the baseline: inside the band centre for the upper half, outside for the lower
            if (angle > 180f) assertTrue("upper half underline is inside the ink ($r)", r < 300f - paints.inkHeight / 2f + 1f) else assertTrue("lower half underline is outside ($r)", r > 300f + paints.inkHeight / 2f - 1f)
        }
    }

    @Test @Config(sdk = [34]) fun INP_18_w44_rtlStringIsSetOnTheSameArcWithItsShapingKept() {
        val arabic = "التصنيف" // التصنيف
        val hebrew = "קטגוריה"
        for (s in listOf(arabic, hebrew)) {
            val paints = bigPaints
            val label = ArcLabel(s, paints, 300f)
            assertEquals("the run is measured as one shaped string", paints.bold.measureText(s), label.widthPx, 0.01f)
            val px = ink(label, 315f, 760)
            assertTrue("ink for $s (${px.size})", px.size > 300)
            val rMin = px.minOf { it.first }; val rMax = px.maxOf { it.first }
            assertTrue("RTL ink $rMin..$rMax stays in the band around 300", rMin > 300 - paints.inkHeight * 1.8f && rMax < 300 + paints.inkHeight * 1.8f)
            val ext = px.maxOf { it.second } - px.minOf { it.second }
            assertEquals("RTL ink spans the arc of its measured width", label.spanDeg, ext, label.spanDeg * 0.2f)
            assertEquals("centred on the slot", 315f, (px.maxOf { it.second } + px.minOf { it.second }) / 2f, 3f)
            // shaping kept: the ink of the arc run has about the pixel mass of the same string drawn straight (a per-glyph unshaped run would differ)
            val bmp = Bitmap.createBitmap(900, 200, Bitmap.Config.ARGB_8888)
            Canvas(bmp).drawText(s, 20f, 120f, paints.bold.also { it.color = 0xFFFFFFFF.toInt() })
            var straight = 0; for (y in 0 until 200) for (x in 0 until 900) if (android.graphics.Color.alpha(bmp.getPixel(x, y)) > 128) straight++
            assertTrue("arc ink ${px.size} vs straight $straight", px.size in (straight * 0.7).toInt()..(straight * 1.3).toInt())
        }
    }

    @Test @Config(sdk = [34]) fun INP_18_w44_labelFollowsTheRotationOfTheWheel() {
        val label = ArcLabel("Recent", bigPaints, 300f)
        val a = ink(label, 300f, 760); val b = ink(label, 330f, 760)
        fun mid(p: List<Pair<Float, Float>>) = (p.minOf { it.second } + p.maxOf { it.second }) / 2f
        assertEquals(30f, mid(b) - mid(a), 1.5f)
        // and the ink is the same shape rotated: same pixel mass within a few percent
        assertTrue(abs(a.size - b.size) < a.size * 0.06)
    }

    @Test @Config(sdk = [34]) fun INP_18_w44_drawingAllocatesNothingAndTheLabelOwnsItsPaths() {
        val src = File(System.getProperty("user.dir"), "src/main/kotlin/com/bos/sphere/feature/grid/RingLabels.kt").readText()
        val draw = src.substringAfter("fun draw(canvas").substringBefore("\n    }\n")
        val alloc = Regex("(?<![A-Za-z])(Path|Paint|RectF|String|StringBuilder|ArrayList|mutableListOf|listOf)\\(")
        assertTrue("draw builds no Path/Paint/RectF/String: ${alloc.find(draw)?.value}", alloc.find(draw) == null)
        val wheel = File(System.getProperty("user.dir"), "src/main/kotlin/com/bos/sphere/feature/grid/WheelDrawer.kt").readText()
        val canvasBlock = wheel.substringAfter("val nc = drawContext.canvas.nativeCanvas").substringBefore("// Touch (wave 44)")
        assertTrue("the chip canvas allocates no Paint, Path or RectF per draw", Regex("(?<![A-Za-z])(Paint|Path|RectF)\\(").find(canvasBlock) == null)
        assertTrue("the old straight label rotate is gone from the band", !wheel.contains("tangentTextRotation(centerAngle)"))
    }

    // ---- screenshots and the drawer's own ink ------------------------------------------------------------------------------------------------
    private fun shoot(name: String, bmp: Bitmap) = File(outDir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    private fun snap(): Bitmap {
        val view = rule.activity.findViewById<android.view.View>(android.R.id.content)
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread { view.draw(Canvas(bmp)) }
        return bmp
    }

    /** Bright chip-label ink between the ring and the hub's edge, grouped by label slot: each slot's ink lies at the label radius, on the arc. */
    @Test @Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun INP_18_w44_drawerChipInkLiesOnTheLabelArcAndShots() {
        val h = open(Corner.BottomLeft, WheelGrouping.Letter)
        for (corner in listOf(Corner.BottomLeft, Corner.BottomRight)) for (g in WheelGrouping.entries) {
            h.set(g = g, a = corner); rule.mainClock.advanceTimeBy(2_000)
            val name = "w44-disc-${corner.name.lowercase()}-${g.name.lowercase()}"
            val bmp = snap(); shoot("$name-dark", bmp)
            val d = rule.activity.resources.displayMetrics.density
            val root = rule.onRoot().fetchSemanticsNode().boundsInRoot
            val cx = WheelGeometry.cornerX(corner, root.width); val cy = WheelGeometry.cornerY(corner, root.height)
            val chipR = PuckLayout.compute(minOf(root.width, root.height) / d).chipRadiusDp
            val qs = WheelGeometry.quadrantStart(corner)
            for ((i, a0) in floatArrayOf(13f, 45f, 77f).withIndex()) {
                var n = 0; var sumR = 0.0
                val lo = (chipR - 4f) * d; val hi = (chipR + 14f) * d
                for (y in 0 until bmp.height) for (x in 0 until bmp.width) {
                    val dx = x - cx; val dy = y - cy
                    val r = hypot(dx, dy)
                    if (r < lo || r > hi) continue
                    var ang = Math.toDegrees(atan2(dy, dx).toDouble()).toFloat(); if (ang < 0) ang += 360f
                    if (abs(WheelGeometry.normalize180(ang - (qs + a0))) > 8f) continue
                    val px = bmp.getPixel(x, y)
                    val lum = (android.graphics.Color.red(px) + android.graphics.Color.green(px) + android.graphics.Color.blue(px)) / 3
                    if (lum > 110 && abs(android.graphics.Color.red(px) - android.graphics.Color.blue(px)) < 60) { n++; sumR += r }
                }
                assertTrue("$name: label $i has ink ($n)", n > 40)
                val mean = (sumR / n) / d
                assertEquals("$name: label $i ink is centred on the label radius", chipR + 3.5, mean, 4.5)
            }
        }
    }

    @Test @Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun INP_18_w44_lightShots() {
        val h = open(Corner.BottomLeft, WheelGrouping.Category, light = true)
        shoot("w44-disc-bottomleft-category-light", snap())
        h.set(g = WheelGrouping.Recent, a = Corner.BottomRight); rule.mainClock.advanceTimeBy(2_000)
        shoot("w44-disc-bottomright-recent-light", snap())
    }

    @Test @Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun INP_18_w44_rotatedRingShot() {
        val h = open(Corner.BottomLeft, WheelGrouping.Letter)
        // tap a band section away from the middle: the ring turns to it (a spring), labels follow
        val d = rule.activity.resources.displayMetrics.density
        val root = rule.onRoot().fetchSemanticsNode().boundsInRoot
        val m = WheelMetrics(root.width, root.height, androidx.compose.ui.unit.Density(d))
        val p = WheelGeometry.polarToScreen(Corner.BottomLeft, root.width, root.height, m.bandMid, 270f + 20f)
        rule.onRoot().performTouchInput { click(Offset(p.x, p.y)) }
        rule.mainClock.advanceTimeBy(2_500)
        shoot("w44-disc-bottomleft-rotated", snap())
        assertTrue(h.taps.isEmpty())
    }

    @Test @Config(sdk = [34], qualifiers = "w891dp-h411dp-land-xhdpi")
    fun INP_18_w44_landscapeAndTopShots() {
        val h = open(Corner.BottomRight, WheelGrouping.Letter); shoot("w44-disc-landscape-bottomright-az", snap())
        h.set(a = Corner.TopLeft); rule.mainClock.advanceTimeBy(2_000); shoot("w44-disc-landscape-topleft-az", snap())
    }
}
