package com.bos.sphere

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import com.bos.sphere.core.data.LauncherStores
import com.bos.sphere.core.data.ThemeMode
import com.bos.sphere.core.design.HyleTheme
import com.bos.sphere.core.design.L10n
import com.bos.sphere.core.design.SphereTheme
import com.bos.sphere.core.design.ThemeVariant
import com.bos.sphere.ui.ThemeFileIo
import com.bos.sphere.ui.ThemeHost
import com.bos.sphere.ui.ThemeImportFlow

/**
 * Wave 39 (dockThemes): opening a `.liftofftheme` file from a file manager or a message lands on the same preview as Ground Control's Import theme.
 * The file is untrusted: read at most 64 KB, validated, previewed, and applied only when the person taps Apply. It never reads anything but the
 * one Uri it was handed and never opens a link or starts anything.
 */
class ThemeImportActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uri = intent?.takeIf { it.action == android.content.Intent.ACTION_VIEW }?.data
        LauncherStores.get(this).prefs.prefs.value.let { p ->
            HyleTheme.apply(when (p.themeMode) { ThemeMode.Dark -> ThemeVariant.Dark; ThemeMode.Amoled -> ThemeVariant.Amoled; ThemeMode.Light -> ThemeVariant.Light }, p.accentColor)
            L10n.setLanguage(p.language.code)
            com.bos.sphere.core.design.HyleFont.apply(p.uiFont)
        }
        val host = ThemeHost.of(this)
        setContent {
            SphereTheme {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding().verticalScroll(rememberScrollState())) {
                    ThemeImportFlow(load = { uri?.let { ThemeFileIo.readBounded(this@ThemeImportActivity, it) } }, host = host, onClose = { finish() })
                }
            }
        }
    }
}
