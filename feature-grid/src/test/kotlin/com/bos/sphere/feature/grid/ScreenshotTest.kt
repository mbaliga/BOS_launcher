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
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.bos.sphere.core.data.AppEntry
import com.bos.sphere.core.data.RailSide
import com.bos.sphere.core.data.DrawerSort
import com.bos.sphere.core.data.DrawerView
import com.bos.sphere.core.data.IndicatorColors
import com.bos.sphere.core.data.IndicatorEffect
import com.bos.sphere.core.data.ReadingKind
import com.bos.sphere.core.data.IndicatorReading
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
        HomeLayoutMath.visualPages(layout, apps.take(9).map { it.key }, capacityOf = { it.capacity(landscape, prefs) })

    @Composable
    private fun Scene(
        prefs: LauncherPrefs,
        landscape: Boolean,
        pages: List<HomePage>,
        corners: Map<Corner, CornerSlot> = DefaultCorners,
        rotation: Int = if (landscape) 1 else 0,
        status: IndicatorReading = IndicatorReading(ReadingKind.Live, 0.9f, ""),
        effects: Set<IndicatorEffect> = setOf(IndicatorEffect.DotsBreathe),
        prefs2: LauncherPrefs? = null,
    ) {
        val state = rememberGridHomeState()
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            IndicatorLayer(
                status, effects,
                IndicatorColors.argb(status, prefs.indicatorLow, prefs.indicatorMid, prefs.indicatorHigh, prefs.indicatorIdle, false),
                0.6f, 0.5f, Modifier.fillMaxSize(),
            )
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
                iconSize = prefs.cornerIconSize.dp,
                iconMode = prefs.cornerIconMode,
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
    fun clockBottomPortrait() {
        rule.mainClock.autoAdvance = false
        val prefs = LauncherPrefs(clockPosition = com.bos.sphere.core.data.ClockPosition.Bottom, clockShowWeather = true)
        rule.setContent { Scene(prefs, landscape = false, pages = pagesFor(prefs, false)) }
        shoot("01b-clock-bottom")
    }

    @Test @Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun coverflowPortrait() {
        rule.mainClock.autoAdvance = false
        val prefs = LauncherPrefs(showLabels = true)
        val pages = listOf(HomePage(apps.take(9).map { it.key }, PageLayout(shape = PageShape.Coverflow)))
        rule.setContent { Scene(prefs, landscape = false, pages = pages) }
        shoot("03b-coverflow-portrait")
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
        rule.setContent { Scene(prefs, landscape = false, pages = pages, corners = corners, status = IndicatorReading(ReadingKind.Live, 0.15f, ""), effects = setOf(IndicatorEffect.DotsBreathe, IndicatorEffect.Vignette)) }
        shoot("04-circle-bubbles-portrait")
    }

    @Test @Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun paletteOpen() {
        rule.mainClock.autoAdvance = false
        val prefs = LauncherPrefs()
        val items = CornerActionId.entries.filter { !it.internal }.map(CornerItem::Action) + apps.map { CornerItem.App(it.key) }
        val corners = DefaultCorners + (Corner.TopRight to CornerSlot.Palette("Quick", items))
        rule.setContent { Scene(prefs, landscape = false, pages = pagesFor(prefs, false), corners = corners) }
        rule.mainClock.advanceTimeBy(300)
        rule.onNodeWithContentDescription("Quick palette").performTouchInput { down(center) }
        // Stay under the long-press timeout (holding still that long opens the corner editor).
        shoot("05-palette-open", settleMs = 450)
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

    @Test @Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun clockBigAndEffects() {
        rule.mainClock.autoAdvance = false
        val prefs = LauncherPrefs(clockSize = 64f, clockShowDay = true, clockShowDate = true, cornerIconSize = 40f)
        rule.setContent {
            Scene(
                prefs, landscape = false, pages = pagesFor(prefs, false),
                effects = setOf(IndicatorEffect.DotsBloomCentre, IndicatorEffect.SnakeOutline, IndicatorEffect.EdgeMeter),
            )
        }
        shoot("08-big-clock-effects", settleMs = 2_000)
    }

    @Test @Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun drawerSpace() {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            AppDrawerSpace(
                visible = true, apps = apps, usage = emptyMap(),
                view = DrawerView.Details, sort = DrawerSort.Alphabetical, rail = RailSide.Right,
                onViewChange = {}, onSortChange = {}, onLaunch = {}, onLongPress = {}, onDismiss = {},
                modifier = Modifier.fillMaxSize(),
            )
        }
        shoot("09-drawer-details")
    }

    @Test @Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun drawerGrid() {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            AppDrawerSpace(
                visible = true, apps = apps, usage = emptyMap(),
                view = DrawerView.Grid, sort = DrawerSort.Alphabetical, rail = RailSide.Right,
                onViewChange = {}, onSortChange = {}, onLaunch = {}, onLongPress = {}, onDismiss = {},
                modifier = Modifier.fillMaxSize(),
            )
        }
        shoot("09b-drawer-grid")
    }

    private val fakeHub = object : com.bos.sphere.core.data.SearchHub {
        private val hits = mapOf(
            com.bos.sphere.core.data.SearchCategory.Files to listOf(
                com.bos.sphere.core.data.SearchHit("f1", com.bos.sphere.core.data.SearchCategory.Files, "Receipt March.pdf", "Documents · 212 KB", kind = com.bos.sphere.core.data.HitKind.Document, snippet = "…paid by card, total 42.50, merchant Maps Cafe…", sourcePackage = "com.example.fylz"),
                com.bos.sphere.core.data.SearchHit("f2", com.bos.sphere.core.data.SearchCategory.Files, "IMG_2041.jpg", "Camera · 3.1 MB", kind = com.bos.sphere.core.data.HitKind.Image, snippet = "Mail address on the parcel label", sourcePackage = "com.example.fotoz"),
                com.bos.sphere.core.data.SearchHit("f3", com.bos.sphere.core.data.SearchCategory.Files, "Music notes.txt", "Download · 4 KB", kind = com.bos.sphere.core.data.HitKind.Document),
                com.bos.sphere.core.data.SearchHit("f4", com.bos.sphere.core.data.SearchCategory.Files, "Trip", "Documents", kind = com.bos.sphere.core.data.HitKind.Folder),
                com.bos.sphere.core.data.SearchHit("f5", com.bos.sphere.core.data.SearchCategory.Files, "clip.mp4", "Movies · 88 MB", kind = com.bos.sphere.core.data.HitKind.Video),
            ),
            com.bos.sphere.core.data.SearchCategory.Actions to listOf(
                com.bos.sphere.core.data.SearchHit("a1", com.bos.sphere.core.data.SearchCategory.Actions, "Music", kind = com.bos.sphere.core.data.HitKind.Action, actionId = CornerActionId.Music),
                com.bos.sphere.core.data.SearchHit("a2", com.bos.sphere.core.data.SearchCategory.Actions, "Maps", kind = com.bos.sphere.core.data.HitKind.Action, actionId = CornerActionId.Maps),
                com.bos.sphere.core.data.SearchHit("a3", com.bos.sphere.core.data.SearchCategory.Actions, "Flashlight", kind = com.bos.sphere.core.data.HitKind.Action, actionId = CornerActionId.Flashlight),
            ),
        )
        override fun search(query: String, prefs: LauncherPrefs) = kotlinx.coroutines.flow.flowOf(hits)
        override fun hasFileAccess() = true
        override fun deepSources() = emptyList<String>()
    }

    private fun searchShot(name: String, layout: com.bos.sphere.core.data.SearchLayout, query: String, cat: com.bos.sphere.core.data.SearchCategory?) {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            SearchScreen(
                apps = apps, recent = apps.take(9), prefs = LauncherPrefs(searchLayout = layout, searchBackdrop = true, searchMotion = true), contacts = null,
                onLaunch = {}, onLongPressApp = {}, onOpenIntent = {}, onRequestContactsPermission = {}, onDismiss = {},
                hub = fakeHub, onOpenHit = null, onRunAction = {}, onRequestFilePermission = {}, sounds = null,
                initialQuery = query, initialCategory = cat,
            )
        }
        shoot(name, settleMs = 800)
    }

    @Test @Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun searchFilmstrip() = searchShot("10-search-filmstrip", com.bos.sphere.core.data.SearchLayout.Filmstrip, "", null)

    @Test @Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun searchFilmstripFiles() = searchShot("12b-search-typing", com.bos.sphere.core.data.SearchLayout.Filmstrip, "ma", null)

    @Test @Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun searchOrbit() = searchShot("11-search-orbit", com.bos.sphere.core.data.SearchLayout.Orbit, "", null)

    @Test @Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun searchFlat() = searchShot("12-search-flat", com.bos.sphere.core.data.SearchLayout.Flat, "ma", null)

    @Test @Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun detailVisualHomeStyle() {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            DetailScreen(listOf("Settings", "Home", "Style"), {}) {
                VisualChoice(com.bos.sphere.core.data.HomeStyle.entries, com.bos.sphere.core.data.HomeStyle.Grid, {}, { it.name }, previewHeight = 140.dp) { HomeStylePreview(it) }
                VisualChoice(com.bos.sphere.core.data.ClockPosition.entries, com.bos.sphere.core.data.ClockPosition.entries.first(), {}, { it.label }, previewHeight = 96.dp) { StripPositionPreview(it) }
            }
        }
        shoot("20-detail-visual")
    }

    @Test @Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun backgroundsShot() {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            androidx.compose.foundation.layout.Column {
                for (k in listOf(com.bos.sphere.core.data.BackgroundKind.Gradient, com.bos.sphere.core.data.BackgroundKind.Texture, com.bos.sphere.core.data.BackgroundKind.Live)) {
                    androidx.compose.foundation.layout.Row(Modifier.weight(1f)) {
                        BackgroundPresets.of(k).take(4).forEach { BackgroundLayer(k, it.id, 0xFF0A0B10, 0xFF2A1B5E, Modifier.weight(1f).fillMaxSize(), animate = false) }
                    }
                }
            }
        }
        shoot("22-backgrounds")
    }

    @Test @Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xhdpi")
    fun settingsPortraitPane() {
        rule.mainClock.autoAdvance = false
        val zones = listOf<String?>(null, "Left", "Centre", "Right")
        val cats = listOf(
            SettingCategory("Home", Glyphs.Apps, listOf(SettingItem.Action("Style", Glyphs.Apps, "Pages") {})),
            SettingCategory(
                "Clock", Glyphs.Clock,
                listOf(
                    SettingItem.Pane("Status strip", Glyphs.Dial, "1") {
                        for (name in listOf("Time", "Battery", "Signal", "Steps", "Next alarm", "Ring mode")) {
                            SectionHeader(name)
                            ChipRow(zones, if (name == "Time") "Centre" else null, { it ?: "Off" }) {}
                        }
                    },
                    SettingItem.Pane("Size", Glyphs.Dial, "34") { Text("slider") },
                    SettingItem.Pane("Format", Glyphs.Clock, "System") { ChipRow(listOf("System", "12-hour", "24-hour"), "System", { it }) {} },
                    SettingItem.Toggle("Day", Glyphs.Calendar, false) {},
                    SettingItem.Toggle("Date", Glyphs.Calendar, false) {},
                ),
            ),
            SettingCategory("Theme", Glyphs.Palette, emptyList()),
        )
        rule.setContent { SettingsSpace(visible = true, categories = cats, initialCategory = 1, sounds = null, onClose = {}, modifier = Modifier.fillMaxSize()) }
        rule.mainClock.advanceTimeBy(400)
        rule.onNodeWithContentDescription("Status strip, 1").performClick()
        shoot("11-settings-portrait-pane", settleMs = 700)
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
