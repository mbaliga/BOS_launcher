package com.bos.sphere.feature.grid

import androidx.compose.ui.unit.Density
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** Wave 33 (drawerDebt): spoke spacing at the diagonal (HOM-01) and non-overlapping TalkBack section nodes (NFR-13, INP-18). */
class WheelDebtW33Test {
    private data class Screen(val name: String, val wDp: Float, val hDp: Float, val density: Float)
    private val screens = listOf(
        Screen("portrait 411x891", 411f, 891f, 2.625f), Screen("landscape 891x411", 891f, 411f, 2.625f),
        Screen("small portrait 320x568", 320f, 568f, 2f), Screen("small landscape 568x320", 568f, 320f, 2f),
        Screen("tablet 800x1280", 800f, 1280f, 2f), Screen("narrow 240x400", 240f, 400f, 1.5f),
    )
    private fun metrics(s: Screen) = WheelMetrics(s.wDp * s.density, s.hDp * s.density, Density(s.density))
    private val groups = listOf(1, 2, 5, 9, 3, 30, 1, 7, 4, 1, 1, 12, 2, 6, 1)

    private fun layout(m: WheelMetrics) = WheelGeometry.layout(groups, m.perSpoke, m.spokeStepDeg, m.gapDeg)

    private class Icon(val x: Float, val y: Float)

    @Test fun HOM_01_w33_no_two_icons_overlap_at_any_rotation_in_portrait_and_landscape() {
        for (s in screens) {
            val m = metrics(s)
            val l = layout(m)
            val half = m.iconPx / 2f
            var rot = -l.span
            var worst = Float.MAX_VALUE
            while (rot <= 0f) {
                val icons = ArrayList<Icon>()
                for (seg in l.segments) for (a in seg.spokeAngles) {
                    val ang = a + rot
                    if (ang < -WheelGeometry.LOOP_MARGIN_DEG || ang > WheelGeometry.QUADRANT + WheelGeometry.LOOP_MARGIN_DEG) continue
                    val rad = Math.toRadians(ang.toDouble())
                    for (k in 0 until m.perSpoke) {
                        val r = m.firstRadius + k * m.radialStep
                        icons += Icon((r * cos(rad)).toFloat(), (r * sin(rad)).toFloat())
                    }
                }
                for (i in icons.indices) for (j in i + 1 until icons.size) {
                    val gap = maxOf(abs(icons[i].x - icons[j].x), abs(icons[i].y - icons[j].y)) - 2 * half
                    worst = minOf(worst, gap)
                    assertTrue("${s.name} rotation $rot: icons overlap by ${-gap} px", gap >= -0.01f)
                }
                rot += 0.5f
            }
        }
    }

    @Test fun HOM_01_w33_the_diagonal_spacing_is_the_least_that_clears_and_keeps_icon_plus_10dp_for_small_icons() {
        // 45 degrees: two upright squares need icon x sqrt(2).
        assertTrue(WheelGeometry.spokeArc(100f, 25f, 2.6f) >= 100f * 1.4143f - 0.5f)
        assertEquals(125f, WheelGeometry.spokeArc(50f, 75f, 2.6f), 0.001f)   // tiny icons: icon + 10 dp still wins
        // never more than the diagonal needs, plus a dp: the group count that fits changes only by the extra ~0.42 of an icon per spoke.
        assertEquals(1.42f * 100f + 2.6f, WheelGeometry.spokeArc(100f, 25f, 2.6f), 0.001f)
        // The angle is the chord's: neighbours at that angle are exactly [arc] apart.
        val a = WheelGeometry.spokeStepDegrees(100f, 300f)
        assertEquals(100f, 2f * 300f * sin(Math.toRadians(a / 2.0)).toFloat(), 0.01f)
    }

    // ---- TalkBack nodes

    /** Rectangle of a node: centre, angle of its radial axis, radial depth, arc width. */
    private class Rect(val cx: Float, val cy: Float, val angRad: Double, val radial: Float, val arc: Float) {
        fun corners(): List<Pair<Double, Double>> {
            val ux = cos(angRad); val uy = sin(angRad)         // radial axis
            val vx = -uy; val vy = ux                          // tangent axis
            return listOf(-1 to -1, -1 to 1, 1 to 1, 1 to -1).map { (a, b) ->
                (cx + ux * a * radial / 2 + vx * b * arc / 2) to (cy + uy * a * radial / 2 + vy * b * arc / 2)
            }
        }
    }

    private fun overlap(a: Rect, b: Rect): Boolean {
        // Separating axis theorem over the four edge normals.
        for (r in listOf(a, b)) for (ang in listOf(r.angRad, r.angRad + Math.PI / 2)) {
            val ax = cos(ang); val ay = sin(ang)
            val pa = a.corners().map { it.first * ax + it.second * ay }; val pb = b.corners().map { it.first * ax + it.second * ay }
            if (pa.max() <= pb.min() + 1e-6 || pb.max() <= pa.min() + 1e-6) return false
        }
        return true
    }

    @Test fun NFR_13_w33_section_nodes_do_not_overlap_and_keep_48dp_depth() {
        for (s in screens) {
            val m = metrics(s)
            val l = layout(m)
            val minNode = 48f * s.density
            var rot = -l.span
            while (rot <= 0f) {
                val rects = WheelGeometry.visibleSegments(l, rot, 0f).map { slot ->
                    val seg = l.segments[slot.index]
                    val node = WheelGeometry.bandNode(seg.width, m.bandMid, minNode)
                    val ang = Math.toRadians((seg.center + slot.copy * l.span + rot).toDouble())
                    Rect((m.bandMid * cos(ang)).toFloat(), (m.bandMid * sin(ang)).toFloat(), ang, node.radialPx, node.arcPx)
                }
                for (i in rects.indices) for (j in i + 1 until rects.size) assertFalse("${s.name} rotation $rot: nodes $i and $j overlap", overlap(rects[i], rects[j]))
                rot += 1f
            }
        }
    }

    @Test fun NFR_13_w33_a_node_is_its_own_slice_and_reaches_48dp_where_the_slice_allows() {
        for (s in screens) {
            val m = metrics(s)
            val minNode = 48f * s.density
            val single = WheelGeometry.bandNode(m.gapDeg + m.spokeStepDeg, m.bandMid, minNode)   // the narrowest slice: a group of one spoke
            assertEquals(minNode, single.radialPx, 0.001f)
            if (minOf(s.wDp, s.hDp) >= 360f) assertTrue("${s.name}: a one-spoke slice is ${single.arcPx / s.density} dp wide", single.arcPx >= minNode - 0.5f)
            // a wider slice gets a wider node, a narrower one never exceeds its own slice
            val wide = WheelGeometry.bandNode(3 * (m.gapDeg + m.spokeStepDeg), m.bandMid, minNode)
            assertTrue(wide.arcPx > single.arcPx)
            val tiny = WheelGeometry.bandNode(1f, m.bandMid, minNode)
            assertTrue(tiny.arcPx <= Math.toRadians(1.0).toFloat() * m.bandMid + 0.01f)
        }
    }
}
