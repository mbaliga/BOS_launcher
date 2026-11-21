package com.bos.sphere.ui

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.bos.sphere.core.data.HomePages
import com.bos.sphere.core.data.LauncherPrefs
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Wave 34 (homeSheet): Robolectric screenshots of the Home sheet body over a dimmed screen, to build/screenshots/home-sheet-*.png (not a device run). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class HomeQuickSheetShotsTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private fun shoot(name: String, rtl: Boolean = false, page: Int? = null) {
        rule.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                Box(Modifier.fillMaxSize().background(Color(0xFF1B2433)), contentAlignment = Alignment.BottomCenter) {
                    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.62f)))
                    Box(Modifier.fillMaxWidth().background(Color.Black, RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp))) {
                        HomeQuickSheetBody(LauncherPrefs(homePages = HomePages.Paginated), {}, page, 2, {}, {}, {})
                    }
                }
            }
        }
        rule.mainClock.advanceTimeBy(800)
        rule.waitForIdle()
        val dir = File(System.getProperty("user.dir"), "build/screenshots").also { it.mkdirs() }
        val view = rule.activity.findViewById<android.view.View>(android.R.id.content)
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread { view.draw(android.graphics.Canvas(bmp)) }
        File(dir, "home-sheet-$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        assertTrue(File(dir, "home-sheet-$name.png").length() > 0)
    }

    @Config(qualifiers = "w411dp-h891dp-port-night-xhdpi") @Test fun dark_portrait() = shoot("dark-portrait")
    @Config(qualifiers = "w411dp-h891dp-port-notnight-xhdpi") @Test fun light_portrait() = shoot("light-portrait")
    @Config(qualifiers = "w411dp-h891dp-port-night-xhdpi") @Test fun page_tools() = shoot("page-tools", page = 0)
    @Config(qualifiers = "w411dp-h891dp-port-night-xhdpi") @Test fun rtl() = shoot("rtl", rtl = true)
    @Config(qualifiers = "w411dp-h891dp-port-night-xhdpi", fontScale = 2f) @Test fun fontScale2() = shoot("font2")
    @Config(qualifiers = "w891dp-h411dp-land-night-xhdpi") @Test fun landscape() = shoot("landscape")
}
