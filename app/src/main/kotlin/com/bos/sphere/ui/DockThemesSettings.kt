package com.bos.sphere.ui

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bos.sphere.core.data.DockStyle
import com.bos.sphere.core.design.HyleColors
import com.bos.sphere.core.design.L10n
import com.bos.sphere.feature.dock.DockStyleChooser
import com.bos.sphere.feature.dock.WorkspaceManager
import com.bos.sphere.feature.dock.WorkspaceRepository
import com.bos.sphere.feature.grid.Glyphs
import com.bos.sphere.feature.grid.SettingItem

/** Wave 39 (dockThemes): the two Ground Control entries this wave adds, built here so the shared settings builder gets one line each. */
fun dockStyleSettingItem(): SettingItem = SettingItem.Pane(L10n.tr("Dock style"), Glyphs.Apps, L10n.tr("Standard, transparent or 3D")) { DockStylePane() }

fun themesSettingItem(): SettingItem = SettingItem.Pane(L10n.tr("Themes"), Glyphs.Palette, L10n.tr("Import, export, reset")) { ThemesPane() }

/** Home screen > Dock style: three radio choices, each with a small preview drawn by the dock's own code. The dock itself stays optional. */
@Composable
fun ColumnScope.DockStylePane(manager: WorkspaceManager = WorkspaceRepository.get(LocalContext.current)) {
    var style by remember { mutableStateOf(manager.current.dock.style) }
    DockStyleChooser(style, onSelect = { s: DockStyle -> style = s; manager.setDock { it.copy(style = s) } }, modifier = Modifier.padding(vertical = 8.dp))
    Text(L10n.tr("The dock is turned on or off under Desktop & dock. A long press on the dock also opens this choice."), color = HyleColors.InkDim, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
}
