package com.bos.sphere.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.bos.sphere.core.data.CommsScaleSettings
import com.bos.sphere.core.data.GroupMode
import com.bos.sphere.core.data.HubNotification
import com.bos.sphere.core.data.LaunchpadPrefs
import com.bos.sphere.core.data.NotificationHistoryStore
import org.junit.After
import org.junit.Before
import java.io.File
import java.util.concurrent.Executor

/**
 * Wave 28 (commsLayout): the shared stage of the geometry tests: the real Comms screen (and menu) of the owner's mock-ups, with the mock-up's four rows,
 * rendered at 411 dp x 909 dp and 352 dpi so one image pixel is one device pixel of the 905 px mock-ups (the stage is [LaunchpadContent] with a hub, the
 * header, the tab row and the home card's sliver, as in the app).
 */
@OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
abstract class CommsLayoutCases(protected val orientation: String) : LaunchpadShots() {
    private val mock = listOf(
        Triple("demo.music", "Lemon", "N.E.R.D & Rihanna" to false), Triple("com.whatsapp", "Tulika Pandey", "Can we meet for Lunch?" to true),
        Triple("com.snapchat.android", "Cutegirlxx", "Open app to view message" to true), Triple("com.openai.chatgpt", "ChatGPT", "ChatGPT needs your permission to read your messages" to false),
    )
    private val colours = mapOf("demo.music" to 0xFF2A2A2A, "com.whatsapp" to 0xFF25D366, "com.snapchat.android" to 0xFFFFFC00, "com.openai.chatgpt" to 0xFFFFFFFF, "com.google.android.gm" to 0xFFD93025, "demo.cal" to 0xFF1A73E8)
    private val labels = mapOf("demo.music" to "Music", "com.whatsapp" to "WhatsApp", "com.snapchat.android" to "Snapchat", "com.openai.chatgpt" to "ChatGPT", "com.google.android.gm" to "Gmail", "demo.cal" to "Calendar")

    /** The mock-up's feed: four rows at 17:34, 17:32, 17:30 and 17:28. */
    protected fun feed(count: Int): List<HubNotification> = (0 until count).map { i ->
        if (i < mock.size) {
            val (pkg, who, msg) = mock[i]
            val (text, messaging) = msg
            if (pkg == "demo.music") HubNotification(id = "k$i", key = "k$i", source = "x", title = "Lemon", summary = text, timestamp = at(17, 34) - i * 120_000L, packageName = pkg, appLabel = labels[pkg]!!)
            else if (messaging) HubNotification(id = "k$i", key = "k$i", source = "x", title = who, summary = text, timestamp = at(17, 34) - i * 120_000L, packageName = pkg, appLabel = labels[pkg]!!, sender = who, conversation = who, isMessaging = true)
            else HubNotification(id = "k$i", key = "k$i", source = "x", title = text, summary = who, timestamp = at(17, 34) - i * 120_000L, packageName = pkg, appLabel = labels[pkg]!!)
        } else {
            val pkg = listOf("com.google.android.gm", "demo.cal", "com.whatsapp")[i % 3]
            HubNotification(id = "k$i", key = "k$i", source = "x", title = "Person $i", summary = "Message number $i", timestamp = at(11, 50) - i * 47L * 60_000L, packageName = pkg, appLabel = labels[pkg] ?: pkg, sender = "Person $i", conversation = "Person $i", isMessaging = true)
        }
    }

    protected lateinit var hub: HubState
    protected var menuRef: LaunchpadMenuState? = null
    protected val opened = mutableListOf<com.bos.sphere.core.data.SystemTileKind>()

    @Before fun seedIcons() {
        CommsScaleSettings.resetForTest()
        android.provider.Settings.System.putString(androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>().contentResolver, android.provider.Settings.System.TIME_12_24, "24")
        colours.forEach { (pkg, c) -> iconCache[pkg] = solid(c.toInt()) }
        val store = NotificationHistoryStore(File(java.nio.file.Files.createTempDirectory("commsL").toFile(), "usage/h.log"), Executor { it.run() })
        store.observe(feed(4))
        hub = HubState(HubPrefs(null), store, true, liveAll = true)
    }
    @After fun clearIcons() { colours.keys.forEach { iconCache.remove(it) }; CommsScaleSettings.resetForTest() }

    private fun solid(argb: Int): ImageBitmap {
        val b = android.graphics.Bitmap.createBitmap(48, 48, android.graphics.Bitmap.Config.ARGB_8888); b.eraseColor(argb); return b.asImageBitmap()
    }

    @Composable
    private fun Stage(items: List<HubNotification>, dark: Boolean, rtl: Boolean, home: Boolean, scale: Int?) {
        CompositionLocalProvider(LocalLp provides if (dark) LpDark else LpLight, LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val menu = rememberLaunchpadMenu().also { menuRef = it }
                val density = androidx.compose.ui.platform.LocalDensity.current.density
                val geo = LaunchpadCardGeometry(maxWidth.value * density, maxHeight.value * density, CardEdge.forLaunchpad(rtl), density)
                Box(Modifier.fillMaxSize().background(LocalLp.current.bg)) {
                    LaunchpadContent(
                        items = items, rules = emptyList(), prefs = LaunchpadPrefs(GroupMode.Flat), readKeys = emptySet(), hasAccess = true, now = at(23, 2),
                        onClose = {}, onOpenItem = {}, onOpenApp = {}, onDismissItem = {}, onClearAll = {}, onAddRule = {},
                        onSetDefaultMode = {}, onRequestAccess = {}, zone = zone, menu = menu, systemState = system.copy(now = at(23, 2), batteryPercent = 60),
                        onOpenSystemPanel = { opened += it }, onOpenSound = {}, hub = hub, viewSwitcher = { HubSwitcher(hub) },
                        endInset = if (home) (geo.sliverPx / density).dp else 0.dp,
                        commsScalePercent = scale ?: rememberCommsScalePercent(),
                    )
                }
                if (home) Box(Modifier.fillMaxSize().launchpadMenuAside(menu, geo.widthPx, rtl).homeCardLayer(geo) { 1f }.background(Color(0xFF000000)))
            }
        }
    }

    /** Renders the stage; [scale] null follows the persisted setting (the pinch and the stepper change it). */
    protected fun render(items: List<HubNotification> = feed(4), dark: Boolean = true, name: String? = null, rtl: Boolean = false, home: Boolean = true, scale: Int? = null) {
        rule.setContent { Stage(items, dark, rtl, home, scale) }
        rule.waitForIdle()
        if (name != null) shoot("comms-$orientation-$name-${if (dark) "dark" else "light"}")
    }
    protected fun openMenu() { rule.onNodeWithTag("lp.menu").performClick(); rule.waitForIdle(); rule.mainClock.advanceTimeBy(1000); rule.waitForIdle() }

    /** A pixel copy of the current frame, for measuring ink. */
    protected fun frame(): android.graphics.Bitmap {
        rule.mainClock.advanceTimeBy(800); rule.waitForIdle()
        val view = rule.activity.findViewById<android.view.View>(android.R.id.content)
        val bmp = android.graphics.Bitmap.createBitmap(view.width, view.height, android.graphics.Bitmap.Config.ARGB_8888)
        rule.runOnUiThread { view.draw(android.graphics.Canvas(bmp)) }
        return bmp
    }
}
