package com.bos.sphere.feature.grid

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * NFR-05 / wave 22: the glass costs next to nothing per frame. The draw path of every glass piece (field, Close, mic, pressed, shaded,
 * light) allocates nothing while the background animates behind it, and no RenderEffect, blur or blurred copy is used anywhere in the glass or the Radar screen.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class RadarGlassFrameCostW22Test {
    private val bean: Any = Class.forName("java.lang.management.ManagementFactory").getMethod("getThreadMXBean").invoke(null)!!
    private val allocMethod = Class.forName("com.sun.management.ThreadMXBean").getMethod("getThreadAllocatedBytes", java.lang.Long.TYPE)
    private fun allocatedBytes(): Long = allocMethod.invoke(bean, Thread.currentThread().id) as Long

    private fun sourceDir() = listOf("src/main/kotlin/com/bos/sphere/feature/grid", "feature-grid/src/main/kotlin/com/bos/sphere/feature/grid").map { File(it) }.first { it.exists() }

    @Test
    fun NFR_05_theGlassDrawPathAllocatesNothingPerFrame() {
        val density = Density(2f)
        val pieces = arrayOf(
            GlassLayers(density, LayoutDirection.Ltr, Size(700f, 112f), RoundedCornerShape(50), Color.White, false, true, 1f, 0f),     // the field
            GlassLayers(density, LayoutDirection.Ltr, Size(96f, 96f), CircleShape, Color.White, false, true, 1f, 0.2f),                  // Close, shaded
            GlassLayers(density, LayoutDirection.Rtl, Size(112f, 112f), CircleShape, Color(0xFFB388FF), true, true, 1f, 0.3f),           // the mic, listening, pressed
            GlassLayers(density, LayoutDirection.Ltr, Size(200f, 96f), RoundedCornerShape(50), Color.White, false, true, 0.45f, 0f),    // the scope pill, nested
            GlassLayers(density, LayoutDirection.Ltr, Size(200f, 96f), RoundedCornerShape(8.dp), Color.White, false, false, 1f, 0f), // light glass, rounded rectangle
        )
        val image = ImageBitmap(720, 200)
        val canvas = Canvas(image)
        val scope = CanvasDrawScope()
        fun frame() {
            scope.draw(density, LayoutDirection.Ltr, canvas, Size(720f, 200f)) { for (i in pieces.indices) pieces[i].draw(this) } // indexed: a for-in over a List would allocate its iterator
        }
        fun emptyFrame() { scope.draw(density, LayoutDirection.Ltr, canvas, Size(720f, 200f)) { } }
        repeat(300) { frame(); emptyFrame() } // class loading, JIT, the shaders of each brush
        val b0 = allocatedBytes()
        repeat(2000) { emptyFrame() }
        val baseline = allocatedBytes() - b0 // what the test harness itself (CanvasDrawScope.draw) costs
        val before = allocatedBytes()
        repeat(2000) { frame() }
        val used = allocatedBytes() - before
        println("RADAR-GLASS frame cost: $used bytes over 2000 frames of ${pieces.size} glass pieces (harness alone: $baseline)")
        assertTrue("the glass draw path allocated ${used - baseline} bytes over 2000 frames beyond the harness", used - baseline < 4 * 1024)
        assertTrue("and under the repo's 64 KB budget in total ($used)", used < 64 * 1024)
    }

    @Test
    fun NFR_05_theDrawLambdaOnlyDrawsAndTheBrushesAreBuiltOncePerSize() {
        val text = File(sourceDir(), "Glass.kt").readText()
        val start = text.indexOf("    fun draw(scope: DrawScope) {")
        assertTrue("found GlassLayers.draw", start > 0)
        var i = text.indexOf('{', start); var depth = 0; val from = i
        while (i < text.length) { if (text[i] == '{') depth++ else if (text[i] == '}') { depth--; if (depth == 0) break }; i++ }
        val body = text.substring(from, i + 1)
        for (banned in listOf("Brush.", "listOf(", "arrayOf(", "mutableListOf", "Stroke(", "Paint(", "Color(", ".copy(", "SolidColor(", "createOutline", "lazy", ".map {")) assertFalse("GlassLayers.draw builds $banned per frame", body.contains(banned))
        assertFalse("no Path built per frame", Regex("""(?<![A-Za-z])Path\(""").containsMatchIn(body))
        // The modifier wraps the layers in drawWithCache, so they are rebuilt only when the size or the look changes.
        assertTrue(text.contains("this.drawWithCache {") && text.contains("onDrawBehind { layers.draw(this) }"))
    }

    @Test
    fun NFR_05_noRenderEffectNoBlurNoBlurredCopyInTheGlassOrTheRadarScreen() {
        val bannedWords = listOf("RenderEffect", "BlurEffect", "Modifier.blur", ".blur(", "setRenderEffect", "RenderScript", "BlurMaskFilter", "createBlurEffect", "setBackdrop")
        for (name in listOf("Glass.kt", "SearchOverlay.kt", "SearchChrome.kt", "SearchLayouts.kt", "SignalBackground.kt", "LivingWallpapers.kt")) {
            // Comments may say "no blur"; only code counts.
            val code = File(sourceDir(), name).readLines().filterNot { it.trimStart().startsWith("//") || it.trimStart().startsWith("*") || it.trimStart().startsWith("/*") }.joinToString("\n")
            for (b in bannedWords) assertFalse("$name uses $b", code.contains(b))
        }
    }
}
