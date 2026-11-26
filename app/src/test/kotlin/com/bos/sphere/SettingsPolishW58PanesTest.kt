package com.bos.sphere

import android.content.ComponentName
import android.graphics.drawable.ColorDrawable
import android.os.Process
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import com.bos.sphere.core.data.AppEntry
import com.bos.sphere.core.data.DockStyle
import com.bos.sphere.core.data.LauncherPrefs
import com.bos.sphere.core.data.PuckActionsStore
import com.bos.sphere.core.data.ThemeApplier
import com.bos.sphere.core.data.ThemeUndo
import com.bos.sphere.core.design.L10n
import com.bos.sphere.core.design.SphereTheme
import com.bos.sphere.feature.grid.LocalPaneFlash
import com.bos.sphere.feature.grid.PaneFlashed
import com.bos.sphere.ui.PuckActionsPane
import com.bos.sphere.ui.SettingsRowMeta
import com.bos.sphere.ui.ThemeFiles
import com.bos.sphere.ui.ThemeHost
import com.bos.sphere.ui.ThemesPane
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Wave 58 (settingsPolish), CFG-67: the panes a Settings search can now land in draw their rows under the ids the index holds, so the row the hit
 * named lights (the host provides the id through [LocalPaneFlash], as it does after a hit; the end-to-end hit is in feature-grid's SettingsPolishW58UiTest).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h1600dp-xhdpi")
class SettingsPolishW58PanesTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private class NoFiles : ThemeFiles {
        override fun pickImport(onBytes: (ByteArray?) -> Unit) {}
        override fun saveExport(fileName: String, text: String, onDone: (Boolean) -> Unit) {}
    }
    private var prefs = LauncherPrefs()
    private var dock = DockStyle.Standard
    private fun host() = ThemeHost({ prefs }, ThemeApplier({ prefs }, { f -> prefs = f(prefs) }, { dock }, { dock = it }))

    @Before fun setUp() { L10n.setLanguage("en"); ThemeUndo.clear() }

    private fun lit(id: String, text: String) {
        rule.waitForIdle()
        val nodes = rule.onAllNodes(SemanticsMatcher.expectValue(PaneFlashed, id), useUnmergedTree = true).fetchSemanticsNodes()
        check(nodes.size == 1) { "one row lights for $id, found ${nodes.size}" }
        // the lit row is the one with the text the search entry shows
        val withText = rule.onAllNodes(SemanticsMatcher.expectValue(PaneFlashed, id) and (hasText(text, substring = true) or androidx.compose.ui.test.hasAnyDescendant(hasText(text, substring = true))), useUnmergedTree = true).fetchSemanticsNodes()
        check(withText.isNotEmpty()) { "the lit row for $id reads \"$text\"" }
    }

    @Test fun CFG_67_aThemesHitLightsItsRow() {
        val which = androidx.compose.runtime.mutableStateOf(SettingsRowMeta.PaneIds.THEMES_IMPORT)
        rule.setContent { SphereTheme { CompositionLocalProvider(LocalPaneFlash provides which.value) { Column { ThemesPane(NoFiles(), host()) } } } }
        lit(SettingsRowMeta.PaneIds.THEMES_IMPORT, "Import theme")
        androidx.compose.runtime.snapshots.Snapshot.withMutableSnapshot { which.value = SettingsRowMeta.PaneIds.THEMES_EXPORT }
        lit(SettingsRowMeta.PaneIds.THEMES_EXPORT, "Export current look")
        androidx.compose.runtime.snapshots.Snapshot.withMutableSnapshot { which.value = SettingsRowMeta.PaneIds.THEMES_RESET }
        lit(SettingsRowMeta.PaneIds.THEMES_RESET, "Reset to default look")
    }

    @Test fun CFG_67_aDrawerShortcutsHitLightsThatSegmentOrTheReset() {
        AppSmokeHarness.resetProcess()
        PuckActionsStore.get(ApplicationProvider.getApplicationContext()).reset()
        val app = AppEntry("demo.notes", ComponentName("demo.notes", "M"), "Notes", Process.myUserHandle()) { ColorDrawable(0xFF2F6BFF.toInt()) }
        val which = androidx.compose.runtime.mutableStateOf(SettingsRowMeta.PaneIds.drawerSegment(3))
        rule.setContent { SphereTheme { CompositionLocalProvider(LocalPaneFlash provides which.value) { Column { PuckActionsPane(mapOf(app.key to app)) } } } }
        lit(SettingsRowMeta.PaneIds.drawerSegment(3), "3.")
        androidx.compose.runtime.snapshots.Snapshot.withMutableSnapshot { which.value = SettingsRowMeta.PaneIds.DRAWER_RESET }
        lit(SettingsRowMeta.PaneIds.DRAWER_RESET, "Reset to default")
    }

    @Test fun CFG_67_theRadarPanesLightTheirRows() {
        val which = androidx.compose.runtime.mutableStateOf(SettingsRowMeta.PaneIds.FOLDERS_ADD)
        rule.setContent {
            SphereTheme {
                CompositionLocalProvider(LocalPaneFlash provides which.value) {
                    Column { com.bos.sphere.feature.grid.FolderGrantsPane(); com.bos.sphere.feature.grid.SearchHistoryPane() }
                }
            }
        }
        lit(SettingsRowMeta.PaneIds.FOLDERS_ADD, "Add a folder")
        androidx.compose.runtime.snapshots.Snapshot.withMutableSnapshot { which.value = SettingsRowMeta.PaneIds.FOLDERS_REINDEX }
        lit(SettingsRowMeta.PaneIds.FOLDERS_REINDEX, "Update the file index now")
        androidx.compose.runtime.snapshots.Snapshot.withMutableSnapshot { which.value = SettingsRowMeta.PaneIds.HISTORY_FORGET }
        lit(SettingsRowMeta.PaneIds.HISTORY_FORGET, "Forget what Radar learned")
    }
}
