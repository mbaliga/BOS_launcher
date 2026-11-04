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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.bos.sphere.core.data.AppEntry
import com.bos.sphere.core.data.ComplicationKind
import com.bos.sphere.core.data.Corner
import com.bos.sphere.core.data.CornerActionId
import com.bos.sphere.core.data.CornerItem
import com.bos.sphere.core.data.CornerSlot
import com.bos.sphere.core.data.CornerStyle
import com.bos.sphere.core.data.DefaultCorners
import com.bos.sphere.core.data.HomeGesture
import com.bos.sphere.core.data.HomeLayoutMath
import com.bos.sphere.core.data.HomeLayout
import com.bos.sphere.core.data.HomePage
import com.bos.sphere.core.data.LauncherPrefs
import com.bos.sphere.core.data.NetworkKind
import com.bos.sphere.core.data.NetworkStatus
import com.bos.sphere.core.data.PageLayout
import com.bos.sphere.core.data.PageShape
import com.bos.sphere.core.data.WheelGrouping
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Renders the main surfaces on the JVM (Robolectric native graphics) and writes PNGs to
 * `feature-grid/build/screenshots/`. A smoke test: it fails only if a surface can't compose or
 * draw; the images are for eyeballing against the reference designs.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ScreenshotTest {

    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val outDir = File(System.getProperty("user.dir"), "build/screenshots").apply { mkdirs() }

    // Generic placeholder apps (coloured tiles with an initial), not real brand marks.
    private val palette = listOf(
        "Photos" to listOf(0xFFFFB000, 0xFFE1306C),
        "Spark" to listOf(0xFFFFFFFF, 0xFFE8ECFF),
        "Chat" to listOf(0xFFFFFFFF, 0xFFF2F2F2),
        "Clips" to listOf(0xFFFF4D4D, 0xFFFF9A3C),
        "Notes" to listOf(0xFFFFF35C, 0xFFFFE600),
        "Friends" to listOf(0xFF2F6BFF, 0xFF1758F2),
        "Paper" to listOf(0xFFFFFFFF, 0xFFF0F6FF),
        "Phone" to listOf(0xFF5BE37D, 0xFF25C04A),
        "Talk" to listOf(0xFF6BE58A, 0xFF34C759),
        "Maps" to listOf(0xFF7AD7FF, 0xFF3A9BFF),
        "Music" to listOf(0xFFFF5F8A, 0xFFB03CFF),
        "Mail" to listOf(0xFF8E7BFF, 0xFF5B48E0),
    )

    private val apps: List<AppEntry> = palette.mapIndexed { i, (name, colors) ->
        AppEntry(
            packageName = "demo.app$i",
            componentName = ComponentName("demo.app$i", "demo.app$i.Main"),
            label = name,
            user = Process.myUserHandle(),
            iconLoader = { AdaptiveIconDrawable(ColorDrawable(colors[1].toInt()), LetterDrawable(name.first(), colors)) },
        )
    }
    private val byKey = apps.associateBy { it.key }

    private fun pagesFor(prefs: LauncherPrefs, landscape: Boolean, layout: HomeLayout = HomeLayout()) =
        HomeLayoutMath.visualPages(layout, apps.take(9).map { it.key }) { it.capacity(landscape, prefs) }

    @Composable
    private fun Scene(
        prefs: LauncherPrefs,
        landscape: Boolean,
        pages: List<HomePage>,
        corners: Map<Corner, CornerSlot> = DefaultCorners,
        rotation: Int = if (landscape) 1 else 0,
        status: NetworkStatus = NetworkStatus(NetworkKind.Good, 0.9f),
    ) {
        val state = rememberGridHomeState()
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            NetworkDotGrid(status, modifier = Modifier.fillMaxSize())
            GridHome(
                pages = pages,
                appsByKey = byKey,
                prefs = prefs,
                isLandscape = landscape,
                state = state,
                suggestion = apps[9],
                gesturesBound = setOf(HomeGesture.PinchIn),
                capacityOf = { it.capacity(landscape, prefs) },
                onLaunch = {}, onAppLongPress = {}, onPagesChange = {}, onEmptyLongPress = {}, onGesture = {},
                onClockTap = {}, onTileLongPress = { _, _ -> },
            )
            CornerLayer(
                placements = cornerPlacements(corners, rotation, true),
                style = prefs.cornerStyle,
                appsByKey = byKey,
                onRun = { _, _ -> }, onEdit = {},
                complication = { _, slot, mod -> ComplicationView(slot.kind, 48.dp, onClick = {}, modifier = mod) },
                cluster = { p, slot, mod -> ComplicationCluster(slot.kinds, p.screen, 42.dp, 92.dp, {}, mod) },
                widget = { _, _ -> },
            )
        }
        LaunchedEffect(pages.size) { state.goToPage(0, animate = false) }
    }

    /**
     * Draws the activity's view tree straight into a bitmap. (captureToImage waits for an idle
     * redraw, which never comes with the clock paused and the dot grid breathing forever.)
     */
    private fun shoot(name: String, settleMs: Long = 1_500) {
        rule.mainClock.advanceTimeBy(settleMs)
        // The content view only (no Robolectric window title).
        val view = rule.activity.findViewById<android.view.View>(android.R.id.content)
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread { view.draw(Canvas(bmp)) }
        File(outDir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test @Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun gridPortrait() {
        rule.mainClock.autoAdvance = false
        val prefs = LauncherPrefs()
        rule.setContent { Scene(prefs, landscape = false, pages = pagesFor(prefs, false)) }
        shoot("01-grid-portrait")
    }

    @Test @Config(sdk = [34], qualifiers = "w891dp-h411dp-land-xhdpi")
    fun gridLandscape() {
        rule.mainClock.autoAdvance = false
        val prefs = LauncherPrefs()
        rule.setContent { Scene(prefs, landscape = true, pages = pagesFor(prefs, true)) }
        shoot("02-grid-landscape")
    }

    @Test @Config(sdk = [34], qualifiers = "w891dp-h411dp-land-xhdpi")
    fun coverflowLandscape() {
        rule.mainClock.autoAdvance = false
        val prefs = LauncherPrefs()
        val pages = listOf(HomePage(apps.take(9).map { it.key }, PageLayout(shape = PageShape.Coverflow)))
        rule.setContent { Scene(prefs, landscape = true, pages = pages) }
        shoot("03-coverflow-landscape")
    }

    @Test @Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun circleBubblesPortrait() {
        rule.mainClock.autoAdvance = false
        val prefs = LauncherPrefs(cornerStyle = CornerStyle.Bubble)
        val pages = listOf(HomePage(apps.take(12).map { it.key }, PageLayout(shape = PageShape.Circle, rings = 2)))
        val corners = DefaultCorners + mapOf(
            Corner.TopRight to CornerSlot.Palette("Media", listOf(CornerActionId.Music, CornerActionId.Camera, CornerActionId.Gallery, CornerActionId.Flashlight).map(CornerItem::Action)),
            Corner.BottomLeft to CornerSlot.ComplicationCluster(listOf(ComplicationKind.Battery, ComplicationKind.Date, ComplicationKind.Ringer)),
        )
        rule.setContent { Scene(prefs, landscape = false, pages = pages, corners = corners, status = NetworkStatus(NetworkKind.Poor, 0.15f)) }
        shoot("04-circle-bubbles-portrait")
    }

    @Test @Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun paletteOpen() {
        rule.mainClock.autoAdvance = false
        val prefs = LauncherPrefs()
        val items = listOf(
            CornerActionId.Music, CornerActionId.Camera, CornerActionId.Flashlight, CornerActionId.RingerCycle,
            CornerActionId.Maps, CornerActionId.Calendar, CornerActionId.Clock,
        ).map(CornerItem::Action) + CornerItem.App(apps[5].key)
        val corners = DefaultCorners + (Corner.TopRight to CornerSlot.Palette("Quick", items))
        rule.setContent { Scene(prefs, landscape = false, pages = pagesFor(prefs, false), corners = corners) }
        rule.mainClock.advanceTimeBy(300)
        rule.onNodeWithContentDescription("Quick palette").performTouchInput { down(center) }
        // Stay under the long-press timeout (holding still that long opens the corner editor).
        shoot("05-palette-open", settleMs = 350)
    }

    @Test @Config(sdk = [34], qualifiers = "w891dp-h411dp-land-xhdpi")
    fun wheelDrawer() {
        rule.mainClock.autoAdvance = false
        val many = (0 until 40).map { i ->
            val (name, colors) = palette[i % palette.size]
            AppEntry("demo.w$i", ComponentName("demo.w$i", "M"), "${('A' + (i % 20))}$name", Process.myUserHandle()) {
                AdaptiveIconDrawable(ColorDrawable(colors[1].toInt()), LetterDrawable(('A' + (i % 20)), colors))
            }
        }
        rule.setContent {
            Box(Modifier.fillMaxSize().background(Color.Black)) {
                NetworkDotGrid(NetworkStatus(NetworkKind.Good, 0.9f), modifier = Modifier.fillMaxSize())
                WheelDrawer(
                    visible = true, anchor = Corner.BottomRight, apps = many, grouping = WheelGrouping.Letter,
                    onGroupingChange = {}, recentKeys = emptyList(), onLaunch = {}, onLongPressApp = {}, onSearch = {}, onDismiss = {},
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        shoot("06-wheel-drawer")
    }

    @Test @Config(sdk = [34], qualifiers = "w891dp-h411dp-land-xhdpi")
    fun settingsSpace() {
        rule.mainClock.autoAdvance = false
        val cats = listOf(
            SettingCategory("Page 1", Glyphs.Widget, listOf(SettingItem.Action("Layout", Glyphs.Apps, "Grid") {})),
            SettingCategory(
                "Home", Glyphs.Apps,
                listOf(
                    SettingItem.Pane("Style", Glyphs.Apps, "Pages") { Text("…") },
                    SettingItem.Pane("Default density", Glyphs.Widget, "Portrait 3 × 6 · Landscape 5 × 2") { Text("…") },
                    SettingItem.Pane("Icon size", Glyphs.Apps, "82% of the cell") { Text("…") },
                    SettingItem.Toggle("App labels", Glyphs.Message, false) {},
                    SettingItem.Toggle("Suggested app", Glyphs.Bolt, true) {},
                    SettingItem.Pane("Page turning", Glyphs.Swipe, "Portrait Sideways · Landscape Up / down") { Text("…") },
                ),
            ),
            SettingCategory("Corners", Glyphs.Palette, emptyList()),
            SettingCategory("Gestures", Glyphs.Pinch, emptyList()),
            SettingCategory("Motion", Glyphs.Shake, emptyList()),
            SettingCategory("Look", Glyphs.Image, emptyList()),
            SettingCategory("Sound", Glyphs.Music, emptyList()),
            SettingCategory("System", Glyphs.Gear, emptyList()),
        )
        rule.setContent { SettingsSpace(visible = true, categories = cats, initialCategory = 1, sounds = null, onClose = {}, modifier = Modifier.fillMaxSize()) }
        shoot("07-settings-space")
    }

    /** A gradient tile with a bold initial — stands in for an adaptive icon foreground. */
    private class LetterDrawable(private val letter: Char, private val colors: List<Long>) : Drawable() {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        override fun draw(canvas: Canvas) {
            val b = bounds
            paint.shader = LinearGradient(0f, b.top.toFloat(), 0f, b.bottom.toFloat(), colors[0].toInt(), colors[1].toInt(), Shader.TileMode.CLAMP)
            canvas.drawRect(b, paint)
            paint.shader = null
            val light = (colors[0] and 0xFFFFFF) > 0xC0C0C0
            paint.color = if (light) 0xFF111111.toInt() else 0xFFFFFFFF.toInt()
            paint.textSize = b.height() * 0.3f
            paint.typeface = Typeface.DEFAULT_BOLD
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText(letter.toString(), b.exactCenterX(), b.exactCenterY() + paint.textSize * 0.35f, paint)
        }
        override fun setAlpha(alpha: Int) = Unit
        override fun setColorFilter(colorFilter: ColorFilter?) = Unit
        @Deprecated("Deprecated in Java")
        override fun getOpacity() = PixelFormat.OPAQUE
    }
}
