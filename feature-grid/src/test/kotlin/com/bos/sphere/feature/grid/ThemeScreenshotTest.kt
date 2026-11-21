package com.bos.sphere.feature.grid

import android.content.ComponentName
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.os.Process
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.bos.sphere.core.data.AppEntry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Renders the XMB and continuous homes plus their previews to `build/screenshots/` for eyeballing. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ThemeScreenshotTest {

    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val outDir = File(System.getProperty("user.dir"), "build/screenshots").apply { mkdirs() }

    private val names = listOf(
        "Photos", "Camera", "Gallery", "Music", "Podcasts", "Chat", "Mail", "Maps", "Browser", "Notes",
        "Clock", "Calendar", "Files", "Settings", "Calculator", "Weather", "Phone", "Contacts", "Video", "Radio",
    )
    private val categories = mapOf(
        "Photos" to "Photo", "Camera" to "Photo", "Gallery" to "Photo", "Video" to "Photo",
        "Music" to "Music", "Podcasts" to "Music", "Radio" to "Music",
        "Chat" to "Social", "Mail" to "Social", "Phone" to "Social", "Contacts" to "Social",
        "Maps" to "Tools", "Browser" to "Tools", "Settings" to "Tools", "Files" to "Tools",
        "Notes" to "Productivity", "Clock" to "Productivity", "Calendar" to "Productivity", "Calculator" to "Productivity",
        "Weather" to "Weather",
    )
    private val tints = longArrayOf(0xFF6D5BFF, 0xFFFF6B6B, 0xFF2EC4B6, 0xFFFFB703, 0xFF3A86FF, 0xFFE76F51, 0xFF8338EC, 0xFF06D6A0, 0xFFEF476F, 0xFF118AB2)

    private val apps: List<AppEntry> = names.mapIndexed { i, name ->
        val c = tints[i % tints.size]
        AppEntry("demo.$i", ComponentName("demo.$i", "demo.$i.Main"), name, Process.myUserHandle()) {
            AdaptiveIconDrawable(ColorDrawable(c.toInt()), LetterDrawable(name.first()))
        }
    }

    private fun shoot(name: String, settleMs: Long = 1_500) {
        rule.mainClock.advanceTimeBy(settleMs)
        val view = rule.activity.findViewById<android.view.View>(android.R.id.content)
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread { view.draw(Canvas(bmp)) }
        // The content is drawn on a transparent host (the real host paints the wallpaper), so lay it over
        // a wallpaper here; this also keeps the edge-fade DstIn from punching holes in the PNG.
        val out = Bitmap.createBitmap(bmp.width, bmp.height, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        val paint = Paint().apply { shader = LinearGradient(0f, 0f, 0f, out.height.toFloat(), intArrayOf(0xFF1B2A55.toInt(), 0xFF3B1E54.toInt(), 0xFF0E1226.toInt()), null, Shader.TileMode.CLAMP) }
        c.drawRect(0f, 0f, out.width.toFloat(), out.height.toFloat(), paint)
        c.drawBitmap(bmp, 0f, 0f, null)
        File(outDir, "$name.png").outputStream().use { out.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test @Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun xmbPortrait() {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            Box(Modifier.fillMaxSize()) {
                XmbHome(
                    apps = apps, recent = apps.take(5), categoryOf = { categories[it.label] ?: "Other" },
                    onLaunch = {}, onLongPress = {}, contentPadding = PaddingValues(top = 36.dp, bottom = 24.dp),
                )
            }
        }
        shoot("20-xmb-portrait")
    }

    @Test @Config(sdk = [34], qualifiers = "w891dp-h411dp-land-xhdpi")
    fun xmbLandscape() {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            Box(Modifier.fillMaxSize()) {
                XmbHome(apps = apps, recent = emptyList(), categoryOf = { categories[it.label] ?: "Other" }, onLaunch = {}, onLongPress = {})
            }
        }
        shoot("21-xmb-landscape")
    }

    @Test @Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun dsiPortrait() {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            Box(Modifier.fillMaxSize()) {
                ContinuousHome(
                    apps = apps, crossCount = 3, landscape = false, style = ContinuousStyle.Dsi,
                    onLaunch = {}, onLongPress = {}, contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 48.dp, bottom = 32.dp),
                )
            }
        }
        shoot("22-dsi-portrait")
    }

    @Test @Config(sdk = [34], qualifiers = "w891dp-h411dp-land-xhdpi")
    fun plainLandscape() {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            Box(Modifier.fillMaxSize()) {
                ContinuousHome(
                    apps = apps, crossCount = 4, landscape = true, style = ContinuousStyle.Plain,
                    onLaunch = {}, onLongPress = {}, contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp),
                )
            }
        }
        shoot("23-plain-landscape")
    }

    @Test @Config(sdk = [34], qualifiers = "w891dp-h411dp-land-xhdpi")
    fun dsiLandscape() {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            Box(Modifier.fillMaxSize()) {
                ContinuousHome(
                    apps = apps, crossCount = 3, landscape = true, style = ContinuousStyle.Dsi,
                    onLaunch = {}, onLongPress = {}, contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp),
                )
            }
        }
        shoot("24-dsi-landscape")
    }

    @Test @Config(sdk = [34], qualifiers = "w411dp-h400dp-port-xhdpi")
    fun previews() {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            Row(Modifier.fillMaxSize().background(Color(0xFF14161F)).padding(8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.width(110.dp).aspectRatio(9f / 17f)) { XmbPreview() }
                Box(Modifier.width(110.dp).aspectRatio(9f / 17f)) { DsiPreview() }
                Box(Modifier.width(110.dp).aspectRatio(9f / 17f)) { ContinuousPreview() }
            }
        }
        shoot("25-theme-previews", 200)
    }

    private class LetterDrawable(private val letter: Char) : Drawable() {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        override fun draw(canvas: Canvas) {
            val b = bounds
            paint.shader = LinearGradient(0f, b.top.toFloat(), 0f, b.bottom.toFloat(), 0x33FFFFFF, 0x00000000, Shader.TileMode.CLAMP)
            canvas.drawRect(b, paint)
            paint.shader = null
            paint.color = 0xFFFFFFFF.toInt()
            paint.textSize = b.height() * 0.3f
            paint.typeface = Typeface.DEFAULT_BOLD
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText(letter.toString(), b.exactCenterX(), b.exactCenterY() + paint.textSize * 0.35f, paint)
        }
        override fun setAlpha(alpha: Int) = Unit
        override fun setColorFilter(colorFilter: ColorFilter?) = Unit
        @Deprecated("Deprecated in Java") override fun getOpacity() = PixelFormat.TRANSLUCENT
    }
}
