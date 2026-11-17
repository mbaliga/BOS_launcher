package com.bos.sphere.feature.grid

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** PAL-24, NFR-05: the per-frame work of the Radar background allocates nothing (all geometry is precomputed). */
class RadarFrameBudgetTest {
    // android.jar hides java.lang.management from the compiler; the JVM running the test has it.
    private val bean: Any = Class.forName("java.lang.management.ManagementFactory").getMethod("getThreadMXBean").invoke(null)!!
    private val allocMethod = Class.forName("com.sun.management.ThreadMXBean").getMethod("getThreadAllocatedBytes", java.lang.Long.TYPE)
    private fun allocatedBytes(): Long = allocMethod.invoke(bean, Thread.currentThread().id) as Long

    @Test fun PAL_24_perFrameMathAllocatesNothing() {
        val dots = DotGridMath.layout(411f, 891f, 2.6f)
        val buckets = Array(DotGridMath.BUCKETS) { FloatArray(dots.count * 2) }; val counts = IntArray(DotGridMath.BUCKETS)
        val comic = ComicLines()
        val line = GlowLinesMath.CENTRE_LINES.maxByOrNull { it.size }!!
        val buf = FloatArray(line.size * 4); val tmp = FloatArray(2)
        val fade = Fade(); val ease = LevelEase()
        // Warm up (class loading, JIT), then measure.
        fun frame(t: Long) {
            DotGridMath.fillBuckets(dots, t, false, buckets, counts)
            comic.fill(411f, 891f, t, false)
            for (rank in 0 until 8) for (k in 0 until 2) {
                GlowLinesMath.pulse(4, GlowLinesMath.PRIORITY[rank], k, t, false, tmp)
                GlowLinesMath.slice(line, tmp[0] - 0.1f, tmp[0] + 0.1f, 411f, 891f, true, buf)
                GlowLinesMath.calm(tmp[0], 0.4f)
            }
            fade.step(1f, t, 400, false)
            ScopeMath.sweepAngle(t); ScopeMath.pingProgress(t, 5000f)
            ease.step(if (t % 3 == 0L) 4 else 1, t * 16, false)
            BackgroundCurves.at(BackgroundCurves.BEAM_REACH, 2.5f)
        }
        for (t in 0L until 200L) frame(t * 16)
        val before = allocatedBytes()
        for (t in 0L until 2000L) frame(t * 16)
        val used = allocatedBytes() - before
        assertTrue("allocated $used bytes over 2000 frames", used < 64 * 1024)
    }

    /** The draw functions of the painters must not build Paths or collections: the source says so too. */
    @Test fun PAL_24_drawFunctionsDoNotConstructPathsOrCollections() {
        val dir = listOf("src/main/kotlin/com/bos/sphere/feature/grid", "feature-grid/src/main/kotlin/com/bos/sphere/feature/grid").map { File(it) }.first { it.exists() }
        val bodies = ArrayList<Pair<String, String>>()
        for (name in listOf("SignalViews.kt", "SignalBackground.kt", "LivingWallpapers.kt", "BackgroundCurves.kt", "ComicLines.kt", "RadarBackdropMath.kt", "GlowLinesMath.kt")) {
            val text = File(dir, name).readText()
            Regex("""(override )?fun (draw\w*|fill\w*|slice|pulse|calm|step)\(""").findAll(text).forEach { m ->
                var i = text.indexOf('{', m.range.last); var depth = 0; val start = i
                if (i < 0 || text.substring(m.range.last, i).let { seg -> listOf("fun ", "interface ", "object ", "class ").any { it in seg } }) return@forEach
                while (i < text.length) { if (text[i] == '{') depth++ else if (text[i] == '}') { depth--; if (depth == 0) break }; i++ }
                bodies += "$name:${m.groupValues[2]}" to text.substring(start, i + 1)
            }
        }
        assertTrue("found the draw functions", bodies.size >= 10)
        val banned = listOf("mutableListOf", "listOf(", "arrayOf(", "FloatArray(", "IntArray(", "ArrayList", ".map {", ".filter {", "Paint(", "Brush.", "Stroke(", "lazy")
        for ((where, body) in bodies) {
            for (b in banned) assertFalse("$where builds $b per frame", body.contains(b))
            assertFalse("$where builds a Path per frame", Regex("""(?<![A-Za-z])Path\(""").containsMatchIn(body))
        }
    }

    @Test fun PAL_24_infiniteMotionIsOneSharedClockThatReadsMotionPrefsAndFocus() {
        val dir = listOf("src/main/kotlin/com/bos/sphere/feature/grid", "feature-grid/src/main/kotlin/com/bos/sphere/feature/grid").map { File(it) }.first { it.exists() }
        val text = File(dir, "SignalBackground.kt").readText()
        assertTrue(text.contains("rememberMotionPrefs()"))
        assertTrue(text.contains("isWindowFocused"))
        assertTrue(text.contains("Lifecycle.State.RESUMED"))
        assertEquals("one shared clock", 1, Regex("rememberInfiniteTransition\\(").findAll(text).count())
    }
}
